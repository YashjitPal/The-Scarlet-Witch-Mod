package com.yashjit.scarlet.mixin.client;

import com.yashjit.scarlet.client.hex.HexSkyClient;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Inside a Hex whose caster has set its weather, the client sees that weather: its rain falls, its sky darkens.
 */
@Mixin(Level.class)
abstract class LevelWeatherMixin {

    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void scarlet$hexRain(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (((Level) (Object) this).isClientSide()) {
            cir.setReturnValue(HexSkyClient.rainLevel(cir.getReturnValueF()));
        }
    }

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
    private void scarlet$hexThunder(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (((Level) (Object) this).isClientSide()) {
            cir.setReturnValue(HexSkyClient.thunderLevel(cir.getReturnValueF()));
        }
    }
}
