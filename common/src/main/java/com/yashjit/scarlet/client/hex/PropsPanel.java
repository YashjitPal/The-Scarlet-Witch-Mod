package com.yashjit.scarlet.client.hex;

import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.decor.Props;
import com.yashjit.scarlet.hex.Hex;
import com.yashjit.scarlet.hex.HexShape;
import com.yashjit.scarlet.hex.HexSnapshot;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.magic.Magic;
import com.yashjit.scarlet.network.PropPayload;
import com.yashjit.scarlet.platform.Services;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Set dressing, beside the inventory of a caster standing in their own Hex: every era decoration and the car, each
 * taken out of thin air with a click for a little chaos energy. It is only there while they could take them, and it
 * follows the inventory wherever the recipe book pushes it.
 */
public final class PropsPanel extends AbstractWidget {

    private static final int SLOT = 18;
    private static final int COLUMNS = 3;
    private static final int PAD = 4;
    private static final int TITLE = 12;
    private static final int GAP = 3;
    private static final int BODY = 0x221B19;
    private static final int BEVEL = 0x4B3E39;
    private static final int SHINE = 0x6A5852;
    private static final int WELL = 0x120E0D;
    /** A well behind something the caster hasn't the energy for. */
    private static final int SHORT_WELL = 0x3A0B14;

    private final IntSupplier left;
    private final IntSupplier right;
    private final IntSupplier top;
    private final List<ItemStack> props = new ArrayList<>();

    /**
     * @param left  where the inventory's left edge is
     * @param right where its right edge is
     * @param top   where its top is
     */
    public PropsPanel(IntSupplier left, IntSupplier right, IntSupplier top) {
        super(0, 0, PAD * 2 + COLUMNS * SLOT, TITLE + Mth.positiveCeilDiv(Props.items().size(), COLUMNS) * SLOT + PAD,
                Component.translatable("props.scarlet.title"));
        for (Item item : Props.items()) {
            props.add(new ItemStack(item));
        }
        this.left = left;
        this.right = right;
        this.top = top;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        active = player != null && available(minecraft, player);
        if (!active) {
            return;
        }
        // beside the inventory on its right, or on its left where the screen runs out
        int x0 = right.getAsInt() + GAP;
        if (x0 + width > minecraft.getWindow().getGuiScaledWidth() - 2) {
            x0 = left.getAsInt() - GAP - width;
        }
        int y0 = top.getAsInt();
        setX(x0);
        setY(y0);
        // dark bakelite under a bevel, like the Showrunner's remote
        graphics.fill(x0, y0, x0 + width, y0 + height, ARGB.color(1.0F, BEVEL));
        graphics.fill(x0 + 1, y0 + 1, x0 + width - 1, y0 + height - 1, ARGB.color(1.0F, BODY));
        graphics.fill(x0 + 2, y0 + 1, x0 + width - 2, y0 + 2, ARGB.color(1.0F, SHINE));
        graphics.pose().pushMatrix();
        graphics.pose().translate(x0 + width / 2.0F, y0 + 4.0F);
        graphics.pose().scale(0.75F);
        graphics.centeredText(minecraft.font, getMessage(), 0, 0, ARGB.color(1.0F, ScarletPalette.BRIGHT_SCARLET));
        graphics.pose().popMatrix();
        float energy = player.isCreative() ? Float.MAX_VALUE : Magic.energy(player, player.level().getGameTime());
        int hovered = indexAt(mouseX, mouseY);
        for (int i = 0; i < props.size(); i++) {
            int x = x0 + PAD + i % COLUMNS * SLOT;
            int y = y0 + TITLE + i / COLUMNS * SLOT;
            ItemStack stack = props.get(i);
            float cost = Props.cost(stack.getItem());
            boolean affordable = energy >= cost;
            graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, ARGB.color(1.0F, affordable ? WELL : SHORT_WELL));
            if (i == hovered) {
                graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, ARGB.color(0.45F, ScarletPalette.SCARLET));
                graphics.setComponentTooltipForNextFrame(minecraft.font, List.of(stack.getHoverName(),
                        Component.translatable("props.scarlet.cost", Math.round(cost)).withStyle(affordable ? ChatFormatting.GRAY : ChatFormatting.RED)),
                        mouseX, mouseY);
            }
            graphics.item(stack, x + 1, y + 1);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        int index = indexAt(event.x(), event.y());
        if (index >= 0) {
            Services.NETWORK.sendToServer(new PropPayload(index));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.7F, 0.6F));
        }
    }

    @Override
    public void playDownSound(SoundManager sounds) {
        // the chime of something taken is its sound, and only when there is something to take
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return active && super.isMouseOver(mouseX, mouseY);
    }

    private int indexAt(double mouseX, double mouseY) {
        int column = Mth.floor((mouseX - getX() - PAD) / SLOT);
        int row = Mth.floor((mouseY - getY() - TITLE) / SLOT);
        if (column < 0 || column >= COLUMNS || row < 0 || mouseX < getX() || mouseY < getY()) {
            return -1;
        }
        int index = row * COLUMNS + column;
        return index < props.size() ? index : -1;
    }

    /**
     * Whether the caster stands in their own Hex while it stands, crowned as its caster must be.
     */
    private static boolean available(Minecraft minecraft, LocalPlayer player) {
        if (minecraft.level == null || !Props.crowned(player)) {
            return false;
        }
        double now = minecraft.level.getGameTime();
        for (HexSnapshot hex : Hexes.clientHexes()) {
            if (hex.caster().equals(player.getUUID()) && hex.phaseValue() != Hex.Phase.COLLAPSING
                    && HexShape.contains(hex.center(), Hexes.wallRadius(hex, now), player.position())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
