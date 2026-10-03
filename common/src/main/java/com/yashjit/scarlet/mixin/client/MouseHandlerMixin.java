package com.yashjit.scarlet.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yashjit.scarlet.client.dev.Showcase;
import com.yashjit.scarlet.client.hex.HomePlacement;
import com.yashjit.scarlet.client.magic.MindControlClient;
import com.yashjit.scarlet.client.magic.SpellWheel;
import com.yashjit.scarlet.client.magic.TelekinesisClient;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
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
     * While the spell wheel is open, the mouse steers its cursor instead of the camera. While someone holds your mind,
     * it turns nothing at all.
     */
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void scarlet$steerSpellWheel(double movementTime, CallbackInfo ci) {
        if (SpellWheel.isOpen()) {
            SpellWheel.mouseMoved(accumulatedDX, accumulatedDY);
            ci.cancel();
        } else if (MindControlClient.held()) {
            ci.cancel();
        }
    }

    /**
     * Inside a mind you hold, the mouse turns its head rather than yours.
     */
    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void scarlet$turnWhatYouHold(LocalPlayer player, double dx, double dy, Operation<Void> original) {
        if (MindControlClient.inside()) {
            MindControlClient.turn(dx, dy);
        } else {
            original.call(player, dx, dy);
        }
    }

    /**
     * While you hold something with Telekinesis, scrolling draws it nearer or pushes it away instead of changing slot;
     * while you choose where your home goes, it turns it.
     */
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void scarlet$pullWhatYouHold(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (Showcase.ignoresPlayerInput() || TelekinesisClient.scroll(yOffset) || HomePlacement.scroll(yOffset)) {
            ci.cancel();
        }
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void scarlet$ignoreClicksDuringShowcase(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        if (Showcase.ignoresPlayerInput()) {
            ci.cancel();
        }
    }

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void scarlet$ignoreMovesDuringShowcase(long window, double x, double y, double dx, double dy, CallbackInfo ci) {
        if (Showcase.ignoresPlayerInput()) {
            ci.cancel();
        }
    }
}
