package com.yashjit.scarlet.hex.town;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * How the town tells open land from anything that was already there. It only ever builds on natural ground (grass,
 * dirt, sand, gravel, bare stone, snow, village paths) and only clears what grows on it (grass, ferns, flowers, snow).
 * Everything else, from player builds and village houses to trees and water, is left exactly as it is.
 */
public final class Terrain {

    /** Natural ground the town may build on; the {@code scarlet:hex_ground} block tag. */
    public static final TagKey<Block> GROUND = TagKey.create(Registries.BLOCK, Scarlet.id("hex_ground"));

    public static final int NO_GROUND = Integer.MIN_VALUE;

    private Terrain() {
    }

    /**
     * The height of the natural ground at a column, or {@link #NO_GROUND} if something else stands on top there:
     * a build, a tree, water.
     */
    public static int ground(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (top < level.getMinY()) {
            return NO_GROUND;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
        BlockState state = level.getBlockState(pos);
        if (!isGround(state)) {
            return NO_GROUND;
        }
        return top;
    }

    public static boolean isGround(BlockState state) {
        return state.is(GROUND) && state.getFluidState().isEmpty();
    }

    /**
     * Air, or something growing on the ground that the town may clear and put back: grass, ferns, flowers, a dusting of
     * snow. Never fluids.
     */
    public static boolean isClearable(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (!state.getFluidState().isEmpty() || state.hasBlockEntity()) {
            return false;
        }
        Block block = state.getBlock();
        return state.canBeReplaced() || state.is(BlockTags.SMALL_FLOWERS) || block instanceof DoublePlantBlock || block instanceof FlowerBedBlock;
    }

    /**
     * Whether the town may put a block where this one is.
     */
    public static boolean isReplaceable(BlockState state) {
        return isClearable(state) || isGround(state);
    }

    /**
     * Whether the column above a point is open to the given height: only air and what grows on the ground, no leaves
     * or anything built.
     */
    public static boolean isOpenAbove(ServerLevel level, int x, int y, int z, int height) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy <= height; dy++) {
            if (!isClearable(level.getBlockState(pos.set(x, y + dy, z)))) {
                return false;
            }
        }
        return true;
    }
}
