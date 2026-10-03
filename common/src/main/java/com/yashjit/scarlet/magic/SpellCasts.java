package com.yashjit.scarlet.magic;

import com.yashjit.scarlet.entity.ChaosBolt;
import com.yashjit.scarlet.network.MagicEventPayload;
import com.yashjit.scarlet.platform.Services;
import com.yashjit.scarlet.registry.ScarletDamageTypes;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What each spell does when it is released, and the sounds of the spells. Server only.
 */
public final class SpellCasts {

    private static final double AIM_RANGE = 64.0;
    public static final double SHOCKWAVE_RADIUS = 8.0;
    public static final double MIST_RANGE = 12.0;

    private SpellCasts() {
    }

    /**
     * The moment of casting, before the effect lands: whatever the spell does while it gathers itself.
     */
    static void begin(ServerPlayer player, Spell spell) {
        if (spell == Spell.SHOCKWAVE) {
            Services.NETWORK.sendToTrackingAndSelf(player, new MagicEventPayload(player.getId(), MagicEventPayload.SHOCKWAVE_GATHER, player.position()));
            play(player, player.position(), SoundEvents.BEACON_POWER_SELECT, 0.5F, 1.8F);
            play(player, player.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 0.6F);
        } else if (spell == Spell.RED_MIST) {
            Services.NETWORK.sendToTrackingAndSelf(player, new MagicEventPayload(player.getId(), MagicEventPayload.MIST_OUT, player.position()));
            play(player, player.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 0.6F, 1.3F);
            play(player, player.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.4F, 1.5F);
        }
    }

    static void perform(ServerPlayer player, Spell spell, boolean offHand) {
        switch (spell) {
            case CHAOS_BOLT -> chaosBolt(player, offHand);
            case SHOCKWAVE -> shockwave(player);
            case RED_MIST -> redMist(player);
            case RUNE_TRAP -> {
                if (!RuneTraps.inscribe(player)) {
                    // nowhere to write it: the sigil sputters out in the hand
                    Services.NETWORK.sendToTrackingAndSelf(player, new MagicEventPayload(player.getId(), MagicEventPayload.RUNE_FIZZLE,
                            handPosition(player, offHand)));
                    play(player, player.getEyePosition(), SoundEvents.FIRE_EXTINGUISH, 0.4F, 1.6F);
                }
            }
            default -> {
            }
        }
    }

    /**
     * Carries the caster to where they are looking, as far as {@link #MIST_RANGE}: onto the ground there, or back from
     * a wall, wherever their body fits. Arriving in mid-air, they drift down rather than fall.
     */
    private static void redMist(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 from = player.position();
        Vec3 to = mistDestination(player);
        if (to == null) {
            // nowhere to go: they gather back where they stood
            Services.NETWORK.sendToTrackingAndSelf(player, new MagicEventPayload(player.getId(), MagicEventPayload.MIST_IN, from));
            return;
        }
        player.stopRiding();
        player.teleportTo(level, to.x, to.y, to.z, Set.of(Relative.X_ROT, Relative.Y_ROT), 0.0F, 0.0F, false);
        player.resetFallDistance();
        if (!level.noCollision(player, player.getBoundingBox().move(0.0, -0.5, 0.0))) {
            player.setDeltaMovement(Vec3.ZERO);
        } else {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false, false));
        }
        Services.NETWORK.sendToTrackingAndSelf(player, new MagicEventPayload(player.getId(), MagicEventPayload.MIST_IN, to));
        play(player, from, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.7F, 1.25F);
        play(player, to, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.8F, 0.95F);
        play(player, to, SoundEvents.AMETHYST_CLUSTER_HIT, 0.5F, 0.7F);
        Mastery.grant(player, 2);
    }

    private static @Nullable Vec3 mistDestination(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(MIST_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double reach = hit.getType() == HitResult.Type.MISS ? MIST_RANGE : hit.getLocation().distanceTo(eye);
        if (hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP) {
            Vec3 standing = hit.getLocation();
            if (fits(player, standing)) {
                return standing;
            }
        }
        double half = player.getBbHeight() * 0.5;
        for (double distance = reach - 0.45; distance >= 1.5; distance -= 0.5) {
            Vec3 feet = eye.add(look.scale(distance)).subtract(0.0, half, 0.0);
            if (!fits(player, feet)) {
                continue;
            }
            // settle onto the ground if it is close below
            for (int drop = 0; drop < 8; drop++) {
                Vec3 lower = feet.subtract(0.0, 0.5, 0.0);
                if (!fits(player, lower)) {
                    break;
                }
                feet = lower;
            }
            return feet;
        }
        return null;
    }

    private static boolean fits(ServerPlayer player, Vec3 feet) {
        return player.level().noCollision(player, player.getBoundingBox().move(feet.subtract(player.position())).deflate(1.0E-4));
    }

    /**
     * Throws back everything around the caster, hardest up close, and hurts whatever lives. Projectiles in the air are
     * blown away with it.
     */
    private static void shockwave(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 feet = player.position();
        Vec3 chest = feet.add(0.0, player.getBbHeight() * 0.5, 0.0);
        float damage = 3.0F + 0.5F * Math.max(0, Mastery.rank(player) - Spell.SHOCKWAVE.rank());
        DamageSource source = ScarletDamageTypes.spell(level, ScarletDamageTypes.SHOCKWAVE, player);
        for (Entity entity : level.getEntities(player, new AABB(chest, chest).inflate(SHOCKWAVE_RADIUS), entity -> entity.isAlive() && !entity.isSpectator())) {
            Vec3 offset = entity.getBoundingBox().getCenter().subtract(chest);
            double distance = offset.length();
            if (distance > SHOCKWAVE_RADIUS || isOnSide(player, entity)) {
                continue;
            }
            double strength = 1.0 - distance / SHOCKWAVE_RADIUS;
            Vec3 flat = new Vec3(offset.x, 0.0, offset.z);
            Vec3 away = flat.lengthSqr() < 1.0E-4 ? player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize() : flat.normalize();
            if (entity instanceof Projectile projectile) {
                projectile.setDeltaMovement(away.scale(0.9 + strength).add(0.0, 0.25, 0.0));
                projectile.needsSync = true;
                continue;
            }
            double resistance = 1.0;
            if (entity instanceof LivingEntity living) {
                living.hurtServer(level, source, damage * (float) (0.4 + 0.6 * strength));
                resistance -= Math.min(1.0, living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            }
            double push = (0.5 + 1.5 * strength) * resistance;
            entity.push(away.x * push, (0.3 + 0.4 * strength) * resistance, away.z * push);
            entity.syncVelocity = true;
        }
        Services.NETWORK.sendToTrackingAndSelf(player, new MagicEventPayload(player.getId(), MagicEventPayload.SHOCKWAVE, feet));
        play(player, feet, SoundEvents.GENERIC_EXPLODE.value(), 0.55F, 1.5F);
        play(player, feet, SoundEvents.WARDEN_SONIC_BOOM, 0.35F, 1.7F);
        play(player, feet, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.8F, 0.6F);
        Mastery.grant(player, 2);
    }

    /**
     * Whether an entity is with the caster: carried by them, carrying them, or their own pet.
     */
    private static boolean isOnSide(ServerPlayer player, Entity entity) {
        return entity.isPassengerOfSameVehicle(player) || entity.hasPassenger(player) || player.hasPassenger(entity)
                || entity instanceof OwnableEntity pet && pet.getOwner() == player;
    }

    private static void chaosBolt(ServerPlayer player, boolean offHand) {
        Vec3 hand = handPosition(player, offHand);
        // fly from the hand to whatever is under the crosshair, so bolts from either hand converge on it
        Vec3 toTarget = aimPoint(player).subtract(hand);
        Vec3 direction = toTarget.lengthSqr() < 4.0 ? player.getLookAngle() : toTarget.normalize();
        ChaosBolt.fire(player, hand, direction);
        float pitch = 1.45F + player.getRandom().nextFloat() * 0.2F;
        play(player, hand, SoundEvents.BREEZE_SHOOT, 0.4F, pitch);
        play(player, hand, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.35F, pitch + 0.3F);
    }

    static void shieldRaised(ServerPlayer player) {
        play(player, player.getEyePosition(), SoundEvents.BEACON_ACTIVATE, 0.45F, 1.75F);
        play(player, player.getEyePosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5F, 1.25F);
    }

    static void shieldLowered(ServerPlayer player) {
        play(player, player.getEyePosition(), SoundEvents.BEACON_DEACTIVATE, 0.35F, 1.9F);
    }

    static void shieldStruck(ServerPlayer player, Vec3 at) {
        play(player, at, SoundEvents.AMETHYST_CLUSTER_HIT, 0.9F, 1.1F + player.getRandom().nextFloat() * 0.3F);
        play(player, at, SoundEvents.AMETHYST_BLOCK_HIT, 0.6F, 0.7F);
    }

    static void shieldShattered(ServerPlayer player) {
        play(player, player.getEyePosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 0.6F);
        play(player, player.getEyePosition(), SoundEvents.GLASS_BREAK, 0.6F, 1.4F);
    }

    static void liftOff(ServerPlayer player) {
        play(player, player.position(), SoundEvents.BREEZE_CHARGE, 0.5F, 1.35F);
        play(player, player.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.45F, 1.6F);
    }

    static void touchDown(ServerPlayer player) {
        play(player, player.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 0.85F);
    }

    static void levitationReleased(ServerPlayer player) {
        play(player, player.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.35F, 1.2F);
    }

    private static void play(ServerPlayer player, Vec3 at, SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /**
     * Roughly where the palm is while striking forward, from the view rather than the animated model (which the
     * server does not have).
     */
    public static Vec3 handPosition(Player player, boolean offHand) {
        HumanoidArm arm = offHand ? player.getMainArm().getOpposite() : player.getMainArm();
        double side = arm == HumanoidArm.RIGHT ? 1.0 : -1.0;
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0E-4) {
            double yaw = Math.toRadians(player.getYRot());
            right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        }
        right = right.normalize();
        Vec3 up = right.cross(look).normalize();
        return player.getEyePosition().add(right.scale(0.34 * side)).add(up.scale(-0.3)).add(look.scale(0.55));
    }

    private static Vec3 aimPoint(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(AIM_RANGE));
        BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, limit, new AABB(eye, limit).inflate(1.0),
                target -> target.isPickable() && !target.isSpectator() && target != player, eye.distanceToSqr(limit));
        return entity != null ? entity.getLocation() : limit;
    }
}
