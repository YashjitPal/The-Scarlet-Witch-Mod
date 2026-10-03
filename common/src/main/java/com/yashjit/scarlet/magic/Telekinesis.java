package com.yashjit.scarlet.magic;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.network.HoldPayload;
import com.yashjit.scarlet.network.MagicEventPayload;
import com.yashjit.scarlet.network.TelekinesisPayload;
import com.yashjit.scarlet.platform.Services;
import com.yashjit.scarlet.registry.ScarletDamageTypes;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Telekinesis, server side: whatever the caster has hold of, kept where they look until they let go of it or throw it.
 *
 * <p>Anything that moves can be held: mobs, animals, players, dropped items, arrows in flight. A block can be torn out
 * of the ground and carried, and settles back into the world wherever it comes down. Bosses are too strong to hold,
 * and players slip free after a few seconds. Whatever is thrown hurts what it flies into, and is hurt by slamming into
 * walls and the ground.
 */
public final class Telekinesis {

    public static final double RANGE = 24.0;
    public static final TagKey<EntityType<?>> RESISTS = TagKey.create(Registries.ENTITY_TYPE, Scarlet.id("resists_telekinesis"));

    private static final double MIN_DISTANCE = 2.5;
    private static final double MAX_DISTANCE = 16.0;
    private static final double LOSE_DISTANCE = 32.0;
    private static final double PULL_STEP = 1.0;
    private static final int PLAYER_HOLD_TICKS = 60;
    private static final double THROW_SPEED = 2.4;
    private static final double MAX_CARRY_SPEED = 1.6;
    private static final int THROWN_TICKS = 50;
    private static final float MAX_HARDNESS = 50.0F;
    private static final int RESYNC_TICKS = 20;

    private static final Map<UUID, Hold> HOLDS = new HashMap<>();
    private static final Map<UUID, List<Thrown>> THROWN = new HashMap<>();

    private Telekinesis() {
    }

    /**
     * What a caster has hold of, if anything.
     */
    public static @Nullable Entity held(ServerPlayer player) {
        Hold hold = HOLDS.get(player.getUUID());
        return hold == null || hold.targetId == HoldPayload.NOTHING ? null : player.level().getEntity(hold.targetId);
    }

    public static boolean isHeld(Entity entity) {
        for (Hold hold : HOLDS.values()) {
            if (hold.targetId == entity.getId()) {
                return true;
            }
        }
        return false;
    }

    static void start(ServerPlayer player, long now) {
        Hold hold = new Hold(player.level());
        HOLDS.put(player.getUUID(), hold);
        tryGrab(player, hold, now);
    }

    /**
     * Once a second: drops whatever is held by a caster who has gone, so nothing is left hanging in the air.
     */
    public static void sweep(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        HOLDS.entrySet().removeIf(entry -> {
            ServerPlayer caster = server.getPlayerList().getPlayer(entry.getKey());
            if (caster != null && caster.level() == entry.getValue().level) {
                return false;
            }
            letFall(entry.getValue());
            return true;
        });
    }

    /**
     * Every tick of the channel: reaches for something under the crosshair until it has hold of it, then carries it.
     *
     * @return false once the hold has ended by itself and the channel should stop
     */
    static boolean hold(ServerPlayer player, long now) {
        Hold hold = HOLDS.get(player.getUUID());
        if (hold == null) {
            return false;
        }
        if (hold.targetId == HoldPayload.NOTHING) {
            tryGrab(player, hold, now);
            return true;
        }
        Entity target = player.level() == hold.level ? player.level().getEntity(hold.targetId) : null;
        if (target == null || !target.isAlive() || target.distanceTo(player) > LOSE_DISTANCE
                || target instanceof Player && now - hold.since > PLAYER_HOLD_TICKS) {
            release(player, false);
            return false;
        }
        carry(player, hold, target);
        if ((now - hold.since) % RESYNC_TICKS == 0) {
            // anyone who has just come into view learns what is held
            Services.NETWORK.sendToTrackingAndSelf(player, new HoldPayload(player.getId(), target.getId()));
        }
        return true;
    }

    static void stop(ServerPlayer player) {
        release(player, false);
    }

    /**
     * Everything thrown, every tick whether channeling or not: what it hits on its way, and what it slams into.
     */
    static void tick(ServerPlayer player, long now) {
        List<Thrown> thrown = THROWN.get(player.getUUID());
        if (thrown == null) {
            return;
        }
        ServerLevel level = player.level();
        Iterator<Thrown> iterator = thrown.iterator();
        while (iterator.hasNext()) {
            Thrown flight = iterator.next();
            Entity entity = level.getEntity(flight.targetId);
            if (entity == null || !entity.isAlive() || now > flight.until || flying(player, flight, entity)) {
                iterator.remove();
            }
        }
        if (thrown.isEmpty()) {
            THROWN.remove(player.getUUID());
        }
    }

    public static void handle(ServerPlayer player, TelekinesisPayload payload) {
        Hold hold = HOLDS.get(player.getUUID());
        if (hold == null || hold.targetId == HoldPayload.NOTHING || !Magic.state(player).channeling(Spell.TELEKINESIS)) {
            return;
        }
        if (payload.action() == TelekinesisPayload.PULL) {
            hold.distance = Math.clamp(hold.distance + Math.clamp(payload.amount(), -3.0F, 3.0F) * PULL_STEP, MIN_DISTANCE, MAX_DISTANCE);
        } else if (payload.action() == TelekinesisPayload.THROW) {
            release(player, true);
            Magic.stopChannel(player, player.level().getGameTime(), false);
        }
    }

    public static void forget(ServerPlayer player) {
        HOLDS.remove(player.getUUID());
        THROWN.remove(player.getUUID());
    }

    private static void tryGrab(ServerPlayer player, Hold hold, long now) {
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(RANGE));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, limit, new AABB(eye, limit).inflate(1.0),
                candidate -> canHold(player, candidate), eye.distanceToSqr(limit));
        Entity target = entityHit != null ? entityHit.getEntity() : null;
        if (target == null && block.getType() == HitResult.Type.BLOCK) {
            target = tearOut(player, block.getBlockPos());
        }
        if (target == null) {
            return;
        }
        hold.targetId = target.getId();
        hold.since = now;
        hold.distance = Math.clamp(eye.distanceTo(target.getBoundingBox().getCenter()), MIN_DISTANCE, MAX_DISTANCE);
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        Services.NETWORK.sendToTrackingAndSelf(player, new HoldPayload(player.getId(), target.getId()));
        Vec3 at = target.getBoundingBox().getCenter();
        play(level, at, SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.6F);
        play(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 0.8F);
        Mastery.grant(player, 2);
    }

    private static boolean canHold(ServerPlayer player, Entity target) {
        if (!target.isAlive() || target.isSpectator() || target == player || target.typeHolder().is(RESISTS) || isHeld(target)
                || target.isPassengerOfSameVehicle(player) || target.hasPassenger(player)) {
            return false;
        }
        return !(target instanceof Player other) || !other.isCreative() && player.canHarmPlayer(other);
    }

    /**
     * Pulls a block out of the world to be carried, if it is one that could be pushed, has nothing stored in it, and
     * may be changed here.
     */
    private static @Nullable Entity tearOut(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
            return null;
        }
        float hardness = state.getDestroySpeed(level, pos);
        PushReaction reaction = state.getPistonPushReaction();
        if (hardness < 0.0F || hardness > MAX_HARDNESS || reaction != PushReaction.PUSH_PULL && reaction != PushReaction.PUSH) {
            return null;
        }
        if (!player.mayBuild() || !level.mayInteract(player, pos) || player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) {
            return null;
        }
        FallingBlockEntity lifted = FallingBlockEntity.fall(level, pos, state);
        lifted.setNoGravity(true);
        lifted.time = 1;
        Services.NETWORK.sendToTrackingAndSelf(player, new MagicEventPayload(player.getId(), MagicEventPayload.TORN_OUT, Vec3.atCenterOf(pos)));
        play(level, Vec3.atCenterOf(pos), state.getSoundType().getBreakSound(), 1.0F, 0.8F);
        return lifted;
    }

    /**
     * Draws the held thing toward the point it is held at, with a little give, as if on a stiff spring. The heavier it
     * is, the more it lags behind.
     */
    private static void carry(ServerPlayer player, Hold hold, Entity target) {
        Vec3 point = player.getEyePosition().add(player.getLookAngle().scale(hold.distance));
        Vec3 pull = point.subtract(target.getBoundingBox().getCenter());
        double heft = heft(target);
        Vec3 velocity = target.getDeltaMovement().scale(0.35).add(pull.scale(0.32 / heft));
        if (velocity.length() > MAX_CARRY_SPEED) {
            velocity = velocity.normalize().scale(MAX_CARRY_SPEED);
        }
        if (!target.isNoGravity()) {
            // it falls a little every tick of its own; hold it up against that
            velocity = velocity.add(0.0, target.getGravity(), 0.0);
        }
        target.setDeltaMovement(velocity);
        target.syncVelocity = true;
        target.resetFallDistance();
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        } else if (target instanceof FallingBlockEntity block) {
            block.time = 1;
        }
    }

    /**
     * Lets go of what is held: gently, so it keeps the way it was moving, or thrown hard where the caster looks. A
     * block falls back to the ground either way.
     */
    private static void release(ServerPlayer player, boolean throwIt) {
        Hold hold = HOLDS.remove(player.getUUID());
        if (hold == null) {
            return;
        }
        Services.NETWORK.sendToTrackingAndSelf(player, new HoldPayload(player.getId(), HoldPayload.NOTHING));
        Entity target = letFall(hold);
        if (target == null || target.level() != player.level()) {
            return;
        }
        ServerLevel level = player.level();
        Vec3 at = target.getBoundingBox().getCenter();
        if (throwIt) {
            Vec3 velocity = player.getLookAngle().scale(THROW_SPEED / Math.sqrt(heft(target)));
            target.setDeltaMovement(velocity);
            target.syncVelocity = true;
            play(level, at, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.7F, 1.3F);
            play(level, at, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.5F, 1.4F);
            Mastery.grant(player, 2);
        } else {
            play(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 0.9F);
        }
        // a hard throw, or a flick of the wrist on letting go, makes it dangerous
        if (target.getDeltaMovement().length() > 0.7) {
            THROWN.computeIfAbsent(player.getUUID(), id -> new ArrayList<>())
                    .add(new Thrown(target.getId(), level.getGameTime() + THROWN_TICKS, target.getDeltaMovement().length()));
        }
    }

    /**
     * Gives the held thing back to gravity.
     */
    private static @Nullable Entity letFall(Hold hold) {
        Entity target = hold.targetId == HoldPayload.NOTHING ? null : hold.level.getEntity(hold.targetId);
        if (target instanceof FallingBlockEntity block) {
            block.setNoGravity(false);
        }
        return target;
    }

    /**
     * @return true once the flight is over
     */
    private static boolean flying(ServerPlayer player, Thrown flight, Entity entity) {
        ServerLevel level = player.level();
        Vec3 velocity = entity.getDeltaMovement();
        double speed = velocity.length();
        DamageSource source = ScarletDamageTypes.spell(level, ScarletDamageTypes.TELEKINESIS, player);
        float damage = (float) Math.clamp(flight.lastSpeed * 5.0, 2.0, 14.0);
        if (flight.lastSpeed > 0.5) {
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(0.25),
                    victim -> victim != entity && victim != player && victim.isAlive() && !flight.struck.contains(victim.getId()))) {
                flight.struck.add(victim.getId());
                if (victim.hurtServer(level, source, damage)) {
                    Vec3 push = velocity.lengthSqr() < 1.0E-6 ? Vec3.ZERO : velocity.normalize().scale(0.7);
                    victim.push(push.x, 0.25, push.z);
                    victim.syncVelocity = true;
                }
                if (entity instanceof LivingEntity living) {
                    living.hurtServer(level, source, damage * 0.5F);
                }
                play(level, victim.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.8F);
            }
        }
        boolean slammed = flight.lastSpeed > 0.7 && speed < flight.lastSpeed * 0.45
                && (entity.horizontalCollision || entity.verticalCollision || entity.onGround());
        if (slammed) {
            if (entity instanceof LivingEntity living) {
                living.hurtServer(level, source, damage);
            }
            Vec3 at = entity.getBoundingBox().getCenter();
            Services.NETWORK.sendToTrackingAndSelf(player, new MagicEventPayload(player.getId(), MagicEventPayload.SLAM, at));
            play(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.3F, 1.8F);
            play(level, at, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.6F);
            return true;
        }
        flight.lastSpeed = speed;
        return false;
    }

    /**
     * How heavy something is to hold: 1 for anything up to a person's size, more for the big ones.
     */
    private static double heft(Entity entity) {
        double volume = entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight();
        return Math.clamp(Math.sqrt(volume / 0.65), 1.0, 3.0);
    }

    private static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static final class Hold {
        final ServerLevel level;
        int targetId = HoldPayload.NOTHING;
        long since;
        double distance = MIN_DISTANCE;

        Hold(ServerLevel level) {
            this.level = level;
        }
    }

    private static final class Thrown {
        final int targetId;
        final long until;
        double lastSpeed;
        final IntSet struck = new IntOpenHashSet();

        Thrown(int targetId, long until, double speed) {
            this.targetId = targetId;
            this.until = until;
            this.lastSpeed = speed;
        }
    }
}
