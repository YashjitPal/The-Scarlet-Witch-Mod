package com.yashjit.scarlet.hex.town;

import static com.yashjit.scarlet.hex.town.Houses.BASE;
import static com.yashjit.scarlet.hex.town.Houses.EXTERIOR;
import static com.yashjit.scarlet.hex.town.Houses.FENCE;
import static com.yashjit.scarlet.hex.town.Houses.FLOWER;
import static com.yashjit.scarlet.hex.town.Houses.FRAME;
import static com.yashjit.scarlet.hex.town.Houses.FULL;
import static com.yashjit.scarlet.hex.town.Houses.GROUND;
import static com.yashjit.scarlet.hex.town.Houses.INTERIOR;
import static com.yashjit.scarlet.hex.town.Houses.ROOF;
import static com.yashjit.scarlet.hex.town.Houses.STAGE_TICKS;
import static com.yashjit.scarlet.hex.town.Houses.WALLS;
import static com.yashjit.scarlet.hex.town.Houses.YARD;
import static com.yashjit.scarlet.hex.town.Houses.gate;
import static com.yashjit.scarlet.hex.town.Houses.pillar;
import static com.yashjit.scarlet.hex.town.Houses.sign;
import static com.yashjit.scarlet.hex.town.Houses.slab;
import static com.yashjit.scarlet.hex.town.Houses.stairs;
import static com.yashjit.scarlet.hex.town.TownPlan.LOT_DEPTH;
import static com.yashjit.scarlet.hex.town.TownPlan.LOT_WIDTH;

import com.yashjit.scarlet.hex.town.Blueprint.Frame;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * A Hex that is an orchard rather than a town: the caster's farmhouse among rows of fruit trees in blossom, country
 * lanes of packed earth between them, and where the town square would be, a farmyard with a red barn, hay, a trough
 * and a farm stand by the lane. Whatever trees already stood there are kept, turned into fruit trees.
 */
final class Orchard {

    private static final BlockState BLOSSOM = Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    private static final int[] PLOT_TICKS = {8, 10, 30};
    /** Where a plot's rows of trees begin, and how far apart its trees stand, across and back. */
    private static final int ROW_FROM = 2;
    private static final int ROW_BACK = 3;
    private static final int ROW_ACROSS = 3;
    private static final int ROW_ALONG = 4;

    private Orchard() {
    }

    /**
     * A plot of fruit trees in rows, the grass between them dotted with wildflowers, a rail fence along the lane with
     * a gap to walk in by, and now and then a hay bale. Nothing goes where a tree already stands.
     *
     * @param occupied which columns a tree or a building already stands in, by b * width + a
     */
    static Blueprint plot(Frame frame, int g, Houses.Heights heights, RandomSource random, boolean[] occupied) {
        Blueprint blueprint = new Blueprint(frame, PLOT_TICKS);
        level(blueprint, g, heights, occupied);
        for (int a = ROW_FROM; a < LOT_WIDTH - 1; a += ROW_ACROSS) {
            for (int b = ROW_BACK; b < LOT_DEPTH - 2; b += ROW_ALONG) {
                if (!near(occupied, a, b)) {
                    fruitTree(blueprint, a, b, g, random, 2);
                }
            }
        }
        flowers(blueprint, g, random, occupied, 6);
        lane(blueprint, g, occupied);
        if (random.nextFloat() < 0.4F) {
            int a = random.nextBoolean() ? 1 : LOT_WIDTH - 2;
            if (!occupied[(LOT_DEPTH - 2) * LOT_WIDTH + a]) {
                blueprint.set(a, LOT_DEPTH - 2, g + 1, Role.HAY, FULL, 2);
            }
        }
        return blueprint;
    }

    /**
     * Now and then a meadow instead: wildflowers all over, hay bales, and a bench under a single fruit tree.
     */
    static Blueprint meadow(Frame frame, int g, Houses.Heights heights, RandomSource random, boolean[] occupied) {
        Blueprint blueprint = new Blueprint(frame, PLOT_TICKS);
        level(blueprint, g, heights, occupied);
        int a0 = LOT_WIDTH / 2 + 1;
        int b0 = LOT_DEPTH / 2;
        if (!near(occupied, a0, b0)) {
            fruitTree(blueprint, a0, b0, g, random, 2);
            blueprint.set(a0 - 2, b0, g + 1, Role.BENCH, stairs(frame.left(), Half.BOTTOM), 2);
        }
        flowers(blueprint, g, random, occupied, 16);
        for (int i = 0; i < 3; i++) {
            int a = 1 + random.nextInt(LOT_WIDTH - 2);
            int b = 2 + random.nextInt(LOT_DEPTH - 3);
            if (!near(occupied, a, b)) {
                blueprint.set(a, b, g + 1, Role.HAY, FULL, 2);
            }
        }
        lane(blueprint, g, occupied);
        return blueprint;
    }

    /**
     * A small fruit tree: a short trunk under a round crown of leaves, flecked with blossom.
     */
    static void fruitTree(Blueprint blueprint, int a, int b, int g, RandomSource random, int stage) {
        for (int dy = 2; dy <= 4; dy++) {
            int spread = dy == 4 ? 1 : 2;
            for (int db = -spread; db <= spread; db++) {
                for (int da = -spread; da <= spread; da++) {
                    if (Math.abs(da) + Math.abs(db) > (dy == 3 ? 3 : spread)) {
                        continue;
                    }
                    boolean blossom = random.nextFloat() < 0.35F;
                    blueprint.set(a + da, b + db, g + dy + 1, blossom ? Role.ORCHARD_BLOSSOM : Role.ORCHARD_LEAVES, BLOSSOM, stage);
                }
            }
        }
        blueprint.fill(a, a, b, b, g + 1, g + 3, Role.TREE_LOG, pillar(Direction.Axis.Y), stage);
    }

    /**
     * A country lane: packed earth down the middle, grass either side.
     */
    static Blueprint lane(Frame frame, int length, int g, Houses.Heights heights) {
        Blueprint blueprint = new Blueprint(frame, 6, 10);
        Houses.level(blueprint, length, TownPlan.STREET_WIDTH, g, heights);
        for (int a = 0; a < length; a++) {
            blueprint.fill(a, a, 2, 4, g, g, Role.LANE, FULL, 1);
        }
        return blueprint;
    }

    /**
     * Where two lanes cross.
     */
    static Blueprint crossing(Frame frame, int g, Houses.Heights heights) {
        Blueprint blueprint = new Blueprint(frame, 6, 10);
        int size = TownPlan.STREET_WIDTH;
        Houses.level(blueprint, size, size, g, heights);
        for (int a = 0; a < size; a++) {
            blueprint.fill(a, a, 2, 4, g, g, Role.LANE, FULL, 1);
            blueprint.fill(2, 4, a, a, g, g, Role.LANE, FULL, 1);
        }
        return blueprint;
    }

    /**
     * The farmyard, where the town square would be: a red barn with its doors open and hay inside, more hay stacked
     * by a fenced paddock with a trough, a farm stand by the lane with its sign, and a few fruit trees.
     */
    static Blueprint farmyard(Frame frame, int width, int depth, int g, Houses.Heights heights, RandomSource random) {
        Blueprint blueprint = new Blueprint(frame, STAGE_TICKS);
        Houses.level(blueprint, width, depth, g, heights);
        int middle = width / 2;
        int left = middle - 5;
        int right = middle + 5;
        int front = 3;
        int back = depth - 2;
        int top = g + 5;
        // the barn
        blueprint.fill(left, right, front, back, g, g + 1, Role.FOUNDATION, FULL, BASE);
        blueprint.fill(left + 1, right - 1, front + 1, back - 1, g + 1, g + 1, Role.FLOOR, FULL, BASE);
        for (int y = g + 2; y <= top; y++) {
            for (int a = left; a <= right; a++) {
                blueprint.set(a, front, y, Role.BARN_WALL, FULL, WALLS);
                blueprint.set(a, back, y, Role.BARN_WALL, FULL, WALLS);
            }
            for (int b = front + 1; b < back; b++) {
                blueprint.set(left, b, y, Role.BARN_WALL, FULL, WALLS);
                blueprint.set(right, b, y, Role.BARN_WALL, FULL, WALLS);
            }
        }
        for (int a = middle - 1; a <= middle + 1; a++) {
            blueprint.fill(a, a, front, front, g + 2, g + 4, Role.CLEAR, Houses.AIR, WALLS);
        }
        for (int a : new int[] {middle - 2, middle + 2}) {
            blueprint.fill(a, a, front, front, g + 2, top, Role.TRIM, pillar(Direction.Axis.Y), FRAME);
        }
        blueprint.fill(middle - 1, middle + 1, front, front, top, top, Role.TRIM, pillar(frame.acrossAxis()), FRAME);
        // its tall roof, ridge running back from the doors
        for (int a = left - 1; a <= right + 1; a++) {
            int rise = Math.min(a - (left - 1), right + 1 - a);
            int y = top + 1 + rise;
            for (int b = front - 1; b <= back + 1; b++) {
                if (a < middle) {
                    blueprint.set(a, b, y, Role.ROOF, stairs(frame.right(), Half.BOTTOM), ROOF);
                } else if (a > middle) {
                    blueprint.set(a, b, y, Role.ROOF, stairs(frame.left(), Half.BOTTOM), ROOF);
                } else {
                    blueprint.set(a, b, y - 1, Role.ROOF_RIDGE, FULL, ROOF);
                    blueprint.set(a, b, y, Role.ROOF_SLAB, slab(SlabType.BOTTOM), ROOF);
                }
            }
            if (a >= left && a <= right) {
                int under = a == middle ? y - 2 : y - 1;
                for (int b : new int[] {front, back}) {
                    blueprint.fill(a, a, b, b, top + 1, under, Role.BARN_WALL, FULL, ROOF);
                }
            }
        }
        // the hayloft door over the barn doors
        blueprint.fill(middle, middle, front, front, top + 2, top + 3, Role.CLEAR, Houses.AIR, ROOF);
        blueprint.fill(left + 1, left + 2, back - 2, back - 1, g + 2, g + 3, Role.HAY, FULL, INTERIOR);
        blueprint.fill(right - 2, right - 1, back - 2, back - 1, g + 2, g + 2, Role.HAY, FULL, INTERIOR);
        // the paddock, with its trough and hay
        int paddockRight = Math.max(4, left - 3);
        for (int a = 1; a <= paddockRight; a++) {
            if (a == (1 + paddockRight) / 2) {
                blueprint.set(a, 5, g + 1, Role.GATE, gate(frame.back()), EXTERIOR);
            } else {
                blueprint.set(a, 5, g + 1, Role.PICKET, FENCE, EXTERIOR);
            }
            blueprint.set(a, back, g + 1, Role.PICKET, FENCE, EXTERIOR);
        }
        for (int b = 5; b <= back; b++) {
            blueprint.set(1, b, g + 1, Role.PICKET, FENCE, EXTERIOR);
            blueprint.set(paddockRight, b, g + 1, Role.PICKET, FENCE, EXTERIOR);
        }
        blueprint.set(3, back - 2, g + 1, Role.SINK, Blocks.CAULDRON.defaultBlockState(), EXTERIOR);
        blueprint.set(4, back - 2, g + 1, Role.SINK, Blocks.CAULDRON.defaultBlockState(), EXTERIOR);
        blueprint.fill(paddockRight + 1, paddockRight + 1, 2, 3, g + 1, g + 1, Role.HAY, FULL, EXTERIOR);
        blueprint.set(paddockRight + 1, 2, g + 2, Role.HAY, FULL, EXTERIOR);
        // the farm stand by the lane
        int stand = right + 4;
        if (stand + 4 < width) {
            blueprint.fill(stand + 1, stand + 3, 2, 2, g + 1, g + 1, Role.COUNTER, FULL, EXTERIOR);
            for (int a : new int[] {stand, stand + 4}) {
                for (int b : new int[] {1, 3}) {
                    blueprint.fill(a, a, b, b, g + 1, g + 2, Role.PORCH_POST, FENCE, EXTERIOR);
                }
            }
            blueprint.fill(stand, stand + 4, 1, 3, g + 3, g + 3, Role.ROOF_SLAB, slab(SlabType.TOP), EXTERIOR);
            blueprint.set(stand + 2, 1, g + 1, Role.SIGN, Signs.FARM_STAND, sign(frame.front()), YARD);
            blueprint.set(stand + 1, 2, g + 2, Role.PLANT, Blocks.POTTED_FERN.defaultBlockState(), YARD);
            blueprint.set(stand + 3, 2, g + 2, Role.PLANT, Blocks.POTTED_FERN.defaultBlockState(), YARD);
        }
        // the path up to the barn doors, and fruit trees about the yard
        for (int a = middle - 1; a <= middle + 1; a++) {
            blueprint.fill(a, a, 0, front - 1, g, g, Role.LANE, FULL, GROUND);
        }
        fruitTree(blueprint, 3, 2, g, random, YARD);
        if (width - 4 > right + 2) {
            fruitTree(blueprint, width - 4, back - 2, g, random, YARD);
        }
        return blueprint;
    }

    // ---------------------------------------------------------------- shared

    /**
     * Levels and turfs a plot, except where a tree or a building already stands.
     */
    private static void level(Blueprint blueprint, int g, Houses.Heights heights, boolean[] occupied) {
        for (int b = 0; b < LOT_DEPTH; b++) {
            for (int a = 0; a < LOT_WIDTH; a++) {
                if (occupied[b * LOT_WIDTH + a]) {
                    continue;
                }
                int height = heights.at(a, b);
                if (height > g) {
                    blueprint.fill(a, a, b, b, g + 1, height + 2, Role.CLEAR, Houses.AIR, GROUND);
                } else {
                    blueprint.fill(a, a, b, b, g + 1, g + 2, Role.CLEAR, Houses.AIR, GROUND);
                    if (height < g) {
                        blueprint.fill(a, a, b, b, height + 1, g - 1, Role.FILL, FULL, GROUND);
                    }
                }
                blueprint.set(a, b, g, Role.LAWN, FULL, GROUND);
            }
        }
    }

    /**
     * A rail fence along the lane, with a gap in the middle to walk in by.
     */
    private static void lane(Blueprint blueprint, int g, boolean[] occupied) {
        for (int a = 0; a < LOT_WIDTH; a++) {
            if (a != LOT_WIDTH / 2 && !occupied[a]) {
                blueprint.set(a, 0, g + 1, Role.PICKET, FENCE, 1);
            }
        }
    }

    private static void flowers(Blueprint blueprint, int g, RandomSource random, boolean[] occupied, int count) {
        for (int i = 0; i < count; i++) {
            int a = 1 + random.nextInt(LOT_WIDTH - 2);
            int b = 1 + random.nextInt(LOT_DEPTH - 2);
            boolean tree = (a - ROW_FROM) % ROW_ACROSS == 0 && (b - ROW_BACK) % ROW_ALONG == 0;
            if (!occupied[b * LOT_WIDTH + a] && !tree) {
                blueprint.set(a, b, g + 1, Role.FLOWER, random.nextInt(EraStyle.FLOWERS), FLOWER, 1);
            }
        }
    }

    /**
     * Whether anything already stands within a tree's reach of a spot.
     */
    private static boolean near(boolean[] occupied, int a, int b) {
        for (int db = -2; db <= 2; db++) {
            for (int da = -2; da <= 2; da++) {
                int x = a + da;
                int y = b + db;
                if (x >= 0 && x < LOT_WIDTH && y >= 0 && y < LOT_DEPTH && occupied[y * LOT_WIDTH + x]) {
                    return true;
                }
            }
        }
        return false;
    }
}
