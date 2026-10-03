package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.PixelSprite;
import com.yashjit.scarlet.client.render.Pixels;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * The shapes Wanda's magic takes, as pixel art plotted onto a {@link PixelSprite}: tendrils curling from a hand to what
 * it reaches for, strands winding round something, rings, bursts and churning balls of it, and eyes kindled red. Every
 * pixel is one step of the {@link Pixels} ramp; light runs along the strands, and they thin out in a dither as they
 * fade.
 */
public final class Wisps {

    private static final float TAU = (float) (Math.PI * 2);
    /** How far in front of a face kindled eyes are drawn, toward whoever is looking. */
    private static final float EYE_LIFT = 0.2F;

    private Wisps() {
    }

    /**
     * A grid for magic reaching between two points, relative to the camera, pinned at whichever lies further ahead of
     * it: its pixels are then the size of a texel there, and toward the nearer end they come out finer than what is
     * around them rather than coarser, which reads as distance rather than as blocks.
     */
    public static PixelSprite farther(PoseStack.Pose pose, Vector3f a, Vector3f b) {
        Glow.Billboard axes = Glow.billboard(pose);
        // the camera's right and up cross to point back at whoever is looking
        Vector3f back = new Vector3f(axes.right()).cross(axes.up());
        Vector3f pin = a.dot(back) <= b.dot(back) ? a : b;
        return PixelSprite.inWorld(pose, pin.x, pin.y, pin.z);
    }

    /**
     * A tendril from {@code from} toward {@code to}, curling as it goes, grown {@code reach} of the way: two pixels thick
     * and hot leaving where it comes from, thin and cooler as it sinks in, a bead of light racing along it.
     *
     * @param curl  how far it strays from the straight way at its middle, in blocks
     * @param speed how fast the bead runs, its length a tick
     * @param salt  keeps tendrils on one sprite from flickering alike
     */
    public static void tendril(PixelSprite sprite, Vector3f from, Vector3f to, float reach, float curl, float seed, float time, float fade,
                               float speed, int salt) {
        Vector3f path = new Vector3f(to).sub(from);
        float length = path.length();
        if (length < 0.05F || reach <= 0.0F || fade <= 0.0F) {
            return;
        }
        Vector3f[] side = Glow.planeAxes(new Vector3f(path).normalize());
        int count = Math.clamp(Math.round(length * reach * 10.0F), 4, 64);
        float bead = (time * speed + salt * 0.37F) % 1.0F * reach;
        int frame = (int) Math.floor(time);
        Vector3f previous = null;
        for (int i = 0; i <= count; i++) {
            float s = reach * i / count;
            float envelope = Mth.sin(Math.min(1.0F, s) * Mth.PI);
            float a = time * 0.4F + s * 8.0F + seed;
            float u = (Mth.sin(a) * curl + Mth.sin(seed * 5.0F) * curl * 0.85F) * envelope;
            float v = (Mth.cos(a * 0.8F + seed) * curl + Mth.cos(seed * 4.0F) * curl * 0.85F) * envelope;
            Vector3f at = new Vector3f(path).mul(s).add(from).add(side[0].x * u + side[1].x * v, side[0].y * u + side[1].y * v,
                    side[0].z * u + side[1].z * v);
            if (previous != null && Pixels.shows(fade * (1.15F - 0.5F * s), i + frame, salt)) {
                float near = Math.abs(s - bead);
                int step = near < 0.035F ? Pixels.HOT : near < 0.08F ? Pixels.PINK : s < 0.35F ? Pixels.BRIGHT : s < 0.75F ? Pixels.SCARLET
                        : Pixels.CRIMSON;
                sprite.line(previous, at, Pixels.opaque(step), 1, s < 0.3F ? 2 : 1);
            }
            previous = at;
        }
    }

    /**
     * Strands winding up round an upright axis through {@code center}, from {@code bottom} to {@code top} above it,
     * narrowing from one radius to the other, turning as time goes on, light running up them.
     *
     * @param turns how many times each winds round
     * @param spin  how fast they turn, in radians a tick
     */
    public static void helix(PixelSprite sprite, Vector3f center, float radiusLow, float radiusHigh, float bottom, float top, int strands, float turns,
                             float spin, float time, float fade, float seed, int salt) {
        if (fade <= 0.0F) {
            return;
        }
        int frame = (int) Math.floor(time);
        float reach = Math.max(radiusLow, radiusHigh);
        int points = Math.clamp(Math.round((top - bottom) * 14.0F + turns * reach * 36.0F), 10, 56);
        for (int k = 0; k < strands; k++) {
            float phase = k * TAU / strands + seed;
            Vector3f previous = null;
            for (int i = 0; i <= points; i++) {
                float s = i / (float) points;
                float angle = s * TAU * turns + time * spin + phase;
                float r = Mth.lerp(s, radiusLow, radiusHigh) * (0.88F + 0.12F * Mth.sin(time * 0.2F + s * 6.0F + k));
                Vector3f at = new Vector3f(center.x + Mth.cos(angle) * r, center.y + Mth.lerp(s, bottom, top), center.z + Mth.sin(angle) * r);
                if (previous != null) {
                    float envelope = Mth.sin(s * Mth.PI);
                    float travel = (float) Math.pow(0.5 + 0.5 * Mth.sin(s * 9.0F - time * 0.5F + k * 2.1F), 3);
                    if (Pixels.shows(fade * (0.4F + 0.8F * envelope), i + frame, k + salt)) {
                        int step = travel > 0.75F ? Pixels.PINK : travel > 0.35F ? Pixels.BRIGHT : envelope < 0.35F ? Pixels.CRIMSON : Pixels.SCARLET;
                        sprite.line(previous, at, Pixels.opaque(step), 1, 1);
                    }
                }
                previous = at;
            }
        }
    }

    /**
     * A ring, or as much of one as {@code reach} goes round from {@code from} radians, in the plane of the unit axes
     * {@code u} and {@code v} through {@code center}.
     */
    public static void ring(PixelSprite sprite, Vector3f center, Vector3f u, Vector3f v, float radius, float from, float reach, int argb, int priority,
                            int thickness) {
        int segments = Math.clamp(Math.round(radius * TAU * 10.0F * reach), 6, 120);
        Vector3f previous = null;
        for (int i = 0; i <= segments; i++) {
            float a = from + reach * TAU * i / segments;
            float x = Mth.cos(a) * radius;
            float y = Mth.sin(a) * radius;
            Vector3f at = new Vector3f(center.x + u.x * x + v.x * y, center.y + u.y * x + v.y * y, center.z + u.z * x + v.z * y);
            if (previous != null) {
                sprite.line(previous, at, argb, priority, thickness);
            }
            previous = at;
        }
    }

    /**
     * A ring of pixels bursting out round where the sprite is pinned, as seen, {@code radius} blocks out and
     * {@code width} deep: white-hot at its leading edge and cooling behind it, breaking up in a dither as it fades.
     */
    public static void burst(PixelSprite sprite, float radius, float width, float fade, int frame) {
        float outer = radius / Pixels.SIZE;
        float inner = Math.max(0.0F, radius - width) / Pixels.SIZE;
        int reach = (int) Math.ceil(outer) + 1;
        for (int i = -reach; i < reach; i++) {
            for (int j = -reach; j < reach; j++) {
                float r = (float) Math.sqrt((i + 0.5F) * (i + 0.5F) + (j + 0.5F) * (j + 0.5F));
                if (r > outer || r < inner) {
                    continue;
                }
                float k = (r - inner) / Math.max(1.0F, outer - inner);
                if (!Pixels.shows(fade * (0.35F + 0.8F * k), i + frame, j)) {
                    continue;
                }
                int step = k > 0.82F ? Pixels.HOT : k > 0.6F ? Pixels.PINK : k > 0.35F ? Pixels.BRIGHT : Pixels.SCARLET;
                sprite.cell(i, j, Pixels.opaque(step), 1);
            }
        }
    }

    /**
     * A ball of magic churning where the sprite is pinned, {@code radius} pixels across its body, white-hot at its heart,
     * its rim licking out like flame.
     *
     * @param flame how far the flames reach past its body, in pixels
     */
    public static void orb(PixelSprite sprite, float radius, float flame, int frame, int salt) {
        int reach = (int) Math.ceil(radius + flame + 1.0F);
        for (int i = -reach; i < reach; i++) {
            for (int j = -reach; j < reach; j++) {
                float x = i + 0.5F;
                float y = j + 0.5F;
                float r = (float) Math.sqrt(x * x + y * y);
                float theta = (float) Math.atan2(y, x);
                float lick = flame * Pixels.noise(Mth.cos(theta) * 2.0F + frame * 0.5F, Mth.sin(theta) * 2.0F - frame * 0.35F, salt);
                if (r >= radius + lick) {
                    continue;
                }
                int step = r < radius * 0.45F ? Pixels.HOT : r < radius ? Pixels.PINK : r < radius + lick * 0.5F ? Pixels.BRIGHT : Pixels.SCARLET;
                sprite.cell(i, j, Pixels.opaque(step), 2);
            }
        }
    }

    /**
     * Two eyes kindled red, a little out from a face: a white-hot pixel each with a red glint round it, bigger on bigger
     * heads, the glint flickering.
     *
     * @param head     where the eyes look from
     * @param size     how big the head is, 1 for a person's
     * @param out      how far in front of where it looks from its face is
     * @param yaw      which way the face looks, in degrees
     * @param strength how fiercely they burn, 0 to 1
     */
    public static void eyes(PixelSprite sprite, Vector3f head, float size, float out, float yaw, float strength, float time) {
        if (strength < 0.01F) {
            return;
        }
        float rad = yaw * Mth.DEG_TO_RAD;
        float fx = -Mth.sin(rad);
        float fz = Mth.cos(rad);
        float apart = 0.11F * size;
        int frame = (int) Math.floor(time);
        int thickness = size > 1.6F ? 2 : 1;
        Vector3f right = sprite.right();
        Vector3f up = sprite.up();
        float step = Pixels.SIZE * (thickness * 0.5F + 0.5F);
        for (int side = -1; side <= 1; side += 2) {
            float x = head.x + fx * out + fz * apart * side;
            float y = head.y + 0.06F * size;
            float z = head.z + fz * out - fx * apart * side;
            // drawn a little in front of the face as seen, so the face never hides them
            sprite.plotInFront(x, y, z, EYE_LIFT, Pixels.opaque(strength > 0.4F ? Pixels.HOT : Pixels.PINK), 4, thickness);
            if (strength < 0.25F) {
                continue;
            }
            // the glint round it, a pixel out each way
            int glint = Pixels.opaque(strength > 0.7F ? Pixels.BRIGHT : Pixels.SCARLET);
            for (int d = 0; d < 4; d++) {
                float di = d == 0 ? -step : d == 1 ? step : 0.0F;
                float dj = d == 2 ? -step : d == 3 ? step : 0.0F;
                if (Pixels.hash(d, side, frame, 3) < 0.35F + 0.6F * strength) {
                    sprite.plotInFront(x + right.x * di + up.x * dj, y + right.y * di + up.y * dj, z + right.z * di + up.z * dj, EYE_LIFT, glint, 3, 1);
                }
            }
        }
    }
}
