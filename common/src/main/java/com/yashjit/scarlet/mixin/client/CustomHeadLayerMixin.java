package com.yashjit.scarlet.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.client.costume.SpeedLook;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CustomHeadLayer.class)
abstract class CustomHeadLayerMixin {
    @Inject(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/CustomHeadLayer;translateToHead(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/entity/layers/CustomHeadLayer$Transforms;)V"))
    private void scarlet$lowerGoggles(PoseStack stack, SubmitNodeCollector collector, int light, LivingEntityRenderState state,
                                       float yRot, float xRot, CallbackInfo ci) {
        if (state instanceof SpeedLook.Pose pose) stack.translate(0, pose.scarlet$goggles() * 2.35F / 16, 0);
    }
}
