package com.yashjit.scarlet.magic;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.network.RunePayload;
import com.yashjit.scarlet.platform.Services;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import org.jspecify.annotations.Nullable;

/**
 * Rune Traps, server side: sigils of scarlet light inscribed on the ground, binding whatever steps onto them.
 *
 * <p>Cast at the ground, a sigil writes itself there and lies in wait, faintly glowing, for half a minute. The moment
 * anything living other than its caster and theirs sets foot inside it, it springs: chains of light rise out of it and
 * bind everything standing on it in place, unable to walk or jump, a creature unable even to fight, for a few seconds.
 * Cast at a creature, the sigil writes itself beneath it and springs at once. A caster keeps at most three sigils; a
 * fourth wipes the oldest away.
 */
public final class RuneTraps {

    public static final float RADIUS = 2.5F;
    /** Ticks a sigil takes to write itself onto the ground before it can spring. */
    public static final int INSCRIBE_TICKS = 12;
    private static final int ARMED_TICKS = 600;
    private static final int BIND_TICKS = 100;
    private static final int PLAYER_BIND_TICKS = 60;
    /** How much longer each rank of mastery past the spell's own binds for. */
    private static final int BIND_PER_RANK = 10;
    private static final int MAX_PER_CASTER = 3;
    /** Ticks before a sigil cast at a creature springs beneath it. */
    private static final int SNAP_TICKS = 4;
    private static final double RANGE = 24.0;
    /** How far above its sigil something counts as standing on it. */
    private static final double STANDING_HEIGHT = 1.5;
    private static final double SEEN_FROM = 96.0;
    private static final int RESYNC_TICKS = 40;
    private static final Identifier BOUND_SPEED = Scarlet.id("rune_bound_speed");
    private static final Identifier BOUND_JUMP = Scarlet.id("rune_bound_jump");

    private static final Map<ServerLevel, List<Trap>> TRAPS = new HashMap<>();
    /** What every sigil holds, by entity id. */
    private static final Int2ObjectMap<Bind> BOUND = new Int2ObjectOpenHashMap<>();
    private static int nextId;

    private RuneTraps() {
    }

    /**
     * Writes a sigil where the caster aims: onto the ground there, or beneath a creature, springing at once.
     *
     * @return false if there was nowhere to write it
     */
    static boolean inscribe(ServerPlayer caster) {
        ServerLevel level = caster.level();
        long now = level.getGameTime();
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(RANGE));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster, eye, limit, new AABB(eye, limit).inflate(1.0),
                candidate -> candidate instanceof LivingEntity living && canBind(caster, living), eye.distanceToSqr(limit));
        Vec3 at;
        long springAt = Long.MAX_VALUE;
        if (hit != null) {
            at = ground(level, hit.getEntity().position().add(0.0, 0.5, 0.0));
            springAt = now + SNAP_TICKS;
        } else if (block.getType() == HitResult.Type.BLOCK) {
            at = block.getDirection() == Direction.UP ? block.getLocation() : ground(level, block.getLocation());
        } else {
            at = null;
        }
        if (at == null) {
            return false;
        }
        List<Trap> traps = TRAPS.computeIfAbsent(level, key -> new ArrayList<>());
        List<Trap> own = traps.stream().filter(trap -> trap.owner.equals(caster.getUUID()) && trap.sprungAt < 0).toList();
        if (own.size() >= MAX_PER_CASTER) {
            fade(level, own.getFirst());
            traps.remove(own.getFirst());
        }
        int rank = Mastery.rank(caster);
        Trap trap = new Trap(nextId++, caster.getUUID(), at, now, springAt, Math.max(0, rank - Spell.RUNE_TRAP.rank()) * BIND_PER_RANK);
        traps.add(trap);
        announce(level, trap, RunePayload.INSCRIBED);
        play(level, at, SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 0.75F);
        play(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 0.6F);
        Mastery.grant(caster, 2);
        return true;
    }

    /**
     * Every tick of every world: sigils lying in wait watch for whatever steps onto them, sprung ones keep hold until
     * their chains give out, and whatever they hold stays where it was caught.
     */
    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        List<Trap> traps = TRAPS.get(level);
        if (traps != null && !traps.isEmpty()) {
            Iterator<Trap> iterator = traps.iterator();
            while (iterator.hasNext()) {
                Trap trap = iterator.next();
                if (trap.sprungAt < 0) {
                    if (now - trap.inscribedAt > ARMED_TICKS) {
                        fade(level, trap);
                        iterator.remove();
                    } else if (now - trap.inscribedAt >= INSCRIBE_TICKS || now >= trap.springAt) {
                        List<LivingEntity> standing = standingOn(level, trap);
                        if (!standing.isEmpty()) {
                            spring(level, trap, standing, now);
                        }
                    }
                } else {
                    trap.bound.removeIf(id -> !BOUND.containsKey(id) || BOUND.get(id).trapId != trap.id);
                    if (trap.bound.isEmpty()) {
                        fade(level, trap);
                        iterator.remove();
                    }
                }
                if (now % RESYNC_TICKS == 0 && traps.contains(trap)) {
                    announce(level, trap, trap.sprungAt < 0 ? RunePayload.INSCRIBED : RunePayload.SPRUNG);
                }
            }
        }
        if (BOUND.isEmpty()) {
            return;
        }
        var binds = BOUND.int2ObjectEntrySet().iterator();
        while (binds.hasNext()) {
            var entry = binds.next();
            Bind bind = entry.getValue();
            if (bind.level != level) {
                continue;
            }
            Entity entity = level.getEntity(entry.getIntKey());
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || now >= bind.until) {
                if (entity instanceof LivingEntity living) {
                    loosen(living);
                    play(level, living.position(), SoundEvents.CHAIN_BREAK, 0.9F, 1.1F);
                }
                binds.remove();
                continue;
            }
            hold(living, bind);
        }
    }

    /**
     * Whether a creature is bound, its own mind kept from doing anything about it.
     */
    public static boolean binds(Mob mob) {
        return !BOUND.isEmpty() && BOUND.containsKey(mob.getId());
    }

    /**
     * In place of a bound creature's own thinking: straining against the chains, looking about, going nowhere.
     */
    public static void strain(Mob mob) {
        mob.getNavigation().stop();
        mob.setXxa(0.0F);
        mob.setYya(0.0F);
        mob.setZza(0.0F);
        mob.setJumping(false);
        if (mob.tickCount % 12 == 0) {
            mob.setYHeadRot(mob.getYHeadRot() + (mob.getRandom().nextFloat() - 0.5F) * 70.0F);
        }
    }

    private static void spring(ServerLevel level, Trap trap, List<LivingEntity> standing, long now) {
        trap.sprungAt = now;
        for (LivingEntity living : standing) {
            int ticks = (living instanceof Player ? PLAYER_BIND_TICKS : BIND_TICKS) + trap.bonusTicks;
            Bind previous = BOUND.get(living.getId());
            if (previous != null && previous.until >= now + ticks) {
                continue;
            }
            BOUND.put(living.getId(), new Bind(trap.id, level, living.position(), now + ticks));
            trap.bound.add(living.getId());
            tighten(living);
        }
        if (trap.bound.isEmpty()) {
            trap.sprungAt = -1;
            return;
        }
        announce(level, trap, RunePayload.SPRUNG);
        play(level, trap.at, SoundEvents.EVOKER_FANGS_ATTACK, 0.9F, 0.8F);
        play(level, trap.at, SoundEvents.CHAIN_PLACE, 1.0F, 0.7F);
        play(level, trap.at, SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.5F);
    }

    private static void fade(ServerLevel level, Trap trap) {
        announce(level, trap, RunePayload.FADED);
        for (int id : trap.bound) {
            Bind bind = BOUND.get(id);
            if (bind != null && bind.trapId == trap.id) {
                BOUND.remove(id);
                if (level.getEntity(id) instanceof LivingEntity living) {
                    loosen(living);
                }
            }
        }
    }

    private static List<LivingEntity> standingOn(ServerLevel level, Trap trap) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(trap.owner);
        AABB area = new AABB(trap.at.x - RADIUS, trap.at.y - 0.5, trap.at.z - RADIUS, trap.at.x + RADIUS, trap.at.y + STANDING_HEIGHT, trap.at.z + RADIUS);
        return level.getEntitiesOfClass(LivingEntity.class, area, living -> {
            double dx = living.getX() - trap.at.x;
            double dz = living.getZ() - trap.at.z;
            return dx * dx + dz * dz <= RADIUS * RADIUS && (owner == null ? !living.getUUID().equals(trap.owner) : canBind(owner, living));
        });
    }

    private static boolean canBind(ServerPlayer caster, LivingEntity living) {
        if (!living.isAlive() || living == caster || living.isSpectator() || living.typeHolder().is(MindControl.RESISTS)) {
            return false;
        }
        if (living instanceof OwnableEntity pet && pet.getOwner() == caster || living.isAlliedTo(caster)) {
            return false;
        }
        return !(living instanceof Player other) || !other.isCreative() && caster.canHarmPlayer(other);
    }

    /**
     * The ground beneath a point, where a sigil can lie: the top of the first solid block within a few blocks below.
     */
    private static @Nullable Vec3 ground(ServerLevel level, Vec3 from) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(from).mutable();
        for (int i = 0; i < 8; i++) {
            BlockPos below = pos.below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty() && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                double top = below.getY() + level.getBlockState(below).getCollisionShape(level, below).max(Direction.Axis.Y);
                return new Vec3(from.x, top, from.z);
            }
            pos.move(Direction.DOWN);
        }
        return null;
    }

    /**
     * Keeps what is bound where it was caught: it cannot walk or jump, and anything that moves it is undone.
     */
    private static void hold(LivingEntity living, Bind bind) {
        Vec3 drift = bind.at.subtract(living.position());
        if (living instanceof Player) {
            living.setDeltaMovement(drift.x * 0.5, Math.min(living.getDeltaMovement().y, 0.0), drift.z * 0.5);
        } else if (drift.horizontalDistanceSqr() > 0.09) {
            living.setPos(bind.at.x, living.getY(), bind.at.z);
            living.setDeltaMovement(0.0, Math.min(living.getDeltaMovement().y, 0.0), 0.0);
        } else {
            living.setDeltaMovement(0.0, living.isNoGravity() ? 0.0 : Math.min(living.getDeltaMovement().y, 0.0), 0.0);
        }
        living.syncVelocity = true;
        if (living.tickCount % 18 == 0) {
            living.level().playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.CHAIN_STEP, SoundSource.PLAYERS, 0.6F,
                    0.8F + living.getRandom().nextFloat() * 0.3F);
        }
    }

    private static void tighten(LivingEntity living) {
        AttributeInstance speed = living.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(BOUND_SPEED, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        AttributeInstance jump = living.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) {
            jump.addOrUpdateTransientModifier(new AttributeModifier(BOUND_JUMP, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        if (living instanceof Mob mob) {
            mob.setTarget(null);
        }
    }

    private static void loosen(LivingEntity living) {
        AttributeInstance speed = living.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(BOUND_SPEED);
        }
        AttributeInstance jump = living.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) {
            jump.removeModifier(BOUND_JUMP);
        }
    }

    private static void announce(ServerLevel level, Trap trap, int stage) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(trap.owner);
        RunePayload payload = new RunePayload(trap.id, trap.at, (int) Mth.clamp(level.getGameTime() - trap.inscribedAt, 0, Integer.MAX_VALUE), stage,
                List.copyOf(trap.bound), owner != null && owner.level() == level ? owner.getId() : -1);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(trap.at) < SEEN_FROM * SEEN_FROM) {
                Services.NETWORK.sendToPlayer(player, payload);
            }
        }
    }

    private static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static final class Trap {
        final int id;
        final UUID owner;
        final Vec3 at;
        final long inscribedAt;
        /** When a sigil cast at a creature springs beneath it; never, for one lying in wait. */
        final long springAt;
        final int bonusTicks;
        long sprungAt = -1;
        final List<Integer> bound = new ArrayList<>();

        Trap(int id, UUID owner, Vec3 at, long inscribedAt, long springAt, int bonusTicks) {
            this.id = id;
            this.owner = owner;
            this.at = at;
            this.inscribedAt = inscribedAt;
            this.springAt = springAt;
            this.bonusTicks = bonusTicks;
        }
    }

    private record Bind(int trapId, ServerLevel level, Vec3 at, long until) {
    }
}
