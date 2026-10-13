package com.yashjit.scarlet.mixin.client;

import com.yashjit.scarlet.client.hex.HexMadeSlots;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Over whatever a Hex made, wherever it is drawn in a slot, its glitching as it goes outside its Hex.
 */
@Mixin(GuiGraphicsExtractor.class)
abstract class GuiGraphicsExtractorMixin {

    @Inject(method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"))
    private void scarlet$hexMade(Font font, ItemStack stack, int x, int y, @Nullable String count, CallbackInfo ci) {
        HexMadeSlots.draw((GuiGraphicsExtractor) (Object) this, stack, x, y);
    }
}
