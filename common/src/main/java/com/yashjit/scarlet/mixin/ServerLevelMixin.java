package com.yashjit.scarlet.mixin;

import com.yashjit.scarlet.hex.HexMending;
import com.yashjit.scarlet.hex.HexRewrites;
import com.yashjit.scarlet.hex.HexTape;
import com.yashjit.scarlet.hex.Hexes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * What has just moved into a Hex, or had its wall come over it, is rewritten to fit, and where everything inside a
 * standing Hex goes, and what comes into the world there, goes on its tape. And whatever a blast broke inside a Hex
 * comes back before the level is saved, so a crater is never saved open.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {

    @Inject(method = "tickNonPassenger", at = @At("TAIL"))
    private void scarlet$rewriteWhatCameIn(Entity entity, CallbackInfo ci) {
        HexRewrites.ticked((ServerLevel) (Object) this, entity);
        HexTape.ticked((ServerLevel) (Object) this, entity);
    }

    @Inject(method = "addFreshEntity", at = @At("RETURN"))
    private void scarlet$tapeWhatCameIntoTheWorld(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            HexTape.added((ServerLevel) (Object) this, entity);
        }
    }

    @Inject(method = "save", at = @At("HEAD"))
    private void scarlet$mendBeforeSaving(@Nullable ProgressListener progressListener, boolean flush, boolean noSave, CallbackInfo ci) {
        if (!noSave) {
            HexMending.finish((ServerLevel) (Object) this);
            Hexes.stopRewinds((ServerLevel) (Object) this);
        }
    }
}
