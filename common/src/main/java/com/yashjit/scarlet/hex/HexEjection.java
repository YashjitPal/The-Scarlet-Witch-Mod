package com.yashjit.scarlet.hex;

import com.yashjit.scarlet.magic.MindControl;
import com.yashjit.scarlet.network.EjectPayload;
import com.yashjit.scarlet.platform.Services;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A caster throwing someone out of their Hex, the way Wanda throws Monica out of Westview.
 *
 * <p>Held on anyone inside the Hex, the cast lifts them off the ground and holds them hanging there in scarlet light,
 * for a few seconds at most. Let go, and they are flung away from the caster in a long, fast arc, high over the town and
 * out through the wall, which bursts in a great ripple of red with a boom as they go through it, landing them on the
 * ground well outside. Nothing keeps them out: they can walk back in.
 *
 * <p>The throw follows one curve, worked out the same way everywhere: the server carries a creature along it, and a
 * player's own game carries them.
 */
public final class HexEjection {

    private static final double REACH = 32.0;
    /** How high off the ground they are held, and for how long at most before they are thrown anyway. */
    private static final double LIFT = 1.6;
    private static final int HOLD_TICKS = 60;
    /** How far past the wall they land. */
    private static final double BEYOND = 12.0;
    private static final float CROSSING_RIPPLE = 1.8F;

    private static final Map<UUID, Seize> SEIZED = new HashMap<>();
    /** Everything in flight, by entity id. */
    private static final Int2ObjectMap<Flight> FLIGHTS = new Int2ObjectOpenHashMap<>();

    private HexEjection() {
    }

    /**
     * Takes hold of whoever the caster aims at inside their own Hex, if anyone.
     *
     * @return whether they took hold of someone
     */
    public static boolean seize(ServerPlayer caster) {
        ServerLevel level = caster.level();
        Hex hex = HexData.of(level).byCaster(caster.getUUID());
        if (hex == null || hex.phase != Hex.Phase.STANDING && hex.phase != Hex.Phase.SPREADING) {
            return false;
        }
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(REACH));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        float wall = Hexes.wallRadius(hex, level.getGameTime());
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster, eye, limit, new AABB(eye, limit).inflate(1.0),
                candidate -> candidate instanceof LivingEntity living && canThrow(caster, living) && HexShape.contains(hex.center, wall, living.position()),
                eye.distanceToSqr(limit));
        if (hit == null) {
            return false;
        }
        LivingEntity target = (LivingEntity) hit.getEntity();
        long now = level.getGameTime();
        Vec3 from = target.position().add(0.0, LIFT, 0.0);
        SEIZED.put(caster.getUUID(), new Seize(target.getId(), now, from));
        FLIGHTS.remove(target.getId());
        announce(caster, target, new EjectPayload(caster.getId(), target.getId(), EjectPayload.SEIZED, from, from, 0));
        play(level, target.position(), SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.6F);
        play(level, target.position(), SoundEvents.BREEZE_CHARGE, 0.9F, 0.7F);
        return true;
    }

    /**
     * Every tick the caster keeps hold: whoever is held hangs in the air, turning slowly.
     *
     * @return false once the hold has ended by itself, and they should be thrown
     */
    public static boolean hold(ServerPlayer caster) {
        Seize seize = SEIZED.get(caster.getUUID());
        if (seize == null) {
            return false;
        }
        Entity target = caster.level().getEntity(seize.targetId);
        if (!(target instanceof LivingEntity living) || !living.isAlive()) {
            SEIZED.remove(caster.getUUID());
            return false;
        }
        long now = caster.level().getGameTime();
        if (!(living instanceof Player)) {
            // lifted up into place, then hanging, bobbing a little
            float rise = Math.min(1.0F, (now - seize.since) / 8.0F);
            Vec3 at = seize.from.add(0.0, (rise - 1.0) * LIFT + Math.sin((now - seize.since) * 0.15) * 0.08, 0.0);
            living.setPos(at.x, at.y, at.z);
            living.setDeltaMovement(Vec3.ZERO);
            living.syncVelocity = true;
            living.resetFallDistance();
        } else {
            living.resetFallDistance();
        }
        return now - seize.since < HOLD_TICKS;
    }

    /**
     * The caster lets go: whoever they held is thrown out through the wall.
     */
    public static void fling(ServerPlayer caster) {
        Seize seize = SEIZED.remove(caster.getUUID());
        if (seize == null) {
            return;
        }
        ServerLevel level = caster.level();
        Hex hex = HexData.of(level).byCaster(caster.getUUID());
        Entity target = level.getEntity(seize.targetId);
        if (!(target instanceof LivingEntity living) || !living.isAlive()) {
            return;
        }
        float wall = hex == null ? 0.0F : Hexes.wallRadius(hex, level.getGameTime());
        Vec3 from = living instanceof Player ? living.position() : seize.from;
        if (hex == null || !HexShape.contains(hex.center, wall, from)) {
            announce(caster, living, new EjectPayload(caster.getId(), living.getId(), EjectPayload.DROPPED, from, from, 0));
            return;
        }
        // away from the caster, out through whichever wall lies that way
        Vec3 away = new Vec3(from.x - caster.getX(), 0.0, from.z - caster.getZ());
        if (away.lengthSqr() < 1.0E-4) {
            away = caster.getLookAngle().multiply(1.0, 0.0, 1.0);
        }
        away = away.normalize();
        double exit = exitDistance(hex.center, wall, from, away);
        Vec3 landing = ground(level, from.add(away.scale(exit + BEYOND)));
        double distance = Math.sqrt(landing.subtract(from).horizontalDistanceSqr());
        int ticks = Mth.clamp((int) Math.round(distance / 1.5), 16, 40);
        Vec3 crossing = from.add(away.scale(exit));
        FLIGHTS.put(living.getId(), new Flight(level, from, landing, level.getGameTime(), ticks, (float) Mth.clamp(exit / distance, 0.0, 1.0),
                crossing, living instanceof Player));
        announce(caster, living, new EjectPayload(caster.getId(), living.getId(), EjectPayload.FLUNG, from, landing, ticks));
        play(level, from, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.4F, 0.6F);
        play(level, from, SoundEvents.WARDEN_SONIC_CHARGE, 0.8F, 1.6F);
    }

    /**
     * Every tick: everything in flight is carried along its throw, and the wall bursts where it goes through.
     */
    public static void tick(ServerLevel level) {
        if (FLIGHTS.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        var iterator = FLIGHTS.int2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Flight flight = entry.getValue();
            if (flight.level != level) {
                continue;
            }
            Entity entity = level.getEntity(entry.getIntKey());
            float t = (now - flight.start) / (float) flight.ticks;
            if (entity == null || !entity.isAlive() || t > 1.4F) {
                iterator.remove();
                continue;
            }
            if (!flight.crossed && throwProgress(t) >= flight.crossingAt) {
                flight.crossed = true;
                Vec3 at = flight.crossing.add(0.0, height(flight, flight.crossingAt) - flight.crossing.y, 0.0);
                HexRipples.ripple(level, at, CROSSING_RIPPLE);
                play(level, at, SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.55F);
                play(level, at, SoundEvents.WARDEN_SONIC_BOOM, 1.4F, 0.8F);
                play(level, at, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1.6F, 0.6F);
            }
            entity.resetFallDistance();
            if (flight.player) {
                // their own game carries them; they only need sparing the landing
                if (t >= 1.0F) {
                    iterator.remove();
                }
                continue;
            }
            if (t >= 1.0F) {
                entity.noPhysics = false;
                Vec3 skid = flight.to.subtract(flight.from).multiply(1.0, 0.0, 1.0).normalize().scale(0.35);
                entity.setPos(flight.to.x, flight.to.y, flight.to.z);
                entity.setDeltaMovement(skid.x, 0.1, skid.z);
                entity.syncVelocity = true;
                iterator.remove();
                continue;
            }
            Vec3 at = position(flight.from, flight.to, t);
            Vec3 next = position(flight.from, flight.to, Math.min(1.0F, t + 1.0F / flight.ticks));
            entity.noPhysics = true;
            entity.setPos(at.x, at.y, at.z);
            entity.setDeltaMovement(next.subtract(at));
            entity.syncVelocity = true;
        }
    }

    /**
     * Whether a creature is held or in flight, so it does nothing of its own accord meanwhile.
     */
    public static boolean carries(Mob mob) {
        if (FLIGHTS.containsKey(mob.getId())) {
            return true;
        }
        for (Seize seize : SEIZED.values()) {
            if (seize.targetId == mob.getId()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Where a throw from {@code from} to {@code to} has got at {@code t}, from 0 to 1: away across the ground fast at
     * first and slower as it comes down, and up in a high arc over the town between.
     */
    public static Vec3 position(Vec3 from, Vec3 to, float t) {
        float u = Math.clamp(t, 0.0F, 1.0F);
        float across = throwProgress(u);
        double x = Mth.lerp(across, from.x, to.x);
        double z = Mth.lerp(across, from.z, to.z);
        return new Vec3(x, height(from, to, u), z);
    }

    /**
     * How far across the ground a throw has got: fast off the mark, easing as it lands.
     */
    public static float throwProgress(float t) {
        float u = Math.clamp(t, 0.0F, 1.0F);
        return 1.0F - (1.0F - u) * (1.0F - u);
    }

    private static double height(Flight flight, float across) {
        // the height at the moment it has got this far across, solving the ease backwards
        float u = 1.0F - (float) Math.sqrt(Math.max(0.0F, 1.0F - across));
        return height(flight.from, flight.to, u);
    }

    private static double height(Vec3 from, Vec3 to, float u) {
        double distance = Math.sqrt(to.subtract(from).horizontalDistanceSqr());
        double apex = Math.max(from.y, to.y) + Math.max(7.0, distance * 0.22);
        // a quadratic arc through the apex, with its control point set so it peaks there
        double control = 2.0 * apex - 0.5 * (from.y + to.y);
        return (1.0 - u) * (1.0 - u) * from.y + 2.0 * u * (1.0 - u) * control + u * u * to.y;
    }

    private static boolean canThrow(ServerPlayer caster, LivingEntity living) {
        if (!living.isAlive() || living == caster || living.isSpectator() || living.typeHolder().is(MindControl.RESISTS) || FLIGHTS.containsKey(living.getId())) {
            return false;
        }
        for (Seize seize : SEIZED.values()) {
            if (seize.targetId == living.getId()) {
                return false;
            }
        }
        return !(living instanceof Player other) || !other.isCreative();
    }

    /**
     * How far from {@code from} along {@code direction} the wall is.
     */
    private static double exitDistance(Vec3 center, float wall, Vec3 from, Vec3 direction) {
        double inside = 0.0;
        double outside = HexShape.reach(wall) * 2.0 + 1.0;
        for (int i = 0; i < 24; i++) {
            double middle = (inside + outside) * 0.5;
            if (HexShape.contains(center, wall, from.add(direction.scale(middle)))) {
                inside = middle;
            } else {
                outside = middle;
            }
        }
        return outside;
    }

    private static Vec3 ground(ServerLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        return new Vec3(at.x, top, at.z);
    }

    private static void announce(ServerPlayer caster, LivingEntity target, EjectPayload payload) {
        Services.NETWORK.sendToTrackingAndSelf(caster, payload);
        if (target instanceof ServerPlayer thrown && thrown != caster) {
            Services.NETWORK.sendToPlayer(thrown, payload);
        }
    }

    private static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private record Seize(int targetId, long since, Vec3 from) {
    }

    private static final class Flight {
        final ServerLevel level;
        final Vec3 from;
        final Vec3 to;
        final long start;
        final int ticks;
        /** How far across the ground the throw has got as it goes through the wall. */
        final float crossingAt;
        final Vec3 crossing;
        final boolean player;
        boolean crossed;

        Flight(ServerLevel level, Vec3 from, Vec3 to, long start, int ticks, float crossingAt, Vec3 crossing, boolean player) {
            this.level = level;
            this.from = from;
            this.to = to;
            this.start = start;
            this.ticks = ticks;
            this.crossingAt = crossingAt;
            this.crossing = crossing;
            this.player = player;
        }
    }
}
