package com.yashjit.scarlet.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.yashjit.scarlet.client.hex.HexScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {

    @Shadow
    @Final
    private RenderTarget mainRenderTarget;

    @Shadow
    @Final
    private RenderTarget hud3DTarget;

    /**
     * Keeps the world's depth intact past the hand, the way vanilla does for its own screen effects.
     */
    @ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z"))
    private boolean scarlet$keepWorldDepthForTheHex(boolean noScreenEffects) {
        return noScreenEffects && !HexScreen.wanted();
    }

    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"))
    private Matrix4f scarlet$captureHexView(Matrix4f projection, @Local CameraRenderState camera) {
        HexScreen.captureView(projection, camera);
        return projection;
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;applyPostEffects()V"))
    private void scarlet$drawHexes(CallbackInfo ci) {
        HexScreen.render(mainRenderTarget, hud3DTarget);
    }
}
