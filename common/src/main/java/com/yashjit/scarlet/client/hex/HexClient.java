package com.yashjit.scarlet.client.hex;

import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.hex.Hex;
import com.yashjit.scarlet.hex.HexShape;
import com.yashjit.scarlet.hex.HexSnapshot;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.network.HexSyncPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Hexes this client can see, and how far each wall has got: easing out as it spreads, following its caster's
 * resizing smoothly, and rushing inward as it falls.
 */
public final class HexClient {

    /** How long the caster's arms stay thrown wide after casting, in ticks. */
    public static final float BURST_TICKS = 30.0F;
    /** How long the static of an era change lasts, in ticks. */
    private static final float CHANNEL_TICKS = 16.0F;

    private static final Map<UUID, Wall> WALLS = new HashMap<>();
    private static final Map<UUID, Double> BURSTS = new HashMap<>();
    private static @Nullable ClientLevel seenLevel;
    private static long lastNanos;

    private HexClient() {
    }

    public static void receive(HexSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        // what is sent on arriving in a level can come before the first tick there, and must not be forgotten by it
        forgetIfLeft(minecraft.level);
        Map<UUID, HexSnapshot> before = new HashMap<>();
        for (HexSnapshot hex : Hexes.clientHexes()) {
            before.put(hex.caster(), hex);
        }
        Hexes.receive(payload);
        if (minecraft.level == null) {
            return;
        }
        double now = minecraft.level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (HexSnapshot hex : payload.hexes()) {
            HexSnapshot old = before.get(hex.caster());
            boolean entered = old == null || old.phase() != hex.phase();
            boolean bursting = hex.phaseValue() == Hex.Phase.FOUNDING || hex.phaseValue() == Hex.Phase.SPREADING;
            if (entered && bursting && now - hex.phaseSince() < 10) {
                BURSTS.put(hex.caster(), (double) hex.phaseSince());
            }
        }
    }

    public static void tick(Minecraft minecraft) {
        forgetIfLeft(minecraft.level);
        if (minecraft.level != null) {
            double now = minecraft.level.getGameTime();
            BURSTS.values().removeIf(at -> now - at > BURST_TICKS + 5);
        }
    }

    private static void forgetIfLeft(@Nullable ClientLevel level) {
        if (level != seenLevel) {
            seenLevel = level;
            Hexes.clearClient();
            WALLS.clear();
            BURSTS.clear();
        }
    }

    /**
     * How far a player is through the arms-wide burst of casting a Hex: 0 before and after, peaking at 1.
     */
    public static float burst(Player player, double now) {
        Double at = BURSTS.get(player.getUUID());
        if (at == null) {
            return 0.0F;
        }
        float t = (float) (now - at);
        if (t < 0.0F || t > BURST_TICKS) {
            return 0.0F;
        }
        float rise = Ease.outBack(Ease.clamp01(t / 5.0F));
        float fall = 1.0F - Ease.inOutCubic(Ease.clamp01((t - 12.0F) / (BURST_TICKS - 12.0F)));
        return rise * fall;
    }

    /**
     * The Hexes worth drawing, nearest first.
     *
     * @param reach how far past its wall a Hex can be seen from
     */
    public static List<Shown> shown(Vec3 camera, double now, double reach, int max) {
        long nanos = System.nanoTime();
        float seconds = lastNanos == 0 ? 0.0F : Math.min(0.1F, (nanos - lastNanos) / 1.0E9F);
        lastNanos = nanos;
        List<Shown> shown = new ArrayList<>();
        for (HexSnapshot hex : Hexes.clientHexes()) {
            Wall wall = WALLS.computeIfAbsent(hex.caster(), id -> new Wall(hex.radius()));
            float before = wall.radius;
            float radius = radius(hex, wall, now, seconds);
            if (radius < 0.05F) {
                continue;
            }
            double distance = camera.distanceTo(hex.center());
            if (distance - HexShape.extent(radius) > reach) {
                continue;
            }
            float flare = flare(hex, now, Math.abs(wall.radius - before) / Math.max(seconds, 1.0E-3F));
            float warning = hex.phaseValue() == Hex.Phase.WARNING ? Ease.clamp01((float) (now - hex.phaseSince()) / Hexes.WARNING_TICKS) : 0.0F;
            float sinceEra = (float) (now - hex.eraSince());
            float channel = hex.eraSince() > 0 && sinceEra >= 0.0F && sinceEra < CHANNEL_TICKS
                    ? (float) Math.pow(1.0F - sinceEra / CHANNEL_TICKS, 1.5) : 0.0F;
            shown.add(new Shown(hex.center(), radius, hex.eraValue(), flare, warning, channel, distance));
        }
        WALLS.keySet().removeIf(id -> Hexes.clientHexes().stream().noneMatch(hex -> hex.caster().equals(id)));
        shown.sort(Comparator.comparingDouble(Shown::distance));
        return shown.size() > max ? shown.subList(0, max) : shown;
    }

    private static float radius(HexSnapshot hex, Wall wall, double now, float seconds) {
        float t = (float) (now - hex.phaseSince());
        switch (hex.phaseValue()) {
            case FOUNDING -> wall.radius = 0.0F;
            case SPREADING -> wall.radius = hex.radius() * Ease.outCubic(Ease.clamp01(t / Hexes.SPREAD_TICKS));
            case COLLAPSING -> wall.radius = hex.phaseRadius() * (1.0F - Ease.inCubic(Ease.clamp01(t / Hexes.COLLAPSE_TICKS)));
            default -> wall.radius = Ease.damp(wall.radius, hex.radius(), 6.0F, seconds);
        }
        return wall.radius;
    }

    /**
     * How brightly the wall's front burns: as it spreads and as it falls, and while it is being resized.
     *
     * @param speed blocks per second the wall is moving
     */
    private static float flare(HexSnapshot hex, double now, float speed) {
        float t = (float) (now - hex.phaseSince());
        return switch (hex.phaseValue()) {
            case FOUNDING -> 0.0F;
            case SPREADING -> 1.0F - Ease.clamp01(t / Hexes.SPREAD_TICKS) * 0.8F;
            case COLLAPSING -> 0.6F + 0.4F * Ease.clamp01(t / Hexes.COLLAPSE_TICKS);
            default -> Math.min(0.6F, speed / 12.0F);
        };
    }

    /**
     * @param center   world position
     * @param channel  the static of an era change, 1 the moment it changes and fading to 0
     * @param distance from the camera to the center
     */
    public record Shown(Vec3 center, float radius, Era era, float flare, float warning, float channel, double distance) {
    }

    private static final class Wall {
        float radius;

        Wall(float radius) {
            this.radius = radius;
        }
    }
}
