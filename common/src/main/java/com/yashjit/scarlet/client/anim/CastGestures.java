package com.yashjit.scarlet.client.anim;

import com.yashjit.scarlet.magic.Magic;
import com.yashjit.scarlet.magic.MagicState;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * Timing of every visible player's casting strikes, so their third-person pose, first-person arms and the magic on
 * their arms all move as one. Strikes come from the synced magic state; your own also start the instant you click.
 *
 * <p>A strike winds up, snaps forward past full reach and settles, holds, then eases home. A new strike starts from
 * wherever the arm already is, so rapid fire flows from hand to hand instead of snapping back each time.
 */
public final class CastGestures {

    private static final float WIND_UP = 0.6F;
    private static final float STRIKE_END = 2.0F;
    private static final float HOLD_END = 4.5F;
    private static final float RECOVER_END = 12.0F;
    /**
     * Where in a strike its magic leaves the palm: once the thrust has the arm at full reach, as the blast leaves from
     * there. At the end of the wind-up the hand is still drawn back low at the side.
     */
    private static final float RELEASE = WIND_UP + (STRIKE_END - WIND_UP) * 0.55F;

    private static final Int2ObjectMap<Track> TRACKS = new Int2ObjectOpenHashMap<>();

    private CastGestures() {
    }

    /**
     * Starts your own strike now, before the server confirms it.
     */
    public static void predict(Player player, HumanoidArm arm, double now) {
        Track track = track(player);
        track.start(arm, now);
        track.predictedAt = now;
        track.predictedArm = arm;
    }

    /**
     * How far the arm is thrust toward the target: dips to about -0.25 while winding up, reaches 1, briefly overshoots.
     */
    public static float extension(Player player, HumanoidArm arm, double now) {
        Track track = track(player);
        return arm == HumanoidArm.RIGHT ? strike(track.rightStart, track.rightFrom, now) : strike(track.leftStart, track.leftFrom, now);
    }

    /**
     * A bright flash of magic on the arm as it strikes, fading over a few ticks.
     */
    public static float flare(Player player, HumanoidArm arm, double now) {
        Track track = track(player);
        double since = now - (arm == HumanoidArm.RIGHT ? track.rightStart : track.leftStart) - WIND_UP;
        if (since < -WIND_UP || since > 10.0) {
            return 0.0F;
        }
        return since < 0.0 ? (float) (1.0 + since / WIND_UP) * 0.6F : (float) Math.exp(-since / 2.2);
    }

    /**
     * When the arm's latest strike lets go of its magic, in game ticks.
     */
    public static double releaseTime(Player player, HumanoidArm arm) {
        Track track = track(player);
        return (arm == HumanoidArm.RIGHT ? track.rightStart : track.leftStart) + RELEASE;
    }

    /**
     * Ticks since either arm last began a strike.
     */
    public static double sinceLastStrike(Player player, double now) {
        Track track = track(player);
        return now - Math.max(track.rightStart, track.leftStart);
    }

    /**
     * Drops players that are gone, so the table only holds what is on screen.
     */
    public static void prune(ClientLevel level) {
        TRACKS.int2ObjectEntrySet().removeIf(entry -> level.getEntity(entry.getIntKey()) == null);
    }

    private static Track track(Player player) {
        Track track = TRACKS.computeIfAbsent(player.getId(), id -> new Track());
        MagicState state = Magic.state(player);
        if (track.seenCount == Integer.MIN_VALUE) {
            // first sight of this player: their past casts are not replayed
            track.seenCount = state.castCount();
        } else if (state.castCount() != track.seenCount) {
            HumanoidArm arm = Magic.castArm(player, state.castCount() - 1);
            boolean alreadyPredicted = arm == track.predictedArm && Math.abs(track.predictedAt - state.lastCastAt()) <= 4.0;
            if (!alreadyPredicted) {
                track.start(arm, state.lastCastAt());
            }
            track.seenCount = state.castCount();
            track.predictedArm = null;
        }
        return track;
    }

    private static float strike(double start, float from, double now) {
        float t = (float) (now - start);
        if (t < 0.0F || t >= RECOVER_END) {
            return 0.0F;
        }
        // still reaching from the last strike: just draw back a little before striking again
        float windUp = from > 0.3F ? from - 0.2F : -0.25F;
        if (t < WIND_UP) {
            return Ease.lerp(from, windUp, Ease.outQuad(t / WIND_UP));
        }
        if (t < STRIKE_END) {
            return Ease.lerp(windUp, 1.0F, Ease.outBack((t - WIND_UP) / (STRIKE_END - WIND_UP)));
        }
        if (t < HOLD_END) {
            return 1.0F;
        }
        return 1.0F - Ease.inOutCubic((t - HOLD_END) / (RECOVER_END - HOLD_END));
    }

    private static final class Track {
        double rightStart = -1.0E9;
        double leftStart = -1.0E9;
        float rightFrom;
        float leftFrom;
        int seenCount = Integer.MIN_VALUE;
        double predictedAt = -1.0E9;
        HumanoidArm predictedArm;

        void start(HumanoidArm arm, double at) {
            if (arm == HumanoidArm.RIGHT) {
                rightFrom = strike(rightStart, rightFrom, at);
                rightStart = at;
            } else {
                leftFrom = strike(leftStart, leftFrom, at);
                leftStart = at;
            }
        }
    }
}
