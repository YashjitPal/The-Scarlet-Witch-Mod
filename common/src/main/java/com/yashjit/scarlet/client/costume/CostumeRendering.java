package com.yashjit.scarlet.client.costume;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class CostumeRendering {

    private CostumeRendering() {
    }

    /**
     * Armor stays equipped and keeps protecting; it is only hidden while the costume covers it. Elytra stay visible.
     */
    public static void hideCoveredEquipment(Avatar entity, AvatarRenderState state, float partialTick) {
        if (!(entity instanceof Player player) || CostumeView.of(player, partialTick) == null) {
            return;
        }
        if (!state.chestEquipment.has(DataComponents.GLIDER)) {
            state.chestEquipment = ItemStack.EMPTY;
        }
        state.legsEquipment = ItemStack.EMPTY;
        state.feetEquipment = ItemStack.EMPTY;
        state.showCape = false;
    }
}
