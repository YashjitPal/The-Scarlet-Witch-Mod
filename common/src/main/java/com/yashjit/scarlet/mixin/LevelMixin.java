package com.yashjit.scarlet.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yashjit.scarlet.hex.HexTape;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Every block that changes inside a standing Hex goes on its tape, to be put back by a rewind.
 */
@Mixin(Level.class)
abstract class LevelMixin {

    @WrapOperation(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;"))
    private @Nullable BlockState scarlet$tapeBlockChange(LevelChunk chunk, BlockPos pos, BlockState state, int flags, Operation<BlockState> original) {
        Object pending = HexTape.changing((Level) (Object) this, pos, state);
        BlockState before = original.call(chunk, pos, state, flags);
        HexTape.changed(pending, pos, before, state);
        return before;
    }
}
