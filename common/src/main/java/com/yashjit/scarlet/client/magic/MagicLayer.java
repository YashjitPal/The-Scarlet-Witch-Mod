package com.yashjit.scarlet.client.magic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.client.anim.CastPoseState;
import com.yashjit.scarlet.client.darkhold.CorruptionClient;
import com.yashjit.scarlet.client.darkhold.DarkholdBook;
import com.yashjit.scarlet.client.fx.ArmMagic;
import com.yashjit.scarlet.entity.DreamBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Magic on a player's arms, where their palms are this frame for effects that leave the hands, and the Darkhold floating
 * before them as they read it.
 */
public final class MagicLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    public static final float PALM_Y = 10.5F / 16.0F;

    public MagicLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet models) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(state.id);
        if (state.isInvisible || !(entity instanceof Player || entity instanceof DreamBody)) {
            return;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        boolean slim = state.skin.model() == PlayerModelType.SLIM;
        PlayerModel model = getParentModel();
        float time = (float) (minecraft.level.getGameTime() % 24000L) + partialTick;
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        Vec3[] palms = new Vec3[2];
        float darkness = entity instanceof DreamBody body ? CorruptionClient.darkness(body.corruption()) : CorruptionClient.darkness(entity);
        for (HumanoidArm arm : HumanoidArm.values()) {
            boolean right = arm == HumanoidArm.RIGHT;
            float intensity = entity instanceof Player player ? MagicVisuals.armIntensity(player, arm, partialTick) : MagicVisuals.MEDITATING;
            if (intensity > 0.01F) {
                ArmMagic.submitArm(poseStack, collector, model, right, slim, time, intensity, darkness);
            }
            poseStack.pushPose();
            model.root().translateAndRotate(poseStack);
            (right ? model.rightArm : model.leftArm).translateAndRotate(poseStack);
            float axisX = (right ? -1.0F : 1.0F) * (slim ? 0.5F : 1.0F) / 16.0F;
            Vector3f palm = poseStack.last().pose().transformPosition(axisX, PALM_Y, 0.0F, new Vector3f());
            poseStack.popPose();
            palms[arm.ordinal()] = camera.add(palm.x, palm.y, palm.z);
        }
        Hands.record(entity, palms[HumanoidArm.RIGHT.ordinal()], palms[HumanoidArm.LEFT.ordinal()]);
        if (entity instanceof Player player) {
            DarkholdBook.submitFloating(poseStack, collector, light, model, slim, player, ((CastPoseState) state).scarlet$read(), state.ageInTicks,
                    partialTick);
        }
    }
}
