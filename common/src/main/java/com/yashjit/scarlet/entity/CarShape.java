package com.yashjit.scarlet.entity;

import com.yashjit.scarlet.hex.Era;
import net.minecraft.world.phys.Vec3;

/**
 * How each era's car is laid out, in sixteenths of a block, from the middle of the car on the ground: across to its
 * right, up, and along to its tail, its nose toward negative along. What it is shaped of is the client's to draw; this is
 * what the car itself needs to know: how big it is, where its wheels turn and where its riders sit.
 *
 * @param kind       how its cabin runs back: a sedan's to a raked rear window over its trunk, a wagon's or a van's all the
 *                   way to its tail, a hatchback's to a hatch sloping down to it
 * @param sill       how high its body starts off the ground
 * @param hood       how high its hood and its trunk lid are
 * @param belt       where its windows start, along the tops of its doors
 * @param roof       the top of its roof
 * @param windshield where the foot of its windshield is, along
 * @param rakeFront  how far back its windshield leans by the time it reaches the roof
 * @param backlight  where the foot of its rear window, or its tailgate, is
 * @param rakeRear   how far forward its rear window leans by the time it reaches the roof
 * @param seat       where its front seats' riders sit, along
 * @param seatHeight how high they sit, in blocks, over the car's own feet, for their eyes to clear the dash
 */
public record CarShape(Kind kind, float length, float width, float sill, float hood, float belt, float roof, float windshield,
                       float rakeFront, float backlight, float rakeRear, float wheelRadius, float wheelWidth, float frontAxle, float rearAxle,
                       float seat, float seatHeight) {

    public enum Kind {
        SEDAN, WAGON, HATCHBACK, VAN, CROSSOVER
    }

    private static final CarShape[] SHAPES = {
            // a long, low sedan with tailfins over its trunk
            new CarShape(Kind.SEDAN, 80, 30, 3, 13.5F, 15, 24, -13, 6, 17, 7, 5.5F, 3.5F, -23, 23, -1, 0.23F),
            // a station wagon, its roof running all the way back
            new CarShape(Kind.WAGON, 80, 30, 3, 13.5F, 15, 24, -12, 6, 38, 1, 5.5F, 3.5F, -24, 24, 0, 0.23F),
            // a bigger wagon still, paneled in wood
            new CarShape(Kind.WAGON, 82, 32, 3.5F, 14, 15.5F, 25, -12, 7, 39.5F, 1, 6, 4, -25, 25, 0, 0.26F),
            // a boxy little hatchback
            new CarShape(Kind.HATCHBACK, 66, 28, 3, 13, 14, 23, -10, 8, 31, 10, 5, 3.5F, -20, 21, 0, 0.17F),
            // a minivan, sat up high, its windshield running far out over its short nose
            new CarShape(Kind.VAN, 78, 31, 3.5F, 15, 16, 28, -19, 12, 37.5F, 2, 6, 3.5F, -24, 25, -3, 0.29F),
            // an electric crossover, smooth all over
            new CarShape(Kind.CROSSOVER, 74, 31, 4.5F, 15.5F, 16.5F, 26, -14, 10, 31, 8, 6.5F, 4, -24, 24, -2, 0.32F)
    };

    public static CarShape of(Era era) {
        return SHAPES[era.ordinal()];
    }

    /** Where across its wheels turn, from the middle to the middle of each tire. */
    public float track() {
        return width / 2 - wheelWidth / 2 - 0.5F;
    }

    /** Where across each front seat's rider sits, from the middle. */
    public float seatAcross() {
        return width / 2 - 7.5F;
    }

    /**
     * Where a rider sits, in blocks from the car's feet, as the car is turned: across to its left (the driver's side for
     * the first), up, and forward.
     */
    public Vec3 seat(int index) {
        double across = (index <= 0 ? 1 : -1) * seatAcross() / 16.0;
        return new Vec3(across, seatHeight, -seat / 16.0);
    }

    /** Half its length, in blocks. */
    public double halfLength() {
        return length / 32.0;
    }

    /** Half its width, in blocks. */
    public double halfWidth() {
        return width / 32.0;
    }

    /** Its height, in blocks. */
    public double height() {
        return roof / 16.0 + 0.1;
    }
}
