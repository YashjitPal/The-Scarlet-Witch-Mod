package com.yashjit.scarlet.hex.town;

import com.yashjit.scarlet.hex.HexShape;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Where everything in a Hex's town goes: a suburban grid laid out from where the Hex was cast.
 *
 * <p>The caster stands on the sidewalk of the main street with their home in front of them, facing back at them, and
 * the town square across the street behind them. Streets run parallel to the main street every {@value #STREET_PERIOD}
 * blocks with a row of lots on each side, and cross streets cut through after every three lots.
 *
 * <p>Positions on the plan are (u, v): v runs from the caster toward their home, u to its right.
 */
public final class TownPlan {

    public static final int LOT_WIDTH = 11;
    public static final int LOT_DEPTH = 13;
    public static final int STREET_WIDTH = 7;
    /** From one street to the next: the street and a row of lots on each side of it. */
    public static final int STREET_PERIOD = STREET_WIDTH + 2 * LOT_DEPTH;
    /** Three lots and a cross street. */
    public static final int BLOCK_PERIOD = 3 * LOT_WIDTH + STREET_WIDTH;
    /** How tall the tallest house stands over its lot, for the open air it needs. */
    public static final int HOUSE_HEIGHT = 15;

    private final BlockPos center;
    private final Direction forward;
    private final Direction right;
    private final List<Part> parts;

    public TownPlan(BlockPos center, Direction forward, float maxRadius) {
        this.center = center;
        this.forward = forward;
        this.right = forward.getClockWise();
        this.parts = Collections.unmodifiableList(layOut(maxRadius));
    }

    public List<Part> parts() {
        return parts;
    }

    public Direction forward() {
        return forward;
    }

    public BlockPos at(int u, int v, int y) {
        return new BlockPos(center.getX() + right.getStepX() * u + forward.getStepX() * v, y,
                center.getZ() + right.getStepZ() * u + forward.getStepZ() * v);
    }

    private List<Part> layOut(float maxRadius) {
        List<Part> laid = new ArrayList<>();
        int streets = (int) Math.ceil(maxRadius * HexShape.CORNER / STREET_PERIOD) + 1;
        int blocks = (int) Math.ceil(maxRadius * HexShape.CORNER / BLOCK_PERIOD) + 1;
        for (int s = -streets; s <= streets; s++) {
            int streetFrom = STREET_PERIOD * s - (STREET_WIDTH - 1);
            int streetTo = STREET_PERIOD * s;
            for (int g = -blocks; g <= blocks; g++) {
                int groupCenter = BLOCK_PERIOD * g;
                for (int k = -1; k <= 1; k++) {
                    int lotFrom = groupCenter + LOT_WIDTH * k - LOT_WIDTH / 2;
                    int lotTo = lotFrom + LOT_WIDTH - 1;
                    // the street in front of this stretch of lots
                    laid.add(part(Kind.STREET, s, g, k, 0, lotFrom, lotTo, streetFrom, streetTo, forward));
                    // the lots facing the street: from beyond it, and from this side of it
                    Kind far = s == 0 && g == 0 && k == 0 ? Kind.HOME : Kind.HOUSE;
                    laid.add(part(far, s, g, k, 0, lotFrom, lotTo, streetTo + 1, streetTo + LOT_DEPTH, forward.getOpposite()));
                    if (!(s == 0 && g == 0)) {
                        laid.add(part(Kind.HOUSE, s, g, k, 1, lotFrom, lotTo, streetFrom - LOT_DEPTH, streetFrom - 1, forward));
                    }
                }
                // the town square takes the three lots across the main street from the caster's home
                if (s == 0 && g == 0) {
                    laid.add(part(Kind.SQUARE, s, g, 0, 1, groupCenter - LOT_WIDTH * 3 / 2, groupCenter + LOT_WIDTH * 3 / 2,
                            streetFrom - LOT_DEPTH, streetFrom - 1, forward));
                }
                int crossFrom = groupCenter + LOT_WIDTH * 3 / 2 + 1;
                int crossTo = crossFrom + STREET_WIDTH - 1;
                laid.add(part(Kind.CROSSING, s, g, 0, 0, crossFrom, crossTo, streetFrom, streetTo, forward));
                // the cross street, through both rows of lots up to the next street
                laid.add(part(Kind.CROSS_STREET, s, g, 0, 0, crossFrom, crossTo, streetTo + 1, streetTo + LOT_DEPTH, right));
                laid.add(part(Kind.CROSS_STREET, s, g, 0, 1, crossFrom, crossTo, streetTo + LOT_DEPTH + 1, streetTo + 2 * LOT_DEPTH, right));
            }
        }
        laid.sort((a, b) -> Double.compare(a.distanceSqr(), b.distanceSqr()));
        return laid;
    }

    /**
     * @param front which way the part faces: for a lot, from its house toward its street
     */
    private Part part(Kind kind, int s, int g, int k, int side, int uFrom, int uTo, int vFrom, int vTo, Direction front) {
        int id = kind.ordinal() << 24 | (s + 64) << 16 | (g + 64) << 8 | (k + 8) << 1 | side;
        BlockPos a = at(uFrom, vFrom, center.getY());
        BlockPos b = at(uTo, vTo, center.getY());
        int minX = Math.min(a.getX(), b.getX());
        int maxX = Math.max(a.getX(), b.getX());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxZ = Math.max(a.getZ(), b.getZ());
        // the front-left corner as seen from the street, looking at the part
        Direction back = front.getOpposite();
        Direction across = back.getClockWise();
        int originX;
        int originZ;
        if (back.getAxis() == Direction.Axis.X) {
            originX = back.getStepX() > 0 ? minX : maxX;
            originZ = across.getStepZ() > 0 ? minZ : maxZ;
        } else {
            originZ = back.getStepZ() > 0 ? minZ : maxZ;
            originX = across.getStepX() > 0 ? minX : maxX;
        }
        double cx = (minX + maxX + 1) / 2.0 - (center.getX() + 0.5);
        double cz = (minZ + maxZ + 1) / 2.0 - (center.getZ() + 0.5);
        return new Part(id, kind, minX, minZ, maxX, maxZ, new BlockPos(originX, center.getY(), originZ), front, cx * cx + cz * cz);
    }

    /**
     * Whether a part's whole footprint lies inside a Hex of the given radius.
     */
    public static boolean fits(Part part, Vec3 center, float radius, int groundY) {
        double inset = 0.5;
        for (int corner = 0; corner < 4; corner++) {
            double x = (corner & 1) == 0 ? part.minX() + inset : part.maxX() + 1 - inset;
            double z = (corner & 2) == 0 ? part.minZ() + inset : part.maxZ() + 1 - inset;
            if (!HexShape.contains(center, radius, new Vec3(x, groundY, z))) {
                return false;
            }
        }
        return true;
    }

    public enum Kind {
        HOME(HOUSE_HEIGHT), HOUSE(HOUSE_HEIGHT), SQUARE(10), STREET(5), CROSSING(2), CROSS_STREET(5);

        private final int height;

        Kind(int height) {
            this.height = height;
        }

        public int height() {
            return height;
        }

        public boolean isStreet() {
            return this == STREET || this == CROSSING || this == CROSS_STREET;
        }
    }

    /**
     * One piece of the plan: a lot, a stretch of street, a crossing or the square.
     *
     * @param origin the front-left corner as seen from the street, at the cast point's height
     * @param front  which way it faces: for a lot, from its house toward its street
     */
    public record Part(int id, Kind kind, int minX, int minZ, int maxX, int maxZ, BlockPos origin, Direction front, double distanceSqr) {

        public int width() {
            return front.getAxis() == Direction.Axis.Z ? maxX - minX + 1 : maxZ - minZ + 1;
        }

        public int depth() {
            return front.getAxis() == Direction.Axis.Z ? maxZ - minZ + 1 : maxX - minX + 1;
        }

        public double centerX() {
            return (minX + maxX + 1) / 2.0;
        }

        public double centerZ() {
            return (minZ + maxZ + 1) / 2.0;
        }
    }
}
