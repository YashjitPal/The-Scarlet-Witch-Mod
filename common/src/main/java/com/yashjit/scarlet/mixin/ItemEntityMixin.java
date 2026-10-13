package com.yashjit.scarlet.mixin;

import com.yashjit.scarlet.decor.HexMade;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Something a Hex made, lying dropped outside it, is gone.
 */
@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void scarlet$goneOutsideItsHex(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.tickCount % 10 == 0 && !self.isRemoved() && self.level() instanceof ServerLevel level) {
            HexMade.tickDropped(level, self);
        }
    }
}
