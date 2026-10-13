package com.yashjit.scarlet.entity;

import com.yashjit.scarlet.hex.HexRipples;
import com.yashjit.scarlet.magic.Mastery;
import com.yashjit.scarlet.network.ChaosImpactPayload;
import com.yashjit.scarlet.platform.Services;
import com.yashjit.scarlet.registry.ScarletDamageTypes;
import com.yashjit.scarlet.registry.ScarletEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A blast of chaos energy: flies straight and fast, hurts what it hits and whatever else is within a block of where it
 * strikes, and bursts on impact. It never breaks blocks.
 */
public class ChaosBolt extends ThrowableProjectile {

    public static final float SPEED = 1.7F;
    private static final int LIFETIME = 200;
    /** How near where it strikes something must be to be caught in the blast too. */
    private static final double SPLASH = 1.0;
    /** How far off it is seen, as far as it flies before it fizzles out or players are told of it. */
    private static final double SEEN_FROM = 256.0;

    /** Whether what it flew into was hurt, which earns its caster their mastery without the blast around it. */
    private boolean landed;

    public ChaosBolt(EntityType<? extends ChaosBolt> type, Level level) {
        super(type, level);
    }

    public static ChaosBolt fire(ServerPlayer owner, Vec3 from, Vec3 direction) {
        ChaosBolt bolt = new ChaosBolt(ScarletEntities.CHAOS_BOLT.get(), owner.level());
        bolt.setOwner(owner);
        bolt.setPos(from);
        bolt.shoot(direction.x, direction.y, direction.z, SPEED, 0.0F);
        owner.level().addFreshEntity(bolt);
        return bolt;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    protected float getAirDrag() {
        return 1.0F;
    }

    @Override
    public void tick() {
        Vec3 from = position();
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isAlive()) {
            return;
        }
        Vec3 wall = HexRipples.crossing(level, from, position());
        if (wall != null) {
            strikeHex(level, wall);
        } else if (tickCount > LIFETIME || !level.isPositionEntityTicking(BlockPos.containing(position().add(getDeltaMovement())))) {
            // spent, or about to fly on where the world stands still, which would hold it there
            burst(position(), getDeltaMovement().normalize().reverse(), false, false);
            discard();
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double reach = SEEN_FROM * getViewScale();
        return distance < reach * reach && (tickCount >= 2 || super.shouldRenderAtSqrDistance(distance));
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /**
     * No chaos blast goes through the wall of a Hex: it bursts on it, and the wall flares red and ripples from where
     * it was struck. Through where its caster has parted the wall, it flies on.
     */
    private void strikeHex(ServerLevel level, Vec3 at) {
        burst(at, getDeltaMovement().normalize().reverse(), false, false);
        HexRipples.ripple(level, at, HexRipples.BLAST);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 1.7F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 0.7F, 1.5F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8F, 0.8F);
        discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level() instanceof ServerLevel level && strike(level, hit.getEntity(), getDeltaMovement())) {
            landed = true;
            if (getOwner() instanceof LivingEntity caster) {
                Mastery.grant(caster, 2);
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 normal = result instanceof BlockHitResult block ? block.getDirection().getUnitVec3() : getDeltaMovement().normalize().reverse();
        Entity struck = result instanceof EntityHitResult hit ? hit.getEntity() : null;
        splash(level, result.getLocation(), normal, struck);
        burst(result.getLocation(), normal, struck != null, true);
        discard();
    }

    /**
     * Catches whatever else lives within {@link #SPLASH} of where it struck and the blast can reach, throwing each back
     * from there. Its caster, and whoever is with them, it spares.
     */
    private void splash(ServerLevel level, Vec3 at, Vec3 normal, @Nullable Entity struck) {
        Entity owner = getOwner();
        // seen from just off what it struck, so the very surface it hit hides nothing standing on it
        Vec3 from = at.add(normal.scale(0.1));
        boolean hurt = false;
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(SPLASH), living -> living != struck
                && living != owner && living.isAlive() && !living.isSpectator() && (owner == null || !withCaster(owner, living)))) {
            AABB box = living.getBoundingBox();
            Vec3 nearest = new Vec3(Math.clamp(at.x, box.minX, box.maxX), Math.clamp(at.y, box.minY, box.maxY), Math.clamp(at.z, box.minZ, box.maxZ));
            if (nearest.distanceToSqr(at) > SPLASH * SPLASH || ServerExplosion.getSeenPercent(from, living) <= 0.0F) {
                continue;
            }
            Vec3 away = box.getCenter().subtract(at).multiply(1.0, 0.0, 1.0);
            hurt |= strike(level, living, away.lengthSqr() < 1.0E-4 ? getDeltaMovement() : away);
        }
        if (hurt && !landed && owner instanceof LivingEntity caster) {
            Mastery.grant(caster, 2);
        }
    }

    /**
     * Hurts something the blast reaches, and throws it back the way given.
     *
     * @return whether it was hurt
     */
    private boolean strike(ServerLevel level, Entity target, Vec3 push) {
        Entity owner = getOwner();
        int rank = owner instanceof LivingEntity caster ? Mastery.rank(caster) : 1;
        float damage = 5.0F + 0.5F * Math.max(0, rank - 1);
        if (!target.hurtServer(level, ScarletDamageTypes.chaosBolt(level, this, owner), damage)) {
            return false;
        }
        if (target instanceof LivingEntity living) {
            double resistance = 1.0 - Math.min(1.0, living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            Vec3 shove = push.multiply(1.0, 0.0, 1.0).normalize().scale(0.42 * resistance);
            living.push(shove.x, 0.08 * resistance, shove.z);
            living.syncVelocity = true;
        }
        return true;
    }

    /**
     * Whether something is with the caster: carried by them, carrying them, or their own pet.
     */
    private static boolean withCaster(Entity caster, Entity entity) {
        return entity.isPassengerOfSameVehicle(caster) || entity.hasPassenger(caster) || caster.hasPassenger(entity)
                || entity instanceof OwnableEntity pet && pet.getOwner() == caster;
    }

    private void burst(Vec3 at, Vec3 normal, boolean hitEntity, boolean loud) {
        Entity owner = getOwner();
        Services.NETWORK.sendToTrackingAndSelf(this, new ChaosImpactPayload(at, normal, getDeltaMovement().normalize(), hitEntity,
                owner != null ? owner.getId() : -1));
        if (loud) {
            float pitch = 1.2F + random.nextFloat() * 0.25F;
            level().playSound(null, at.x, at.y, at.z, SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.5F, pitch);
            level().playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.55F, pitch * 0.7F);
        }
    }
}
