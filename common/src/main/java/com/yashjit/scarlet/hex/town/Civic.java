package com.yashjit.scarlet.hex.town;

import static com.yashjit.scarlet.hex.town.Houses.BASE;
import static com.yashjit.scarlet.hex.town.Houses.EXTERIOR;
import static com.yashjit.scarlet.hex.town.Houses.FENCE;
import static com.yashjit.scarlet.hex.town.Houses.FLOWER;
import static com.yashjit.scarlet.hex.town.Houses.FRAME;
import static com.yashjit.scarlet.hex.town.Houses.FULL;
import static com.yashjit.scarlet.hex.town.Houses.GROUND;
import static com.yashjit.scarlet.hex.town.Houses.INTERIOR;
import static com.yashjit.scarlet.hex.town.Houses.LEAVES;
import static com.yashjit.scarlet.hex.town.Houses.ROOF;
import static com.yashjit.scarlet.hex.town.Houses.STAGE_TICKS;
import static com.yashjit.scarlet.hex.town.Houses.WALLS;
import static com.yashjit.scarlet.hex.town.Houses.YARD;
import static com.yashjit.scarlet.hex.town.Houses.door;
import static com.yashjit.scarlet.hex.town.Houses.gate;
import static com.yashjit.scarlet.hex.town.Houses.lantern;
import static com.yashjit.scarlet.hex.town.Houses.pillar;
import static com.yashjit.scarlet.hex.town.Houses.sign;
import static com.yashjit.scarlet.hex.town.Houses.slab;
import static com.yashjit.scarlet.hex.town.Houses.stairs;
import static com.yashjit.scarlet.hex.town.Houses.window;
import static com.yashjit.scarlet.hex.town.TownPlan.LOT_DEPTH;
import static com.yashjit.scarlet.hex.town.TownPlan.LOT_WIDTH;

import com.yashjit.scarlet.hex.town.Blueprint.Frame;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The town's public places, for a Hex big enough to hold more than houses: playgrounds, the public library, a chapel
 * with its steeple and bell, a one-room schoolhouse, and the shops along main street, each with its awning and its
 * sign. Every one stands on a lot of its own, facing its street, and builds itself stage by stage like a house.
 */
final class Civic {

    private static final BlockState CHAIN = Blocks.IRON_CHAIN.defaultBlockState();
    /** How far in each building stands from the side of its lot, in the middle of it across, and back from its street. */
    private static final int ACROSS = (LOT_WIDTH - 11) / 2;
    private static final int SET_BACK = 3;

    private Civic() {
    }

    // ---------------------------------------------------------------- the playground

    /**
     * Swings, a climbing frame with a slide off it, a sandbox, benches for the parents and a tree to sit under, behind a
     * low hedge with a picket fence and gate on the street.
     */
    static Blueprint playground(Frame frame, int g, Houses.Heights heights, RandomSource random) {
        Blueprint lot = new Blueprint(frame, STAGE_TICKS);
        Houses.level(lot, LOT_WIDTH, LOT_DEPTH, g, heights);
        int middle = LOT_WIDTH / 2;
        for (int b = 1; b < LOT_DEPTH; b++) {
            lot.set(0, b, g + 1, Role.HEDGE, LEAVES, YARD);
            lot.set(LOT_WIDTH - 1, b, g + 1, Role.HEDGE, LEAVES, YARD);
        }
        for (int a = 1; a < LOT_WIDTH - 1; a++) {
            lot.set(a, LOT_DEPTH - 1, g + 1, Role.HEDGE, LEAVES, YARD);
        }
        for (int a = 0; a < LOT_WIDTH; a++) {
            if (a == middle) {
                lot.set(a, 0, g + 1, Role.GATE, gate(frame.back()), YARD);
            } else {
                lot.set(a, 0, g + 1, Role.PICKET, FENCE, YARD);
            }
        }
        for (int b = 0; b <= 2 + SET_BACK; b++) {
            lot.set(middle, b, g, Role.WALKWAY, FULL, GROUND);
        }
        lot.set(LOT_WIDTH - 2, 3, g + 1, Role.BENCH, stairs(frame.right(), Half.BOTTOM), EXTERIOR);
        lot.fill(middle + 2, middle + 2, 1, 1, g + 1, g + 3, Role.LAMP_POST, FENCE, EXTERIOR);
        lot.set(middle + 2, 1, g + 4, Role.LAMP, lantern(false), EXTERIOR);
        // the things to play on, in the middle of the lot
        Blueprint blueprint = lot.shifted(ACROSS, SET_BACK);
        // the sandbox
        blueprint.fill(1, 3, 2, 4, g, g, Role.SAND, FULL, BASE);
        // the swings: a post at either end, a bar over the top, two seats hanging from it
        int swings = 7;
        for (int a : new int[] {1, 5}) {
            blueprint.fill(a, a, swings, swings, g + 1, g + 4, Role.PLAY_FRAME, FULL, FRAME);
        }
        blueprint.fill(2, 4, swings, swings, g + 4, g + 4, Role.PLAY_FRAME, FULL, FRAME);
        for (int a : new int[] {2, 4}) {
            blueprint.set(a, swings, g + 3, Role.CHAIN, CHAIN, WALLS);
            blueprint.set(a, swings, g + 2, Role.TABLE, slab(SlabType.TOP), WALLS);
        }
        // the climbing frame, a cube of bars, with a slide running down off its front
        int ca = 8;
        int cb = 8;
        for (int a = ca - 1; a <= ca + 1; a++) {
            for (int b = cb - 1; b <= cb + 1; b++) {
                for (int y = g + 1; y <= g + 3; y++) {
                    int edges = (a != ca ? 1 : 0) + (b != cb ? 1 : 0) + (y != g + 2 ? 1 : 0);
                    if (edges >= 2) {
                        blueprint.set(a, b, y, Role.PLAY_FRAME, FULL, FRAME);
                    }
                }
            }
        }
        for (int step = 0; step < 3; step++) {
            blueprint.set(ca, cb - 2 - step, g + 3 - step, Role.SLIDE, stairs(frame.back(), Half.BOTTOM), WALLS);
        }
        blueprint.set(1, 5, g + 1, Role.BENCH, stairs(frame.left(), Half.BOTTOM), EXTERIOR);
        Houses.shadeTree(blueprint, 2, 10, g, YARD);
        return lot;
    }

    // ---------------------------------------------------------------- the library

    /**
     * The public library: brick, with tall windows, a portico of columns over its steps, its sign under the portico,
     * and inside, shelves of books along the walls and reading tables down the middle.
     */
    static Blueprint library(Frame frame, int g, Houses.Heights heights, RandomSource random) {
        Blueprint lot = new Blueprint(frame, STAGE_TICKS);
        Houses.level(lot, LOT_WIDTH, LOT_DEPTH, g, heights);
        frontYard(lot, g, 1 + SET_BACK, random);
        Blueprint blueprint = lot.shifted(ACROSS, SET_BACK);
        int left = 2;
        int right = 8;
        int front = 5;
        int back = 11;
        int top = g + 6;
        hall(blueprint, left, right, front, back, g, top, Role.CIVIC_WALL, 0);
        for (int a : new int[] {left, right}) {
            for (int b : new int[] {front, back}) {
                blueprint.fill(a, a, b, b, g + 2, top, Role.TRIM, pillar(Direction.Axis.Y), FRAME);
            }
        }
        // tall windows all round, and one over the door
        for (int a : new int[] {3, 7}) {
            window(blueprint, a, front, g + 3, 3, WALLS);
        }
        for (int a : new int[] {4, 6}) {
            window(blueprint, a, back, g + 3, 3, WALLS);
        }
        for (int b : new int[] {7, 9}) {
            window(blueprint, left, b, g + 3, 3, WALLS);
            window(blueprint, right, b, g + 3, 3, WALLS);
        }
        door(blueprint, 5, front, g + 2, frame.back(), WALLS);
        window(blueprint, 5, front, g + 4, 1, WALLS);
        // a flat roof with a cornice standing out all round, over the portico too
        blueprint.fill(left - 1, right + 1, front - 1, back + 1, top + 1, top + 1, Role.ROOF_RIDGE, FULL, ROOF);
        blueprint.fill(3, 7, 3, 3, top + 1, top + 1, Role.ROOF_RIDGE, FULL, ROOF);
        ring(blueprint, left - 1, right + 1, 3, back + 1, top + 2, Role.ROOF_SLAB, slab(SlabType.BOTTOM), ROOF);
        // the portico: two columns, a floor and steps up to it
        blueprint.fill(3, 7, 3, 4, g + 1, g + 1, Role.PORCH, FULL, BASE);
        blueprint.fill(4, 6, 2, 2, g + 1, g + 1, Role.STEP, stairs(frame.back(), Half.BOTTOM), BASE);
        for (int a : new int[] {3, 7}) {
            blueprint.fill(a, a, 3, 3, g + 2, top, Role.TRIM, pillar(Direction.Axis.Y), FRAME);
        }
        blueprint.set(5, front - 1, g + 5, Role.SIGN, Signs.LIBRARY, sign(frame.front()), EXTERIOR);
        // books along the walls between the windows, tables and chairs down the middle, lamps hanging over them
        for (int b : new int[] {6, 8, 10}) {
            blueprint.fill(left + 1, left + 1, b, b, g + 2, g + 4, Role.SHELF, FULL, INTERIOR);
            blueprint.fill(right - 1, right - 1, b, b, g + 2, g + 4, Role.SHELF, FULL, INTERIOR);
            blueprint.set(5, b, g + 2, Role.RUG, Houses.CARPET, INTERIOR);
        }
        for (int b : new int[] {7, 9}) {
            blueprint.set(5, b, g + 2, Role.TABLE, slab(SlabType.TOP), INTERIOR);
            blueprint.set(4, b, g + 2, Role.BENCH, stairs(frame.left(), Half.BOTTOM), INTERIOR);
            blueprint.set(6, b, g + 2, Role.BENCH, stairs(frame.right(), Half.BOTTOM), INTERIOR);
        }
        blueprint.set(5, 6, top, Role.LAMP, lantern(true), INTERIOR);
        blueprint.set(5, 10, top, Role.LAMP, lantern(true), INTERIOR);
        for (int a : new int[] {3, 7}) {
            blueprint.fill(a, a, 1, 1, g + 1, g + 3, Role.LAMP_POST, FENCE, EXTERIOR);
            blueprint.set(a, 1, g + 4, Role.LAMP, lantern(false), EXTERIOR);
        }
        return lot;
    }

    // ---------------------------------------------------------------- the chapel

    /**
     * A white chapel under a steep roof, with a steeple over its door where the bell hangs, pews either side of an
     * aisle and an altar at the far end.
     */
    static Blueprint chapel(Frame frame, int g, Houses.Heights heights, RandomSource random) {
        Blueprint lot = new Blueprint(frame, STAGE_TICKS);
        Houses.level(lot, LOT_WIDTH, LOT_DEPTH, g, heights);
        frontYard(lot, g, 1 + SET_BACK, random);
        Blueprint blueprint = lot.shifted(ACROSS, SET_BACK);
        int left = 3;
        int right = 7;
        int front = 5;
        int back = 11;
        int top = g + 5;
        int paint = random.nextInt(EraStyle.PAINTS);
        hall(blueprint, left, right, front, back, g, top, Role.WALL, paint);
        blueprint.fill(left + 1, right - 1, front + 1, back - 1, top, top, Role.CEILING, FULL, WALLS);
        for (int b : new int[] {7, 9}) {
            window(blueprint, left, b, g + 3, 2, WALLS);
            window(blueprint, right, b, g + 3, 2, WALLS);
        }
        window(blueprint, 5, back, g + 3, 2, WALLS);
        gableRoof(blueprint, left, right, front, back, top, Role.WALL, paint);
        // the steeple, built over the front of the roof
        int tower = 2;
        blueprint.fill(4, 6, tower, 4, g, g + 1, Role.FOUNDATION, FULL, BASE);
        blueprint.set(5, 3, g + 1, Role.FLOOR, FULL, BASE);
        for (int y = g + 2; y <= g + 9; y++) {
            for (int a = 4; a <= 6; a++) {
                for (int b = tower; b <= 4; b++) {
                    if (a != 5 || b != 3) {
                        blueprint.set(a, b, y, Role.WALL, paint, FULL, WALLS);
                    } else {
                        blueprint.set(a, b, y, Role.CLEAR, Houses.AIR, WALLS);
                    }
                }
            }
        }
        door(blueprint, 5, tower, g + 2, frame.back(), WALLS);
        for (int y = g + 2; y <= g + 3; y++) {
            blueprint.set(5, 4, y, Role.CLEAR, Houses.AIR, WALLS);
            blueprint.set(5, front, y, Role.CLEAR, Houses.AIR, WALLS);
        }
        // the belfry, open on all four sides, with the bell hanging in it
        for (int y = g + 8; y <= g + 9; y++) {
            blueprint.set(5, tower, y, Role.CLEAR, Houses.AIR, WALLS);
            blueprint.set(5, 4, y, Role.CLEAR, Houses.AIR, WALLS);
            blueprint.set(4, 3, y, Role.CLEAR, Houses.AIR, WALLS);
            blueprint.set(6, 3, y, Role.CLEAR, Houses.AIR, WALLS);
        }
        blueprint.fill(4, 6, tower, 4, g + 10, g + 10, Role.ROOF_RIDGE, FULL, ROOF);
        blueprint.set(5, 3, g + 11, Role.ROOF_RIDGE, FULL, ROOF);
        blueprint.set(5, tower, g + 11, Role.ROOF, stairs(frame.back(), Half.BOTTOM), ROOF);
        blueprint.set(5, 4, g + 11, Role.ROOF, stairs(frame.front(), Half.BOTTOM), ROOF);
        blueprint.set(4, 3, g + 11, Role.ROOF, stairs(frame.right(), Half.BOTTOM), ROOF);
        blueprint.set(6, 3, g + 11, Role.ROOF, stairs(frame.left(), Half.BOTTOM), ROOF);
        blueprint.fill(5, 5, 3, 3, g + 12, g + 14, Role.PORCH_POST, FENCE, ROOF);
        blueprint.set(5, 3, g + 9, Role.BELL, bell(frame.front(), BellAttachType.CEILING), EXTERIOR);
        blueprint.set(5, tower - 1, g + 5, Role.SIGN, Signs.CHAPEL, sign(frame.front()), EXTERIOR);
        // pews facing the altar either side of the aisle
        for (int b : new int[] {6, 8}) {
            blueprint.set(4, b, g + 2, Role.BENCH, stairs(frame.front(), Half.BOTTOM), INTERIOR);
            blueprint.set(6, b, g + 2, Role.BENCH, stairs(frame.front(), Half.BOTTOM), INTERIOR);
        }
        for (int b = 6; b <= 9; b++) {
            blueprint.set(5, b, g + 2, Role.RUG, Houses.CARPET, INTERIOR);
        }
        blueprint.set(5, 10, g + 2, Role.TABLE, slab(SlabType.TOP), INTERIOR);
        blueprint.set(5, 10, g + 3, Role.LAMP, lantern(false), INTERIOR);
        blueprint.set(5, 7, top - 1, Role.LAMP, lantern(true), INTERIOR);
        return lot;
    }

    // ---------------------------------------------------------------- shops

    /**
     * A shop on main street: a false front rising over a flat roof, big windows either side of the door under a
     * striped awning, its sign above, the sidewalk run right up to it with a bench either side. Inside, a diner or an
     * ice cream parlor has booths at its windows and a counter; anything else has a counter and shelves of goods.
     *
     * @param kind what it sells, one of {@link Signs#SHOPS}
     */
    static Blueprint shop(Frame frame, int g, Houses.Heights heights, int kind, RandomSource random) {
        Blueprint lot = new Blueprint(frame, STAGE_TICKS);
        Houses.level(lot, LOT_WIDTH, LOT_DEPTH, g, heights);
        lot.fill(0, LOT_WIDTH - 1, 0, 2, g, g, Role.SIDEWALK, FULL, GROUND);
        for (int a : new int[] {0, LOT_WIDTH - 1}) {
            lot.fill(a, a, 1, 1, g + 1, g + 3, Role.LAMP_POST, FENCE, EXTERIOR);
            lot.set(a, 1, g + 4, Role.LAMP, lantern(false), EXTERIOR);
        }
        // the shop itself, its front right on the sidewalk
        Blueprint blueprint = lot.shifted(ACROSS, 0);
        int left = 1;
        int right = 9;
        int front = 3;
        int back = 10;
        int top = g + 5;
        boolean brick = random.nextBoolean();
        Role walls = brick ? Role.CIVIC_WALL : Role.WALL;
        int paint = random.nextInt(EraStyle.PAINTS);
        hall(blueprint, left, right, front, back, g, top, walls, paint);
        // the false front, and a cornice along its top
        blueprint.fill(left, right, front, front, top + 1, top + 2, walls, paint, FULL, WALLS);
        blueprint.fill(left, right, front, front, top + 3, top + 3, Role.ROOF_SLAB, slab(SlabType.BOTTOM), ROOF);
        blueprint.fill(left, right, front + 1, back, top + 1, top + 1, Role.ROOF_SLAB, slab(SlabType.BOTTOM), ROOF);
        for (int a = 2; a <= 8; a++) {
            if (a != 5) {
                window(blueprint, a, front, g + 3, 2, WALLS);
            }
        }
        door(blueprint, 5, front, g + 2, frame.back(), WALLS);
        window(blueprint, 5, front, g + 4, 1, WALLS);
        for (int b : new int[] {6, 8}) {
            window(blueprint, left, b, g + 3, 2, WALLS);
            window(blueprint, right, b, g + 3, 2, WALLS);
        }
        blueprint.fill(left, right, front - 1, front - 1, g + 5, g + 5, Role.AWNING, stairs(frame.back(), Half.BOTTOM), EXTERIOR);
        blueprint.set(5, front - 1, g + 6, Role.SIGN, kind, sign(frame.front()), EXTERIOR);
        // inside
        if (kind == Signs.DINER || kind == Signs.ICE_CREAM || kind == Signs.BAKERY) {
            for (int table : new int[] {3, 7}) {
                blueprint.set(table, 5, g + 2, Role.TABLE, slab(SlabType.TOP), INTERIOR);
                blueprint.set(table - 1, 5, g + 2, Role.COUCH, stairs(frame.left(), Half.BOTTOM), INTERIOR);
                blueprint.set(table + 1, 5, g + 2, Role.COUCH, stairs(frame.right(), Half.BOTTOM), INTERIOR);
            }
            blueprint.fill(2, 7, 8, 8, g + 2, g + 2, Role.COUNTER, FULL, INTERIOR);
        } else {
            blueprint.fill(2, 4, 6, 6, g + 2, g + 2, Role.COUNTER, FULL, INTERIOR);
            for (int b = 5; b <= 8; b++) {
                blueprint.fill(right - 1, right - 1, b, b, g + 2, g + 3, Role.SHELF, FULL, INTERIOR);
            }
        }
        blueprint.fill(2, 8, back - 1, back - 1, g + 2, g + 3, Role.SHELF, FULL, INTERIOR);
        blueprint.set(3, 6, top, Role.LAMP, lantern(true), INTERIOR);
        blueprint.set(7, 6, top, Role.LAMP, lantern(true), INTERIOR);
        // out front: a bench either side of the door, with the street lamps at each corner of the lot
        blueprint.set(2, 1, g + 1, Role.BENCH, stairs(frame.back(), Half.BOTTOM), EXTERIOR);
        blueprint.set(8, 1, g + 1, Role.BENCH, stairs(frame.back(), Half.BOTTOM), EXTERIOR);
        return lot;
    }

    // ---------------------------------------------------------------- the schoolhouse

    /**
     * A one-room schoolhouse in red brick, its bell in a cupola on the ridge, a little porch roof over the door, the
     * teacher's desk at the front and rows of desks and chairs facing it.
     */
    static Blueprint school(Frame frame, int g, Houses.Heights heights, RandomSource random) {
        Blueprint lot = new Blueprint(frame, STAGE_TICKS);
        Houses.level(lot, LOT_WIDTH, LOT_DEPTH, g, heights);
        frontYard(lot, g, 2 + SET_BACK, random);
        Blueprint blueprint = lot.shifted(ACROSS, SET_BACK);
        int left = 2;
        int right = 8;
        int front = 5;
        int back = 11;
        int top = g + 5;
        hall(blueprint, left, right, front, back, g, top, Role.CIVIC_WALL, 0);
        blueprint.fill(left + 1, right - 1, front + 1, back - 1, top, top, Role.CEILING, FULL, WALLS);
        for (int b : new int[] {6, 7, 9, 10}) {
            window(blueprint, left, b, g + 3, 2, WALLS);
            window(blueprint, right, b, g + 3, 2, WALLS);
        }
        for (int a : new int[] {3, 7}) {
            window(blueprint, a, front, g + 3, 2, WALLS);
        }
        door(blueprint, 5, front, g + 2, frame.back(), WALLS);
        int ridge = gableRoof(blueprint, left, right, front, back, top, Role.CIVIC_WALL, 0);
        // the bell, on the ridge under a little cap
        blueprint.set(5, front + 1, ridge, Role.BELL, bell(frame.front(), BellAttachType.FLOOR), EXTERIOR);
        blueprint.set(5, front + 1, ridge + 1, Role.ROOF_SLAB, slab(SlabType.BOTTOM), EXTERIOR);
        // the porch
        blueprint.fill(4, 6, 4, 4, g + 1, g + 1, Role.PORCH, FULL, BASE);
        blueprint.set(5, 3, g + 1, Role.STEP, stairs(frame.back(), Half.BOTTOM), BASE);
        for (int a : new int[] {4, 6}) {
            blueprint.fill(a, a, 4, 4, g + 2, g + 3, Role.PORCH_POST, FENCE, EXTERIOR);
        }
        blueprint.fill(4, 6, 4, 4, g + 4, g + 4, Role.ROOF_SLAB, slab(SlabType.TOP), EXTERIOR);
        blueprint.set(5, 4, g + 5, Role.SIGN, Signs.SCHOOL, sign(frame.front()), EXTERIOR);
        // the teacher's desk, and rows of desks with chairs behind them
        blueprint.set(5, 6, g + 2, Role.TABLE, slab(SlabType.TOP), INTERIOR);
        for (int b : new int[] {7, 9}) {
            for (int a : new int[] {4, 6}) {
                blueprint.set(a, b, g + 2, Role.TABLE, slab(SlabType.TOP), INTERIOR);
                blueprint.set(a, b + 1, g + 2, Role.BENCH, stairs(frame.back(), Half.BOTTOM), INTERIOR);
            }
        }
        blueprint.set(5, 8, top - 1, Role.LAMP, lantern(true), INTERIOR);
        return lot;
    }

    // ---------------------------------------------------------------- shared

    /**
     * A building's foundation, floor and four walls, of one material.
     */
    private static void hall(Blueprint blueprint, int left, int right, int front, int back, int g, int top, Role walls, int paint) {
        blueprint.fill(left, right, front, back, g, g + 1, Role.FOUNDATION, FULL, BASE);
        blueprint.fill(left + 1, right - 1, front + 1, back - 1, g + 1, g + 1, Role.FLOOR, FULL, BASE);
        for (int y = g + 2; y <= top; y++) {
            ring(blueprint, left, right, front, back, y, walls, paint, FULL, WALLS);
        }
    }

    /**
     * A steep roof with its gable to the street, ridge running back down the middle, the gable ends filled in.
     *
     * @return the height of the ridge's top
     */
    private static int gableRoof(Blueprint blueprint, int left, int right, int front, int back, int top, Role walls, int paint) {
        Frame frame = blueprint.frame();
        int middle = (left + right) / 2;
        int ridge = top;
        for (int a = left - 1; a <= right + 1; a++) {
            int rise = Math.min(a - (left - 1), right + 1 - a);
            int y = top + 1 + rise;
            ridge = Math.max(ridge, y);
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
                    blueprint.fill(a, a, b, b, top + 1, under, walls, paint, FULL, ROOF);
                }
            }
        }
        return ridge;
    }

    private static void ring(Blueprint blueprint, int left, int right, int front, int back, int y, Role role, BlockState template, int stage) {
        ring(blueprint, left, right, front, back, y, role, 0, template, stage);
    }

    private static void ring(Blueprint blueprint, int left, int right, int front, int back, int y, Role role, int paint, BlockState template,
                             int stage) {
        for (int a = left; a <= right; a++) {
            blueprint.set(a, front, y, role, paint, template, stage);
            blueprint.set(a, back, y, role, paint, template, stage);
        }
        for (int b = front + 1; b < back; b++) {
            blueprint.set(left, b, y, role, paint, template, stage);
            blueprint.set(right, b, y, role, paint, template, stage);
        }
    }

    /**
     * A walk up from the street, beds of flowers either side of it and hedges at the front corners.
     *
     * @param to how far back the walk runs
     */
    private static void frontYard(Blueprint blueprint, int g, int to, RandomSource random) {
        int middle = LOT_WIDTH / 2;
        for (int b = 0; b <= to; b++) {
            blueprint.set(middle, b, g, Role.WALKWAY, FULL, GROUND);
        }
        int kind = random.nextInt(EraStyle.FLOWERS);
        for (int a : new int[] {0, 1, LOT_WIDTH - 2, LOT_WIDTH - 1}) {
            blueprint.set(a, 3, g + 1, Role.FLOWER, kind, FLOWER, YARD);
        }
        blueprint.set(0, 1, g + 1, Role.HEDGE, LEAVES, YARD);
        blueprint.set(LOT_WIDTH - 1, 1, g + 1, Role.HEDGE, LEAVES, YARD);
    }

    private static BlockState bell(Direction facing, BellAttachType attachment) {
        return Blocks.BELL.defaultBlockState().setValue(BellBlock.FACING, facing).setValue(BellBlock.ATTACHMENT, attachment);
    }
}
