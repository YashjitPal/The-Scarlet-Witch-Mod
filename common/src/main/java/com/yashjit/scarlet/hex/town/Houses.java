package com.yashjit.scarlet.hex.town;

import com.yashjit.scarlet.hex.town.Blueprint.Frame;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Sitcom houses, each on an {@value TownPlan#LOT_WIDTH} by {@value TownPlan#LOT_DEPTH} lot: a picket fence along the
 * sidewalk with a gate and a mailbox, a lawn with flower beds, a raised porch up a step, and a furnished living room
 * and kitchen. They come side-gabled or front-gabled, and the caster's home has a second floor.
 *
 * <p>Each builds itself the way the house does in WandaVision: the lot is leveled, the foundation laid, the frame
 * rises, the walls close in with their windows and door, the roof goes on, and then the porch, yard and rooms
 * finish themselves.
 */
public final class Houses {

    static final int GROUND = 0;
    static final int BASE = 1;
    static final int FRAME = 2;
    static final int WALLS = 3;
    static final int ROOF = 4;
    static final int EXTERIOR = 5;
    static final int YARD = 6;
    static final int INTERIOR = 7;
    private static final int[] STAGE_TICKS = {8, 8, 14, 22, 18, 10, 10, 10};

    static final BlockState AIR = Blocks.AIR.defaultBlockState();
    static final BlockState FULL = Blocks.STONE.defaultBlockState();
    static final BlockState FENCE = Blocks.OAK_FENCE.defaultBlockState();
    static final BlockState PANE = Blocks.GLASS_PANE.defaultBlockState();
    static final BlockState CARPET = Blocks.CARPET.pick(DyeColor.WHITE).defaultBlockState();
    static final BlockState LEAVES = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    static final BlockState FLOWER = Blocks.POPPY.defaultBlockState();
    static final BlockState POT = Blocks.POTTED_FERN.defaultBlockState();
    static final BlockState CAULDRON = Blocks.CAULDRON.defaultBlockState();

    private Houses() {
    }

    /**
     * The natural ground height under each column of a lot, on the lot's grid.
     */
    @FunctionalInterface
    public interface Heights {
        int at(int a, int b);
    }

    public enum Shape {
        SIDE_GABLE, FRONT_GABLE, TWO_STORY
    }

    public static Blueprint house(Frame frame, int ground, Heights heights, Shape shape, RandomSource random) {
        Blueprint blueprint = new Blueprint(frame, STAGE_TICKS);
        level(blueprint, TownPlan.LOT_WIDTH, TownPlan.LOT_DEPTH, ground, heights);
        Paint paint = new Paint(random.nextInt(EraStyle.PAINTS), random.nextInt(EraStyle.FLOWERS), random.nextInt(EraStyle.FLOWERS),
                random.nextBoolean(), random.nextFloat() < 0.75F);
        switch (shape) {
            case SIDE_GABLE -> sideGable(blueprint, ground, 1, paint);
            case TWO_STORY -> sideGable(blueprint, ground, 2, paint);
            case FRONT_GABLE -> frontGable(blueprint, ground, paint);
        }
        return blueprint;
    }

    /**
     * @param paint   siding paint
     * @param flowers the two kinds of flower in the beds
     */
    private record Paint(int paint, int flowers, int otherFlowers, boolean chimneyRight, boolean shutters) {
    }

    // ---------------------------------------------------------------- the lot

    /**
     * Levels a lot to one height: ground above is cut away, hollows below are filled, and the whole lot is turfed.
     */
    static void level(Blueprint blueprint, int width, int depth, int ground, Heights heights) {
        for (int b = 0; b < depth; b++) {
            for (int a = 0; a < width; a++) {
                int height = heights.at(a, b);
                if (height > ground) {
                    blueprint.fill(a, a, b, b, ground + 1, height + 2, Role.CLEAR, AIR, GROUND);
                } else {
                    blueprint.fill(a, a, b, b, ground + 1, ground + 2, Role.CLEAR, AIR, GROUND);
                    if (height < ground) {
                        blueprint.fill(a, a, b, b, height + 1, ground - 1, Role.FILL, FULL, GROUND);
                    }
                }
                blueprint.set(a, b, ground, Role.LAWN, FULL, GROUND);
            }
        }
    }

    /**
     * The picket fence along the front with its gate, the walkway up to the step, the mailbox, and a hedge at each
     * front corner.
     */
    private static void yard(Blueprint blueprint, int ground, int walkwayTo, Paint paint) {
        Frame frame = blueprint.frame();
        int middle = TownPlan.LOT_WIDTH / 2;
        for (int a = 0; a < TownPlan.LOT_WIDTH; a++) {
            if (a != middle) {
                blueprint.set(a, 0, ground + 1, Role.PICKET, FENCE, YARD);
            }
        }
        blueprint.set(middle, 0, ground + 1, Role.GATE, gate(frame.back()), YARD);
        for (int b = 0; b <= walkwayTo; b++) {
            blueprint.set(middle, b, ground, Role.WALKWAY, FULL, YARD);
        }
        blueprint.set(middle - 2, 1, ground + 1, Role.MAILBOX_POST, FENCE, YARD);
        blueprint.set(middle - 2, 1, ground + 2, Role.MAILBOX, FULL, YARD);
        blueprint.set(0, 1, ground + 1, Role.HEDGE, LEAVES, YARD);
        blueprint.set(TownPlan.LOT_WIDTH - 1, 1, ground + 1, Role.HEDGE, LEAVES, YARD);
    }

    // ---------------------------------------------------------------- side-gabled

    /**
     * A house whose roof ridge runs along the street: nine wide, seven deep, with a porch at the door.
     */
    private static void sideGable(Blueprint blueprint, int g, int stories, Paint paint) {
        Frame frame = blueprint.frame();
        int left = 1;
        int right = 9;
        int front = 5;
        int back = 11;
        int top = g + 1 + 4 * stories;
        int roof = top + 1;
        Direction.Axis across = frame.acrossAxis();
        Direction.Axis depth = frame.depthAxis();

        // foundation, then the floor inside a ring of foundation stone
        blueprint.fill(left, right, front, back, g, g, Role.FOUNDATION, FULL, BASE);
        blueprint.fill(4, 6, 3, 4, g, g, Role.FOUNDATION, FULL, BASE);
        blueprint.fill(left, right, front, back, g + 1, g + 1, Role.FOUNDATION, FULL, BASE);
        blueprint.fill(left + 1, right - 1, front + 1, back - 1, g + 1, g + 1, Role.FLOOR, FULL, BASE);
        blueprint.fill(4, 6, 3, 4, g + 1, g + 1, Role.PORCH, FULL, BASE);

        // the frame: corner posts, and beams along the top of every story
        for (int a : new int[] {left, right}) {
            for (int b : new int[] {front, back}) {
                blueprint.fill(a, a, b, b, g + 2, top, Role.TRIM, pillar(Direction.Axis.Y), FRAME);
            }
        }
        for (int story = 1; story <= stories; story++) {
            int beam = g + 1 + 4 * story;
            blueprint.fill(left + 1, right - 1, front, front, beam, beam, Role.TRIM, pillar(across), FRAME);
            blueprint.fill(left + 1, right - 1, back, back, beam, beam, Role.TRIM, pillar(across), FRAME);
            blueprint.fill(left, left, front + 1, back - 1, beam, beam, Role.TRIM, pillar(depth), FRAME);
            blueprint.fill(right, right, front + 1, back - 1, beam, beam, Role.TRIM, pillar(depth), FRAME);
        }

        // the walls between, with their windows and the front door
        for (int story = 1; story <= stories; story++) {
            int floor = g + 1 + 4 * (story - 1);
            blueprint.fill(left + 1, right - 1, front, front, floor + 1, floor + 3, Role.WALL, paint.paint(), FULL, WALLS);
            blueprint.fill(left + 1, right - 1, back, back, floor + 1, floor + 3, Role.WALL, paint.paint(), FULL, WALLS);
            blueprint.fill(left, left, front + 1, back - 1, floor + 1, floor + 3, Role.WALL, paint.paint(), FULL, WALLS);
            blueprint.fill(right, right, front + 1, back - 1, floor + 1, floor + 3, Role.WALL, paint.paint(), FULL, WALLS);
            int sill = floor + 2;
            window(blueprint, 2, front, sill, 2, WALLS);
            window(blueprint, 3, front, sill, 2, WALLS);
            window(blueprint, 7, front, sill, 2, WALLS);
            window(blueprint, 8, front, sill, 2, WALLS);
            if (story == 2) {
                window(blueprint, 5, front, sill, 2, WALLS);
            }
            window(blueprint, 3, back, sill, 2, WALLS);
            window(blueprint, 7, back, sill, 2, WALLS);
            for (int b = 7; b <= 8; b++) {
                window(blueprint, left, b, sill, 2, WALLS);
                window(blueprint, right, b, sill, 2, WALLS);
            }
            if (paint.shutters()) {
                shutters(blueprint, sill, left - 1, 6, 9, frame.left(), true);
                shutters(blueprint, sill, right + 1, 6, 9, frame.right(), true);
                shutters(blueprint, sill, front - 1, 1, 4, frame.front(), false);
                shutters(blueprint, sill, front - 1, 6, 9, frame.front(), false);
            }
        }
        door(blueprint, 5, front, g + 2, frame.back(), WALLS);
        if (stories == 1) {
            blueprint.fill(left + 1, right - 1, front + 1, back - 1, top, top, Role.CEILING, FULL, WALLS);
        } else {
            blueprint.fill(left + 1, right - 1, front + 1, back - 1, g + 5, g + 5, Role.FLOOR, FULL, WALLS);
            blueprint.fill(left + 1, right - 1, front + 1, back - 1, top, top, Role.CEILING, FULL, WALLS);
            // a stair up the left wall, through a hole in the floor above it
            for (int i = 0; i < 4; i++) {
                blueprint.set(left + 1, back - 1 - i, g + 2 + i, Role.STEP, stairs(frame.front(), Half.BOTTOM), WALLS);
            }
            for (int b = back - 1; b >= back - 3; b--) {
                blueprint.set(left + 1, b, g + 5, Role.CLEAR, AIR, WALLS);
            }
        }

        // the roof: stairs climbing from the eaves at front and back to the ridge, gables filled in at each end
        for (int b = front - 1; b <= back + 1; b++) {
            int rise = Math.min(b - (front - 1), back + 1 - b);
            int y = roof + rise;
            for (int a = left - 1; a <= right + 1; a++) {
                if (b < 8) {
                    blueprint.set(a, b, y, Role.ROOF, stairs(frame.back(), Half.BOTTOM), ROOF);
                } else if (b > 8) {
                    blueprint.set(a, b, y, Role.ROOF, stairs(frame.front(), Half.BOTTOM), ROOF);
                } else {
                    blueprint.set(a, b, y - 1, Role.ROOF_RIDGE, FULL, ROOF);
                    blueprint.set(a, b, y, Role.ROOF_SLAB, slab(SlabType.BOTTOM), ROOF);
                }
            }
            if (b >= front && b <= back) {
                int under = b == 8 ? y - 2 : y - 1;
                for (int a : new int[] {left, right}) {
                    blueprint.fill(a, a, b, b, roof, under, Role.WALL, paint.paint(), FULL, ROOF);
                }
            }
        }
        window(blueprint, left, 8, roof + 1, 1, ROOF);
        window(blueprint, right, 8, roof + 1, 1, ROOF);

        // the porch: posts holding up a little roof, and a step up to it
        blueprint.fill(4, 4, 3, 3, g + 2, g + 4, Role.PORCH_POST, FENCE, EXTERIOR);
        blueprint.fill(6, 6, 3, 3, g + 2, g + 4, Role.PORCH_POST, FENCE, EXTERIOR);
        blueprint.fill(3, 7, 3, 4, g + 5, g + 5, Role.ROOF_SLAB, slab(SlabType.TOP), EXTERIOR);
        blueprint.set(5, 2, g + 1, Role.STEP, stairs(frame.back(), Half.BOTTOM), EXTERIOR);
        int chimney = paint.chimneyRight() ? right + 1 : left - 1;
        blueprint.fill(chimney, chimney, back - 1, back - 1, g + 1, roof + 6, Role.CHIMNEY, FULL, EXTERIOR);

        yard(blueprint, g, 2, paint);
        for (int a = 1; a <= 3; a++) {
            blueprint.set(a, 4, g + 1, Role.FLOWER, paint.flowers(), FLOWER, YARD);
            blueprint.set(a + 6, 4, g + 1, Role.FLOWER, paint.otherFlowers(), FLOWER, YARD);
        }

        livingRoom(blueprint, g + 1, left + 1, right - 1, front + 1, back - 1, top, stories == 1);
        if (stories == 2) {
            bedroom(blueprint, g + 5, left + 2, right - 1, front + 1, back - 1, top);
        }
    }

    // ---------------------------------------------------------------- front-gabled

    /**
     * A bungalow with its gable to the street: seven wide, eight deep, a porch across its whole front.
     */
    private static void frontGable(Blueprint blueprint, int g, Paint paint) {
        Frame frame = blueprint.frame();
        int left = 2;
        int right = 8;
        int front = 4;
        int back = 11;
        int top = g + 5;
        int roof = top + 1;
        int ridge = 5;
        Direction.Axis across = frame.acrossAxis();
        Direction.Axis depth = frame.depthAxis();

        blueprint.fill(left, right, front, back, g, g, Role.FOUNDATION, FULL, BASE);
        blueprint.fill(left, right, 2, 3, g, g, Role.FOUNDATION, FULL, BASE);
        blueprint.fill(left, right, front, back, g + 1, g + 1, Role.FOUNDATION, FULL, BASE);
        blueprint.fill(left + 1, right - 1, front + 1, back - 1, g + 1, g + 1, Role.FLOOR, FULL, BASE);
        blueprint.fill(left, right, 2, 3, g + 1, g + 1, Role.PORCH, FULL, BASE);

        for (int a : new int[] {left, right}) {
            for (int b : new int[] {front, back}) {
                blueprint.fill(a, a, b, b, g + 2, top, Role.TRIM, pillar(Direction.Axis.Y), FRAME);
            }
        }
        blueprint.fill(left + 1, right - 1, front, front, top, top, Role.TRIM, pillar(across), FRAME);
        blueprint.fill(left + 1, right - 1, back, back, top, top, Role.TRIM, pillar(across), FRAME);
        blueprint.fill(left, left, front + 1, back - 1, top, top, Role.TRIM, pillar(depth), FRAME);
        blueprint.fill(right, right, front + 1, back - 1, top, top, Role.TRIM, pillar(depth), FRAME);

        blueprint.fill(left + 1, right - 1, front, front, g + 2, g + 4, Role.WALL, paint.paint(), FULL, WALLS);
        blueprint.fill(left + 1, right - 1, back, back, g + 2, g + 4, Role.WALL, paint.paint(), FULL, WALLS);
        blueprint.fill(left, left, front + 1, back - 1, g + 2, g + 4, Role.WALL, paint.paint(), FULL, WALLS);
        blueprint.fill(right, right, front + 1, back - 1, g + 2, g + 4, Role.WALL, paint.paint(), FULL, WALLS);
        window(blueprint, 3, front, g + 3, 2, WALLS);
        window(blueprint, 7, front, g + 3, 2, WALLS);
        window(blueprint, 4, back, g + 3, 2, WALLS);
        window(blueprint, 6, back, g + 3, 2, WALLS);
        for (int b : new int[] {6, 7}) {
            window(blueprint, left, b, g + 3, 2, WALLS);
            window(blueprint, right, b, g + 3, 2, WALLS);
        }
        if (paint.shutters()) {
            shutters(blueprint, g + 3, left - 1, 5, 8, frame.left(), true);
            shutters(blueprint, g + 3, right + 1, 5, 8, frame.right(), true);
        }
        door(blueprint, 5, front, g + 2, frame.back(), WALLS);
        blueprint.fill(left + 1, right - 1, front + 1, back - 1, top, top, Role.CEILING, FULL, WALLS);

        // the roof: stairs climbing from the eaves at either side to a ridge running back from the street
        for (int a = left - 1; a <= right + 1; a++) {
            int rise = Math.min(a - (left - 1), right + 1 - a);
            int y = roof + rise;
            for (int b = front - 1; b <= back + 1; b++) {
                if (a < ridge) {
                    blueprint.set(a, b, y, Role.ROOF, stairs(frame.right(), Half.BOTTOM), ROOF);
                } else if (a > ridge) {
                    blueprint.set(a, b, y, Role.ROOF, stairs(frame.left(), Half.BOTTOM), ROOF);
                } else {
                    blueprint.set(a, b, y - 1, Role.ROOF_RIDGE, FULL, ROOF);
                    blueprint.set(a, b, y, Role.ROOF_SLAB, slab(SlabType.BOTTOM), ROOF);
                }
            }
            if (a >= left && a <= right) {
                int under = a == ridge ? y - 2 : y - 1;
                for (int b : new int[] {front, back}) {
                    blueprint.fill(a, a, b, b, roof, under, Role.WALL, paint.paint(), FULL, ROOF);
                }
            }
        }
        window(blueprint, ridge, front, roof + 1, 1, ROOF);
        window(blueprint, ridge, back, roof + 1, 1, ROOF);

        blueprint.fill(left, left, 2, 2, g + 2, g + 4, Role.PORCH_POST, FENCE, EXTERIOR);
        blueprint.fill(right, right, 2, 2, g + 2, g + 4, Role.PORCH_POST, FENCE, EXTERIOR);
        blueprint.fill(left - 1, right + 1, 2, 3, g + 5, g + 5, Role.ROOF_SLAB, slab(SlabType.TOP), EXTERIOR);
        blueprint.set(5, 1, g + 1, Role.STEP, stairs(frame.back(), Half.BOTTOM), EXTERIOR);
        int chimney = paint.chimneyRight() ? right + 1 : left - 1;
        blueprint.fill(chimney, chimney, back - 1, back - 1, g + 1, roof + 6, Role.CHIMNEY, FULL, EXTERIOR);

        yard(blueprint, g, 1, paint);
        blueprint.set(1, 1, g + 1, Role.FLOWER, paint.flowers(), FLOWER, YARD);
        blueprint.set(1, 2, g + 1, Role.FLOWER, paint.flowers(), FLOWER, YARD);
        blueprint.set(9, 1, g + 1, Role.FLOWER, paint.otherFlowers(), FLOWER, YARD);
        blueprint.set(9, 2, g + 1, Role.FLOWER, paint.otherFlowers(), FLOWER, YARD);

        livingRoom(blueprint, g + 1, left + 1, right - 1, front + 1, back - 1, top, true);
    }

    // ---------------------------------------------------------------- rooms

    /**
     * A couch against the back wall facing a coffee table on a rug, end tables with plants, a lamp in each back
     * corner, bookshelves by the door and a little kitchen across from them, under a ceiling light.
     *
     * @param floor the floor's height; the room stands on it
     * @param withShelves whether the left wall is free for shelves, rather than taken by a stair
     */
    private static void livingRoom(Blueprint blueprint, int floor, int left, int right, int front, int back, int ceiling, boolean withShelves) {
        Frame frame = blueprint.frame();
        int y = floor + 1;
        int middle = (left + right) / 2;
        blueprint.fill(left + 1, right - 1, front + 2, back - 1, y, y, Role.RUG, CARPET, INTERIOR);
        for (int a = middle - 1; a <= middle + 1; a++) {
            blueprint.set(a, back, y, Role.COUCH, stairs(frame.back(), Half.BOTTOM), INTERIOR);
        }
        blueprint.set(middle, back - 2, y, Role.TABLE, slab(SlabType.TOP), INTERIOR);
        if (middle - 2 > left) {
            blueprint.set(middle - 2, back, y, Role.TABLE, slab(SlabType.TOP), INTERIOR);
            blueprint.set(middle - 2, back, y + 1, Role.PLANT, POT, INTERIOR);
            blueprint.set(middle + 2, back, y, Role.TABLE, slab(SlabType.TOP), INTERIOR);
        } else {
            blueprint.set(middle, back - 2, y + 1, Role.PLANT, POT, INTERIOR);
        }
        if (withShelves) {
            blueprint.fill(left, left, front, front + 1, y, y + 1, Role.SHELF, FULL, INTERIOR);
            blueprint.set(left, back, y, Role.PORCH_POST, FENCE, INTERIOR);
            blueprint.set(left, back, y + 1, Role.LAMP, lantern(false), INTERIOR);
        }
        blueprint.set(right, back, y, Role.PORCH_POST, FENCE, INTERIOR);
        blueprint.set(right, back, y + 1, Role.LAMP, lantern(false), INTERIOR);
        blueprint.fill(right, right, front, front, y, y + 1, Role.FRIDGE, pillar(Direction.Axis.Y), INTERIOR);
        blueprint.set(right, front + 1, y, Role.COUNTER, FULL, INTERIOR);
        blueprint.set(right, front + 2, y, Role.SINK, CAULDRON, INTERIOR);
        blueprint.set(middle, (front + back) / 2, ceiling - 1, Role.LAMP, lantern(true), INTERIOR);
    }

    private static void bedroom(Blueprint blueprint, int floor, int left, int right, int front, int back, int ceiling) {
        int y = floor + 1;
        blueprint.fill(left + 1, right - 1, front + 1, back - 1, y, y, Role.RUG, CARPET, INTERIOR);
        blueprint.fill(right, right, front, front + 1, y, y + 1, Role.SHELF, FULL, INTERIOR);
        blueprint.set(right, back, y, Role.TABLE, slab(SlabType.TOP), INTERIOR);
        blueprint.set(right, back, y + 1, Role.PLANT, POT, INTERIOR);
        blueprint.set((left + right) / 2, (front + back) / 2, ceiling - 1, Role.LAMP, lantern(true), INTERIOR);
    }

    // ---------------------------------------------------------------- pieces

    private static void window(Blueprint blueprint, int a, int b, int sill, int height, int stage) {
        blueprint.fill(a, a, b, b, sill, sill + height - 1, Role.WINDOW, PANE, stage);
    }

    private static void door(Blueprint blueprint, int a, int b, int y, Direction facing, int stage) {
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
        blueprint.set(a, b, y, Role.DOOR, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), stage);
        blueprint.set(a, b, y + 1, Role.DOOR, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), stage);
    }

    /**
     * Open shutters either side of a window, two high.
     *
     * @param onSide whether they hang on a side wall, spaced along its depth, rather than on the front
     * @param from   where the first shutter goes: its depth on a side wall, otherwise its place across
     * @param to     the same for the second
     * @param facing outward from the wall they hang on
     */
    private static void shutters(Blueprint blueprint, int sill, int line, int from, int to, Direction facing, boolean onSide) {
        BlockState shutter = Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, facing).setValue(TrapDoorBlock.OPEN, true);
        for (int at : new int[] {from, to}) {
            int a = onSide ? line : at;
            int b = onSide ? at : line;
            blueprint.fill(a, a, b, b, sill, sill + 1, Role.SHUTTER, shutter, EXTERIOR);
        }
    }

    static BlockState stairs(Direction facing, Half half) {
        return Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, half);
    }

    static BlockState slab(SlabType type) {
        return Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    static BlockState gate(Direction facing) {
        return Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, facing);
    }

    static BlockState pillar(Direction.Axis axis) {
        return Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    static BlockState lantern(boolean hanging) {
        return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, hanging);
    }
}
