package com.yashjit.scarlet.magic;

import com.yashjit.scarlet.entity.ChaosBolt;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What each spell does when it is released, and the sounds of the spells. Server only.
 */
public final class SpellCasts {

    private static final double AIM_RANGE = 64.0;

    private SpellCasts() {
    }

    static void perform(ServerPlayer player, Spell spell, boolean offHand) {
        if (spell == Spell.CHAOS_BOLT) {
            chaosBolt(player, offHand);
        }
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
