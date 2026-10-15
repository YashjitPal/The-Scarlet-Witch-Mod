package com.yashjit.scarlet.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yashjit.scarlet.client.hex.Ejections;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Thrown out of a Hex the way Monica is thrown out of Westview: blasted away backward, laid back on the air with the
 * arms and legs flung out before them toward whoever threw them, flailing; then down on their back on the ground,
 * rolling over once as they skid, arms out, winded, until they get up.
 */
public final class ThrownPose {

    /** How high off the ground the middle of a body lying on its back is, in blocks. */
    private static final float LYING_HEIGHT = 0.16F;

    private ThrownPose() {
    }

    /** Called while extracting any living thing's render state. */
    public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTick) {
        Ejections.Pose pose = Ejections.pose(entity, partialTick);
        ThrownRenderState thrown = (ThrownRenderState) state;
        if (pose == null) {
            thrown.scarlet$setThrown(0.0F, 0.0F, 0.0F, 0.0F);
            return;
        }
        // facing back the way it was thrown from, its head held straight with its body
        state.bodyRot = pose.facing();
        state.yRot = 0.0F;
        state.xRot = 0.0F;
        state.walkAnimationSpeed = 0.0F;
        thrown.scarlet$setThrown(pose.tilt(), pose.roll(), pose.flail(), pose.lie());
    }

    /**
     * Called at the end of {@code LivingEntityRenderer.setupRotations}, its body turned the way it faces: laid back about
     * its middle, which follows the throw, and brought down to the ground as it lies there, then rolled about its length.
     */
    public static void rotate(LivingEntityRenderState state, PoseStack poseStack) {
        ThrownRenderState thrown = (ThrownRenderState) state;
        float tilt = thrown.scarlet$thrownTilt();
        float roll = thrown.scarlet$thrownRoll();
        if (tilt == 0.0F && roll == 0.0F) {
            return;
        }
        float middle = state.boundingBoxHeight * 0.5F;
        poseStack.translate(0.0F, Mth.lerp(thrown.scarlet$thrownLie(), middle, LYING_HEIGHT), 0.0F);
        poseStack.rotateDegrees(Axis.XP, tilt);
        poseStack.rotateDegrees(Axis.YP, roll);
        poseStack.translate(0.0F, -middle, 0.0F);
    }

    /**
     * Called once the model is posed: in the air, the arms and legs flung out before the body toward whoever threw it,
     * flailing; on the ground, the arms out to the sides and the legs straight, a little apart.
     */
    public static void limbs(PlayerModel model, LivingEntityRenderState state) {
        ThrownRenderState thrown = (ThrownRenderState) state;
        float tilt = thrown.scarlet$thrownTilt();
        if (tilt == 0.0F) {
            return;
        }
        float age = state.ageInTicks;
        float flail = thrown.scarlet$thrownFlail();
        float weight = Math.min(1.0F, tilt / 40.0F);
        float flung = flail * weight;
        float sprawled = (1.0F - flail) * weight;
        float wave = Mth.sin(age * 0.9F);
        float kick = Mth.sin(age * 1.1F + 1.3F);
        pose(model.rightArm, -2.3F * flung + 0.15F * sprawled + wave * 0.35F * flung, 0.0F, (0.55F + wave * 0.2F) * flung + 1.25F * sprawled, weight);
        pose(model.leftArm, -2.3F * flung + 0.15F * sprawled - wave * 0.35F * flung, 0.0F, -(0.55F - wave * 0.2F) * flung - 1.25F * sprawled, weight);
        pose(model.rightLeg, (-0.9F + kick * 0.3F) * flung, 0.0F, 0.18F * flung + 0.1F * sprawled, weight);
        pose(model.leftLeg, (-0.9F - kick * 0.3F) * flung, 0.0F, -0.18F * flung - 0.1F * sprawled, weight);
        model.head.xRot = Mth.lerp(weight, model.head.xRot, -0.35F * flung);
        model.head.yRot = Mth.lerp(weight, model.head.yRot, 0.0F);
    }

    private static void pose(net.minecraft.client.model.geom.ModelPart part, float xRot, float yRot, float zRot, float weight) {
        part.xRot = Mth.lerp(weight, part.xRot, xRot);
        part.yRot = Mth.lerp(weight, part.yRot, yRot);
        part.zRot = Mth.lerp(weight, part.zRot, zRot);
    }
}
