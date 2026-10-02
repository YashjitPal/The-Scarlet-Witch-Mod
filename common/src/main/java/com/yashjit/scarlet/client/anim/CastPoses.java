package com.yashjit.scarlet.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yashjit.scarlet.client.fx.ShieldFx;
import com.yashjit.scarlet.client.hex.HexClient;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * Full-body casting poses, layered over vanilla's after it has posed the model.
 *
 * <ul>
 *     <li>Strike: the arm swings up to point where the head looks, angled in toward the crosshair, and the torso
 *     twists into it.</li>
 *     <li>Shield: both palms push forward together, trembling with the strain, and are knocked back by each blow.</li>
 *     <li>Levitation: the arms float out from the sides and the legs hang loose. The arms sweep back as you pick up
 *     speed, the body leans into the flight, and the whole figure bobs gently.</li>
 * </ul>
 */
public final class CastPoses {

    private static final float HALF_PI = (float) (Math.PI / 2);
    private static final float LEVITATION_PIVOT = 0.9F;
    private static final float BOB_HEIGHT = 0.07F;

    private CastPoses() {
    }

    /**
     * Called while extracting a player's render state.
     */
    public static void extract(Avatar avatar, AvatarRenderState state, float partialTick) {
        float right = 0.0F;
        float left = 0.0F;
        float shield = 0.0F;
        float recoil = 0.0F;
        float levitate = 0.0F;
        float lean = 0.0F;
        float burst = 0.0F;
        if (avatar instanceof Player player) {
            double now = player.level().getGameTime() + partialTick;
            right = CastGestures.extension(player, HumanoidArm.RIGHT, now);
            left = CastGestures.extension(player, HumanoidArm.LEFT, now);
            PoseBlends.Blend blend = PoseBlends.of(player);
            shield = blend.shield;
            recoil = ShieldFx.recoil(player, now);
            levitate = blend.levitate;
            lean = blend.lean;
            burst = HexClient.burst(player, now);
        }
        ((CastPoseState) state).scarlet$set(right, left, shield, recoil, levitate, lean, burst);
    }

    /**
     * Called at the end of {@code PlayerModel.setupAnim}.
     */
    public static void apply(PlayerModel model, AvatarRenderState state) {
        CastPoseState pose = (CastPoseState) state;
        float weight = state.isFallFlying ? 0.0F : 1.0F - state.swimAmount;
        if (weight <= 0.0F) {
            return;
        }
        float age = state.ageInTicks;
        float levitate = pose.scarlet$levitate() * weight;
        if (levitate > 0.001F) {
            levitate(model, levitate, age, Math.max(0.0F, pose.scarlet$lean() / PoseBlends.MAX_LEAN));
        }
        float shield = pose.scarlet$shield() * weight;
        if (shield > 0.001F) {
            shield(model, shield, pose.scarlet$recoil(), age);
        }
        float burst = pose.scarlet$burst() * weight;
        if (burst > 0.001F) {
            burst(model, burst);
        }
        strikes(model, pose.scarlet$rightStrike() * weight, pose.scarlet$leftStrike() * weight);
    }

    /**
     * Called at the end of {@code AvatarRenderer.setupRotations}: the hover bob and the lean into flight.
     */
    public static void rotate(AvatarRenderState state, PoseStack poseStack) {
        CastPoseState pose = (CastPoseState) state;
        float levitate = pose.scarlet$levitate();
        if (levitate <= 0.001F || state.isFallFlying) {
            return;
        }
        poseStack.translate(0.0F, bob(state.ageInTicks, levitate), 0.0F);
        float lean = pose.scarlet$lean();
        if (Math.abs(lean) > 0.01F) {
            poseStack.translate(0.0F, LEVITATION_PIVOT, 0.0F);
            poseStack.rotateDegrees(Axis.XP, -lean);
            poseStack.translate(0.0F, -LEVITATION_PIVOT, 0.0F);
        }
    }

    /**
     * How far a levitating body has risen from its resting height in its hover, in blocks.
     */
    public static float bob(float ageInTicks, float levitate) {
        return Mth.sin(ageInTicks * 0.1F) * BOB_HEIGHT * levitate;
    }

    private static void levitate(PlayerModel model, float weight, float age, float speed) {
        float breathe = Mth.sin(age * 0.08F);
        float drift = Mth.sin(age * 0.05F + 1.1F);
        float spread = 0.42F + drift * 0.05F - speed * 0.16F;
        float armX = -0.12F + speed * 0.6F;
        pose(model.rightArm, armX + breathe * 0.04F, 0.12F - speed * 0.1F, spread, weight);
        pose(model.leftArm, armX - breathe * 0.04F, -0.12F + speed * 0.1F, -spread, weight);
        pose(model.rightLeg, 0.08F + speed * 0.25F + breathe * 0.03F, 0.0F, 0.04F, weight);
        pose(model.leftLeg, 0.24F + speed * 0.2F - breathe * 0.03F, 0.0F, -0.04F, weight);
    }

    private static void shield(PlayerModel model, float weight, float recoil, float age) {
        float tremble = Mth.sin(age * 1.9F) * 0.015F;
        float aimX = -HALF_PI + model.head.xRot * 0.75F + recoil * 0.35F;
        float spread = 0.42F - recoil * 0.1F;
        pose(model.rightArm, aimX + tremble, model.head.yRot - spread, 0.0F, weight);
        pose(model.leftArm, aimX - tremble, model.head.yRot + spread, 0.0F, weight);
    }

    /**
     * The Hex bursting out of you: arms flung out to the sides, stance braced, head thrown back.
     */
    private static void burst(PlayerModel model, float weight) {
        pose(model.rightArm, -0.5F, 0.2F, 1.35F, weight);
        pose(model.leftArm, -0.5F, -0.2F, -1.35F, weight);
        model.rightLeg.zRot = Ease.lerp(model.rightLeg.zRot, 0.12F, weight);
        model.leftLeg.zRot = Ease.lerp(model.leftLeg.zRot, -0.12F, weight);
        model.head.xRot -= 0.3F * weight;
    }

    private static void pose(ModelPart part, float xRot, float yRot, float zRot, float weight) {
        part.xRot = Ease.lerp(part.xRot, xRot, weight);
        part.yRot = Ease.lerp(part.yRot, yRot, weight);
        part.zRot = Ease.lerp(part.zRot, zRot, weight);
    }

    private static void strikes(PlayerModel model, float right, float left) {
        if (right == 0.0F && left == 0.0F) {
            return;
        }
        float twist = strike(model.rightArm, model.head, right, 1.0F) + strike(model.leftArm, model.head, left, -1.0F);
        if (twist == 0.0F) {
            return;
        }
        // turn the torso the way vanilla's attack swing does: shoulders move with it, arms keep their aim
        model.body.yRot += twist;
        model.rightArm.z = Mth.sin(model.body.yRot) * 5.0F;
        model.rightArm.x = -Mth.cos(model.body.yRot) * 5.0F;
        model.leftArm.z = -Mth.sin(model.body.yRot) * 5.0F;
        model.leftArm.x = Mth.cos(model.body.yRot) * 5.0F;
        model.rightArm.yRot += twist * (1.0F - Math.clamp(right, 0.0F, 1.0F));
        model.leftArm.yRot += twist * (1.0F - Math.clamp(left, 0.0F, 1.0F));
    }

    /**
     * @param side 1 for the right arm, -1 for the left
     * @return how much this arm twists the torso
     */
    private static float strike(ModelPart arm, ModelPart head, float extension, float side) {
        if (extension == 0.0F) {
            return 0.0F;
        }
        float reach = Math.clamp(extension, 0.0F, 1.2F);
        float pull = Math.clamp(-extension, 0.0F, 0.35F);
        float blend = Math.min(1.0F, reach);
        float overshoot = Math.max(0.0F, reach - 1.0F);
        float aimX = -HALF_PI + head.xRot * 0.85F - overshoot * 0.6F;
        float aimY = head.yRot - side * 0.16F;
        arm.xRot = Ease.lerp(arm.xRot, aimX, blend) + pull * 1.15F;
        arm.yRot = Ease.lerp(arm.yRot, aimY, blend);
        arm.zRot = Ease.lerp(arm.zRot, 0.0F, blend) + side * pull * 0.25F;
        return side * (reach * 0.2F - pull * 0.12F);
    }
}
