package com.yashjit.scarlet.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Pixels on a strip that runs along a path and turns about it to face the camera: a trail or a beam drawn as pixel
 * art, its pixels a set size in the world and tiling with each other however it bends, the way a texture would. Row k
 * runs from the k-th point of the path to the next, and pixel b of it lies b pixels to one side of the path's middle
 * (negative b to the other). Seen end on it narrows away, as anything flat does.
 */
public final class PixelRibbon {

    private final PoseStack.Pose pose;
    private final Vector3f eye;
    private final float size;
    private final List<Vector3f> points = new ArrayList<>();
    private final List<Vector3f> sides = new ArrayList<>();
    private final Vector3f a = new Vector3f();
    private final Vector3f b = new Vector3f();
    private final Vector3f c = new Vector3f();
    private final Vector3f d = new Vector3f();

    public PixelRibbon(PoseStack.Pose pose, float size) {
        this.pose = pose;
        this.eye = new Matrix4f(pose.pose()).invert().transformPosition(0.0F, 0.0F, 0.0F, new Vector3f());
        this.size = size;
    }

    public PixelRibbon(PoseStack.Pose pose) {
        this(pose, Pixels.SIZE);
    }

    /**
     * Adds the next point of the path, in the pose's space; they should be about a pixel apart.
     */
    public void add(Vector3f point) {
        points.add(new Vector3f(point));
        sides.clear();
    }

    /**
     * How many rows it has: one fewer than its points.
     */
    public int rows() {
        return Math.max(0, points.size() - 1);
    }

    public Vector3f point(int k) {
        return points.get(k);
    }

    private void build() {
        if (sides.size() == points.size()) {
            return;
        }
        sides.clear();
        Vector3f previous = null;
        for (int k = 0; k < points.size(); k++) {
            Vector3f along = new Vector3f(points.get(Math.min(k + 1, points.size() - 1))).sub(points.get(Math.max(k - 1, 0)));
            Vector3f view = new Vector3f(points.get(k)).sub(eye);
            Vector3f side = along.cross(view);
            if (side.lengthSquared() < 1.0E-10F) {
                side = previous != null ? new Vector3f(previous) : new Vector3f(0.0F, 1.0F, 0.0F);
            } else {
                side.normalize();
                // keep it turning the same way along its length, so the pixels of one row meet those of the next
                if (previous != null && side.dot(previous) < 0.0F) {
                    side.negate();
                }
            }
            sides.add(side);
            previous = side;
        }
    }

    /**
     * Draws pixel b of row k.
     */
    public void cell(VertexConsumer buffer, int k, int b, int argb) {
        build();
        if (k < 0 || k >= rows()) {
            return;
        }
        Vector3f p0 = points.get(k);
        Vector3f p1 = points.get(k + 1);
        Vector3f s0 = sides.get(k);
        Vector3f s1 = sides.get(k + 1);
        a.set(s0).mul(b * size).add(p0);
        this.b.set(s0).mul((b + 1) * size).add(p0);
        c.set(s1).mul((b + 1) * size).add(p1);
        d.set(s1).mul(b * size).add(p1);
        Pixels.quad(buffer, pose, a, this.b, c, d, argb);
    }
}
