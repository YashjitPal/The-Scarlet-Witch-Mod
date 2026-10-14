package com.yashjit.scarlet.client.hex;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The server's game time, as Hexes are drawn by: going on smoothly at whatever pace the server keeps, and never going
 * back.
 *
 * <p>A client's own clock runs on at twenty ticks a second whatever the server does, and is set back to the server's
 * each time it hears it. A server that can't keep up hears it set back a second or more each time, which would set every
 * wall, town and era change it draws jumping back with it. This one goes at the pace the server has been keeping
 * instead, eases after where the server must be by now, and slows or waits rather than going back.
 */
public final class HexClock {

    /** Further behind than this from where the server must be, it jumps there, as on arriving somewhere. */
    private static final double SNAP_TICKS = 100.0;
    /** Ticks off from the server it takes to go at no speed, or at double speed. */
    private static final double CATCH_UP_TICKS = 10.0;
    /** Longest after the server last told it its time it goes on as if it had kept its pace: a server that has stopped stops it. */
    private static final double MOST_UNHEARD_TICKS = 30.0;

    private static @Nullable ClientLevel level;
    /** The server's game time as last heard, and when, in {@link Util#getNanos} nanoseconds. */
    private static long heard;
    private static long heardAt;
    private static boolean heardAny;
    /** How fast the server's clock has been going, in ticks a second. */
    private static double pace = 20.0;
    /** The time as last drawn by, and when. */
    private static double shown;
    private static long shownAt;

    private HexClock() {
    }

    /**
     * The server's word of its game time, as the client sets its own clock to it.
     */
    public static void heard(ClientLevel from, long gameTime) {
        long nanos = Util.getNanos();
        if (from != level) {
            forget(from);
        }
        if (heardAny && gameTime > heard && nanos > heardAt) {
            double measured = (gameTime - heard) / ((nanos - heardAt) / 1.0E9);
            pace = Mth.lerp(0.5, pace, Math.clamp(measured, 0.0, 200.0));
        }
        heard = gameTime;
        heardAt = nanos;
        heardAny = true;
    }

    /**
     * The game time to draw Hexes by this frame.
     */
    public static double now(Minecraft minecraft) {
        ClientLevel current = minecraft.level;
        if (current == null) {
            return 0.0;
        }
        if (current != level) {
            forget(current);
        }
        long nanos = Util.getNanos();
        double own = current.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (!heardAny) {
            shown = own;
            shownAt = nanos;
            return shown;
        }
        double seconds = Math.max(0.0, (nanos - shownAt) / 1.0E9);
        shownAt = nanos;
        if (minecraft.isPaused() || !current.tickRateManager().runsNormally()) {
            // the server stands still as well; so does the time it was last heard at
            heardAt += (long) (seconds * 1.0E9);
            return shown;
        }
        double target = heard + Math.min(pace * (nanos - heardAt) / 1.0E9, MOST_UNHEARD_TICKS);
        double behind = target - shown;
        if (behind > SNAP_TICKS) {
            shown = target;
            return shown;
        }
        double speed = Math.clamp(1.0 + behind / CATCH_UP_TICKS, 0.0, 2.0);
        shown += seconds * pace * speed;
        return shown;
    }

    private static void forget(@Nullable ClientLevel to) {
        level = to;
        heardAny = false;
        pace = 20.0;
        shown = to == null ? 0.0 : to.getGameTime();
        shownAt = Util.getNanos();
    }
}
