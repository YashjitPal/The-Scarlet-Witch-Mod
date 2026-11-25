package com.yashjit.scarlet.client.magic;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.ScarletKeyMappings;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.render.Pixels;
import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.darkhold.Darkhold;
import com.yashjit.scarlet.magic.Magic;
import com.yashjit.scarlet.magic.MagicState;
import com.yashjit.scarlet.magic.Mastery;
import com.yashjit.scarlet.magic.Spell;
import com.yashjit.scarlet.magic.WiccanSpells;
import com.yashjit.scarlet.network.SelectSpellPayload;
import com.yashjit.scarlet.platform.Services;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Hold the wheel key, flick the mouse toward a spell, let go. It is drawn over the HUD rather than as a screen, so you
 * keep walking while you choose; the mouse steers its cursor instead of the camera while it is open. The Darkhold's
 * spells take their places on it, in black-crimson, only while the book is carried.
 */
public final class SpellWheel {

    private static final float RADIUS = 46.0F;
    private static final float DEAD_ZONE = 10.0F;
    private static final Identifier SLOT = Scarlet.id("wheel/slot");
    private static final Identifier LOCK = Scarlet.id("wheel/lock");

    private static boolean open;
    private static boolean craft;
    private static float openness;
    private static float cursorX;
    private static float cursorY;
    /** Spell under the cursor, if any. */
    private static @Nullable Spell hovered;
    /** How far each spell has lit under the cursor, by spell. */
    private static final float[] HOVER = new float[Spell.count()];

    private SpellWheel() {
    }

    public static boolean isOpen() {
        return open;
    }

    /**
     * The spells on the wheel for someone, clockwise from the top.
     */
    static List<Spell> slots(Player player) {
        if (com.yashjit.scarlet.player.AgathaForm.worn(player)) return com.yashjit.scarlet.magic.AgathaMagic.wheel(player, craft);
        if (CrownItem.isWiccan(player)) return WiccanSpells.wheel(player, craft);
        boolean book = Darkhold.carries(player);
        var slots = new java.util.ArrayList<>(Spell.WHEEL.stream().filter(spell -> !spell.darkhold() || book).toList());
        for (Spell spell : com.yashjit.scarlet.magic.GrimoireLeaf.COVEN_SPELLS)
            if (!slots.contains(spell) && com.yashjit.scarlet.magic.GrimoireLeaf.learned(player, spell)) slots.add(spell);
        return slots;
    }

    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        boolean held = player != null && minecraft.gui.screen() == null && ScarletKeyMappings.SPELL_WHEEL.isDown() && Magic.canCast(player)
                && !player.isSpectator();
        boolean nextCraft = held && (CrownItem.isWiccan(player) || com.yashjit.scarlet.player.AgathaForm.worn(player)) && minecraft.options.keyShift.isDown();
        if (held && craft != nextCraft) {
            craft = nextCraft;
            hovered = null;
            cursorX = cursorY = 0;
        }
        if (held && !open) {
            open = true;
            cursorX = 0.0F;
            cursorY = 0.0F;
            hovered = null;
            player.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.25F, 1.7F);
        } else if (!held && open) {
            open = false;
            if (player != null && hovered != null) {
                Spell spell = hovered;
                if (minecraft.gui.screen() == null && slots(player).contains(spell) && selectable(player, spell) && Magic.state(player).selectedSpell() != spell) {
                    Services.NETWORK.sendToServer(new SelectSpellPayload(spell.ordinal()));
                    player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, spell.darkhold() ? 0.8F : 1.35F);
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
        LocalPlayer player = Minecraft.getInstance().player;
        if (length > DEAD_ZONE && player != null) {
            List<Spell> slots = slots(player);
            double angle = Math.atan2(cursorX, -cursorY);
            double slot = angle / (Math.PI * 2 / slots.size());
            hovered = slots.get(Math.floorMod((int) Math.round(slot), slots.size()));
        } else hovered = null;
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
        return WiccanSpells.permits(player, spell) && spell.available() && WiccanSpells.unlocked(player, spell) && (!spell.darkhold() || Darkhold.carries(player));
    }

    static void render(GuiGraphicsExtractor graphics, Minecraft minecraft, float seconds) {
        openness = Ease.damp(openness, open ? 1.0F : 0.0F, open ? 13.0F : 20.0F, seconds);
        for (Spell spell : Spell.values()) {
            HOVER[spell.ordinal()] = Ease.damp(HOVER[spell.ordinal()], open && spell == hovered ? 1.0F : 0.0F, 16.0F, seconds);
        }
        LocalPlayer player = minecraft.player;
        if (openness < 0.01F || player == null) {
            return;
        }
        MagicState state = Magic.state(player);
        List<Spell> slots = slots(player);
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;
        float scale = 0.8F + 0.2F * Ease.outBack(openness);
        Pixels.Ramp ramp = Pixels.Ramp.ofCaster(player);
        boolean billy = ramp == Pixels.Ramp.WICCAN;
        boolean violet = ramp == Pixels.Ramp.AGATHA;
        int detail = violet ? 0xEAD3F4 : billy ? 0xCCE4F4 : 0xE6D6DA;
        float radius = (slots.size() > 9 ? 58 : RADIUS) * scale;

        MagicHud.sprite(graphics, MagicHud.GLOW, cx, cy, (RADIUS + 34.0F) * 2.0F * scale, ARGB.color(0.6F * openness, ramp.color(Pixels.SHADOW)));
        for (int i = 0; i < 72; i++) {
            double angle = Math.PI * 2 * i / 72;
            MagicHud.bead(graphics, cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius, 0.75F,
                    ARGB.color(0.4F * openness, violet ? 0xAC72D4 : billy ? 0x5DBBFF : ScarletPalette.CRIMSON));
        }

        for (int i = 0; i < slots.size(); i++) {
            Spell spell = slots.get(i);
            double angle = Math.PI * 2 * i / slots.size() - Math.PI / 2;
            float x = cx + (float) Math.cos(angle) * radius;
            float y = cy + (float) Math.sin(angle) * radius;
            float hover = HOVER[spell.ordinal()];
            boolean selectable = selectable(player, spell);
            boolean selected = state.selectedSpell() == spell;
            boolean dark = spell.darkhold() && !billy;
            float size = scale * (1.0F + 0.2F * hover);
            float glow = 0.3F * hover + (selected ? 0.22F : 0.0F);
            if (dark) {
                // the book's own spells sit in a pall of their own
                MagicHud.sprite(graphics, MagicHud.GLOW, x, y, 40.0F * size, ARGB.color(openness * 0.75F, ScarletPalette.VOID));
            }
            if (glow > 0.01F) {
                MagicHud.sprite(graphics, MagicHud.GLOW, x, y, 34.0F * size, ARGB.color(openness * glow, violet ? 0xBB79E6 : dark ? ScarletPalette.SICKLY : billy ? 0x419EFF : ScarletPalette.SCARLET));
            }
            int slotTint = dark ? (selected ? 0xC2304F : selectable ? ScarletPalette.SICKLY : 0x4A2A32)
                    : selected ? ScarletPalette.BRIGHT_SCARLET : selectable ? 0xC23048 : 0x6E6066;
            if (violet) slotTint = selected ? 0xEBB8FF : selectable ? 0x995BB6 : 0x63536B;
            if (billy) slotTint = selected ? 0x88D9FF : selectable ? 0x396EAB : 0x47536A;
            MagicHud.sprite(graphics, SLOT, x, y, 24.0F * size, ARGB.color(openness * (selectable ? 1.0F : 0.75F), slotTint));
            int iconTint = !selectable ? (billy ? 0x596677 : 0x5E5258) : dark ? 0xE8C8CE : 0xFFFFFF;
            String icon = violet ? "agatha_" + spell.id() : billy && !spell.billyOnly() ? "wiccan_" + spell.id() : spell.id();
            MagicHud.sprite(graphics, Scarlet.id("spell/" + icon), x, y, 16.0F * size, ARGB.color(openness, iconTint));
            if (!WiccanSpells.unlocked(player, spell)) {
                MagicHud.sprite(graphics, LOCK, x + 7.0F * size, y + 7.0F * size, 8.0F * size, ARGB.color(openness, detail));
            }
        }

        Spell focus = hovered != null && slots.contains(hovered) ? hovered : slots.contains(state.selectedSpell()) ? state.selectedSpell() : slots.getFirst();
        boolean focusSelectable = selectable(player, focus);
        Component subtitle;
        if (!WiccanSpells.unlocked(player, focus)) {
            subtitle = Component.translatable("spell.scarlet.locked", Mastery.numeral(focus.rank()));
        } else if (!focus.available()) {
            subtitle = Component.translatable("spell.scarlet.unavailable");
        } else if ((billy || violet) && state.readyAt(focus) > player.level().getGameTime()) {
            subtitle = Component.translatable("wiccan.cooldown", (state.readyAt(focus) - player.level().getGameTime() + 19) / 20);
        } else if (focus == Spell.SIPHON) {
            subtitle = Component.translatable("agatha.scarlet.charge", com.yashjit.scarlet.magic.AgathaMagic.charge(player));
        } else if (focus.input() == Spell.Input.TAP) {
            subtitle = Component.translatable("spell.scarlet.cost", Math.round(focus.cost()));
        } else {
            subtitle = Component.translatable("spell.scarlet.cost.channel", Math.round(focus.cost() * 20));
        }
        int nameColor = violet ? (focusSelectable ? 0xECCBFF : 0xBBAAC8) : billy ? (focusSelectable ? 0xA6E5FF : 0xA4B6C8)
                : !focusSelectable ? 0xB8A8AE : focus.darkhold() ? 0xD9425E : ScarletPalette.BRIGHT_SCARLET;
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy - 6);
        graphics.pose().scale(scale);
        graphics.centeredText(minecraft.font, focus.displayName(), 0, 0, ARGB.color(openness, nameColor));
        graphics.pose().scale(0.7F);
        graphics.centeredText(minecraft.font, subtitle, 0, 15, ARGB.color(openness * 0.85F, detail));
        graphics.pose().popMatrix();

        if (billy || violet) {
            Component label = craft ? Component.translatable("wiccan.wheel.craft") : Component.translatable(violet ? "agatha.scarlet.grimoire" : "wiccan.wheel.grimoire", minecraft.options.keyShift.getTranslatedKeyMessage());
            int y = Math.min(graphics.guiHeight() - 10, cy + (int)radius + 16);
            graphics.centeredText(minecraft.font, label, cx, y, ARGB.color(openness, violet ? 0xE6C8F5 : 0xC5E7FF));
        }

        if (open && Math.hypot(cursorX, cursorY) > 2.0) {
            MagicHud.bead(graphics, cx + cursorX * scale, cy + cursorY * scale, 1.5F, ARGB.color(0.7F * openness, ramp.color(Pixels.HOT)));
        }
    }
}
