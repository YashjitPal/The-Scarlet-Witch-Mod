package com.yashjit.scarlet.client.magic;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.ScarletKeyMappings;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.magic.Magic;
import com.yashjit.scarlet.magic.MagicState;
import com.yashjit.scarlet.magic.Mastery;
import com.yashjit.scarlet.magic.Spell;
import com.yashjit.scarlet.network.SelectSpellPayload;
import com.yashjit.scarlet.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Player;

/**
 * Hold the wheel key, flick the mouse toward a spell, let go. It is drawn over the HUD rather than as a screen, so you
 * keep walking while you choose; the mouse steers its cursor instead of the camera while it is open.
 */
public final class SpellWheel {

    private static final float RADIUS = 46.0F;
    private static final float DEAD_ZONE = 10.0F;
    private static final Identifier SLOT = Scarlet.id("wheel/slot");
    private static final Identifier LOCK = Scarlet.id("wheel/lock");

    private static boolean open;
    private static float openness;
    private static float cursorX;
    private static float cursorY;
    private static int hovered = -1;
    private static final float[] HOVER = new float[Spell.count()];

    private SpellWheel() {
    }

    public static boolean isOpen() {
        return open;
    }

    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        boolean held = player != null && minecraft.gui.screen() == null && ScarletKeyMappings.SPELL_WHEEL.isDown() && CrownItem.isWearingCrown(player);
        if (held && !open) {
            open = true;
            cursorX = 0.0F;
            cursorY = 0.0F;
            hovered = -1;
            player.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.25F, 1.7F);
        } else if (!held && open) {
            open = false;
            if (player != null && hovered >= 0) {
                Spell spell = Spell.byIndex(hovered);
                if (selectable(player, spell) && Magic.state(player).selected() != hovered) {
                    Services.NETWORK.sendToServer(new SelectSpellPayload(hovered));
                    player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.35F);
                }
            }
        }
    }

    public static void mouseMoved(double dx, double dy) {
        float scale = 0.6F / Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        cursorX += (float) dx * scale;
        cursorY += (float) dy * scale;
        float length = (float) Math.hypot(cursorX, cursorY);
        if (length > RADIUS) {
            cursorX *= RADIUS / length;
            cursorY *= RADIUS / length;
            length = RADIUS;
        }
        if (length > DEAD_ZONE) {
            double angle = Math.atan2(cursorX, -cursorY);
            double slot = angle / (Math.PI * 2 / Spell.count());
            hovered = Math.floorMod((int) Math.round(slot), Spell.count());
        }
    }

    /**
     * Development only: places the cursor directly, for automated scenes.
     */
    public static void debugCursor(float x, float y) {
        cursorX = 0.0F;
        cursorY = 0.0F;
        float scale = 0.6F / Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        mouseMoved(x / scale, y / scale);
    }

    static boolean selectable(Player player, Spell spell) {
        return spell.available() && Mastery.rank(player) >= spell.rank();
    }

    static void render(GuiGraphicsExtractor graphics, Minecraft minecraft, float seconds) {
        openness = Ease.damp(openness, open ? 1.0F : 0.0F, open ? 13.0F : 20.0F, seconds);
        for (int i = 0; i < HOVER.length; i++) {
            HOVER[i] = Ease.damp(HOVER[i], open && i == hovered ? 1.0F : 0.0F, 16.0F, seconds);
        }
        LocalPlayer player = minecraft.player;
        if (openness < 0.01F || player == null) {
            return;
        }
        MagicState state = Magic.state(player);
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;
        float scale = 0.8F + 0.2F * Ease.outBack(openness);
        float radius = RADIUS * scale;

        MagicHud.sprite(graphics, MagicHud.GLOW, cx, cy, (RADIUS + 34.0F) * 2.0F * scale, ARGB.color(0.6F * openness, ScarletPalette.SHADOW));
        for (int i = 0; i < 72; i++) {
            double angle = Math.PI * 2 * i / 72;
            MagicHud.bead(graphics, cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius, 0.75F,
                    ARGB.color(0.4F * openness, ScarletPalette.CRIMSON));
        }

        for (int i = 0; i < Spell.count(); i++) {
            Spell spell = Spell.byIndex(i);
            double angle = Math.PI * 2 * i / Spell.count() - Math.PI / 2;
            float x = cx + (float) Math.cos(angle) * radius;
            float y = cy + (float) Math.sin(angle) * radius;
            float hover = HOVER[i];
            boolean selectable = selectable(player, spell);
            boolean selected = state.selected() == i;
            float size = scale * (1.0F + 0.2F * hover);
            float glow = 0.3F * hover + (selected ? 0.22F : 0.0F);
            if (glow > 0.01F) {
                MagicHud.sprite(graphics, MagicHud.GLOW, x, y, 34.0F * size, ARGB.color(openness * glow, ScarletPalette.SCARLET));
            }
            int slotTint = selected ? ScarletPalette.BRIGHT_SCARLET : selectable ? 0xC23048 : 0x6E6066;
            MagicHud.sprite(graphics, SLOT, x, y, 24.0F * size, ARGB.color(openness * (selectable ? 1.0F : 0.75F), slotTint));
            int iconTint = selectable ? 0xFFFFFF : 0x5E5258;
            MagicHud.sprite(graphics, Scarlet.id("spell/" + spell.id()), x, y, 16.0F * size, ARGB.color(openness, iconTint));
            if (Mastery.rank(player) < spell.rank()) {
                MagicHud.sprite(graphics, LOCK, x + 7.0F * size, y + 7.0F * size, 8.0F * size, ARGB.color(openness, 0xE6D6DA));
            }
        }

        Spell focus = hovered >= 0 ? Spell.byIndex(hovered) : state.selectedSpell();
        boolean focusSelectable = selectable(player, focus);
        Component subtitle;
        if (Mastery.rank(player) < focus.rank()) {
            subtitle = Component.translatable("spell.scarlet.locked", Mastery.numeral(focus.rank()));
        } else if (!focus.available()) {
            subtitle = Component.translatable("spell.scarlet.unavailable");
        } else if (focus.input() == Spell.Input.TAP) {
            subtitle = Component.translatable("spell.scarlet.cost", Math.round(focus.cost()));
        } else {
            subtitle = Component.translatable("spell.scarlet.cost.channel", Math.round(focus.cost() * 20));
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy - 6);
        graphics.pose().scale(scale);
        graphics.centeredText(minecraft.font, focus.displayName(), 0, 0, ARGB.color(openness, focusSelectable ? ScarletPalette.BRIGHT_SCARLET : 0xB8A8AE));
        graphics.pose().scale(0.7F);
        graphics.centeredText(minecraft.font, subtitle, 0, 15, ARGB.color(openness * 0.85F, 0xE6D6DA));
        graphics.pose().popMatrix();

        if (open && Math.hypot(cursorX, cursorY) > 2.0) {
            MagicHud.bead(graphics, cx + cursorX * scale, cy + cursorY * scale, 1.5F, ARGB.color(0.7F * openness, ScarletPalette.CORE));
        }
    }
}
