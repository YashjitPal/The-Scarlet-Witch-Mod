package com.yashjit.scarlet.hex.town;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How the town reads the land it is rewritten over.
 *
 * <p>Under whatever stands on it, a village, a forest, someone's base, a field of wool, lies the natural ground: grass,
 * dirt, sand, gravel, stone, snow. The town finds it by looking down through everything else, and levels itself on it.
 * Everything above that ground is cleared away into the Hex's memory and put back exactly as it was when the Hex
 * falls. Only what can't be broken is never touched, and lakes and the sea are left as they are.
 */
public final class Terrain {

    /** Natural ground the town may build on; the {@code scarlet:hex_ground} block tag. */
    public static final TagKey<Block> GROUND = TagKey.create(Registries.BLOCK, Scarlet.id("hex_ground"));
    /** Blocks the town must never clear, past what can't be broken anyway; the {@code scarlet:hex_untouchable} tag. */
    public static final TagKey<Block> UNTOUCHABLE = TagKey.create(Registries.BLOCK, Scarlet.id("hex_untouchable"));

    public static final int NO_GROUND = Integer.MIN_VALUE;

    private Terrain() {
    }

    /**
     * Whether the chunk a block is in is loaded and ready to read: a level's own check only says one is meant to be,
     * and reading a block in one still being made holds the server up until it is done.
     */
    public static boolean isReady(ServerLevel level, BlockPos pos) {
        return isReady(level, SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    public static boolean isReady(ServerLevel level, int chunkX, int chunkZ) {
        return level.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
    }

    /**
     * Whether a chunk and the eight round it are all ready: everything putting blocks back in it reads, or tells of
     * what changed, without holding the server up while some of it loads.
     */
    public static boolean isReadyAround(ServerLevel level, int chunkX, int chunkZ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!isReady(level, chunkX + dx, chunkZ + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Whether a block can be put back now: its chunk ready, and at the chunk's edge the one beside it, which the
     * block's neighbors there are in.
     */
    public static boolean canPutBack(ServerLevel level, long key) {
        int x = BlockPos.getX(key);
        int z = BlockPos.getZ(key);
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        return isReady(level, chunkX, chunkZ)
                && ((x & 15) != 0 || isReady(level, chunkX - 1, chunkZ)) && ((x & 15) != 15 || isReady(level, chunkX + 1, chunkZ))
                && ((z & 15) != 0 || isReady(level, chunkX, chunkZ - 1)) && ((z & 15) != 15 || isReady(level, chunkX, chunkZ + 1));
    }

    /**
     * Asks for a chunk and the eight round it to be loaded, for a moment, without anything there being set going: for
     * as long as it goes on being asked.
     */
    public static void fetchAround(ServerLevel level, int chunkX, int chunkZ) {
        level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_SPAWN, new ChunkPos(chunkX, chunkZ), 1);
    }

    public static boolean isGround(BlockState state) {
        return state.is(GROUND) && state.getFluidState().isEmpty();
    }

    /**
     * Water, lava or ice: what a pond, a river or the sea is made of.
     */
    public static boolean isWater(BlockState state) {
        return !state.getFluidState().isEmpty() || state.is(BlockTags.ICE);
    }

    /**
     * What the town may never clear: anything that can't be broken, like bedrock, portals and command blocks.
     */
    public static boolean isUntouchable(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.canBeReplaced() && !state.is(UNTOUCHABLE)) {
            return false;
        }
        return state.is(UNTOUCHABLE) || state.getDestroySpeed(level, pos) < 0.0F;
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
     * Whether the town may put a block where this one is, once the land has been cleared.
     */
    public static boolean isReplaceable(BlockState state) {
        return isClearable(state) || isGround(state);
    }

    /**
     * One column of land as the town reads it.
     *
     * @param surface     the height of its natural ground, or {@link #NO_GROUND}
     * @param water       how deep water, lava or ice lies over that ground
     * @param top         the height of the highest block of anything in it
     * @param untouchable whether anything above the ground can't be cleared
     */
    public record Column(int surface, int water, int top, boolean untouchable) {

        public boolean isLand(int shallow) {
            return surface != NO_GROUND && water <= shallow;
        }
    }
}
