package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.hex.Era;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Glitches of what a Hex makes, kept blocky like the rest of Minecraft.
 *
 * <ul>
 *     <li>Being rewritten, something glitches red: hard bars of red light tear across it and red pixels flicker over
 *     it, while it flicks between its old self and its new.</li>
 *     <li>While its Hex is unsteady, what it made slips now and then: for a few frames it jumps into another era, or
 *     out of existence altogether, then settles back. These need no drawing of their own; whatever draws the thing
 *     asks {@link #slip}.</li>
 * </ul>
 */
public final class Glitch {

    /** Frames of the glitch per tick. */
    private static final float FRAME_RATE = 0.75F;
    /** Frames a slip lasts. */
    private static final int SLIP_FRAMES = 3;

    private Glitch() {
    }

    /**
     * Which frame of the glitch it is, at a moment, as a whole number to seed it.
     */
    public static long frame(double now) {
        return (long) Math.floor(now * FRAME_RATE);
    }

    /**
     * Whether, at this frame, something mid-change shows its new self rather than its old: more and more often as
     * {@code progress} runs from 0 to 1, with the odd flash back.
     */
    public static boolean showsNew(long frame, int seed, float progress) {
        return hash(frame, seed, 3) < Math.clamp(progress * 1.25F - 0.1F, 0.0F, 1.0F);
    }

    public enum Slip {
        NONE,
        /** Jumped into another era. */
        ERA,
        /** Flickered out of existence, showing what it really is. */
        GONE
    }

    /**
     * How something an unsteady Hex made is slipping at a moment: not at all most of the time, and at chance moments
     * into another era or out of existence for a few frames, more often the worse the {@code unrest}.
     */
    public static Slip slip(double now, int seed, float unrest) {
        if (unrest <= 0.01F) {
            return Slip.NONE;
        }
        long window = frame(now) / SLIP_FRAMES;
        if (hash(window, seed, 5) >= unrest * 0.45F) {
            return Slip.NONE;
        }
        return hash(window, seed, 6) < 0.3F ? Slip.GONE : Slip.ERA;
    }

    /**
     * The era something slips into, never its own: a different one each time.
     */
    public static Era slipEra(double now, int seed, Era actual) {
        int count = Era.values().length;
        int shift = 1 + Math.min(count - 2, (int) (hash(frame(now) / SLIP_FRAMES, seed, 7) * (count - 1)));
        return Era.byIndex((actual.ordinal() + shift) % count);
    }

    /**
     * The red glitch over something being rewritten, in the glow pass: centered at c, w across and h tall. Bars of it
     * tear sideways and red pixels flicker over it, sized to it so a house glitches in big blocks and a person in small
     * ones.
     */
    public static void draw(VertexConsumer buffer, PoseStack.Pose pose, Glow.Billboard axes, Vector3f c, float w, float h, float intensity,
                            long frame, int seed) {
        if (intensity <= 0.01F) {
            return;
        }
        Vector3f r = axes.right();
        Vector3f u = axes.up();
        // one pixel of the glitch: an eighth of a block on a person, more on a house
        float pixel = Math.max(0.125F, h / 20.0F);
        int bars = 1 + Math.round(4 * intensity * hash(frame, seed, 1));
        for (int i = 0; i < bars; i++) {
            float y = snap((hash(frame, seed, 20 + i) - 0.5F) * h, pixel);
            float shift = snap((hash(frame, seed, 40 + i) - 0.5F) * w * 0.6F, pixel);
            float length = snap(w * (0.4F + 0.8F * hash(frame, seed, 60 + i)), pixel);
            float thickness = pixel * (1 + (int) (hash(frame, seed, 80 + i) * 3.0F));
            int color = hash(frame, seed, 100 + i) > 0.8F ? ScarletPalette.CORE : hash(frame, seed, 120 + i) > 0.5F ? ScarletPalette.BRIGHT_SCARLET
                    : ScarletPalette.SCARLET;
            quad(buffer, pose, r, u, c.x + r.x * shift, c.y + y, c.z + r.z * shift, length, thickness, Glow.withAlpha(color, 0.8F * intensity));
        }
        int pixels = Math.round(14 * intensity);
        for (int i = 0; i < pixels; i++) {
            float x = snap((hash(frame, seed, 200 + i) - 0.5F) * w, pixel);
            float y = snap((hash(frame, seed, 300 + i) - 0.5F) * h, pixel);
            int color = hash(frame, seed, 400 + i) > 0.7F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET;
            quad(buffer, pose, r, u, c.x + r.x * x, c.y + y, c.z + r.z * x, pixel, pixel, Glow.withAlpha(color, 0.9F * intensity));
        }
    }

    /**
     * Red pixels of static crawling over a box, for the tick.
     */
    public static void crawl(Vec3 min, Vec3 max, float intensity) {
        crawl(min, max, intensity, at -> true);
    }

    /**
     * The same, only where {@code keep} allows: inside a Hex's wall, say.
     */
    public static void crawl(Vec3 min, Vec3 max, float intensity, java.util.function.Predicate<Vec3> keep) {
        RandomSource random = ScarletFx.random();
        for (int i = 0, n = Math.round(6 * intensity * ScarletFx.density()); i < n; i++) {
            Vec3 at = new Vec3(Mth.lerp(random.nextDouble(), min.x, max.x), Mth.lerp(random.nextDouble(), min.y, max.y),
                    Mth.lerp(random.nextDouble(), min.z, max.z));
            if (!keep.test(at)) {
                continue;
            }
            int color = random.nextFloat() < 0.25F ? ScarletPalette.CORE : random.nextBoolean() ? ScarletPalette.BRIGHT_SCARLET : ScarletPalette.SCARLET;
            ScarletFx.spark(at, new Vec3((random.nextFloat() - 0.5F) * 0.06, 0.0, (random.nextFloat() - 0.5F) * 0.06), 2 + random.nextInt(3),
                    0.04F + random.nextFloat() * 0.03F, color, ScarletPalette.SCARLET, 0.0F, 0.6F);
        }
    }

    /**
     * A puff of white and grey pixels where something slips into another era or back: the static of a changed channel.
     */
    public static void puff(Vec3 min, Vec3 max) {
        RandomSource random = ScarletFx.random();
        for (int i = 0, n = Math.round(8 * ScarletFx.density()); i < n; i++) {
            Vec3 at = new Vec3(Mth.lerp(random.nextDouble(), min.x, max.x), Mth.lerp(random.nextDouble(), min.y, max.y),
                    Mth.lerp(random.nextDouble(), min.z, max.z));
            int grey = 0xB0B0B0 + random.nextInt(0x40) * 0x010101;
            ScarletFx.spark(at, new Vec3(0.0, 0.004, 0.0), 3 + random.nextInt(4), 0.05F, 0xFFFFFF, grey, 0.0F, 0.7F);
        }
    }

    private static float snap(float value, float step) {
        return Math.round(value / step) * step;
    }

    /**
     * A flat, hard-edged rectangle facing the camera.
     */
    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, Vector3f r, Vector3f u, float x, float y, float z, float width, float height,
                             int argb) {
        float hx = r.x * width * 0.5F;
        float hy = r.y * width * 0.5F;
        float hz = r.z * width * 0.5F;
        float vx = u.x * height * 0.5F;
        float vy = u.y * height * 0.5F;
        float vz = u.z * height * 0.5F;
        buffer.addVertex(pose, x - hx - vx, y - hy - vy, z - hz - vz).setColor(argb);
        buffer.addVertex(pose, x + hx - vx, y + hy - vy, z + hz - vz).setColor(argb);
        buffer.addVertex(pose, x + hx + vx, y + hy + vy, z + hz + vz).setColor(argb);
        buffer.addVertex(pose, x - hx + vx, y - hy + vy, z - hz + vz).setColor(argb);
    }

    /**
     * A steady number from 0 to 1 for this frame, thing and purpose.
     */
    public static float hash(long frame, int seed, int salt) {
        long h = frame * 0x9E3779B97F4A7C15L ^ seed * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        return (h >>> 40) / (float) (1L << 24);
    }
}
