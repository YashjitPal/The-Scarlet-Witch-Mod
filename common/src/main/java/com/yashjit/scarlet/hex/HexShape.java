package com.yashjit.scarlet.hex;

import net.minecraft.world.phys.Vec3;

/**
 * The shape of a Hex: a hexagon seen from above, its six walls standing straight up without end. Nothing closes over
 * the top: seen from the ground the walls rise until they fade into the sky. The Hex is everything within its walls,
 * as high and as deep as the world goes.
 *
 * <p>Flat walls face north and south; corners point east and west. A Hex's radius reaches the middle of each wall,
 * and its corners stand a little further out. The shader {@code post/hex.fsh} traces the same shape.
 */
public final class HexShape {

    /** How far the corners stand from the center, per block of radius: 1 / cos 30°. */
    public static final float CORNER = 1.1547005F;

    private static final double COS_30 = Math.sqrt(3.0) / 2.0;

    private HexShape() {
    }

    /**
     * How far out a point is, as a share of the way from the center to the wall it faces: under 1 inside.
     */
    public static double level(Vec3 center, float radius, Vec3 point) {
        double x = point.x - center.x;
        double z = point.z - center.z;
        return Math.max(Math.abs(z), Math.max(Math.abs(COS_30 * x + 0.5 * z), Math.abs(COS_30 * x - 0.5 * z))) / radius;
    }

    public static boolean contains(Vec3 center, float radius, Vec3 point) {
        return radius > 0.0F && level(center, radius, point) < 1.0;
    }

    /**
     * How far across the ground the Hex reaches from its center, out to its corners.
     */
    public static float reach(float radius) {
        return radius * CORNER;
    }
}
