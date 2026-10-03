package com.yashjit.scarlet.hex.town;

import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.hex.HexPaint;
import com.yashjit.scarlet.hex.RemnantSnapshot;
import com.yashjit.scarlet.network.RemnantBlocksPayload;
import com.yashjit.scarlet.network.TownUnformPayload;
import com.yashjit.scarlet.platform.Services;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * A caster's home, left standing as their Hex falls in around it. When the falling wall reaches it, it is let go on its
 * own rather than taken down with the rest of the town, and it holds on after the Hex has gone, glitching through the
 * eras ever more wildly, as its clients show it. Then it goes a part at a time, the way it went up but backward: the
 * rooms empty, the yard and the porch go, then the roof, the walls and the frame, down to the bare lot, and last of all
 * the land comes back just as it was.
 *
 * <p>It is never saved as it is: if the world is saved while it stands, what is left of it is saved with what is waiting
 * to be put back, and goes back as soon as the world is loaded again.
 */
public final class HomeRemnant {

    /** How long before each block goes that players are told of it, to show it glitching out. */
    private static final int LEAVE_LEAD = 8;
    /** Ticks a home takes to go up, and so to come down, stage by stage. */
    static final int BUILD_TICKS = java.util.Arrays.stream(Houses.ANCHOR_TICKS).sum();
    /** Ticks what stood on its lot before takes to come back, once the last of it has gone. */
    private static final int LAND_TICKS = 20;
    /** How near a player must be to be told what it is built of, and when each block of it goes. */
    private static final double SEEN_FROM = 160.0;
    private static final int TELL_INTERVAL = 10;
    private static final int MAX_GONE_CELLS = 96;
    private static final long NOT_GONE = Long.MIN_VALUE;
    private static final AtomicInteger IDS = new AtomicInteger();

    private final int id = IDS.incrementAndGet();
    private final UUID caster;
    private final Vec3 center;
    private final int ground;
    private final Era era;
    private final long start;
    private final Long2ObjectOpenHashMap<HexTown.Built> blocks;
    private final List<HexTown.Kept> kept;
    /** Its blocks in the order they go, and the tick after it starts going that each does. */
    private final long[] order;
    private final int[] at;
    private final int unbuildTicks;
    private final BlockPos min;
    private final BlockPos max;
    private final Set<UUID> told = new HashSet<>();
    /** Where what stood on its lot before just came back, by two-by-two cell, with the lowest and highest y that did. */
    private final Long2LongOpenHashMap gone = new Long2LongOpenHashMap();
    private int cursor;
    private int announced;
    private boolean begun;

    {
        gone.defaultReturnValue(NOT_GONE);
    }

    private HomeRemnant(UUID caster, Vec3 center, int ground, Era era, long start, Long2ObjectOpenHashMap<HexTown.Built> blocks,
                        List<HexTown.Kept> kept, long[] order, int[] at, int unbuildTicks, BlockPos min, BlockPos max) {
        this.caster = caster;
        this.center = center;
        this.ground = ground;
        this.era = era;
        this.start = start;
        this.blocks = blocks;
        this.kept = kept;
        this.order = order;
        this.at = at;
        this.unbuildTicks = unbuildTicks;
        this.min = min;
        this.max = max;
    }

    /**
     * @param builtAt  the tick each block went up at, after its home began building; -1 for those not in the plan of the
     *                 house, such as the levelling of its lot, or all of a home made of what stood there before
     * @param duration ticks it took to build
     */
    static HomeRemnant of(UUID caster, Vec3 center, int ground, Era era, Long2ObjectOpenHashMap<HexTown.Built> blocks, List<HexTown.Kept> kept,
                          Long2IntOpenHashMap builtAt, int duration, long now) {
        long[] keys = blocks.keySet().toLongArray();
        int bottom = Integer.MAX_VALUE;
        int top = Integer.MIN_VALUE;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long key : keys) {
            int y = BlockPos.getY(key);
            bottom = Math.min(bottom, y);
            top = Math.max(top, y);
            if (blocks.get(key).role() != Role.CLEAR) {
                minX = Math.min(minX, BlockPos.getX(key));
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, BlockPos.getZ(key));
                maxX = Math.max(maxX, BlockPos.getX(key));
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, BlockPos.getZ(key));
            }
        }
        if (minX > maxX) {
            BlockPos middle = BlockPos.containing(center);
            minX = maxX = middle.getX();
            minY = maxY = middle.getY();
            minZ = maxZ = middle.getZ();
        }
        float span = Math.max(1, top - bottom);
        // where each stage's blocks go, backward: the last stage to go up first to come down
        int[] stageEnds = new int[Houses.ANCHOR_TICKS.length];
        int end = 0;
        for (int stage = stageEnds.length - 1; stage >= 0; stage--) {
            end += Houses.ANCHOR_TICKS[stage] * duration / BUILD_TICKS;
            stageEnds[stage] = end;
        }
        int[] times = new int[keys.length];
        for (int k = 0; k < keys.length; k++) {
            long key = keys[k];
            float height = (BlockPos.getY(key) - bottom) / span;
            int tick = builtAt.get(key);
            Role role = blocks.get(key).role();
            if (role == Role.CLEAR) {
                // what stood there before comes back once the house is gone, from the ground up
                times[k] = duration + Math.round(LAND_TICKS * height);
            } else if (tick >= 0) {
                // the house goes the way it went up, backward: what went up last goes first
                times[k] = duration - tick;
            } else {
                // otherwise with the rest of what it is for, its stage from the top down
                int stage = Houses.stageOf(role);
                int length = Houses.ANCHOR_TICKS[stage] * duration / BUILD_TICKS;
                times[k] = stageEnds[stage] - length + Math.round(length * (1.0F - height));
            }
        }
        Integer[] index = new Integer[keys.length];
        for (int k = 0; k < keys.length; k++) {
            index[k] = k;
        }
        Arrays.sort(index, (a, b) -> Integer.compare(times[a], times[b]));
        long[] order = new long[keys.length];
        int[] at = new int[keys.length];
        for (int k = 0; k < keys.length; k++) {
            order[k] = keys[index[k]];
            at[k] = Math.max(0, times[index[k]]);
        }
        return new HomeRemnant(caster, center, ground, era, now, blocks, kept, order, at, duration + LAND_TICKS + 1,
                new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    public UUID caster() {
        return caster;
    }

    public Vec3 center() {
        return center;
    }

    public boolean isBuilt(long pos) {
        return blocks.containsKey(pos);
    }

    /**
     * How far across the ground from the middle of its Hex it reaches, at its furthest corner.
     */
    public float reach() {
        double dx = Math.max(Math.abs(min.getX() - center.x), Math.abs(max.getX() + 1 - center.x));
        double dz = Math.max(Math.abs(min.getZ() - center.z), Math.abs(max.getZ() + 1 - center.z));
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    public RemnantSnapshot snapshot() {
        return new RemnantSnapshot(id, caster, center, min, max, start, ground, unbuildTicks);
    }

    /**
     * Lets it go on, glitching while it holds on and then a block at a time.
     *
     * @param unloaded     what to do with blocks in chunks that aren't loaded when their time comes
     * @param unloadedKept the same for what hung on it
     * @return whether it is all gone
     */
    public boolean tick(ServerLevel level, long now, BiConsumer<Long, HexTown.Built> unloaded, Consumer<HexTown.Kept> unloadedKept) {
        long since = now - start;
        if (!begun) {
            // the picture losing its hold, as the wall closes in over it
            begun = true;
            play(level, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 2.0F, 0.5F);
            play(level, SoundEvents.BEACON_DEACTIVATE, 2.0F, 0.6F);
        }
        if (told.isEmpty() || now % TELL_INTERVAL == 0) {
            tell(level);
        }
        if (since < RemnantSnapshot.GLITCH_TICKS) {
            return false;
        }
        int elapsed = (int) (since - RemnantSnapshot.GLITCH_TICKS);
        if (elapsed == 0) {
            play(level, SoundEvents.BEACON_DEACTIVATE, 2.5F, 0.45F);
            play(level, SoundEvents.ILLUSIONER_MIRROR_MOVE, 2.0F, 0.5F);
        }
        announce(level, elapsed);
        while (cursor < order.length && at[cursor] <= elapsed) {
            long key = order[cursor++];
            HexTown.Built entry = blocks.remove(key);
            if (entry == null) {
                continue;
            }
            if (!level.hasChunkAt(BlockPos.of(key))) {
                unloaded.accept(key, entry);
                continue;
            }
            if (restore(level, key, entry) && entry.role() == Role.CLEAR) {
                gone(key);
            }
        }
        sendGone(level);
        if (cursor < order.length) {
            return false;
        }
        // with its walls back as they were, what hung on them can go back up
        for (HexTown.Kept entry : kept) {
            if (level.hasChunkAt(BlockPos.containing(entry.at()))) {
                HexTown.setDown(level, entry);
            } else {
                unloadedKept.accept(entry);
            }
        }
        kept.clear();
        play(level, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.6F, 0.6F);
        play(level, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1.2F, 1.3F);
        return true;
    }

    /**
     * Puts it all back at once: when a Hex is cast over it, or every Hex here is dispelled. What lies in chunks that
     * aren't loaded is handed over to be put back when they are.
     */
    public void finish(ServerLevel level, BiConsumer<Long, HexTown.Built> unloaded, Consumer<HexTown.Kept> unloadedKept) {
        // bottom up, so plants come back onto their ground
        Long[] keys = Arrays.stream(blocks.keySet().toLongArray()).boxed().toArray(Long[]::new);
        Arrays.sort(keys, (a, b) -> Integer.compare(BlockPos.getY(a), BlockPos.getY(b)));
        for (long key : keys) {
            HexTown.Built entry = blocks.remove(key);
            if (entry == null) {
                continue;
            }
            if (!level.hasChunkAt(BlockPos.of(key))) {
                unloaded.accept(key, entry);
                continue;
            }
            restore(level, key, entry);
        }
        for (HexTown.Kept entry : kept) {
            if (level.hasChunkAt(BlockPos.containing(entry.at()))) {
                HexTown.setDown(level, entry);
            } else {
                unloadedKept.accept(entry);
            }
        }
        kept.clear();
        cursor = order.length;
    }

    /**
     * Copies what is left of it into the blocks waiting to be put back, for the world to be saved with.
     */
    public void saveBlocksInto(Long2ObjectOpenHashMap<HexTown.Built> pending) {
        pending.putAll(blocks);
    }

    /**
     * The same for what hung on it.
     */
    public void saveKeptInto(List<HexTown.Kept> pendingKept) {
        pendingKept.addAll(kept);
    }

    /**
     * Puts back what stood at a spot before the town, unless a player has since put something of their own there, and
     * the other half of a tall plant with it.
     *
     * @return whether anything changed in the world
     */
    private boolean restore(ServerLevel level, long key, HexTown.Built entry) {
        BlockPos pos = BlockPos.of(key);
        BlockState current = level.getBlockState(pos);
        if (!HexTown.isStill(entry, current) && !HexPaint.paintedOver(level, pos, current)) {
            return false;
        }
        boolean moved = HexTown.putBack(level, pos, current, entry);
        if (entry.original().hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            BlockPos other = entry.original().getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            HexTown.Built pair = blocks.remove(other.asLong());
            if (pair != null) {
                BlockState otherNow = level.getBlockState(other);
                if (HexTown.isStill(pair, otherNow)) {
                    HexTown.putBack(level, other, otherNow, pair);
                }
            }
        }
        return moved;
    }

    /**
     * Tells everyone near enough to see it who hasn't been yet what it is built of, for their clients to glitch it
     * through the eras.
     */
    private void tell(ServerLevel level) {
        RemnantBlocksPayload payload = null;
        for (ServerPlayer player : level.players()) {
            if (told.contains(player.getUUID()) || player.distanceToSqr(center) > SEEN_FROM * SEEN_FROM) {
                continue;
            }
            if (payload == null) {
                payload = blocksPayload();
            }
            Services.NETWORK.sendToPlayer(player, payload);
            told.add(player.getUUID());
        }
    }

    private RemnantBlocksPayload blocksPayload() {
        LongArrayList positions = new LongArrayList();
        ByteArrayList roles = new ByteArrayList();
        ByteArrayList paints = new ByteArrayList();
        for (var entry : blocks.long2ObjectEntrySet()) {
            HexTown.Built block = entry.getValue();
            if (block.role() == Role.CLEAR || block.role() == Role.PRESERVE) {
                continue;
            }
            positions.add(entry.getLongKey());
            roles.add((byte) block.role().ordinal());
            paints.add((byte) block.paint());
        }
        return new RemnantBlocksPayload(id, era.ordinal(), positions.toLongArray(), roles.toByteArray(), paints.toByteArray());
    }

    /**
     * Tells everyone near enough to see it which of its blocks are about to go, and when.
     */
    private void announce(ServerLevel level, int elapsed) {
        IntArrayList leaving = new IntArrayList();
        while (announced < order.length && at[announced] <= elapsed + LEAVE_LEAD) {
            long key = order[announced];
            int when = at[announced];
            announced++;
            HexTown.Built entry = blocks.get(key);
            if (entry == null || entry.role() == Role.CLEAR || entry.role() == Role.PRESERVE) {
                continue;
            }
            leaving.add(BlockPos.getX(key));
            leaving.add(BlockPos.getY(key));
            leaving.add(BlockPos.getZ(key));
            leaving.add(Math.max(0, when - elapsed));
        }
        if (leaving.isEmpty()) {
            return;
        }
        TownUnformPayload payload = new TownUnformPayload(leaving.toIntArray());
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) < SEEN_FROM * SEEN_FROM) {
                Services.NETWORK.sendToPlayer(player, payload);
            }
        }
    }

    private void gone(long key) {
        int x = BlockPos.getX(key) >> 1;
        int z = BlockPos.getZ(key) >> 1;
        int y = BlockPos.getY(key);
        long cell = (long) x << 32 | z & 0xFFFFFFFFL;
        long span = gone.get(cell);
        int low = span == NOT_GONE ? y : Math.min(y, (int) (span >> 32));
        int high = span == NOT_GONE ? y : Math.max(y, (int) span);
        gone.put(cell, (long) low << 32 | high & 0xFFFFFFFFL);
    }

    /**
     * Tells everyone nearby where the land just came back, to show it glitching back in.
     */
    private void sendGone(ServerLevel level) {
        if (gone.isEmpty()) {
            return;
        }
        int count = gone.size();
        int keep = Math.min(count, MAX_GONE_CELLS);
        int[] cells = new int[keep * 4];
        int taken = 0;
        int index = 0;
        for (Long2LongMap.Entry entry : gone.long2LongEntrySet()) {
            if (taken < keep && (long) index * keep / count >= taken) {
                long cell = entry.getLongKey();
                long span = entry.getLongValue();
                cells[taken * 4] = (int) (cell >> 32) << 1;
                cells[taken * 4 + 1] = (int) cell << 1;
                cells[taken * 4 + 2] = (int) (span >> 32);
                cells[taken * 4 + 3] = (int) span;
                taken++;
            }
            index++;
        }
        gone.clear();
        TownEvents.gone(level, center, 0.0F, taken == keep ? cells : Arrays.copyOf(cells, taken * 4));
    }

    private void play(ServerLevel level, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, (min.getX() + max.getX() + 1) / 2.0, (min.getY() + max.getY() + 1) / 2.0, (min.getZ() + max.getZ() + 1) / 2.0, sound,
                SoundSource.BLOCKS, volume, pitch);
    }
}
