package com.yashjit.scarlet.client.dev;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.yashjit.scarlet.Scarlet;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.util.TimeSource;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Films the showcase, frame by frame. While it films, the game's clock, which its server keeps time by too, moves on by
 * exactly one frame for each frame drawn, however long that frame takes to draw, and every frame goes straight to
 * ffmpeg: the film plays back smooth at its own frame rate on any machine. Development only.
 */
public final class ShowcaseRecorder {

    public static final int FPS = 30;
    /** What every film comes out as, whatever the window. */
    public static final int WIDTH = 1920;
    public static final int HEIGHT = 1080;
    private static final long FRAME_NANOS = 1_000_000_000L / FPS;
    private static final long TICK_NANOS = 50_000_000L;
    private static final byte[] END = new byte[0];

    private static final Clock CLOCK = new Clock();
    private static boolean installed;
    private static @Nullable Film film;
    /** How much of the game's time passes in a frame of film, as a share of the frame's own. */
    private static float speed = 1.0F;

    private ShowcaseRecorder() {
    }

    /** Where films are saved: the Videos folder, unless {@code -Dscarlet.showcase.films} says otherwise. */
    public static Path folder() {
        String chosen = System.getProperty("scarlet.showcase.films");
        return chosen != null && !chosen.isBlank() ? Path.of(chosen) : Path.of(System.getProperty("user.home"), "Videos", "Scarlet Witch", "raw");
    }

    private static Path ffmpeg() {
        String chosen = System.getProperty("scarlet.showcase.ffmpeg");
        return chosen != null && !chosen.isBlank() ? Path.of(chosen) : Path.of(System.getProperty("user.home"), ".scarlet-tools", "ffmpeg", "ffmpeg.exe");
    }

    public static boolean filming() {
        return film != null && film.rolling;
    }

    /**
     * Films slower or faster than the game runs: at a half, every second of the game takes two of film, drawn smooth
     * between its ticks; at two, the game races through twice as much.
     */
    public static void speed(float filmSpeed) {
        speed = Math.max(0.05F, filmSpeed);
    }

    /**
     * Starts a film, to the Videos folder: from now until it is cut, the clock moves a frame at a time.
     */
    public static void roll(Minecraft minecraft, String name) {
        if (film != null) {
            cut();
        }
        if (!installed) {
            Util.setTimeSource(CLOCK);
            installed = true;
        }
        RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
        try {
            Files.createDirectories(folder());
            film = new Film(name, target.width, target.height);
            CLOCK.stepping();
            film.syncFrom(minecraft);
            film.rolling = true;
            Scarlet.LOG.info("Showcase: filming {} at {}x{}", name, target.width, target.height);
        } catch (IOException e) {
            Scarlet.LOG.error("Showcase: could not start filming {}", name, e);
            film = null;
        }
    }

    /**
     * Stops filming for a moment, the clock running free again, as the next shot is set up.
     */
    public static void hold() {
        if (film != null && film.rolling) {
            film.rolling = false;
            CLOCK.running();
        }
    }

    /**
     * Films on after a hold.
     */
    public static void resume(Minecraft minecraft) {
        if (film != null && !film.rolling) {
            CLOCK.stepping();
            film.syncFrom(minecraft);
            film.rolling = true;
        }
    }

    /**
     * Ends the film: the frames still on their way are written out, and ffmpeg finishes the file.
     */
    public static void cut() {
        Film ending = film;
        if (ending == null) {
            return;
        }
        hold();
        film = null;
        speed = 1.0F;
        ending.finish();
    }

    /**
     * The end of every frame drawn: while filming, the frame is taken, and the clock moves on by one frame once the
     * server has caught up with it.
     */
    public static void frameEnded(RenderTarget target) {
        Film rolling = film;
        if (rolling == null || !rolling.rolling) {
            return;
        }
        long step = Math.round(FRAME_NANOS * (double) speed);
        if (target.width != rolling.width || target.height != rolling.height || target.getColorTexture() == null) {
            // the window was resized mid-shot: this frame is skipped rather than drawn squashed
            CLOCK.advance(step);
            return;
        }
        rolling.take(target);
        CLOCK.advance(step);
        rolling.waitForServer(Minecraft.getInstance());
    }

    /**
     * One film being made: ffmpeg encoding what is written to it, and the frames on their way there.
     */
    private static final class Film {
        final String name;
        final int width;
        final int height;
        final Process encoder;
        final BlockingQueue<byte[]> frames = new ArrayBlockingQueue<>(6);
        final AtomicInteger inFlight = new AtomicInteger();
        final Thread writer;
        volatile boolean rolling;
        int taken;
        long syncNanos;
        int syncTicks;

        Film(String name, int width, int height) throws IOException {
            this.name = name;
            this.width = width;
            this.height = height;
            Path out = folder().resolve(name + ".mp4");
            List<String> command = new ArrayList<>(List.of(ffmpeg().toString(), "-y", "-hide_banner", "-loglevel", "error",
                    "-f", "rawvideo", "-pix_fmt", "rgba", "-s", width + "x" + height, "-framerate", Integer.toString(FPS), "-i", "-",
                    "-vf", "vflip,scale=" + WIDTH + ":" + HEIGHT + ":flags=lanczos,format=yuv420p",
                    "-c:v", "libx264", "-preset", "medium", "-crf", "17", "-movflags", "+faststart", out.toString()));
            encoder = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(folder().resolve(name + ".log").toFile()).start();
            OutputStream stdin = new BufferedOutputStream(encoder.getOutputStream(), 1 << 20);
            writer = new Thread(() -> write(stdin), "Showcase film writer");
            writer.setDaemon(true);
            writer.start();
        }

        private void write(OutputStream stdin) {
            try (stdin) {
                while (true) {
                    byte[] frame = frames.take();
                    if (frame == END) {
                        break;
                    }
                    stdin.write(frame);
                }
            } catch (IOException | InterruptedException e) {
                Scarlet.LOG.error("Showcase: writing film {} failed", name, e);
            }
        }

        /**
         * Copies the finished frame off the GPU, to be written once it has arrived.
         */
        void take(RenderTarget target) {
            var texture = target.getColorTexture();
            GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Showcase film frame", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                    (long) width * height * 4);
            inFlight.incrementAndGet();
            RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(texture, buffer, 0L, () -> {
                try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
                    byte[] pixels = new byte[width * height * 4];
                    view.data().get(pixels);
                    frames.put(pixels);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    buffer.close();
                    inFlight.decrementAndGet();
                }
            }, 0);
            taken++;
        }

        void syncFrom(Minecraft minecraft) {
            syncNanos = CLOCK.getAsLong();
            IntegratedServer server = minecraft.getSingleplayerServer();
            syncTicks = server == null ? 0 : server.getTickCount();
        }

        /**
         * Waits for the server to have run every tick the clock has reached, so what moves on it is where it should be
         * in the next frame.
         */
        void waitForServer(Minecraft minecraft) {
            IntegratedServer server = minecraft.getSingleplayerServer();
            if (server == null) {
                return;
            }
            if (minecraft.isPaused()) {
                // a paused server counts no ticks, so the count is taken afresh once it goes on
                syncFrom(minecraft);
                return;
            }
            long due = syncTicks + (CLOCK.getAsLong() - syncNanos) / TICK_NANOS;
            long giveUp = System.nanoTime() + 3_000_000_000L;
            long quietSince = -1L;
            Thread thread = server.getRunningThread();
            // its count goes up as a tick begins: done is when it has sat waiting for a moment, as a tick about to begin
            // wakes it within a tenth of a millisecond
            while (System.nanoTime() < giveUp && server.isRunning()) {
                long now = System.nanoTime();
                if (server.getTickCount() < due || thread.getState() == Thread.State.RUNNABLE) {
                    quietSince = -1L;
                } else if (quietSince < 0L) {
                    quietSince = now;
                } else if (now - quietSince > 300_000L) {
                    break;
                }
                Thread.onSpinWait();
            }
        }

        /**
         * Waits for the last frames to come off the GPU, then lets ffmpeg finish, on its own thread.
         */
        void finish() {
            Thread finisher = new Thread(() -> {
                long giveUp = System.nanoTime() + 10_000_000_000L;
                while (inFlight.get() > 0 && System.nanoTime() < giveUp) {
                    Thread.onSpinWait();
                }
                try {
                    frames.put(END);
                    writer.join();
                    int code = encoder.waitFor();
                    Scarlet.LOG.info("Showcase: film {} done, {} frames ({} seconds), ffmpeg exit {}", name, taken,
                            String.format(java.util.Locale.ROOT, "%.1f", taken / (double) FPS), code);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Showcase film finisher");
            finisher.setDaemon(false);
            finisher.start();
        }
    }

    /**
     * The game's clock while it films: a frame's worth at a time, else the real clock, carried on from where filming
     * left it, so time never jumps.
     */
    private static final class Clock implements TimeSource.NanoTimeSource {
        private volatile boolean stepping;
        private volatile long frame;
        private volatile long offset;

        @Override
        public long getAsLong() {
            return stepping ? frame : System.nanoTime() - offset;
        }

        void stepping() {
            if (!stepping) {
                frame = System.nanoTime() - offset;
                stepping = true;
            }
        }

        void running() {
            if (stepping) {
                offset = System.nanoTime() - frame;
                stepping = false;
            }
        }

        void advance(long nanos) {
            frame += nanos;
        }
    }
}
