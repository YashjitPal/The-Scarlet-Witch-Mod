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
 * A beam of chaos magic flung from a caster's hand at what it works on, drawn as pixel art:
 *
 * <ul>
 *     <li>a stream of red energy pouring dead straight out of the palm, so it never droops or sags like anything
 *     poured, churning as it flows out along its length, white-hot down its middle where surges of light run out of the
 *     hand, its edges breaking up into smoke and spreading wider where it lands;</li>
 *     <li>tendrils of it twisting about it like rope, cooling and breaking into dashes along the way;</li>
 *     <li>a churning ball of it in the palm, and a splash of it flickering where it lands.</li>
 * </ul>
 *
 * <p>Right in front of the eyes, leaving your own hand, it thins away rather than fill the view.
 */
public final class MagicBeam {

    private static final int TENDRILS = 3;
    /**
     * Nearer the eyes than this none of it is drawn, and it comes in fully over the next stretch: leaving your own hand
     * it would be a wall of color across the view, where the magic in your palm is drawn instead.
     */
    private static final float NEAR = 1.2F;
    private static final float NEAR_FADE = 1.8F;
    private static final float TAU = (float) (Math.PI * 2);

    private MagicBeam() {
    }

    /**
     * @param from     the palm, relative to the camera
     * @param to       where it lands, relative to the camera
     * @param seed     which beam it is, so no two churn alike
     * @param presence how far it has come on: 0 to 1
     */
    public static void draw(VertexConsumer buffer, PoseStack.Pose pose, Vector3f from, Vector3f to, double now, float seed, float presence) {
        Vector3f along = new Vector3f(to).sub(from);
        float length = along.length();
        if (length < 0.05F || presence < 0.01F) {
            return;
        }
        Vector3f direction = new Vector3f(along).div(length);
        Vector3f eye = new Matrix4f(pose.pose()).invert().transformPosition(0.0F, 0.0F, 0.0F, new Vector3f());
        Vector3f[] around = Glow.planeAxes(direction);
        int salt = Math.round(seed * 1000.0F);
        float time = (float) now;
        stream(buffer, pose, eye, from, direction, length, time, salt, presence);
        for (int w = 0; w < TENDRILS; w++) {
            tendril(buffer, pose, eye, from, direction, around, length, time, salt, w, presence);
        }
        if (near(eye.distance(from)) >= 1.0F) {
            PixelSprite palm = new PixelSprite(pose, from.x, from.y, from.z);
            orb(palm, 2.6F * presence, time, salt);
            palm.draw(buffer);
        }
        PixelSprite landing = new PixelSprite(pose, to.x, to.y, to.z);
        splash(landing, presence, time, salt);
        landing.draw(buffer);
    }

    private static float near(float distance) {
        return Math.clamp((distance - NEAR) / NEAR_FADE, 0.0F, 1.0F);
    }

    /**
     * The stream itself, a pixel to each row along it.
     */
    private static void stream(VertexConsumer buffer, PoseStack.Pose pose, Vector3f eye, Vector3f from, Vector3f direction, float length, float time,
                               int salt, float presence) {
        PixelRibbon ribbon = new PixelRibbon(pose);
        int rows = Math.max(2, Math.round(length / Pixels.SIZE));
        for (int k = 0; k <= rows; k++) {
            ribbon.add(new Vector3f(direction).mul(length * k / rows).add(from));
        }
        int frame = (int) Math.floor(time);
        for (int k = 0; k < rows; k++) {
            float t = (k + 0.5F) / rows;
            float out = t * length;
            float near = near(ribbon.point(k).distance(eye));
            if (near <= 0.0F) {
                continue;
            }
            // pouring out of the palm, narrow, and spreading where it lands
            float emerge = Math.min(1.0F, out / 0.25F);
            float half = (2.2F + 1.4F * t + 1.4F * Math.max(0.0F, (t - 0.85F) / 0.15F)) * (0.45F + 0.55F * emerge) * presence;
            if (half < 0.6F) {
                continue;
            }
            // surges of light running out of the hand
            float surge = (float) Math.pow(0.5 + 0.5 * Mth.sin(out * 1.3F - time * 1.7F + salt), 6);
            int reach = (int) Math.ceil(half);
            for (int b = -reach; b < reach; b++) {
                float across = Math.abs(b + 0.5F) / half;
                if (across >= 1.0F) {
                    continue;
                }
                // the churn flows out along it a tick at a time, like an animated texture
                float churn = Pixels.churn(out * 3.2F - frame * 0.9F, (b + 0.5F) * 0.4F, salt);
                float heat = (1.0F - across * across) * (0.5F + 0.65F * churn) * near;
                if (across < 0.3F) {
                    heat += (0.3F + 0.4F * surge) * near;
                }
                if (!Pixels.shows(heat * 2.4F - 0.15F, k + frame, b)) {
                    continue;
                }
                ribbon.cell(buffer, k, b, heat > 1.05F ? Pixels.opaque(Pixels.HOT) : heat > 0.85F ? Pixels.opaque(Pixels.PINK)
                        : heat > 0.66F ? Pixels.opaque(Pixels.BRIGHT) : heat > 0.47F ? Pixels.opaque(Pixels.SCARLET)
                        : heat > 0.32F ? Pixels.ramp(Pixels.CRIMSON, 0.75F) : Pixels.ramp(Pixels.WINE, 0.5F));
            }
        }
    }

    /**
     * A tendril twisting about the stream, rooted in the palm.
     */
    private static void tendril(VertexConsumer buffer, PoseStack.Pose pose, Vector3f eye, Vector3f from, Vector3f direction, Vector3f[] around,
                                float length, float time, int salt, int w, float presence) {
        float turning = w % 2 == 0 ? 1.0F : -1.0F;
        float phase = w * TAU / TENDRILS + salt * 0.37F;
        PixelRibbon ribbon = new PixelRibbon(pose);
        // a little under a pixel along for each point, as the twist makes it longer than the beam
        int rows = Math.max(2, Math.round(length / (Pixels.SIZE * 0.8F)));
        for (int k = 0; k <= rows; k++) {
            float t = k / (float) rows;
            float out = t * length;
            float radius = (0.2F + 0.1F * t + 0.05F * Mth.sin(out * 1.7F + time * 0.3F + w * 2.0F)) * Math.min(1.0F, out / 0.4F) * presence;
            float angle = out * 2.6F * turning + time * 0.55F * turning + phase;
            float c = Mth.cos(angle) * radius;
            float d = Mth.sin(angle) * radius;
            ribbon.add(new Vector3f(direction).mul(out).add(from)
                    .add(around[0].x * c + around[1].x * d, around[0].y * c + around[1].y * d, around[0].z * c + around[1].z * d));
        }
        // broken into dashes that run out along it
        int dash = (int) Math.floor(time * 0.6F);
        for (int k = 0; k < rows; k++) {
            float t = (k + 0.5F) / rows;
            float near = near(ribbon.point(k).distance(eye));
            if (near <= 0.0F || near < 1.0F && !Pixels.shows(near, k, w)) {
                continue;
            }
            int run = (k - dash * 3) / 5;
            if (Pixels.hash(run, w, 0, salt) < 0.22F + 0.35F * t) {
                continue;
            }
            int step = t < 0.25F ? Pixels.BRIGHT : t < 0.6F ? Pixels.SCARLET : Pixels.CRIMSON;
            int color = Pixels.ramp(step, t < 0.8F ? 1.0F : 0.75F);
            ribbon.cell(buffer, k, -1, color);
            if (t < 0.35F) {
                ribbon.cell(buffer, k, 0, color);
            }
        }
    }

    /**
     * The magic gathered in the palm: a churning ball, white-hot at its heart, its rim flickering.
     */
    private static void orb(PixelSprite sprite, float radius, float time, int salt) {
        int frame = (int) Math.floor(time);
        int reach = (int) Math.ceil(radius + 2.0F);
        for (int i = -reach; i < reach; i++) {
            for (int j = -reach; j < reach; j++) {
                float x = i + 0.5F;
                float y = j + 0.5F;
                float r = (float) Math.sqrt(x * x + y * y);
                float theta = (float) Math.atan2(y, x);
                float flame = 1.4F * Pixels.noise(Mth.cos(theta) * 2.5F + frame * 0.45F, Mth.sin(theta) * 2.5F - frame * 0.3F, salt);
                if (r >= radius + flame) {
                    continue;
                }
                int step = r < radius * 0.35F ? Pixels.HOT : r < radius * 0.65F ? Pixels.PINK : r < radius ? Pixels.BRIGHT : Pixels.SCARLET;
                sprite.cell(i, j, Pixels.opaque(step), 2);
            }
        }
    }

    /**
     * Where it lands: a flickering splash of it, a star of light at its heart.
     */
    private static void splash(PixelSprite sprite, float presence, float time, int salt) {
        int frame = (int) Math.floor(time);
        float radius = (3.0F + 1.2F * Pixels.hash(frame, 0, salt, 3)) * presence;
        int reach = (int) Math.ceil(radius + 3.0F);
        for (int i = -reach; i < reach; i++) {
            for (int j = -reach; j < reach; j++) {
                float x = i + 0.5F;
                float y = j + 0.5F;
                float r = (float) Math.sqrt(x * x + y * y);
                float theta = (float) Math.atan2(y, x);
                float lick = 2.6F * Pixels.noise(Mth.cos(theta) * 3.0F + frame * 0.7F, Mth.sin(theta) * 3.0F + frame * 0.5F, salt + 5);
                if (r >= radius + lick) {
                    continue;
                }
                float level = 1.0F - r / (radius + lick);
                if (!Pixels.shows(level * 1.8F + 0.1F, i + frame, j)) {
                    continue;
                }
                int step = r < radius * 0.4F ? Pixels.PINK : r < radius ? Pixels.BRIGHT : Pixels.SCARLET;
                sprite.cell(i, j, Pixels.ramp(step, r < radius ? 1.0F : 0.75F), 2);
            }
        }
        // a star of light at its heart, its arms changing each tick
        int arm = 2 + Math.round(3.0F * Pixels.hash(frame, 1, salt, 4) * presence);
        sprite.cell(0, 0, Pixels.opaque(Pixels.HOT), 4);
        sprite.cell(-1, 0, Pixels.opaque(Pixels.HOT), 4);
        sprite.cell(0, -1, Pixels.opaque(Pixels.HOT), 4);
        sprite.cell(-1, -1, Pixels.opaque(Pixels.HOT), 4);
        for (int s = 1; s <= arm; s++) {
            int color = Pixels.opaque(s < arm ? Pixels.HOT : Pixels.PINK);
            sprite.cell(s, 0, color, 4);
            sprite.cell(-1 - s, -1, color, 4);
            sprite.cell(-1, s, color, 4);
            sprite.cell(0, -1 - s, color, 4);
        }
    }
}
