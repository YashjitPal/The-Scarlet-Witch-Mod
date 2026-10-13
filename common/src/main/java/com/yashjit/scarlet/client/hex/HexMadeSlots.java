package com.yashjit.scarlet.client.hex;

import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.fx.Glitch;
import com.yashjit.scarlet.decor.HexMade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * Whatever a Hex made, as it shows in a slot: a faint scarlet tag in its corner while it is inside its Hex, and outside
 * it the picture breaking up over it, the way the Hex's own blocks break up as they go, more and more until it is gone.
 */
public final class HexMadeSlots {

    private static final int[] GLITCH = {ScarletPalette.SCARLET, ScarletPalette.BRIGHT_SCARLET, 0xFFFFFF, 0x000000};

    private HexMadeSlots() {
    }

    public static void draw(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        if (stack.isEmpty() || HexMade.maker(stack) == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long now = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        float fading = HexMade.fading(stack, now);
        if (fading < 0.0F) {
            graphics.fill(x, y, x + 2, y + 1, ARGB.color(0.85F, ScarletPalette.SCARLET));
            graphics.fill(x, y + 1, x + 1, y + 2, ARGB.color(0.85F, ScarletPalette.SCARLET));
            return;
        }
        // a new picture of the noise a dozen times a second, each slot its own
        int frame = (int) (Util.getMillis() / 80);
        int seed = x * 31 + y * 17;
        if (fading > 0.55F && Glitch.hash(frame, seed, 7) < fading - 0.3F) {
            // the picture losing its hold altogether for a frame
            graphics.fill(x, y, x + 16, y + 16, ARGB.color(0.75F, 0x120206));
        }
        int bars = 1 + Math.round(fading * 7);
        for (int k = 0; k < bars; k++) {
            int row = (int) (Glitch.hash(frame, seed, 11 + k) * 16);
            int tall = Glitch.hash(frame, seed, 31 + k) < 0.3F ? 2 : 1;
            int shift = (int) ((Glitch.hash(frame, seed, 51 + k) - 0.5F) * 6);
            int color = GLITCH[(int) (Glitch.hash(frame, seed, 71 + k) * GLITCH.length) % GLITCH.length];
            int from = Math.max(0, shift);
            int to = Math.min(16, 16 + shift);
            graphics.fill(x + from, y + row, x + to, y + Math.min(16, row + tall), ARGB.color(0.5F + 0.45F * fading, color));
        }
        int pixels = 3 + Math.round(fading * 20);
        for (int k = 0; k < pixels; k++) {
            int px = (int) (Glitch.hash(frame, seed, 101 + k) * 16);
            int py = (int) (Glitch.hash(frame, seed, 201 + k) * 16);
            int color = Glitch.hash(frame, seed, 301 + k) < 0.7F ? ScarletPalette.BRIGHT_SCARLET : 0xFFFFFF;
            graphics.fill(x + px, y + py, x + px + 1, y + py + 1, ARGB.color(0.9F, color));
        }
    }
}
