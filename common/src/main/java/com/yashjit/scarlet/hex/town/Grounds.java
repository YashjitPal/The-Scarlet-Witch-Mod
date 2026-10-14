package com.yashjit.scarlet.hex.town;

import static com.yashjit.scarlet.hex.town.Houses.AIR;
import static com.yashjit.scarlet.hex.town.Houses.CAP;
import static com.yashjit.scarlet.hex.town.Houses.CAULDRON;
import static com.yashjit.scarlet.hex.town.Houses.FENCE;
import static com.yashjit.scarlet.hex.town.Houses.FILL_DEEP;
import static com.yashjit.scarlet.hex.town.Houses.FLOWER;
import static com.yashjit.scarlet.hex.town.Houses.FULL;
import static com.yashjit.scarlet.hex.town.Houses.GARDEN_TICKS;
import static com.yashjit.scarlet.hex.town.Houses.LEAVES;
import static com.yashjit.scarlet.hex.town.Houses.gate;
import static com.yashjit.scarlet.hex.town.Houses.lantern;
import static com.yashjit.scarlet.hex.town.Houses.shadeTree;
import static com.yashjit.scarlet.hex.town.Houses.slab;
import static com.yashjit.scarlet.hex.town.Houses.stairs;
import static com.yashjit.scarlet.hex.town.TownPlan.LOT_DEPTH;
import static com.yashjit.scarlet.hex.town.TownPlan.LOT_WIDTH;

import com.yashjit.scarlet.hex.town.Blueprint.Frame;
import java.util.Arrays;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * What the town makes of land a house won't go on, so that none inside the Hex is left as it was, and of the odd lot
 * among the houses: a pond kept as a park with a boardwalk out over it, the edge of a drop made a lookout, a slope
 * laid in terraces, and ball courts and picnic groves, which also go on lots their street passes high over or far
 * under. Each goes up like a garden: the land first, then its paths and walls, then what stands on it.
 */
final class Grounds {

    private static final int LAND = 0;
    private static final int PATHS = 1;
    private static final int THINGS = 2;
    private static final BlockState BARS = Blocks.IRON_BARS.defaultBlockState();
    /**
     * How many rows back each of a terraced garden's terraces runs, the most its wall may stand over the one below, and
     * the most the first may stand over its sidewalk or under it, its steps through the wall along the street having
     * only its own rows to go in.
     */
    static final int TERRACE = 4;
    static final int TERRACE_WALL = 4;
    private static final int FRONT_WALL = 3;
    /** The most water a boardwalk's posts go down through, and how far apart they stand. */
    private static final int DEEPEST_POST = 12;
    private static final int POST_EVERY = 3;
    /** Where there is no water over a column. */
    static final int DRY = Integer.MIN_VALUE;
    /** Where how high a lot's sidewalk is laid isn't known. */
    static final int NO_STREET = Integer.MIN_VALUE;

    private Grounds() {
    }

    // ---------------------------------------------------------------- the ball court

    /**
     * A ball court: asphalt with lines painted round it, across its middle and round the key under each hoop, a hoop
     * at either end, a chain-link fence round it with a way in from the street, and outside it benches along one side
     * and a lamp at each corner of the lot.
     */
    static Blueprint court(Frame frame, int g, Houses.Heights heights, RandomSource random) {
        Blueprint blueprint = new Blueprint(frame, GARDEN_TICKS);
        Houses.level(blueprint, LOT_WIDTH, LOT_DEPTH, g, heights);
        int middle = LOT_WIDTH / 2;
        int left = 2;
        int right = LOT_WIDTH - 3;
        int front = 2;
        int back = LOT_DEPTH - 2;
        int half = (front + back) / 2;
        for (int a = left; a <= right; a++) {
            for (int b = front; b <= back; b++) {
                boolean line = a == left || a == right || b == front || b == back || b == half;
                blueprint.set(a, b, g, line ? Role.ROAD_LINE : Role.ROAD, FULL, PATHS);
            }
        }
        for (int end : new int[] {front, back}) {
            int inward = end == front ? 1 : -1;
            for (int k = 1; k <= 4; k++) {
                blueprint.set(middle - 2, end + inward * k, g, Role.ROAD_LINE, FULL, PATHS);
                blueprint.set(middle + 2, end + inward * k, g, Role.ROAD_LINE, FULL, PATHS);
            }
            blueprint.fill(middle - 1, middle + 1, end + inward * 4, end + inward * 4, g, g, Role.ROAD_LINE, FULL, PATHS);
        }
        for (int a = left - 1; a <= right + 1; a++) {
            if (Math.abs(a - middle) > 1) {
                blueprint.fill(a, a, front - 1, front - 1, g + 1, g + 2, Role.NET, BARS, PATHS);
            }
            blueprint.fill(a, a, back + 1, back + 1, g + 1, g + 2, Role.NET, BARS, PATHS);
        }
        for (int b = front; b <= back; b++) {
            blueprint.fill(left - 1, left - 1, b, b, g + 1, g + 2, Role.NET, BARS, PATHS);
            blueprint.fill(right + 1, right + 1, b, b, g + 1, g + 2, Role.NET, BARS, PATHS);
        }
        for (int b = 0; b < front; b++) {
            blueprint.set(middle, b, g, Role.WALKWAY, FULL, PATHS);
        }
        // the hoops: a post on each end line, the board on top of it, the hoop out from the board over the court
        for (int end : new int[] {front, back}) {
            int inward = end == front ? 1 : -1;
            blueprint.fill(middle, middle, end, end, g + 1, g + 3, Role.PLAY_FRAME, FULL, THINGS);
            blueprint.fill(middle - 1, middle + 1, end, end, g + 4, g + 5, Role.PLAY_FRAME, FULL, THINGS);
            blueprint.set(middle, end + inward, g + 4, Role.NET, BARS, THINGS);
        }
        for (int b = 5; b <= LOT_DEPTH - 6; b += 4) {
            blueprint.set(LOT_WIDTH - 1, b, g + 1, Role.BENCH, stairs(frame.right(), Half.BOTTOM), THINGS);
        }
        for (int a : new int[] {0, LOT_WIDTH - 1}) {
            for (int b : new int[] {1, LOT_DEPTH - 2}) {
                lamp(blueprint, a, b, g);
            }
        }
        return blueprint;
    }

    // ---------------------------------------------------------------- the picnic grove

    /**
     * A picnic grove: shade trees round a lawn, picnic tables among them with a bench either side, a grill, a walk in
     * from the street between beds of flowers that forks across the grove, a hedge along the street, and a lamp.
     */
    static Blueprint grove(Frame frame, int g, Houses.Heights heights, RandomSource random) {
        Blueprint blueprint = new Blueprint(frame, GARDEN_TICKS);
        Houses.level(blueprint, LOT_WIDTH, LOT_DEPTH, g, heights);
        int middle = LOT_WIDTH / 2;
        int fork = LOT_DEPTH / 2;
        for (int a = 0; a < LOT_WIDTH; a++) {
            if (Math.abs(a - middle) > 1) {
                blueprint.set(a, 0, g + 1, Role.HEDGE, LEAVES, PATHS);
            }
        }
        for (int b = 0; b <= fork; b++) {
            blueprint.set(middle, b, g, Role.WALKWAY, FULL, PATHS);
        }
        blueprint.fill(3, LOT_WIDTH - 4, fork, fork, g, g, Role.WALKWAY, FULL, PATHS);
        int kind = random.nextInt(EraStyle.FLOWERS);
        for (int b = 2; b <= 5; b++) {
            blueprint.set(middle - 1, b, g + 1, Role.FLOWER, kind, FLOWER, THINGS);
            blueprint.set(middle + 1, b, g + 1, Role.FLOWER, kind, FLOWER, THINGS);
        }
        for (int[] tree : new int[][] {{3, 4}, {LOT_WIDTH - 4, 4}, {3, LOT_DEPTH - 5}, {LOT_WIDTH - 4, LOT_DEPTH - 5}, {middle, LOT_DEPTH - 3}}) {
            shadeTree(blueprint, tree[0], tree[1], g, THINGS);
        }
        for (int a : new int[] {5, LOT_WIDTH - 6}) {
            for (int b : new int[] {fork - 3, fork + 3}) {
                picnicTable(blueprint, frame, a, b, g);
            }
        }
        blueprint.set(middle, fork + 3, g + 1, Role.SINK, CAULDRON, THINGS);
        lamp(blueprint, middle + 1, fork - 1, g);
        return blueprint;
    }

    // ---------------------------------------------------------------- the pond

    /**
     * A park round a pond, the water left as it lies: the land round it laid level with the water or above it and
     * turfed, a walk from the street down to the bank, from there a boardwalk on posts out over the water with a rail
     * either side, ending in a deck with a bench and a lamp, and on the bank more benches, a tree and flowers.
     *
     * @param water how high the water over each column comes, or {@link #DRY} where there is none
     */
    static Blueprint pond(Frame frame, int g, Houses.Heights heights, Houses.Heights water, RandomSource random) {
        Blueprint blueprint = new Blueprint(frame, GARDEN_TICKS);
        int highest = DRY;
        for (int b = 0; b < LOT_DEPTH; b++) {
            for (int a = 0; a < LOT_WIDTH; a++) {
                highest = Math.max(highest, water.at(a, b));
            }
        }
        // the bank no lower than the water, for none of it to run out over the land
        int bank = Math.max(g, highest);
        for (int b = 0; b < LOT_DEPTH; b++) {
            for (int a = 0; a < LOT_WIDTH; a++) {
                if (water.at(a, b) == DRY) {
                    turf(blueprint, a, b, bank, heights.at(a, b));
                }
            }
        }
        // the boardwalk goes out from where the water comes nearest the street, near the middle
        int middle = LOT_WIDTH / 2;
        int walk = middle;
        int shore = LOT_DEPTH;
        for (int k = 0; k <= LOT_WIDTH - 7; k++) {
            int a = middle + (k % 2 == 0 ? k / 2 : -(k + 1) / 2);
            for (int b = 1; b < shore; b++) {
                if (water.at(a, b) != DRY) {
                    shore = b;
                    walk = a;
                    break;
                }
            }
        }
        for (int b = 0; b < shore; b++) {
            if (water.at(walk, b) == DRY) {
                blueprint.set(walk, b, bank, Role.WALKWAY, FULL, PATHS);
            }
        }
        lamp(blueprint, walk + 1, 1, bank);
        if (shore < LOT_DEPTH - 3) {
            int end = shore;
            while (end + 1 < LOT_DEPTH - 2 && end - shore < 10 && water.at(walk, end + 1) != DRY) {
                end++;
            }
            int deck = bank;
            for (int b = shore; b <= end + 1; b++) {
                for (int a = walk - 2; a <= walk + 2; a++) {
                    deck = Math.max(deck, water.at(a, b) + 1);
                }
            }
            if (deck > bank) {
                blueprint.set(walk, shore - 1, bank + 1, Role.LANE_DECK_STEP, stairs(frame.back(), Half.BOTTOM), PATHS);
            }
            // out to the end, three boards wide, railed either side
            for (int b = shore; b < end; b++) {
                blueprint.fill(walk - 1, walk + 1, b, b, deck, deck, Role.LANE_DECK, FULL, PATHS);
                blueprint.set(walk - 1, b, deck + 1, Role.LANE_RAIL, FENCE, THINGS);
                blueprint.set(walk + 1, b, deck + 1, Role.LANE_RAIL, FENCE, THINGS);
                if ((b - shore) % POST_EVERY == 0) {
                    post(blueprint, walk - 1, b, deck, heights);
                    post(blueprint, walk + 1, b, deck, heights);
                }
            }
            // the deck at the end, five wide, railed round its sides and far edge
            int far = Math.min(end + 1, LOT_DEPTH - 2);
            blueprint.fill(walk - 2, walk + 2, end - 1, far, deck, deck, Role.LANE_DECK, FULL, PATHS);
            for (int b = end - 1; b <= far; b++) {
                blueprint.set(walk - 2, b, deck + 1, Role.LANE_RAIL, FENCE, THINGS);
                blueprint.set(walk + 2, b, deck + 1, Role.LANE_RAIL, FENCE, THINGS);
            }
            blueprint.fill(walk - 1, walk + 1, far, far, deck + 1, deck + 1, Role.LANE_RAIL, FENCE, THINGS);
            blueprint.clear(walk - 1, end - 1, deck + 1);
            blueprint.clear(walk + 1, end - 1, deck + 1);
            for (int a : new int[] {walk - 2, walk + 2}) {
                for (int b : new int[] {end - 1, far}) {
                    post(blueprint, a, b, deck, heights);
                }
            }
            blueprint.set(walk, far - 1, deck + 1, Role.BENCH, stairs(frame.front(), Half.BOTTOM), THINGS);
            blueprint.set(walk + 2, far, deck + 2, Role.LAMP, lantern(false), THINGS);
        }
        // benches on the bank looking out over the water, a tree and flowers where there is room
        for (int a : new int[] {walk - 3, walk + 3}) {
            int b = Math.min(shore, LOT_DEPTH) - 1;
            if (a >= 0 && a < LOT_WIDTH && b >= 1 && water.at(a, b) == DRY && b + 1 < LOT_DEPTH && water.at(a, b + 1) != DRY) {
                blueprint.set(a, b, bank + 1, Role.BENCH, stairs(frame.front(), Half.BOTTOM), THINGS);
            }
        }
        for (int[] spot : new int[][] {{3, 3}, {LOT_WIDTH - 4, 3}, {3, LOT_DEPTH - 4}, {LOT_WIDTH - 4, LOT_DEPTH - 4}}) {
            if (dry(water, spot[0], spot[1], 2) && Math.abs(spot[0] - walk) > 2) {
                shadeTree(blueprint, spot[0], spot[1], bank, THINGS);
                break;
            }
        }
        int kind = random.nextInt(EraStyle.FLOWERS);
        for (int i = 0; i < 8; i++) {
            int a = random.nextInt(LOT_WIDTH);
            int b = 1 + random.nextInt(LOT_DEPTH - 1);
            if (Math.abs(a - walk) > 2 && dry(water, a, b, 1)) {
                blueprint.set(a, b, bank + 1, Role.FLOWER, kind, FLOWER, THINGS);
            }
        }
        return blueprint;
    }

    // ---------------------------------------------------------------- the lookout

    /**
     * A lookout over a drop: the lot laid level out to the edge, a stone parapet along the edge, benches facing out
     * over it, a telescope on a post to look through, a walk in from the street, a lamp, flowers and a shade tree well
     * back from the edge.
     */
    static Blueprint lookout(Frame frame, int g, Houses.Heights heights, RandomSource random) {
        Blueprint blueprint = new Blueprint(frame, GARDEN_TICKS);
        Houses.level(blueprint, LOT_WIDTH, LOT_DEPTH, g, heights);
        int middle = LOT_WIDTH / 2;
        // the edge: land with a drop beside it, which way it falls away, and every few along it a bench facing out
        int seen = 0;
        int telescope = -1;
        int telescopeAt = Integer.MAX_VALUE;
        Direction[] toward = {frame.right(), frame.left(), frame.back(), frame.front()};
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int b = 0; b < LOT_DEPTH; b++) {
            for (int a = 0; a < LOT_WIDTH; a++) {
                if (falls(heights, a, b, g)) {
                    continue;
                }
                int side = -1;
                for (int s = 0; s < steps.length && side < 0; s++) {
                    if (falls(heights, a + steps[s][0], b + steps[s][1], g)) {
                        side = s;
                    }
                }
                if (side < 0) {
                    continue;
                }
                blueprint.set(a, b, g + 1, Role.RETAINING, FULL, PATHS);
                int ia = a - steps[side][0];
                int ib = b - steps[side][1];
                if (!inLot(ia, ib) || falls(heights, ia, ib, g) || edge(heights, ia, ib, g)) {
                    continue;
                }
                int distance = Math.abs(ia - middle) + Math.abs(ib - LOT_DEPTH / 2);
                if (distance < telescopeAt) {
                    telescopeAt = distance;
                    telescope = (ib * LOT_WIDTH + ia) * 4 + side;
                }
                if (seen++ % 5 == 2) {
                    blueprint.set(ia, ib, g + 1, Role.BENCH, stairs(toward[side].getOpposite(), Half.BOTTOM), THINGS);
                }
            }
        }
        if (telescope >= 0) {
            int side = telescope % 4;
            int ia = telescope / 4 % LOT_WIDTH;
            int ib = telescope / 4 / LOT_WIDTH;
            blueprint.set(ia, ib, g + 1, Role.LAMP_POST, FENCE, THINGS);
            blueprint.set(ia, ib, g + 2, Role.STEP, stairs(toward[side].getOpposite(), Half.TOP), THINGS);
        }
        for (int b = 0; b < LOT_DEPTH && !falls(heights, middle, b, g) && !edge(heights, middle, b, g); b++) {
            blueprint.set(middle, b, g, Role.WALKWAY, FULL, PATHS);
        }
        if (clear(heights, middle + 1, 1, g, 0)) {
            lamp(blueprint, middle + 1, 1, g);
        }
        for (int[] spot : new int[][] {{3, 3}, {LOT_WIDTH - 4, 3}, {3, LOT_DEPTH - 4}, {LOT_WIDTH - 4, LOT_DEPTH - 4}, {middle, LOT_DEPTH - 4}}) {
            if (clear(heights, spot[0], spot[1], g, 3)) {
                shadeTree(blueprint, spot[0], spot[1], g, THINGS);
                break;
            }
        }
        int kind = random.nextInt(EraStyle.FLOWERS);
        for (int a : new int[] {middle - 2, middle - 1, middle + 2, middle + 3}) {
            if (clear(heights, a, 2, g, 1)) {
                blueprint.set(a, 2, g + 1, Role.FLOWER, kind, FLOWER, THINGS);
            }
        }
        return blueprint;
    }

    // ---------------------------------------------------------------- terraces

    /**
     * A garden laid up a slope too steep to level for a house: the land in terraces a few rows deep stepping up or down
     * it, each laid near the middle height of its own land and held up by a stone wall, a flight of steps up the middle
     * from one to the next, a bed of flowers along each, a bench and a tree. The first is laid within a wall's height of
     * the sidewalk, and where it stands over it or under it, a wall runs along the street with a flight of steps through
     * it and a fence along its top; where it doesn't, a picket fence and gate.
     *
     * @param street how high the sidewalk in front of the lot is laid, or {@link #NO_STREET}
     */
    static Blueprint terraces(Frame frame, int street, Houses.Heights heights, RandomSource random) {
        Blueprint blueprint = new Blueprint(frame, GARDEN_TICKS);
        int count = (LOT_DEPTH + TERRACE - 1) / TERRACE;
        int[] levels = new int[count];
        int[] row = new int[LOT_WIDTH * TERRACE];
        for (int t = 0; t < count; t++) {
            int n = 0;
            for (int b = t * TERRACE; b < Math.min(LOT_DEPTH, (t + 1) * TERRACE); b++) {
                for (int a = 0; a < LOT_WIDTH; a++) {
                    row[n++] = heights.at(a, b);
                }
            }
            Arrays.sort(row, 0, n);
            int middle = row[n / 2];
            int from = t > 0 ? levels[t - 1] : street != NO_STREET ? street : middle;
            int wall = t > 0 ? TERRACE_WALL : FRONT_WALL;
            levels[t] = Math.clamp(middle, from - wall, from + wall);
        }
        int middle = LOT_WIDTH / 2;
        // where its edges are raised over the land past them, they are walled in stone rather than left as bare earth
        for (int b = 0; b < LOT_DEPTH; b++) {
            int level = levels[b / TERRACE];
            for (int a = 0; a < LOT_WIDTH; a++) {
                boolean edge = b == LOT_DEPTH - 1 || a == 0 || a == LOT_WIDTH - 1;
                turf(blueprint, a, b, level, heights.at(a, b), edge ? Role.RETAINING : Role.FILL);
            }
            blueprint.set(middle, b, level, Role.WALKWAY, FULL, PATHS);
        }
        // a bed of flowers along each terrace
        for (int t = 0; t < count; t++) {
            int b = Math.min(LOT_DEPTH - 1, t * TERRACE + 2);
            int kind = random.nextInt(EraStyle.FLOWERS);
            for (int a = 1; a < LOT_WIDTH - 1; a++) {
                if (Math.abs(a - middle) > 1 && (a + t) % 3 != 0) {
                    blueprint.set(a, b, levels[t] + 1, Role.FLOWER, kind, FLOWER, THINGS);
                }
            }
        }
        // the wall along the street, its top level with the sidewalk where the lot lies under it; the steps up through it
        // cut into the first terrace and lined either side, or down from its top
        int front = street != NO_STREET ? street : levels[0];
        int lastStep = -1;
        if (front != levels[0]) {
            boolean climbs = levels[0] > front;
            int lower = Math.min(front, levels[0]);
            int upper = Math.max(front, levels[0]);
            int rise = upper - lower;
            blueprint.fill(0, LOT_WIDTH - 1, 0, 0, lower + 1, upper, Role.RETAINING, FULL, PATHS);
            for (int k = 1; k <= rise; k++) {
                int b = climbs ? k - 1 : k;
                int y = climbs ? front + k : front - k;
                if (climbs) {
                    blueprint.fill(middle - 1, middle - 1, b, b, lower + 1, upper, Role.RETAINING, FULL, PATHS);
                    blueprint.fill(middle + 1, middle + 1, b, b, lower + 1, upper, Role.RETAINING, FULL, PATHS);
                    blueprint.set(middle - 1, b, upper + 1, Role.PICKET, FENCE, PATHS);
                    blueprint.set(middle + 1, b, upper + 1, Role.PICKET, FENCE, PATHS);
                    blueprint.fill(middle, middle, b, b, y + 1, upper + 2, Role.CLEAR, AIR, PATHS);
                }
                blueprint.fill(middle, middle, b, b, lower + 1, y - 1, Role.RETAINING, FULL, PATHS);
                blueprint.set(middle, b, y, Role.STEP, stairs(climbs ? frame.back() : frame.front(), Half.BOTTOM), PATHS);
            }
            lastStep = climbs ? rise - 1 : rise;
        }
        // the walls between them, a hedge along the top of each, and the steps up or down through each, one flight going
        // on from the last, or off to one side where the flight down into a terrace leaves no room for the one up out of it
        for (int t = 1; t < count; t++) {
            int lower = Math.min(levels[t - 1], levels[t]);
            int upper = Math.max(levels[t - 1], levels[t]);
            if (upper == lower) {
                continue;
            }
            boolean climbs = levels[t] > levels[t - 1];
            int wall = climbs ? t * TERRACE : t * TERRACE - 1;
            blueprint.fill(0, LOT_WIDTH - 1, wall, wall, lower + 1, upper, Role.RETAINING, FULL, PATHS);
            int rise = upper - lower;
            int a = climbs && wall - rise <= lastStep ? middle + 3 : middle;
            for (int x = 0; x < LOT_WIDTH; x++) {
                if (x != a) {
                    blueprint.set(x, wall, upper + 1, Role.HEDGE, LEAVES, THINGS);
                }
            }
            for (int k = 1; k <= rise; k++) {
                int b = climbs ? wall - rise + k - 1 : wall + k;
                int y = climbs ? lower + k : upper - k;
                blueprint.fill(a, a, b, b, lower + 1, y - 1, Role.RETAINING, FULL, PATHS);
                blueprint.set(a, b, y, Role.STEP, stairs(climbs ? frame.back() : frame.front(), Half.BOTTOM), PATHS);
            }
            lastStep = climbs ? wall - 1 : wall + rise;
        }
        // the fence on the street, open where the steps go through the wall, a bench and a tree
        int open = front == levels[0] ? -1 : levels[0] > front ? 1 : 0;
        for (int a = 0; a < LOT_WIDTH; a++) {
            if (Math.abs(a - middle) <= open) {
                continue;
            }
            boolean gate = a == middle;
            blueprint.set(a, 0, Math.max(front, levels[0]) + 1, gate ? Role.GATE : Role.PICKET, gate ? gate(frame.back()) : FENCE, PATHS);
        }
        int bench = Math.min(count - 1, 2);
        blueprint.set(middle - 2, bench * TERRACE + 1, levels[bench] + 1, Role.BENCH, stairs(frame.left(), Half.BOTTOM), THINGS);
        int last = count - 1;
        shadeTree(blueprint, LOT_WIDTH - 4, Math.min(LOT_DEPTH - 3, last * TERRACE + 1), levels[last], THINGS);
        return blueprint;
    }

    // ---------------------------------------------------------------- shared

    /** One column of a lot laid at a height: ground above it cut away, a hollow under it filled, a hole capped, turfed. */
    static void turf(Blueprint blueprint, int a, int b, int g, int height) {
        turf(blueprint, a, b, g, height, Role.FILL);
    }

    /**
     * @param fill what a hollow under it is filled with
     */
    private static void turf(Blueprint blueprint, int a, int b, int g, int height, Role fill) {
        if (height > g) {
            blueprint.fill(a, a, b, b, g + 1, height + 2, Role.CLEAR, AIR, LAND);
        } else {
            blueprint.fill(a, a, b, b, g + 1, g + 2, Role.CLEAR, AIR, LAND);
            if (height < g) {
                blueprint.fill(a, a, b, b, g - height > FILL_DEEP ? g - CAP : height + 1, g - 1, fill, FULL, LAND);
            }
        }
        blueprint.set(a, b, g, Role.LAWN, FULL, LAND);
    }

    /** A picnic table: a board on a post, a bench either side of it facing across it. */
    private static void picnicTable(Blueprint blueprint, Frame frame, int a, int b, int g) {
        blueprint.set(a, b, g + 1, Role.TABLE, slab(SlabType.TOP), THINGS);
        blueprint.set(a, b - 1, g + 1, Role.BENCH, stairs(frame.front(), Half.BOTTOM), THINGS);
        blueprint.set(a, b + 1, g + 1, Role.BENCH, stairs(frame.back(), Half.BOTTOM), THINGS);
    }

    private static void lamp(Blueprint blueprint, int a, int b, int g) {
        blueprint.fill(a, a, b, b, g + 1, g + 3, Role.LAMP_POST, FENCE, THINGS);
        blueprint.set(a, b, g + 4, Role.LAMP, lantern(false), THINGS);
    }

    /** A boardwalk's post, from under its boards down to the bed of the water. */
    private static void post(Blueprint blueprint, int a, int b, int deck, Houses.Heights heights) {
        int bed = Math.max(heights.at(a, b) + 1, deck - DEEPEST_POST);
        if (bed < deck) {
            blueprint.fill(a, a, b, b, bed, deck - 1, Role.LANE_PIER, FULL, PATHS);
        }
    }

    private static boolean inLot(int a, int b) {
        return a >= 0 && a < LOT_WIDTH && b >= 0 && b < LOT_DEPTH;
    }

    /** Whether a column of the lot is over a drop, too far down to fill; never one past the lot, which isn't looked at. */
    private static boolean falls(Houses.Heights heights, int a, int b, int g) {
        return inLot(a, b) && g - heights.at(a, b) > FILL_DEEP;
    }

    /** Whether a column has a drop beside it. */
    private static boolean edge(Houses.Heights heights, int a, int b, int g) {
        return falls(heights, a + 1, b, g) || falls(heights, a - 1, b, g) || falls(heights, a, b + 1, g) || falls(heights, a, b - 1, g);
    }

    /** Whether nothing falls away within a reach of a column, nor past the lot's sides. */
    private static boolean clear(Houses.Heights heights, int a, int b, int g, int reach) {
        for (int db = -reach - 1; db <= reach + 1; db++) {
            for (int da = -reach - 1; da <= reach + 1; da++) {
                if (Math.abs(da) <= reach && Math.abs(db) <= reach && !inLot(a + da, b + db) || falls(heights, a + da, b + db, g)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether there is no water within a reach of a column, nor is it past the lot's sides. */
    private static boolean dry(Houses.Heights water, int a, int b, int reach) {
        for (int db = -reach; db <= reach; db++) {
            for (int da = -reach; da <= reach; da++) {
                if (!inLot(a + da, b + db) || water.at(a + da, b + db) != DRY) {
                    return false;
                }
            }
        }
        return true;
    }
}
