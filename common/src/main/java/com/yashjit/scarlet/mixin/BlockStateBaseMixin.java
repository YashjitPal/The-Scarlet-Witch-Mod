package com.yashjit.scarlet.mixin;

import com.yashjit.scarlet.hex.HexData;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks a Hex's town built drop nothing, however they are broken, so the town can't be farmed.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateBaseMixin {

    @Inject(method = "getDrops", at = @At("HEAD"), cancellable = true)
    private void scarlet$nothingFromTheHex(LootParams.Builder params, CallbackInfoReturnable<List<ItemStack>> cir) {
        Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
        if (origin != null && HexData.of(params.getLevel()).isBuilt(BlockPos.containing(origin))) {
            cir.setReturnValue(List.of());
        }
    }
}
