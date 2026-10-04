package com.yashjit.scarlet.client.hex;

import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.ScarletKeyMappings;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.config.ScarletClientConfig;
import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.hex.HexShape;
import com.yashjit.scarlet.hex.HexSnapshot;
import com.yashjit.scarlet.hex.HexTape;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.network.ShowrunnerPayload;
import com.yashjit.scarlet.platform.Services;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Rewinding a Hex, client side: the rewind key or the remote's rewind button held sends word to the server, and a Hex
 * winding back looks and sounds the way the set its era is played on would show it, more dramatically the older the
 * era. Everyone inside sees it; from outside, the wall glitches hard instead.
 *
 * <ul>
 *     <li>1950s: film yanked back through a projector, flickering, scratched and rolling, the projector whirring.</li>
 *     <li>1960s: a black and white broadcast slipping, the picture rolling and its horizontal hold tearing.</li>
 *     <li>1970s: color film and early tape, smeared, warm and juddering.</li>
 *     <li>1980s: VHS, tracking bands crawling up a squashed picture, "REW" in the corner, the tape whining.</li>
 *     <li>2000s: DVD, the picture jumping back in blocky skips, its speed in the corner.</li>
 *     <li>The present day: a smooth scrub along a thin progress bar, with a ten-seconds-back mark.</li>
 * </ul>
 */
public final class RewindClient {

    /** Ticks the picture takes to start winding back, and to settle once it stops. */
    private static final float IN_TICKS = 4.0F;
    private static final float OUT_TICKS = 7.0F;

    private static boolean keyHeld;
    private static boolean remoteHeld;
    /** What the server was last told: held or not. */
    private static boolean told;
    /** How strongly the view is winding back, this tick and the last, for the frames between. */
    private static float amount;
    private static float amountBefore;
    private static Era era = Era.PRESENT;
    private static long since;
    private static boolean wasRewinding;
    private static @Nullable Whir whir;

    private RewindClient() {
    }

    /**
     * The remote's rewind button held down, or let go.
     */
    public static void holdRemote(boolean held) {
        remoteHeld = held;
    }

    public static boolean remoteHeld() {
        return remoteHeld;
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            keyHeld = false;
            remoteHeld = false;
            told = false;
            amount = 0.0F;
            amountBefore = 0.0F;
            stopWhir();
            return;
        }
        keyHeld = minecraft.gui.screen() == null && ScarletKeyMappings.REWIND.isDown();
        boolean held = keyHeld || remoteHeld;
        if (held != told) {
            told = held;
            Services.NETWORK.sendToServer(ShowrunnerPayload.of(ShowrunnerPayload.REWIND, held ? 1 : 0));
        }
        HexSnapshot around = around(minecraft);
        boolean rewinding = around != null && around.rewinding();
        if (rewinding) {
            era = around.eraValue();
            since = around.rewindSince();
        }
        amountBefore = amount;
        amount = rewinding ? Math.min(1.0F, amount + 1.0F / IN_TICKS) : Math.max(0.0F, amount - 1.0F / OUT_TICKS);
        double now = minecraft.level.getGameTime();
        if (rewinding != wasRewinding) {
            wasRewinding = rewinding;
            clunk(minecraft, rewinding);
            if (rewinding) {
                startWhir(minecraft);
            } else {
                stopWhir();
            }
        }
        if (rewinding) {
            tickSprockets(minecraft, now);
        }
    }

    /**
     * The Hex the view is inside, if any.
     */
    private static @Nullable HexSnapshot around(Minecraft minecraft) {
        Entity camera = minecraft.getCameraEntity() != null ? minecraft.getCameraEntity() : minecraft.player;
        Vec3 eye = camera.getEyePosition();
        double now = minecraft.level.getGameTime();
        for (HexSnapshot hex : Hexes.clientHexes()) {
            if (HexShape.contains(hex.center(), HexClient.drawnRadius(hex, now), eye)) {
                return hex;
            }
        }
        return null;
    }

    /**
     * How strongly the view winds back right now, 0 to 1.
     */
    public static float amount(float partialTick) {
        return Mth.lerp(partialTick, amountBefore, amount);
    }

    /**
     * How fast it winds, 1 to 4 times, gathering speed as the server does.
     */
    public static float speed(double now) {
        return 1.0F + (HexTape.TOP_SPEED - 1) * Ease.clamp01((float) (now - since) / HexTape.GATHER_TICKS);
    }

    /**
     * Seconds since it began winding.
     */
    public static float seconds(double now) {
        return (float) Math.max(0.0, now - since) / 20.0F;
    }

    // ---------------------------------------------------------------- sound

    private static void clunk(Minecraft minecraft, boolean starting) {
        switch (era) {
            case FIFTIES, SIXTIES -> play(minecraft, SoundEvents.PISTON_CONTRACT, starting ? 1.7F : 1.4F, 0.3F);
            case SEVENTIES -> play(minecraft, SoundEvents.PISTON_CONTRACT, starting ? 1.3F : 1.1F, 0.3F);
            case EIGHTIES -> play(minecraft, SoundEvents.PISTON_CONTRACT, starting ? 0.9F : 1.2F, 0.45F);
            case TWO_THOUSANDS -> play(minecraft, SoundEvents.UI_BUTTON_CLICK.value(), starting ? 1.5F : 1.3F, 0.25F);
            case PRESENT -> play(minecraft, SoundEvents.UI_TOAST_IN, starting ? 1.5F : 1.1F, 0.35F);
        }
    }

    /**
     * The film's sprockets clattering through the gate, and a DVD's skips clicking.
     */
    private static void tickSprockets(Minecraft minecraft, double now) {
        long tick = (long) now;
        switch (era) {
            case FIFTIES, SIXTIES -> {
                if (tick % 2 == 0) {
                    play(minecraft, SoundEvents.UI_BUTTON_CLICK.value(), 1.9F + minecraft.level.getRandom().nextFloat() * 0.2F, 0.06F);
                }
            }
            case SEVENTIES -> {
                if (tick % 4 == 0) {
                    play(minecraft, SoundEvents.UI_BUTTON_CLICK.value(), 1.6F, 0.05F);
                }
            }
            case TWO_THOUSANDS -> {
                if (tick % Math.max(2, Math.round(8.0F / speed(now))) == 0) {
                    play(minecraft, SoundEvents.UI_BUTTON_CLICK.value(), 1.7F, 0.12F);
                }
            }
            default -> {
            }
        }
    }

    private static void play(Minecraft minecraft, SoundEvent sound, float pitch, float volume) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private static void startWhir(Minecraft minecraft) {
        stopWhir();
        SoundEvent sound = switch (era) {
            case FIFTIES, SIXTIES, SEVENTIES -> SoundEvents.MINECART_INSIDE;
            case EIGHTIES -> SoundEvents.MINECART_RIDING;
            default -> null;
        };
        if (sound != null) {
            whir = new Whir(sound, era == Era.EIGHTIES ? 1.9F : era == Era.SEVENTIES ? 1.4F : 1.75F);
            minecraft.getSoundManager().play(whir);
        }
    }

    private static void stopWhir() {
        if (whir != null) {
            whir.end();
            whir = null;
        }
    }

    /**
     * The projector or the tape running back, rising in pitch as it gathers speed.
     */
    private static final class Whir extends AbstractTickableSoundInstance {

        private final float basePitch;
        private boolean ending;

        Whir(SoundEvent sound, float basePitch) {
            super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.basePitch = basePitch;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001F;
            this.pitch = basePitch * 0.8F;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        void end() {
            ending = true;
        }

        @Override
        public void tick() {
            Minecraft minecraft = Minecraft.getInstance();
            double now = minecraft.level == null ? 0.0 : minecraft.level.getGameTime();
            volume = ending ? volume * 0.6F : Math.min(0.32F, volume + 0.08F);
            pitch = Math.min(2.0F, basePitch * (0.8F + 0.05F * speed(now)));
            if (ending && volume < 0.01F) {
                stop();
            }
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    // ---------------------------------------------------------------- the display on screen

    /**
     * What the set shows over the picture while it winds back: "REW" on videotape, the speed on a DVD, a progress bar
     * when streaming. Film and broadcast show nothing over it; the picture itself is enough.
     */
    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        float shown = amount(deltaTracker.getGameTimeDeltaPartialTick(false));
        if (shown <= 0.01F) {
            return;
        }
        double now = minecraft.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);
        Font font = minecraft.font;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        // the older sets' displays sit inside their 4:3 picture
        int frame = Math.max(0, Math.round((width - height * 4.0F / 3.0F) / 2.0F));
        switch (era) {
            case EIGHTIES -> videotape(graphics, font, frame, shown, now);
            case TWO_THOUSANDS -> disc(graphics, font, width - frame, shown, now);
            case PRESENT -> streaming(graphics, font, width, height, shown, now);
            default -> {
            }
        }
    }

    /**
     * A VCR's display: two arrows and REW in blocky white, flickering with the tape.
     *
     * @param left where the picture's left edge is
     */
    private static void videotape(GuiGraphicsExtractor graphics, Font font, int left, float shown, double now) {
        boolean steady = ScarletClientConfig.get().reduceFlashing;
        float flicker = steady ? 1.0F : 0.82F + 0.18F * Mth.sin((float) now * 2.7F);
        int color = ARGB.color(shown * flicker, 0xF2F2F2);
        int x = left + 18 + (steady ? 0 : Math.round(Mth.sin((float) now * 1.3F)));
        int y = 16;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(2.0F);
        arrows(graphics, 0, 0, 7, color, ARGB.color(shown * flicker, 0x101010));
        graphics.text(font, Component.translatable("rewind.scarlet.rew"), 17, 0, color, true);
        graphics.pose().popMatrix();
    }

    /**
     * A DVD player's display: the arrows and how fast, clean, in a dark box at the top right.
     *
     * @param right where the picture's right edge is
     */
    private static void disc(GuiGraphicsExtractor graphics, Font font, int right, float shown, double now) {
        int speed = Math.clamp(Math.round(speed(now)), 1, HexTape.TOP_SPEED);
        Component label = Component.translatable("rewind.scarlet.speed", 1 << speed);
        int boxWidth = 22 + font.width(label);
        int x = right - boxWidth - 14;
        int y = 14;
        graphics.fill(x, y, x + boxWidth, y + 16, ARGB.color(shown * 0.55F, 0x0B1A33));
        graphics.fill(x, y + 15, x + boxWidth, y + 16, ARGB.color(shown * 0.8F, 0x5A8DEE));
        arrows(graphics, x + 5, y + 4, 4, ARGB.color(shown, 0xE8F0FF), 0);
        graphics.text(font, label, x + 19, y + 4, ARGB.color(shown, 0xE8F0FF), false);
    }

    /**
     * A streaming service's scrub: the picture dims at the bottom, a thin bar runs across it with its mark sliding back,
     * and a ten-seconds-back sign shows in the middle.
     */
    private static void streaming(GuiGraphicsExtractor graphics, Font font, int width, int height, float shown, double now) {
        graphics.fillGradient(0, height - 46, width, height, 0, ARGB.color(shown * 0.6F, 0x000000));
        int left = 20;
        int right = width - 20;
        int y = height - 22;
        // how much of the scene is still left to wind back to, sliding left as it goes
        float played = 1.0F - Ease.clamp01(RewindClient.seconds(now) * RewindClient.speed(now) / (HexTape.LENGTH / 20.0F));
        int at = left + Math.round((right - left) * (0.35F + 0.6F * played));
        graphics.fill(left, y, right, y + 2, ARGB.color(shown * 0.35F, 0xFFFFFF));
        graphics.fill(left, y, at, y + 2, ARGB.color(shown * 0.9F, ScarletPalette.SCARLET));
        graphics.fill(at - 3, y - 2, at + 3, y + 4, ARGB.color(shown, ScarletPalette.SCARLET));
        // the ten-seconds-back sign, a ring with an arrowhead and 10 in it, over the left of the picture as a stream shows it
        int cx = Math.round(width * 0.3F);
        int cy = height / 2;
        int ring = ARGB.color(shown * 0.85F, 0xFFFFFF);
        for (int k = 0; k < 28; k++) {
            float angle = (float) (Math.PI * 2.0 * k / 28.0);
            if (k < 3) {
                continue;
            }
            int px = cx + Math.round(Mth.cos(angle) * 13.0F);
            int py = cy + Math.round(Mth.sin(angle) * 13.0F);
            graphics.fill(px - 1, py - 1, px + 1, py + 1, ring);
        }
        graphics.fill(cx + 9, cy - 6, cx + 14, cy - 4, ring);
        graphics.fill(cx + 12, cy - 9, cx + 14, cy - 4, ring);
        graphics.centeredText(font, Component.translatable("rewind.scarlet.ten"), cx, cy - 4, ring);
    }

    /**
     * Two arrows pointing back, side by side, drawn in squares like the set's own display.
     */
    private static void arrows(GuiGraphicsExtractor graphics, int x, int y, int size, int color, int shadow) {
        for (int arrow = 0; arrow < 2; arrow++) {
            int left = x + arrow * size;
            for (int row = 0; row < size; row++) {
                int reach = size - Math.abs(row - size / 2) * 2;
                int from = left + size - Math.max(1, reach);
                if (shadow != 0) {
                    graphics.fill(from + 1, y + row + 1, left + size + 1, y + row + 2, shadow);
                }
                graphics.fill(from, y + row, left + size, y + row + 1, color);
            }
        }
    }
}
