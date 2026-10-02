package com.yashjit.scarlet.client.costume;

import com.yashjit.scarlet.client.hex.Outfits;
import com.yashjit.scarlet.crown.CrownItem;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class CostumeRendering {

    private CostumeRendering() {
    }

    /**
     * Armor stays equipped and keeps protecting; it is only hidden while the costume or an era outfit covers it. Elytra
     * and crowns stay visible.
     */
    public static void hideCoveredEquipment(Avatar entity, AvatarRenderState state, float partialTick) {
        if (!(entity instanceof Player player)) {
            return;
        }
        boolean costume = CostumeView.of(player, partialTick) != null;
        boolean outfit = !costume && Outfits.wearing(player, partialTick);
        if (!costume && !outfit) {
            return;
        }
        if (!state.chestEquipment.has(DataComponents.GLIDER)) {
            state.chestEquipment = ItemStack.EMPTY;
        }
        state.legsEquipment = ItemStack.EMPTY;
        state.feetEquipment = ItemStack.EMPTY;
        if (outfit && !(state.headEquipment.getItem() instanceof CrownItem)) {
            state.headEquipment = ItemStack.EMPTY;
        }
        state.showCape = false;
    }
}
