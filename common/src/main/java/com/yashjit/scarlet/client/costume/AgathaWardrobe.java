package com.yashjit.scarlet.client.costume;

import com.yashjit.scarlet.magic.Mastery;
import com.yashjit.scarlet.network.AgathaOutfitPayload;
import com.yashjit.scarlet.platform.Services;
import com.yashjit.scarlet.player.AgathaForm;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public final class AgathaWardrobe extends Screen {
    private AgathaForm form;
    private boolean hair;

    public AgathaWardrobe(Player player) {
        super(Component.translatable("agatha.scarlet.wardrobe"));
        form = AgathaForm.of(player);
        hair = AgathaForm.styledHair(player);
    }

    @Override protected void init() {
        if (minecraft.player == null) return;
        int x = width / 2 - 14, y = height / 2 - 54;
        for (AgathaForm outfit : AgathaForm.values()) {
            Component label = Component.translatable("agatha.scarlet.form." + outfit.getSerializedName());
            boolean unlocked = Mastery.rank(minecraft.player) >= outfit.requiredRank();
            if (!unlocked) label = label.copy().append("  " + Mastery.numeral(outfit.requiredRank()));
            Button button = addRenderableWidget(Button.builder(label, b -> {
                form = outfit;
                apply();
                rebuildWidgets();
            }).bounds(x, y, 125, 20).build());
            button.active = unlocked && outfit != form;
            y += 22;
        }
        addRenderableWidget(Checkbox.builder(Component.translatable("agatha.scarlet.hair"), font)
                .pos(x, y + 3).selected(hair).onValueChange((box, checked) -> {
                    hair = checked;
                    apply();
                }).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(x, y + 28, 125, 20).build());
    }

    private void apply() {
        Services.NETWORK.sendToServer(new AgathaOutfitPayload(form.ordinal(), hair));
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void tick() {
        if (minecraft.player == null || !AgathaForm.worn(minecraft.player)) onClose();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int cx = width / 2, cy = height / 2;
        g.fill(cx - 122, cy - 80, cx + 121, cy + 69, 0xEB201A26);
        g.fill(cx - 121, cy - 80, cx + 120, cy - 79, 0xFFB388CA);
        g.centeredText(font, title, cx, cy - 71, 0xFFF2E6F6);
        if (minecraft.player != null) InventoryScreen.extractEntityInInventoryFollowsMouse(g,
                cx - 120, cy - 55, cx - 18, cy + 61, 50, 0.07F, mouseX, mouseY, minecraft.player);
        super.extractRenderState(g, mouseX, mouseY, partial);
    }
}
