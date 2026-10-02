package com.yashjit.scarlet.hex.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.hex.HexBuild;
import com.yashjit.scarlet.hex.HexShape;
import com.yashjit.scarlet.hex.town.Blueprint.Frame;
import com.yashjit.scarlet.hex.town.Blueprint.Piece;
import com.yashjit.scarlet.hex.town.TownPlan.Kind;
import com.yashjit.scarlet.hex.town.TownPlan.Part;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Hex's town: every block it has put down and what stood there before, the parts of its plan it has built, and
 * those it is building now.
 *
 * <p>Parts build themselves as the Hex's wall passes over them, and again whenever it grows. A part that no longer
 * fits after the Hex shrinks is taken back down. When the era changes, a makeover sweeps out from the middle, and
 * when the Hex falls, the town comes down as the wall rushes in, leaving the land as it was. Blocks a player has put
 * in place of the town's are left alone.
 */
public final class HexTown {

    /** Most blocks the town changes in a tick, building, restyling and taking down together. */
    private static final int BUDGET = 384;
    /** Most blocks it puts back in a tick while the Hex falls. */
    private static final int COLLAPSE_BUDGET = 2048;
    private static final int TRIGGER_INTERVAL = 4;
    private static final int STARTS_PER_TRIGGER = 3;
    private static final int RETRY_SKIPPED = 400;
    private static final int RESTYLE_TICKS = 40;
    private static final int BUILT_FLAGS = Block.UPDATE_ALL;
    private static final int QUIET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final Codec<int[]> INTS = Codec.INT_STREAM.xmap(IntStream::toArray, IntStream::of);
    private static final Codec<long[]> LONGS = Codec.LONG_STREAM.xmap(LongStream::toArray, LongStream::of);

    /** Town blocks by position, saved compactly. */
    public static final Codec<Long2ObjectOpenHashMap<Built>> BLOCKS_CODEC = Saved.CODEC.xmap(saved -> {
        Long2ObjectOpenHashMap<Built> blocks = new Long2ObjectOpenHashMap<>();
        saved.into(blocks);
        return blocks;
    }, Saved::of);

    public static final Codec<HexTown> CODEC = RecordCodecBuilder.create(i -> i.group(
            HexBuild.CODEC.optionalFieldOf("mode", HexBuild.TOWN).forGetter(town -> town.mode),
            Direction.CODEC.optionalFieldOf("forward", Direction.NORTH).forGetter(town -> town.forward),
            Codec.LONG.optionalFieldOf("seed", 0L).forGetter(town -> town.seed),
            Era.CODEC.optionalFieldOf("built_era", Era.FIFTIES).forGetter(town -> town.builtEra),
            Saved.CODEC.optionalFieldOf("blocks", Saved.EMPTY).forGetter(town -> Saved.of(town.built)),
            INTS.optionalFieldOf("finished", new int[0]).forGetter(town -> town.finished.toIntArray()),
            INTS.optionalFieldOf("started", new int[0]).forGetter(town -> town.started.keySet().toIntArray()),
            LONGS.optionalFieldOf("started_at", new long[0]).forGetter(town -> {
                int[] ids = town.started.keySet().toIntArray();
                return Arrays.stream(ids).mapToLong(town.started::get).toArray();
            }),
            INTS.optionalFieldOf("leveled", new int[0]).forGetter(town -> town.levels.keySet().toIntArray()),
            INTS.optionalFieldOf("levels", new int[0]).forGetter(town -> {
                int[] ids = town.levels.keySet().toIntArray();
                return Arrays.stream(ids).map(town.levels::get).toArray();
            })
    ).apply(i, HexTown::load));

    private final HexBuild mode;
    private final Direction forward;
    private final long seed;
    private final Long2ObjectOpenHashMap<Built> built = new Long2ObjectOpenHashMap<>();
    /** The ground level each part was laid at, from when it was begun. */
    private final Int2IntOpenHashMap levels = new Int2IntOpenHashMap();
    private final IntOpenHashSet finished = new IntOpenHashSet();
    /** Parts being built, and when they began. */
    private final Int2LongOpenHashMap started = new Int2LongOpenHashMap();
    /** The era the town's blocks are in now, which a makeover brings in line with its Hex's. */
    private Era builtEra;

    private @Nullable TownPlan plan;
    private @Nullable BlockPos planCenter;
    private final IntOpenHashSet skipped = new IntOpenHashSet();
    private final Int2ObjectLinkedOpenHashMap<Build> builds = new Int2ObjectLinkedOpenHashMap<>();
    private final ArrayDeque<Long> takedown = new ArrayDeque<>();
    private @Nullable Sweep restyle;
    private @Nullable Sweep collapse;
    private boolean changed;

    public HexTown(HexBuild mode, Direction forward, long seed, Era era) {
        this.mode = mode;
        this.forward = forward;
        this.seed = seed;
        this.builtEra = era;
    }

    private static HexTown load(HexBuild mode, Direction forward, long seed, Era era, Saved blocks, int[] finished, int[] started,
                                long[] startedAt, int[] leveled, int[] levels) {
        HexTown town = new HexTown(mode, forward.getAxis().isHorizontal() ? forward : Direction.NORTH, seed, era);
        blocks.into(town.built);
        for (int id : finished) {
            town.finished.add(id);
        }
        for (int k = 0; k < Math.min(started.length, startedAt.length); k++) {
            town.started.put(started[k], startedAt[k]);
        }
        for (int k = 0; k < Math.min(leveled.length, levels.length); k++) {
            town.levels.put(leveled[k], levels[k]);
        }
        return town;
    }

    public HexBuild mode() {
        return mode;
    }

    public Direction forward() {
        return forward;
    }

    public boolean isBuilt(long pos) {
        return built.containsKey(pos);
    }

    public int size() {
        return built.size();
    }

    /**
     * Whether anything has changed that needs saving since this was last asked.
     */
    public boolean takeChanged() {
        boolean was = changed;
        changed = false;
        return was;
    }

    public TownPlan plan(BlockPos center) {
        if (plan == null || !center.equals(planCenter)) {
            plan = new TownPlan(center, forward, com.yashjit.scarlet.hex.Hexes.MAX_RADIUS);
            planCenter = center;
        }
        return plan;
    }

    // ---------------------------------------------------------------- the home

    /**
     * The caster's home in the plan.
     */
    public @Nullable Part home(BlockPos center) {
        for (Part part : plan(center).parts()) {
            if (part.kind() == Kind.HOME) {
                return part;
            }
        }
        return null;
    }

    /**
     * Begins building the caster's home, if its lot is open land.
     *
     * @return how many ticks it will take, or -1 if there is no room for it
     */
    public int foundHome(ServerLevel level, Vec3 hexCenter, Era era, long now) {
        BlockPos center = BlockPos.containing(hexCenter);
        Part home = home(center);
        if (mode == HexBuild.NOTHING || home == null || !isLoaded(level, home)) {
            return -1;
        }
        Survey survey = survey(level, home);
        if (survey == null) {
            return -1;
        }
        Build build = start(level, center, home, survey, now);
        return build.duration();
    }

    public boolean isHomeFinished(BlockPos center) {
        Part home = home(center);
        return home == null || finished.contains(home.id()) || !started.containsKey(home.id()) && !builds.containsKey(home.id());
    }

    // ---------------------------------------------------------------- the tick

    /**
     * @param wall   how far the Hex's wall has spread, or is falling inward
     * @param radius the Hex's size now, which what is built must fit inside
     */
    public void tick(ServerLevel level, Vec3 hexCenter, float wall, float radius, Era era, boolean falling, long now) {
        BlockPos center = BlockPos.containing(hexCenter);
        if (falling) {
            fall(level, hexCenter, wall);
            return;
        }
        int budget = BUDGET;
        budget = takeDown(level, budget);
        if (now % TRIGGER_INTERVAL == 0) {
            shrinkTo(level, center, hexCenter, radius);
            trigger(level, center, hexCenter, wall, now);
        }
        if (now % RETRY_SKIPPED == 0) {
            skipped.clear();
        }
        budget = advance(level, center, era, now, budget);
        restyle(level, hexCenter, era, now, budget);
    }

    /**
     * Starts building whatever now fits inside the wall, nearest first.
     */
    private void trigger(ServerLevel level, BlockPos center, Vec3 hexCenter, float wall, long now) {
        if (mode == HexBuild.NOTHING || wall <= 0.0F) {
            return;
        }
        int starts = 0;
        for (Part part : plan(center).parts()) {
            int id = part.id();
            if (finished.contains(id) || started.containsKey(id) || builds.containsKey(id) || skipped.contains(id)) {
                continue;
            }
            if (mode == HexBuild.HOME && part.kind() != Kind.HOME) {
                continue;
            }
            if (!TownPlan.fits(part, hexCenter, wall, center.getY())) {
                continue;
            }
            if (!isLoaded(level, part)) {
                continue;
            }
            Survey survey = survey(level, part);
            if (survey == null) {
                skipped.add(id);
                continue;
            }
            start(level, center, part, survey, now);
            if (++starts >= STARTS_PER_TRIGGER) {
                return;
            }
        }
    }

    private Build start(ServerLevel level, BlockPos center, Part part, Survey survey, long now) {
        Blueprint blueprint = blueprint(part, survey);
        List<Piece> pieces = blueprint.schedule();
        if (survey.blocked() != null) {
            pieces.removeIf(piece -> survey.isBlocked(part, piece.pos()));
        }
        Build build = new Build(part, survey.ground(), pieces, now, blueprint.duration());
        builds.put(part.id(), build);
        started.put(part.id(), now);
        levels.put(part.id(), survey.ground());
        changed = true;
        TownEvents.started(level, part, survey.ground(), now, blueprint.duration());
        return build;
    }

    /**
     * Picks up builds that were under way when the world was last saved.
     */
    private void resume(ServerLevel level, BlockPos center) {
        if (started.isEmpty() || started.size() == builds.size()) {
            return;
        }
        Map<Integer, Part> byId = new HashMap<>();
        for (Part part : plan(center).parts()) {
            byId.put(part.id(), part);
        }
        for (int id : started.keySet().toIntArray()) {
            if (builds.containsKey(id)) {
                continue;
            }
            Part part = byId.get(id);
            if (part == null || !isLoaded(level, part)) {
                continue;
            }
            Survey survey = survey(level, part);
            if (survey == null) {
                survey = new Survey(levels.getOrDefault(id, center.getY()), null, null, part.width());
            }
            Survey leveled = new Survey(levels.getOrDefault(id, survey.ground()), survey.heights(), survey.blocked(), part.width());
            Blueprint blueprint = blueprint(part, leveled);
            List<Piece> pieces = blueprint.schedule();
            if (leveled.blocked() != null) {
                pieces.removeIf(piece -> leveled.isBlocked(part, piece.pos()));
            }
            builds.put(id, new Build(part, leveled.ground(), pieces, started.get(id), blueprint.duration()));
        }
    }

    private int advance(ServerLevel level, BlockPos center, Era era, long now, int budget) {
        resume(level, center);
        Iterator<Build> iterator = builds.values().iterator();
        while (iterator.hasNext() && budget > 0) {
            Build build = iterator.next();
            long elapsed = now - build.start;
            while (build.next < build.pieces.size() && budget > 0) {
                Piece piece = build.pieces.get(build.next);
                if (piece.tick() > elapsed) {
                    break;
                }
                build.next++;
                if (place(level, build, piece, era)) {
                    budget--;
                }
            }
            if (build.next >= build.pieces.size()) {
                iterator.remove();
                started.remove(build.part.id());
                finished.add(build.part.id());
                changed = true;
                TownEvents.finished(level, build.part);
            }
        }
        return budget;
    }

    // ---------------------------------------------------------------- putting blocks down

    /**
     * @return whether anything changed in the world
     */
    private boolean place(ServerLevel level, Build build, Piece piece, Era era) {
        BlockPos pos = piece.pos();
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState target = EraStyle.state(piece.role(), era, piece.paint(), piece.template());
        if (target.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return placeDouble(level, build, piece, target);
        }
        long key = pos.asLong();
        BlockState current = level.getBlockState(pos);
        Built existing = built.get(key);
        if (existing == null && !Terrain.isReplaceable(current)) {
            // something that isn't the town's or open land stands here now; leave it be
            return false;
        }
        BlockState shaped = Block.updateFromNeighbourShapes(target, level, pos);
        if (shaped.isAir() && !target.isAir()) {
            return false;
        }
        if (shaped == current) {
            return false;
        }
        if (existing == null) {
            record(level, pos, current, piece, build.part.id());
        } else if (existing.part() != build.part.id() || existing.role() != piece.role()) {
            built.put(key, new Built(existing.original(), piece.role(), piece.paint(), build.part.id()));
        }
        level.setBlock(pos, shaped, BUILT_FLAGS);
        nudge(level, build, pos, shaped);
        changed = true;
        return true;
    }

    /**
     * Doors and the like are put down both halves at once, or the first would fall apart waiting for the second.
     */
    private boolean placeDouble(ServerLevel level, Build build, Piece piece, BlockState target) {
        if (target.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            // the lower half put this down already
            return false;
        }
        BlockPos lower = piece.pos();
        BlockPos upper = lower.above();
        BlockState lowerNow = level.getBlockState(lower);
        BlockState upperNow = level.getBlockState(upper);
        boolean lowerOurs = built.containsKey(lower.asLong());
        boolean upperOurs = built.containsKey(upper.asLong());
        if (!lowerOurs && !Terrain.isReplaceable(lowerNow) || !upperOurs && !Terrain.isReplaceable(upperNow)) {
            return false;
        }
        Piece upperPiece = new Piece(upper, piece.role(), piece.paint(), piece.template(), piece.stage(), piece.tick());
        if (!lowerOurs) {
            record(level, lower, lowerNow, piece, build.part.id());
        }
        if (!upperOurs) {
            record(level, upper, upperNow, upperPiece, build.part.id());
        }
        level.setBlock(lower, target, QUIET_FLAGS);
        level.setBlock(upper, target.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), QUIET_FLAGS);
        level.getBlockState(lower).updateNeighbourShapes(level, lower, BUILT_FLAGS);
        level.getBlockState(upper).updateNeighbourShapes(level, upper, BUILT_FLAGS);
        changed = true;
        return true;
    }

    /**
     * Remembers what stood somewhere before the town built there. A plant two blocks tall is remembered whole, since
     * taking either half takes both.
     */
    private void record(ServerLevel level, BlockPos pos, BlockState original, Piece piece, int part) {
        built.put(pos.asLong(), new Built(original, piece.role(), piece.paint(), part));
        if (original.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            BlockPos other = original.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            long otherKey = other.asLong();
            if (!built.containsKey(otherKey)) {
                built.put(otherKey, new Built(level.getBlockState(other), Role.CLEAR, 0, part));
            }
        }
    }

    /**
     * Moves anyone caught inside a new block: up onto it near the ground, otherwise out onto the lawn in front.
     */
    private static void nudge(ServerLevel level, Build build, BlockPos pos, BlockState state) {
        if (state.getCollisionShape(level, pos).isEmpty()) {
            return;
        }
        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos));
        for (LivingEntity entity : caught) {
            if (!entity.getBoundingBox().intersects(new AABB(pos))) {
                continue;
            }
            if (pos.getY() <= build.ground + 1) {
                entity.teleportTo(entity.getX(), pos.getY() + 1.0, entity.getZ());
            } else {
                Frame frame = new Frame(build.part.origin(), build.part.front());
                BlockPos out = frame.at(build.part.width() / 2, -1, build.ground + 1);
                entity.teleportTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5);
            }
        }
    }

    // ---------------------------------------------------------------- taking it back down

    /**
     * Takes down every part that no longer fits inside the Hex.
     */
    private void shrinkTo(ServerLevel level, BlockPos center, Vec3 hexCenter, float radius) {
        if (finished.isEmpty() && builds.isEmpty()) {
            return;
        }
        for (Part part : plan(center).parts()) {
            int id = part.id();
            boolean standing = finished.contains(id) || builds.containsKey(id) || started.containsKey(id);
            if (!standing || TownPlan.fits(part, hexCenter, radius, levels.getOrDefault(id, center.getY()))) {
                continue;
            }
            finished.remove(id);
            builds.remove(id);
            started.remove(id);
            levels.remove(id);
            LongArrayList blocks = new LongArrayList();
            for (Long2ObjectMap.Entry<Built> entry : built.long2ObjectEntrySet()) {
                if (entry.getValue().part() == id) {
                    blocks.add(entry.getLongKey());
                }
            }
            long[] sorted = blocks.toLongArray();
            // from the top down, so nothing is left hanging
            Long[] boxed = Arrays.stream(sorted).boxed().toArray(Long[]::new);
            Arrays.sort(boxed, (a, b) -> Integer.compare(BlockPos.getY(b), BlockPos.getY(a)));
            takedown.addAll(Arrays.asList(boxed));
            changed = true;
        }
    }

    private int takeDown(ServerLevel level, int budget) {
        while (!takedown.isEmpty() && budget > 0) {
            long key = takedown.poll();
            Built built = this.built.get(key);
            if (built != null && restore(level, key, built)) {
                budget--;
            }
        }
        return budget;
    }

    /**
     * Brings the town down behind the wall as it rushes in.
     */
    private void fall(ServerLevel level, Vec3 hexCenter, float wall) {
        builds.clear();
        started.clear();
        if (collapse == null) {
            collapse = Sweep.outsideIn(built, hexCenter);
        }
        int budget = COLLAPSE_BUDGET;
        while (collapse.cursor < collapse.order.length && budget > 0) {
            int at = collapse.cursor;
            if (collapse.reach[at] < wall) {
                break;
            }
            collapse.cursor++;
            long key = collapse.order[at];
            Built entry = built.get(key);
            if (entry != null && restore(level, key, entry)) {
                budget--;
            }
        }
    }

    /**
     * Puts everything back at once: for a Hex dispelled outright, or at the end of its fall. What lies in chunks that
     * aren't loaded is handed over to be put back when they are.
     */
    public void restoreAll(ServerLevel level, java.util.function.BiConsumer<Long, Built> unloaded) {
        builds.clear();
        started.clear();
        takedown.clear();
        long[] keys = built.keySet().toLongArray();
        // bottom up, so plants come back onto their ground
        Long[] boxed = Arrays.stream(keys).boxed().toArray(Long[]::new);
        Arrays.sort(boxed, (a, b) -> Integer.compare(BlockPos.getY(a), BlockPos.getY(b)));
        for (long key : boxed) {
            Built entry = built.get(key);
            if (entry == null) {
                continue;
            }
            if (!level.hasChunkAt(BlockPos.of(key))) {
                built.remove(key);
                unloaded.accept(key, entry);
                continue;
            }
            restore(level, key, entry);
        }
        built.clear();
        finished.clear();
        changed = true;
    }

    /**
     * Puts back what stood at a spot before the town, unless a player has since put something of their own there.
     *
     * @return whether anything changed in the world
     */
    private boolean restore(ServerLevel level, long key, Built entry) {
        BlockPos pos = BlockPos.of(key);
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        built.remove(key);
        changed = true;
        BlockState current = level.getBlockState(pos);
        if (!current.isAir() && !EraStyle.isStyleOf(entry.role(), entry.paint(), current)) {
            return false;
        }
        boolean moved = putBack(level, pos, current, entry.original());
        // the other half of a tall plant comes back with it
        if (entry.original().hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            BlockPos other = entry.original().getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            Built pair = built.remove(other.asLong());
            if (pair != null) {
                BlockState otherNow = level.getBlockState(other);
                if (otherNow.isAir() || EraStyle.isStyleOf(pair.role(), pair.paint(), otherNow)) {
                    putBack(level, other, otherNow, pair.original());
                }
            }
        }
        return moved;
    }

    /**
     * Puts back a block left over from a town that has gone, once its chunk is loaded.
     *
     * @return whether it is done with, rather than still waiting for its chunk
     */
    public static boolean putBackLeftover(ServerLevel level, long key, Built entry) {
        BlockPos pos = BlockPos.of(key);
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState current = level.getBlockState(pos);
        if (current.isAir() || EraStyle.isStyleOf(entry.role(), entry.paint(), current)) {
            putBack(level, pos, current, entry.original());
        }
        return true;
    }

    private static boolean putBack(ServerLevel level, BlockPos pos, BlockState current, BlockState original) {
        if (current == original) {
            return false;
        }
        level.setBlock(pos, original, QUIET_FLAGS);
        original.updateNeighbourShapes(level, pos, BUILT_FLAGS);
        return true;
    }

    // ---------------------------------------------------------------- era makeovers

    /**
     * Sweeps the town into the Hex's era, outward from the middle.
     */
    private void restyle(ServerLevel level, Vec3 hexCenter, Era era, long now, int budget) {
        if (era == builtEra && restyle == null) {
            return;
        }
        if (restyle == null || restyle.era != era) {
            restyle = Sweep.insideOut(built, hexCenter);
            restyle.era = era;
            restyle.start = now;
        }
        Sweep sweep = restyle;
        float maxReach = sweep.order.length == 0 ? 0.0F : sweep.reach[sweep.order.length - 1];
        float front = maxReach * Math.min(1.0F, (now - sweep.start + 1) / (float) RESTYLE_TICKS);
        while (sweep.cursor < sweep.order.length && budget > 0) {
            int at = sweep.cursor;
            if (sweep.reach[at] > front) {
                break;
            }
            sweep.cursor++;
            long key = sweep.order[at];
            Built entry = built.get(key);
            if (entry == null || entry.role() == Role.CLEAR) {
                continue;
            }
            BlockPos pos = BlockPos.of(key);
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            BlockState current = level.getBlockState(pos);
            if (!EraStyle.isStyleOf(entry.role(), entry.paint(), current)) {
                continue;
            }
            if (current.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && current.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                continue;
            }
            BlockState next = EraStyle.state(entry.role(), era, entry.paint(), current);
            if (next == current) {
                continue;
            }
            level.setBlock(pos, next, QUIET_FLAGS);
            if (next.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                BlockPos upper = pos.above();
                BlockState upperNow = level.getBlockState(upper);
                if (EraStyle.isStyleOf(entry.role(), entry.paint(), upperNow)) {
                    level.setBlock(upper, EraStyle.state(entry.role(), era, entry.paint(), upperNow), QUIET_FLAGS);
                }
            }
            sweep.touched.add(key);
            budget--;
        }
        if (sweep.cursor >= sweep.order.length) {
            // a block's connections can only be worked out once its neighbors have changed too
            for (long key : sweep.touched.toLongArray()) {
                BlockPos pos = BlockPos.of(key);
                if (!level.hasChunkAt(pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                    continue;
                }
                BlockState shaped = Block.updateFromNeighbourShapes(state, level, pos);
                if (shaped != state && !shaped.isAir()) {
                    level.setBlock(pos, shaped, QUIET_FLAGS);
                }
            }
            builtEra = era;
            restyle = null;
            changed = true;
        }
    }

    // ---------------------------------------------------------------- the land

    private boolean isLoaded(ServerLevel level, Part part) {
        return level.hasChunkAt(new BlockPos(part.minX(), 0, part.minZ())) && level.hasChunkAt(new BlockPos(part.maxX(), 0, part.maxZ()))
                && level.hasChunkAt(new BlockPos(part.minX(), 0, part.maxZ())) && level.hasChunkAt(new BlockPos(part.maxX(), 0, part.minZ()));
    }

    /**
     * Works out the lie of the land under a part: the level to lay it at, and the columns it must leave alone. Lots
     * and the square need every column to be open land; streets pave around whatever they can't.
     *
     * @return null if the part can't go there
     */
    private @Nullable Survey survey(ServerLevel level, Part part) {
        Frame frame = new Frame(part.origin(), part.front());
        int width = part.width();
        int depth = part.depth();
        int[] heights = new int[width * depth];
        int[] valid = new int[width * depth];
        int count = 0;
        for (int b = 0; b < depth; b++) {
            for (int a = 0; a < width; a++) {
                BlockPos column = frame.at(a, b, 0);
                int height = naturalGround(level, column.getX(), column.getZ());
                heights[b * width + a] = height;
                if (height != Terrain.NO_GROUND) {
                    valid[count++] = height;
                }
            }
        }
        boolean street = part.kind().isStreet();
        if (count == 0 || !street && count < heights.length) {
            return null;
        }
        int[] sorted = Arrays.copyOf(valid, count);
        Arrays.sort(sorted);
        int ground = sorted[count / 2];
        int tolerance = street ? 2 : 3;
        boolean[] blocked = new boolean[heights.length];
        int blockedCount = 0;
        for (int b = 0; b < depth; b++) {
            for (int a = 0; a < width; a++) {
                int index = b * width + a;
                int height = heights[index];
                BlockPos column = frame.at(a, b, 0);
                boolean bad = height == Terrain.NO_GROUND || Math.abs(height - ground) > tolerance
                        || !isOpen(level, column.getX(), Math.max(height, ground), column.getZ(), ground + part.kind().height());
                if (bad) {
                    if (!street) {
                        return null;
                    }
                    blocked[index] = true;
                    blockedCount++;
                }
            }
        }
        if (blockedCount * 2 > heights.length) {
            return null;
        }
        return new Survey(ground, heights, blockedCount > 0 ? blocked : null, width);
    }

    /**
     * The height of the natural ground at a column, seeing through anything the town itself put there.
     */
    private int naturalGround(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
        for (int i = 0; i < 40 && pos.getY() >= level.getMinY(); i++, pos.move(Direction.DOWN)) {
            Built entry = built.get(pos.asLong());
            BlockState state = entry != null ? entry.original() : level.getBlockState(pos);
            if (Terrain.isGround(state)) {
                return pos.getY();
            }
            if (!Terrain.isClearable(state)) {
                return Terrain.NO_GROUND;
            }
        }
        return Terrain.NO_GROUND;
    }

    /**
     * Whether everything from just above one height up to another is open, again seeing through the town's own blocks.
     */
    private boolean isOpen(ServerLevel level, int x, int from, int z, int to) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = from + 1; y <= to; y++) {
            pos.set(x, y, z);
            Built entry = built.get(pos.asLong());
            BlockState state = entry != null ? entry.original() : level.getBlockState(pos);
            if (!Terrain.isClearable(state)) {
                return false;
            }
        }
        return true;
    }

    private Blueprint blueprint(Part part, Survey survey) {
        Frame frame = new Frame(part.origin(), part.front());
        int ground = survey.ground();
        Houses.Heights heights = (a, b) -> {
            int[] all = survey.heights();
            if (all == null) {
                return ground;
            }
            int height = all[b * part.width() + a];
            return height == Terrain.NO_GROUND ? ground : height;
        };
        RandomSource random = RandomSource.create(seed ^ (part.id() * 0x9E3779B97F4A7C15L));
        return switch (part.kind()) {
            case HOME -> Houses.house(frame, ground, heights, Houses.Shape.TWO_STORY, random);
            case HOUSE -> Houses.house(frame, ground, heights, random.nextBoolean() ? Houses.Shape.SIDE_GABLE : Houses.Shape.FRONT_GABLE, random);
            case SQUARE -> Streets.square(frame, part.width(), part.depth(), ground, heights, random);
            case STREET -> Streets.street(frame, part.width(), ground, heights, 0);
            case CROSSING -> Streets.crossing(frame, ground, heights);
            case CROSS_STREET -> Streets.street(frame, part.width(), ground, heights, part.width() / 2);
        };
    }

    // ---------------------------------------------------------------- records

    /**
     * A block the town put down.
     *
     * @param original what stood there before
     * @param paint    which of its role's variants it is
     * @param part     the part of the plan it belongs to
     */
    public record Built(BlockState original, Role role, int paint, int part) {
    }

    /**
     * @param heights the natural ground under each column, by b * width + a; null if unknown
     * @param blocked columns a street must leave alone; null for none
     */
    private record Survey(int ground, int @Nullable [] heights, boolean @Nullable [] blocked, int width) {

        boolean isBlocked(Part part, BlockPos pos) {
            if (blocked == null) {
                return false;
            }
            Frame frame = new Frame(part.origin(), part.front());
            Direction right = frame.right();
            Direction back = frame.back();
            int dx = pos.getX() - part.origin().getX();
            int dz = pos.getZ() - part.origin().getZ();
            int a = dx * right.getStepX() + dz * right.getStepZ();
            int b = dx * back.getStepX() + dz * back.getStepZ();
            int index = b * width + a;
            return a >= 0 && a < width && b >= 0 && index < blocked.length && blocked[index];
        }
    }

    private static final class Build {
        final Part part;
        final int ground;
        final List<Piece> pieces;
        final long start;
        final int duration;
        int next;

        Build(Part part, int ground, List<Piece> pieces, long start, int duration) {
            this.part = part;
            this.ground = ground;
            this.pieces = pieces;
            this.start = start;
            this.duration = duration;
        }

        int duration() {
            return duration;
        }
    }

    /**
     * The town's blocks in order of how far out they lie, for a sweep across it.
     */
    private static final class Sweep {
        final long[] order;
        final float[] reach;
        final it.unimi.dsi.fastutil.longs.LongArrayList touched = new it.unimi.dsi.fastutil.longs.LongArrayList();
        int cursor;
        @Nullable Era era;
        long start;

        private Sweep(long[] order, float[] reach) {
            this.order = order;
            this.reach = reach;
        }

        static Sweep insideOut(Long2ObjectOpenHashMap<Built> built, Vec3 center) {
            return sorted(built, center, true);
        }

        static Sweep outsideIn(Long2ObjectOpenHashMap<Built> built, Vec3 center) {
            return sorted(built, center, false);
        }

        private static Sweep sorted(Long2ObjectOpenHashMap<Built> built, Vec3 center, boolean ascending) {
            long[] keys = built.keySet().toLongArray();
            float[] reaches = new float[keys.length];
            Integer[] index = new Integer[keys.length];
            for (int k = 0; k < keys.length; k++) {
                BlockPos pos = BlockPos.of(keys[k]);
                reaches[k] = (float) HexShape.level(center, 1.0F, Vec3.atCenterOf(pos));
                index[k] = k;
            }
            Arrays.sort(index, (a, b) -> ascending ? Float.compare(reaches[a], reaches[b]) : Float.compare(reaches[b], reaches[a]));
            long[] order = new long[keys.length];
            float[] reach = new float[keys.length];
            for (int k = 0; k < keys.length; k++) {
                order[k] = keys[index[k]];
                reach[k] = reaches[index[k]];
            }
            return new Sweep(order, reach);
        }
    }

    /**
     * The town's blocks, saved compactly: each distinct original state once, and the rest as parallel arrays.
     */
    private record Saved(List<BlockState> palette, long[] positions, int[] originals, int[] roles, int[] parts) {

        static final Saved EMPTY = new Saved(List.of(), new long[0], new int[0], new int[0], new int[0]);

        static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.listOf().fieldOf("palette").forGetter(Saved::palette),
                LONGS.fieldOf("positions").forGetter(Saved::positions),
                INTS.fieldOf("originals").forGetter(Saved::originals),
                INTS.fieldOf("roles").forGetter(Saved::roles),
                INTS.fieldOf("parts").forGetter(Saved::parts)
        ).apply(i, Saved::new));

        static Saved of(Long2ObjectOpenHashMap<Built> built) {
            List<BlockState> palette = new ArrayList<>();
            Map<BlockState, Integer> indices = new HashMap<>();
            int size = built.size();
            long[] positions = new long[size];
            int[] originals = new int[size];
            int[] roles = new int[size];
            int[] parts = new int[size];
            int k = 0;
            for (Long2ObjectMap.Entry<Built> entry : built.long2ObjectEntrySet()) {
                Built value = entry.getValue();
                positions[k] = entry.getLongKey();
                originals[k] = indices.computeIfAbsent(value.original(), state -> {
                    palette.add(state);
                    return palette.size() - 1;
                });
                roles[k] = value.role().ordinal() | value.paint() << 8;
                parts[k] = value.part();
                k++;
            }
            return new Saved(palette, positions, originals, roles, parts);
        }

        void into(Long2ObjectOpenHashMap<Built> built) {
            int count = Math.min(Math.min(positions.length, originals.length), Math.min(roles.length, parts.length));
            for (int k = 0; k < count; k++) {
                int state = originals[k];
                if (state < 0 || state >= palette.size()) {
                    continue;
                }
                built.put(positions[k], new Built(palette.get(state), Role.byIndex(roles[k] & 0xFF), roles[k] >>> 8, parts[k]));
            }
        }
    }
}
