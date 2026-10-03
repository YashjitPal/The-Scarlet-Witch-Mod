package com.yashjit.scarlet.hex;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yashjit.scarlet.hex.town.Terrain;
import com.yashjit.scarlet.network.PaintPayload;
import com.yashjit.scarlet.platform.Services;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Restyling inside a Hex. Its caster holds a block in their off hand, holds the cast and sweeps their aim: what they
 * look at takes on that block's look, a patch at a time over the surface, or a block at a time while they sneak.
 *
 * <p>Only the look changes. Every painted block remembers what it was: breaking one gives what it was, pistons can't
 * carry one off, and each changes back as the wall passes over it, when the Hex is drawn in or falls.
 *
 * <p>Over the Hex's own town, paint stays the town's: era makeovers pass it by, and when the town comes down it puts
 * back what stood before it, painted or not. Where the town builds over painted ground, it remembers the ground as it
 * was before it was painted.
 */
public final class HexPaint {

    /** How far off the caster can paint. */
    public static final double REACH = 32.0;
    /** Blocks out from the middle of the brush, along both ways across the face it is on. */
    private static final int BRUSH = 1;
    /** Ticks between telling those nearby that someone is still painting, while nothing changes. */
    private static final int STILL_PAINTING = 3;
    private static final double SEEN_FROM = 96.0;
    /** Painting changes only how a block looks, so nothing around it is told and nothing drops. */
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final Codec<int[]> INTS = Codec.INT_STREAM.xmap(IntStream::toArray, IntStream::of);
    private static final Codec<long[]> LONGS = Codec.LONG_STREAM.xmap(LongStream::toArray, LongStream::of);

    /** Painted blocks by position, saved with a palette of the blocks used. */
    static final Codec<Long2ObjectOpenHashMap<Painted>> BLOCKS_CODEC = RecordCodecBuilder.<Saved>create(i -> i.group(
            BlockState.CODEC.listOf().fieldOf("palette").forGetter(Saved::palette),
            LONGS.fieldOf("positions").forGetter(Saved::positions),
            INTS.fieldOf("before").forGetter(Saved::before),
            INTS.fieldOf("after").forGetter(Saved::after)
    ).apply(i, Saved::new)).xmap(Saved::blocks, Saved::of);

    public static final Codec<HexPaint> CODEC = BLOCKS_CODEC.xmap(HexPaint::new, paint -> paint.painted);

    /** When each caster last told those nearby that they were painting. */
    private static final Map<UUID, Long> TOLD = new HashMap<>();

    private final Long2ObjectOpenHashMap<Painted> painted;
    /** How far out the wall stood when last looked at, to tell when it comes in over painted blocks. */
    private float lastWall = -1.0F;

    public HexPaint() {
        this(new Long2ObjectOpenHashMap<>());
    }

    private HexPaint(Long2ObjectOpenHashMap<Painted> painted) {
        this.painted = new Long2ObjectOpenHashMap<>(painted);
    }

    public int size() {
        return painted.size();
    }

    /**
     * @param before what stood there before it was painted
     * @param after  what it was painted
     */
    public record Painted(BlockState before, BlockState after) {
    }

    // ---------------------------------------------------------------- painting

    /**
     * What the block in a caster's off hand paints with, if anything: a whole block of something solid that keeps
     * nothing inside it, doesn't fall, and doesn't power or blow up whatever is near it.
     */
    public static @Nullable BlockState ink(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) {
            return null;
        }
        BlockState state = item.getBlock().defaultBlockState();
        Block block = state.getBlock();
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || block instanceof Fallable || block instanceof TntBlock
                || state.isSignalSource() || !state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) || state.is(Terrain.UNTOUCHABLE)) {
            return null;
        }
        return state;
    }

    /**
     * Whether the caster can take up painting now: standing in their own Hex with a block to paint with.
     */
    public static boolean canPaint(ServerPlayer player) {
        Hex hex = HexData.of(player.level()).byCaster(player.getUUID());
        return hex != null && hex.phase == Hex.Phase.STANDING && ink(player.getOffhandItem()) != null && hex.contains(player.position());
    }

    /**
     * A tick of painting: what the caster looks at takes on the look of the block in their off hand.
     *
     * @return whether they can go on, until they let go of the block or their Hex stops standing
     */
    public static boolean paint(ServerPlayer player, long now) {
        ServerLevel level = player.level();
        HexData data = HexData.of(level);
        Hex hex = data.byCaster(player.getUUID());
        BlockState ink = ink(player.getOffhandItem());
        if (hex == null || hex.phase != Hex.Phase.STANDING || ink == null) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(REACH)), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        IntArrayList changed = new IntArrayList();
        if (hit.getType() == HitResult.Type.BLOCK) {
            Direction face = hit.getDirection();
            Direction[] across = across(face);
            int reach = player.isShiftKeyDown() ? 0 : BRUSH;
            for (int a = -reach; a <= reach; a++) {
                for (int b = -reach; b <= reach; b++) {
                    BlockPos pos = surface(level, hit.getBlockPos().relative(across[0], a).relative(across[1], b), face);
                    if (pos != null && paintOne(level, player, hex, pos, ink)) {
                        changed.add(pos.getX());
                        changed.add(pos.getY());
                        changed.add(pos.getZ());
                    }
                }
            }
        }
        if (!changed.isEmpty()) {
            data.setDirty();
            Vec3 at = hit.getLocation();
            if (now % 2 == 0) {
                level.playSound(null, at.x, at.y, at.z, ink.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.45F, 1.25F);
            }
            if (now % 4 == 0) {
                level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.35F, 1.7F + level.getRandom().nextFloat() * 0.2F);
            }
        }
        Long told = TOLD.get(player.getUUID());
        if (!changed.isEmpty() || told == null || now - told >= STILL_PAINTING) {
            TOLD.put(player.getUUID(), now);
            tell(level, player.position(), new PaintPayload(player.getId(), changed.toIntArray()));
        }
        return true;
    }

    public static void stop(ServerPlayer player) {
        TOLD.remove(player.getUUID());
    }

    /**
     * The block of a surface showing at a spot of the brush, looking in from the face it was laid on: the block there,
     * or where the surface steps down or up a block, the one behind or in front of it.
     */
    private static @Nullable BlockPos surface(ServerLevel level, BlockPos cell, Direction face) {
        for (BlockPos pos : new BlockPos[] {cell, cell.relative(face.getOpposite()), cell.relative(face)}) {
            BlockState state = level.getBlockState(pos);
            if (canTake(level, pos, state) && !level.getBlockState(pos.relative(face)).isSolidRender()) {
                return pos;
            }
        }
        return null;
    }

    private static boolean paintOne(ServerLevel level, ServerPlayer player, Hex hex, BlockPos pos, BlockState ink) {
        if (!level.hasChunkAt(pos) || !hex.contains(Vec3.atCenterOf(pos)) || !level.mayInteract(player, pos)) {
            return false;
        }
        BlockState current = level.getBlockState(pos);
        BlockState next = shaped(ink, current);
        if (next == current) {
            return false;
        }
        long key = pos.asLong();
        Painted was = hex.paint.painted.get(key);
        // painted over again, it still remembers what it first was; changed by hand since, it is what it is now
        BlockState before = was != null && was.after().getBlock() == current.getBlock() ? was.before() : current;
        level.setBlock(pos, next, QUIET);
        if (next == before) {
            hex.paint.painted.remove(key);
        } else {
            hex.paint.painted.put(key, new Painted(before, next));
        }
        return true;
    }

    /**
     * Whether a block can be painted over: a whole block of something solid, keeping nothing inside it, that can be
     * broken and powers nothing.
     */
    private static boolean canTake(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity() && !state.isSignalSource()
                && state.isCollisionShapeFullBlock(level, pos) && !Terrain.isUntouchable(level, pos, state);
    }

    /**
     * The block to paint with, turned the way the one it paints over is: logs keep lying as they lay.
     */
    private static BlockState shaped(BlockState ink, BlockState like) {
        BlockState state = ink;
        for (Property<?> property : like.getProperties()) {
            if (state.hasProperty(property)) {
                state = copy(state, like, property);
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState to, BlockState from, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }

    /**
     * The two ways across a face, along it.
     */
    private static Direction[] across(Direction face) {
        return switch (face.getAxis()) {
            case X -> new Direction[] {Direction.UP, Direction.SOUTH};
            case Y -> new Direction[] {Direction.EAST, Direction.SOUTH};
            case Z -> new Direction[] {Direction.UP, Direction.EAST};
        };
    }

    // ---------------------------------------------------------------- changing back

    /**
     * Every tick for each Hex, before its town: whatever painted block its wall has come in over changes back.
     */
    static void tick(ServerLevel level, HexData data, Hex hex, float wall) {
        HexPaint paint = hex.paint;
        boolean closing = wall < paint.lastWall - 1.0E-4F || hex.phase == Hex.Phase.COLLAPSING;
        paint.lastWall = wall;
        if (!closing || paint.painted.isEmpty()) {
            return;
        }
        IntArrayList back = new IntArrayList();
        boolean any = false;
        var iterator = paint.painted.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<Painted> entry = iterator.next();
            long key = entry.getLongKey();
            Painted painted = entry.getValue();
            BlockPos pos = BlockPos.of(key);
            if (HexShape.contains(hex.center, wall, Vec3.atCenterOf(pos))) {
                continue;
            }
            // the iterator's entry is reused once removed
            iterator.remove();
            any = true;
            if (!level.hasChunkAt(pos)) {
                data.pendingPaint().put(key, painted);
            } else if (putBack(level, pos, painted)) {
                back.add(pos.getX());
                back.add(pos.getY());
                back.add(pos.getZ());
            }
        }
        if (any) {
            data.setDirty();
        }
        if (!back.isEmpty()) {
            tell(level, hex.center, new PaintPayload(-1, back.toIntArray()));
        }
    }

    /**
     * Puts back everything a Hex painted at once, as it is taken down. What lies in chunks that aren't loaded goes back
     * when they are.
     */
    static void restoreAll(ServerLevel level, HexData data, Hex hex) {
        var iterator = hex.paint.painted.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<Painted> entry = iterator.next();
            BlockPos pos = BlockPos.of(entry.getLongKey());
            if (level.hasChunkAt(pos)) {
                putBack(level, pos, entry.getValue());
            } else {
                data.pendingPaint().put(entry.getLongKey(), entry.getValue());
            }
        }
        hex.paint.painted.clear();
        data.setDirty();
    }

    /**
     * Puts back painted blocks a fallen Hex left in chunks that weren't loaded, once they are.
     */
    static void putBackLeftovers(ServerLevel level, HexData data) {
        var iterator = data.pendingPaint().long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<Painted> entry = iterator.next();
            BlockPos pos = BlockPos.of(entry.getLongKey());
            if (level.hasChunkAt(pos)) {
                putBack(level, pos, entry.getValue());
                iterator.remove();
                data.setDirty();
            }
        }
    }

    /**
     * Puts back what a block was before it was painted, unless it has since been broken or changed by hand.
     */
    private static boolean putBack(ServerLevel level, BlockPos pos, Painted entry) {
        BlockState current = level.getBlockState(pos);
        if (current.getBlock() != entry.after().getBlock()) {
            return false;
        }
        level.setBlock(pos, entry.before(), QUIET);
        return true;
    }

    private static void tell(ServerLevel level, Vec3 near, PaintPayload payload) {
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(near) < SEEN_FROM * SEEN_FROM) {
                Services.NETWORK.sendToPlayer(player, payload);
            }
        }
    }

    // ---------------------------------------------------------------- what the rest of the world asks

    /**
     * What a painted block at a spot is, if one is.
     */
    public static @Nullable Painted at(ServerLevel level, BlockPos pos) {
        HexData data = HexData.of(level);
        long key = pos.asLong();
        for (Hex hex : data.all()) {
            Painted entry = hex.paint.painted.get(key);
            if (entry != null) {
                return entry;
            }
        }
        return data.pendingPaint().get(key);
    }

    /**
     * For the town building over a spot: what stood there before it was painted, if it was, to remember in place of
     * the paint. The spot is the town's from then on.
     */
    public static BlockState unpainted(ServerLevel level, BlockPos pos, BlockState current) {
        Painted entry = take(level, pos);
        return entry != null && entry.after().getBlock() == current.getBlock() ? entry.before() : current;
    }

    /**
     * For the town taking down a block of its own: whether the block there is its own under paint, for it to put back
     * what stood before it. The paint is forgotten.
     */
    public static boolean paintedOver(ServerLevel level, BlockPos pos, BlockState current) {
        Painted entry = at(level, pos);
        if (entry == null || entry.after().getBlock() != current.getBlock()) {
            return false;
        }
        take(level, pos);
        return true;
    }

    private static @Nullable Painted take(ServerLevel level, BlockPos pos) {
        HexData data = HexData.of(level);
        long key = pos.asLong();
        for (Hex hex : data.all()) {
            Painted entry = hex.paint.painted.remove(key);
            if (entry != null) {
                data.setDirty();
                return entry;
            }
        }
        Painted entry = data.pendingPaint().remove(key);
        if (entry != null) {
            data.setDirty();
        }
        return entry;
    }

    /**
     * Painted blocks as saved: a palette of the blocks used, and for each position the palette index of what it was
     * and of what it was painted.
     */
    private record Saved(List<BlockState> palette, long[] positions, int[] before, int[] after) {

        static Saved of(Long2ObjectOpenHashMap<Painted> blocks) {
            List<BlockState> palette = new ArrayList<>();
            Map<BlockState, Integer> index = new HashMap<>();
            long[] positions = new long[blocks.size()];
            int[] before = new int[blocks.size()];
            int[] after = new int[blocks.size()];
            int k = 0;
            for (Long2ObjectMap.Entry<Painted> entry : blocks.long2ObjectEntrySet()) {
                positions[k] = entry.getLongKey();
                before[k] = index.computeIfAbsent(entry.getValue().before(), state -> {
                    palette.add(state);
                    return palette.size() - 1;
                });
                after[k] = index.computeIfAbsent(entry.getValue().after(), state -> {
                    palette.add(state);
                    return palette.size() - 1;
                });
                k++;
            }
            return new Saved(palette, positions, before, after);
        }

        Long2ObjectOpenHashMap<Painted> blocks() {
            Long2ObjectOpenHashMap<Painted> blocks = new Long2ObjectOpenHashMap<>();
            int count = Math.min(positions.length, Math.min(before.length, after.length));
            for (int k = 0; k < count; k++) {
                if (before[k] >= 0 && before[k] < palette.size() && after[k] >= 0 && after[k] < palette.size()) {
                    blocks.put(positions[k], new Painted(palette.get(before[k]), palette.get(after[k])));
                }
            }
            return blocks;
        }
    }
}
