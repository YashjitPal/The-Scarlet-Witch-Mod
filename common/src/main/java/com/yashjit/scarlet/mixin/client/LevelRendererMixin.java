package com.yashjit.scarlet.mixin.client;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.yashjit.scarlet.client.render.GlowPass;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {

    @Inject(method = "prepareTranslucents", at = @At("HEAD"))
    private void scarlet$prepareGlows(CallbackInfo ci) {
        GlowPass.prepare();
    }

    @Inject(method = "executeClassicTransparency", at = @At("TAIL"))
    private void scarlet$drawGlows(ChunkSectionsToRender chunkSectionsToRender, FeatureRenderDispatcher.PreparedFrame featureFrame,
                                   RenderPass renderPass, CallbackInfo ci) {
        GlowPass.draw(renderPass);
    }
}
