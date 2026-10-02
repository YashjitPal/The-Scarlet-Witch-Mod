package com.yashjit.scarlet.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.yashjit.scarlet.client.dev.Showcase;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla never draws your own player unless the camera is that player. The development showcase moves the camera
 * freely around you, so it needs the player drawn anyway.
 */
@Mixin(LevelExtractor.class)
abstract class LevelExtractorMixin {

    @ModifyExpressionValue(method = "extractVisibleEntities", at = @At(value = "CONSTANT", args = "classValue=net/minecraft/client/player/LocalPlayer"))
    private boolean scarlet$drawLocalPlayerForShowcase(boolean isLocalPlayer) {
        return isLocalPlayer && !Showcase.isFreeCameraActive();
    }
}
