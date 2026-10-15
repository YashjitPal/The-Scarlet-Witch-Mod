package com.yashjit.scarlet.hex;

import com.yashjit.scarlet.hex.town.HexTown;
import com.yashjit.scarlet.hex.town.Terrain;
import com.yashjit.scarlet.magic.MindControl;
import com.yashjit.scarlet.network.EjectPayload;
import com.yashjit.scarlet.platform.Services;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
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
 * for a few seconds at most. Let go, and they are blasted away from the caster, flying backward with their arms and legs
 * thrown out before them, fast but never too fast to follow: low through the town, crashing through whatever stands in
 * their way, up over the hills between, and out through the wall, which bursts in a great ripple of red with a boom as
 * they go through it. Outside they come down hard, skid and roll over along the ground, and lie there winded a moment
 * before they get up. Nothing keeps them out: they can walk back in.
 *
 * <p>Whatever stands in their way inside the wall, they crash straight through: it bursts open around them in a crater as
 * wide as a creeper's blast, and the Hex mends it the way it mends any blast. Outside the wall they break nothing.
 *
 * <p>The throw follows one path, worked out the same way everywhere: the server carries a creature along it, and a
 * player's own game carries them.
 */
public final class HexEjection {

    private static final double REACH = 32.0;
    /** How high off the ground they are held, and for how long at most before they are thrown anyway. */
    private static final double LIFT = 1.6;
    private static final int HOLD_TICKS = 60;
    /** How far past the wall they come down. */
    private static final double BEYOND = 10.0;
    /** How fast they fly on average, in blocks a tick, and the longest and the shortest a throw takes however far it goes. */
    private static final double SPEED = 1.0;
    private static final int LONGEST = 150;
    private static final int SHORTEST = 30;
    /** How much faster than that they go off the mark, slowing through the air as they go. */
    private static final float LAUNCH = 1.25F;
    /** How far over where they start the throw rises at least and at most, and how far over the land it keeps. */
    private static final double LEAST_RISE = 2.0;
    private static final double MOST_RISE = 40.0;
    private static final double CLEARANCE = 1.4;
    /** Ticks they skid along the ground once down, lie there winded, and take to get up; and furthest they skid. */
    public static final int SKID = 14;
    public static final int DOWN = 30;
    public static final int RISE = 12;
    private static final double LONGEST_SKID = 8.0;
    /** How hard the ground hits them: a player is always left with a heart. */
    private static final float LANDING_DAMAGE = 4.0F;
    private static final float CROSSING_RIPPLE = 1.8F;
    /** How far around a body crashing through something it bursts open, how often along its way it looks, and how many
     * things one throw can crash through. */
    private static final double CRASH_RADIUS = 2.6;
    private static final double CRASH_STEP = 0.4;
    private static final int CRASHES = 20;

    private static final Map<UUID, Seize> SEIZED = new HashMap<>();
    /** Everything thrown, in the air or down, by entity id. */
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
        announce(caster, target, EjectPayload.at(caster.getId(), target.getId(), EjectPayload.SEIZED, from));
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
            announce(caster, living, EjectPayload.at(caster.getId(), living.getId(), EjectPayload.DROPPED, from));
            return;
        }
        // away from the caster, out through whichever wall lies that way
        Vec3 away = new Vec3(from.x - caster.getX(), 0.0, from.z - caster.getZ());
        if (away.lengthSqr() < 1.0E-4) {
            away = caster.getLookAngle().multiply(1.0, 0.0, 1.0);
        }
        away = away.normalize();
        double exit = exitDistance(hex.center, wall, from, away);
        double fallback = from.y - LIFT;
        Vec3 landing = ground(level, from.add(away.scale(exit + BEYOND)), fallback);
        double distance = Math.sqrt(landing.subtract(from).horizontalDistanceSqr());
        int ticks = Mth.clamp((int) Math.round(distance / SPEED), SHORTEST, LONGEST);
        double control = control(level, hex, from, landing, distance);
        // they come down still going, and skid on until the ground takes it out of them
        double skid = Math.min(LONGEST_SKID, (2.0F - LAUNCH) * distance / ticks * SKID / 2.0);
        Vec3 rest = skidTo(level, landing, away, skid);
        Vec3 crossing = from.add(away.scale(exit));
        FLIGHTS.put(living.getId(), new Flight(level, from, landing, rest, control, level.getGameTime(), ticks,
                (float) Mth.clamp(exit / distance, 0.0, 1.0), crossing, living instanceof Player));
        announce(caster, living, new EjectPayload(caster.getId(), living.getId(), EjectPayload.FLUNG, from, landing, rest, control, ticks));
        play(level, from, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.4F, 0.6F);
        play(level, from, SoundEvents.WARDEN_SONIC_CHARGE, 0.8F, 1.6F);
    }

    /**
     * Every tick: everything thrown is carried along its throw, and the wall bursts where it goes through; down on the
     * ground, they lie winded until they get up.
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
            float elapsed = now - flight.start;
            if (entity == null || !entity.isAlive() || elapsed > flight.ticks + SKID + DOWN + RISE) {
                if (entity != null) {
                    entity.noPhysics = false;
                }
                iterator.remove();
                continue;
            }
            float t = elapsed / flight.ticks;
            if (!flight.crossed && progress(t) >= flight.crossingAt) {
                flight.crossed = true;
                Vec3 at = flight.crossing.add(0.0, heightAt(flight.from, flight.to, flight.control, flight.crossingAt) - flight.crossing.y, 0.0);
                HexRipples.ripple(level, at, CROSSING_RIPPLE);
                play(level, at, SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.55F);
                play(level, at, SoundEvents.WARDEN_SONIC_BOOM, 1.4F, 0.8F);
                play(level, at, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1.6F, 0.6F);
            }
            entity.resetFallDistance();
            if (t < 1.0F) {
                crashThrough(level, flight, entity, t);
            } else if (!flight.landed) {
                flight.landed = true;
                land(level, flight, entity);
            }
            if (flight.player) {
                // their own game carries them
                continue;
            }
            if (elapsed <= flight.ticks + SKID) {
                Vec3 at = position(level, flight.from, flight.to, flight.rest, flight.control, flight.ticks, elapsed);
                Vec3 next = position(level, flight.from, flight.to, flight.rest, flight.control, flight.ticks, elapsed + 1.0F);
                entity.noPhysics = elapsed < flight.ticks;
                entity.setPos(at.x, at.y, at.z);
                entity.setDeltaMovement(next.subtract(at));
            } else {
                // winded on the ground, then getting up
                entity.noPhysics = false;
                entity.setPos(flight.rest.x, flight.rest.y, flight.rest.z);
                entity.setDeltaMovement(Vec3.ZERO);
            }
            entity.syncVelocity = true;
        }
    }

    /**
     * Coming down: a thud and a burst of whatever they land on, and the ground knocking the wind out of them.
     */
    private static void land(ServerLevel level, Flight flight, Entity entity) {
        Vec3 at = flight.to;
        BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1, at.z, 40, 0.6, 0.1, 0.6, 0.2);
        }
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.3, at.z, 10, 0.5, 0.1, 0.5, 0.05);
        play(level, at, SoundEvents.PLAYER_BIG_FALL, 1.3F, 0.8F);
        play(level, at, ground.getSoundType().getBreakSound(), 1.0F, 0.7F);
        if (entity instanceof LivingEntity living) {
            float hurt = living instanceof Player ? Math.min(LANDING_DAMAGE, living.getHealth() - 2.0F) : LANDING_DAMAGE;
            if (hurt > 0.0F) {
                living.hurtServer(level, level.damageSources().fall(), hurt);
            }
        }
    }

    /**
     * Bursts open whatever stands in the way of a body over its last tick of flight, while it is still inside the wall.
     * The way is worked out along the throw's own path, so a player, whose own game carries them, crashes through the
     * same things as a creature.
     */
    private static void crashThrough(ServerLevel level, Flight flight, Entity entity, float t) {
        if (flight.crashes >= CRASHES) {
            return;
        }
        float elapsed = t * flight.ticks;
        Vec3 from = position(level, flight.from, flight.to, flight.rest, flight.control, flight.ticks, Math.max(0.0F, elapsed - 1.0F));
        Vec3 to = position(level, flight.from, flight.to, flight.rest, flight.control, flight.ticks, elapsed);
        int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to) / CRASH_STEP));
        double half = entity.getBbWidth() * 0.5;
        double height = entity.getBbHeight();
        for (int i = 1; i <= steps; i++) {
            Vec3 at = from.lerp(to, i / (double) steps);
            if (!HexMending.inside(level, at)) {
                return;
            }
            // a hair smaller than the body, so the ground it lands on doesn't count as in its way
            AABB body = new AABB(at.x - half, at.y, at.z - half, at.x + half, at.y + height, at.z + half).deflate(0.05);
            if (blocked(level, body)) {
                crash(level, at.add(0.0, height * 0.5, 0.0));
                if (++flight.crashes >= CRASHES) {
                    return;
                }
            }
        }
    }

    private static boolean blocked(ServerLevel level, AABB body) {
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(body.minX, body.minY, body.minZ), BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && breakable(level, pos, state) && !state.getCollisionShape(level, pos).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * What a blast would break: not bedrock, obsidian or anything else nothing breaks.
     */
    private static boolean breakable(ServerLevel level, BlockPos pos, BlockState state) {
        return state.getDestroySpeed(level, pos) >= 0.0F && state.getBlock().getExplosionResistance() < 1200.0F;
    }

    /**
     * A body crashing through: everything breakable in a ragged ball around it bursts, and the Hex takes it whole to mend.
     */
    private static void crash(ServerLevel level, Vec3 center) {
        List<BlockPos> burst = new ArrayList<>();
        int reach = (int) Math.ceil(CRASH_RADIUS);
        BlockPos middle = BlockPos.containing(center);
        RandomSource random = level.getRandom();
        for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-reach, -reach, -reach), middle.offset(reach, reach, reach))) {
            double radius = CRASH_RADIUS * (0.7 + 0.3 * random.nextDouble());
            BlockState state = level.getBlockState(pos);
            if (Vec3.atCenterOf(pos).distanceToSqr(center) <= radius * radius && !state.isAir() && breakable(level, pos, state)) {
                burst.add(pos.immutable());
            }
        }
        int debris = 0;
        for (BlockPos pos : burst) {
            if (debris < 12 && HexMending.inside(level, Vec3.atCenterOf(pos))) {
                level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(level.getBlockState(pos)));
                debris++;
            }
        }
        // what lies inside the wall goes, to come back; what lies outside it stays where it is
        HexMending.blast(level, center, burst);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.0F, 0.85F);
    }

    /**
     * Whether a creature is held or thrown, so it does nothing of its own accord meanwhile.
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
     * Where a throw has got {@code elapsed} ticks in: along its arc through the air, then skidding along the ground to
     * where it comes to rest, and there after that.
     */
    public static Vec3 position(Level level, Vec3 from, Vec3 to, Vec3 rest, double control, int ticks, float elapsed) {
        if (elapsed <= ticks) {
            float s = progress(elapsed / ticks);
            return new Vec3(Mth.lerp(s, from.x, to.x), heightAt(from, to, control, s), Mth.lerp(s, from.z, to.z));
        }
        float k = Math.min(1.0F, (elapsed - ticks) / SKID);
        float slid = 1.0F - (1.0F - k) * (1.0F - k);
        double x = Mth.lerp(slid, to.x, rest.x);
        double z = Mth.lerp(slid, to.z, rest.z);
        return new Vec3(x, groundUnder(level, x, z, Mth.lerp(slid, to.y, rest.y)), z);
    }

    /**
     * How far along the way across a throw has got, from 0 to 1, a share {@code t} of the way through its time in the
     * air: fast off the mark, slowing through the air.
     */
    public static float progress(float t) {
        float u = Math.clamp(t, 0.0F, 1.0F);
        return LAUNCH * u - (LAUNCH - 1.0F) * u * u;
    }

    /** How high the throw's arc is a share {@code s} of the way across. */
    private static double heightAt(Vec3 from, Vec3 to, double control, float s) {
        return (1.0 - s) * (1.0 - s) * from.y + 2.0 * s * (1.0 - s) * control + s * s * to.y;
    }

    /**
     * The height of the arc's control point: as low as keeps them clear of the land along the way, and a little over
     * where they start; the town's buildings it takes no account of, as they crash through those.
     */
    private static double control(ServerLevel level, Hex hex, Vec3 from, Vec3 to, double distance) {
        double highest = Math.max(from.y, to.y);
        double control = 2.0 * (highest + LEAST_RISE) - 0.5 * (from.y + to.y);
        double most = 2.0 * (highest + MOST_RISE) - 0.5 * (from.y + to.y);
        int samples = Math.max(2, (int) Math.ceil(distance / 2.0));
        for (int i = 1; i < samples; i++) {
            float s = i / (float) samples;
            if (s < 0.04F || s > 0.96F) {
                continue;
            }
            double x = Mth.lerp(s, from.x, to.x);
            double z = Mth.lerp(s, from.z, to.z);
            // clear of the land by a little, coming down to it at the end
            double clearance = CLEARANCE * Math.min(1.0, (1.0 - s) * distance / 8.0);
            double need = landUnder(level, hex, x, z, Double.NaN) + clearance;
            if (Double.isNaN(need)) {
                continue;
            }
            double along = (1.0 - s) * (1.0 - s) * from.y + s * s * to.y;
            control = Math.max(control, (need - along) / (2.0 * s * (1.0 - s)));
        }
        return Math.min(control, most);
    }

    /**
     * How high the land itself stands at a column, through whatever the town built on it, or the fallback where its land
     * isn't loaded.
     */
    private static double landUnder(ServerLevel level, Hex hex, double x, double z, double fallback) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        if (!Terrain.isReady(level, bx >> 4, bz >> 4)) {
            return fallback;
        }
        HexTown town = hex.town();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(bx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1, bz);
        for (int k = 0; k < 48 && pos.getY() > level.getMinY(); k++) {
            BlockState state = level.getBlockState(pos);
            boolean built = town != null && town.isBuilt(pos.asLong());
            if (!built && !state.getCollisionShape(level, pos).isEmpty()) {
                break;
            }
            pos.move(0, -1, 0);
        }
        return pos.getY() + 1.0;
    }

    /** How high the ground stands under a point, or the fallback where its land isn't loaded. */
    private static double groundUnder(Level level, double x, double z, double fallback) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        if (level.getChunkSource().getChunkNow(bx >> 4, bz >> 4) == null) {
            return fallback;
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
    }

    /** Where a skid along the ground from where they come down stops: as far as it goes, or at whatever is in the way. */
    private static Vec3 skidTo(ServerLevel level, Vec3 landing, Vec3 away, double length) {
        Vec3 at = landing;
        for (double d = 0.5; d <= length; d += 0.5) {
            Vec3 next = landing.add(away.scale(d));
            double ground = groundUnder(level, next.x, next.z, at.y);
            BlockPos body = BlockPos.containing(next.x, ground + 0.5, next.z);
            if (ground - at.y > 0.6 || !level.getBlockState(body).getCollisionShape(level, body).isEmpty()) {
                break;
            }
            at = new Vec3(next.x, ground, next.z);
        }
        return at;
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

    private static Vec3 ground(ServerLevel level, Vec3 at, double fallback) {
        return new Vec3(at.x, groundUnder(level, at.x, at.z, fallback), at.z);
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
        final Vec3 rest;
        final double control;
        final long start;
        final int ticks;
        /** How far across the ground the throw has got as it goes through the wall. */
        final float crossingAt;
        final Vec3 crossing;
        final boolean player;
        boolean crossed;
        boolean landed;
        int crashes;

        Flight(ServerLevel level, Vec3 from, Vec3 to, Vec3 rest, double control, long start, int ticks, float crossingAt, Vec3 crossing, boolean player) {
            this.level = level;
            this.from = from;
            this.to = to;
            this.rest = rest;
            this.control = control;
            this.start = start;
            this.ticks = ticks;
            this.crossingAt = crossingAt;
            this.crossing = crossing;
            this.player = player;
        }
    }
}
