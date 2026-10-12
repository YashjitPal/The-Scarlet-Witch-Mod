package com.yashjit.scarlet.mixin.client;

import com.yashjit.scarlet.client.hex.PropsPanel;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Set dressing beside the inventory, for a caster standing in their own Hex.
 */
@Mixin(InventoryScreen.class)
abstract class InventoryScreenMixin extends AbstractRecipeBookScreen<InventoryMenu> {

    private InventoryScreenMixin(InventoryMenu menu, RecipeBookComponent<?> book, Inventory inventory, Component title) {
        super(menu, book, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void scarlet$setDressing(CallbackInfo ci) {
        addRenderableWidget(new PropsPanel(() -> leftPos, () -> leftPos + imageWidth, () -> topPos));
    }
}
