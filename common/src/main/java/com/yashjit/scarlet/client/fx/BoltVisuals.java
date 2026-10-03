package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.PixelRibbon;
import com.yashjit.scarlet.client.render.PixelSprite;
import com.yashjit.scarlet.client.render.Pixels;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The look of a chaos blast, the Scarlet Witch's signature, drawn as pixel art around its head in the head's own space:
 *
 * <ul>
 *     <li>a ball of churning energy, white-hot at its heart with two arms of light spinning through it, its rim licking
 *     outward like fire and swept back by its speed into a comet's head, outlined dark against the sky;</li>
 *     <li>tendrils of it whipping around the head and streaming back;</li>
 *     <li>a stream of red smoke pouring back off it, hot where it leaves the head and burning down the ramp to wine as it
 *     thins and breaks up;</li>
 *     <li>three wisps twisting around the stream like rope, cooling and breaking into dashes as they trail off;</li>
 *     <li>stars of light winking about the head.</li>
 * </ul>
 *
 * <p>Right in front of the eyes, leaving your own hand, the ball is smaller and the rest thins away, rather than fill
 * the view.
 */
public final class BoltVisuals {

    public static final float TRAIL_LENGTH = 4.2F;
    private static final float WISP_LENGTH = 2.8F;
    private static final int WISPS = 3;
    private static final int WHIPS = 4;
    /** The ball's radius, and how far its flames lick past it, in pixels. */
    private static final float BALL = 5.0F;
    private static final float FLAMES = 4.0F;
    /** Nearer the eyes than this it is not drawn at all, and it comes in fully over the next stretch. */
    private static final float NEAR = 0.9F;
    private static final float NEAR_FADE = 1.6F;
    private static final float TAU = (float) (Math.PI * 2);

    private BoltVisuals() {
    }

    /**
     * @param age   ticks since launch, fractional
     * @param flown how far it has come from the hand that threw it, which caps the trail so it never reaches back past
     *              that hand
     * @param seed  per-blast variation
     */
    public static void blast(VertexConsumer buffer, PoseStack.Pose pose, Vector3f direction, float age, float flown, int seed) {
        Vector3f eye = eye(pose);
        float near = near(eye.length());
        trail(buffer, pose, new Vector3f(), direction, Math.min(TRAIL_LENGTH, flown), age, 1.0F, seed);
        if (near <= 0.0F) {
            return;
        }
        PixelSprite sprite = new PixelSprite(pose, 0.0F, 0.0F, 0.0F);
        Vector3f[] around = Glow.planeAxes(direction);
        wisps(sprite, eye, direction, around, Math.min(WISP_LENGTH, flown), age, seed);
        whips(sprite, eye, direction, around, Math.min(1.4F, Math.max(0.25F, flown)), age, seed);
        head(sprite, direction, age, seed, near);
        sprite.draw(buffer);
    }

    /**
     * Whether a stroke at {@code at} is drawn, thinning away near the eyes: from right in front of them a stroke would be
     * thrown across the whole view.
     */
    private static boolean seen(Vector3f eye, Vector3f at, int i, int j) {
        float near = near(at.distance(eye));
        return near >= 1.0F || near > 0.0F && Pixels.shows(near, i, j);
    }

    private static Vector3f eye(PoseStack.Pose pose) {
        return new Matrix4f(pose.pose()).invert().transformPosition(0.0F, 0.0F, 0.0F, new Vector3f());
    }

    /**
     * How far it has come in, 0 to 1, at this distance from the eyes.
     */
    private static float near(float distance) {
        return Math.clamp((distance - NEAR) / NEAR_FADE, 0.0F, 1.0F);
    }

    /**
     * The stream of smoke from {@code head} back along {@code -direction}, {@code length} long. {@code fade} thins it
     * away as it draws back into where the blast struck.
     */
    public static void trail(VertexConsumer buffer, PoseStack.Pose pose, Vector3f head, Vector3f direction, float length, float age, float fade,
                             int seed) {
        if (length < 0.1F || fade <= 0.0F) {
            return;
        }
        Vector3f eye = eye(pose);
        Vector3f[] around = Glow.planeAxes(direction);
        PixelRibbon ribbon = new PixelRibbon(pose);
        int rows = Math.max(2, Math.round(length / Pixels.SIZE));
        for (int k = 0; k <= rows; k++) {
            float s = k / (float) rows;
            // it sways more the further it is from the head
            float swayA = Mth.sin(s * 7.0F - age * 0.9F) * 0.09F * s;
            float swayB = Mth.cos(s * 5.0F - age * 1.1F) * 0.09F * s;
            ribbon.add(new Vector3f(direction).mul(-s * length).add(head)
                    .add(around[0].x * swayA + around[1].x * swayB, around[0].y * swayA + around[1].y * swayB, around[0].z * swayA + around[1].z * swayB));
        }
        int frame = (int) Math.floor(age);
        for (int k = 0; k < rows; k++) {
            float s = (k + 0.5F) / rows;
            float near = near(ribbon.point(k).distance(eye));
            if (near <= 0.0F) {
                continue;
            }
            float behind = s * length;
            float half = (0.4F * (float) Math.pow(1.0F - s, 0.8) + 0.03F) / Pixels.SIZE;
            int reach = (int) Math.ceil(half);
            for (int b = -reach; b < reach; b++) {
                float across = Math.abs(b + 0.5F) / half;
                if (across >= 1.0F) {
                    continue;
                }
                // smoke churning back along it
                float churn = Pixels.churn(behind * 3.0F - frame * 0.55F, (b + 0.5F) * 0.35F, seed);
                float heat = (1.0F - across * across) * (0.45F + 0.75F * churn) * (1.0F - 0.55F * s) * fade * near;
                // and a hot line down its middle where it leaves the head
                if (across < 0.25F && s < 0.4F) {
                    heat += 0.4F * (1.0F - s / 0.4F) * near;
                }
                // breaking up as it thins
                if (!Pixels.shows(heat * 2.2F - s * 0.4F, k, b + frame)) {
                    continue;
                }
                ribbon.cell(buffer, k, b, heat > 0.95F ? Pixels.opaque(Pixels.PINK) : heat > 0.75F ? Pixels.opaque(Pixels.BRIGHT)
                        : heat > 0.55F ? Pixels.opaque(Pixels.SCARLET) : heat > 0.38F ? Pixels.ramp(Pixels.CRIMSON, 0.75F) : Pixels.ramp(Pixels.WINE, 0.5F));
            }
        }
    }

    /**
     * Three wisps twisting around the stream like rope, from just behind the head.
     */
    private static void wisps(PixelSprite sprite, Vector3f eye, Vector3f direction, Vector3f[] around, float length, float age, int seed) {
        if (length < 0.2F) {
            return;
        }
        int points = 26;
        int dash = (int) Math.floor(age * 0.5F);
        for (int w = 0; w < WISPS; w++) {
            float phase = w * TAU / WISPS + seed * 0.61F + age * 0.55F;
            Vector3f previous = null;
            for (int k = 0; k <= points; k++) {
                float s = k / (float) points;
                float behind = 0.15F + s * length;
                float radius = 0.24F + 0.26F * s + 0.04F * Mth.sin(age * 0.8F + w * 2.0F + s * 6.0F);
                Vector3f at = around(direction, around, behind, phase + s * 5.0F, radius);
                if (previous != null && Pixels.hash(k, w, dash, seed) > s * 0.45F && seen(eye, at, k, w)) {
                    int step = s < 0.25F ? Pixels.BRIGHT : s < 0.55F ? Pixels.SCARLET : s < 0.8F ? Pixels.CRIMSON : Pixels.WINE;
                    sprite.line(previous, at, Pixels.ramp(step, s < 0.8F ? 1.0F : 0.75F), 1, s < 0.35F ? 2 : 1);
                }
                previous = at;
            }
        }
    }

    /**
     * Tendrils of energy whipping around the head, each rooted in the ball and streaming back, flailing as it goes.
     */
    private static void whips(PixelSprite sprite, Vector3f eye, Vector3f direction, Vector3f[] around, float length, float age, int seed) {
        int points = 14;
        for (int k = 0; k < WHIPS; k++) {
            float phase = k * TAU / WHIPS + seed * 0.37F;
            float reach = length * (0.7F + 0.3F * Mth.sin(age * 0.7F + k + seed));
            Vector3f previous = null;
            for (int i = 0; i <= points; i++) {
                float s = i / (float) points;
                float theta = phase + s * 2.2F + age * (1.3F + 0.2F * k);
                float radius = 0.18F + 0.38F * s * (0.7F + 0.3F * Mth.sin(age * 1.7F + k * 1.3F + s * 3.0F));
                Vector3f at = around(direction, around, s * reach, theta, radius);
                if (previous != null && seen(eye, at, i, k)) {
                    int step = s < 0.25F ? Pixels.PINK : s < 0.55F ? Pixels.BRIGHT : s < 0.8F ? Pixels.SCARLET : Pixels.CRIMSON;
                    sprite.line(previous, at, Pixels.opaque(step), 1, s < 0.45F ? 2 : 1);
                }
                previous = at;
            }
        }
    }

    /**
     * A point {@code behind} back along the flight, {@code radius} out from it at angle {@code theta} around it.
     */
    private static Vector3f around(Vector3f direction, Vector3f[] around, float behind, float theta, float radius) {
        float c = Mth.cos(theta) * radius;
        float d = Mth.sin(theta) * radius;
        return new Vector3f(direction).mul(-behind)
                .add(around[0].x * c + around[1].x * d, around[0].y * c + around[1].y * d, around[0].z * c + around[1].z * d);
    }

    /**
     * The ball of energy itself, on the grid pinned at its middle, {@code near} shrinking it right in front of the eyes.
     */
    private static void head(PixelSprite sprite, Vector3f direction, float age, int seed, float near) {
        int frame = (int) Math.floor(age);
        Vector3f right = sprite.right();
        Vector3f up = sprite.up();
        // which way it streams back, as seen, and how side on it is seen: head on, it is round
        float bx = -direction.dot(right) / right.lengthSquared();
        float by = -direction.dot(up) / up.lengthSquared();
        float sweep = (float) Math.sqrt(bx * bx + by * by);
        if (sweep > 1.0E-4F) {
            bx /= sweep;
            by /= sweep;
        } else {
            bx = 0.0F;
            by = -1.0F;
        }
        sweep = Math.min(1.0F, sweep);
        float breath = 1.0F + 0.08F * Mth.sin(age * 1.9F) + 0.05F * Mth.sin(age * 5.3F + seed);
        // leaving your own hand right in front of the eyes, it swells as it goes rather than fill the view
        float swell = 0.4F + 0.6F * near;
        float ball = BALL * breath * swell;
        float core = (Pixels.hash(frame, 0, seed, 1) < 0.5F ? 2.0F : 2.6F) * swell;
        float spin = age * 0.9F + seed;
        int reach = (int) Math.ceil(ball + FLAMES * swell + 3.0F + sweep * 8.0F);
        for (int i = -reach; i < reach; i++) {
            for (int j = -reach; j < reach; j++) {
                float x = i + 0.5F;
                float y = j + 0.5F;
                // swept back by its speed, stretched out behind it
                float back = x * bx + y * by;
                float side = y * bx - x * by;
                float stretched = back > 0.0F ? back / (1.0F + 1.6F * sweep) : back;
                float r = (float) Math.sqrt(stretched * stretched + side * side);
                float theta = (float) Math.atan2(y, x);
                float cos = Mth.cos(theta);
                float sin = Mth.sin(theta);
                float licking = Math.max(0.0F, Pixels.noise(cos * 3.0F + frame * 0.4F, sin * 3.0F - frame * 0.33F, seed) - 0.45F) / 0.55F;
                float behindness = Math.max(0.0F, back / Math.max(1.0E-3F, (float) Math.sqrt(x * x + y * y)));
                float flames = (0.8F + FLAMES * licking) * swell * (1.0F + 0.8F * sweep * behindness);
                int color;
                if (r < core) {
                    color = Pixels.opaque(Pixels.HOT);
                } else if (r < core + 1.2F) {
                    color = Pixels.opaque(Pixels.PINK);
                } else if (r < ball - 1.0F) {
                    // two arms of light spinning through it
                    float arm = 0.5F + 0.5F * Mth.cos(theta * 2.0F - spin * 2.0F + (float) Math.log(r / ball + 0.15F) * 3.0F);
                    color = Pixels.opaque(arm > 0.72F ? Pixels.PINK : arm > 0.3F ? Pixels.BRIGHT : Pixels.SCARLET);
                } else if (r < ball) {
                    color = Pixels.opaque(Pixels.SCARLET);
                } else if (r < ball + flames) {
                    float along = (r - ball) / flames;
                    color = along < 0.4F ? Pixels.opaque(Pixels.SCARLET) : along < 0.75F ? Pixels.opaque(Pixels.CRIMSON) : Pixels.ramp(Pixels.WINE, 0.75F);
                } else if (r < ball + flames + 1.0F) {
                    color = Pixels.ramp(Pixels.WINE, 0.5F);
                } else {
                    continue;
                }
                sprite.cell(i, j, color, 2);
            }
        }
        // stars of light winking about it, a tick each
        for (int g = 0; g < 2; g++) {
            if (Pixels.hash(frame, g, seed, 7) > 0.6F || near < 1.0F) {
                continue;
            }
            float angle = Pixels.hash(frame, g, seed, 8) * TAU;
            float distance = ball + FLAMES + 2.0F + Pixels.hash(frame, g, seed, 9) * 3.0F;
            int gi = Math.round(Mth.cos(angle) * distance);
            int gj = Math.round(Mth.sin(angle) * distance);
            sprite.cell(gi, gj, Pixels.opaque(Pixels.HOT), 3);
            sprite.cell(gi + 1, gj, Pixels.opaque(Pixels.PINK), 3);
            sprite.cell(gi - 1, gj, Pixels.opaque(Pixels.PINK), 3);
            sprite.cell(gi, gj + 1, Pixels.opaque(Pixels.PINK), 3);
            sprite.cell(gi, gj - 1, Pixels.opaque(Pixels.PINK), 3);
        }
    }
}
