package com.yashjit.scarlet.hex.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yashjit.scarlet.hex.HexShape;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where everything in a Hex's town goes: a suburban grid laid out from where the Hex was cast.
 *
 * <p>The caster stands on the sidewalk of the main street with their home in front of them, facing back at them, and
 * the town square across the street behind them. Streets run parallel to the main street every {@value #STREET_PERIOD}
 * blocks with a row of lots on each side, and cross streets cut through after every three lots.
 *
 * <p>Positions on a grid are (u, v): v runs from the caster toward their home, u to its right.
 *
 * <p>Where the Hex covers ground a town its caster raised before stood on, that town comes back there, on its own grid
 * just as it was laid out: each town a layer of the plan.
 */
public final class TownPlan {

    public static final int LOT_WIDTH = 17;
    public static final int LOT_DEPTH = 19;
    public static final int STREET_WIDTH = 7;
    /** From one street to the next: the street and a row of lots on each side of it. */
    public static final int STREET_PERIOD = STREET_WIDTH + 2 * LOT_DEPTH;
    /** Three lots and a cross street. */
    public static final int BLOCK_PERIOD = 3 * LOT_WIDTH + STREET_WIDTH;
    /** How tall the tallest house stands over its lot, for the open air it needs. */
    public static final int HOUSE_HEIGHT = 22;
    /** Where in a part's id the layer it is in goes, above what it is and where on its grid. */
    static final int LAYER_SHIFT = 27;
    /** Most towns one plan holds, its own and those it brings back. */
    private static final int MAX_LAYERS = 16;
    /** How far back into its lot from the street a house's front wall stands, behind its lawn and porch. */
    public static final int HOME_FRONT = 6;
    /** How far back into its lot from the street a home's living room is, where its caster floats as it rises. */
    public static final int LIVING_ROOM = HOME_FRONT + 4;
    /** Where on its grid a home raised off it is, in its id: past any street or block a grid lays out. */
    private static final int OFF_GRID = 0xFF << 16 | 0xFF << 8;
    /** Where on its grid a town's own home lot is, in its id, for a home raised off it to look just the same. */
    private static final int HOME_LOT = 64 << 16 | 64 << 8 | 8 << 1;

    /**
     * A town's grid: where it is laid out from, which way its main street runs, and the seed its lots are drawn from.
     * The same grid lays out the same town every time.
     */
    public record Grid(BlockPos origin, Direction forward, long seed) {
    }

    /**
     * Where a caster raised their home again inside their Hex, off their town's grid: the middle of its lot and the
     * way its front faces. Whatever else the town had there gives way to it, and the lot it had stood on takes a house
     * like any other.
     */
    public record HomeLot(int x, int z, Direction front) {

        public static final Codec<HomeLot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x").forGetter(HomeLot::x),
                Codec.INT.fieldOf("z").forGetter(HomeLot::z),
                Direction.CODEC.fieldOf("front").forGetter(HomeLot::front)
        ).apply(i, (x, z, front) -> new HomeLot(x, z, front.getAxis().isHorizontal() ? front : Direction.NORTH)));

        private boolean wide() {
            return front.getAxis() == Direction.Axis.Z;
        }

        public int minX() {
            return x - (wide() ? LOT_WIDTH : LOT_DEPTH) / 2;
        }

        public int maxX() {
            return minX() + (wide() ? LOT_WIDTH : LOT_DEPTH) - 1;
        }

        public int minZ() {
            return z - (wide() ? LOT_DEPTH : LOT_WIDTH) / 2;
        }

        public int maxZ() {
            return minZ() + (wide() ? LOT_DEPTH : LOT_WIDTH) - 1;
        }

        /**
         * Where in it its caster's living room is, on the ground at {@code y}: where they float as it rises around them.
         */
        public Vec3 livingRoom(int y) {
            Direction back = front.getOpposite();
            Direction right = back.getClockWise();
            Blueprint.Frame frame = new Blueprint.Frame(new BlockPos(x - right.getStepX() * (LOT_WIDTH / 2) - back.getStepX() * (LOT_DEPTH / 2), y,
                    z - right.getStepZ() * (LOT_WIDTH / 2) - back.getStepZ() * (LOT_DEPTH / 2)), front);
            return Vec3.atBottomCenterOf(frame.at(LOT_WIDTH / 2, LIVING_ROOM, y));
        }
    }

    private final List<Grid> grids;
    private final List<Part> parts;

    private TownPlan(List<Grid> grids, List<Part> parts) {
        this.grids = List.copyOf(grids);
        this.parts = Collections.unmodifiableList(parts);
    }

    /**
     * A town's whole plan by itself, from its middle out as far as a Hex of the given size could reach: just as it was
     * first laid out.
     */
    public static TownPlan whole(Grid grid, float maxRadius) {
        List<Part> laid = new ArrayList<>();
        layOut(grid, 0, 0.0, 0.0, maxRadius, laid, part -> true, false);
        laid.sort(Comparator.comparingDouble(Part::distanceSqr));
        return new TownPlan(List.of(grid), laid);
    }

    /**
     * What a Hex lays out: its own town, as far out as it could ever reach, except on ground a town its caster raised
     * before stood on, where that town comes back just as it was. On ground more than one stood on, the first of them
     * does. A lot or street that would straddle where one town gives way to another is left out, and the land there
     * stays as it is. Wherever a caster raised their home off its grid, it stands there instead of whatever else would.
     *
     * @param ownHome    where the Hex's own town has its home off its grid, if it does
     * @param remembered the towns its caster raised before with the same build, oldest first
     * @param focus      the Hex's middle, which the plan is laid out around, nearest first
     */
    public static TownPlan around(Grid own, @Nullable HomeLot ownHome, List<TownMemory> remembered, Vec3 focus, float maxRadius) {
        List<Grid> grids = new ArrayList<>();
        List<HomeLot> homes = new ArrayList<>();
        grids.add(own);
        homes.add(ownHome);
        for (TownMemory town : remembered) {
            Grid grid = town.grid();
            if (!grids.contains(grid) && grids.size() < MAX_LAYERS) {
                grids.add(grid);
                homes.add(town.homeLot().orElse(null));
            }
        }
        List<Part> laid = new ArrayList<>();
        for (int layer = 0; layer < grids.size(); layer++) {
            Grid grid = grids.get(layer);
            Direction forward = grid.forward();
            Direction right = forward.getClockWise();
            // the Hex's middle on this town's grid
            double dx = focus.x - (grid.origin().getX() + 0.5);
            double dz = focus.z - (grid.origin().getZ() + 0.5);
            double u = dx * right.getStepX() + dz * right.getStepZ();
            double v = dx * forward.getStepX() + dz * forward.getStepZ();
            int at = layer;
            layOut(grid, layer, u, v, maxRadius, laid, remembered.isEmpty() ? part -> true : part -> belongs(part, at, grids, remembered),
                    homes.get(layer) != null);
        }
        List<Part> raised = new ArrayList<>();
        for (int layer = 0; layer < grids.size(); layer++) {
            HomeLot lot = homes.get(layer);
            if (lot == null) {
                continue;
            }
            Part home = home(grids.get(layer), layer, lot);
            if (raised.stream().noneMatch(other -> overlaps(other, home))) {
                raised.add(home);
            }
        }
        if (!raised.isEmpty()) {
            laid.removeIf(part -> raised.stream().anyMatch(home -> overlaps(part, home)));
            laid.addAll(raised);
        }
        laid.sort(Comparator.comparingDouble(part -> Mth.square(part.centerX() - focus.x) + Mth.square(part.centerZ() - focus.z)));
        return new TownPlan(grids, laid);
    }

    /**
     * A town's home where its caster raised it off its grid.
     */
    public static Part home(Grid grid, int layer, HomeLot lot) {
        return part(grid, layer << LAYER_SHIFT | Kind.HOME.ordinal() << 24 | OFF_GRID, Kind.HOME, lot.minX(), lot.minZ(), lot.maxX(), lot.maxZ(),
                lot.front());
    }

    private static boolean overlaps(Part a, Part b) {
        return a.minX() <= b.maxX() && b.minX() <= a.maxX() && a.minZ() <= b.maxZ() && b.minZ() <= a.maxZ();
    }

    public List<Part> parts() {
        return parts;
    }

    /**
     * The towns of the plan: the Hex's own first, then those it brings back.
     */
    public List<Grid> grids() {
        return grids;
    }

    public Grid grid(int layer) {
        return grids.get(layer);
    }

    /**
     * Lays out the parts of a grid within reach of a point on it.
     *
     * @param u         where the point is across the grid
     * @param v         and along it
     * @param homeMoved whether its home stands off the grid, leaving its lot to a house like any other
     */
    private static void layOut(Grid grid, int layer, double u, double v, float maxRadius, List<Part> laid, Predicate<Part> keep,
                               boolean homeMoved) {
        Direction forward = grid.forward();
        double span = maxRadius * HexShape.CORNER;
        int streetsFrom = Mth.floor((v - span) / STREET_PERIOD) - 1;
        int streetsTo = Mth.ceil((v + span) / STREET_PERIOD) + 1;
        int blocksFrom = Mth.floor((u - span) / BLOCK_PERIOD) - 1;
        int blocksTo = Mth.ceil((u + span) / BLOCK_PERIOD) + 1;
        List<Part> parts = new ArrayList<>();
        for (int s = streetsFrom; s <= streetsTo; s++) {
            int streetFrom = STREET_PERIOD * s - (STREET_WIDTH - 1);
            int streetTo = STREET_PERIOD * s;
            for (int g = blocksFrom; g <= blocksTo; g++) {
                int groupCenter = BLOCK_PERIOD * g;
                for (int k = -1; k <= 1; k++) {
                    int lotFrom = groupCenter + LOT_WIDTH * k - LOT_WIDTH / 2;
                    int lotTo = lotFrom + LOT_WIDTH - 1;
                    // the street in front of this stretch of lots
                    parts.add(part(grid, layer, Kind.STREET, s, g, k, 0, lotFrom, lotTo, streetFrom, streetTo, forward));
                    // the lots facing the street: from beyond it, and from this side of it
                    Kind far = s == 0 && g == 0 && k == 0 && !homeMoved ? Kind.HOME : Kind.HOUSE;
                    parts.add(part(grid, layer, far, s, g, k, 0, lotFrom, lotTo, streetTo + 1, streetTo + LOT_DEPTH, forward.getOpposite()));
                    if (!(s == 0 && g == 0)) {
                        parts.add(part(grid, layer, Kind.HOUSE, s, g, k, 1, lotFrom, lotTo, streetFrom - LOT_DEPTH, streetFrom - 1, forward));
                    }
                }
                // the town square takes the three lots across the main street from the caster's home
                if (s == 0 && g == 0) {
                    parts.add(part(grid, layer, Kind.SQUARE, s, g, 0, 1, groupCenter - LOT_WIDTH * 3 / 2, groupCenter + LOT_WIDTH * 3 / 2,
                            streetFrom - LOT_DEPTH, streetFrom - 1, forward));
                }
                int crossFrom = groupCenter + LOT_WIDTH * 3 / 2 + 1;
                int crossTo = crossFrom + STREET_WIDTH - 1;
                parts.add(part(grid, layer, Kind.CROSSING, s, g, 0, 0, crossFrom, crossTo, streetFrom, streetTo, forward));
                // the cross street, through both rows of lots up to the next street
                parts.add(part(grid, layer, Kind.CROSS_STREET, s, g, 0, 0, crossFrom, crossTo, streetTo + 1, streetTo + LOT_DEPTH,
                        forward.getClockWise()));
                parts.add(part(grid, layer, Kind.CROSS_STREET, s, g, 0, 1, crossFrom, crossTo, streetTo + LOT_DEPTH + 1, streetTo + 2 * LOT_DEPTH,
                        forward.getClockWise()));
            }
        }
        for (Part part : parts) {
            if (keep.test(part)) {
                laid.add(part);
            }
        }
    }

    /**
     * Whether a part of one of the plan's towns goes where it lies: all of it on ground that town was the first to
     * stand on, or for the Hex's own town, on ground no other town stood on first.
     */
    private static boolean belongs(Part part, int layer, List<Grid> grids, List<TownMemory> remembered) {
        Grid grid = grids.get(layer);
        double[] xs = {part.minX() + 0.5, part.centerX(), part.maxX() + 0.5};
        double[] zs = {part.minZ() + 0.5, part.centerZ(), part.maxZ() + 0.5};
        for (double x : xs) {
            for (double z : zs) {
                TownMemory first = TownMemory.first(remembered, x, z);
                boolean ours = first == null ? layer == 0 : first.grid().equals(grid);
                if (!ours) {
                    return false;
                }
            }
        }
        return true;
    }

    private static BlockPos at(Grid grid, int u, int v) {
        Direction forward = grid.forward();
        Direction right = forward.getClockWise();
        BlockPos origin = grid.origin();
        return new BlockPos(origin.getX() + right.getStepX() * u + forward.getStepX() * v, origin.getY(),
                origin.getZ() + right.getStepZ() * u + forward.getStepZ() * v);
    }

    /**
     * @param front which way the part faces: for a lot, from its house toward its street
     */
    private static Part part(Grid grid, int layer, Kind kind, int s, int g, int k, int side, int uFrom, int uTo, int vFrom, int vTo,
                             Direction front) {
        int id = layer << LAYER_SHIFT | kind.ordinal() << 24 | (s + 64) << 16 | (g + 64) << 8 | (k + 8) << 1 | side;
        BlockPos a = at(grid, uFrom, vFrom);
        BlockPos b = at(grid, uTo, vTo);
        return part(grid, id, kind, Math.min(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()),
                Math.max(a.getZ(), b.getZ()), front);
    }

    private static Part part(Grid grid, int id, Kind kind, int minX, int minZ, int maxX, int maxZ, Direction front) {
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
        BlockPos middle = grid.origin();
        double cx = (minX + maxX + 1) / 2.0 - (middle.getX() + 0.5);
        double cz = (minZ + maxZ + 1) / 2.0 - (middle.getZ() + 0.5);
        return new Part(id, kind, minX, minZ, maxX, maxZ, new BlockPos(originX, middle.getY(), originZ), front, cx * cx + cz * cz);
    }

    /**
     * Whether a part's whole footprint lies inside a Hex of the given radius.
     */
    public static boolean fits(Part part, Vec3 center, float radius, int groundY) {
        return fits(part.minX(), part.minZ(), part.maxX(), part.maxZ(), center, radius);
    }

    /**
     * Whether a home raised there would stand all inside a Hex of the given radius.
     */
    public static boolean fits(HomeLot lot, Vec3 center, float radius) {
        return fits(lot.minX(), lot.minZ(), lot.maxX(), lot.maxZ(), center, radius);
    }

    private static boolean fits(int minX, int minZ, int maxX, int maxZ, Vec3 center, float radius) {
        double inset = 0.5;
        for (int corner = 0; corner < 4; corner++) {
            double x = (corner & 1) == 0 ? minX + inset : maxX + 1 - inset;
            double z = (corner & 2) == 0 ? minZ + inset : maxZ + 1 - inset;
            if (!HexShape.contains(center, radius, new Vec3(x, center.y, z))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether any of a part's footprint lies inside a Hex of the given radius, though maybe not all of it: where the
     * wall cuts through it.
     */
    public static boolean touches(Part part, Vec3 center, float radius, int groundY) {
        for (int x = part.minX(); x <= part.maxX(); x += 2) {
            for (int z = part.minZ(); z <= part.maxZ(); z += 2) {
                if (HexShape.contains(center, radius - 0.5F, new Vec3(x + 0.5, groundY, z + 0.5))) {
                    return true;
                }
            }
        }
        return false;
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
     * @param origin      the front-left corner as seen from the street, at the height its town was laid out at
     * @param front       which way it faces: for a lot, from its house toward its street
     * @param distanceSqr how far it is from the middle of its own town
     */
    public record Part(int id, Kind kind, int minX, int minZ, int maxX, int maxZ, BlockPos origin, Direction front, double distanceSqr) {

        /**
         * Which of the plan's towns it is part of: 0 for the Hex's own.
         */
        public int layer() {
            return id >>> LAYER_SHIFT;
        }

        /**
         * Which part of its town's grid it is, the same in any plan its town comes back in.
         */
        public int gridId() {
            return id & (1 << LAYER_SHIFT) - 1;
        }

        /**
         * Whether it is a home its caster raised off its town's grid.
         */
        public boolean offGrid() {
            return (id & OFF_GRID) == OFF_GRID;
        }

        /**
         * What its look is drawn from: which part of its grid it is, or for a home raised off it, its town's own home
         * lot, so it looks just the same wherever it is raised.
         */
        public int lookId() {
            return offGrid() ? Kind.HOME.ordinal() << 24 | HOME_LOT : gridId();
        }

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
