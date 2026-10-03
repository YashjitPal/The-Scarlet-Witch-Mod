package com.yashjit.scarlet.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.ScarletPalette;
import org.joml.Vector3f;

/**
 * Magic drawn the way Minecraft draws everything: in square pixels a sixteenth of a block across, the size of a texel
 * on a block or a skin, each one flat color stepped down a ramp from white-hot to shadow. Nothing soft and nothing
 * smaller. It fades by stepping down the ramp and thinning out in a dither, as pixel art does, rather than by blurring.
 * Its patterns change a tick at a time, like an animated block texture, while the magic itself moves smoothly.
 *
 * <p>Drawn through {@link GlowPass#submitPixels}, blended over the world as they are: unlike light, a pixel can be as
 * dark as crimson over snow.
 */
public final class Pixels {

    /** A pixel's size in blocks. */
    public static final float SIZE = 1.0F / 16.0F;

    public static final int HOT = 0;
    public static final int PINK = 1;
    public static final int BRIGHT = 2;
    public static final int SCARLET = 3;
    public static final int CRIMSON = 4;
    public static final int WINE = 5;
    public static final int SHADOW = 6;
    /** The ramp, white-hot down to shadow: every pixel of magic is one of these. */
    private static final int[] RAMP = {ScarletPalette.CORE, ScarletPalette.PINK, ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET,
            ScarletPalette.CRIMSON, ScarletPalette.WINE, ScarletPalette.SHADOW};
    private static final float[] BAYER = {
            0.0F / 16, 8.0F / 16, 2.0F / 16, 10.0F / 16,
            12.0F / 16, 4.0F / 16, 14.0F / 16, 6.0F / 16,
            3.0F / 16, 11.0F / 16, 1.0F / 16, 9.0F / 16,
            15.0F / 16, 7.0F / 16, 13.0F / 16, 5.0F / 16};

    private Pixels() {
    }

    /**
     * A step of the ramp as a color, {@link #HOT} to {@link #SHADOW}; steps past either end stop there.
     */
    public static int ramp(int step) {
        return RAMP[Math.clamp(step, HOT, SHADOW)];
    }

    /**
     * A step of the ramp, opaque.
     */
    public static int opaque(int step) {
        return 0xFF000000 | ramp(step);
    }

    /**
     * A step of the ramp at an alpha, kept to a few levels so it reads as pixel art: 1, 3/4, 1/2 and 1/4.
     */
    public static int ramp(int step, float alpha) {
        float a = Math.round(Math.clamp(alpha, 0.0F, 1.0F) * 4.0F) / 4.0F;
        return Glow.withAlpha(ramp(step), a);
    }

    /**
     * The step of the ramp nearest a color.
     */
    public static int stepOf(int rgb) {
        int best = SCARLET;
        int bestDistance = Integer.MAX_VALUE;
        for (int step = HOT; step <= SHADOW; step++) {
            int dr = ((rgb >> 16) & 0xFF) - ((RAMP[step] >> 16) & 0xFF);
            int dg = ((rgb >> 8) & 0xFF) - ((RAMP[step] >> 8) & 0xFF);
            int db = (rgb & 0xFF) - (RAMP[step] & 0xFF);
            int distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                best = step;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * The step of the ramp for a heat from 1, white-hot, down to 0, shadow.
     */
    public static int step(float heat) {
        return Math.clamp(Math.round((1.0F - heat) * SHADOW), HOT, SHADOW);
    }

    /**
     * The threshold of a 4 by 4 ordered dither at a pixel, 0 to 1: a level shows there if it is above it. Laid over a
     * smooth level, it breaks the edge into the checkered fade of pixel art.
     */
    public static float dither(int i, int j) {
        return BAYER[(j & 3) * 4 + (i & 3)];
    }

    /**
     * Whether a level from 0 to 1 shows at a pixel, through the dither.
     */
    public static boolean shows(float level, int i, int j) {
        return level > dither(i, j) + 0.03125F;
    }

    /**
     * A steady number from 0 to 1 for a pixel, a frame and a purpose.
     */
    public static float hash(int i, int j, int frame, int salt) {
        long h = i * 0x9E3779B97F4A7C15L ^ j * 0xC2B2AE3D27D4EB4FL ^ frame * 0x165667B19E3779F9L ^ salt * 0xD6E8FEB86659FD93L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 40) / (float) (1 << 24);
    }

    /**
     * Smooth value noise from 0 to 1, varying over about a unit: the churn of smoke, read off at pixels.
     */
    public static float noise(float x, float y, int salt) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        float fx = x - x0;
        float fy = y - y0;
        float sx = fx * fx * (3.0F - 2.0F * fx);
        float sy = fy * fy * (3.0F - 2.0F * fy);
        float a = hash(x0, y0, 0, salt);
        float b = hash(x0 + 1, y0, 0, salt);
        float c = hash(x0, y0 + 1, 0, salt);
        float d = hash(x0 + 1, y0 + 1, 0, salt);
        return a + (b - a) * sx + (c - a) * sy + (a - b - c + d) * sx * sy;
    }

    /**
     * Two octaves of {@link #noise}, for churn with some grain in it.
     */
    public static float churn(float x, float y, int salt) {
        return noise(x, y, salt) * 0.65F + noise(x * 2.03F + 17.0F, y * 2.03F - 11.0F, salt + 1) * 0.35F;
    }

    /**
     * A flat quad of one color between four corners, in the pose's space, darkened as the magic being drawn is.
     */
    public static void quad(VertexConsumer buffer, PoseStack.Pose pose, Vector3f a, Vector3f b, Vector3f c, Vector3f d, int argb) {
        int color = Glow.shaded(argb);
        buffer.addVertex(pose, a.x, a.y, a.z).setColor(color);
        buffer.addVertex(pose, b.x, b.y, b.z).setColor(color);
        buffer.addVertex(pose, c.x, c.y, c.z).setColor(color);
        buffer.addVertex(pose, d.x, d.y, d.z).setColor(color);
    }

    /**
     * A square centered at x, y, z with its sides along the unit axes {@code r} and {@code u}, {@code half} from the
     * center to each side.
     */
    public static void square(VertexConsumer buffer, PoseStack.Pose pose, Vector3f r, Vector3f u, float x, float y, float z, float half,
                              int argb) {
        int color = Glow.shaded(argb);
        float rx = r.x * half;
        float ry = r.y * half;
        float rz = r.z * half;
        float ux = u.x * half;
        float uy = u.y * half;
        float uz = u.z * half;
        buffer.addVertex(pose, x - rx - ux, y - ry - uy, z - rz - uz).setColor(color);
        buffer.addVertex(pose, x + rx - ux, y + ry - uy, z + rz - uz).setColor(color);
        buffer.addVertex(pose, x + rx + ux, y + ry + uy, z + rz + uz).setColor(color);
        buffer.addVertex(pose, x - rx + ux, y - ry + uy, z - rz + uz).setColor(color);
    }

    /**
     * Pixel (i, j) of a grid lying in the plane through {@code origin} spanned by the unit axes {@code u} and
     * {@code v}, pixels {@code size} across, pixel (0, 0) just up and to the right of the origin.
     */
    public static void cell(VertexConsumer buffer, PoseStack.Pose pose, Vector3f origin, Vector3f u, Vector3f v, int i, int j, float size,
                            int argb) {
        float x = origin.x + (u.x * (i + 0.5F) + v.x * (j + 0.5F)) * size;
        float y = origin.y + (u.y * (i + 0.5F) + v.y * (j + 0.5F)) * size;
        float z = origin.z + (u.z * (i + 0.5F) + v.z * (j + 0.5F)) * size;
        square(buffer, pose, u, v, x, y, z, size * 0.5F, argb);
    }
}
