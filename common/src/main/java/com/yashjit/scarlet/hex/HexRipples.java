package com.yashjit.scarlet.hex;

import com.yashjit.scarlet.network.HexRipplePayload;
import com.yashjit.scarlet.platform.Services;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where things meet a Hex wall. A chaos blast bursts on it and sets it rippling red; anyone walking through it sends a
 * gentler ripple across it.
 */
public final class HexRipples {

    /** Strength of the ripple a chaos blast makes. */
    public static final float BLAST = 1.0F;
    private static final float PASSING = 0.3F;
    private static final double HEARD_FROM = 256.0;

    private static final Map<ServerPlayer, Vec3> LAST = new WeakHashMap<>();

    private HexRipples() {
    }

    /**
     * Where the path from {@code from} to {@code to} goes through the wall of a Hex standing in the level, if it does.
     */
    public static @Nullable Vec3 crossing(ServerLevel level, Vec3 from, Vec3 to) {
        long now = level.getGameTime();
        for (Hex hex : HexData.of(level).all()) {
            Vec3 at = crossing(hex.center, Hexes.wallRadius(hex, now), from, to);
            if (at != null) {
                return at;
            }
        }
        return null;
    }

    /**
     * Where the path from {@code from} to {@code to} goes through the wall of a Hex of radius {@code wall} around
     * {@code center}, if it does.
     */
    public static @Nullable Vec3 crossing(Vec3 center, float wall, Vec3 from, Vec3 to) {
        if (wall < 1.0F) {
            return null;
        }
        boolean startsInside = HexShape.contains(center, wall, from);
        if (startsInside == HexShape.contains(center, wall, to)) {
            return null;
        }
        // the wall is flat, so halving the step closes on it quickly
        Vec3 inside = startsInside ? from : to;
        Vec3 outside = startsInside ? to : from;
        for (int i = 0; i < 10; i++) {
            Vec3 middle = inside.add(outside).scale(0.5);
            if (HexShape.contains(center, wall, middle)) {
                inside = middle;
            } else {
                outside = middle;
            }
        }
        return inside.add(outside).scale(0.5);
    }

    /**
     * Sets the wall rippling for everyone near enough to see it.
     */
    public static void ripple(ServerLevel level, Vec3 at, float strength) {
        HexRipplePayload payload = new HexRipplePayload(at, strength);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(at) < HEARD_FROM * HEARD_FROM) {
                Services.NETWORK.sendToPlayer(player, payload);
            }
        }
    }

    /**
     * Every tick for each player: a ripple where they step through a wall.
     */
    public static void watch(ServerPlayer player) {
        Vec3 now = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
        Vec3 before = LAST.put(player, now);
        if (before == null || before.distanceToSqr(now) < 1.0E-6 || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.level();
        Vec3 at = crossing(level, before, now);
        if (at != null) {
            ripple(level, at, PASSING);
        }
    }
}
