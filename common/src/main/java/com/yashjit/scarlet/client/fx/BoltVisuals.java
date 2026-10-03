package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import net.minecraft.util.ARGB;
import org.joml.Vector3f;

/**
 * The look of a chaos blast, drawn around its head in the head's own space:
 *
 * <ul>
 *     <li>a white-hot core inside a scarlet body, a wide halo and a faint crimson haze, all breathing slightly;</li>
 *     <li>tendrils of energy that flail around the head and stream back into the trail;</li>
 *     <li>a thick trail that undulates as it thins, with a hot line through its middle;</li>
 *     <li>arcs that crackle off the core, changing every couple of ticks.</li>
 * </ul>
 */
public final class BoltVisuals {

    private static final int TENDRILS = 5;
    private static final int TENDRIL_POINTS = 9;
    private static final int TRAIL_POINTS = 12;
    private static final int ARCS = 3;
    private static final float TRAIL_LENGTH = 4.2F;

    private BoltVisuals() {
    }

    /**
     * @param age     ticks since launch, fractional
     * @param flown   distance flown so far, which caps the trail so it never reaches back past the hand
     * @param seed    per-blast variation
     */
    public static void blast(VertexConsumer buffer, PoseStack.Pose pose, Vector3f direction, float age, float flown, int seed) {
        Glow.Billboard axes = Glow.billboard(pose);
        Vector3f[] around = Glow.planeAxes(direction);
        float breath = 1.0F + 0.08F * (float) Math.sin(age * 1.9F) + 0.05F * (float) Math.sin(age * 5.3F + seed);

        trail(buffer, pose, axes, new Vector3f(), direction, around, Math.min(TRAIL_LENGTH, flown), age, 1.0F);
        for (int k = 0; k < TENDRILS; k++) {
            tendril(buffer, pose, axes, direction, around, age, seed, k);
        }

        Glow.disc(buffer, pose, axes, 0, 0, 0, 1.15F * breath, Glow.withAlpha(ScarletPalette.CRIMSON, 0.13F));
        Glow.disc(buffer, pose, axes, 0, 0, 0, 0.62F * breath, Glow.withAlpha(ScarletPalette.SCARLET, 0.5F));
        Glow.disc(buffer, pose, axes, 0, 0, 0, 0.33F, Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.9F));
        float flicker = 0.9F + 0.1F * (float) Math.sin(age * 7.1F + seed * 0.37F);
        Glow.disc(buffer, pose, axes, 0, 0, 0, 0.17F * flicker, Glow.withAlpha(ScarletPalette.CORE, 1.0F));
        Glow.disc(buffer, pose, axes, 0, 0, 0, 0.09F, Glow.withAlpha(ScarletPalette.CORE, 1.0F));

        int bucket = (int) Math.floor(age / 2.0F);
        for (int j = 0; j < ARCS; j++) {
            arc(buffer, pose, axes, seed, bucket, j, age);
        }
    }

    /**
     * The dark a corrupted caster's blast carries, for the tint pass: a pall around the head and down the trail. Its
     * light alone could only brighten what is behind it; this is what makes it read as black against a bright sky.
     */
    public static void shadow(VertexConsumer buffer, PoseStack.Pose pose, Vector3f direction, float age, float flown, float darkness) {
        Glow.Billboard axes = Glow.billboard(pose);
        Glow.tintDisc(buffer, pose, axes, 0, 0, 0, 0.85F, GlowPass.tint(ScarletPalette.VOID, 0.75F * darkness));
        float length = Math.min(TRAIL_LENGTH, flown);
        if (length < 0.05F) {
            return;
        }
        Vector3f[] around = Glow.planeAxes(direction);
        Vector3f[] points = new Vector3f[TRAIL_POINTS];
        float[] widths = new float[TRAIL_POINTS];
        int[] tints = new int[TRAIL_POINTS];
        for (int i = 0; i < TRAIL_POINTS; i++) {
            float s = i / (float) (TRAIL_POINTS - 1);
            float swayA = (float) Math.sin(s * 7.0F - age * 0.9F) * 0.09F * s;
            float swayB = (float) Math.cos(s * 5.0F - age * 1.1F) * 0.09F * s;
            points[i] = new Vector3f(direction).mul(-s * length).add(new Vector3f(around[0]).mul(swayA)).add(new Vector3f(around[1]).mul(swayB));
            widths[i] = 0.9F * (float) Math.pow(1.0F - s, 0.8) + 0.05F;
            tints[i] = GlowPass.tint(ScarletPalette.VOID, 0.65F * darkness * (float) Math.pow(1.0F - s, 1.2));
        }
        Glow.tintRibbon(buffer, pose, axes, points, widths, tints);
    }

    /**
     * The trail from {@code head} back along {@code -direction}. {@code alpha} fades it as it draws back after impact.
     */
    public static void trail(VertexConsumer buffer, PoseStack.Pose pose, Glow.Billboard axes, Vector3f head, Vector3f direction,
                             Vector3f[] around, float length, float age, float alpha) {
        if (length < 0.05F) {
            return;
        }
        Vector3f[] points = new Vector3f[TRAIL_POINTS];
        float[] widths = new float[TRAIL_POINTS];
        int[] colors = new int[TRAIL_POINTS];
        Vector3f[] inner = new Vector3f[TRAIL_POINTS];
        float[] innerWidths = new float[TRAIL_POINTS];
        int[] innerColors = new int[TRAIL_POINTS];
        for (int i = 0; i < TRAIL_POINTS; i++) {
            float s = i / (float) (TRAIL_POINTS - 1);
            // the trail sways more the further it is from the head
            float swayA = (float) Math.sin(s * 7.0F - age * 0.9F) * 0.09F * s;
            float swayB = (float) Math.cos(s * 5.0F - age * 1.1F) * 0.09F * s;
            Vector3f point = new Vector3f(direction).mul(-s * length).add(head)
                    .add(new Vector3f(around[0]).mul(swayA)).add(new Vector3f(around[1]).mul(swayB));
            points[i] = point;
            widths[i] = 0.62F * (float) Math.pow(1.0F - s, 0.8) + 0.04F;
            colors[i] = Glow.withAlpha(ARGB.srgbLerp(s, ScarletPalette.BRIGHT_SCARLET, ScarletPalette.CRIMSON),
                    alpha * 0.85F * (float) Math.pow(1.0F - s, 1.4));
            inner[i] = new Vector3f(direction).mul(-s * length * 0.6F).add(head);
            innerWidths[i] = 0.22F * (float) Math.pow(1.0F - s, 1.2) + 0.01F;
            innerColors[i] = Glow.withAlpha(ARGB.srgbLerp(s, ScarletPalette.CORE, ScarletPalette.BRIGHT_SCARLET), alpha * 0.9F * (1.0F - s) * (1.0F - s));
        }
        Glow.ribbon(buffer, pose, axes, points, widths, colors);
        Glow.ribbon(buffer, pose, axes, inner, innerWidths, innerColors);
    }

    private static void tendril(VertexConsumer buffer, PoseStack.Pose pose, Glow.Billboard axes, Vector3f direction, Vector3f[] around,
                                float age, int seed, int k) {
        Vector3f[] points = new Vector3f[TENDRIL_POINTS];
        float[] widths = new float[TENDRIL_POINTS];
        int[] colors = new int[TENDRIL_POINTS];
        float phase = k * (float) (Math.PI * 2 / TENDRILS) + seed * 0.61F;
        float reach = 0.9F + 0.5F * (float) Math.sin(age * 0.7F + k + seed);
        for (int i = 0; i < TENDRIL_POINTS; i++) {
            float s = i / (float) (TENDRIL_POINTS - 1);
            float theta = phase + s * 2.2F + age * (1.3F + 0.2F * k);
            float radius = 0.16F + 0.42F * s * (0.7F + 0.3F * (float) Math.sin(age * 1.7F + k * 1.3F + s * 3.0F));
            points[i] = new Vector3f(direction).mul(-s * reach)
                    .add(new Vector3f(around[0]).mul((float) Math.cos(theta) * radius))
                    .add(new Vector3f(around[1]).mul((float) Math.sin(theta) * radius));
            widths[i] = 0.13F * (1.0F - s) + 0.01F;
            colors[i] = Glow.withAlpha(ARGB.srgbLerp(s, ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET), 0.75F * (1.0F - s));
        }
        Glow.ribbon(buffer, pose, axes, points, widths, colors);
    }

    /**
     * A jagged arc of energy jumping off the core, the same on every client because it is seeded by the blast and
     * the tick.
     */
    private static void arc(VertexConsumer buffer, PoseStack.Pose pose, Glow.Billboard axes, int seed, int bucket, int j, float age) {
        long hash = mix(seed * 31L + bucket * 7919L + j * 104729L);
        if ((hash & 7) < 2) {
            return;
        }
        Vector3f out = new Vector3f(unit(hash), unit(hash >>> 11), unit(hash >>> 22)).normalize();
        float length = 0.35F + 0.25F * ((hash >>> 33) & 255) / 255.0F;
        int segments = 4;
        Vector3f[] points = new Vector3f[segments + 1];
        float[] widths = new float[segments + 1];
        int[] colors = new int[segments + 1];
        float flicker = 0.6F + 0.4F * (float) Math.abs(Math.sin(age * 9.0F + j));
        for (int i = 0; i <= segments; i++) {
            float s = i / (float) segments;
            long h = mix(hash + i * 977L);
            float jitter = i == 0 || i == segments ? 0.0F : 0.09F;
            points[i] = new Vector3f(out).mul(0.12F + s * length)
                    .add(unit(h) * jitter, unit(h >>> 13) * jitter, unit(h >>> 26) * jitter);
            widths[i] = 0.045F * (1.0F - s * 0.6F);
            colors[i] = Glow.withAlpha(s < 0.5F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET, flicker * (1.0F - s * 0.7F));
        }
        Glow.ribbon(buffer, pose, axes, points, widths, colors);
    }

    private static float unit(long bits) {
        return ((bits & 0x3FF) / 511.5F) - 1.0F;
    }

    private static long mix(long x) {
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }
}
