package com.yashjit.scarlet.mixin.client;

import com.yashjit.scarlet.client.hex.HexSkyClient;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Notes which of the client's clocks is the overworld's, whose day a Hex's sky can set to its own hour.
 */
@Mixin(ClientClockManager.class)
abstract class ClientClockManagerMixin {

    @Inject(method = "getInstance", at = @At("RETURN"))
    private void scarlet$noteOverworldClock(Holder<WorldClock> definition, CallbackInfoReturnable<ClientClockManager.ClientClockInstance> cir) {
        HexSkyClient.recordClock(definition.is(WorldClocks.OVERWORLD), cir.getReturnValue());
    }
}
