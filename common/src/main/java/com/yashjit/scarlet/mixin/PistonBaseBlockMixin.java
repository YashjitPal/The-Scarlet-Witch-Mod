package com.yashjit.scarlet.mixin;

import com.yashjit.scarlet.hex.HexData;
import com.yashjit.scarlet.hex.HexPaint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pistons can't push a Hex's town or its painted blocks around, or they could be carried off and kept.
 */
@Mixin(PistonBaseBlock.class)
abstract class PistonBaseBlockMixin {

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private static void scarlet$townStaysPut(BlockState state, Level level, BlockPos pos, Direction direction, boolean allowDestroyable,
                                            Direction connectionDirection, CallbackInfoReturnable<Boolean> cir) {
        if (!state.isAir() && level instanceof ServerLevel server && (HexData.of(server).isBuilt(pos) || HexPaint.at(server, pos) != null)) {
            cir.setReturnValue(false);
        }
    }
}
