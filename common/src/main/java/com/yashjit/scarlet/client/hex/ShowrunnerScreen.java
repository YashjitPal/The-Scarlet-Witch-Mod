package com.yashjit.scarlet.client.hex;

import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.ScarletKeyMappings;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.hex.Hex;
import com.yashjit.scarlet.hex.HexBuild;
import com.yashjit.scarlet.hex.HexSky;
import com.yashjit.scarlet.hex.HexSnapshot;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.magic.Mastery;
import com.yashjit.scarlet.magic.Spell;
import com.yashjit.scarlet.network.ShowrunnerPayload;
import com.yashjit.scarlet.platform.Services;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The Showrunner's remote: a caster's controls for their Hex, styled as an old television remote in dark bakelite with
 * round buttons that light scarlet. It slides up beside the view without pausing anything, so every change is seen
 * happening: the channel (the era), whether a new episode airs every morning, the hour and the weather inside, the
 * Hex's name, and what the next Hex builds.
 */
public final class ShowrunnerScreen extends Screen {

    private static final int WIDTH = 168;
    /** Short enough to fit the shortest screen Minecraft lays out, 240 tall. */
    private static final int HEIGHT = 230;
    private static final int MARGIN = 24;
    private static final int ERA_ROW = 60;
    private static final int EPISODE_ROW = 79;
    private static final int HOME_ROW = 97;
    private static final int TIME_ROW = 127;
    private static final int WEATHER_ROW = 156;
    private static final int BUILD_ROW = 185;
    private static final int NAME_ROW = 205;
    private static final float OPEN_TICKS = 6.0F;
    /** How long a press shows before word of it comes back. */
    private static final double PENDING_TICKS = 30.0;

    private static final int BODY_TOP = 0x2F2725;
    private static final int BODY_BOTTOM = 0x1A1413;
    private static final int BEVEL = 0x4B3E39;
    private static final int KEY = 0x3A302C;
    private static final int KEY_HOVER = 0x4D403B;
    private static final int KEY_EDGE = 0x15100F;
    private static final int LABEL = 0xB8A8AE;
    private static final int TEXT = 0xE6D6DA;
    /** The key that puts the remote down to choose where the caster's home goes, rather than telling the Hex anything. */
    private static final int RAISE_HOME = -100;

    private final List<Key> keys = new ArrayList<>();
    private final double openedAt;
    private @Nullable EditBox name;
    private int pendingAction = -1;
    private int pendingValue;
    private double pendingAt = -1.0E9;

    public ShowrunnerScreen() {
        super(Component.translatable("showrunner.scarlet.title"));
        Minecraft minecraft = Minecraft.getInstance();
        openedAt = minecraft.level == null ? 0.0 : minecraft.level.getGameTime();
    }

    /**
     * Opens the remote, for a caster whose crown has mastered the Hex.
     */
    public static void open(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player != null && CrownItem.isWearingCrown(player) && Mastery.rank(player) >= Spell.HEX.rank() && minecraft.gui.screen() == null) {
            HomePlacement.stop();
            minecraft.gui.setScreen(new ShowrunnerScreen());
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6F, 0.25F));
        }
    }

    @Override
    protected void init() {
        keys.clear();
        int x0 = left();
        int y0 = top();
        Era[] eras = Era.values();
        for (int i = 0; i < eras.length; i++) {
            int value = i;
            keys.add(new Key(x0 + 6 + i * 26, y0 + ERA_ROW, 24, 16, label("era", eras[i].getSerializedName()), ShowrunnerPayload.ERA, value, true,
                    () -> own() == null ? -1 : own().era()));
        }
        keys.add(new Key(x0 + WIDTH - 50, y0 + EPISODE_ROW, 44, 14, Component.empty(), ShowrunnerPayload.EPISODES, -1, true,
                () -> own() != null && own().episodes() ? 1 : 0));
        keys.add(new Key(x0 + WIDTH - 50, y0 + HOME_ROW, 44, 14, Component.translatable("showrunner.scarlet.raise"), RAISE_HOME, 0, true, () -> -1));
        HexSky.Time[] hours = HexSky.Time.values();
        for (int i = 0; i < hours.length; i++) {
            keys.add(new Key(x0 + 6 + i * 31, y0 + TIME_ROW, 29, 14, label("time", hours[i].getSerializedName()), ShowrunnerPayload.TIME, i, true,
                    () -> own() == null ? -1 : own().sky().time().ordinal()));
        }
        HexSky.Weather[] weathers = HexSky.Weather.values();
        for (int i = 0; i < weathers.length; i++) {
            keys.add(new Key(x0 + 6 + i * 39, y0 + WEATHER_ROW, 37, 14, label("weather", weathers[i].getSerializedName()), ShowrunnerPayload.WEATHER, i,
                    true, () -> own() == null ? -1 : own().sky().weather().ordinal()));
        }
        HexBuild[] builds = HexBuild.values();
        for (int i = 0; i < builds.length; i++) {
            keys.add(new Key(x0 + 6 + i * 39, y0 + BUILD_ROW, 37, 14, label("build", builds[i].getSerializedName()), ShowrunnerPayload.BUILD, i, false,
                    this::nextBuild));
        }
        EditBox box = new EditBox(font, x0 + 8, y0 + NAME_ROW + 5, WIDTH - 16, 14, Component.translatable("showrunner.scarlet.name"));
        box.setMaxLength(Hex.MAX_NAME_LENGTH);
        box.setBordered(false);
        box.setTextColor(ARGB.color(1.0F, TEXT));
        HexSnapshot own = own();
        box.setValue(own == null ? "" : own.name());
        box.setHint(Component.translatable("showrunner.scarlet.name_hint"));
        name = addRenderableWidget(box);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // the world stays in view, every change seen as it happens
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        double now = minecraft.level == null ? 0.0 : minecraft.level.getGameTime() + partialTick;
        float open = Ease.outCubic(Ease.clamp01((float) ((now - openedAt) / OPEN_TICKS)));
        float slide = (1.0F - open) * (HEIGHT * 0.6F);
        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0F, slide);
        int x0 = left();
        int y0 = top();
        body(graphics, x0, y0, open, now);
        HexSnapshot own = own();
        graphics.pose().pushMatrix();
        graphics.pose().translate(x0 + WIDTH / 2.0F, y0 + 7.0F);
        graphics.pose().scale(0.75F);
        graphics.centeredText(font, Component.translatable("showrunner.scarlet.title"), 0, 0, ARGB.color(open, ScarletPalette.BRIGHT_SCARLET));
        graphics.pose().popMatrix();
        display(graphics, x0, y0, own, open, now);
        label(graphics, x0, y0 + ERA_ROW - 9, "showrunner.scarlet.channel", open);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x0 + 7.0F, y0 + EPISODE_ROW + 4.5F);
        graphics.pose().scale(0.625F);
        graphics.text(font, Component.translatable("showrunner.scarlet.episodes"), 0, 0, ARGB.color(open * 0.85F, LABEL), false);
        graphics.pose().popMatrix();
        graphics.pose().pushMatrix();
        graphics.pose().translate(x0 + 7.0F, y0 + HOME_ROW + 4.5F);
        graphics.pose().scale(0.625F);
        graphics.text(font, Component.translatable("showrunner.scarlet.home"), 0, 0, ARGB.color(open * 0.85F, LABEL), false);
        graphics.pose().popMatrix();
        label(graphics, x0, y0 + TIME_ROW - 9, "showrunner.scarlet.time", open);
        label(graphics, x0, y0 + WEATHER_ROW - 9, "showrunner.scarlet.weather", open);
        label(graphics, x0, y0 + BUILD_ROW - 9, "showrunner.scarlet.next", open);
        for (Key key : keys) {
            key(graphics, key, mouseX, mouseY - Math.round(slide), own, open, now);
        }
        // the name, on a strip of cream like a label stuck on the remote
        fillRounded(graphics, x0 + 5, y0 + NAME_ROW, WIDTH - 10, 18, ARGB.color(open, 0x2A2220));
        graphics.fill(x0 + 6, y0 + NAME_ROW + 1, x0 + WIDTH - 6, y0 + NAME_ROW + 2, ARGB.color(open * 0.6F, KEY_EDGE));
        graphics.pose().popMatrix();
        if (name != null) {
            name.setY(y0 + NAME_ROW + 5 + Math.round(slide));
            name.visible = own != null;
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (own == null) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(x0 + WIDTH / 2.0F, y0 + NAME_ROW + 5 + slide);
            graphics.pose().scale(0.75F);
            graphics.centeredText(font, Component.translatable("showrunner.scarlet.no_hex"), 0, 0, ARGB.color(open * 0.7F, LABEL));
            graphics.pose().popMatrix();
        }
    }

    /**
     * The remote's body: dark bakelite shading down to black, a bevel catching the light along its edge, and a red
     * light by its top that glows steadily while a Hex stands.
     */
    private void body(GuiGraphicsExtractor graphics, int x0, int y0, float open, double now) {
        fillRounded(graphics, x0 - 1, y0 - 1, WIDTH + 2, HEIGHT + 2, ARGB.color(open * 0.9F, BEVEL));
        int steps = 12;
        for (int i = 0; i < steps; i++) {
            int top = y0 + HEIGHT * i / steps;
            int bottom = y0 + HEIGHT * (i + 1) / steps;
            int color = ARGB.srgbLerp(i / (float) (steps - 1), BODY_TOP, BODY_BOTTOM);
            int inset = i == 0 || i == steps - 1 ? 2 : 0;
            graphics.fill(x0 + inset, top, x0 + WIDTH - inset, bottom, ARGB.color(open * 0.97F, color));
        }
        graphics.fill(x0 + 3, y0 + 1, x0 + WIDTH - 3, y0 + 2, ARGB.color(open * 0.5F, 0x6A5852));
        float pulse = own() == null ? 0.25F : 0.75F + 0.25F * Mth.sin((float) now * 0.12F);
        graphics.fill(x0 + WIDTH - 12, y0 + 6, x0 + WIDTH - 8, y0 + 10, ARGB.color(open * pulse, ScarletPalette.BRIGHT_SCARLET));
        graphics.fill(x0 + WIDTH - 11, y0 + 7, x0 + WIDTH - 9, y0 + 8, ARGB.color(open * pulse, ScarletPalette.CORE));
    }

    /**
     * The little window at the top, like a remote's display: what is on, or that nothing is.
     */
    private void display(GuiGraphicsExtractor graphics, int x0, int y0, @Nullable HexSnapshot own, float open, double now) {
        fillRounded(graphics, x0 + 8, y0 + 18, WIDTH - 16, 26, ARGB.color(open, 0x0E0A0A));
        float flicker = 0.92F + 0.08F * Mth.sin((float) now * 0.9F);
        if (own == null) {
            graphics.centeredText(font, Component.translatable("showrunner.scarlet.off_air"), x0 + WIDTH / 2, y0 + 27,
                    ARGB.color(open * 0.6F * flicker, ScarletPalette.CRIMSON));
            return;
        }
        graphics.centeredText(font, Component.literal(own.name()), x0 + WIDTH / 2, y0 + 21, ARGB.color(open * flicker, TEXT));
        Component line = Component.translatable("showrunner.scarlet.on_air", own.eraValue().displayName(), own.episode());
        graphics.pose().pushMatrix();
        graphics.pose().translate(x0 + WIDTH / 2.0F, y0 + 33.0F);
        graphics.pose().scale(0.75F);
        graphics.centeredText(font, line, 0, 0, ARGB.color(open * flicker, ScarletPalette.BRIGHT_SCARLET));
        graphics.pose().popMatrix();
    }

    private void label(GuiGraphicsExtractor graphics, int x0, int y, String key, float open) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x0 + 7.0F, y);
        graphics.pose().scale(0.625F);
        graphics.text(font, Component.translatable(key), 0, 0, ARGB.color(open * 0.85F, LABEL), false);
        graphics.pose().popMatrix();
        graphics.fill(x0 + 6, y + 7, x0 + WIDTH - 6, y + 8, ARGB.color(open * 0.35F, BEVEL));
    }

    /**
     * One button: raised bakelite with its label, lighting scarlet while it is the one in effect.
     */
    private void key(GuiGraphicsExtractor graphics, Key key, int mouseX, int mouseY, @Nullable HexSnapshot own, float open, double now) {
        boolean usable = own != null || !key.needsHex();
        boolean lit = usable && current(key) == key.value() || key.action() == ShowrunnerPayload.EPISODES && usable && current(key) == 1;
        boolean hover = usable && key.contains(mouseX, mouseY);
        float alpha = open * (usable ? 1.0F : 0.4F);
        fillRounded(graphics, key.x(), key.y() + 1, key.width(), key.height(), ARGB.color(alpha, KEY_EDGE));
        int face = lit ? ScarletPalette.CRIMSON : hover ? KEY_HOVER : KEY;
        fillRounded(graphics, key.x(), key.y(), key.width(), key.height() - 1, ARGB.color(alpha, face));
        int shine = lit ? ScarletPalette.SCARLET : 0x5C4D47;
        graphics.fill(key.x() + 2, key.y(), key.x() + key.width() - 2, key.y() + 1, ARGB.color(alpha * 0.8F, shine));
        if (lit) {
            float glow = 0.5F + 0.2F * Mth.sin((float) now * 0.2F + key.x());
            graphics.fill(key.x() + 2, key.y() + 1, key.x() + key.width() - 2, key.y() + 2, ARGB.color(alpha * glow, ScarletPalette.BRIGHT_SCARLET));
        }
        Component text = key.action() == ShowrunnerPayload.EPISODES
                ? Component.translatable(current(key) == 1 ? "showrunner.scarlet.on" : "showrunner.scarlet.off") : key.label();
        graphics.pose().pushMatrix();
        graphics.pose().translate(key.x() + key.width() / 2.0F, key.y() + (key.height() - 1) / 2.0F - 2.5F);
        graphics.pose().scale(0.75F);
        graphics.centeredText(font, text, 0, 0, ARGB.color(alpha, lit ? ScarletPalette.CORE : TEXT));
        graphics.pose().popMatrix();
    }

    /**
     * What a row of buttons stands at: what was just pressed, until word of it comes back, otherwise what the Hex says.
     */
    private int current(Key key) {
        Minecraft minecraft = Minecraft.getInstance();
        double now = minecraft.level == null ? 0.0 : minecraft.level.getGameTime();
        if (pendingAction == key.action() && now - pendingAt < PENDING_TICKS) {
            return pendingValue;
        }
        return key.current().getAsInt();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        Minecraft minecraft = Minecraft.getInstance();
        double now = minecraft.level == null ? 0.0 : minecraft.level.getGameTime();
        float open = Ease.outCubic(Ease.clamp01((float) ((now - openedAt) / OPEN_TICKS)));
        int slide = Math.round((1.0F - open) * (HEIGHT * 0.6F));
        if (event.button() == 0) {
            for (Key key : keys) {
                if ((own() != null || !key.needsHex()) && key.contains((int) event.x(), (int) event.y() - slide)) {
                    press(key, now);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void press(Key key, double now) {
        if (key.action() == RAISE_HOME) {
            // the remote goes down, to look out over the Hex for where the home goes
            Minecraft minecraft = Minecraft.getInstance();
            onClose();
            HomePlacement.begin(minecraft);
            return;
        }
        int value = key.action() == ShowrunnerPayload.EPISODES ? (current(key) == 1 ? 0 : 1) : key.value();
        Services.NETWORK.sendToServer(ShowrunnerPayload.of(key.action(), value));
        pendingAction = key.action();
        pendingValue = value;
        pendingAt = now;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4F + value * 0.04F, 0.3F));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (name != null && name.isFocused() && (event.key() == 257 || event.key() == 335)) {
            String value = name.getValue().strip();
            if (!value.isEmpty()) {
                Services.NETWORK.sendToServer(new ShowrunnerPayload(ShowrunnerPayload.NAME, 0, value));
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.8F, 0.3F));
            }
            name.setFocused(false);
            return true;
        }
        if ((name == null || !name.isFocused()) && ScarletKeyMappings.SHOWRUNNER.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private int nextBuild() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? -1 : Services.PLAYER_DATA.get(player).hexBuild().ordinal();
    }

    private static @Nullable HexSnapshot own() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        for (HexSnapshot hex : Hexes.clientHexes()) {
            if (hex.caster().equals(player.getUUID()) && hex.phaseValue() != Hex.Phase.COLLAPSING) {
                return hex;
            }
        }
        return null;
    }

    private int left() {
        return width - WIDTH - MARGIN;
    }

    private int top() {
        return Math.max(4, (height - HEIGHT) / 2);
    }

    /**
     * A rectangle with its corners rounded off by a pixel.
     */
    private static void fillRounded(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x + 1, y, x + width - 1, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    /**
     * A button's own short label, kept apart from the longer names used elsewhere so it fits on the button.
     */
    private static Component label(String row, String value) {
        return Component.translatable("showrunner.scarlet." + row + "." + value);
    }

    /**
     * @param needsHex whether it only does anything while the caster's Hex stands
     * @param current  what its row stands at now, to light the one in effect
     */
    private record Key(int x, int y, int width, int height, Component label, int action, int value, boolean needsHex, IntSupplier current) {

        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
