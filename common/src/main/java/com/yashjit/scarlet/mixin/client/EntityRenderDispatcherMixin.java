package com.yashjit.scarlet.mixin.client;

import com.yashjit.scarlet.client.hex.ResidentRenderer;
import com.yashjit.scarlet.client.hex.ResidentsClient;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws a Hex's townspeople as people. Their render state is the one players are drawn from, so drawing it goes to
 * the player renderer by itself; only reading them needs pointing elsewhere.
 */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {

    @Inject(method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;", at = @At("HEAD"),
            cancellable = true)
    private void scarlet$townspeople(Entity entity, CallbackInfoReturnable<EntityRenderer<?, ?>> cir) {
        ResidentRenderer renderer = ResidentRenderer.instance();
        if (renderer != null && entity instanceof LivingEntity && ResidentsClient.drawsAsResident(entity)) {
            cir.setReturnValue(renderer);
        }
    }

    @ModifyArg(method = "onResourceManagerReload", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderers;createEntityRenderers(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;)Ljava/util/Map;"))
    private EntityRendererProvider.Context scarlet$bakeTownspeople(EntityRendererProvider.Context context) {
        ResidentRenderer.bake(context);
        return context;
    }
}
