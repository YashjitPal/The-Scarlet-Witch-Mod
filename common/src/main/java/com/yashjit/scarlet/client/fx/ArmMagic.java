package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;

/**
 * Scarlet wisps spiraling around the forearms with a hot glow cupped in each palm. Drawn in the arm's own space so they
 * follow every pose exactly, in third and first person.
 */
public final class ArmMagic {

    private static final int STRANDS = 3;
    private static final int BEADS = 26;

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
        GlowPass.submit(collector, poseStack, arm(right, slim, time, intensity, darkness));
        poseStack.popPose();
    }

    /**
     * Glow geometry for one arm, in the space of its shoulder pivot.
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
        Glow.Billboard axes = Glow.billboard(pose);
        for (int k = 0; k < STRANDS; k++) {
            float strandPhase = k * (float) (Math.PI * 2 / STRANDS);
            for (int i = 0; i < BEADS; i++) {
                float s = i / (float) (BEADS - 1);
                float angle = s * (float) (Math.PI * 2.4) + time * 0.22F + strandPhase;
                float radius = (2.7F + 0.6F * (float) Math.sin(time * 0.31F + s * 5.0F + k)) / 16.0F;
                float x = axisX + (float) Math.cos(angle) * radius;
                float z = (float) Math.sin(angle) * radius;
                float y = (3.8F + s * 6.6F) / 16.0F;
                float envelope = (float) Math.sin(s * Math.PI);
                // a bright pulse travels along each strand so it reads as flowing energy
                float travel = (float) Math.pow(0.5 + 0.5 * Math.sin(s * 9.0F - time * 0.55F + k * 2.1F), 3);
                float alpha = intensity * envelope * (0.55F + 0.45F * travel);
                float size = (0.24F + 0.2F * envelope + 0.12F * travel) / 16.0F;
                int core = travel > 0.8F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET;
                Glow.spark(buffer, pose, axes, x, y, z, size, core, ScarletPalette.SCARLET, Math.min(1.0F, alpha * 1.3F));
            }
        }
        float palm = intensity * (0.75F + 0.25F * (float) Math.sin(time * 0.5F));
        Glow.disc(buffer, pose, axes, axisX, 10.6F / 16.0F, 0.0F, 4.5F / 16.0F, Glow.withAlpha(ScarletPalette.SCARLET, palm * 0.45F));
        Glow.disc(buffer, pose, axes, axisX, 10.6F / 16.0F, 0.0F, 1.6F / 16.0F, Glow.withAlpha(ScarletPalette.CORE, palm * 0.8F));
    }
}
