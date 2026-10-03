package com.yashjit.scarlet.mixin.client;

import com.yashjit.scarlet.client.magic.MindControlClient;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Looking out through a mind you hold, you look up and down with your own mouse at once, rather than waiting for word
 * of where its head has turned.
 */
@Mixin(Entity.class)
abstract class EntityViewMixin {

    @Inject(method = "getViewXRot", at = @At("HEAD"), cancellable = true)
    private void scarlet$lookThroughHeldMind(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (MindControlClient.looksThrough((Entity) (Object) this)) {
            cir.setReturnValue(MindControlClient.pitch());
        }
    }

    @Inject(method = "getViewYRot", at = @At("HEAD"), cancellable = true)
    private void scarlet$turnThroughHeldMind(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (MindControlClient.looksThrough((Entity) (Object) this)) {
            cir.setReturnValue(MindControlClient.yaw());
        }
    }
}
