package com.yashjit.scarlet.mixin.client;

import com.yashjit.scarlet.client.dev.Showcase;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void scarlet$ignoreDuringShowcase(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (Showcase.ignoresPlayerInput()) {
            ci.cancel();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void scarlet$ignoreTypingDuringShowcase(long window, CharacterEvent event, CallbackInfo ci) {
        if (Showcase.ignoresPlayerInput()) {
            ci.cancel();
        }
    }
}
