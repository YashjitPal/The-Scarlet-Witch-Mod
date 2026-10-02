package com.yashjit.scarlet.mixin.client;

import com.yashjit.scarlet.client.magic.SpellWheel;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    /**
     * While the spell wheel is open, the mouse steers its cursor instead of the camera.
     */
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void scarlet$steerSpellWheel(double movementTime, CallbackInfo ci) {
        if (SpellWheel.isOpen()) {
            SpellWheel.mouseMoved(accumulatedDX, accumulatedDY);
            ci.cancel();
        }
    }
}
