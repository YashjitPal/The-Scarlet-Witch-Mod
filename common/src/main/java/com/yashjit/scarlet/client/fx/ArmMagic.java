package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.client.magic.MagicLayer;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.client.render.PixelSprite;
import com.yashjit.scarlet.client.render.Pixels;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * Magic on the arms, as pixel art: wisps of it curling up around the forearm, cooler toward the elbow and hot where
 * light runs along them, and a ball of it churning in the palm, its rim flickering. Drawn in the arm's own space on a
 * grid pinned at the palm, so it follows every pose exactly, in the third person and the first.
 */
public final class ArmMagic {

    private static final int STRANDS = 3;
    private static final int POINTS = 14;
    private static final float TAU = (float) (Math.PI * 2);

    private ArmMagic() {
    }

    /**
     * One arm of a player seen in the world. Pose stack at the model root, as inside a render layer.
     *
     * @param darkness how far the Darkhold's corruption darkens it
     */
    public static void submitArm(PoseStack poseStack, SubmitNodeCollector collector, PlayerModel model, boolean right, boolean slim, float time,
                                 float intensity, float darkness) {
        poseStack.pushPose();
        model.root().translateAndRotate(poseStack);
        (right ? model.rightArm : model.leftArm).translateAndRotate(poseStack);
        GlowPass.submitPixels(collector, poseStack, arm(right, slim, time, intensity, darkness));
        poseStack.popPose();
    }

    /**
     * Pixels for one arm, in the space of its shoulder pivot.
     */
    public static SubmitNodeCollector.CustomGeometryRenderer arm(boolean right, boolean slim, float time, float intensity, float darkness) {
        float axisX = (right ? -1.0F : 1.0F) * (slim ? 0.5F : 1.0F) / 16.0F;
        float t = time + (right ? 0.0F : 37.0F);
        return (pose, buffer) -> {
            try (Glow.Darkening ignored = Glow.darkening(darkness)) {
                emit(buffer, pose, axisX, t, intensity);
            }
        };
    }

    private static void emit(VertexConsumer buffer, PoseStack.Pose pose, float axisX, float time, float intensity) {
        PixelSprite sprite = new PixelSprite(pose, axisX, MagicLayer.PALM_Y, 0.0F);
        int frame = (int) Math.floor(time);
        for (int k = 0; k < STRANDS; k++) {
            float phase = k * TAU / STRANDS;
            Vector3f previous = null;
            for (int i = 0; i <= POINTS; i++) {
                // from just below the elbow, s = 0, down to the wrist
                float s = i / (float) POINTS;
                float angle = s * TAU * 1.2F + time * 0.22F + phase;
                float radius = (2.7F + 0.6F * Mth.sin(time * 0.31F + s * 5.0F + k)) / 16.0F;
                Vector3f at = new Vector3f(axisX + Mth.cos(angle) * radius, (3.8F + s * 6.6F) / 16.0F, Mth.sin(angle) * radius);
                if (previous != null) {
                    float envelope = Mth.sin(s * (float) Math.PI);
                    // light running down each strand, so it reads as flowing
                    float travel = (float) Math.pow(0.5 + 0.5 * Mth.sin(s * 9.0F - time * 0.55F + k * 2.1F), 3);
                    if (Pixels.shows(intensity * (0.45F + 0.75F * envelope), i + frame, k)) {
                        int step = travel > 0.75F ? Pixels.PINK : travel > 0.35F ? Pixels.BRIGHT : s < 0.3F ? Pixels.CRIMSON : Pixels.SCARLET;
                        sprite.line(previous, at, Pixels.opaque(step), 1, 1);
                    }
                }
                previous = at;
            }
        }
        palm(sprite, time, intensity);
        sprite.draw(buffer);
    }

    /**
     * The ball of magic churning in the palm.
     */
    private static void palm(PixelSprite sprite, float time, float intensity) {
        Wisps.orb(sprite, (1.5F + 0.5F * Mth.sin(time * 0.5F)) * intensity + 0.5F, 1.5F * intensity, (int) Math.floor(time), 23);
    }
}
