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
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("TAIL"))
    private void scarlet$layThrown(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
        ThrownPose.rotate(state, poseStack);
    }
}
