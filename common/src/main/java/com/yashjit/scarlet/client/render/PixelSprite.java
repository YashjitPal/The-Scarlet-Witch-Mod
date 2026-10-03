package com.yashjit.scarlet.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.Arrays;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Pixels on a grid that faces the camera, the way a particle's sprite does: whatever is plotted on it lands on whole
 * {@link Pixels} that tile with each other on screen, so a wisp curling through the air reads as a line of pixels
 * rather than a smear. Each pixel is pushed along its line of sight to the depth it was plotted at, so it stays on the
 * grid as seen while the world, and the caster, still hide whatever passes behind them. Where two things land on one
 * pixel, the one plotted with the higher priority shows, and of those the nearer.
 *
 * <p>Coordinates are in the space of the pose it was made for, as everything drawn with that pose is.
 */
public final class PixelSprite {

    private final PoseStack.Pose pose;
    /** The camera's right and up, a block long as seen, in the pose's space. */
    private final Vector3f right;
    private final Vector3f up;
    private final Vector3f normal;
    private final Vector3f eye;
    private final Vector3f anchor;
    private final float size;
    private final float rightLength2;
    private final float upLength2;
    private final float anchorDepth;

    private final float[] at = new float[3];
    private final float[] from = new float[3];
    private final float[] to = new float[3];
    private final Long2IntOpenHashMap index = new Long2IntOpenHashMap();
    private int count;
    private int[] cells = new int[64];
    private int[] colors = new int[32];
    private int[] priorities = new int[32];
    private float[] depths = new float[32];

    /**
     * A grid pinned with a pixel corner at x, y, z, its pixels {@code size} blocks across where it is pinned.
     */
    public PixelSprite(PoseStack.Pose pose, float x, float y, float z, float size) {
        this.pose = pose;
        Glow.Billboard axes = Glow.billboard(pose);
        this.right = axes.right();
        this.up = axes.up();
        this.normal = new Vector3f(right).cross(up);
        this.eye = new Matrix4f(pose.pose()).invert().transformPosition(0.0F, 0.0F, 0.0F, new Vector3f());
        this.anchor = new Vector3f(x, y, z);
        this.size = size;
        this.rightLength2 = right.lengthSquared();
        this.upLength2 = up.lengthSquared();
        float depth = new Vector3f(anchor).sub(eye).dot(normal);
        this.anchorDepth = Math.abs(depth) < 1.0E-5F ? 1.0E-5F : depth;
        index.defaultReturnValue(-1);
    }

    /**
     * A grid of standard pixels pinned at x, y, z.
     */
    public PixelSprite(PoseStack.Pose pose, float x, float y, float z) {
        this(pose, x, y, z, Pixels.SIZE);
    }

    /**
     * The camera's right, a block long, in the pose's space.
     */
    public Vector3f right() {
        return right;
    }

    /**
     * The camera's up, a block long, in the pose's space.
     */
    public Vector3f up() {
        return up;
    }

    /**
     * Where a point falls on the grid, as seen, in pixels: {@code out} gets its two grid coordinates, then its depth as
     * a ratio to the grid's own. False if it is behind the camera.
     */
    private boolean locate(float x, float y, float z, float[] out) {
        float px = x - eye.x;
        float py = y - eye.y;
        float pz = z - eye.z;
        float depth = px * normal.x + py * normal.y + pz * normal.z;
        float lambda = depth / anchorDepth;
        if (lambda <= 1.0E-3F) {
            return false;
        }
        // where its line of sight crosses the grid
        float qx = eye.x + px / lambda - anchor.x;
        float qy = eye.y + py / lambda - anchor.y;
        float qz = eye.z + pz / lambda - anchor.z;
        out[0] = (qx * right.x + qy * right.y + qz * right.z) / rightLength2 / size;
        out[1] = (qx * up.x + qy * up.y + qz * up.z) / upLength2 / size;
        out[2] = lambda;
        return true;
    }

    /**
     * Plots a point onto the pixel it falls on.
     */
    public void plot(float x, float y, float z, int argb, int priority) {
        plot(x, y, z, argb, priority, 1);
    }

    /**
     * Plots a point as a square of pixels {@code thickness} across, centered where it falls.
     */
    public void plot(float x, float y, float z, int argb, int priority, int thickness) {
        if (!locate(x, y, z, at)) {
            return;
        }
        int i0 = (int) Math.floor(at[0] - thickness * 0.5F + 0.5F);
        int j0 = (int) Math.floor(at[1] - thickness * 0.5F + 0.5F);
        for (int di = 0; di < thickness; di++) {
            for (int dj = 0; dj < thickness; dj++) {
                put(i0 + di, j0 + dj, at[2], argb, priority);
            }
        }
    }

    /**
     * Plots a line of pixels from a to b, with no gaps in it, its color and thickness at each end.
     */
    public void line(Vector3f a, Vector3f b, int argb, int priority, int thickness) {
        if (!locate(a.x, a.y, a.z, from) || !locate(b.x, b.y, b.z, to)) {
            plot(a.x, a.y, a.z, argb, priority, thickness);
            return;
        }
        int steps = Math.min(512, (int) Math.ceil(Math.max(Math.abs(to[0] - from[0]), Math.abs(to[1] - from[1])) * 2.0F) + 1);
        for (int k = 0; k <= steps; k++) {
            float t = k / (float) steps;
            plot(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t, argb, priority, thickness);
        }
    }

    /**
     * Sets pixel (i, j) of the grid itself, pixel (0, 0) just up and to the right of where it is pinned, at the depth
     * it is pinned at.
     */
    public void cell(int i, int j, int argb, int priority) {
        put(i, j, 1.0F, argb, priority);
    }

    /**
     * Which pixel of the grid a point falls on, as {i, j}, or null if it is behind the camera.
     */
    public int[] cellOf(float x, float y, float z) {
        if (!locate(x, y, z, at)) {
            return null;
        }
        return new int[] {(int) Math.floor(at[0]), (int) Math.floor(at[1])};
    }

    private void put(int i, int j, float lambda, int argb, int priority) {
        if ((argb >>> 24) == 0) {
            return;
        }
        long key = ((long) i << 32) ^ (j & 0xFFFFFFFFL);
        int slot = index.get(key);
        if (slot < 0) {
            if (count == colors.length) {
                int grown = count * 2;
                cells = Arrays.copyOf(cells, grown * 2);
                colors = Arrays.copyOf(colors, grown);
                priorities = Arrays.copyOf(priorities, grown);
                depths = Arrays.copyOf(depths, grown);
            }
            slot = count++;
            index.put(key, slot);
            cells[slot * 2] = i;
            cells[slot * 2 + 1] = j;
        } else if (priorities[slot] > priority || priorities[slot] == priority && depths[slot] <= lambda) {
            return;
        }
        colors[slot] = argb;
        priorities[slot] = priority;
        depths[slot] = lambda;
    }

    /**
     * How many pixels have been set.
     */
    public int count() {
        return count;
    }

    /**
     * Draws every pixel set.
     */
    public void draw(VertexConsumer buffer) {
        Vector3f center = new Vector3f();
        for (int k = 0; k < count; k++) {
            float lambda = depths[k];
            float i = cells[k * 2] + 0.5F;
            float j = cells[k * 2 + 1] + 0.5F;
            // the pixel's middle on the grid, then out along its line of sight to its own depth
            center.set(anchor).add(right.x * i * size + up.x * j * size, right.y * i * size + up.y * j * size, right.z * i * size + up.z * j * size)
                    .sub(eye).mul(lambda).add(eye);
            float half = size * 0.5F * lambda;
            float rx = right.x * half;
            float ry = right.y * half;
            float rz = right.z * half;
            float ux = up.x * half;
            float uy = up.y * half;
            float uz = up.z * half;
            int color = Glow.shaded(colors[k]);
            buffer.addVertex(pose, center.x - rx - ux, center.y - ry - uy, center.z - rz - uz).setColor(color);
            buffer.addVertex(pose, center.x + rx - ux, center.y + ry - uy, center.z + rz - uz).setColor(color);
            buffer.addVertex(pose, center.x + rx + ux, center.y + ry + uy, center.z + rz + uz).setColor(color);
            buffer.addVertex(pose, center.x - rx + ux, center.y - ry + uy, center.z - rz + uz).setColor(color);
        }
    }
}
