package com.yashjit.scarlet.hex;

import net.minecraft.world.phys.Vec3;

/**
 * The shape of a Hex: a hexagon seen from above and from every side, all flat faces and straight edges. Six walls
 * rise straight up from the ground to a sharp edge, and six flat triangular facets slope in from there to a single
 * point over the middle, the ridges between them running straight up from the corners. It reaches as far below the
 * center as above, so a Hex cast on a hill still comes down to the valley around it.
 *
 * <p>Flat walls face north and south; corners point east and west. A Hex's radius reaches the middle of each wall,
 * and its corners stand a little further out. The shader {@code post/hex.fsh} traces the same shape.
 */
public final class HexShape {

    /** How high the walls stand over the center before the roof facets begin, per block of radius. */
    public static final float WALL = 0.7F;
    /** How high the point of the roof stands over the center, per block of radius. */
    public static final float HEIGHT = 1.25F;
    /** How far the corners stand from the center, per block of radius: 1 / cos 30°. */
    public static final float CORNER = 1.1547005F;

    private static final double COS_30 = Math.sqrt(3.0) / 2.0;

    private HexShape() {
    }

    /**
     * How far out a point is, as a share of the way from the center to the surface through it: under 1 inside. Both
     * the walls and the roof facets are flat, so this is the largest of their planes' measures.
     */
    public static double level(Vec3 center, float radius, Vec3 point) {
        double x = point.x - center.x;
        double z = point.z - center.z;
        double across = Math.max(Math.abs(z), Math.max(Math.abs(COS_30 * x + 0.5 * z), Math.abs(COS_30 * x - 0.5 * z))) / radius;
        double up = Math.abs(point.y - center.y) / radius;
        return Math.max(across, (up + (HEIGHT - WALL) * across) / HEIGHT);
    }

    public static boolean contains(Vec3 center, float radius, Vec3 point) {
        return radius > 0.0F && level(center, radius, point) < 1.0;
    }

    /**
     * The radius of a ball around the center that holds the whole Hex: out to the tops of its corners, or its point.
     */
    public static float extent(float radius) {
        return radius * (float) Math.max(Math.hypot(CORNER, WALL), HEIGHT);
    }
}
