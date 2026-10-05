package com.yashjit.scarlet.mixin.client;

import com.yashjit.scarlet.client.dev.Showcase;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {

    /**
     * The player moves by their keys, and tells the server where they are and where they look, only while the view is
     * their own. Under the showcase's free camera they play on as if it were.
     */
    @Inject(method = "isControlledCamera", at = @At("HEAD"), cancellable = true)
    private void scarlet$playOnUnderTheFreeCamera(CallbackInfoReturnable<Boolean> cir) {
        if (Showcase.isFreeCameraActive()) {
            cir.setReturnValue(true);
        }
    }
}
