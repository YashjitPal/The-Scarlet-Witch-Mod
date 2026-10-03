package com.yashjit.scarlet.client.config;

import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.registry.ScarletItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;

/**
 * A small button wearing the Scarlet Witch's tiara, in the corner of the game's own options, opening the mod's settings.
 */
public final class SettingsButton extends Button {

    public static final int SIZE = 20;
    private static final Component NAME = Component.translatable("settings.scarlet.open");

    public SettingsButton(int x, int y) {
        super(x, y, SIZE, SIZE, NAME, button -> SettingsScreen.open(Minecraft.getInstance()), DEFAULT_NARRATION);
        setTooltip(Tooltip.create(NAME));
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        boolean lit = isHoveredOrFocused();
        int x = getX();
        int y = getY();
        graphics.fill(x + 1, y, x + SIZE - 1, y + SIZE, ARGB.color(lit ? 1.0F : 0.75F, ScarletPalette.SICKLY));
        graphics.fill(x, y + 1, x + SIZE, y + SIZE - 1, ARGB.color(lit ? 1.0F : 0.75F, ScarletPalette.SICKLY));
        graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, ARGB.color(1.0F, lit ? 0x3A1018 : 0x1A070D));
        graphics.fakeItem(new ItemStack(ScarletItems.WITCH_TIARA.get()), x + 2, y + 2);
    }
}
