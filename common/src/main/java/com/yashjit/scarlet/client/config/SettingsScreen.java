package com.yashjit.scarlet.client.config;

import com.mojang.blaze3d.platform.InputConstants;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.magic.MagicHud;
import com.yashjit.scarlet.config.ScarletClientConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Scarlet Witch's own settings, styled like the rest of its magic: how much magic shows, ways to soften it, and the
 * Hex's cinematic touches. Every change takes at once and is saved as it is made.
 *
 * <p>Hover a setting for what it does. The arrow keys move between them too, Enter or Space changes the one picked, and
 * left and right step through the quality.
 */
public final class SettingsScreen extends Screen {

    /** Laid out to fit the shortest screen Minecraft lays out, 240 tall. */
    private static final int WIDTH = 300;
    private static final int ROW = 16;
    private static final int SECTION = 13;
    private static final int TOP = 30;
    private static final int TIP = 24;
    private static final int FOOTER = 22;
    private static final float OPEN_SECONDS = 0.25F;
    private static final int TEXT = 0xE6D6DA;
    private static final int MUTED = 0xA8949A;
    private static final int PANEL = 0x12040A;
    private static final int TRACK = 0x2A1A1F;

    private final @Nullable Screen parent;
    private final List<Entry> entries = new ArrayList<>();
    private final long openedAt = Util.getNanos();
    private long lastNanos;
    /** The setting the mouse or the keys last picked. */
    private int picked = -1;
    private boolean doneHovered;

    public SettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("settings.scarlet.title"));
        this.parent = parent;
        ScarletClientConfig config = ScarletClientConfig.get();
        section("settings.scarlet.magic");
        entries.add(new Quality());
        section("settings.scarlet.comfort");
        toggle("reduce_flashing", () -> config.reduceFlashing, on -> config.reduceFlashing = on);
        toggle("reduce_camera_shake", () -> config.reduceCameraShake, on -> config.reduceCameraShake = on);
        toggle("reduce_screen_effects", () -> config.reduceScreenEffects, on -> config.reduceScreenEffects = on);
        toggle("instant_transformations", () -> config.instantTransformations, on -> config.instantTransformations = on);
        section("settings.scarlet.hex");
        toggle("cinematic_founding", () -> config.cinematicFounding, on -> config.cinematicFounding = on);
        toggle("era_audio", () -> config.eraAudio, on -> config.eraAudio = on);
    }

    public static void open(Minecraft minecraft) {
        minecraft.gui.setScreen(new SettingsScreen(minecraft.gui.screen()));
    }

    private void section(String key) {
        entries.add(new Section(Component.translatable(key)));
    }

    private void toggle(String name, Supplier<Boolean> value, Consumer<Boolean> set) {
        entries.add(new Toggle(Component.translatable("settings.scarlet." + name), Component.translatable("settings.scarlet." + name + ".tip"), value, set));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long nanos = Util.getNanos();
        float seconds = lastNanos == 0 ? 0.0F : Math.min(0.1F, (nanos - lastNanos) / 1.0E9F);
        lastNanos = nanos;
        float open = Ease.outCubic(Ease.clamp01((nanos - openedAt) / 1.0E9F / OPEN_SECONDS));
        int x0 = left();
        int y0 = top() + Math.round((1.0F - open) * 8.0F);
        int height = panelHeight();
        // the panel: near black, rimmed in scarlet, a glow behind the title
        fillRounded(graphics, x0 - 1, y0 - 1, WIDTH + 2, height + 2, ARGB.color(open * 0.7F, ScarletPalette.SICKLY));
        fillRounded(graphics, x0, y0, WIDTH, height, ARGB.color(open * 0.94F, PANEL));
        MagicHud.sprite(graphics, MagicHud.GLOW, width / 2.0F, y0 + 11.0F, 140.0F, ARGB.color(open * 0.25F, ScarletPalette.CRIMSON));
        graphics.pose().pushMatrix();
        graphics.pose().translate(width / 2.0F, y0 + 6.0F);
        graphics.pose().scale(1.25F);
        graphics.centeredText(font, title, 0, 0, ARGB.color(open, ScarletPalette.BRIGHT_SCARLET));
        graphics.pose().popMatrix();
        graphics.pose().pushMatrix();
        graphics.pose().translate(width / 2.0F, y0 + 19.0F);
        graphics.pose().scale(0.75F);
        graphics.centeredText(font, Component.translatable("settings.scarlet.subtitle"), 0, 0, ARGB.color(open * 0.85F, MUTED));
        graphics.pose().popMatrix();
        int y = y0 + TOP;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            boolean lit = i == picked;
            entry.shown = Ease.damp(entry.shown, lit ? 1.0F : 0.0F, 14.0F, seconds);
            entry.draw(graphics, x0, y, open, seconds, mouseX, mouseY);
            y += entry.height();
        }
        // what the picked setting does
        Entry tipped = picked >= 0 ? entries.get(picked) : null;
        Component tip = tipped == null ? Component.translatable("settings.scarlet.hint") : tipped.tip();
        graphics.fill(x0 + 10, y + 2, x0 + WIDTH - 10, y + 3, ARGB.color(open * 0.35F, 0x3A1420));
        List<FormattedCharSequence> lines = font.split(tip, Math.round((WIDTH - 24) / 0.75F));
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(x0 + 12.0F, y + 6.0F + i * 8.0F);
            graphics.pose().scale(0.75F);
            graphics.text(font, lines.get(i), 0, 0, ARGB.color(open * (tipped == null ? 0.6F : 0.85F), MUTED), false);
            graphics.pose().popMatrix();
        }
        // and done
        int bx = width / 2 - 40;
        int by = y0 + height - FOOTER + 2;
        doneHovered = mouseX >= bx && mouseX < bx + 80 && mouseY >= by && mouseY < by + 16;
        fillRounded(graphics, bx - 1, by - 1, 82, 18, ARGB.color(open * (doneHovered ? 0.9F : 0.5F), ScarletPalette.SICKLY));
        fillRounded(graphics, bx, by, 80, 16, ARGB.color(open, doneHovered ? ScarletPalette.CRIMSON : 0x2A1018));
        graphics.centeredText(font, CommonComponents.GUI_DONE, width / 2, by + 4, ARGB.color(open, doneHovered ? ScarletPalette.CORE : TEXT));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }
        if (doneHovered) {
            onClose();
            return true;
        }
        int y = top() + TOP;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (event.y() >= y && event.y() < y + entry.height() && event.x() >= left() && event.x() < left() + WIDTH && entry.click(event.x() - left())) {
                picked = i;
                changed();
                return true;
            }
            y += entry.height();
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        int y = top() + TOP;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.pickable() && mouseY >= y && mouseY < y + entry.height() && mouseX >= left() && mouseX < left() + WIDTH) {
                picked = i;
            }
            y += entry.height();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_UP) {
            step(key == InputConstants.KEY_DOWN ? 1 : -1);
            return true;
        }
        if (picked >= 0 && (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_SPACE || key == InputConstants.KEY_LEFT
                || key == InputConstants.KEY_RIGHT)) {
            if (entries.get(picked).press(key)) {
                changed();
            }
            return true;
        }
        return super.keyPressed(event);
    }

    private void step(int by) {
        int i = picked;
        for (int tries = 0; tries < entries.size(); tries++) {
            i = Math.floorMod(i + by, entries.size());
            if (entries.get(i).pickable()) {
                picked = i;
                return;
            }
        }
    }

    private void changed() {
        ScarletClientConfig.save();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, 0.4F));
    }

    @Override
    public void onClose() {
        ScarletClientConfig.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return Math.max(2, (height - panelHeight()) / 2);
    }

    private int panelHeight() {
        int rows = 0;
        for (Entry entry : entries) {
            rows += entry.height();
        }
        return TOP + rows + TIP + FOOTER;
    }

    private static void fillRounded(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x + 1, y, x + width - 1, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    private static void label(GuiGraphicsExtractor graphics, Font font, Component text, float x, float y, float scale, int color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale);
        graphics.text(font, text, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    /**
     * A line in the panel: a heading, or a setting.
     */
    private abstract class Entry {

        /** How lit it is, from being picked. */
        float shown;

        abstract int height();

        abstract void draw(GuiGraphicsExtractor graphics, int x0, int y, float open, float seconds, int mouseX, int mouseY);

        boolean pickable() {
            return true;
        }

        Component tip() {
            return Component.empty();
        }

        /**
         * @param x where along the panel it was clicked
         * @return whether it changed
         */
        boolean click(double x) {
            return false;
        }

        boolean press(int key) {
            return false;
        }

        void highlight(GuiGraphicsExtractor graphics, int x0, int y, float open) {
            if (shown > 0.01F) {
                graphics.fill(x0 + 6, y, x0 + WIDTH - 6, y + height(), ARGB.color(open * 0.18F * shown, ScarletPalette.CRIMSON));
                graphics.fill(x0 + 6, y, x0 + 7, y + height(), ARGB.color(open * shown, ScarletPalette.BRIGHT_SCARLET));
            }
        }
    }

    private final class Section extends Entry {

        private final Component name;

        Section(Component name) {
            this.name = name;
        }

        @Override
        int height() {
            return SECTION;
        }

        @Override
        boolean pickable() {
            return false;
        }

        @Override
        void draw(GuiGraphicsExtractor graphics, int x0, int y, float open, float seconds, int mouseX, int mouseY) {
            label(graphics, font, name, x0 + 12.0F, y + 4.5F, 0.7F, ARGB.color(open * 0.9F, 0xD9425E));
            int lineX = x0 + 16 + Math.round(font.width(name) * 0.7F);
            graphics.fill(lineX, y + 7, x0 + WIDTH - 12, y + 8, ARGB.color(open * 0.35F, 0x3A1420));
        }
    }

    private final class Toggle extends Entry {

        private final Component name;
        private final Component tip;
        private final Supplier<Boolean> value;
        private final Consumer<Boolean> set;
        private float knob = -1.0F;

        Toggle(Component name, Component tip, Supplier<Boolean> value, Consumer<Boolean> set) {
            this.name = name;
            this.tip = tip;
            this.value = value;
            this.set = set;
        }

        @Override
        int height() {
            return ROW;
        }

        @Override
        Component tip() {
            return tip;
        }

        @Override
        boolean click(double x) {
            set.accept(!value.get());
            return true;
        }

        @Override
        boolean press(int key) {
            boolean on = key == InputConstants.KEY_RIGHT || key != InputConstants.KEY_LEFT && !value.get();
            if (on == value.get()) {
                return false;
            }
            set.accept(on);
            return true;
        }

        @Override
        void draw(GuiGraphicsExtractor graphics, int x0, int y, float open, float seconds, int mouseX, int mouseY) {
            highlight(graphics, x0, y, open);
            boolean on = value.get();
            knob = knob < 0.0F ? (on ? 1.0F : 0.0F) : Ease.damp(knob, on ? 1.0F : 0.0F, 18.0F, seconds);
            label(graphics, font, name, x0 + 14.0F, y + 4.0F, 1.0F, ARGB.color(open, TEXT));
            // a little switch: dark when off, lit scarlet when on, its knob sliding across
            int tx = x0 + WIDTH - 14 - 26;
            int ty = y + 3;
            fillRounded(graphics, tx, ty, 26, 10, ARGB.color(open, ARGB.srgbLerp(knob, TRACK, ScarletPalette.CRIMSON)));
            if (knob > 0.5F) {
                MagicHud.sprite(graphics, MagicHud.GLOW, tx + 13.0F, ty + 5.0F, 30.0F, ARGB.color(open * 0.35F * knob, ScarletPalette.SCARLET));
            }
            int kx = tx + 1 + Math.round(16.0F * knob);
            fillRounded(graphics, kx, ty + 1, 8, 8, ARGB.color(open, ARGB.srgbLerp(knob, 0x6E6066, ScarletPalette.CORE)));
            Component state = Component.translatable(on ? "settings.scarlet.on" : "settings.scarlet.off");
            label(graphics, font, state, tx - 4.0F - font.width(state) * 0.75F, y + 5.5F, 0.75F, ARGB.color(open * 0.8F, on ? TEXT : MUTED));
        }
    }

    private final class Quality extends Entry {

        private static final int SEGMENT = 40;

        @Override
        int height() {
            return ROW;
        }

        @Override
        Component tip() {
            return Component.translatable("settings.scarlet.quality.tip");
        }

        private int segmentsLeft(int x0) {
            return x0 + WIDTH - 14 - SEGMENT * ScarletClientConfig.EffectsQuality.values().length;
        }

        @Override
        boolean click(double x) {
            int index = (int) Math.floor((x - (segmentsLeft(0))) / SEGMENT);
            ScarletClientConfig.EffectsQuality[] all = ScarletClientConfig.EffectsQuality.values();
            if (index < 0 || index >= all.length || all[index] == ScarletClientConfig.get().effectsQuality) {
                return false;
            }
            ScarletClientConfig.get().effectsQuality = all[index];
            return true;
        }

        @Override
        boolean press(int key) {
            ScarletClientConfig.EffectsQuality[] all = ScarletClientConfig.EffectsQuality.values();
            int at = ScarletClientConfig.get().effectsQuality.ordinal();
            int next = key == InputConstants.KEY_LEFT ? Math.max(0, at - 1) : key == InputConstants.KEY_RIGHT ? Math.min(all.length - 1, at + 1)
                    : (at + 1) % all.length;
            if (next == at) {
                return false;
            }
            ScarletClientConfig.get().effectsQuality = all[next];
            return true;
        }

        @Override
        void draw(GuiGraphicsExtractor graphics, int x0, int y, float open, float seconds, int mouseX, int mouseY) {
            highlight(graphics, x0, y, open);
            label(graphics, font, Component.translatable("settings.scarlet.quality"), x0 + 14.0F, y + 4.0F, 1.0F, ARGB.color(open, TEXT));
            ScarletClientConfig.EffectsQuality[] all = ScarletClientConfig.EffectsQuality.values();
            ScarletClientConfig.EffectsQuality current = ScarletClientConfig.get().effectsQuality;
            int sx = segmentsLeft(x0);
            fillRounded(graphics, sx - 1, y + 1, SEGMENT * all.length + 2, 14, ARGB.color(open * 0.6F, 0x3A1420));
            for (int i = 0; i < all.length; i++) {
                int x = sx + i * SEGMENT;
                boolean selected = all[i] == current;
                boolean hover = mouseX >= x && mouseX < x + SEGMENT && mouseY >= y + 1 && mouseY < y + 15;
                int face = selected ? ScarletPalette.CRIMSON : hover ? 0x3A1A22 : TRACK;
                graphics.fill(x + (i == 0 ? 1 : 0), y + 2, x + SEGMENT - (i == all.length - 1 ? 1 : 0), y + 14, ARGB.color(open, face));
                if (selected) {
                    graphics.fill(x + 2, y + 2, x + SEGMENT - 2, y + 3, ARGB.color(open * 0.8F, ScarletPalette.BRIGHT_SCARLET));
                }
                Component name = Component.translatable("settings.scarlet.quality." + all[i].name().toLowerCase(Locale.ROOT));
                graphics.pose().pushMatrix();
                graphics.pose().translate(x + SEGMENT / 2.0F, y + 5.0F);
                graphics.pose().scale(0.75F);
                graphics.centeredText(font, name, 0, 0, ARGB.color(open, selected ? ScarletPalette.CORE : MUTED));
                graphics.pose().popMatrix();
            }
        }
    }
}
