package com.yashjit.scarlet.client.hex;

import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.hex.Hex;
import com.yashjit.scarlet.hex.HexShape;
import com.yashjit.scarlet.hex.HexSnapshot;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.network.HexRipplePayload;
import com.yashjit.scarlet.network.HexSyncPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Util;
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
    private static final float CHANNEL_TICKS = 8.0F;
    private static final float CHANNEL_STRENGTH = 0.45F;

    /** How long a wall goes on rippling where it was struck, in ticks. */
    private static final double RIPPLE_TICKS = 70.0;

    /** How quickly a wall's burning comes on, and how much more slowly it dies away, as rates for {@link Ease#damp}. */
    private static final float FLARE_RISE = 10.0F;
    private static final float FLARE_FALL = 4.0F;

    private static final Map<UUID, Wall> WALLS = new HashMap<>();
    private static final Map<UUID, Double> BURSTS = new HashMap<>();
    private static final List<Ripple> RIPPLES = new ArrayList<>();
    private static @Nullable ClientLevel seenLevel;
    private static long lastNanos;

    private HexClient() {
    }

    public static void ripple(HexRipplePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        RIPPLES.add(new Ripple(payload.at(), minecraft.level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false),
                payload.strength()));        if (RIPPLES.size() > HexScreen.MAX_RIPPLES) {
            RIPPLES.removeFirst();
        }
    }

    /**
     * The blows the walls are still rippling from, oldest first.
     */
    public static List<Ripple> ripples(double now) {
        RIPPLES.removeIf(ripple -> now - ripple.start() > RIPPLE_TICKS);
        return RIPPLES;
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
            RIPPLES.clear();
        }
    }

    /**
     * Flings a player's arms wide, as casting a Hex does: throwing someone out of it.
     */
    public static void flingFrom(Player player, double at) {
        BURSTS.put(player.getUUID(), at);
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
        long nanos = Util.getNanos();
        float seconds = lastNanos == 0 ? 0.0F : Math.min(0.1F, (nanos - lastNanos) / 1.0E9F);
        lastNanos = nanos;
        List<Shown> shown = new ArrayList<>();
        for (HexSnapshot hex : Hexes.clientHexes()) {
            Wall wall = WALLS.computeIfAbsent(hex.caster(), id -> new Wall(hex.radius()));
            float radius = radius(hex, wall, now);
            float flare = flare(hex, now, wall.speed(now), !HexShape.contains(hex.center(), radius, camera));
            wall.flare = Ease.damp(wall.flare, flare, flare > wall.flare ? FLARE_RISE : FLARE_FALL, seconds);
            if (radius < 0.05F) {
                continue;
            }
            double distance = Math.hypot(camera.x - hex.center().x, camera.z - hex.center().z);
            if (distance - HexShape.reach(radius) > reach) {
                continue;
            }
            float warning = hex.phaseValue() == Hex.Phase.WARNING ? Ease.clamp01((float) (now - hex.phaseSince()) / Hexes.WARNING_TICKS) : 0.0F;
            float sinceEra = (float) (now - hex.eraSince());
            // the picture only flickers as the era changes; the new one then spreads out over the Hex from the middle
            float channel = hex.eraSince() > 0 && sinceEra >= 0.0F && sinceEra < CHANNEL_TICKS
                    ? CHANNEL_STRENGTH * (float) Math.pow(1.0F - sinceEra / CHANNEL_TICKS, 1.5) : 0.0F;
            float eraFront = hex.previousEraValue() != hex.eraValue() ? Hexes.eraFront(hex.radius(), now - hex.eraSince()) : Float.MAX_VALUE;
            wall.opening = hex.parted() ? Ease.damp(wall.opening, hex.opening(), 12.0F, seconds) : 0.0F;
            shown.add(new Shown(hex.center(), radius, hex.eraValue(), hex.previousEraValue(), eraFront, wall.flare, warning, channel, distance,
                    hex.tearAt(), hex.parted() ? wall.opening : -1.0F));
        }
        WALLS.keySet().removeIf(id -> Hexes.clientHexes().stream().noneMatch(hex -> hex.caster().equals(id)));
        shown.sort(Comparator.comparingDouble(Shown::distance));
        return shown.size() > max ? shown.subList(0, max) : shown;
    }

    private static float radius(HexSnapshot hex, Wall wall, double now) {
        // the size glides, so the wall keeps pace with the town the server is cutting back or building out behind it
        float size = wall.size(hex.radius(), now);
        float t = (float) (now - hex.phaseSince());
        return switch (hex.phaseValue()) {
            case FOUNDING -> 0.0F;
            case SPREADING -> size * Ease.outCubic(Ease.clamp01(t / Hexes.SPREAD_TICKS));
            case COLLAPSING -> hex.phaseRadius() * (1.0F - Hexes.fallen(t / Hexes.collapseTicks(hex.phaseRadius())));
            default -> size;
        };
    }

    /**
     * How far out a Hex's wall stands as drawn.
     */
    public static float drawnRadius(HexSnapshot hex, double now) {
        Wall wall = WALLS.get(hex.caster());
        return wall != null ? radius(hex, wall, now) : Hexes.wallRadius(hex, now);
    }

    /**
     * How brightly the wall's front should burn: as it spreads and as it falls, while it is being resized, and, seen from
     * outside, while its caster winds it back. Inside, the picture itself shows the rewind.
     *
     * @param speed   blocks a tick the wall is gliding at
     * @param outside whether it is seen from outside
     */
    private static float flare(HexSnapshot hex, double now, float speed, boolean outside) {
        float t = (float) (now - hex.phaseSince());
        return switch (hex.phaseValue()) {
            case FOUNDING -> 0.0F;
            case SPREADING -> 1.0F - Ease.clamp01(t / Hexes.SPREAD_TICKS) * 0.8F;
            case COLLAPSING -> 0.6F + 0.4F * Ease.clamp01(t / Hexes.collapseTicks(hex.phaseRadius()));
            default -> Math.max(hex.rewinding() && outside ? 0.95F : 0.0F, 0.6F * Math.min(1.0F, speed / (Hexes.RESIZE_SPEED * Hexes.SHRINK_SHARE)));
        };
    }

    /**
     * How unsteady a Hex is at a moment: parted, or shaken by a blow to its caster.
     */
    public static float unrest(HexSnapshot hex, double now) {
        return hex.unrest(now);
    }

    /**
     * @param center      world position
     * @param previousEra the era before, still showing past {@code eraFront}
     * @param eraFront    how far out from the middle the era has spread since it changed, as {@link HexShape#level} measures;
     *                    {@link Float#MAX_VALUE} once it has spread everywhere
     * @param channel     the static of an era change, fading to 0 from the moment it changes
     * @param distance across the ground from the camera to the center
     * @param tearAt   where its caster took hold of its wall to part it, if they did
     * @param opening  how far each edge of the opening stands from there, in blocks, or less than 0 for none
     */
    public record Shown(Vec3 center, float radius, Era era, Era previousEra, float eraFront, float flare, float warning, float channel,
                        double distance, @Nullable Vec3 tearAt, float opening) {

        public boolean changingEra() {
            return previousEra != era && eraFront < Float.MAX_VALUE;
        }
    }

    /**
     * How far each edge of a caster's opening stands from its middle as drawn, easing after the true width: in blocks,
     * or less than 0 for none.
     */
    public static float opening(HexSnapshot hex) {
        Wall wall = WALLS.get(hex.caster());
        return !hex.parted() ? -1.0F : wall != null ? wall.opening : hex.opening();
    }

    /**
     * @param strength 1 for a chaos blast, less for someone passing through
     */
    public record Ripple(Vec3 at, double start, float strength) {
    }

    private static final class Wall {
        float opening;
        /** How brightly its front burns as drawn, easing after how brightly it should. */
        float flare;
        /** The size it is gliding to, from what, since when and over how long. */
        float target;
        float from;
        double since;
        float span = 1.0F;

        Wall(float size) {
            this.target = size;
            this.from = size;
        }

        /**
         * The Hex's size as drawn, gliding on from wherever it has got to each time a new one comes. Each glide
         * outlasts the wait for the next size, so a wall being resized moves on steadily, never stopping between them.
         */
        float size(float size, double now) {
            if (size != target) {
                from = at(now);
                target = size;
                since = now;
                span = Math.max(Hexes.GLIDE_TICKS, Math.abs(target - from) / 3.0F);
            }
            return at(now);
        }

        /** Blocks a tick it is gliding at. */
        float speed(double now) {
            return now - since < span ? Math.abs(target - from) / span : 0.0F;
        }

        private float at(double now) {
            return from + (target - from) * Ease.clamp01((float) ((now - since) / span));
        }
    }
}
