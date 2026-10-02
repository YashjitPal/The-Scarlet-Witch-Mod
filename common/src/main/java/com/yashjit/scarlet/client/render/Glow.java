package com.yashjit.scarlet.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * Soft glowing discs for the additive {@link ScarletRenderTypes#glow()} type. Each disc is a fan of triangles (written
 * as degenerate quads) that is bright at the center and fades to nothing at the rim, so no texture is needed.
 */
public final class Glow {

    private static final int SEGMENTS = 8;
    private static final float[] COS = new float[SEGMENTS + 1];
    private static final float[] SIN = new float[SEGMENTS + 1];

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = Math.PI * 2 * i / SEGMENTS;
            COS[i] = (float) Math.cos(angle);
            SIN[i] = (float) Math.sin(angle);
        }
    }

    private Glow() {
    }

    /**
     * Camera-facing axes expressed in the local space of {@code pose}, so discs drawn inside a model part's transform
     * still face the camera and keep their intended world size.
     */
    public static Billboard billboard(PoseStack.Pose pose) {
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        Vector3f right = new Vector3f(camera.leftVector()).negate();
        Vector3f up = new Vector3f(camera.upVector());
        Matrix3f inverse = new Matrix3f(pose.pose()).invert();
        return new Billboard(inverse.transform(right), inverse.transform(up));
    }

    public record Billboard(Vector3f right, Vector3f up) {
    }

    /**
     * A disc of the given radius (in the pose's local units) centered at x, y, z.
     */
    public static void disc(VertexConsumer buffer, PoseStack.Pose pose, Billboard axes, float x, float y, float z, float radius, int argb) {
        int rim = argb & 0x00FFFFFF;
        Vector3f r = axes.right();
        Vector3f u = axes.up();
        for (int i = 0; i < SEGMENTS; i++) {
            float ax = (COS[i] * r.x + SIN[i] * u.x) * radius;
            float ay = (COS[i] * r.y + SIN[i] * u.y) * radius;
            float az = (COS[i] * r.z + SIN[i] * u.z) * radius;
            float bx = (COS[i + 1] * r.x + SIN[i + 1] * u.x) * radius;
            float by = (COS[i + 1] * r.y + SIN[i + 1] * u.y) * radius;
            float bz = (COS[i + 1] * r.z + SIN[i + 1] * u.z) * radius;
            buffer.addVertex(pose, x, y, z).setColor(argb);
            buffer.addVertex(pose, x + ax, y + ay, z + az).setColor(rim);
            buffer.addVertex(pose, x + bx, y + by, z + bz).setColor(rim);
            buffer.addVertex(pose, x + bx, y + by, z + bz).setColor(rim);
        }
    }

    /**
     * A bright core inside a wider, fainter halo: the basic spark.
     */
    public static void spark(VertexConsumer buffer, PoseStack.Pose pose, Billboard axes, float x, float y, float z, float radius,
                             int coreColor, int haloColor, float alpha) {
        disc(buffer, pose, axes, x, y, z, radius * 2.4F, withAlpha(haloColor, alpha * 0.22F));
        disc(buffer, pose, axes, x, y, z, radius, withAlpha(coreColor, alpha));
    }

    /**
     * A soft strip through {@code points}, facing the camera: bright along its spine and fading to nothing at both
     * edges. Width and color are given per point.
     */
    public static void ribbon(VertexConsumer buffer, PoseStack.Pose pose, Billboard axes, Vector3f[] points, float[] widths, int[] colors) {
        Vector3f forward = new Vector3f(axes.right()).cross(axes.up());
        Vector3f[] sides = new Vector3f[points.length];
        for (int i = 0; i < points.length; i++) {
            Vector3f along = new Vector3f(points[Math.min(i + 1, points.length - 1)]).sub(points[Math.max(i - 1, 0)]);
            Vector3f side = along.cross(forward);
            sides[i] = side.lengthSquared() < 1.0E-10F ? new Vector3f(axes.up()) : side.normalize();
            sides[i].mul(widths[i] * 0.5F);
        }
        for (int i = 0; i < points.length - 1; i++) {
            Vector3f a = points[i];
            Vector3f b = points[i + 1];
            int ca = colors[i];
            int cb = colors[i + 1];
            for (float sign : new float[] {1.0F, -1.0F}) {
                buffer.addVertex(pose, a.x, a.y, a.z).setColor(ca);
                buffer.addVertex(pose, a.x + sides[i].x * sign, a.y + sides[i].y * sign, a.z + sides[i].z * sign).setColor(ca & 0x00FFFFFF);
                buffer.addVertex(pose, b.x + sides[i + 1].x * sign, b.y + sides[i + 1].y * sign, b.z + sides[i + 1].z * sign).setColor(cb & 0x00FFFFFF);
                buffer.addVertex(pose, b.x, b.y, b.z).setColor(cb);
            }
        }
    }

    /**
     * A soft ring in the plane spanned by {@code u} and {@code v} (unit vectors), brightest at {@code radius}.
     */
    public static void ring(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, Vector3f u, Vector3f v, float radius,
                            float thickness, int argb, int segments) {
        float inner = Math.max(0.0F, radius - thickness * 0.5F);
        float outer = radius + thickness * 0.5F;
        int clear = argb & 0x00FFFFFF;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments;
            double a1 = Math.PI * 2 * (i + 1) / segments;
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            ringQuad(buffer, pose, x, y, z, u, v, c0, s0, c1, s1, inner, radius, clear, argb);
            ringQuad(buffer, pose, x, y, z, u, v, c0, s0, c1, s1, radius, outer, argb, clear);
        }
    }

    /**
     * A filled disc lying in the plane spanned by {@code u} and {@code v}, shading from {@code centerColor} to
     * {@code rimColor}.
     */
    public static void planeDisc(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, Vector3f u, Vector3f v, float radius,
                                 int centerColor, int rimColor, int segments) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments;
            double a1 = Math.PI * 2 * (i + 1) / segments;
            float c0 = (float) Math.cos(a0) * radius;
            float s0 = (float) Math.sin(a0) * radius;
            float c1 = (float) Math.cos(a1) * radius;
            float s1 = (float) Math.sin(a1) * radius;
            buffer.addVertex(pose, x, y, z).setColor(centerColor);
            buffer.addVertex(pose, x + u.x * c0 + v.x * s0, y + u.y * c0 + v.y * s0, z + u.z * c0 + v.z * s0).setColor(rimColor);
            buffer.addVertex(pose, x + u.x * c1 + v.x * s1, y + u.y * c1 + v.y * s1, z + u.z * c1 + v.z * s1).setColor(rimColor);
            buffer.addVertex(pose, x + u.x * c1 + v.x * s1, y + u.y * c1 + v.y * s1, z + u.z * c1 + v.z * s1).setColor(rimColor);
        }
    }

    /**
     * A flat band between two radii in the plane spanned by {@code u} and {@code v}, shading from {@code innerColor}
     * to {@code outerColor}.
     */
    public static void annulus(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, Vector3f u, Vector3f v, float inner,
                               float outer, int innerColor, int outerColor, int segments) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments;
            double a1 = Math.PI * 2 * (i + 1) / segments;
            ringQuad(buffer, pose, x, y, z, u, v, (float) Math.cos(a0), (float) Math.sin(a0), (float) Math.cos(a1), (float) Math.sin(a1),
                    inner, outer, innerColor, outerColor);
        }
    }

    /**
     * A soft line in the plane spanned by {@code u} and {@code v}, between the plane coordinates (ax, ay) and (bx, by)
     * around the origin x, y, z: bright along its middle, fading to nothing at both edges.
     */
    public static void planeLine(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, Vector3f u, Vector3f v, float ax, float ay,
                                 float bx, float by, float width, int argb) {
        float dx = bx - ax;
        float dy = by - ay;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0E-5F) {
            return;
        }
        float nx = -dy / length * width * 0.5F;
        float ny = dx / length * width * 0.5F;
        int clear = argb & 0x00FFFFFF;
        float pax = x + u.x * ax + v.x * ay;
        float pay = y + u.y * ax + v.y * ay;
        float paz = z + u.z * ax + v.z * ay;
        float pbx = x + u.x * bx + v.x * by;
        float pby = y + u.y * bx + v.y * by;
        float pbz = z + u.z * bx + v.z * by;
        float ox = u.x * nx + v.x * ny;
        float oy = u.y * nx + v.y * ny;
        float oz = u.z * nx + v.z * ny;
        for (float sign : new float[] {1.0F, -1.0F}) {
            buffer.addVertex(pose, pax, pay, paz).setColor(argb);
            buffer.addVertex(pose, pax + ox * sign, pay + oy * sign, paz + oz * sign).setColor(clear);
            buffer.addVertex(pose, pbx + ox * sign, pby + oy * sign, pbz + oz * sign).setColor(clear);
            buffer.addVertex(pose, pbx, pby, pbz).setColor(argb);
        }
    }

    /**
     * Blends between two RGB colors.
     */
    public static int mix(int from, int to, float amount) {
        float t = Math.clamp(amount, 0.0F, 1.0F);
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static void ringQuad(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, Vector3f u, Vector3f v,
                                 float c0, float s0, float c1, float s1, float r0, float r1, int color0, int color1) {
        buffer.addVertex(pose, x + (u.x * c0 + v.x * s0) * r0, y + (u.y * c0 + v.y * s0) * r0, z + (u.z * c0 + v.z * s0) * r0).setColor(color0);
        buffer.addVertex(pose, x + (u.x * c0 + v.x * s0) * r1, y + (u.y * c0 + v.y * s0) * r1, z + (u.z * c0 + v.z * s0) * r1).setColor(color1);
        buffer.addVertex(pose, x + (u.x * c1 + v.x * s1) * r1, y + (u.y * c1 + v.y * s1) * r1, z + (u.z * c1 + v.z * s1) * r1).setColor(color1);
        buffer.addVertex(pose, x + (u.x * c1 + v.x * s1) * r0, y + (u.y * c1 + v.y * s1) * r0, z + (u.z * c1 + v.z * s1) * r0).setColor(color0);
    }

    /**
     * Two unit vectors perpendicular to {@code normal} and to each other.
     */
    public static Vector3f[] planeAxes(Vector3f normal) {
        Vector3f helper = Math.abs(normal.y) < 0.9F ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f u = new Vector3f(normal).cross(helper).normalize();
        Vector3f v = new Vector3f(normal).cross(u).normalize();
        return new Vector3f[] {u, v};
    }

    public static int withAlpha(int rgb, float alpha) {
        int a = Math.clamp(Math.round(alpha * 255), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
