package com.yashjit.scarlet.mixin.client;

import com.yashjit.scarlet.client.magic.MindControlClient;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Living things turn their view with their heads, which a mind held from inside turns with your mouse at once.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityViewMixin {

    @Inject(method = "getViewYRot", at = @At("HEAD"), cancellable = true)
    private void scarlet$turnThroughHeldMind(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (MindControlClient.looksThrough((Entity) (Object) this)) {
            cir.setReturnValue(MindControlClient.yaw());
        }
    }
}
