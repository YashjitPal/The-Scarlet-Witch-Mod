package com.yashjit.scarlet.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yashjit.scarlet.client.fx.ShieldFx;
import com.yashjit.scarlet.client.fx.ShockwaveFx;
import com.yashjit.scarlet.client.hex.Founding;
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
    /** How far back the body arches while founding a Hex, in degrees, about the hips. */
    private static final float FOUNDING_ARCH = 32.0F;
    private static final float FOUNDING_PIVOT = 0.75F;
    /** Lowest an arm casting a beam aims, in radians below level, so the hand stays up however low the magic lands. */
    private static final float BEAM_LOWEST = 0.45F;

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
        float gather = 0.0F;
        float hold = 0.0F;
        float tear = 0.0F;
        float spread = 0.0F;
        float found = 0.0F;
        float control = 0.0F;
        float beam = 0.0F;
        float raise = 0.0F;
        float read = 0.0F;
        boolean beamRight = true;
        if (avatar instanceof Player player) {
            double now = player.level().getGameTime() + partialTick;
            right = CastGestures.extension(player, HumanoidArm.RIGHT, now);
            left = CastGestures.extension(player, HumanoidArm.LEFT, now);
            PoseBlends.Blend blend = PoseBlends.of(player);
            shield = blend.shield;
            recoil = ShieldFx.recoil(player, now);
            levitate = blend.levitate;
            lean = blend.lean;
            burst = Math.max(HexClient.burst(player, now), ShockwaveFx.fling(player, now));
            gather = ShockwaveFx.gather(player, now);
            hold = blend.hold;
            tear = blend.tear;
            spread = blend.spread;
            control = blend.control;
            found = Founding.lift(player, now);
            beam = blend.beam;
            raise = blend.raise;
            read = blend.read;
            beamRight = player.getMainArm() == HumanoidArm.RIGHT;
        }
        ((CastPoseState) state).scarlet$set(right, left, shield, recoil, levitate, lean, burst, gather, hold, tear, spread);
        ((CastPoseState) state).scarlet$setFound(found);
        ((CastPoseState) state).scarlet$setControl(control);
        ((CastPoseState) state).scarlet$setBeam(beam, beamRight, raise);
        ((CastPoseState) state).scarlet$setRead(read);
        double now = avatar.level().getGameTime() + partialTick;
        ((CastPoseState) state).scarlet$setMeditation(Meditation.sit(avatar, now), Meditation.lift(avatar, now), Meditation.offset(avatar, now));
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
        float hold = pose.scarlet$hold() * weight;
        if (hold > 0.001F) {
            hold(model, hold, age);
        }
        float control = pose.scarlet$control() * weight;
        if (control > 0.001F) {
            control(model, control, age);
        }
        float beam = pose.scarlet$beam() * weight;
        if (beam > 0.001F) {
            beam(model, beam, pose.scarlet$beamRight(), age);
        }
        float raise = pose.scarlet$raise() * weight;
        if (raise > 0.001F) {
            raise(model, raise, age);
        }
        float read = pose.scarlet$read() * weight;
        if (read > 0.001F) {
            read(model, read, age);
        }
        float sit = pose.scarlet$sit() * weight;
        if (sit > 0.001F) {
            meditate(model, sit, pose.scarlet$lift() * weight, age);
        }
        float tear = pose.scarlet$tear() * weight;
        if (tear > 0.001F) {
            tear(model, tear, pose.scarlet$spread(), age);
        }
        float gather = pose.scarlet$gather() * weight;
        if (gather > 0.001F) {
            gather(model, gather, age);
        }
        float found = pose.scarlet$found() * weight;
        if (found > 0.001F) {
            found(model, found, age);
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
        if (pose.scarlet$sit() > 0.001F) {
            // sat down on the ground, then risen above it
            poseStack.translate(0.0F, pose.scarlet$meditationOffset(), 0.0F);
        }
        float found = pose.scarlet$found();
        if (found > 0.001F) {
            // founding a Hex: the whole body arched back from the hips, chest thrown up to the sky, swaying slowly
            float sway = Mth.sin(state.ageInTicks * 0.06F) * 3.0F;
            poseStack.translate(0.0F, FOUNDING_PIVOT, 0.0F);
            poseStack.rotateDegrees(Axis.XP, (FOUNDING_ARCH + sway) * found);
            poseStack.translate(0.0F, -FOUNDING_PIVOT, 0.0F);
        }
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
     * Telekinesis: both arms reach out at what is held, spread apart, the leading hand higher, fingers clawing at the
     * air in slow waves and shaking with the strain.
     */
    private static void hold(PlayerModel model, float weight, float age) {
        float tremble = Mth.sin(age * 2.2F) * 0.02F;
        float claw = Mth.sin(age * 0.16F) * 0.06F;
        float aimX = -HALF_PI + model.head.xRot * 0.85F;
        pose(model.rightArm, aimX - 0.12F + tremble, model.head.yRot - 0.18F, claw, weight);
        pose(model.leftArm, aimX + 0.1F - tremble, model.head.yRot + 0.32F, -claw, weight);
    }

    /**
     * Painting: the casting arm flung out at where the magic lands, high and off to the side, never dropping much below
     * level however low it aims, and shaking with the force of it; the other arm held out low for balance.
     */
    private static void beam(PlayerModel model, float weight, boolean right, float age) {
        float tremble = Mth.sin(age * 2.3F) * 0.02F;
        float side = right ? 1.0F : -1.0F;
        float aimX = -HALF_PI + Math.min(model.head.xRot, BEAM_LOWEST) * 0.9F;
        pose(right ? model.rightArm : model.leftArm, aimX + tremble, model.head.yRot - side * 0.3F, side * 0.08F, weight);
        pose(right ? model.leftArm : model.rightArm, -0.35F, side * 0.15F, -side * 0.3F, weight);
    }

    /**
     * Raising a home far off: both arms thrown up and out toward it, palms open, the magic pouring out of both, swaying
     * and trembling with it.
     */
    private static void raise(PlayerModel model, float weight, float age) {
        float tremble = Mth.sin(age * 2.0F) * 0.02F;
        float sway = Mth.sin(age * 0.11F) * 0.05F;
        float aimX = -HALF_PI - 0.35F + Math.min(model.head.xRot, BEAM_LOWEST) * 0.6F;
        pose(model.rightArm, aimX + tremble + sway, model.head.yRot - 0.42F, 0.1F, weight);
        pose(model.leftArm, aimX - tremble - sway, model.head.yRot + 0.42F, -0.1F, weight);
    }

    /**
     * Reading the Darkhold: the book held open before the chest in both hands, the head bowed over it, swaying a little
     * as if it were heavy.
     */
    private static void read(PlayerModel model, float weight, float age) {
        float sway = Mth.sin(age * 0.07F) * 0.03F;
        pose(model.rightArm, -0.95F + sway, -0.38F, 0.0F, weight);
        pose(model.leftArm, -0.95F - sway, 0.38F, 0.0F, weight);
        model.head.xRot = Ease.lerp(model.head.xRot, Math.max(model.head.xRot, 0.45F), weight);
    }

    /**
     * Dreamwalking: sat with the legs crossed before the body, the hands held open over the knees, the head level and
     * still, breathing slowly. Risen into the air, the hands float a little further out.
     */
    private static void meditate(PlayerModel model, float sit, float lift, float age) {
        float breathe = Mth.sin(age * 0.06F);
        pose(model.rightLeg, -HALF_PI + 0.06F, -0.48F, 0.0F, sit);
        pose(model.leftLeg, -HALF_PI - 0.06F, 0.48F, 0.0F, sit);
        float open = 0.16F + 0.12F * lift + breathe * 0.02F;
        pose(model.rightArm, -0.8F + breathe * 0.03F, -0.12F, open, sit);
        pose(model.leftArm, -0.8F + breathe * 0.03F, 0.12F, -open, sit);
        model.head.xRot = Ease.lerp(model.head.xRot, 0.04F, sit);
        model.head.yRot = Ease.lerp(model.head.yRot, 0.0F, sit);
        model.body.xRot = Ease.lerp(model.body.xRot, 0.0F, sit);
    }

    /**
     * Mind Control: both arms reach out at the mind held, the right hand higher, as if holding its strings, and work
     * them in slow alternating pulls, the head bowed into the stare and trembling with it.
     */
    private static void control(PlayerModel model, float weight, float age) {
        float tremble = Mth.sin(age * 2.0F) * 0.018F;
        float pull = Mth.sin(age * 0.12F) * 0.12F;
        float aimX = -HALF_PI + model.head.xRot * 0.8F;
        pose(model.rightArm, aimX - 0.28F + pull + tremble, model.head.yRot - 0.1F, 0.06F, weight);
        pose(model.leftArm, aimX + 0.06F - pull - tremble, model.head.yRot + 0.3F, -0.06F, weight);
        model.head.xRot += 0.12F * weight;
    }

    /**
     * Tearing a Hex open: both hands grip the wall before you, together at first, and pull it apart, further as it
     * opens, shaking with the strain of it.
     */
    private static void tear(PlayerModel model, float weight, float spread, float age) {
        float tremble = Mth.sin(age * 2.4F) * 0.035F;
        float aimX = -HALF_PI + model.head.xRot * 0.6F + 0.1F;
        float apart = -0.3F + 1.3F * spread;
        pose(model.rightArm, aimX + tremble, model.head.yRot + apart, 0.0F, weight);
        pose(model.leftArm, aimX - tremble, model.head.yRot - apart, 0.0F, weight);
        model.head.xRot -= 0.08F * weight * spread;
    }

    /**
     * Gathering a Shockwave: both hands drawn in before the chest as if holding something about to burst, shaking with
     * it.
     */
    private static void gather(PlayerModel model, float weight, float age) {
        float tremble = Mth.sin(age * 2.6F) * 0.035F;
        pose(model.rightArm, -1.2F + tremble, -0.62F, 0.0F, weight);
        pose(model.leftArm, -1.2F - tremble, 0.62F, 0.0F, weight);
        model.head.xRot += 0.15F * weight;
    }

    /**
     * Founding a Hex, floating as the house rises around you: head thrown back to the sky, arms hanging open out and
     * back from the sides with the palms turned out, legs loose beneath, the whole figure breathing slowly with the magic
     * pouring out of it.
     */
    private static void found(PlayerModel model, float weight, float age) {
        float breathe = Mth.sin(age * 0.07F);
        float tremble = Mth.sin(age * 1.7F) * 0.02F;
        pose(model.rightArm, 0.38F + breathe * 0.05F + tremble, -0.25F, 0.62F + breathe * 0.06F, weight);
        pose(model.leftArm, 0.38F + breathe * 0.05F - tremble, 0.25F, -0.62F - breathe * 0.06F, weight);
        pose(model.rightLeg, 0.18F + breathe * 0.03F, 0.0F, 0.06F, weight);
        pose(model.leftLeg, 0.32F - breathe * 0.03F, 0.0F, -0.06F, weight);
        model.head.xRot = Ease.lerp(model.head.xRot, -0.75F, weight);
        model.head.yRot = Ease.lerp(model.head.yRot, 0.0F, weight);
    }

    /**
     * A Hex or a Shockwave bursting out of you: arms flung out to the sides, stance braced, head thrown back.
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
