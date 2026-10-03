package com.yashjit.scarlet.hex.town;

import com.yashjit.scarlet.Scarlet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.function.LongPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.BlastFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.SmokerBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * How the Hex rewrites what was already built where its town goes. A village cottage or someone's house isn't torn
 * down but made over in the era's style, the way Westview's own buildings became sitcom houses: its shape stays, and
 * every block of it takes on the part it plays, wall, trim, roof, window, door, shutter, floor, fence, and that part's
 * look in the era. Farms become flower gardens and village paths walkways. What isn't building material, a chest, a
 * bed, a furnace, a workbench, a torch, is left just as it is.
 *
 * <p>A building is told from a heap of blocks or a fence by having a room in it: somewhere enclosed, with something
 * over it.
 */
public final class Restyle {

    /** Building materials the Hex can make over: the {@code scarlet:hex_building_materials} block tag. */
    public static final TagKey<Block> MATERIALS = TagKey.create(Registries.BLOCK, Scarlet.id("hex_building_materials"));
    /** Fewest blocks for something built to count as a building. */
    private static final int MIN_BUILDING = 20;
    /** Most blocks of one building looked at; anything bigger is still a building, just not all of it is seen. */
    private static final int MAX_BUILDING = 6000;
    /** How far past a part the Hex follows a building that reaches into it, to see it whole. */
    private static final int REACH = 14;
    /** Lowest a block of a building's roof sits over the land around it. */
    private static final int ROOF_FROM = 4;
    /** How far from a caster something standing can be for their home to be made of it. */
    private static final int SITE_REACH = 10;
    /** Furthest a front door can be set back into a building, in a porch, and still be its front door. */
    static final int MAX_DOOR_INSET = 2;

    private Restyle() {
    }

    /**
     * Whether a block is part of something built, rather than the land, water, or what grows on it.
     */
    public static boolean isBuilt(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || Terrain.isGround(state) && !state.is(Blocks.DIRT_PATH)) {
            return false;
        }
        Block block = state.getBlock();
        if (block instanceof LiquidBlock || state.canBeReplaced()) {
            return false;
        }
        if (block instanceof LeavesBlock) {
            return state.getValue(LeavesBlock.PERSISTENT);
        }
        if (state.is(BlockTags.LOGS)) {
            // a tree's trunk grows, among its leaves; a cabin's logs are built
            return !touchesLeaves(level, pos);
        }
        return !(block instanceof VegetationBlock) || block instanceof CropBlock;
    }

    private static boolean touchesLeaves(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockState next = level.getBlockState(pos.relative(direction));
            if (next.getBlock() instanceof LeavesBlock && !next.getValue(LeavesBlock.PERSISTENT)) {
                return true;
            }
        }
        BlockState above = level.getBlockState(pos.above(2));
        return above.is(BlockTags.LOGS) || above.getBlock() instanceof LeavesBlock && !above.getValue(LeavesBlock.PERSISTENT);
    }

    /**
     * The part a block of a building plays in the town, and so the look the era gives it, or null to leave it as it is.
     *
     * @param ground the level of the land around the building
     */
    public static @Nullable Role roleOf(ServerLevel level, BlockPos pos, BlockState state, int ground) {
        if (state.hasBlockEntity()) {
            return null;
        }
        Block block = state.getBlock();
        int y = pos.getY();
        if (block instanceof DoorBlock) {
            return Role.DOOR;
        }
        if (block instanceof TrapDoorBlock) {
            return Role.SHUTTER;
        }
        if (block instanceof FenceGateBlock) {
            return Role.GATE;
        }
        if (block instanceof FenceBlock || block instanceof WallBlock) {
            return y <= ground + 1 ? Role.PICKET : Role.PORCH_POST;
        }
        if (block instanceof IronBarsBlock || state.is(BlockTags.IMPERMEABLE)) {
            return Role.WINDOW;
        }
        if (block instanceof StairBlock) {
            return y >= ground + ROOF_FROM ? Role.ROOF : Role.STEP;
        }
        if (block instanceof SlabBlock) {
            return y >= ground + ROOF_FROM ? Role.ROOF_SLAB : Role.PORCH;
        }
        if (state.is(BlockTags.LOGS)) {
            return Role.TRIM;
        }
        if (block instanceof LeavesBlock) {
            return Role.HEDGE;
        }
        if (block instanceof CarpetBlock) {
            return Role.RUG;
        }
        if (block instanceof FlowerPotBlock) {
            return Role.PLANT;
        }
        if (block instanceof FarmlandBlock) {
            return Role.LAWN;
        }
        if (block instanceof CropBlock) {
            return Role.FLOWER;
        }
        if (state.is(Blocks.DIRT_PATH)) {
            return Role.WALKWAY;
        }
        if (state.is(MATERIALS)) {
            if (y <= ground + 1) {
                return Role.FOUNDATION;
            }
            // a layer with open space over and under it is a floor between rooms, or a flat roof
            return level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.below()).isAir() ? Role.FLOOR : Role.WALL;
        }
        return null;
    }

    /**
     * Finds the buildings that stand, even partly, in a part's footprint: each built thing reaching into it is followed
     * out past it to be seen whole, and kept if it is big enough and has a room. Farms and paths count too, though they
     * have no rooms, so they turn into gardens and walkways.
     *
     * @param inFootprint whether a column, given as a position, lies in the part where it is being built
     * @param ours        whether the town already put a block at a position, so it isn't anyone's building
     * @return every block of those buildings inside the footprint
     */
    public static LongOpenHashSet buildings(ServerLevel level, int minX, int minZ, int maxX, int maxZ, int ground, LongPredicate inFootprint,
                                            LongPredicate ours) {
        LongOpenHashSet found = new LongOpenHashSet();
        LongOpenHashSet seen = new LongOpenHashSet();
        int bottom = ground - 2;
        int top = ground + 48;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!inFootprint.test(BlockPos.asLong(x, ground, z))) {
                    continue;
                }
                for (int y = bottom; y <= top; y++) {
                    long key = BlockPos.asLong(x, y, z);
                    if (seen.contains(key) || ours.test(key) || !isBuilt(level, pos.set(x, y, z), level.getBlockState(pos))) {
                        continue;
                    }
                    LongArrayList building = follow(level, pos.immutable(), minX - REACH, minZ - REACH, maxX + REACH, maxZ + REACH, bottom, top, seen,
                            ours);
                    if (isBuilding(level, building) || isGarden(level, building)) {
                        for (long block : building.toLongArray()) {
                            if (inFootprint.test(BlockPos.asLong(BlockPos.getX(block), ground, BlockPos.getZ(block)))) {
                                found.add(block);
                            }
                        }
                    }
                }
            }
        }
        return found;
    }

    /**
     * What stands on a home's lot that the home can be made of: a house, or what is left of one, a ruin, a shell of
     * walls, a bare foundation. Unlike a lot's buildings it needs no room in it, only enough of it standing to build on;
     * a field or a path won't do. The biggest of them, if more than one stands there.
     *
     * @param inFootprint whether a column, given as a position, lies in the lot
     * @param ours        whether the town already put a block at a position
     * @return every block of it on the lot, or nothing if nothing there will do
     */
    public static LongOpenHashSet home(ServerLevel level, int minX, int minZ, int maxX, int maxZ, int ground, LongPredicate inFootprint,
                                       LongPredicate ours) {
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayList best = null;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int bottom = ground - 2;
        int top = ground + 24;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!inFootprint.test(BlockPos.asLong(x, ground, z))) {
                    continue;
                }
                for (int y = bottom; y <= top; y++) {
                    long key = BlockPos.asLong(x, y, z);
                    if (seen.contains(key) || ours.test(key) || !isBuilt(level, pos.set(x, y, z), level.getBlockState(pos))) {
                        continue;
                    }
                    LongArrayList found = follow(level, pos.immutable(), minX - 2, minZ - 2, maxX + 2, maxZ + 2, bottom, top, seen, ours);
                    if ((best == null || found.size() > best.size()) && isHouseLike(level, found)) {
                        best = found;
                    }
                }
            }
        }
        LongOpenHashSet blocks = new LongOpenHashSet();
        if (best != null) {
            for (long block : best.toLongArray()) {
                if (inFootprint.test(BlockPos.asLong(BlockPos.getX(block), ground, BlockPos.getZ(block)))) {
                    blocks.add(block);
                }
            }
        }
        return blocks;
    }

    /**
     * Whether something built could be a home: big enough, four blocks across at the least, and either with a room in
     * it, or walls standing round much of its edge, or a floor across much of it.
     */
    public static boolean isHouseLike(ServerLevel level, LongArrayList built) {
        if (built.size() < MIN_BUILDING || isGarden(level, built)) {
            return false;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long key : built.toLongArray()) {
            minX = Math.min(minX, BlockPos.getX(key));
            minY = Math.min(minY, BlockPos.getY(key));
            minZ = Math.min(minZ, BlockPos.getZ(key));
            maxX = Math.max(maxX, BlockPos.getX(key));
            maxZ = Math.max(maxZ, BlockPos.getZ(key));
        }
        if (maxX - minX < 3 || maxZ - minZ < 3) {
            return false;
        }
        if (isBuilding(level, built)) {
            return true;
        }
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        boolean[] low = new boolean[width * depth];
        boolean[] base = new boolean[width * depth];
        for (long key : built.toLongArray()) {
            int index = (BlockPos.getZ(key) - minZ) * width + BlockPos.getX(key) - minX;
            int up = BlockPos.getY(key) - minY;
            if (up <= 3) {
                low[index] = true;
            }
            if (up <= 1) {
                base[index] = true;
            }
        }
        int edge = 0;
        int edgeCovered = 0;
        int inside = 0;
        int insideCovered = 0;
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                if (x == 0 || z == 0 || x == width - 1 || z == depth - 1) {
                    edge++;
                    edgeCovered += low[index] ? 1 : 0;
                } else {
                    inside++;
                    insideCovered += base[index] ? 1 : 0;
                }
            }
        }
        return edgeCovered >= edge * 0.4 || inside > 0 && insideCovered >= inside * 0.6;
    }

    /**
     * Sizes up what a home is made of, on its lot's grid: the footprint of its walls, its floor and how high its walls
     * stand, whether something already stands over it, and where its door is on its front.
     *
     * @param width the lot's width, across its front
     * @param depth and its depth back from the street
     */
    static Houses.Shell shell(ServerLevel level, LongOpenHashSet blocks, Blueprint.Frame frame, int width, int depth) {
        Direction right = frame.right();
        Direction back = frame.back();
        BlockPos origin = frame.origin();
        // the floor: the height most of its lowest blocks stand at
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap lowest = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        for (long key : blocks) {
            long column = BlockPos.asLong(BlockPos.getX(key), 0, BlockPos.getZ(key));
            int y = BlockPos.getY(key);
            if (!lowest.containsKey(column) || y < lowest.get(column)) {
                lowest.put(column, y);
            }
        }
        it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap counts = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
        for (int y : lowest.values()) {
            counts.addTo(y, 1);
        }
        int floor = 0;
        int most = -1;
        for (var entry : counts.int2IntEntrySet()) {
            if (entry.getIntValue() > most || entry.getIntValue() == most && entry.getIntKey() < floor) {
                most = entry.getIntValue();
                floor = entry.getIntKey();
            }
        }
        // the footprint of its walls, not of the eaves of a roof standing out over them
        int left = Integer.MAX_VALUE;
        int rightEdge = Integer.MIN_VALUE;
        int front = Integer.MAX_VALUE;
        int backEdge = Integer.MIN_VALUE;
        for (long key : blocks) {
            int y = BlockPos.getY(key);
            if (y < floor || y > floor + 2) {
                continue;
            }
            int dx = BlockPos.getX(key) - origin.getX();
            int dz = BlockPos.getZ(key) - origin.getZ();
            int a = dx * right.getStepX() + dz * right.getStepZ();
            int b = dx * back.getStepX() + dz * back.getStepZ();
            left = Math.min(left, a);
            rightEdge = Math.max(rightEdge, a);
            front = Math.min(front, b);
            backEdge = Math.max(backEdge, b);
        }
        left = Math.clamp(left, 1, width - 2);
        rightEdge = Math.clamp(rightEdge, left, width - 2);
        front = Math.clamp(front, 1, depth - 2);
        backEdge = Math.clamp(backEdge, front, depth - 2);
        // how high its walls stand, and whether anything stands over the room inside them
        int[] tops = new int[2 * (rightEdge - left + backEdge - front) + 4];
        int edges = 0;
        int inside = 0;
        int covered = 0;
        int door = Houses.NO_DOOR;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int b = front; b <= backEdge; b++) {
            for (int a = left; a <= rightEdge; a++) {
                BlockPos at = frame.at(a, b, floor);
                if (b <= front + MAX_DOOR_INSET && a > left && a < rightEdge) {
                    // its front door, in its front wall or set back a little in a porch, opening toward the street; the
                    // one nearest the middle of the lot, where its caster floats, if it has more than one
                    BlockState state = level.getBlockState(pos.set(at.getX(), floor + 1, at.getZ()));
                    if (state.getBlock() instanceof DoorBlock && blocks.contains(pos.asLong())
                            && state.getValue(DoorBlock.FACING).getAxis() == frame.depthAxis()
                            && (door == Houses.NO_DOOR || Math.abs(a - width / 2) < Math.abs(door - width / 2))) {
                        door = a;
                    }
                }
                boolean edge = a == left || a == rightEdge || b == front || b == backEdge;
                if (edge) {
                    int y = floor + 1;
                    while (y <= floor + 12 && blocks.contains(pos.set(at.getX(), y, at.getZ()).asLong())) {
                        y++;
                    }
                    if (edges < tops.length) {
                        tops[edges++] = y - 1;
                    }
                } else {
                    inside++;
                    for (int y = floor + 3; y <= floor + 14; y++) {
                        if (blocks.contains(pos.set(at.getX(), y, at.getZ()).asLong())) {
                            covered++;
                            break;
                        }
                    }
                }
            }
        }
        java.util.Arrays.sort(tops, 0, edges);
        int wallTop = edges == 0 ? floor + 1 : tops[edges * 3 / 5];
        boolean roofed = inside > 0 && covered * 2 >= inside;
        return new Houses.Shell(left, rightEdge, front, backEdge, floor, Math.min(wallTop, floor + 12), roofed, door);
    }

    /**
     * Where a caster's home could be made of something standing near them: in the middle of its living room, on its
     * floor, where they float as it is made over, and which way its town is laid out from there, in through its front
     * door, away from the street.
     */
    public record HomeSite(Vec3 center, Direction forward) {
    }

    /**
     * Finds what near a caster their home could be made of, if anything there will do: the house, or the ruin of one,
     * nearest them that a home's lot can hold, its front where its door is or else toward them.
     *
     * @param towns whether a Hex's town put down the block at a position, which is never anyone's home to make over
     */
    public static @Nullable HomeSite site(ServerLevel level, Vec3 caster, Direction facing, LongPredicate towns) {
        BlockPos at = BlockPos.containing(caster);
        java.util.List<BlockPos> starts = new java.util.ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -SITE_REACH; dx <= SITE_REACH; dx++) {
            for (int dz = -SITE_REACH; dz <= SITE_REACH; dz++) {
                for (int dy = -3; dy <= 4; dy++) {
                    pos.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    if (level.hasChunkAt(pos) && !towns.test(pos.asLong()) && isBuilt(level, pos, level.getBlockState(pos))) {
                        starts.add(pos.immutable());
                        break;
                    }
                }
            }
        }
        starts.sort(java.util.Comparator.comparingDouble(start -> start.distSqr(at)));
        LongOpenHashSet seen = new LongOpenHashSet();
        int bounds = SITE_REACH + TownPlan.LOT_WIDTH;
        for (BlockPos start : starts) {
            if (seen.contains(start.asLong())) {
                continue;
            }
            LongArrayList found = follow(level, start, at.getX() - bounds, at.getZ() - bounds, at.getX() + bounds, at.getZ() + bounds, at.getY() - 6,
                    at.getY() + 24, seen, towns);
            if (isHouseLike(level, found)) {
                HomeSite site = fit(level, found, caster, facing);
                if (site != null) {
                    return site;
                }
            }
        }
        return null;
    }

    /**
     * Lays a home's lot over something standing: its front toward the street, its walls inside the lot, and its caster
     * floating a few blocks in from its door, where there is room for them.
     */
    private static @Nullable HomeSite fit(ServerLevel level, LongArrayList built, Vec3 caster, Direction facing) {
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap lowest = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        for (long key : built.toLongArray()) {
            long column = BlockPos.asLong(BlockPos.getX(key), 0, BlockPos.getZ(key));
            int y = BlockPos.getY(key);
            if (!lowest.containsKey(column) || y < lowest.get(column)) {
                lowest.put(column, y);
            }
        }
        it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap counts = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
        for (int y : lowest.values()) {
            counts.addTo(y, 1);
        }
        int floor = 0;
        int most = -1;
        for (var entry : counts.int2IntEntrySet()) {
            if (entry.getIntValue() > most) {
                most = entry.getIntValue();
                floor = entry.getIntKey();
            }
        }
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        BlockPos door = null;
        double doorDistance = Double.MAX_VALUE;
        for (long key : built.toLongArray()) {
            int y = BlockPos.getY(key);
            if (y < floor || y > floor + 2) {
                continue;
            }
            int x = BlockPos.getX(key);
            int z = BlockPos.getZ(key);
            minX = Math.min(minX, x);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxZ = Math.max(maxZ, z);
            BlockPos pos = BlockPos.of(key);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                double distance = pos.distToCenterSqr(caster);
                if (distance < doorDistance) {
                    doorDistance = distance;
                    door = pos;
                }
            }
        }
        if (minX > maxX) {
            return null;
        }
        // which way it faces: out of the door nearest the caster, the way it opens, if that is out through one of its
        // outside walls or a porch set a little way into one; or toward the caster, standing outside it; or back the
        // way they look, standing in it
        Direction front = null;
        int inset = 0;
        if (door != null) {
            Direction.Axis through = level.getBlockState(door).getValue(DoorBlock.FACING).getAxis();
            int low = through == Direction.Axis.Z ? door.getZ() - minZ : door.getX() - minX;
            int high = through == Direction.Axis.Z ? maxZ - door.getZ() : maxX - door.getX();
            inset = Math.min(low, high);
            if (inset <= MAX_DOOR_INSET) {
                front = through == Direction.Axis.Z ? (low <= high ? Direction.NORTH : Direction.SOUTH) : (low <= high ? Direction.WEST : Direction.EAST);
            }
        }
        if (front == null) {
            door = null;
            inset = 0;
            double cx = (minX + maxX + 1) / 2.0;
            double cz = (minZ + maxZ + 1) / 2.0;
            double dx = (caster.x - cx) / Math.max(1.0, (maxX - minX + 1) / 2.0);
            double dz = (caster.z - cz) / Math.max(1.0, (maxZ - minZ + 1) / 2.0);
            if (Math.max(Math.abs(dx), Math.abs(dz)) > 1.0) {
                front = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
            } else {
                front = facing.getOpposite();
            }
        }
        boolean wide = front.getAxis() == Direction.Axis.Z;
        int across = wide ? maxX - minX + 1 : maxZ - minZ + 1;
        int deep = wide ? maxZ - minZ + 1 : maxX - minX + 1;
        if (across > TownPlan.LOT_WIDTH - 2 || deep > TownPlan.LOT_DEPTH - 3) {
            return null;
        }
        // its front a few blocks in front of the living room, no further back in the lot than leaves a yard behind it
        int inward = Math.clamp(TownPlan.LIVING_ROOM - TownPlan.HOME_FRONT, 1, deep - 2);
        inward = Math.max(inward, TownPlan.LIVING_ROOM - (TownPlan.LOT_DEPTH - 1 - deep));
        if (inward > deep - 2 || TownPlan.LIVING_ROOM - inward < 1) {
            return null;
        }
        int lowAcross = wide ? minX : minZ;
        int highAcross = wide ? maxX : maxZ;
        if (highAcross - lowAcross < 2) {
            return null;
        }
        int face = switch (front) {
            case NORTH -> minZ;
            case SOUTH -> maxZ;
            case WEST -> minX;
            default -> maxX;
        };
        Direction in = front.getOpposite();
        // in line with its door, if its lot can be laid out round it so, or else across the middle of it; somewhere
        // with room for them behind the door, and nothing between them and it, or past it, for them to walk out round
        int center = (lowAcross + highAcross) / 2;
        int doorLine = door == null ? center : wide ? door.getX() : door.getZ();
        int half = TownPlan.LOT_WIDTH / 2 - 1;
        int nearest = Math.max(1, TownPlan.LIVING_ROOM - (TownPlan.LOT_DEPTH - 1 - deep));
        int farthest = Math.min(deep - 2, TownPlan.LIVING_ROOM - 1);
        HomeSite blocked = null;
        for (int line : doorLine == center ? new int[] {center} : new int[] {doorLine, center}) {
            int middle = Math.clamp(line, lowAcross + 1, highAcross - 1);
            if (middle - lowAcross > half || highAcross - middle > half) {
                continue;
            }
            int doorAt = line == doorLine ? inset : 0;
            for (int nudge : new int[] {0, 1, -1, 2, -2}) {
                int k = inward + nudge;
                if (k < nearest || k > farthest || k <= doorAt) {
                    continue;
                }
                BlockPos spot = inFrom(face, k, middle, floor, in, wide);
                boolean open = true;
                for (int y = floor + 1; y <= floor + 3; y++) {
                    BlockState state = level.getBlockState(spot.atY(y));
                    open &= state.isAir() || state.canBeReplaced() || state.getBlock() instanceof CarpetBlock;
                }
                if (!open) {
                    continue;
                }
                HomeSite site = new HomeSite(new Vec3(spot.getX() + 0.5, floor, spot.getZ() + 0.5), in);
                boolean clear = true;
                for (int j = 0; j < k && clear; j++) {
                    clear = j == doorAt || walkable(level, inFrom(face, j, middle, floor + 1, in, wide));
                }
                if (clear) {
                    return site;
                }
                if (blocked == null) {
                    blocked = site;
                }
            }
        }
        return blocked;
    }

    /**
     * A spot some way in from a building's front, in line with its door.
     *
     * @param face where its front wall stands, along the way in
     * @param wide whether its front runs along x
     */
    private static BlockPos inFrom(int face, int inward, int middle, int y, Direction in, boolean wide) {
        int depthAt = face + inward * (wide ? in.getStepZ() : in.getStepX());
        return wide ? new BlockPos(middle, y, depthAt) : new BlockPos(depthAt, y, middle);
    }

    /**
     * Whether someone could walk through a spot: nothing on the floor there they couldn't walk over, like a rug, and
     * nothing at head height.
     */
    private static boolean walkable(ServerLevel level, BlockPos feet) {
        VoxelShape low = level.getBlockState(feet).getCollisionShape(level, feet);
        BlockPos head = feet.above();
        return (low.isEmpty() || low.max(Direction.Axis.Y) <= 0.1) && level.getBlockState(head).getCollisionShape(level, head).isEmpty();
    }

    /**
     * Every tree standing in a part's footprint: the trunks, and the leaves that grew on them.
     *
     * @param inFootprint whether a column, given as a position, lies in the part where it is being built
     * @param ours        whether the town already put a block at a position
     */
    public static LongOpenHashSet trees(ServerLevel level, int minX, int minZ, int maxX, int maxZ, int ground, LongPredicate inFootprint,
                                        LongPredicate ours) {
        LongOpenHashSet found = new LongOpenHashSet();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!inFootprint.test(BlockPos.asLong(x, ground, z))) {
                    continue;
                }
                for (int y = ground - 1; y <= ground + 32; y++) {
                    long key = BlockPos.asLong(x, y, z);
                    if (ours.test(key)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    boolean leaves = state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT);
                    if (leaves || state.is(BlockTags.LOGS) && touchesLeaves(level, pos)) {
                        found.add(key);
                    }
                }
            }
        }
        return found;
    }

    /**
     * What a building becomes in the town, by what is in it, the way a village's own buildings each find their part in
     * a sitcom town: the librarian's house with its lectern the public library, a temple with its brewing stand or a
     * building with a bell the church or the town hall, the cleric's little house a pharmacy, the butcher's a diner,
     * the farmer's a grocery, the cartographer's the post office, the smiths' the hardware store, a weaver's or a
     * fletcher's the five and dime, a building stacked with hay a barn, and anything grand enough the town hall. The
     * rest are homes. Public buildings are made over in brick and barns in red boards; shops get an awning; all of
     * them get a sign over the door that faces the street.
     *
     * @param street  which way the street is from the lot
     * @param orchard whether the Hex is an orchard, where a farmer's place is a farm stand and buildings are barns
     */
    public static Identity identify(ServerLevel level, LongOpenHashSet building, net.minecraft.core.Direction street, boolean orchard) {
        int bells = 0;
        int brewing = 0;
        int books = 0;
        int kitchens = 0;
        int smiths = 0;
        int maps = 0;
        int stores = 0;
        int crafts = 0;
        int hay = 0;
        int beds = 0;
        double cx = 0.0;
        double cz = 0.0;
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        BlockPos door = null;
        double doorScore = -Double.MAX_VALUE;
        for (long key : building) {
            BlockPos pos = BlockPos.of(key);
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();
            cx += pos.getX() + 0.5;
            cz += pos.getZ() + 0.5;
            low = Math.min(low, pos.getY());
            high = Math.max(high, pos.getY());
            if (block instanceof BellBlock) {
                bells++;
            } else if (block instanceof BrewingStandBlock) {
                brewing++;
            } else if (block instanceof LecternBlock || state.is(Blocks.BOOKSHELF) || state.is(Blocks.CHISELED_BOOKSHELF)) {
                books += block instanceof LecternBlock ? 4 : 1;
            } else if (block instanceof SmokerBlock || block instanceof CampfireBlock || block instanceof FurnaceBlock) {
                kitchens++;
            } else if (block instanceof BlastFurnaceBlock || block instanceof AnvilBlock || block instanceof SmithingTableBlock
                    || block instanceof GrindstoneBlock || block instanceof StonecutterBlock) {
                smiths++;
            } else if (block instanceof CartographyTableBlock) {
                maps++;
            } else if (block instanceof BarrelBlock || block instanceof ComposterBlock) {
                stores++;
            } else if (block instanceof LoomBlock || state.is(Blocks.FLETCHING_TABLE)) {
                crafts++;
            } else if (state.is(Blocks.HAY_BLOCK)) {
                hay++;
            } else if (block instanceof BedBlock) {
                beds++;
            } else if (block instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                double score = pos.getX() * street.getStepX() + pos.getZ() * street.getStepZ();
                if (score > doorScore) {
                    doorScore = score;
                    door = pos;
                }
            }
        }
        net.minecraft.core.Direction out = null;
        if (door != null && !building.isEmpty()) {
            // out of the door is away from the middle of the building
            cx /= building.size();
            cz /= building.size();
            net.minecraft.core.Direction facing = level.getBlockState(door).getValue(DoorBlock.FACING);
            double along = (door.getX() + 0.5 - cx) * facing.getStepX() + (door.getZ() + 0.5 - cz) * facing.getStepZ();
            out = along >= 0.0 ? facing : facing.getOpposite();
        }
        boolean grand = building.size() >= 600 || high - low >= 10;
        if (bells > 0 || brewing > 0 && grand) {
            return new Identity(brewing > 0 ? Signs.CHAPEL : Signs.TOWN_HALL, brewing > 0 ? Role.WALL : Role.CIVIC_WALL, false, door, out);
        }
        if (books >= 4) {
            return new Identity(Signs.LIBRARY, Role.CIVIC_WALL, false, door, out);
        }
        if (brewing > 0) {
            return new Identity(Signs.PHARMACY, Role.WALL, true, door, out);
        }
        if (kitchens > 0 && beds == 0 || kitchens > 1) {
            return new Identity(orchard ? Signs.BAKERY : Signs.DINER, Role.WALL, true, door, out);
        }
        if (maps > 0) {
            return new Identity(Signs.POST_OFFICE, Role.CIVIC_WALL, false, door, out);
        }
        if (smiths > 0) {
            return new Identity(Signs.HARDWARE, orchard ? Role.BARN_WALL : Role.WALL, !orchard, door, out);
        }
        if (stores > 0) {
            return new Identity(orchard ? Signs.FARM_STAND : Signs.GROCERY, orchard ? Role.BARN_WALL : Role.WALL, !orchard, door, out);
        }
        if (crafts > 0) {
            return new Identity(Signs.FIVE_AND_DIME, Role.WALL, true, door, out);
        }
        if (hay >= 3 || orchard && beds == 0) {
            return new Identity(-1, Role.BARN_WALL, false, door, out);
        }
        if (grand) {
            return new Identity(Signs.TOWN_HALL, Role.CIVIC_WALL, false, door, out);
        }
        return new Identity(-1, Role.WALL, false, door, out);
    }

    /**
     * What a building has become.
     *
     * @param sign   what its sign says, a {@link Signs} kind, or less than 0 for none: a home or a barn
     * @param walls  what its walls are made over in
     * @param awning whether it has an awning over its door, as a shop does
     * @param door   the lower half of its door nearest the street, if it has one
     * @param out    which way is out of that door
     */
    public record Identity(int sign, Role walls, boolean awning, @Nullable BlockPos door, net.minecraft.core.@Nullable Direction out) {
    }

    /**
     * Every built block joined to one, corner to corner, within bounds.
     */
    private static LongArrayList follow(ServerLevel level, BlockPos start, int minX, int minZ, int maxX, int maxZ, int bottom, int top,
                                        LongOpenHashSet seen, LongPredicate ours) {
        LongArrayList building = new LongArrayList();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start.asLong());
        while (!queue.isEmpty() && building.size() < MAX_BUILDING) {
            BlockPos at = queue.poll();
            building.add(at.asLong());
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int x = at.getX() + dx;
                        int y = at.getY() + dy;
                        int z = at.getZ() + dz;
                        if (x < minX || x > maxX || z < minZ || z > maxZ || y < bottom || y > top) {
                            continue;
                        }
                        long key = BlockPos.asLong(x, y, z);
                        if (seen.contains(key) || ours.test(key)) {
                            continue;
                        }
                        BlockPos next = new BlockPos(x, y, z);
                        if (!level.hasChunkAt(next)) {
                            continue;
                        }
                        if (isBuilt(level, next, level.getBlockState(next))) {
                            seen.add(key);
                            queue.add(next);
                        }
                    }
                }
            }
        }
        return building;
    }

    /**
     * Big enough to be a building, with a room in it: open space with a part of the building over it and solid ground or
     * the building under it.
     */
    private static boolean isBuilding(ServerLevel level, LongArrayList building) {
        if (building.size() < MIN_BUILDING) {
            return false;
        }
        LongOpenHashSet blocks = new LongOpenHashSet(building);
        for (long key : building.toLongArray()) {
            int x = BlockPos.getX(key);
            int y = BlockPos.getY(key);
            int z = BlockPos.getZ(key);
            // look down from each block for open space, then something solid beneath it
            BlockPos below = new BlockPos(x, y - 1, z);
            if (!level.getBlockState(below).isAir()) {
                continue;
            }
            for (int depth = 2; depth <= 6; depth++) {
                BlockState under = level.getBlockState(new BlockPos(x, y - depth, z));
                if (under.isAir()) {
                    continue;
                }
                if (blocks.contains(BlockPos.asLong(x, y - depth, z)) || Terrain.isGround(under) || under.isSolid()) {
                    return true;
                }
                break;
            }
        }
        return false;
    }

    /**
     * Fields of crops on farmland, and village paths: no rooms, but still made over rather than cleared.
     */
    private static boolean isGarden(ServerLevel level, LongArrayList built) {
        int tended = 0;
        for (long key : built.toLongArray()) {
            BlockState state = level.getBlockState(BlockPos.of(key));
            if (state.getBlock() instanceof FarmlandBlock || state.getBlock() instanceof CropBlock || state.is(Blocks.DIRT_PATH)) {
                tended++;
            }
        }
        return tended >= 6 && tended * 2 >= built.size();
    }
}
