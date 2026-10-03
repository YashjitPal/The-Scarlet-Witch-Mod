package com.yashjit.scarlet.hex.town;

import com.yashjit.scarlet.hex.town.Blueprint.Frame;
import com.yashjit.scarlet.registry.ScarletBlocks;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Sitcom houses, each on a {@value TownPlan#LOT_WIDTH} by {@value TownPlan#LOT_DEPTH} lot behind a picket fence or a
 * hedge along the sidewalk, with a gate and a mailbox, a lawn with flower beds, and a walk up to the porch. No two
 * streets look alike: ranch houses, long and low, often with a garage and a driveway; bungalows with their gable to the
 * street behind a porch across the front; two-story colonials; foursquares under hipped roofs; cottages with a porch
 * wrapping round one side. Inside, a living room with a kitchen, and up the stairs, a bedroom.
 *
 * <p>The caster's home is one of the two-story kinds, picked by their town, its front door always in the middle with
 * its living room behind it, where they float as it rises around them.
 *
 * <p>Each builds itself the way the house does in WandaVision: the lot is leveled, the foundation laid, the frame
 * rises, the walls close in with their windows and door, the roof goes on, and then the porch, yard and rooms
 * finish themselves. A home made of something already standing keeps all of it and is finished around it: see
 * {@link #finished}.
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
    static final int[] STAGE_TICKS = {8, 10, 16, 26, 22, 12, 10, 12};
    /** The caster's home takes its time: some fifteen seconds from the ground up to the last lamp inside. */
    static final int[] ANCHOR_TICKS = {30, 26, 44, 66, 52, 30, 24, 28};
    private static final int[] GARDEN_TICKS = {8, 10, 12};
    /** Leveling around a building that stays, a while for it to be made over, then the yard. */
    private static final int[] AROUND_TICKS = {8, 30, 12};

    /** From one floor to the next: the floor itself and four rows of wall over it. */
    static final int STORY = 5;
    /** Where a house's front wall stands, back from the street behind its lawn and porch. */
    private static final int FRONT = TownPlan.HOME_FRONT;
    private static final int GARAGE_WIDTH = 5;
    private static final int GARAGE_DEPTH = 7;
    /** No door found on a building's front. */
    static final int NO_DOOR = Integer.MIN_VALUE;

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

    /** How a house's roof runs. */
    enum Roof {
        /** Its ridge along the street, its gables at the sides. */
        SIDE_GABLE,
        /** Its gable to the street, its ridge running back. */
        FRONT_GABLE,
        /** Sloping down on all four sides. */
        HIP
    }

    /** What stands out in front of a house's door. */
    enum Porch {
        /** A little porch at the door under a flat roof. */
        STOOP,
        /** The same under a little gable, its pediment to the street. */
        PORTICO,
        /** A porch across the whole front. */
        FULL,
        /** A porch across the front and round one side. */
        WRAP
    }

    /**
     * Everything that sets one house apart from the next.
     *
     * @param width   across its front, wall to wall; odd, so its door can stand in the middle
     * @param depth   back from its front wall
     * @param stories one or two
     * @param garage  which side a garage is built on to: -1 its left, 1 its right, 0 none
     */
    record Design(int width, int depth, int stories, Roof roof, Porch porch, int garage, Paint paint) {
    }

    /**
     * @param paint        siding paint
     * @param flowers      the kinds of flower in the beds, one each side of the walk
     * @param chimneyRight which side its chimney stands, if it has one
     * @param hedgeFront   a hedge along the street instead of a picket fence
     * @param tree         a shade tree in the front yard, where there is room for one
     */
    record Paint(int paint, int flowers, int otherFlowers, boolean chimney, boolean chimneyRight, boolean shutters, boolean hedgeFront,
                 boolean tree) {

        static Paint pick(RandomSource random) {
            return new Paint(random.nextInt(EraStyle.PAINTS), random.nextInt(EraStyle.FLOWERS), random.nextInt(EraStyle.FLOWERS),
                    random.nextFloat() < 0.7F, random.nextBoolean(), random.nextFloat() < 0.75F, random.nextFloat() < 0.25F,
                    random.nextFloat() < 0.45F);
        }
    }

    /**
     * Where a house stands on its lot, on the lot's grid.
     *
     * @param g    the level of the lot; the house's floor is a block over it
     * @param top  the height of the ceiling over its top story, the roof going on over it
     * @param door where across its front its door is
     */
    private record Box(int left, int right, int front, int back, int g, int top, int stories, int door) {
    }

    // ---------------------------------------------------------------- what goes up

    /**
     * A house along a street, so that no two streets, and no two Hexes, look alike.
     */
    public static Blueprint house(Frame frame, int ground, Heights heights, RandomSource random) {
        return build(frame, ground, heights, town(random, Paint.pick(random)), STAGE_TICKS);
    }

    /**
     * The caster's own home, the house the whole Hex grows out of: two stories, raised around them slowly and surely, a
     * stage at a time, while they float in its living room pouring out the magic it is made of. Which house it is
     * comes from their town, so the same town always raises the same home.
     */
    public static Blueprint anchor(Frame frame, int ground, Heights heights, RandomSource random) {
        Paint paint = Paint.pick(random);
        Design design = switch (random.nextInt(5)) {
            // a colonial, its door under a portico, shuttered windows either side
            case 0 -> new Design(13, 9, 2, Roof.SIDE_GABLE, Porch.PORTICO, 0, paint);
            // a farmhouse with a porch across its whole front
            case 1 -> new Design(13, 9, 2, Roof.SIDE_GABLE, Porch.FULL, 0, paint);
            // a foursquare, square and tall under a hipped roof
            case 2 -> new Design(11, 11, 2, Roof.HIP, Porch.FULL, 0, paint);
            // its gable to the street, a porch wrapping round one side
            case 3 -> new Design(11, 11, 2, Roof.FRONT_GABLE, Porch.WRAP, 0, paint);
            // wide and grand under a hipped roof
            default -> new Design(15, 9, 2, Roof.HIP, Porch.PORTICO, 0, paint);
        };
        return build(frame, ground, heights, design, ANCHOR_TICKS);
    }

    /**
     * What kind of house goes up on a lot along a street.
     */
    private static Design town(RandomSource random, Paint paint) {
        float pick = random.nextFloat();
        int side = random.nextBoolean() ? 1 : -1;
        if (pick < 0.3F) {
            // a ranch house, long and low, often with a garage and the driveway up to it
            boolean garage = random.nextFloat() < 0.6F;
            return new Design(garage ? 11 : 13 + 2 * random.nextInt(2), 9, 1, random.nextBoolean() ? Roof.HIP : Roof.SIDE_GABLE,
                    random.nextBoolean() ? Porch.STOOP : Porch.FULL, garage ? side : 0, paint);
        }
        if (pick < 0.5F) {
            // a bungalow, its gable to the street over a porch across its front
            return new Design(11, 11, 1, Roof.FRONT_GABLE, Porch.FULL, 0, paint);
        }
        if (pick < 0.72F) {
            // a two-story colonial
            boolean garage = random.nextFloat() < 0.35F;
            return new Design(garage ? 11 : 13, 9, 2, Roof.SIDE_GABLE, random.nextBoolean() ? Porch.PORTICO : Porch.STOOP, garage ? side : 0,
                    paint);
        }
        if (pick < 0.88F) {
            // a foursquare
            return new Design(11, 11, 2, Roof.HIP, Porch.FULL, 0, paint);
        }
        // a cottage with its porch wrapping round one side
        return new Design(11, 9, 1, Roof.SIDE_GABLE, Porch.WRAP, 0, paint);
    }

    private static Blueprint build(Frame frame, int g, Heights heights, Design design, int[] stageTicks) {
        Blueprint blueprint = new Blueprint(frame, stageTicks);
        level(blueprint, TownPlan.LOT_WIDTH, TownPlan.LOT_DEPTH, g, heights);
        Paint paint = design.paint();
        int total = design.width() + (design.garage() != 0 ? GARAGE_WIDTH : 0);
        int left = (TownPlan.LOT_WIDTH - total) / 2 + (design.garage() < 0 ? GARAGE_WIDTH : 0);
        int right = left + design.width() - 1;
        int back = FRONT + design.depth() - 1;
        Box box = new Box(left, right, FRONT, back, g, g + 1 + STORY * design.stories(), design.stories(), (left + right) / 2);

        foundation(blueprint, box);
        frame(blueprint, box);
        for (int story = 1; story <= box.stories(); story++) {
            walls(blueprint, box, story, paint);
        }
        if (box.stories() > 1) {
            blueprint.fill(left + 1, right - 1, FRONT + 1, back - 1, g + 1 + STORY, g + 1 + STORY, Role.FLOOR, FULL, WALLS);
            stairway(blueprint, box);
        }
        blueprint.fill(left + 1, right - 1, FRONT + 1, back - 1, box.top(), box.top(), Role.CEILING, FULL, WALLS);
        int ridge = roof(blueprint, box, design.roof(), paint);
        porch(blueprint, box, design.porch());
        if (paint.chimney()) {
            int side = paint.chimneyRight() ? 1 : -1;
            if (side == design.garage() || side > 0 && design.porch() == Porch.WRAP) {
                side = -side;
            }
            int a = side > 0 ? right + 1 : left - 1;
            int b = (FRONT + back) / 2;
            blueprint.fill(a, a, b, b, g + 1, ridge + 1, Role.CHIMNEY, FULL, EXTERIOR);
        }
        int driveFrom = Integer.MAX_VALUE;
        int driveTo = Integer.MIN_VALUE;
        if (design.garage() != 0) {
            int[] drive = garage(blueprint, box, design.garage());
            driveFrom = drive[0];
            driveTo = drive[1];
        }
        yard(blueprint, box, design.porch(), paint, driveFrom, driveTo, design.garage() == 0);
        rooms(blueprint, box);
        return blueprint;
    }

    // ---------------------------------------------------------------- the house

    private static void foundation(Blueprint blueprint, Box box) {
        int g = box.g();
        blueprint.fill(box.left(), box.right(), box.front(), box.back(), g, g + 1, Role.FOUNDATION, FULL, BASE);
        blueprint.fill(box.left() + 1, box.right() - 1, box.front() + 1, box.back() - 1, g + 1, g + 1, Role.FLOOR, FULL, BASE);
    }

    /**
     * The frame the house rises on: posts up its corners, and beams along the top of every story.
     */
    private static void frame(Blueprint blueprint, Box box) {
        Frame frame = blueprint.frame();
        Direction.Axis across = frame.acrossAxis();
        Direction.Axis depth = frame.depthAxis();
        for (int a : new int[] {box.left(), box.right()}) {
            for (int b : new int[] {box.front(), box.back()}) {
                blueprint.fill(a, a, b, b, box.g() + 2, box.top(), Role.TRIM, pillar(Direction.Axis.Y), FRAME);
            }
        }
        for (int story = 1; story <= box.stories(); story++) {
            int beam = box.g() + 1 + STORY * story;
            blueprint.fill(box.left() + 1, box.right() - 1, box.front(), box.front(), beam, beam, Role.TRIM, pillar(across), FRAME);
            blueprint.fill(box.left() + 1, box.right() - 1, box.back(), box.back(), beam, beam, Role.TRIM, pillar(across), FRAME);
            blueprint.fill(box.left(), box.left(), box.front() + 1, box.back() - 1, beam, beam, Role.TRIM, pillar(depth), FRAME);
            blueprint.fill(box.right(), box.right(), box.front() + 1, box.back() - 1, beam, beam, Role.TRIM, pillar(depth), FRAME);
        }
    }

    /**
     * One story's walls between the posts, its windows in pairs, and on the ground floor the front door with a window
     * over it.
     */
    private static void walls(Blueprint blueprint, Box box, int story, Paint paint) {
        Frame frame = blueprint.frame();
        int floor = box.g() + 1 + STORY * (story - 1);
        int from = floor + 1;
        int to = floor + STORY - 1;
        int left = box.left();
        int right = box.right();
        int front = box.front();
        int back = box.back();
        blueprint.fill(left + 1, right - 1, front, front, from, to, Role.WALL, paint.paint(), FULL, WALLS);
        blueprint.fill(left + 1, right - 1, back, back, from, to, Role.WALL, paint.paint(), FULL, WALLS);
        blueprint.fill(left, left, front + 1, back - 1, from, to, Role.WALL, paint.paint(), FULL, WALLS);
        blueprint.fill(right, right, front + 1, back - 1, from, to, Role.WALL, paint.paint(), FULL, WALLS);
        int sill = floor + 2;
        int door = box.door();
        // pairs either side of the door, a little further out on a wider house
        int near = (right - left) / 2 >= 6 ? 3 : 2;
        int[] pairs = {door - near - 1, door + near};
        for (int a : pairs) {
            window(blueprint, a, front, sill, 2, WALLS);
            window(blueprint, a + 1, front, sill, 2, WALLS);
            window(blueprint, a, back, sill, 2, WALLS);
            window(blueprint, a + 1, back, sill, 2, WALLS);
            if (paint.shutters()) {
                shutters(blueprint, sill, front - 1, a - 1, a + 2, frame.front(), false);
            }
        }
        for (int b : new int[] {front + 2, back - 3}) {
            for (int a : new int[] {left, right}) {
                window(blueprint, a, b, sill, 2, WALLS);
                window(blueprint, a, b + 1, sill, 2, WALLS);
            }
        }
        if (back - front >= 10) {
            int middle = (front + back) / 2;
            window(blueprint, paint.chimneyRight() ? left : right, middle, sill, 2, WALLS);
        }
        if (story == 1) {
            door(blueprint, door, front, from, frame.back(), WALLS);
            window(blueprint, door, front, from + 2, 1, WALLS);
        } else {
            window(blueprint, door, front, sill, 2, WALLS);
        }
    }

    /**
     * A stair up the left wall from the back of the living room, through a hole in the floor above, with a rail beside
     * it upstairs.
     */
    private static void stairway(Blueprint blueprint, Box box) {
        stairway(blueprint, box.left() + 1, box.left() + 2, box.back() - 1, box.g() + 1);
    }

    /**
     * A stair up one side of a house from the back of its ground floor, a step for every block up, its top step set in
     * the floor above; through a hole in that floor over the steps below, with a rail along it upstairs.
     *
     * @param a      the line of it, across the house
     * @param rail   the line of the rail beside it, upstairs
     * @param bottom where its bottom step is, back from the street
     * @param floor  the height of the ground floor
     */
    private static void stairway(Blueprint blueprint, int a, int rail, int bottom, int floor) {
        Frame frame = blueprint.frame();
        int upstairs = floor + STORY;
        for (int i = 0; i < STORY; i++) {
            blueprint.set(a, bottom - i, floor + 1 + i, Role.STEP, stairs(frame.front(), Half.BOTTOM), WALLS);
        }
        for (int b = bottom - STORY + 2; b <= bottom; b++) {
            blueprint.set(a, b, upstairs, Role.CLEAR, AIR, WALLS);
            blueprint.set(rail, b, upstairs + 1, Role.PORCH_POST, FENCE, INTERIOR);
        }
    }

    /**
     * @return the height of the top of the roof
     */
    private static int roof(Blueprint blueprint, Box box, Roof roof, Paint paint) {
        int base = box.top() + 1;
        int left = box.left();
        int right = box.right();
        int front = box.front();
        int back = box.back();
        return switch (roof) {
            case SIDE_GABLE -> {
                int ridge = gable(blueprint, left, right, front, back, base, true, Role.WALL, paint.paint(), ROOF);
                int middle = (front + back) / 2;
                int tall = ridge - base >= 4 ? 2 : 1;
                window(blueprint, left, middle, base + 1, tall, ROOF);
                window(blueprint, right, middle, base + 1, tall, ROOF);
                yield ridge;
            }
            case FRONT_GABLE -> {
                int ridge = gable(blueprint, left, right, front, back, base, false, Role.WALL, paint.paint(), ROOF);
                int tall = ridge - base >= 4 ? 2 : 1;
                window(blueprint, box.door(), front, base + 1, tall, ROOF);
                window(blueprint, box.door(), back, base + 1, tall, ROOF);
                yield ridge;
            }
            case HIP -> hipRoof(blueprint, left - 1, right + 1, front - 1, back + 1, base);
        };
    }

    /**
     * A gabled roof over a box, its eaves standing a block out all round, climbing a block for every block in from the
     * eaves to its ridge, the gable ends walled in up under it.
     *
     * @param across whether its ridge runs across the front, side to side, rather than back from it
     * @return the height of its ridge
     */
    static int gable(Blueprint blueprint, int left, int right, int front, int back, int base, boolean across, Role walls, int paint, int stage) {
        Frame frame = blueprint.frame();
        int lo = across ? front - 1 : left - 1;
        int hi = across ? back + 1 : right + 1;
        int from = across ? left - 1 : front - 1;
        int to = across ? right + 1 : back + 1;
        int ridge = base;
        for (int s = lo; s <= hi; s++) {
            int rise = Math.min(s - lo, hi - s);
            int y = base + rise;
            ridge = Math.max(ridge, y);
            boolean middle = hi - lo == 2 * rise;
            Direction up = s - lo < hi - s ? (across ? frame.back() : frame.right()) : (across ? frame.front() : frame.left());
            for (int t = from; t <= to; t++) {
                int a = across ? t : s;
                int b = across ? s : t;
                if (middle) {
                    blueprint.set(a, b, y - 1, Role.ROOF_RIDGE, FULL, stage);
                    blueprint.set(a, b, y, Role.ROOF_SLAB, slab(SlabType.BOTTOM), stage);
                } else {
                    blueprint.set(a, b, y, Role.ROOF, stairs(up, Half.BOTTOM), stage);
                }
            }
            boolean over = across ? s >= front && s <= back : s >= left && s <= right;
            int under = middle ? y - 2 : y - 1;
            if (over && under >= base) {
                for (int t : across ? new int[] {left, right} : new int[] {front, back}) {
                    int a = across ? t : s;
                    int b = across ? s : t;
                    blueprint.fill(a, a, b, b, base, under, walls, paint, FULL, stage);
                }
            }
        }
        return ridge;
    }

    /**
     * A hipped roof: stairs climbing in from all four eaves, each ring a step higher and smaller than the one under it,
     * to a short ridge.
     *
     * @return the height of its ridge
     */
    private static int hipRoof(Blueprint blueprint, int left, int right, int front, int back, int y) {
        Frame frame = blueprint.frame();
        while (right - left >= 2 && back - front >= 2) {
            for (int a = left; a <= right; a++) {
                blueprint.set(a, front, y, Role.ROOF, stairs(frame.back(), Half.BOTTOM), ROOF);
                blueprint.set(a, back, y, Role.ROOF, stairs(frame.front(), Half.BOTTOM), ROOF);
            }
            for (int b = front + 1; b < back; b++) {
                blueprint.set(left, b, y, Role.ROOF, stairs(frame.right(), Half.BOTTOM), ROOF);
                blueprint.set(right, b, y, Role.ROOF, stairs(frame.left(), Half.BOTTOM), ROOF);
            }
            left++;
            right--;
            front++;
            back--;
            y++;
        }
        blueprint.fill(left, right, front, back, y - 1, y - 1, Role.ROOF_RIDGE, FULL, ROOF);
        blueprint.fill(left, right, front, back, y, y, Role.ROOF_SLAB, slab(SlabType.BOTTOM), ROOF);
        return y;
    }

    /**
     * Out in front of the door, raised level with the floor, with a step up to it from the walk.
     */
    private static void porch(Blueprint blueprint, Box box, Porch porch) {
        Frame frame = blueprint.frame();
        int g = box.g();
        int front = box.front();
        int door = box.door();
        int over = g + STORY;
        switch (porch) {
            case STOOP, PORTICO -> {
                blueprint.fill(door - 1, door + 1, front - 2, front - 1, g + 1, g + 1, Role.PORCH, FULL, BASE);
                for (int a : new int[] {door - 1, door + 1}) {
                    blueprint.fill(a, a, front - 2, front - 2, g + 2, over - 1, Role.PORCH_POST, FENCE, EXTERIOR);
                }
                if (porch == Porch.STOOP) {
                    blueprint.fill(door - 2, door + 2, front - 2, front - 1, over, over, Role.ROOF_SLAB, slab(SlabType.TOP), EXTERIOR);
                } else {
                    // a little gable, its pediment over the posts
                    for (int i = -2; i <= 2; i++) {
                        int rise = 2 - Math.abs(i);
                        for (int b = front - 2; b <= front - 1; b++) {
                            if (i == 0) {
                                blueprint.set(door, b, over + rise - 1, Role.ROOF_RIDGE, FULL, EXTERIOR);
                                blueprint.set(door, b, over + rise, Role.ROOF_SLAB, slab(SlabType.BOTTOM), EXTERIOR);
                            } else {
                                blueprint.set(door + i, b, over + rise, Role.ROOF, stairs(i < 0 ? frame.right() : frame.left(), Half.BOTTOM), EXTERIOR);
                            }
                        }
                        if (Math.abs(i) <= 1) {
                            blueprint.set(door + i, front - 2, over, Role.TRIM, pillar(frame.acrossAxis()), EXTERIOR);
                        }
                    }
                }
            }
            case FULL, WRAP -> {
                int left = box.left();
                int right = box.right();
                blueprint.fill(left, right, front - 2, front - 1, g + 1, g + 1, Role.PORCH, FULL, BASE);
                int corner = porch == Porch.WRAP ? right + 2 : right;
                for (int a = left; a <= corner; a++) {
                    boolean post = a == left || a == corner || a == door - 2 || a == door + 2;
                    if (post) {
                        blueprint.fill(a, a, front - 2, front - 2, g + 2, over - 1, Role.PORCH_POST, FENCE, EXTERIOR);
                    } else if (Math.abs(a - door) > 1) {
                        blueprint.set(a, front - 2, g + 2, Role.PORCH_POST, FENCE, EXTERIOR);
                    }
                }
                blueprint.fill(left - 1, corner + 1, front - 2, front - 1, over, over, Role.ROOF_SLAB, slab(SlabType.TOP), EXTERIOR);
                if (porch == Porch.WRAP) {
                    // and round the right side
                    int end = front + 4;
                    blueprint.fill(right + 1, right + 2, front - 2, end, g + 1, g + 1, Role.PORCH, FULL, BASE);
                    for (int b = front - 1; b <= end; b++) {
                        boolean post = b == front + 1 || b == end;
                        if (post) {
                            blueprint.fill(right + 2, right + 2, b, b, g + 2, over - 1, Role.PORCH_POST, FENCE, EXTERIOR);
                        } else {
                            blueprint.set(right + 2, b, g + 2, Role.PORCH_POST, FENCE, EXTERIOR);
                        }
                    }
                    blueprint.fill(right + 1, right + 3, front - 2, end, over, over, Role.ROOF_SLAB, slab(SlabType.TOP), EXTERIOR);
                }
            }
        }
        blueprint.set(door, front - 3, g + 1, Role.STEP, stairs(frame.back(), Half.BOTTOM), EXTERIOR);
    }

    /**
     * A garage built on to one side, a story high under a flat roof, its door of folding panels, and a driveway out to
     * the street.
     *
     * @return the columns across the lot the driveway takes
     */
    private static int[] garage(Blueprint blueprint, Box box, int side) {
        Frame frame = blueprint.frame();
        int g = box.g();
        int a0 = side > 0 ? box.right() + 1 : box.left() - GARAGE_WIDTH;
        int a1 = a0 + GARAGE_WIDTH - 1;
        int outer = side > 0 ? a1 : a0;
        int front = box.front() + 1;
        int back = front + GARAGE_DEPTH - 1;
        int top = g + STORY - 1;
        blueprint.fill(a0, a1, front, back, g, g, Role.FOUNDATION, FULL, BASE);
        for (int b : new int[] {front, back}) {
            blueprint.fill(outer, outer, b, b, g + 1, top, Role.TRIM, pillar(Direction.Axis.Y), FRAME);
        }
        int wallFrom = side > 0 ? a0 : a0 + 1;
        int wallTo = side > 0 ? a1 - 1 : a1;
        blueprint.fill(wallFrom, wallTo, front, front, g + 1, top, Role.WALL, 0, FULL, WALLS);
        blueprint.fill(wallFrom, wallTo, back, back, g + 1, top, Role.WALL, 0, FULL, WALLS);
        blueprint.fill(outer, outer, front + 1, back - 1, g + 1, top, Role.WALL, 0, FULL, WALLS);
        window(blueprint, outer, (front + back) / 2, g + 2, 2, WALLS);
        // its door: three across and three high, folding panels standing in the opening
        int doorFrom = side > 0 ? a0 + 1 : a1 - 3;
        BlockState panel = Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, frame.front()).setValue(TrapDoorBlock.OPEN, true);
        blueprint.fill(doorFrom, doorFrom + 2, front, front, g + 1, g + 3, Role.SHUTTER, panel, WALLS);
        blueprint.fill(a0, a1, front - 1, back + 1, top + 1, top + 1, Role.ROOF_SLAB, slab(SlabType.BOTTOM), ROOF);
        blueprint.fill(outer, outer, front - 1, back + 1, top + 1, top + 1, Role.ROOF_RIDGE, FULL, ROOF);
        blueprint.set(doorFrom + 1, front - 1, top, Role.LAMP, lantern(true), EXTERIOR);
        for (int b = 0; b < front; b++) {
            blueprint.fill(doorFrom, doorFrom + 2, b, b, g, g, Role.SIDEWALK, FULL, YARD);
        }
        // the family car, parked nose in on the driveway
        blueprint.car(doorFrom + 1, 1, front - 2, g + 1, frame.back(), Math.floorMod(Long.hashCode(frame.origin().asLong()), 5));
        return new int[] {doorFrom, doorFrom + 2};
    }

    /**
     * The fence or hedge along the sidewalk with its gate, the walk up to the porch step, the mailbox by the gate, a
     * hedge at each front corner, beds of flowers along the front, and maybe a shade tree.
     *
     * @param driveFrom where the driveway crosses the front of the lot, if it does
     */
    private static void yard(Blueprint blueprint, Box box, Porch porch, Paint paint, int driveFrom, int driveTo, boolean room) {
        Frame frame = blueprint.frame();
        int g = box.g();
        int door = box.door();
        int last = TownPlan.LOT_WIDTH - 1;
        for (int a = 0; a <= last; a++) {
            if (a == door || a >= driveFrom && a <= driveTo) {
                continue;
            }
            if (paint.hedgeFront()) {
                blueprint.set(a, 0, g + 1, Role.HEDGE, LEAVES, YARD);
            } else {
                blueprint.set(a, 0, g + 1, Role.PICKET, FENCE, YARD);
            }
        }
        blueprint.set(door, 0, g + 1, Role.GATE, gate(frame.back()), YARD);
        for (int b = 0; b <= box.front() - 4; b++) {
            blueprint.set(door, b, g, Role.WALKWAY, FULL, YARD);
        }
        int mailbox = driveFrom < door ? door + 2 : door - 2;
        blueprint.set(mailbox, 1, g + 1, Role.MAILBOX_POST, FENCE, YARD);
        blueprint.set(mailbox, 1, g + 2, Role.MAILBOX, FULL, YARD);
        for (int a : new int[] {0, last}) {
            if (a < driveFrom || a > driveTo) {
                blueprint.set(a, 1, g + 1, Role.HEDGE, LEAVES, YARD);
            }
        }
        // beds along the wall beside the porch, or along the front of a porch across the house
        boolean wide = porch == Porch.FULL || porch == Porch.WRAP;
        int bed = wide ? box.front() - 3 : box.front() - 1;
        int to = porch == Porch.WRAP ? box.right() + 2 : box.right();
        for (int a = box.left(); a <= to; a++) {
            int off = Math.abs(a - door);
            if (wide ? off == 0 : off <= 1) {
                continue;
            }
            blueprint.set(a, bed, g + 1, Role.FLOWER, a < door ? paint.flowers() : paint.otherFlowers(), FLOWER, YARD);
        }
        if (paint.tree() && room && !wide) {
            shadeTree(blueprint, mailbox < door ? last - 2 : 2, 2, g, YARD);
        }
    }

    // ---------------------------------------------------------------- rooms

    /**
     * Downstairs, the living room of a sitcom: a couch against the back wall facing the television over a coffee table
     * on a rug, a telephone and a lamp on the end tables either side, a clock and pictures on the wall over it, an
     * armchair, shelves by the door with the radio on them; the kitchen along the right wall, refrigerator, range,
     * sink and a toaster on the counter, with a table and chairs; and a light hanging over it all. Upstairs, a bed
     * between two nightstands, a lamp, a rug, a dresser, a poster and a light.
     */
    private static void rooms(Blueprint blueprint, Box box) {
        Frame frame = blueprint.frame();
        int g = box.g();
        int left = box.left() + 1;
        int right = box.right() - 1;
        int front = box.front() + 1;
        int back = box.back() - 1;
        int c = box.door();
        int y = g + 2;
        boolean stairs = box.stories() > 1;
        blueprint.fill(c - 2, c + 2, front + 2, back - 1, y, y, Role.RUG, CARPET, INTERIOR);
        for (int a = c - 1; a <= c + 1; a++) {
            blueprint.set(a, back, y, Role.COUCH, decor(ScarletBlocks.COUCH, frame.front()), INTERIOR);
        }
        blueprint.set(c, back - 2, y, Role.TABLE, slab(SlabType.TOP), INTERIOR);
        blueprint.set(c - 2, back, y, Role.TABLE, slab(SlabType.TOP), INTERIOR);
        blueprint.set(c - 2, back, y + 1, Role.TELEPHONE, decor(ScarletBlocks.TELEPHONE, frame.front()), INTERIOR);
        blueprint.set(c + 2, back, y, Role.TABLE, slab(SlabType.TOP), INTERIOR);
        blueprint.set(c + 2, back, y + 1, Role.TABLE_LAMP, lit(decor(ScarletBlocks.LAMP, frame.front())), INTERIOR);
        blueprint.set(c, back, y + 3, Role.CLOCK, decor(ScarletBlocks.WALL_CLOCK, frame.front()), INTERIOR);
        blueprint.set(c - 1, back, y + 2, Role.PICTURE, decor(ScarletBlocks.PICTURE_FRAME, frame.front()), INTERIOR);
        blueprint.set(c + 1, back, y + 2, Role.PICTURE, decor(ScarletBlocks.PICTURE_FRAME, frame.front()), INTERIOR);
        // the television by the front wall, turned to the couch, and an armchair drawn up beside the rug
        blueprint.set(c - 2, front + 1, y, Role.TELEVISION, lit(decor(ScarletBlocks.TELEVISION, frame.back())), INTERIOR);
        blueprint.set(c - 3, back - 1, y, Role.ARMCHAIR, decor(ScarletBlocks.ARMCHAIR, frame.front()), INTERIOR);
        blueprint.fill(left, left, front, front + 1, y, y + 1, Role.SHELF, FULL, INTERIOR);
        blueprint.set(left, front + 1, y + 2, Role.RADIO, decor(ScarletBlocks.RADIO, frame.right()), INTERIOR);
        blueprint.set(left + 1, front + 2, y, Role.PLANT, POT, INTERIOR);
        if (!stairs) {
            blueprint.set(left, back, y, Role.PORCH_POST, FENCE, INTERIOR);
            blueprint.set(left, back, y + 1, Role.LAMP, lantern(false), INTERIOR);
        }
        fridge(blueprint, right, back, y, frame.left(), INTERIOR);
        blueprint.fill(right, right, back - 3, back - 1, y, y, Role.COUNTER, FULL, INTERIOR);
        blueprint.set(right, back - 1, y, Role.STOVE, decor(ScarletBlocks.STOVE, frame.left()), INTERIOR);
        blueprint.set(right, back - 2, y, Role.SINK, CAULDRON, INTERIOR);
        blueprint.set(right, back - 3, y + 1, Role.TOASTER, decor(ScarletBlocks.TOASTER, frame.left()), INTERIOR);
        blueprint.set(right - 2, front + 1, y, Role.TABLE, slab(SlabType.TOP), INTERIOR);
        blueprint.set(right - 3, front + 1, y, Role.BENCH, stairs(frame.left(), Half.BOTTOM), INTERIOR);
        blueprint.set(right - 1, front + 1, y, Role.BENCH, stairs(frame.right(), Half.BOTTOM), INTERIOR);
        blueprint.set(c, back - 2, g + STORY, Role.LAMP, lantern(true), INTERIOR);
        if (!stairs) {
            return;
        }
        int up = y + STORY;
        blueprint.fill(c - 1, right - 1, front + 1, back - 2, up, up, Role.RUG, CARPET, INTERIOR);
        bed(blueprint, c, back - 1, up, frame.back(), INTERIOR);
        bed(blueprint, c + 1, back - 1, up, frame.back(), INTERIOR);
        blueprint.set(c - 1, back, up, Role.TABLE, slab(SlabType.TOP), INTERIOR);
        blueprint.set(c - 1, back, up + 1, Role.PLANT, POT, INTERIOR);
        blueprint.set(c + 2, back, up, Role.TABLE, slab(SlabType.TOP), INTERIOR);
        blueprint.set(c + 2, back, up + 1, Role.TABLE_LAMP, lit(decor(ScarletBlocks.LAMP, frame.front())), INTERIOR);
        blueprint.set(c - 1, front, up + 1, Role.POSTER, decor(ScarletBlocks.POSTER, frame.back()), INTERIOR);
        blueprint.fill(right, right, front, front + 1, up, up + 1, Role.SHELF, FULL, INTERIOR);
        blueprint.set(c, (front + back) / 2, box.top() - 1, Role.LAMP, lantern(true), INTERIOR);
    }

    /** An era decoration, facing the way given, in the present day until the town's era is put on it. */
    static BlockState decor(Supplier<Block> block, Direction facing) {
        return block.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
    }

    /** The same, switched on: a lamp lit, a television showing its program. */
    static BlockState lit(BlockState state) {
        return state.hasProperty(BlockStateProperties.LIT) ? state.setValue(BlockStateProperties.LIT, true) : state;
    }

    /** A refrigerator against a wall, both its halves, facing out into the room. */
    static void fridge(Blueprint blueprint, int a, int b, int y, Direction facing, int stage) {
        BlockState fridge = decor(ScarletBlocks.REFRIGERATOR, facing);
        blueprint.set(a, b, y, Role.FRIDGE, fridge.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER), stage);
        blueprint.set(a, b, y + 1, Role.FRIDGE, fridge.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), stage);
    }

    // ---------------------------------------------------------------- a home made of what stands

    /**
     * What stands of a building a home is made of, on its lot's grid.
     *
     * @param floor  the height of its floor
     * @param top    the height of the top of its walls
     * @param roofed whether something already stands over most of it
     * @param door   where across its front its door is, or {@link #NO_DOOR}
     */
    record Shell(int left, int right, int front, int back, int floor, int top, boolean roofed, int door) {

        /**
         * Where across its front its door is, or goes if it has none: in line with the middle of the lot, where its
         * caster floats, so they walk straight out of it.
         */
        int doorAt() {
            if (door != NO_DOOR) {
                return door;
            }
            return right - left >= 2 ? Math.clamp(TownPlan.LOT_WIDTH / 2, left + 1, right - 1) : (left + right) / 2;
        }
    }

    /**
     * A home made of what already stands where it goes: a house, a ruin, a shell of walls, a bare foundation. Nothing of
     * it is torn down; what stands keeps its shape and is made over in the era's look, and the home is finished around
     * it. Its floor is laid wherever it has none, its walls are closed up where they have fallen, with windows in them,
     * a door is put in its front if it has none, and a roof goes on if it has none, under a ceiling. What has no roof
     * yet and room enough rises two stories on what stands, as a home does, with a stair up to a bedroom. Then a porch
     * at its door, the yard round it, and a living room inside, all wherever there is room.
     *
     * @param occupied which columns of the lot it stands in, by b * width + a
     * @param siding   the paint its walls are made over in, for the walls it is finished with to match
     */
    static Blueprint finished(Frame frame, int g, Heights heights, boolean[] occupied, Shell shell, int siding, RandomSource random) {
        Blueprint blueprint = new Blueprint(frame, ANCHOR_TICKS);
        Paint style = Paint.pick(random);
        Paint paint = new Paint(siding, style.flowers(), style.otherFlowers(), style.chimney(), style.chimneyRight(), style.shutters(),
                style.hedgeFront(), style.tree());
        int width = TownPlan.LOT_WIDTH;
        int depth = TownPlan.LOT_DEPTH;
        int left = shell.left();
        int right = shell.right();
        int front = shell.front();
        int back = shell.back();
        int floor = shell.floor();
        int stories = !shell.roofed() && right - left >= 6 && back - front >= 6 ? 2 : 1;
        int top = Math.max(shell.top(), floor + STORY * stories - 1);
        int door = shell.doorAt();
        // the yard round it, leveled and turfed, but never under it
        for (int b = 0; b < depth; b++) {
            for (int a = 0; a < width; a++) {
                boolean under = a >= left && a <= right && b >= front && b <= back;
                if (under || occupied[b * width + a]) {
                    continue;
                }
                int height = heights.at(a, b);
                if (height > g) {
                    blueprint.fill(a, a, b, b, g + 1, height + 2, Role.CLEAR, AIR, GROUND);
                } else {
                    blueprint.fill(a, a, b, b, g + 1, g + 2, Role.CLEAR, AIR, GROUND);
                    if (height < g) {
                        blueprint.fill(a, a, b, b, height + 1, g - 1, Role.FILL, FULL, GROUND);
                    }
                }
                blueprint.set(a, b, g, Role.LAWN, FULL, GROUND);
            }
        }
        // its floor, and the stone round its foot
        blueprint.fill(left, right, front, back, floor, floor, Role.FOUNDATION, FULL, BASE);
        blueprint.fill(left + 1, right - 1, front + 1, back - 1, floor, floor, Role.FLOOR, FULL, BASE);
        // its corners, and its walls wherever they have fallen, with windows every few blocks; a building with its roof
        // on is whole, and keeps its shape, porches and all
        if (!shell.roofed()) {
            for (int a : new int[] {left, right}) {
                for (int b : new int[] {front, back}) {
                    blueprint.fill(a, a, b, b, floor + 1, top, Role.TRIM, pillar(Direction.Axis.Y), FRAME);
                }
            }
            for (int a = left + 1; a < right; a++) {
                boolean glazed = (a - left) % 3 != 0 && a != door;
                for (int b : new int[] {front, back}) {
                    wallColumn(blueprint, a, b, floor, top, glazed, paint.paint());
                }
            }
            for (int b = front + 1; b < back; b++) {
                boolean glazed = (b - front) % 3 != 0;
                for (int a : new int[] {left, right}) {
                    wallColumn(blueprint, a, b, floor, top, glazed, paint.paint());
                }
            }
        }
        if (shell.door() == NO_DOOR) {
            door(blueprint, door, front, floor + 1, frame.back(), WALLS);
        }
        Direction.Axis across = frame.acrossAxis();
        Direction.Axis along = frame.depthAxis();
        // a floor over the ground floor, on a beam round the walls, and a stair up to it along the side with more room
        int stair = NO_DOOR;
        if (stories > 1) {
            int over = floor + STORY;
            blueprint.fill(left + 1, right - 1, front, front, over, over, Role.TRIM, pillar(across), FRAME);
            blueprint.fill(left + 1, right - 1, back, back, over, over, Role.TRIM, pillar(across), FRAME);
            blueprint.fill(left, left, front + 1, back - 1, over, over, Role.TRIM, pillar(along), FRAME);
            blueprint.fill(right, right, front + 1, back - 1, over, over, Role.TRIM, pillar(along), FRAME);
            blueprint.fill(left + 1, right - 1, front + 1, back - 1, over, over, Role.FLOOR, FULL, WALLS);
            stair = door - left > right - door ? left + 1 : right - 1;
            stairway(blueprint, stair, stair == left + 1 ? stair + 1 : stair - 1, back - 1, floor);
        }
        // a roof over it, if it has none
        if (!shell.roofed()) {
            blueprint.fill(left + 1, right - 1, front, front, top + 1, top + 1, Role.TRIM, pillar(across), FRAME);
            blueprint.fill(left + 1, right - 1, back, back, top + 1, top + 1, Role.TRIM, pillar(across), FRAME);
            blueprint.fill(left, left, front, back, top + 1, top + 1, Role.TRIM, pillar(along), FRAME);
            blueprint.fill(right, right, front, back, top + 1, top + 1, Role.TRIM, pillar(along), FRAME);
            blueprint.fill(left + 1, right - 1, front + 1, back - 1, top + 1, top + 1, Role.CEILING, FULL, WALLS);
            gable(blueprint, left, right, front, back, top + 2, right - left >= back - front, Role.WALL, paint.paint(), ROOF);
        }
        // a stoop at its door, and steps down from it to the lawn if it stands high
        if (front >= 3 && floor >= g) {
            blueprint.fill(door - 1, door + 1, front - 2, front - 1, floor, floor, Role.PORCH, FULL, BASE);
            for (int a : new int[] {door - 1, door + 1}) {
                blueprint.fill(a, a, front - 2, front - 2, floor + 1, floor + 3, Role.PORCH_POST, FENCE, EXTERIOR);
            }
            blueprint.fill(door - 2, door + 2, front - 2, front - 1, floor + 4, floor + 4, Role.ROOF_SLAB, slab(SlabType.TOP), EXTERIOR);
            for (int k = 0; k <= floor - g - 1 && front - 3 - k >= 1; k++) {
                blueprint.set(door, front - 3 - k, floor - k, Role.STEP, stairs(frame.back(), Half.BOTTOM), EXTERIOR);
            }
        }
        // the fence along the street with its gate, the walk up to the door, the mailbox and the beds
        for (int a = 0; a < width; a++) {
            if (a == door || occupied[a]) {
                continue;
            }
            blueprint.set(a, 0, g + 1, paint.hedgeFront() ? Role.HEDGE : Role.PICKET, paint.hedgeFront() ? LEAVES : FENCE, YARD);
        }
        blueprint.set(door, 0, g + 1, Role.GATE, gate(frame.back()), YARD);
        for (int b = 0; b <= front - 4 - Math.max(0, floor - g); b++) {
            blueprint.set(door, b, g, Role.WALKWAY, FULL, YARD);
        }
        if (door - 2 >= 0 && !occupied[width + door - 2]) {
            blueprint.set(door - 2, 1, g + 1, Role.MAILBOX_POST, FENCE, YARD);
            blueprint.set(door - 2, 1, g + 2, Role.MAILBOX, FULL, YARD);
        }
        for (int a = left; a <= right && front >= 2; a++) {
            if (Math.abs(a - door) > 2) {
                blueprint.set(a, front - 1, g + 1, Role.FLOWER, a < door ? paint.flowers() : paint.otherFlowers(), FLOWER, YARD);
            }
        }
        // and a living room in it, behind where its caster floats, so nothing stands in their way out of the door: a
        // couch facing a table on a rug, a lamp, a plant
        int y = floor + 1;
        int inside = back - 1;
        if (right - left >= 4 && inside > TownPlan.LIVING_ROOM) {
            blueprint.fill(door - 1, door + 1, front + 2, inside - 1, y, y, Role.RUG, CARPET, INTERIOR);
            for (int a = door - 1; a <= door + 1; a++) {
                blueprint.set(a, inside, y, Role.COUCH, decor(ScarletBlocks.COUCH, frame.front()), INTERIOR);
            }
            if (door - 2 > left) {
                // the television by the front wall, turned to the couch
                blueprint.set(door - 2, front + 1, y, Role.TELEVISION, lit(decor(ScarletBlocks.TELEVISION, frame.back())), INTERIOR);
            }
            if (inside - 2 > TownPlan.LIVING_ROOM) {
                blueprint.set(door, inside - 2, y, Role.TABLE, slab(SlabType.TOP), INTERIOR);
            }
            if (stair != left + 1) {
                blueprint.set(left + 1, inside, y, Role.PORCH_POST, FENCE, INTERIOR);
                blueprint.set(left + 1, inside, y + 1, Role.LAMP, lantern(false), INTERIOR);
            }
            if (stair != right - 1) {
                blueprint.set(right - 1, inside, y, Role.TABLE, slab(SlabType.TOP), INTERIOR);
                blueprint.set(right - 1, inside, y + 1, Role.PLANT, POT, INTERIOR);
            }
            blueprint.set(door, inside - 2, Math.min(top, floor + STORY - 1), Role.LAMP, lantern(true), INTERIOR);
        }
        // upstairs, a bed on the far side from the stair, a nightstand and a lamp beside it, a rug at its foot
        if (stories > 1) {
            int up = floor + STORY + 1;
            int bed = stair == left + 1 ? right - 2 : left + 1;
            bed(blueprint, bed, back - 2, up, frame.back(), INTERIOR);
            bed(blueprint, bed + 1, back - 2, up, frame.back(), INTERIOR);
            int nightstand = stair == left + 1 ? bed - 1 : bed + 2;
            blueprint.set(nightstand, back - 1, up, Role.TABLE, slab(SlabType.TOP), INTERIOR);
            blueprint.set(nightstand, back - 1, up + 1, Role.LAMP, lantern(false), INTERIOR);
            blueprint.fill(bed, bed + 1, back - 4, back - 3, up, up, Role.RUG, CARPET, INTERIOR);
            blueprint.set(door, (front + back) / 2, top, Role.LAMP, lantern(true), INTERIOR);
        }
        return blueprint;
    }

    /**
     * One column of a wall, from the floor to its top: siding, with a window two high in it on every story it stands
     * the whole height of, if it is to have them.
     */
    private static void wallColumn(Blueprint blueprint, int a, int b, int floor, int top, boolean glazed, int paint) {
        for (int y = floor + 1; y <= top; y++) {
            int up = (y - floor) % STORY;
            boolean pane = glazed && (up == 2 || up == 3) && top >= y - up + STORY - 1;
            if (pane) {
                blueprint.set(a, b, y, Role.WINDOW, PANE, WALLS);
            } else {
                blueprint.set(a, b, y, Role.WALL, paint, FULL, WALLS);
            }
        }
    }

    // ---------------------------------------------------------------- other lots

    /**
     * A little park in place of a house: a lawn behind low hedges, open to the street, a walk up through it to a round
     * with benches facing each other, shade trees, beds of flowers, and a lamp by the round.
     */
    public static Blueprint park(Frame frame, int ground, Heights heights, RandomSource random) {
        Blueprint blueprint = new Blueprint(frame, GARDEN_TICKS);
        int width = TownPlan.LOT_WIDTH;
        int depth = TownPlan.LOT_DEPTH;
        level(blueprint, width, depth, ground, heights);
        int middle = width / 2;
        int round = depth / 2;
        for (int b = 2; b < depth; b++) {
            blueprint.set(0, b, ground + 1, Role.HEDGE, LEAVES, 1);
            blueprint.set(width - 1, b, ground + 1, Role.HEDGE, LEAVES, 1);
        }
        for (int a = 1; a < width - 1; a++) {
            blueprint.set(a, depth - 1, ground + 1, Role.HEDGE, LEAVES, 1);
        }
        for (int b = 0; b < depth - 1; b++) {
            blueprint.set(middle, b, ground, Role.WALKWAY, FULL, 1);
        }
        for (int da = -2; da <= 2; da++) {
            for (int db = -2; db <= 2; db++) {
                if (Math.abs(da) + Math.abs(db) <= 3) {
                    blueprint.set(middle + da, round + db, ground, Role.WALKWAY, FULL, 1);
                }
            }
        }
        for (int db = -1; db <= 1; db++) {
            blueprint.set(middle - 3, round + db, ground + 1, Role.BENCH, stairs(frame.left(), Half.BOTTOM), 2);
            blueprint.set(middle + 3, round + db, ground + 1, Role.BENCH, stairs(frame.right(), Half.BOTTOM), 2);
        }
        blueprint.fill(middle + 2, middle + 2, round - 3, round - 3, ground + 1, ground + 3, Role.LAMP_POST, FENCE, 2);
        blueprint.set(middle + 2, round - 3, ground + 4, Role.LAMP, lantern(false), 2);
        boolean mirrored = random.nextBoolean();
        int near = mirrored ? width - 4 : 3;
        int far = mirrored ? 3 : width - 4;
        shadeTree(blueprint, near, 4, ground, 2);
        shadeTree(blueprint, far, depth - 5, ground, 2);
        int kind = random.nextInt(EraStyle.FLOWERS);
        int other = random.nextInt(EraStyle.FLOWERS);
        for (int k = 0; k < 4; k++) {
            blueprint.set(far + (mirrored ? k : -k), 4, ground + 1, Role.FLOWER, kind, FLOWER, 2);
            blueprint.set(near + (mirrored ? -k : k), depth - 5, ground + 1, Role.FLOWER, other, FLOWER, 2);
        }
        return blueprint;
    }

    /**
     * A lot the wall cuts through, with too little of it inside the Hex for a house: a lawn behind a picket fence, a
     * hedge round its other sides, a shade tree and a bed of flowers by the path. Whatever lies past the wall is left
     * off it.
     */
    public static Blueprint garden(Frame frame, int ground, Heights heights, RandomSource random) {
        Blueprint blueprint = new Blueprint(frame, GARDEN_TICKS);
        int width = TownPlan.LOT_WIDTH;
        int depth = TownPlan.LOT_DEPTH;
        level(blueprint, width, depth, ground, heights);
        int middle = width / 2;
        for (int a = 0; a < width; a++) {
            if (a == middle) {
                blueprint.set(a, 0, ground + 1, Role.GATE, gate(frame.back()), 1);
            } else {
                blueprint.set(a, 0, ground + 1, Role.PICKET, FENCE, 1);
            }
            blueprint.set(a, depth - 1, ground + 1, Role.HEDGE, LEAVES, 1);
        }
        for (int b = 1; b < depth - 1; b++) {
            blueprint.set(0, b, ground + 1, Role.HEDGE, LEAVES, 1);
            blueprint.set(width - 1, b, ground + 1, Role.HEDGE, LEAVES, 1);
        }
        for (int b = 0; b < 5; b++) {
            blueprint.set(middle, b, ground, Role.WALKWAY, FULL, 1);
        }
        boolean treeLeft = random.nextBoolean();
        shadeTree(blueprint, treeLeft ? 4 : width - 5, depth / 2 - 1 + random.nextInt(3), ground, 2);
        int kind = random.nextInt(EraStyle.FLOWERS);
        int bed = treeLeft ? middle + 2 : middle - 5;
        for (int a = bed; a < bed + 4; a++) {
            blueprint.set(a, 3, ground + 1, Role.FLOWER, kind, FLOWER, 2);
        }
        return blueprint;
    }

    /**
     * A lot where a building already stood, which the Hex makes over rather than replaces: around it, wherever it leaves
     * room, the lot is leveled and turfed, with a picket fence along the street, its gate and a walkway up toward the
     * building, a mailbox, and hedges at the front corners.
     *
     * @param occupied which columns the building stands in, by b * width + a
     */
    public static Blueprint around(Frame frame, int ground, Heights heights, boolean[] occupied) {
        Blueprint blueprint = new Blueprint(frame, AROUND_TICKS);
        int width = TownPlan.LOT_WIDTH;
        int depth = TownPlan.LOT_DEPTH;
        for (int b = 0; b < depth; b++) {
            for (int a = 0; a < width; a++) {
                if (occupied[b * width + a]) {
                    continue;
                }
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
        int middle = width / 2;
        for (int a = 0; a < width; a++) {
            if (occupied[a]) {
                continue;
            }
            if (a == middle) {
                blueprint.set(a, 0, ground + 1, Role.GATE, gate(frame.back()), 2);
            } else {
                blueprint.set(a, 0, ground + 1, Role.PICKET, FENCE, 2);
            }
        }
        for (int b = 0; b < depth && !occupied[b * width + middle]; b++) {
            blueprint.set(middle, b, ground, Role.WALKWAY, FULL, 2);
        }
        if (!occupied[width + middle - 2] && !occupied[middle - 2]) {
            blueprint.set(middle - 2, 1, ground + 1, Role.MAILBOX_POST, FENCE, 2);
            blueprint.set(middle - 2, 1, ground + 2, Role.MAILBOX, FULL, 2);
        }
        for (int a : new int[] {0, width - 1}) {
            if (!occupied[width + a]) {
                blueprint.set(a, 1, ground + 1, Role.HEDGE, LEAVES, 2);
            }
        }
        return blueprint;
    }

    /**
     * A round little tree: a trunk four tall under a crown of leaves.
     */
    static void shadeTree(Blueprint blueprint, int a, int b, int g, int stage) {
        for (int dy = 3; dy <= 5; dy++) {
            int spread = dy == 5 ? 1 : 2;
            for (int db = -spread; db <= spread; db++) {
                for (int da = -spread; da <= spread; da++) {
                    if (spread == 2 && Math.abs(da) == 2 && Math.abs(db) == 2) {
                        continue;
                    }
                    blueprint.set(a + da, b + db, g + dy, Role.TREE_LEAVES, LEAVES, stage);
                }
            }
        }
        blueprint.fill(a, a, b, b, g + 1, g + 4, Role.TREE_LOG, pillar(Direction.Axis.Y), stage);
    }

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
     * The stage a block of a house goes up in, by what it is for: how a home taken down a part at a time goes, backward.
     */
    static int stageOf(Role role) {
        return switch (role) {
            case CLEAR, FILL, LAWN, SAND, LANE -> GROUND;
            case FOUNDATION, FLOOR, PORCH -> BASE;
            case TRIM -> FRAME;
            case WALL, WINDOW, DOOR, CEILING, CIVIC_WALL, BARN_WALL, STEP -> WALLS;
            case ROOF, ROOF_SLAB, ROOF_RIDGE -> ROOF;
            case PORCH_POST, CHIMNEY, SHUTTER, AWNING, SIGN, LAMP_POST -> EXTERIOR;
            case WALKWAY, PICKET, GATE, HEDGE, FLOWER, MAILBOX, MAILBOX_POST, TREE_LOG, TREE_LEAVES, ORCHARD_LEAVES, ORCHARD_BLOSSOM,
                 SIDEWALK, ROAD, ROAD_LINE, PLAZA, GAZEBO_POST, BENCH, HAY, PLAY_FRAME, SLIDE, CHAIN, BELL, PRESERVE -> YARD;
            case RUG, COUCH, TABLE, SHELF, FRIDGE, COUNTER, SINK, PLANT, LAMP, BED, TELEVISION, RADIO, TELEPHONE, STOVE, TOASTER, ARMCHAIR,
                 TABLE_LAMP, CLOCK, PICTURE, POSTER -> INTERIOR;
        };
    }

    // ---------------------------------------------------------------- pieces

    static void window(Blueprint blueprint, int a, int b, int sill, int height, int stage) {
        blueprint.fill(a, a, b, b, sill, sill + height - 1, Role.WINDOW, PANE, stage);
    }

    static void door(Blueprint blueprint, int a, int b, int y, Direction facing, int stage) {
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
        blueprint.set(a, b, y, Role.DOOR, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), stage);
        blueprint.set(a, b, y + 1, Role.DOOR, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), stage);
    }

    /**
     * A bed, its foot here and its head the next block on the way it faces.
     */
    static void bed(Blueprint blueprint, int a, int b, int y, Direction facing, int stage) {
        BlockState bed = Blocks.BED.pick(DyeColor.WHITE).defaultBlockState().setValue(BedBlock.FACING, facing);
        blueprint.set(a, b, y, Role.BED, bed.setValue(BedBlock.PART, BedPart.FOOT), stage);
        int da = facing == blueprint.frame().right() ? 1 : facing == blueprint.frame().left() ? -1 : 0;
        int db = facing == blueprint.frame().back() ? 1 : facing == blueprint.frame().front() ? -1 : 0;
        blueprint.set(a + da, b + db, y, Role.BED, bed.setValue(BedBlock.PART, BedPart.HEAD), stage);
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

    /** A sign on the wall behind it, facing out. */
    static BlockState sign(Direction facing) {
        return Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(net.minecraft.world.level.block.WallSignBlock.FACING, facing);
    }
}
