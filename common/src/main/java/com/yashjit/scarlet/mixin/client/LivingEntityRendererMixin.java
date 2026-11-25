package com.yashjit.scarlet.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.client.anim.ThrownPose;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lays anyone thrown out of a Hex back on the air as they fly, and down on the ground where they come to rest, whatever
 * they are and however they are drawn.
 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void scarlet$thrown(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        ThrownPose.extract(entity, state, partialTick);
        ((com.yashjit.scarlet.client.fx.AshState)state).scarlet$setAsh(com.yashjit.scarlet.client.fx.AgathaFx.ash(entity.getId()));
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z"))
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void scarlet$drainedSkin(LivingEntityRenderState state, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector,
                                       net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
        float ash = ((com.yashjit.scarlet.client.fx.AshState)state).scarlet$ash();
        if (ash <= 0 || state.isInvisible) return;
        var model = ((LivingEntityRenderer)(Object)this).getModel();
        collector.submitModel(model, state, pose, net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(ASH_SKIN),
                state.lightCoords, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                net.minecraft.util.ARGB.white(ash * 0.90F), null, 0);
    }

    @org.spongepowered.asm.mixin.Unique
    private static final net.minecraft.resources.Identifier ASH_SKIN = com.yashjit.scarlet.Scarlet.id("textures/entity/drained_ash.png");

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("TAIL"))
    private void scarlet$layThrown(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
        ThrownPose.rotate(state, poseStack);
    }
}
