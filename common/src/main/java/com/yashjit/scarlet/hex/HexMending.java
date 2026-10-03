package com.yashjit.scarlet.hex;

import com.yashjit.scarlet.network.TownFormPayload;
import com.yashjit.scarlet.platform.Services;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Blasts inside a Hex mend themselves, the Hex rewinding them. Whatever a creeper, TNT, a fireball or any other blast
 * would break inside the wall is taken whole instead, dropping nothing, a chest keeping what was in it; after a moment
 * it all comes back, slowly, over about ten seconds, the crater closing in from its edge to where the blast went off,
 * each block flickering in as an outline of scarlet light and snapping back in glitching red, as a caster's home is
 * written. TNT a blast sets off is used up, no fire catches inside the wall, and what hangs on its walls, paintings and
 * item frames, isn't broken at all. What lies outside the wall stays broken, so a blast against it leaves a crater cut
 * clean at the wall.
 *
 * <p>Something put in a gap meanwhile stays, and the gap with it. Should the Hex fall first, its town's blocks are left
 * to the town, which puts back what stood before it, and a painted block comes back as it was before it was painted.
 * Whatever is left to mend when a level is saved comes back there and then, so a crater is never saved open.
 */
public final class HexMending {

    /** Ticks after a blast before the first of it comes back, and over which the rest follows. */
    private static final int WAIT_TICKS = 20;
    private static final int MEND_TICKS = 200;
    /** Ticks before each block comes back that players are told of it, to see it flicker in where it will stand. */
    private static final int LEAD_TICKS = 16;
    /** How far from a blast players see it mend. */
    private static final double SEEN_FROM = 96.0;
    private static final int TAKE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
    private static final int PUT_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final Map<ServerLevel, List<Mend>> MENDING = new WeakHashMap<>();

    private HexMending() {
    }

    /**
     * Takes what a blast is about to break inside a Hex out of its list, whole, to mend; the rest it breaks as it would.
     */
    public static void blast(ServerLevel level, Vec3 center, List<BlockPos> targets) {
        HexData data = HexData.of(level);
        if (data.all().isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        List<Gap> gaps = new ArrayList<>();
        Iterator<BlockPos> iterator = targets.iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getBlock() instanceof TntBlock || state.getBlock() instanceof LiquidBlock || !inside(level, pos, now)) {
                continue;
            }
            BlockEntity entity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
            CompoundTag kept = entity != null ? entity.saveWithFullMetadata(level.registryAccess()) : null;
            HexPaint.Painted painted = HexPaint.at(level, pos);
            BlockState plain = painted != null && painted.after().getBlock() == state.getBlock() ? painted.before() : state;
            gaps.add(new Gap(pos.immutable(), state, plain, data.isBuilt(pos), kept, Vec3.atCenterOf(pos).distanceToSqr(center)));
            iterator.remove();
        }
        if (gaps.isEmpty()) {
            return;
        }
        // all taken at once, so none falls or pops off as the others go
        for (Gap gap : gaps) {
            level.setBlock(gap.pos(), Blocks.AIR.defaultBlockState(), TAKE_FLAGS);
        }
        // the crater closes in from its edge, where the blast went off last of all
        gaps.sort(Comparator.comparingDouble(Gap::distance).reversed());
        long[] at = new long[gaps.size()];
        for (int i = 0; i < at.length; i++) {
            at[i] = now + WAIT_TICKS + (at.length == 1 ? 0 : Math.round(MEND_TICKS * i / (double) (at.length - 1)));
        }
        MENDING.computeIfAbsent(level, key -> new ArrayList<>()).add(new Mend(center, gaps, at));
    }

    /**
     * Keeps a blast from lighting fires inside a Hex: takes the spots inside out of those it might.
     */
    public static void unlit(ServerLevel level, List<BlockPos> spots) {
        if (HexData.of(level).all().isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        spots.removeIf(pos -> inside(level, pos, now));
    }

    /**
     * Whether a point lies inside the wall of a Hex, as far out as the wall has come.
     */
    public static boolean inside(ServerLevel level, Vec3 point) {
        long now = level.getGameTime();
        for (Hex hex : HexData.of(level).all()) {
            if (HexShape.contains(hex.center, Hexes.wallRadius(hex, now), point)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inside(ServerLevel level, BlockPos pos, long now) {
        Vec3 point = Vec3.atCenterOf(pos);
        for (Hex hex : HexData.of(level).all()) {
            if (HexShape.contains(hex.center, Hexes.wallRadius(hex, now), point)) {
                return true;
            }
        }
        return false;
    }

    public static void tick(ServerLevel level) {
        List<Mend> mends = MENDING.get(level);
        if (mends == null || mends.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Mend> iterator = mends.iterator();
        while (iterator.hasNext()) {
            Mend mend = iterator.next();
            tell(level, mend, now);
            if (!mend.begun && now >= mend.at[0] - LEAD_TICKS) {
                // the tape winding back
                mend.begun = true;
                play(level, mend.center, SoundEvents.BEACON_POWER_SELECT, 1.2F, 0.6F);
                play(level, mend.center, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.8F);
            }
            while (mend.next < mend.gaps.size() && mend.at[mend.next] <= now) {
                Gap gap = mend.gaps.get(mend.next);
                if (!level.hasChunkAt(gap.pos())) {
                    break;
                }
                putBack(level, gap, now);
                mend.next++;
            }
            if (mend.next >= mend.gaps.size()) {
                iterator.remove();
                play(level, mend.center, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
            }
        }
    }

    /**
     * Puts back at once everything still to mend in a level, as it is about to be saved.
     */
    public static void finish(ServerLevel level) {
        List<Mend> mends = MENDING.remove(level);
        if (mends == null) {
            return;
        }
        long now = level.getGameTime();
        for (Mend mend : mends) {
            for (int i = mend.next; i < mend.gaps.size(); i++) {
                Gap gap = mend.gaps.get(i);
                if (level.hasChunkAt(gap.pos())) {
                    putBack(level, gap, now);
                }
            }
        }
    }

    /**
     * Tells everyone near enough of the blocks about to come back, to flicker in where each will stand.
     */
    private static void tell(ServerLevel level, Mend mend, long now) {
        int from = mend.told;
        while (mend.told < mend.gaps.size() && mend.at[mend.told] - LEAD_TICKS <= now) {
            mend.told++;
        }
        if (mend.told == from) {
            return;
        }
        int[] blocks = new int[(mend.told - from) * 4];
        for (int i = from; i < mend.told; i++) {
            BlockPos pos = mend.gaps.get(i).pos();
            int k = (i - from) * 4;
            blocks[k] = pos.getX();
            blocks[k + 1] = pos.getY();
            blocks[k + 2] = pos.getZ();
            blocks[k + 3] = (int) Math.max(1L, mend.at[i] - now);
        }
        TownFormPayload payload = new TownFormPayload(blocks);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(mend.center) < SEEN_FROM * SEEN_FROM) {
                Services.NETWORK.sendToPlayer(player, payload);
            }
        }
    }

    private static void putBack(ServerLevel level, Gap gap, long now) {
        BlockPos pos = gap.pos();
        BlockState current = level.getBlockState(pos);
        // only air, fire, or water that has run in is mended over
        boolean open = current.isAir() || current.getBlock() instanceof BaseFireBlock
                || current.getBlock() instanceof LiquidBlock && !current.getFluidState().isSource();
        if (!open) {
            return;
        }
        BlockState state;
        if (inside(level, pos, now)) {
            state = gap.state();
        } else if (gap.town()) {
            return;
        } else {
            state = gap.plain();
        }
        nudge(level, pos, state);
        level.setBlock(pos, state, PUT_FLAGS);
        if (gap.kept() != null && state.getBlock() == gap.state().getBlock()) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity != null) {
                entity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), gap.kept()));
                entity.setChanged();
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * Lifts anyone standing where a block comes back up onto it.
     */
    private static void nudge(ServerLevel level, BlockPos pos, BlockState state) {
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return;
        }
        AABB box = shape.bounds().move(pos);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity.getBoundingBox().intersects(box)) {
                entity.teleportTo(entity.getX(), box.maxY, entity.getZ());
            }
        }
    }

    private static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, pitch);
    }

    /**
     * A block a blast broke inside a Hex, to come back.
     *
     * @param plain    what it was before it was painted, if it was, else the same as {@code state}
     * @param town     whether the Hex's town built it
     * @param kept     what was kept in it, a chest's contents or a sign's words
     * @param distance how far it was from the blast, squared
     */
    private record Gap(BlockPos pos, BlockState state, BlockState plain, boolean town, @Nullable CompoundTag kept, double distance) {
    }

    /**
     * What one blast broke inside a Hex, in the order it comes back, and when each of it does.
     */
    private static final class Mend {
        final Vec3 center;
        final List<Gap> gaps;
        final long[] at;
        /** The first not yet told of, and the first not yet put back. */
        int told;
        int next;
        boolean begun;

        Mend(Vec3 center, List<Gap> gaps, long[] at) {
            this.center = center;
            this.gaps = gaps;
            this.at = at;
        }
    }
}
