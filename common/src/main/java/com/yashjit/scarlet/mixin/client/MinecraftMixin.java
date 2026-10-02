package com.yashjit.scarlet.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.yashjit.scarlet.client.magic.CastInput;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {

    /**
     * Right before vanilla checks whether a hand holds something it could use: by then the main hand has had its
     * chance to interact with the block or entity under the crosshair, and the off hand has not been tried yet.
     */
    @Inject(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z", ordinal = 1), cancellable = true)
    private void scarlet$castWithEmptyHand(CallbackInfo ci, @Local InteractionHand hand, @Local ItemStack heldItem) {
        if (hand == InteractionHand.MAIN_HAND && heldItem.isEmpty() && CastInput.tryCast((Minecraft) (Object) this)) {
            ci.cancel();
        }
    }
}
