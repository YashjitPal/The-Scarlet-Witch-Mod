package com.yashjit.scarlet.mixin;

import com.yashjit.scarlet.hex.HexRewrites;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * What has just moved into a Hex, or had its wall come over it, is rewritten to fit.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {

    @Inject(method = "tickNonPassenger", at = @At("TAIL"))
    private void scarlet$rewriteWhatCameIn(Entity entity, CallbackInfo ci) {
        HexRewrites.ticked((ServerLevel) (Object) this, entity);
    }
}
