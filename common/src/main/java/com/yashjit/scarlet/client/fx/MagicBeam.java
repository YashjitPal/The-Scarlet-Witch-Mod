package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * A beam of chaos magic flung from a caster's hand at what it works on. A white-hot core crackles inside it, jumping to
 * a new shape every moment like a live wire, with a fainter second arc flickering around it; a haze of scarlet holds
 * it, wisps twist around it and surges of light run out along it, from a ball of light in the palm to a burst where it
 * lands. It runs dead straight from the hand, so it never droops or sags like anything poured.
 */
public final class MagicBeam {

    /** Ticks each shape of the crackle holds before it jumps to the next. */
    private static final double CRACKLE_TICKS = 1.5;
    private static final float TAU = (float) (Math.PI * 2);

    private MagicBeam() {
    }

    /**
     * Scarlet glass under the beam, for the tint pass, so it reads red over bright sand or snow as much as at night.
     *
     * @param from     the palm, relative to the camera
     * @param to       where it lands, relative to the camera
     * @param presence how far it has come on: 0 to 1
     */
    public static void tint(VertexConsumer buffer, PoseStack.Pose pose, Glow.Billboard axes, Vector3f from, Vector3f to, float presence) {
        if (presence < 0.01F || from.distanceSquared(to) < 0.0025F) {
            return;
        }
        Vector3f[] points = {from, new Vector3f(from).lerp(to, 0.5F), to};
        float[] widths = {0.16F * presence, 0.24F * presence, 0.3F * presence};
        int[] tints = {GlowPass.tint(ScarletPalette.GLASS, 0.5F * presence), GlowPass.tint(ScarletPalette.GLASS, 0.62F * presence),
                GlowPass.tint(ScarletPalette.GLASS, 0.62F * presence)};
        Glow.tintRibbon(buffer, pose, axes, points, widths, tints);
    }

    /**
     * The beam itself, for the glow pass.
     *
     * @param from     the palm, relative to the camera
     * @param to       where it lands, relative to the camera
     * @param seed     which beam it is, so no two crackle alike
     * @param presence how far it has come on: 0 to 1
     */
    public static void draw(VertexConsumer buffer, PoseStack.Pose pose, Glow.Billboard axes, Vector3f from, Vector3f to, double now, float seed,
                            float presence) {
        Vector3f along = new Vector3f(to).sub(from);
        float length = along.length();
        if (length < 0.05F || presence < 0.01F) {
            return;
        }
        Vector3f direction = new Vector3f(along).div(length);
        Vector3f side = new Vector3f(direction).cross(0, 1, 0);
        if (side.lengthSquared() < 1.0E-6F) {
            side.set(1, 0, 0);
        }
        side.normalize();
        Vector3f up = new Vector3f(side).cross(direction).normalize();
        float time = (float) now;
        long frame = (long) Math.floor(now / CRACKLE_TICKS);
        int n = Math.clamp(Math.round(length * 2.0F), 8, 64);
        float flicker = 0.85F + 0.15F * hash(seed, frame, 7, 0, 0);

        // the haze holding it, straight and steady, wider where it lands
        Glow.ribbon(buffer, pose, axes, new Vector3f[] {from, new Vector3f(from).lerp(to, 0.5F), to},
                new float[] {0.22F * presence, 0.3F * presence, 0.38F * presence},
                new int[] {Glow.withAlpha(ScarletPalette.SCARLET, 0.4F * presence * flicker), Glow.withAlpha(ScarletPalette.SCARLET, 0.32F * presence * flicker),
                        Glow.withAlpha(ScarletPalette.SCARLET, 0.38F * presence * flicker)});

        // the crackle: a jagged core pinned at the palm and where it lands, and now and then a second arc around it
        Vector3f[] points = new Vector3f[n + 1];
        float[] widths = new float[n + 1];
        int[] colors = new int[n + 1];
        float amplitude = Math.min(0.12F, 0.03F + length * 0.008F);
        for (int strand = 0; strand < 2; strand++) {
            if (strand == 1 && hash(seed, frame, 9, 0, 0) < -0.3F) {
                continue;
            }
            float reach = amplitude * (strand == 0 ? 1.0F : 2.2F);
            for (int i = 0; i <= n; i++) {
                float t = i / (float) n;
                float pinned = Mth.sin((float) Math.PI * t);
                float a = hash(seed, frame, strand, i, 0) * reach * pinned;
                float b = hash(seed, frame, strand, i, 1) * reach * pinned;
                points[i] = new Vector3f(from).lerp(to, t).add(side.x * a + up.x * b, side.y * a + up.y * b, side.z * a + up.z * b);
                // surges of light running out of the hand along it
                float surge = (float) Math.pow(0.5 + 0.5 * Mth.sin(t * length * 1.3F - time * 1.7F + seed), 6);
                if (strand == 0) {
                    widths[i] = (0.07F + 0.05F * surge) * presence;
                    colors[i] = Glow.withAlpha(Glow.mix(ScarletPalette.BRIGHT_SCARLET, ScarletPalette.CORE, 0.25F + 0.6F * surge), 0.9F * presence);
                } else {
                    widths[i] = 0.035F * presence;
                    colors[i] = Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.5F * presence * flicker);
                }
            }
            Glow.ribbon(buffer, pose, axes, points, widths, colors);
            if (strand == 0) {
                // and white-hot down its middle
                for (int i = 0; i <= n; i++) {
                    widths[i] = 0.026F * presence;
                    colors[i] = Glow.withAlpha(ScarletPalette.CORE, 0.95F * presence);
                }
                Glow.ribbon(buffer, pose, axes, points, widths, colors);
            }
        }

        // wisps twisting around it, one each way
        int m = Math.clamp(Math.round(length * 3.0F), 12, 96);
        Vector3f[] wisp = new Vector3f[m + 1];
        float[] wispWidths = new float[m + 1];
        int[] wispColors = new int[m + 1];
        for (int w = 0; w < 2; w++) {
            float turning = w == 0 ? 1.0F : -1.0F;
            for (int i = 0; i <= m; i++) {
                float t = i / (float) m;
                float taper = Math.min(1.0F, t * 8.0F) * Math.min(1.0F, (1.0F - t) * 5.0F);
                float radius = (0.09F + 0.05F * Mth.sin(t * 9.0F + time * 0.3F + w * 2.0F)) * presence;
                float angle = t * length * 2.6F * turning + time * 0.55F * turning + w * (float) Math.PI + seed;
                float c = Mth.cos(angle) * radius;
                float d = Mth.sin(angle) * radius;
                wisp[i] = new Vector3f(from).lerp(to, t).add(side.x * c + up.x * d, side.y * c + up.y * d, side.z * c + up.z * d);
                wispWidths[i] = 0.024F * taper * presence;
                wispColors[i] = Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.55F * taper * presence);
            }
            Glow.ribbon(buffer, pose, axes, wisp, wispWidths, wispColors);
        }

        // light gathered in the palm, and bursting where it lands
        float pulse = 0.85F + 0.15F * Mth.sin(time * 0.9F + seed * TAU);
        Glow.disc(buffer, pose, axes, from.x, from.y, from.z, 0.28F * presence * pulse, Glow.withAlpha(ScarletPalette.SCARLET, 0.3F * presence));
        Glow.spark(buffer, pose, axes, from.x, from.y, from.z, 0.1F * presence * pulse, ScarletPalette.CORE, ScarletPalette.BRIGHT_SCARLET, 0.9F * presence);
        Glow.disc(buffer, pose, axes, to.x, to.y, to.z, 0.5F * presence * flicker, Glow.withAlpha(ScarletPalette.SCARLET, 0.22F * presence));
        Glow.spark(buffer, pose, axes, to.x, to.y, to.z, 0.2F * presence * flicker, ScarletPalette.CORE, ScarletPalette.SCARLET, 0.85F * presence);
    }

    /**
     * A steady random number from -1 to 1 for one point of one shape of the crackle.
     */
    private static float hash(float seed, long frame, int strand, int i, int axis) {
        long x = Float.floatToIntBits(seed) * 0x9E3779B97F4A7C15L ^ frame * 0xC2B2AE3D27D4EB4FL ^ (strand * 131L + i * 7L + axis) * 0x165667B19E3779F9L;
        x = (x ^ x >>> 30) * 0xBF58476D1CE4E5B9L;
        x = (x ^ x >>> 27) * 0x94D049BB133111EBL;
        x ^= x >>> 31;
        return (x >>> 40) / (float) (1 << 24) * 2.0F - 1.0F;
    }
}
