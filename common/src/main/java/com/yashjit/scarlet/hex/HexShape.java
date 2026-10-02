package com.yashjit.scarlet.hex;

import net.minecraft.world.phys.Vec3;

/**
 * The shape of a Hex: a hexagon seen from above, its six walls rising straight up from the ground and bending over
 * into a dome, with ridges at the corners running up to meet over the middle. It is where three elliptic cylinders,
 * lying 60 degrees apart, all overlap, so each wall is the curve of one of them. It reaches as far below the center
 * as above, so a Hex cast on a hill still comes down to the valley around it.
 *
 * <p>Flat walls face north and south; corners point east and west. A Hex's radius reaches the middle of each wall,
 * and its corners stand a little further out. The shader {@code post/hex.fsh} traces the same shape.
 */
public final class HexShape {

    /** How high the dome rises over the center, per block of radius. */
    public static final float HEIGHT = 1.15F;
    /** How far the corners stand from the center, per block of radius: 1 / cos 30°. */
    public static final float CORNER = 1.1547005F;

    private static final double COS_30 = Math.sqrt(3.0) / 2.0;

    private HexShape() {
    }

    /**
     * How far out a point is, as a share of the way from the center to the wall through it: under 1 inside.
     */
    public static double level(Vec3 center, float radius, Vec3 point) {
        double x = point.x - center.x;
        double z = point.z - center.z;
        double across = Math.max(Math.abs(z), Math.max(Math.abs(COS_30 * x + 0.5 * z), Math.abs(COS_30 * x - 0.5 * z))) / radius;
        double up = (point.y - center.y) / (radius * HEIGHT);
        return Math.sqrt(across * across + up * up);
    }

    public static boolean contains(Vec3 center, float radius, Vec3 point) {
        return radius > 0.0F && level(center, radius, point) < 1.0;
    }

    /**
     * The radius of a ball around the center that holds the whole Hex.
     */
    public static float extent(float radius) {
        return radius * Math.max(CORNER, HEIGHT);
    }
}
