package com.yashjit.scarlet.client.darkhold;

import com.mojang.blaze3d.platform.InputConstants;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.magic.MagicHud;
import com.yashjit.scarlet.network.DreamOptionsPayload;
import com.yashjit.scarlet.network.DreamPayload;
import com.yashjit.scarlet.platform.Services;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Choosing where your spirit goes, as Dreamwalking is cast: every dimension laid out like a window into the dark, each
 * with what it is and where in it you would arrive. Click one or press its number; Escape stays.
 */
public final class DreamScreen extends Screen {

    private static final int CARD_WIDTH = 112;
    private static final int CARD_HEIGHT = 78;
    private static final int GAP = 10;
    private static final float OPEN_TICKS = 8.0F;
    private static final int TEXT = 0xE6D6DA;
    private static final int MUTED = 0xA8949A;

    private final double openedAt;
    private @Nullable List<DreamOptionsPayload.Destination> destinations;
    private final List<Card> cards = new ArrayList<>();
    private float[] hover = new float[0];
    private int hovered = -1;
    private long lastNanos;

    public DreamScreen() {
        super(Component.translatable("dreamwalk.scarlet.title"));
        Minecraft minecraft = Minecraft.getInstance();
        openedAt = minecraft.level == null ? 0.0 : minecraft.level.getGameTime();
    }

    /**
     * Word back of where the spirit could go: the overworld, the Nether and the End first, then any others.
     */
    void offered(List<DreamOptionsPayload.Destination> offered) {
        List<DreamOptionsPayload.Destination> sorted = new ArrayList<>(offered);
        sorted.sort(Comparator.comparingInt((DreamOptionsPayload.Destination d) -> order(d.dimension()))
                .thenComparing(d -> d.dimension().identifier().toString()));
        destinations = sorted;
        hover = new float[sorted.size()];
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // the world stays in view behind the dark
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long nanos = Util.getNanos();
        float seconds = lastNanos == 0 ? 0.0F : Math.min(0.1F, (nanos - lastNanos) / 1.0E9F);
        lastNanos = nanos;
        Minecraft minecraft = Minecraft.getInstance();
        double now = minecraft.level == null ? 0.0 : minecraft.level.getGameTime() + partialTick;
        float open = Ease.outCubic(Ease.clamp01((float) ((now - openedAt) / OPEN_TICKS)));
        graphics.fill(0, 0, width, height, ARGB.color(0.55F * open, ScarletPalette.VOID));
        MagicHud.sprite(graphics, MagicHud.GLOW, width / 2.0F, height / 2.0F, Math.max(width, height) * 1.1F, ARGB.color(0.35F * open, ScarletPalette.ABYSS));
        layout();
        int top = (cards.isEmpty() ? height / 2 - CARD_HEIGHT / 2 : cards.getFirst().y()) - 34;
        int bottom = cards.isEmpty() ? height / 2 + CARD_HEIGHT / 2 : cards.getLast().y() + CARD_HEIGHT;
        graphics.pose().pushMatrix();
        graphics.pose().translate(width / 2.0F, top);
        graphics.pose().scale(1.25F);
        graphics.centeredText(font, title, 0, 0, ARGB.color(open, 0xD9425E));
        graphics.pose().popMatrix();
        graphics.pose().pushMatrix();
        graphics.pose().translate(width / 2.0F, top + 14.0F);
        graphics.pose().scale(0.75F);
        graphics.centeredText(font, Component.translatable("dreamwalk.scarlet.where"), 0, 0, ARGB.color(open * 0.85F, MUTED));
        graphics.pose().popMatrix();
        if (destinations == null) {
            int dots = 1 + (int) ((now / 6.0) % 3.0);
            graphics.centeredText(font, Component.translatable("dreamwalk.scarlet.reaching").append(".".repeat(dots)), width / 2, height / 2,
                    ARGB.color(open * 0.8F, MUTED));
            return;
        }
        hovered = -1;
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).contains(mouseX, mouseY)) {
                hovered = i;
            }
        }
        for (int i = 0; i < cards.size(); i++) {
            hover[i] = Ease.damp(hover[i], i == hovered ? 1.0F : 0.0F, 14.0F, seconds);
            card(graphics, cards.get(i), i, open, hover[i], now);
        }
        Component hint = Component.translatable("dreamwalk.scarlet.choose");
        graphics.pose().pushMatrix();
        graphics.pose().translate(width / 2.0F, bottom + 12.0F);
        graphics.pose().scale(0.7F);
        graphics.centeredText(font, hint, 0, 0, ARGB.color(open * 0.7F, MUTED));
        graphics.pose().popMatrix();
    }

    /**
     * One window into a dimension: a dark panel, a ring of light around what the place is made of, its name, and where
     * in it you would arrive.
     */
    private void card(GuiGraphicsExtractor graphics, Card card, int index, float open, float hover, double now) {
        float lift = 2.0F * hover + (1.0F - open) * 10.0F;
        int x = card.x();
        int y = Math.round(card.y() - lift);
        int tint = tint(card.destination().dimension());
        float alpha = open;
        fillRounded(graphics, x - 1, y - 1, CARD_WIDTH + 2, CARD_HEIGHT + 2, ARGB.color(alpha * (0.35F + 0.65F * hover), ScarletPalette.SICKLY));
        fillRounded(graphics, x, y, CARD_WIDTH, CARD_HEIGHT, ARGB.color(alpha * 0.92F, 0x12040A));
        graphics.fill(x + 2, y + 1, x + CARD_WIDTH - 2, y + 2, ARGB.color(alpha * 0.35F, 0x3A1420));
        float cx = x + CARD_WIDTH / 2.0F;
        float cy = y + 26.0F;
        MagicHud.sprite(graphics, MagicHud.GLOW, cx, cy, 52.0F + 8.0F * hover, ARGB.color(alpha * (0.22F + 0.3F * hover), tint));
        float turn = (float) (now * 0.02) + index * 1.3F;
        for (int i = 0; i < 40; i++) {
            float angle = turn + Mth.TWO_PI * i / 40.0F;
            float glint = 0.35F + 0.65F * Math.max(0.0F, Mth.sin(angle * 3.0F + (float) now * 0.08F));
            MagicHud.bead(graphics, cx + Mth.cos(angle) * 17.0F, cy + Mth.sin(angle) * 17.0F, 1.0F,
                    ARGB.color(alpha * (0.4F + 0.4F * hover) * glint, i % 2 == 0 ? tint : ScarletPalette.SICKLY));
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);
        graphics.pose().scale(1.25F + 0.1F * hover);
        graphics.fakeItem(icon(card.destination().dimension()), -8, -8);
        graphics.pose().popMatrix();
        graphics.centeredText(font, name(card.destination().dimension()), Math.round(cx), y + 50, ARGB.color(alpha, hover > 0.5F ? 0xF2D0D6 : TEXT));
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, y + 63.0F);
        graphics.pose().scale(0.65F);
        graphics.centeredText(font, arrival(card.destination()), 0, 0, ARGB.color(alpha * 0.85F, MUTED));
        graphics.pose().popMatrix();
        if (index < 9) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + 5.0F, y + 4.0F);
            graphics.pose().scale(0.6F);
            graphics.text(font, Component.literal(Integer.toString(index + 1)), 0, 0, ARGB.color(alpha * 0.6F, MUTED), false);
            graphics.pose().popMatrix();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && destinations != null) {
            layout();
            for (int i = 0; i < cards.size(); i++) {
                if (cards.get(i).contains((int) event.x(), (int) event.y())) {
                    pick(cards.get(i).destination());
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int index = event.key() - InputConstants.KEY_1;
        if (destinations != null && index >= 0 && index < Math.min(9, destinations.size())) {
            pick(destinations.get(index));
            return true;
        }
        return super.keyPressed(event);
    }

    private void pick(DreamOptionsPayload.Destination destination) {
        Services.NETWORK.sendToServer(DreamPayload.go(destination.dimension()));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 0.6F));
        onClose();
    }

    private void layout() {
        cards.clear();
        if (destinations == null) {
            return;
        }
        int perRow = Math.max(1, Math.min(destinations.size(), (width - 20 + GAP) / (CARD_WIDTH + GAP)));
        int rows = rows();
        int y0 = height / 2 - CARD_HEIGHT / 2 - (rows - 1) * (CARD_HEIGHT + GAP) / 2;
        for (int i = 0; i < destinations.size(); i++) {
            int row = i / perRow;
            int inRow = Math.min(perRow, destinations.size() - row * perRow);
            int rowWidth = inRow * CARD_WIDTH + (inRow - 1) * GAP;
            int x = (width - rowWidth) / 2 + (i % perRow) * (CARD_WIDTH + GAP);
            cards.add(new Card(destinations.get(i), x, y0 + row * (CARD_HEIGHT + GAP)));
        }
    }

    private int rows() {
        if (destinations == null || destinations.isEmpty()) {
            return 1;
        }
        int perRow = Math.max(1, Math.min(destinations.size(), (width - 20 + GAP) / (CARD_WIDTH + GAP)));
        return (destinations.size() + perRow - 1) / perRow;
    }

    private static int order(ResourceKey<Level> dimension) {
        return dimension == Level.OVERWORLD ? 0 : dimension == Level.NETHER ? 1 : dimension == Level.END ? 2 : 3;
    }

    /**
     * A dimension's name: its own if it has one, otherwise made from its id.
     */
    public static Component name(ResourceKey<Level> dimension) {
        Identifier id = dimension.identifier();
        String[] words = id.getPath().replace('/', '_').split("_");
        StringBuilder fallback = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                fallback.append(fallback.isEmpty() ? "" : " ").append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
            }
        }
        return Component.translatableWithFallback("dimension." + id.getNamespace() + "." + id.getPath().replace('/', '.'), fallback.toString());
    }

    private static Component arrival(DreamOptionsPayload.Destination destination) {
        String kind = switch (destination.arrival()) {
            case DreamOptionsPayload.LAST_STOOD -> "last_stood";
            case DreamOptionsPayload.RESPAWN -> "respawn";
            case DreamOptionsPayload.WORLD_SPAWN -> "world_spawn";
            default -> "unknown";
        };
        return Component.translatable("dreamwalk.scarlet.arrival." + kind);
    }

    private static ItemStack icon(ResourceKey<Level> dimension) {
        if (dimension == Level.OVERWORLD) {
            return new ItemStack(Items.GRASS_BLOCK);
        }
        if (dimension == Level.NETHER) {
            return new ItemStack(Items.NETHERRACK);
        }
        return new ItemStack(dimension == Level.END ? Items.END_STONE : Items.ENDER_EYE);
    }

    /**
     * The light of each place, seen through its window.
     */
    private static int tint(ResourceKey<Level> dimension) {
        if (dimension == Level.OVERWORLD) {
            return 0x8FC97A;
        }
        if (dimension == Level.NETHER) {
            return 0xE8582E;
        }
        return dimension == Level.END ? 0xC9AEF2 : ScarletPalette.SCARLET;
    }

    private static void fillRounded(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x + 1, y, x + width - 1, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    private record Card(DreamOptionsPayload.Destination destination, int x, int y) {

        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + CARD_WIDTH && mouseY >= y && mouseY < y + CARD_HEIGHT;
        }
    }
}
