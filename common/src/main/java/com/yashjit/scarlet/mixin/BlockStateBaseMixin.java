package com.yashjit.scarlet.mixin;

import com.yashjit.scarlet.hex.HexData;
import com.yashjit.scarlet.hex.HexPaint;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks a Hex's town built drop nothing, however they are broken, and blocks painted inside a Hex drop what they were
 * before, so neither can be farmed.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateBaseMixin {

    @Inject(method = "getDrops", at = @At("HEAD"), cancellable = true)
    private void scarlet$nothingFromTheHex(LootParams.Builder params, CallbackInfoReturnable<List<ItemStack>> cir) {
        Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
        if (origin == null) {
            return;
        }
        BlockPos pos = BlockPos.containing(origin);
        if (HexData.of(params.getLevel()).isBuilt(pos)) {
            cir.setReturnValue(List.of());
            return;
        }
        HexPaint.Painted painted = HexPaint.at(params.getLevel(), pos);
        BlockState self = (BlockState) (Object) this;
        if (painted != null && painted.after().getBlock() == self.getBlock()) {
            cir.setReturnValue(painted.before().getDrops(params));
        }
    }
}
