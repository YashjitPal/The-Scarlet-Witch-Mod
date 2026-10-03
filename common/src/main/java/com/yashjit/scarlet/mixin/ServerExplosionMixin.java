package com.yashjit.scarlet.mixin;

import com.yashjit.scarlet.hex.HexMending;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A blast breaks nothing inside a Hex for good: what it would break there is taken whole to mend, no fire catches there,
 * and what hangs on the walls there isn't broken.
 */
@Mixin(ServerExplosion.class)
abstract class ServerExplosionMixin {

    @Shadow
    @Final
    private ServerLevel level;
    @Shadow
    @Final
    private Vec3 center;
    @Shadow
    @Final
    private Explosion.BlockInteraction blockInteraction;

    @Inject(method = "interactWithBlocks", at = @At("HEAD"))
    private void scarlet$mendInsideTheHex(List<BlockPos> targetBlocks, CallbackInfo ci) {
        // a wind charge only works what it hits, breaking nothing
        if (blockInteraction == Explosion.BlockInteraction.DESTROY || blockInteraction == Explosion.BlockInteraction.DESTROY_WITH_DECAY) {
            HexMending.blast(level, center, targetBlocks);
        }
    }

    @Inject(method = "createFire", at = @At("HEAD"))
    private void scarlet$noFireInsideTheHex(List<BlockPos> targetBlocks, CallbackInfo ci) {
        HexMending.unlit(level, targetBlocks);
    }

    @Inject(method = "shouldAffectBlocklikeEntities", at = @At("RETURN"), cancellable = true)
    private void scarlet$spareWhatHangsInsideTheHex(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && HexMending.inside(level, center)) {
            cir.setReturnValue(false);
        }
    }
}
