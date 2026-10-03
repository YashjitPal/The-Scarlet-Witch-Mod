package com.yashjit.scarlet.mixin;

import com.yashjit.scarlet.darkhold.Dreamwalk;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A dreamwalker's spirit carried into another dimension hasn't been there, as far as advancements go.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {

    @Inject(method = "triggerDimensionChangeTriggers", at = @At("HEAD"), cancellable = true)
    private void scarlet$spiritsTravelUnseen(ServerLevel oldLevel, CallbackInfo ci) {
        if (Dreamwalk.travelling((ServerPlayer) (Object) this)) {
            ci.cancel();
        }
    }
}
