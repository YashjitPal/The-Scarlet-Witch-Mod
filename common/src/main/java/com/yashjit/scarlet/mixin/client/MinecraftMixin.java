package com.yashjit.scarlet.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.yashjit.scarlet.client.hex.HomePlacement;
import com.yashjit.scarlet.client.magic.CastInput;
import com.yashjit.scarlet.client.magic.MindControlClient;
import com.yashjit.scarlet.client.magic.TelekinesisClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {

    /**
     * Inside a mind you hold, your own body uses nothing around it; held yourself, a click only fights it.
     */
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void scarlet$noUseWhileElsewhere(CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (MindControlClient.inside() || MindControlClient.held() || HomePlacement.use(minecraft)) {
            ci.cancel();
        } else if (CastInput.claimsClick(minecraft)) {
            CastInput.tryCast(minecraft);
            ci.cancel();
        }
    }

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

    /**
     * Attacking while you hold something with Telekinesis throws it; inside a mind you hold, what you hold strikes;
     * held yourself, nothing happens.
     */
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void scarlet$throwWhatYouHold(CallbackInfoReturnable<Boolean> cir) {
        if (MindControlClient.held() || HomePlacement.attack((Minecraft) (Object) this)) {
            cir.setReturnValue(false);
        } else if (MindControlClient.attack() || TelekinesisClient.attack()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void scarlet$noBreakingWhileHolding(boolean attacking, CallbackInfo ci) {
        if (TelekinesisClient.channeling() || MindControlClient.inside() || MindControlClient.held() || HomePlacement.holdsClick()) {
            ci.cancel();
        }
    }
}
