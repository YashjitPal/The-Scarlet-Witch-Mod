package com.yashjit.scarlet.client.magic;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.darkhold.CorruptionClient;
import com.yashjit.scarlet.client.darkhold.DreamwalkClient;
import com.yashjit.scarlet.client.hex.Ejections;
import com.yashjit.scarlet.client.hex.HomePlacement;
import com.yashjit.scarlet.client.platform.ClientPlatform;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.hex.Hex;
import com.yashjit.scarlet.hex.HexPaint;
import com.yashjit.scarlet.hex.HexShape;
import com.yashjit.scarlet.hex.HexSnapshot;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.magic.Magic;
import com.yashjit.scarlet.magic.MagicState;
import com.yashjit.scarlet.magic.Mastery;
import com.yashjit.scarlet.magic.Spell;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The casting HUD: a slim chaos energy bar laid just above the experience bar while a crown is worn, with vanilla's
 * hearts and hunger raised to make room, and a brief caption where held item names appear when the spell changes or
 * a cast is refused.
 */
public final class MagicHud {

    public static final Identifier GLOW = Scarlet.id("hud/glow");

    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 3;
    /** From the bottom of the screen to the top of the bar: one pixel above the experience bar. */
    private static final int BAR_TOP = 33;
    private static final float VITALS_LIFT = 4.0F;
    private static final float LEVEL_LIFT = 8.0F;
    private static final double CAPTION_TICKS = 34.0;

    private static float shown;
    private static float idleDim;
    private static long lastNanos;
    private static double refusedAt = -1.0E9;
    private static Magic.Refusal refusal = Magic.Refusal.NONE;
    private static Spell refusedSpell = Spell.CHAOS_BOLT;
    private static int lastSelected = -1;
    private static double selectedAt = -1.0E9;
    private static final double TIP_TICKS = 100.0;
    /** When the crown first went on this session, for the tip on how to fly. */
    private static double crownedAt = -1.0E9;
    private static float hintShown;
    private static @Nullable Component lastHint;

    private MagicHud() {
    }

    static void onRefused(Magic.Refusal refusal, Spell spell) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            MagicHud.refusal = refusal;
            MagicHud.refusedSpell = spell;
            MagicHud.refusedAt = minecraft.level.getGameTime();
        }
    }

    /**
     * How far vanilla's status bars move up to make room for the energy bar.
     */
    public static float lift(ClientPlatform.StatusBar bar) {
        return switch (bar) {
            case VITALS, MESSAGES -> VITALS_LIFT * shown;
            case LEVEL -> LEVEL_LIFT * shown;
        };
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        long nanos = Util.getNanos();
        float seconds = lastNanos == 0 ? 0.0F : Math.min(0.1F, (nanos - lastNanos) / 1.0E9F);
        lastNanos = nanos;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean crowned = player != null && CrownItem.isWearingCrown(player) && minecraft.gameMode != null
                && (!player.isSpectator() || DreamwalkClient.away());
        shown = Ease.damp(shown, crowned ? 1.0F : 0.0F, 10.0F, seconds);
        if (player == null || minecraft.level == null) {
            return;
        }
        double now = minecraft.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);
        MagicState state = Magic.state(player);
        if (state.selected() != lastSelected) {
            if (lastSelected != -1) {
                selectedAt = now;
            }
            lastSelected = state.selected();
        }
        float max = Magic.maxEnergy(player);
        float energy = Magic.energy(state, now, max);
        boolean idle = energy >= max - 0.01F && now - state.lastCastAt() > 60 && !SpellWheel.isOpen();
        idleDim = Ease.damp(idleDim, idle ? 1.0F : 0.0F, idle ? 1.5F : 12.0F, seconds);
        if (crowned && crownedAt < 0.0) {
            crownedAt = now;
        }
        Component hint = hint(minecraft, player, state, now);
        hintShown = Ease.damp(hintShown, hint != null ? 1.0F : 0.0F, hint != null ? 8.0F : 5.0F, seconds);
        if (hint != null) {
            lastHint = hint;
        }
        if (shown > 0.01F) {
            energyBar(graphics, energy / max, energy < state.selectedSpell().cost(), now, CorruptionClient.darkness(player));
            caption(graphics, minecraft, now, state.selectedSpell());
            if (hintShown > 0.02F && lastHint != null && !SpellWheel.isOpen()) {
                hintLine(graphics, minecraft, lastHint, hintShown * shown);
            }
        }
        SpellWheel.render(graphics, minecraft, seconds);
    }

    /**
     * What is worth knowing right now, if anything: how to fly when the crown first goes on, and how to shape your Hex
     * while its spell is chosen.
     */
    private static @Nullable Component hint(Minecraft minecraft, LocalPlayer player, MagicState state, double now) {
        Component placing = HomePlacement.hint(minecraft);
        if (placing != null) {
            return placing;
        }
        if (now - crownedAt < TIP_TICKS && Mastery.rank(player) >= Spell.LEVITATION.rank() && !state.levitating()) {
            return Component.translatable("hud.scarlet.levitate.tip", minecraft.options.keyJump.getTranslatedKeyMessage());
        }
        if (state.selectedSpell() != Spell.HEX || !Hexes.ownsHex(player)) {
            return null;
        }
        HexSnapshot own = null;
        for (HexSnapshot hex : Hexes.clientHexes()) {
            if (hex.caster().equals(player.getUUID())) {
                own = hex;
            }
        }
        if (state.channeling(Spell.HEX) && own != null && own.parting()) {
            return Component.translatable("hud.scarlet.hex.parting", Math.round(own.opening() * 2.0F));
        }
        boolean inked = own != null && own.phaseValue() == Hex.Phase.STANDING && HexPaint.ink(player.getOffhandItem()) != null
                && HexShape.contains(own.center(), own.radius(), player.position());
        if (state.channeling(Spell.HEX) && inked && !Ejections.holds(player)) {
            return Component.translatable("hud.scarlet.hex.painting", player.getOffhandItem().getHoverName(),
                    minecraft.options.keyShift.getTranslatedKeyMessage());
        }
        if (state.channeling(Spell.HEX) && own != null && !Ejections.holds(player)) {
            return Component.translatable("hud.scarlet.hex.radius", Math.round(own.radius()), Math.round(Hexes.MAX_RADIUS));
        }
        if (own != null && !state.channeling(Spell.HEX) && minecraft.crosshairPickEntity instanceof LivingEntity aimed
                && HexShape.contains(own.center(), own.radius(), aimed.position())) {
            return Component.translatable("hud.scarlet.hex.throw", minecraft.options.keyUse.getTranslatedKeyMessage());
        }
        if (own != null && own.phaseValue() == Hex.Phase.STANDING) {
            Vec3 eye = player.getEyePosition();
            Vec3 reach = eye.add(player.getLookAngle().scale(Hexes.TEAR_REACH));
            if (HexShape.contains(own.center(), own.radius(), eye) != HexShape.contains(own.center(), own.radius(), reach)) {
                return own.parted()
                        ? Component.translatable("hud.scarlet.hex.widen", minecraft.options.keyUse.getTranslatedKeyMessage(),
                                minecraft.options.keyShift.getTranslatedKeyMessage())
                        : Component.translatable("hud.scarlet.hex.part", minecraft.options.keyUse.getTranslatedKeyMessage());
            }
        }
        if (inked && !state.channeling(Spell.HEX)) {
            return Component.translatable("hud.scarlet.hex.paint", minecraft.options.keyUse.getTranslatedKeyMessage(),
                    player.getOffhandItem().getHoverName());
        }
        if (now - selectedAt < TIP_TICKS || now - state.lastCastAt() < TIP_TICKS) {
            return Component.translatable("hud.scarlet.hex.controls", minecraft.options.keyUse.getTranslatedKeyMessage(),
                    minecraft.options.keyShift.getTranslatedKeyMessage());
        }
        return null;
    }

    private static void hintLine(GuiGraphicsExtractor graphics, Minecraft minecraft, Component text, float alpha) {
        int y = graphics.guiHeight() - 71 - Math.round(VITALS_LIFT * shown);
        if (minecraft.gameMode != null && !minecraft.gameMode.canHurtPlayer()) {
            y += 14;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(graphics.guiWidth() / 2.0F, y);
        graphics.pose().scale(0.75F);
        graphics.centeredText(minecraft.font, text, 0, 0, ARGB.color(0.85F * alpha, 0xE6D6DA));
        graphics.pose().popMatrix();
    }

    /**
     * @param darkness how far the Darkhold's corruption has darkened your magic, and with it the energy
     */
    private static void energyBar(GuiGraphicsExtractor graphics, float fraction, boolean low, double now, float darkness) {
        float shake = refusal == Magic.Refusal.ENERGY ? (float) Math.max(0.0, 1.0 - (now - refusedAt) / 8.0) : 0.0F;
        float x0 = (graphics.guiWidth() - BAR_WIDTH) / 2.0F + shake * (float) Math.sin(now * 3.1) * 1.4F;
        // slides up into place as the crown goes on
        float y0 = graphics.guiHeight() - BAR_TOP + (1.0F - shown) * 6.0F;
        float alpha = shown * (1.0F - 0.3F * idleDim);
        float pulse = low ? 0.5F + 0.5F * (float) Math.sin(now * 0.45) : 0.0F;
        int core = Glow.mix(ScarletPalette.CORE, ScarletPalette.SICKLY, darkness);
        int bright = Glow.mix(ScarletPalette.BRIGHT_SCARLET, 0x7A1028, darkness);
        int scarlet = Glow.mix(ScarletPalette.SCARLET, 0x5E0A1E, darkness);
        int crimson = Glow.mix(ScarletPalette.CRIMSON, ScarletPalette.ABYSS, darkness);
        int wine = Glow.mix(ScarletPalette.WINE, ScarletPalette.VOID, darkness);

        rect(graphics, x0 - 1, y0 - 1, BAR_WIDTH + 2, BAR_HEIGHT + 2, ARGB.color(alpha * 0.75F, ScarletPalette.SHADOW));
        rect(graphics, x0, y0, BAR_WIDTH, BAR_HEIGHT, ARGB.color(alpha * 0.85F, wine));
        float fill = BAR_WIDTH * Math.clamp(fraction, 0.0F, 1.0F);
        if (fill > 0.0F) {
            int top = ARGB.srgbLerp(pulse, bright, core);
            int middle = ARGB.srgbLerp(pulse, scarlet, bright);
            rect(graphics, x0, y0, fill, 1, ARGB.color(alpha, top));
            rect(graphics, x0, y0 + 1, fill, 1, ARGB.color(alpha, middle));
            rect(graphics, x0, y0 + 2, fill, 1, ARGB.color(alpha, crimson));
            // a soft gleam travels along the energy every couple of seconds
            float gleam = (float) ((now % 50.0) / 50.0) * (BAR_WIDTH + 40.0F) - 20.0F;
            if (gleam > 0.0F && gleam < fill) {
                sprite(graphics, GLOW, x0 + gleam, y0 + 1.5F, 10.0F, ARGB.color(alpha * 0.35F, core));
            }
            sprite(graphics, GLOW, x0 + fill, y0 + 1.5F, 12.0F, ARGB.color(alpha * (0.5F + 0.3F * pulse), scarlet));
            rect(graphics, x0 + fill - 1, y0, 1, BAR_HEIGHT, ARGB.color(alpha, core));
        }
        if (shake > 0.0F) {
            rect(graphics, x0, y0, BAR_WIDTH, BAR_HEIGHT, ARGB.color(shake * 0.35F * alpha, core));
        }
    }

    private static void caption(GuiGraphicsExtractor graphics, Minecraft minecraft, double now, Spell selected) {
        Component text;
        double since;
        boolean warning = refusal == Magic.Refusal.LOCKED || refusal == Magic.Refusal.UNAVAILABLE || refusal == Magic.Refusal.DARKHOLD
                || refusal == Magic.Refusal.FOOTING;
        if (warning && now - refusedAt < CAPTION_TICKS && now - refusedAt < now - selectedAt) {
            since = now - refusedAt;
            text = switch (refusal) {
                case LOCKED -> Component.translatable("spell.scarlet.locked", Mastery.numeral(refusedSpell.rank()));
                case DARKHOLD -> Component.translatable("spell.scarlet.darkhold");
                case FOOTING -> Component.translatable("spell.scarlet.footing");
                default -> Component.translatable("spell.scarlet.unavailable");
            };
        } else if (now - selectedAt < CAPTION_TICKS) {
            since = now - selectedAt;
            text = selected.displayName();
        } else {
            return;
        }
        float fade = (float) Math.min(Math.min(1.0, since / 3.0), (CAPTION_TICKS - since) / 10.0);
        if (fade <= 0.02F || SpellWheel.isOpen()) {
            return;
        }
        // where vanilla names the held item, raised with it
        int y = graphics.guiHeight() - 59 - Math.round(VITALS_LIFT * shown);
        if (minecraft.gameMode != null && !minecraft.gameMode.canHurtPlayer()) {
            y += 14;
        }
        graphics.centeredText(minecraft.font, text, graphics.guiWidth() / 2, y, ARGB.color(fade, warning ? 0xF2A0AC : ScarletPalette.BRIGHT_SCARLET));
    }

    /**
     * A filled rectangle at fractional GUI coordinates.
     */
    public static void rect(GuiGraphicsExtractor graphics, float x, float y, float width, float height, int color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(width, height);
        graphics.fill(0, 0, 1, 1, color);
        graphics.pose().popMatrix();
    }

    /**
     * A crisp square of {@code size} GUI pixels centered at a fractional position, in the HUD's pixel style.
     */
    public static void bead(GuiGraphicsExtractor graphics, float x, float y, float size, int color) {
        rect(graphics, x - size / 2, y - size / 2, size, size, color);
    }

    /**
     * Draws a square sprite of {@code size} GUI pixels centered at a fractional position.
     */
    public static void sprite(GuiGraphicsExtractor graphics, Identifier sprite, float x, float y, float size, int color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(size / 16.0F);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, -8, -8, 16, 16, color);
        graphics.pose().popMatrix();
    }
}
