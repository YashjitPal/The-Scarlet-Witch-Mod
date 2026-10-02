package com.yashjit.scarlet.client.hex;

import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.hex.Hex;
import com.yashjit.scarlet.hex.HexSnapshot;
import com.yashjit.scarlet.hex.Hexes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * A Hex's title card, like the opening of a sitcom episode: its name, the episode, and who stars in it, styled for the
 * era, with the era's theme playing. It shows when you come into a Hex, and again when a new episode begins while
 * you're inside.
 */
public final class TitleCard {

    /** Seconds on screen. */
    private static final float LENGTH = 5.5F;
    private static final float FADE_IN = 0.6F;
    private static final float FADE_OUT = 0.9F;
    /** Ticks after stepping in, for the wall's static to clear first. */
    private static final int ENTER_DELAY = 10;
    /** Ticks after the era changes, for the channel to finish changing. */
    private static final int EPISODE_DELAY = 26;
    /** Coming back into the same episode within this many ticks shows nothing new. */
    private static final int REPEAT_TICKS = 1200;
    /** Titles each era has to choose from. */
    private static final int TITLES = 4;

    private static final int[] SEVENTIES_STRIPES = {0xD9481C, 0xF08A24, 0xF6C445, 0x7A4A1E};
    private static final int[] BOXES = {0xE63946, 0xF4A261, 0x2A9D8F, 0xE9C46A, 0x457B9D, 0x8AC926};

    private static final Map<UUID, Seen> SEEN = new HashMap<>();
    private static @Nullable ClientLevel seenLevel;
    /** The caster of the Hex the camera is in. */
    private static @Nullable UUID inside;
    private static @Nullable Card pending;
    private static long pendingAt;
    private static @Nullable Card showing;
    private static long shownAt;

    private TitleCard() {
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level != seenLevel) {
            seenLevel = level;
            SEEN.clear();
            inside = null;
            pending = null;
            showing = null;
        }
        if (level == null || player == null) {
            return;
        }
        long now = level.getGameTime();
        HexSnapshot hex = Hexes.clientHexAt(player.getEyePosition(), now);
        UUID id = hex != null ? hex.caster() : null;
        if (hex != null && hex.phaseValue() != Hex.Phase.WARNING && hex.phaseValue() != Hex.Phase.COLLAPSING) {
            Seen seen = SEEN.get(id);
            boolean cameIn = !id.equals(inside);
            boolean newEpisode = seen == null || seen.season != hex.season() || seen.episode != hex.episode();
            if (newEpisode || cameIn && now - seen.at >= REPEAT_TICKS) {
                pending = new Card(id, hex.name(), hex.casterName(), hex.eraValue(), hex.episode(), hex.season());
                pendingAt = now + (cameIn ? ENTER_DELAY : EPISODE_DELAY);
                SEEN.put(id, new Seen(hex.season(), hex.episode(), now));
            }
        }
        inside = id;
        if (pending != null && now >= pendingAt) {
            if (pending.hex.equals(inside)) {
                showing = pending;
                shownAt = System.nanoTime();
                HexTunes.play(pending.era);
            }
            pending = null;
        }
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Card card = showing;
        if (card == null) {
            return;
        }
        float t = (System.nanoTime() - shownAt) / 1.0E9F;
        if (t >= LENGTH) {
            showing = null;
            return;
        }
        float fade = Math.min(Ease.clamp01(t / FADE_IN), Ease.clamp01((LENGTH - t) / FADE_OUT));
        Font font = Minecraft.getInstance().font;
        switch (card.era) {
            case FIFTIES -> fifties(graphics, font, card, t, fade);
            case SIXTIES -> sixties(graphics, font, card, t, fade);
            case SEVENTIES -> seventies(graphics, font, card, t, fade);
            case EIGHTIES -> eighties(graphics, font, card, t, fade);
            case TWO_THOUSANDS -> twoThousands(graphics, font, card, t, fade);
            case PRESENT -> present(graphics, font, card, t, fade);
        }
    }

    /**
     * Soft black and white: the name glowing like a bright title on an old tube, a ruled line drawn out under it, and
     * the credits.
     */
    private static void fifties(GuiGraphicsExtractor g, Font font, Card card, float t, float fade) {
        int w = g.guiWidth();
        float cx = w / 2.0F;
        float cy = g.guiHeight() * 0.4F;
        rect(g, 0, 0, w, g.guiHeight(), ARGB.color(0.55F * fade, 0x000000));
        float s = nameScale(font, card.name, w) * (0.94F + 0.06F * Ease.outCubic(Ease.clamp01(t / 1.4F)));
        Component name = Component.literal(card.name);
        for (int k = 0; k < 8; k++) {
            float angle = k * Mth.PI / 4.0F;
            centered(g, font, name, cx + Mth.cos(angle) * s * 0.45F, cy + Mth.sin(angle) * s * 0.45F, s, ARGB.color(0.1F * fade, 0xFFFFFF));
        }
        centered(g, font, name, cx, cy, s, ARGB.color(fade, 0xF4F4F0));

        float ruleY = cy + 4.5F * s + 8.0F;
        float reach = 48.0F * Ease.outCubic(Ease.clamp01((t - 0.3F) / 0.9F));
        rect(g, cx - 7.0F - reach, ruleY, reach, 1.0F, ARGB.color(0.8F * fade, 0xE0E0E0));
        rect(g, cx + 7.0F, ruleY, reach, 1.0F, ARGB.color(0.8F * fade, 0xE0E0E0));
        diamond(g, cx, ruleY + 0.5F, 3.5F, ARGB.color(fade, 0xF4F4F0));
        spaced(g, font, episodeLine(card).getString().toUpperCase(), cx, ruleY + 11.0F, 1.6F, ARGB.color(0.9F * fade, 0xD6D6D6));
        centered(g, font, title(card).withStyle(ChatFormatting.ITALIC), cx, ruleY + 23.0F, 1.0F, ARGB.color(0.85F * fade, 0xB8B8B8));
        centered(g, font, starring(card), cx, ruleY + 40.0F, 0.75F, ARGB.color(0.7F * fade, 0x9A9A9A));
    }

    /**
     * The animated opening of a 1960s cartoon: inked letters bouncing in one at a time, with stars twinkling around them.
     */
    private static void sixties(GuiGraphicsExtractor g, Font font, Card card, float t, float fade) {
        int w = g.guiWidth();
        float cx = w / 2.0F;
        float cy = g.guiHeight() * 0.4F;
        rect(g, 0, 0, w, g.guiHeight(), ARGB.color(0.4F * fade, 0x000000));
        float s = nameScale(font, card.name, w);
        float x = cx - font.width(card.name) * s / 2.0F;
        for (int i = 0; i < card.name.length(); i++) {
            String letter = String.valueOf(card.name.charAt(i));
            float bounce = Ease.clamp01((t - 0.15F - i * 0.07F) / 0.5F);
            if (bounce > 0.0F) {
                float y = cy - (1.0F - Ease.outBack(bounce)) * 28.0F;
                inked(g, font, Component.literal(letter), x + font.width(letter) * s / 2.0F, y, s, fade, 0xFFFFFF);
            }
            x += font.width(letter) * s;
        }
        float spreadX = font.width(card.name) * s / 2.0F + 28.0F;
        float spreadY = 4.5F * s + 18.0F;
        for (int k = 0; k < 9; k++) {
            float twinkle = Math.max(0.0F, Mth.sin(t * 5.0F + k * 1.7F)) * Ease.clamp01((t - 0.6F) / 0.4F);
            float sx = cx + (hash(k, 1, card.name) * 2.0F - 1.0F) * spreadX;
            float sy = cy + (hash(k, 2, card.name) * 2.0F - 1.0F) * spreadY;
            star(g, sx, sy, 1.5F + 3.0F * twinkle, ARGB.color(twinkle * fade, 0xFFFFFF));
        }
        float pop = Ease.outBack(Ease.clamp01((t - 0.9F) / 0.4F));
        if (pop > 0.01F) {
            float lineY = cy + 4.5F * s + 12.0F;
            inked(g, font, episodeLine(card), cx, lineY, 1.25F * pop, fade, 0xFFFFFF);
            centered(g, font, title(card), cx, lineY + 14.0F, pop, ARGB.color(0.85F * fade, 0xD0D0D0));
            centered(g, font, starring(card), cx, lineY + 30.0F, 0.75F * pop, ARGB.color(0.7F * fade, 0xA8A8A8));
        }
    }

    /**
     * Warm 1970s film: bands of orange, gold and brown sweeping in above and below a cream title, its letters fading
     * in one after another.
     */
    private static void seventies(GuiGraphicsExtractor g, Font font, Card card, float t, float fade) {
        int w = g.guiWidth();
        float cx = w / 2.0F;
        float cy = g.guiHeight() * 0.4F;
        rect(g, 0, 0, w, g.guiHeight(), ARGB.color(0.35F * fade, 0x2A160A));
        float s = nameScale(font, card.name, w);
        float half = 4.5F * s;
        float sweep = w * Ease.outCubic(Ease.clamp01(t / 0.9F));
        for (int k = 0; k < SEVENTIES_STRIPES.length; k++) {
            rect(g, 0, cy - half - 16.0F + k * 3.0F, sweep, 2.0F, ARGB.color(0.9F * fade, SEVENTIES_STRIPES[k]));
            rect(g, w - sweep, cy + half + 5.0F + k * 3.0F, sweep, 2.0F, ARGB.color(0.9F * fade, SEVENTIES_STRIPES[SEVENTIES_STRIPES.length - 1 - k]));
        }
        float x = cx - font.width(card.name) * s / 2.0F;
        for (int i = 0; i < card.name.length(); i++) {
            String letter = String.valueOf(card.name.charAt(i));
            float alpha = fade * Ease.clamp01((t - 0.3F - i * 0.05F) / 0.35F);
            if (alpha > 0.01F) {
                float lx = x + font.width(letter) * s / 2.0F;
                centered(g, font, Component.literal(letter), lx + s * 0.5F, cy + s * 0.5F, s, ARGB.color(alpha, 0x5A3315));
                centered(g, font, Component.literal(letter), lx, cy, s, ARGB.color(alpha, 0xFFF1D0));
            }
            x += font.width(letter) * s;
        }
        float lineY = cy + half + 26.0F;
        centered(g, font, episodeLine(card), cx, lineY, 1.25F, ARGB.color(fade, 0xFFF1D0));
        centered(g, font, title(card), cx, lineY + 13.0F, 1.0F, ARGB.color(0.9F * fade, 0xF6C445));
        centered(g, font, starring(card), cx, lineY + 28.0F, 0.75F, ARGB.color(0.75F * fade, 0xE9C9A0));
    }

    /**
     * Neon on videotape: a dark band edged in pink and cyan, scanlines, and a title with its colors split apart that
     * jumps now and then like a worn tape.
     */
    private static void eighties(GuiGraphicsExtractor g, Font font, Card card, float t, float fade) {
        int w = g.guiWidth();
        float cx = w / 2.0F;
        float cy = g.guiHeight() * 0.4F;
        float s = nameScale(font, card.name, w);
        float bandHeight = 9.0F * s + 52.0F;
        float top = cy - 4.5F * s - 14.0F;
        float sweep = w * Ease.outCubic(Ease.clamp01(t / 0.6F));
        rect(g, 0, top, w, bandHeight, ARGB.color(0.7F * fade, 0x07020F));
        rect(g, 0, top, sweep, 1.0F, ARGB.color(fade, 0xFF2E9A));
        rect(g, w - sweep, top + bandHeight - 1.0F, sweep, 1.0F, ARGB.color(fade, 0x2EE6FF));
        for (float y = top + 1.0F; y < top + bandHeight - 1.0F; y += 2.0F) {
            rect(g, 0, y, w, 1.0F, ARGB.color(0.22F * fade, 0x000000));
        }
        float frame = (float) Math.floor(t * 12.0F);
        float jolt = hash((int) frame, 7, card.name) < 0.12F ? (hash((int) frame, 8, card.name) - 0.5F) * 8.0F : 0.0F;
        Component name = Component.literal(card.name);
        float split = s * 0.4F * (1.0F + Math.abs(jolt) * 0.3F);
        float glow = Ease.clamp01(t / 0.5F);
        centered(g, font, name, cx + jolt - split, cy, s, ARGB.color(0.8F * fade * glow, 0x2EE6FF));
        centered(g, font, name, cx + jolt + split, cy, s, ARGB.color(0.8F * fade * glow, 0xFF2E9A));
        centered(g, font, name, cx + jolt, cy, s, ARGB.color(fade * glow, 0xFFF6FF));
        float lineY = cy + 4.5F * s + 13.0F;
        centered(g, font, episodeLine(card), cx + jolt * 0.5F, lineY, 1.25F, ARGB.color(fade, 0xFFE45E));
        centered(g, font, title(card), cx + jolt * 0.5F, lineY + 13.0F, 1.0F, ARGB.color(0.9F * fade, 0xFF7AC8));
    }

    /**
     * A loud 2000s opening: every letter in its own tilted box of color, slamming in one after another and never quite
     * holding still.
     */
    private static void twoThousands(GuiGraphicsExtractor g, Font font, Card card, float t, float fade) {
        int w = g.guiWidth();
        float cx = w / 2.0F;
        float cy = g.guiHeight() * 0.4F;
        rect(g, 0, 0, w, g.guiHeight(), ARGB.color(0.25F * fade, 0x000000));
        float s = nameScale(font, card.name, w) * 0.9F;
        float gap = 3.0F;
        float total = font.width(card.name) * s + gap * (card.name.length() - 1);
        float x = cx - total / 2.0F;
        float boxHeight = 9.0F * s + 6.0F;
        for (int i = 0; i < card.name.length(); i++) {
            String letter = String.valueOf(card.name.charAt(i));
            float letterWidth = font.width(letter) * s;
            float slam = Ease.clamp01((t - 0.1F - i * 0.06F) / 0.3F);
            if (slam > 0.0F && !letter.isBlank()) {
                int color = BOXES[Math.floorMod(i + card.name.hashCode(), BOXES.length)];
                float jitter = Mth.sin(t * 23.0F + i * 2.3F) * 0.02F * (hash((int) (t * 2.0F), i, card.name) < 0.3F ? 1.0F : 0.0F);
                g.pose().pushMatrix();
                g.pose().translate(x + letterWidth / 2.0F, cy);
                g.pose().rotate((hash(i, 3, card.name) - 0.5F) * 0.3F + jitter);
                g.pose().scale(Ease.outBack(slam));
                rect(g, -letterWidth / 2.0F - 3.0F, -boxHeight / 2.0F, letterWidth + 6.0F, boxHeight, ARGB.color(fade, color));
                centered(g, font, Component.literal(letter), 0.0F, 0.5F, s, ARGB.color(fade, color == 0xE9C46A ? 0x1A1A1A : 0xFFFFFF));
                g.pose().popMatrix();
            }
            x += letterWidth + gap;
        }
        float lineY = cy + boxHeight / 2.0F + 14.0F;
        Component line = episodeLine(card);
        float lineWidth = font.width(line) * 1.25F + 10.0F;
        float pop = Ease.outBack(Ease.clamp01((t - 0.8F) / 0.3F));
        if (pop > 0.01F) {
            g.pose().pushMatrix();
            g.pose().translate(cx, lineY);
            g.pose().scale(pop);
            rect(g, -lineWidth / 2.0F, -8.0F, lineWidth, 15.0F, ARGB.color(fade, 0xFFFFFF));
            centered(g, font, line, 0.0F, 0.0F, 1.25F, ARGB.color(fade, 0x111111));
            g.pose().popMatrix();
            centered(g, font, title(card), cx, lineY + 16.0F, 1.0F, ARGB.color(fade, 0xFFFFFF));
        }
    }

    /**
     * The present day, filmed like a documentary: no fuss, just a caption sliding in at the side of the frame.
     */
    private static void present(GuiGraphicsExtractor g, Font font, Card card, float t, float fade) {
        float slide = Ease.outCubic(Ease.clamp01(t / 0.8F));
        float x = 24.0F - (1.0F - slide) * 14.0F;
        float y = g.guiHeight() * 0.58F;
        rect(g, x, y, 2.0F, 38.0F * slide, ARGB.color(fade, 0xFFFFFF));
        g.pose().pushMatrix();
        g.pose().translate(x + 9.0F, y + 1.0F);
        g.pose().scale(2.0F);
        g.text(font, card.name, 0, 0, ARGB.color(fade, 0xFFFFFF), true);
        g.pose().popMatrix();
        g.text(font, Component.translatable("hex.scarlet.card.season_episode", card.season, card.episode), Math.round(x + 9.0F),
                Math.round(y + 21.0F), ARGB.color(0.9F * fade, 0xD0D0D0), true);
        g.text(font, title(card).withStyle(ChatFormatting.ITALIC), Math.round(x + 9.0F), Math.round(y + 31.0F),
                ARGB.color(0.8F * fade, 0xA8A8A8), true);
    }

    private static float nameScale(Font font, String name, int screenWidth) {
        return Math.clamp(screenWidth * 0.6F / Math.max(1, font.width(name)), 1.5F, 4.0F);
    }

    private static Component episodeLine(Card card) {
        return card.season > 1
                ? Component.translatable("hex.scarlet.card.season_episode", card.season, card.episode)
                : Component.translatable("hex.scarlet.card.episode", card.episode);
    }

    private static MutableComponent title(Card card) {
        int pick = Math.floorMod(card.episode - 1 + (card.season - 1) * 3, TITLES);
        return Component.translatable("hex.scarlet.card.title",
                Component.translatable("hex.scarlet.episode." + card.era.getSerializedName() + "." + pick));
    }

    private static Component starring(Card card) {
        return Component.translatable("hex.scarlet.card.starring", card.casterName);
    }

    /**
     * Text centered on a point, scaled up from the font's own size.
     */
    private static void centered(GuiGraphicsExtractor g, Font font, Component text, float x, float y, float scale, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale);
        g.pose().translate(-font.width(text) / 2.0F, -4.0F);
        g.text(font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    /**
     * Text with a thick black outline, like an inked cartoon title.
     */
    private static void inked(GuiGraphicsExtractor g, Font font, Component text, float x, float y, float scale, float fade, int color) {
        float o = Math.max(1.0F, scale * 0.45F);
        for (int k = 0; k < 8; k++) {
            float angle = k * Mth.PI / 4.0F;
            centered(g, font, text, x + Mth.cos(angle) * o, y + Mth.sin(angle) * o, scale, ARGB.color(fade, 0x000000));
        }
        centered(g, font, text, x + o * 0.6F, y + o * 1.6F, scale, ARGB.color(fade, 0x000000));
        centered(g, font, text, x, y, scale, ARGB.color(fade, color));
    }

    /**
     * Small capitals spread apart, centered on a point.
     */
    private static void spaced(GuiGraphicsExtractor g, Font font, String text, float x, float y, float spacing, int color) {
        float width = font.width(text) + spacing * (text.length() - 1);
        float at = x - width / 2.0F;
        for (int i = 0; i < text.length(); i++) {
            String letter = String.valueOf(text.charAt(i));
            g.pose().pushMatrix();
            g.pose().translate(at, y - 4.0F);
            g.text(font, letter, 0, 0, color, false);
            g.pose().popMatrix();
            at += font.width(letter) + spacing;
        }
    }

    private static void rect(GuiGraphicsExtractor g, float x, float y, float width, float height, int color) {
        if (width <= 0.0F || height <= 0.0F) {
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(width, height);
        g.fill(0, 0, 1, 1, color);
        g.pose().popMatrix();
    }

    private static void diamond(GuiGraphicsExtractor g, float x, float y, float size, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().rotate(Mth.PI / 4.0F);
        rect(g, -size / 2.0F, -size / 2.0F, size, size, color);
        g.pose().popMatrix();
    }

    /**
     * A four-pointed twinkle.
     */
    private static void star(GuiGraphicsExtractor g, float x, float y, float size, int color) {
        rect(g, x - size, y - 0.5F, size * 2.0F, 1.0F, color);
        rect(g, x - 0.5F, y - size, 1.0F, size * 2.0F, color);
        rect(g, x - 1.0F, y - 1.0F, 2.0F, 2.0F, color);
    }

    private static float hash(int a, int b, String salt) {
        int h = salt.hashCode() * 31 + a * 0x9E3779B1 + b * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    /**
     * @param hex the caster of the Hex it belongs to
     */
    private record Card(UUID hex, String name, String casterName, Era era, int episode, int season) {
    }

    private record Seen(int season, int episode, long at) {
    }
}
