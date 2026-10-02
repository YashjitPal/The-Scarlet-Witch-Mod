package com.yashjit.scarlet.entity;

import com.yashjit.scarlet.magic.Mastery;
import com.yashjit.scarlet.network.ChaosImpactPayload;
import com.yashjit.scarlet.platform.Services;
import com.yashjit.scarlet.registry.ScarletDamageTypes;
import com.yashjit.scarlet.registry.ScarletEntities;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A blast of chaos energy: flies straight and fast, hurts what it hits and bursts on impact. It never breaks blocks.
 */
public class ChaosBolt extends ThrowableProjectile {

    public static final float SPEED = 1.7F;
    private static final int LIFETIME = 40;

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
        super.tick();
        if (!level().isClientSide() && isAlive() && tickCount > LIFETIME) {
            burst(position(), getDeltaMovement().normalize().reverse(), false, false);
            discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Entity target = hit.getEntity();
        Entity owner = getOwner();
        int rank = owner instanceof LivingEntity caster ? Mastery.rank(caster) : 1;
        float damage = 5.0F + 0.5F * Math.max(0, rank - 1);
        if (target.hurtServer(level, ScarletDamageTypes.chaosBolt(level, this, owner), damage)) {
            if (target instanceof LivingEntity living) {
                double resistance = 1.0 - Math.min(1.0, living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                Vec3 push = getDeltaMovement().multiply(1.0, 0.0, 1.0).normalize().scale(0.42 * resistance);
                living.push(push.x, 0.08 * resistance, push.z);
                living.syncVelocity = true;
            }
            if (owner instanceof LivingEntity caster) {
                Mastery.grant(caster, 2);
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level().isClientSide()) {
            return;
        }
        Vec3 normal = result instanceof BlockHitResult block ? block.getDirection().getUnitVec3() : getDeltaMovement().normalize().reverse();
        burst(result.getLocation(), normal, result.getType() == HitResult.Type.ENTITY, true);
        discard();
    }

    private void burst(Vec3 at, Vec3 normal, boolean hitEntity, boolean loud) {
        Services.NETWORK.sendToTrackingAndSelf(this, new ChaosImpactPayload(at, normal, getDeltaMovement().normalize(), hitEntity));
        if (loud) {
            float pitch = 1.2F + random.nextFloat() * 0.25F;
            level().playSound(null, at.x, at.y, at.z, SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.5F, pitch);
            level().playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.55F, pitch * 0.7F);
        }
    }
}
