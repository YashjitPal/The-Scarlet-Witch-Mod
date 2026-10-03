package com.yashjit.scarlet.client.magic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.client.fx.ArmMagic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Magic on a player's arms, and where their palms are this frame for effects that leave the hands.
 */
public final class MagicLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    static final float PALM_Y = 10.5F / 16.0F;

    public MagicLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet models) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (state.isInvisible || minecraft.level == null || !(minecraft.level.getEntity(state.id) instanceof Player player)) {
            return;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        boolean slim = state.skin.model() == PlayerModelType.SLIM;
        PlayerModel model = getParentModel();
        float time = (float) (minecraft.level.getGameTime() % 24000L) + partialTick;
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        Vec3[] palms = new Vec3[2];
        for (HumanoidArm arm : HumanoidArm.values()) {
            boolean right = arm == HumanoidArm.RIGHT;
            float intensity = MagicVisuals.armIntensity(player, arm, partialTick);
            if (intensity > 0.01F) {
                ArmMagic.submitArm(poseStack, collector, model, right, slim, time, intensity);
            }
            poseStack.pushPose();
            model.root().translateAndRotate(poseStack);
            (right ? model.rightArm : model.leftArm).translateAndRotate(poseStack);
            float axisX = (right ? -1.0F : 1.0F) * (slim ? 0.5F : 1.0F) / 16.0F;
            Vector3f palm = poseStack.last().pose().transformPosition(axisX, PALM_Y, 0.0F, new Vector3f());
            poseStack.popPose();
            palms[arm.ordinal()] = camera.add(palm.x, palm.y, palm.z);
        }
        Hands.record(player, palms[HumanoidArm.RIGHT.ordinal()], palms[HumanoidArm.LEFT.ordinal()]);
    }
}
