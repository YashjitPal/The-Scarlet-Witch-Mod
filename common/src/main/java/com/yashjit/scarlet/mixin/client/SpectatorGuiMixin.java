package com.yashjit.scarlet.mixin.client;

import com.yashjit.scarlet.client.darkhold.DreamwalkClient;
import net.minecraft.client.gui.components.spectator.SpectatorGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A dreamwalker's spirit goes about as a spectator, without a spectator's menu for going wherever it likes.
 */
@Mixin(SpectatorGui.class)
abstract class SpectatorGuiMixin {

    @Inject(method = {"onHotbarSelected", "onMouseScrolled", "onHotbarActionKeyPressed"}, at = @At("HEAD"), cancellable = true)
    private void scarlet$noMenuWhileAway(CallbackInfo ci) {
        if (DreamwalkClient.away()) {
            ci.cancel();
        }
    }
}
