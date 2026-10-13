package com.yashjit.scarlet.mixin;

import com.yashjit.scarlet.decor.HexMade;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Something a Hex made says so, and that it won't last outside it.
 */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {

    @Inject(method = "addDetailsToTooltip", at = @At("TAIL"))
    private void scarlet$madeByAHex(Item.TooltipContext context, TooltipDisplay display, @Nullable Player player, TooltipFlag flag,
                                    Consumer<Component> lines, CallbackInfo ci) {
        HexMade.addTooltip((ItemStack) (Object) this, lines);
    }
}
