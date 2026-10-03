package com.yashjit.scarlet.entity;

import com.yashjit.scarlet.darkhold.Darkhold;
import com.yashjit.scarlet.darkhold.Dreamwalk;
import com.yashjit.scarlet.platform.Services;
import com.yashjit.scarlet.registry.ScarletEntities;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A dreamwalker's body, left behind while their spirit is elsewhere: sitting cross-legged in the air where they sat down,
 * in their own skin, clothes and crown. As far as the world is concerned it is them: hostile creatures nearby go for it,
 * and a blow to it snaps their spirit back into it to take the blow.
 *
 * <p>It is never saved. Whatever brings the spirit back takes it away, and one whose spirit is not coming back to it goes
 * by itself.
 */
public class DreamBody extends Avatar {

    protected static final EntityDataAccessor<ResolvableProfile> PROFILE = SynchedEntityData.defineId(DreamBody.class, EntityDataSerializers.RESOLVABLE_PROFILE);
    /** How far the Darkhold has taken its owner, 0 to 1, which darkens the magic about it. */
    private static final EntityDataAccessor<Float> CORRUPTION = SynchedEntityData.defineId(DreamBody.class, EntityDataSerializers.FLOAT);
    /** Whether its owner was suited up, so the costume stays on it. */
    private static final EntityDataAccessor<Boolean> SUITED = SynchedEntityData.defineId(DreamBody.class, EntityDataSerializers.BOOLEAN);
    /** Sitting cross-legged in the air: from the ground beneath it to the top of its head. */
    private static final EntityDimensions SITTING = EntityDimensions.scalable(0.7F, 1.6F).withEyeHeight(1.33F);
    private static final int SYNC_EVERY = 20;

    /** Swapped on the client for its own kind, which knows how to look like its owner. */
    public static EntityType.EntityFactory<DreamBody> constructor = DreamBody::new;

    private @Nullable UUID owner;

    public DreamBody(EntityType<? extends DreamBody> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static DreamBody create(EntityType<DreamBody> type, Level level) {
        return constructor.create(type, level);
    }

    /**
     * Leaves a body where a dreamwalker sits, looking like them and wearing what they wear.
     */
    public static DreamBody leave(ServerPlayer player) {
        DreamBody body = new DreamBody(ScarletEntities.DREAM_BODY.get(), player.level());
        body.owner = player.getUUID();
        body.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        body.setYHeadRot(player.getYRot());
        body.setYBodyRot(player.getYRot());
        body.entityData.set(PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
        body.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, player.getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION));
        body.entityData.set(SUITED, Services.PLAYER_DATA.get(player).suited());
        body.entityData.set(CORRUPTION, Darkhold.corruption(player, player.level().getGameTime()));
        body.setMainArm(player.getMainArm());
        body.setCustomName(player.getName());
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || slot.getType() == EquipmentSlot.Type.HAND) {
                body.setItemSlot(slot, player.getItemBySlot(slot).copy());
            }
        }
        player.level().addFreshEntity(body);
        return body;
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public boolean suited() {
        return entityData.get(SUITED);
    }

    public float corruption() {
        return entityData.get(CORRUPTION);
    }

    @Override
    public ResolvableProfile getProfile() {
        return entityData.get(PROFILE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(PROFILE, ResolvableProfile.Static.EMPTY);
        entityData.define(CORRUPTION, 0.0F);
        entityData.define(SUITED, false);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (player == null || Dreamwalk.bodyOf(player) != this) {
            discard();
        } else if (tickCount % SYNC_EVERY == 0) {
            entityData.set(CORRUPTION, Darkhold.corruption(player, level.getGameTime()));
        }
    }

    /**
     * A blow to the body wakes its owner in it, to take the blow themselves.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isRemoved() || isInvulnerableTo(level, source)) {
            return false;
        }
        Dreamwalk.bodyStruck(this, source, amount);
        return true;
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return SITTING;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(double xa, double ya, double za) {
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean ignorePassenger) {
        return false;
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }
}
