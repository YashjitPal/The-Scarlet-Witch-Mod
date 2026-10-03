package com.yashjit.scarlet.mixin.client;

import com.yashjit.scarlet.client.hex.HexSkyClient;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Inside a Hex whose caster has set its hour, the overworld's clock reads that hour, so the sun, the stars, the sky's
 * colors and its light all follow it.
 */
@Mixin(ClientClockManager.ClientClockInstance.class)
abstract class ClientClockInstanceMixin {

    @Inject(method = "totalTicks", at = @At("RETURN"), cancellable = true)
    private void scarlet$hexHour(CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(HexSkyClient.clockTicks((ClientClockManager.ClientClockInstance) (Object) this, cir.getReturnValueJ()));
    }
}
