package com.yashjit.scarlet.hex.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.hex.HexBuild;
import com.yashjit.scarlet.hex.HexPaint;
import com.yashjit.scarlet.hex.HexShape;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.hex.town.Blueprint.Frame;
import com.yashjit.scarlet.entity.ParkedCar;
import com.yashjit.scarlet.hex.town.Blueprint.Piece;
import com.yashjit.scarlet.hex.town.TownPlan.Kind;
import com.yashjit.scarlet.hex.town.TownPlan.Part;
import com.yashjit.scarlet.network.TownGlitchPayload;
import com.yashjit.scarlet.platform.Services;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Hex's town: every block it has put down and what stood there before, the parts of its plan it has built, and
 * those it is building now.
 *
 * <p>The Hex rewrites everything inside it into a suburb. As its wall passes over each part of the plan, whatever stood
 * there dissolves from the top down, a village, a forest, someone's base, and the land is leveled, cutting hills and
 * filling hollows, before the house or street builds itself. Nothing is lost: every block cleared away is remembered,
 * with what was in it, and so are paintings, item frames, armor stands and parked carts, and the leaves of trees cut
 * through are held fast so they don't wither. Where the wall cuts through the plan, gardens and street ends run right
 * up to it.
 *
 * <p>A part that no longer fits after the Hex shrinks is taken back down. When the era changes, a makeover sweeps out
 * from the middle, and when the Hex falls, the town comes down as the wall closes in, leaving the land exactly as it
 * was: all but the caster's home, which the wall lets go of to stand on alone for a while (see {@link HomeRemnant}).
 * Blocks a player has put in place of the town's are left alone.
 */
public final class HexTown {

    /** Most blocks the town changes in a tick, building, restyling and taking down together. */
    private static final int BUDGET = 512;
    /** Most blocks it puts back in a tick while the Hex falls. */
    private static final int COLLAPSE_BUDGET = 2048;
    private static final int TRIGGER_INTERVAL = 4;
    private static final int STARTS_PER_TRIGGER = 3;
    private static final int RETRY_SKIPPED = 400;
    /** How near a player must be to a slipping part to be shown it. */
    private static final double SLIP_SEEN_FROM = 160.0;
    /** Parts slip only this near someone, where they can be seen to. */
    private static final double SLIP_NEAR = 48.0;
    private static final int BUILT_FLAGS = Block.UPDATE_ALL;
    private static final int QUIET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Clearing touches nothing around it: no neighbors told, nothing dropped, no chest spilling its contents. */
    private static final int CLEAR_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /** How far the town will cut a hill down, or fill a hollow in, to level a part. */
    private static final int MAX_CUT = 8;
    private static final int MAX_FILL = 6;
    /** Water deeper than this is a lake, a river or the sea, and the town leaves it be. */
    private static final int SHALLOW = 2;
    /** How far down through what stands on the land the town looks for the ground. */
    private static final int SCAN_DEPTH = 96;
    /** Fewest and most ticks a part takes to dissolve what stood on it before it builds. */
    private static final int MIN_CLEAR_TICKS = 8;
    private static final int MAX_CLEAR_TICKS = 30;
    /** How far from a felled trunk the leaves of its tree are held fast. */
    private static final int LEAF_REACH = 7;
    private static final int MAX_LEAVES_HELD = 600;
    /** How long the wall must stand still before the town builds out to it where it cuts through the plan. */
    private static final int SETTLE_TICKS = 40;
    /** No clipping: the part is built whole. */
    private static final float WHOLE = Float.MAX_VALUE;
    /** Ticks the wall as players see it trails where it truly is, while it is resized. */
    private static final int SEEN_LAG = Hexes.GLIDE_TICKS;
    /** How far in from the wall a sweep taking the town back looks ahead, and how often it looks again for new blocks. */
    private static final float CUT_WINDOW = 24.0F;
    private static final int CUT_REFRESH = 10;
    /** How long after the wall stops moving in its sweep is let go. */
    private static final int CUT_LINGER = 40;
    /** Most blocks put back each tick as the wall moves in, enough to keep up with it at its fastest. */
    private static final int CUT_BUDGET = 4096;
    /** Ticks a house the wall cuts into takes to dissolve, from the wall inward. */
    private static final int TAKEDOWN_TICKS = 14;
    /** Ticks what stands in the way takes to dissolve, from the top down, as the town makes way for a home raised anew. */
    private static final int MAKE_WAY_TICKS = 24;
    /** Most places clients are told about a tick, where bits of the town went back. */
    private static final int MAX_GONE_CELLS = 96;
    private static final long NOT_GONE = Long.MIN_VALUE;
    /** Blocks back from the caster the plan is laid from, putting them in the middle of their home's living room. */
    private static final int HOME_SHIFT = TownPlan.LIVING_ROOM + 1;
    /** Marks a piece that makes over a block already standing, rather than putting one down. */
    private static final int MAKEOVER = -2;
    /** When, once the land is clear, buildings that stay begin taking on the era, and how long it takes them. */
    private static final int MAKEOVER_FROM = 8;
    private static final int MAKEOVER_TICKS = 30;
    /** The same for what a home is made of, made over slowly while the rest of it goes up around it. */
    private static final int HOME_MAKEOVER_FROM = 40;
    private static final int HOME_MAKEOVER_TICKS = 160;
    /** How long before each block of the caster's home lands that players are told of it, for the magic to reach it. */
    private static final int FORM_LEAD = 6;

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
            }),
            INTS.optionalFieldOf("partial", new int[0]).forGetter(town -> town.partial.keySet().toIntArray()),
            INTS.optionalFieldOf("partial_clips", new int[0]).forGetter(town -> {
                int[] ids = town.partial.keySet().toIntArray();
                return Arrays.stream(ids).map(id -> Float.floatToIntBits(town.partial.get(id))).toArray();
            }),
            Kept.CODEC.listOf().optionalFieldOf("kept", List.of()).forGetter(town -> List.copyOf(town.kept)),
            INTS.optionalFieldOf("adopted", new int[0]).forGetter(town -> town.adopted.toIntArray()),
            // towns from before homes were raised around their casters were laid out from where they stood
            Codec.INT.optionalFieldOf("home_shift", 0).forGetter(town -> town.homeShift),
            Recall.CODEC.forGetter(town -> new Recall(Optional.ofNullable(town.origin), Optional.ofNullable(town.home), town.reach,
                    town.remembered, Optional.ofNullable(town.homeLot)))
    ).apply(i, HexTown::load));

    /**
     * Where a town's grid is laid out from, where its home rose, how far out its wall has stood, the towns it gives way
     * to and where its caster raised their home off its grid, saved with the rest; towns from before these were kept
     * are laid out from their Hex's middle.
     */
    private record Recall(Optional<BlockPos> origin, Optional<Vec3> home, float reach, List<TownMemory> remembered,
                          Optional<TownPlan.HomeLot> homeLot) {

        static final MapCodec<Recall> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BlockPos.CODEC.optionalFieldOf("origin").forGetter(Recall::origin),
                Vec3.CODEC.optionalFieldOf("home").forGetter(Recall::home),
                Codec.FLOAT.optionalFieldOf("reach", 0.0F).forGetter(Recall::reach),
                TownMemory.CODEC.listOf().optionalFieldOf("remembered", List.of()).forGetter(Recall::remembered),
                TownPlan.HomeLot.CODEC.optionalFieldOf("home_lot").forGetter(Recall::homeLot)
        ).apply(i, Recall::new));
    }

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
    /** What each lot is for, worked out from the plan the first time it is needed. */
    private @Nullable Int2ObjectOpenHashMap<Use> zoning;
    /** What each shop on main street sells: a {@link Signs} kind. */
    private final Int2IntOpenHashMap shopKinds = new Int2IntOpenHashMap();
    private @Nullable BlockPos planCenter;
    private final IntOpenHashSet skipped = new IntOpenHashSet();
    private final Int2ObjectLinkedOpenHashMap<Build> builds = new Int2ObjectLinkedOpenHashMap<>();
    /** Blocks to put back, each with the tick it goes at. */
    private final ArrayDeque<long[]> takedown = new ArrayDeque<>();
    /** Where the wall stood the last few ticks, a ring by game time. */
    private final float[] recent = new float[SEEN_LAG + 1];
    private int recentTicks;
    private float lastSeen = -1.0F;
    private long lastClosing;
    /** The town's blocks from the outside in, while the wall is moving in over them. */
    private @Nullable Sweep cut;
    private float cutFloor;
    private int cutVersion;
    private long cutAt;
    /** Counts every block the town puts down, so a sweep knows when it has missed some. */
    private int version;
    /** Where bits of the town went back this tick, by two-by-two cell, with the lowest and highest y that went. */
    private final Long2LongOpenHashMap gone = new Long2LongOpenHashMap();

    {
        gone.defaultReturnValue(NOT_GONE);
    }
    private @Nullable Sweep restyle;
    private @Nullable Sweep collapse;
    private boolean changed;
    /** Parts slipping now, until when. */
    private final Int2LongOpenHashMap slipping = new Int2LongOpenHashMap();
    private @Nullable Int2ObjectOpenHashMap<Part> partsById;
    private @Nullable TownPlan partsOf;
    /** Parts the wall cuts through, built only as far as the wall reached when they were begun. */
    private final Int2FloatOpenHashMap partial = new Int2FloatOpenHashMap();
    /** Paintings, item frames, armor stands, carts and the like that stood where the town now does. */
    private final List<Kept> kept = new ArrayList<>();
    /** Lots where a building already stood, made over rather than replaced. */
    private final IntOpenHashSet adopted = new IntOpenHashSet();
    private float lastRadius = -1.0F;
    private long radiusChangedAt;
    /**
     * How far back from the caster the town is laid out, so that their home rises around them where they stand: right
     * in the middle of its living room.
     */
    private final int homeShift;
    /** Where the town's grid is laid out from; null for towns from before it was kept, laid out from their Hex's middle. */
    private final @Nullable BlockPos origin;
    /** Where its caster's home rose around them, when the town was first raised or since, wherever this Hex was cast. */
    private @Nullable Vec3 home;
    /** Where its caster raised their home off the town's grid, if they have. */
    private TownPlan.@Nullable HomeLot homeLot;
    /** How far out its wall has stood, at its furthest: the ground the town is remembered for once it falls. */
    private float reach;
    /** The towns its caster raised before with the same build, which come back where they stood. */
    private final List<TownMemory> remembered;
    /** The caster, while they float in the middle of their rising home. */
    private @Nullable UUID aloft;
    /** Whether the home is being raised around its caster as their Hex is cast, rather than anywhere else. */
    private boolean founding;
    /** Whether the town is making way for a home raised somewhere new, which goes up once all of that is down. */
    private boolean makingWay;
    /**
     * Whether what is left of parts the plan no longer has has been looked for since the town was loaded: a home's old
     * lot, or what stood where it went, saved before it was all down.
     */
    private boolean swept;
    /** Whether the falling wall has reached the caster's home and let it go on its own. */
    private boolean homeLeft;
    /** How far out the outermost block of the caster's home stands, once worked out as the Hex falls. */
    private float homeReach = -1.0F;

    private HexTown(HexBuild mode, Direction forward, long seed, Era era, int homeShift, @Nullable BlockPos origin, @Nullable Vec3 home, float reach,
                    List<TownMemory> remembered, TownPlan.@Nullable HomeLot homeLot) {
        this.mode = mode;
        this.forward = forward;
        this.seed = seed;
        this.builtEra = era;
        this.homeShift = homeShift;
        this.origin = origin;
        this.home = home;
        this.reach = reach;
        this.remembered = List.copyOf(remembered);
        this.homeLot = homeLot;
    }

    /**
     * A new town, its home rising around its caster where they stand and its main street at their back.
     *
     * @param remembered the towns the caster raised before with the same build, oldest first, which come back where they
     *                   stood
     */
    public static HexTown raise(HexBuild mode, Vec3 center, Direction forward, long seed, List<TownMemory> remembered, Era era) {
        BlockPos origin = BlockPos.containing(center).relative(forward, -HOME_SHIFT);
        return new HexTown(mode, forward, seed, era, HOME_SHIFT, origin, center, 0.0F, remembered, null);
    }

    /**
     * A town raised again just as it was, over ground it stood on before, its home wherever its caster last raised it.
     */
    public static HexTown recall(TownMemory town, List<TownMemory> remembered, Era era) {
        return new HexTown(town.mode(), town.forward(), town.seed(), era, HOME_SHIFT, town.origin(), town.home(), 0.0F, remembered,
                town.homeLot().orElse(null));
    }

    private static HexTown load(HexBuild mode, Direction forward, long seed, Era era, Saved blocks, int[] finished, int[] started,
                                long[] startedAt, int[] leveled, int[] levels, int[] partial, int[] partialClips, List<Kept> kept,
                                int[] adopted, int homeShift, Recall recall) {
        HexTown town = new HexTown(mode, forward.getAxis().isHorizontal() ? forward : Direction.NORTH, seed, era, homeShift,
                recall.origin().orElse(null), recall.home().orElse(null), recall.reach(), recall.remembered(), recall.homeLot().orElse(null));
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
        for (int k = 0; k < Math.min(partial.length, partialClips.length); k++) {
            town.partial.put(partial[k], Float.intBitsToFloat(partialClips[k]));
        }
        town.kept.addAll(kept);
        for (int id : adopted) {
            town.adopted.add(id);
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
            TownPlan.Grid own = new TownPlan.Grid(origin != null ? origin : center.relative(forward, -homeShift), forward, seed);
            plan = TownPlan.around(own, homeLot, remembered, Vec3.atBottomCenterOf(center), Hexes.MAX_RADIUS);
            planCenter = center;
        }
        return plan;
    }

    /**
     * The seed a part of the plan is drawn from: its own town's.
     */
    private long seedOf(Part part) {
        return plan != null ? plan.grid(part.layer()).seed() : seed;
    }

    /**
     * What is remembered of the town once its Hex falls, for the next Hex its caster casts over the same ground with the
     * same build: nothing if its wall never stood out far enough to raise any of it.
     */
    public @Nullable TownMemory memory(UUID caster, Vec3 hexCenter) {
        if (mode == HexBuild.NOTHING || reach < Hexes.MIN_RADIUS) {
            return null;
        }
        TownPlan.Grid own = plan(BlockPos.containing(hexCenter)).grid(0);
        return new TownMemory(caster, mode, own.origin(), own.forward(), own.seed(), home != null ? home : hexCenter, hexCenter, reach,
                Optional.ofNullable(homeLot));
    }

    // ---------------------------------------------------------------- the home

    /**
     * The caster's home in the plan: their own town's, not one of those it brings back.
     */
    public @Nullable Part home(BlockPos center) {
        for (Part part : plan(center).parts()) {
            if (part.kind() == Kind.HOME && part.layer() == 0) {
                return part;
            }
        }
        return null;
    }

    /**
     * Begins building the caster's home, if its lot is open land.
     *
     * @param delay ticks before it begins, for its caster to be carried over to it
     * @return how many ticks until it stands, or -1 if there is no room for it
     */
    public int foundHome(ServerLevel level, Vec3 hexCenter, Era era, long now, int delay) {
        BlockPos center = BlockPos.containing(hexCenter);
        Part home = home(center);
        if (mode == HexBuild.NOTHING || home == null || !isLoaded(level, home)) {
            return -1;
        }
        Survey survey = survey(level, home, hexCenter, WHOLE);
        if (survey == null) {
            return -1;
        }
        founding = true;
        Build build = start(level, hexCenter, home, survey, WHOLE, now + delay);
        founding = false;
        return delay + build.duration();
    }

    /**
     * Where a caster founding their home on open land stands, whatever they stood on to cast: on the land its lot is
     * leveled to, a pillar under them cut down to it and a hollow filled in. Its floor is laid at that height, and the
     * founding floats the caster up from there and lands them on top of it.
     *
     * @return empty if there is no room for their home
     */
    public OptionalInt foundingHeight(ServerLevel level, Vec3 hexCenter) {
        Part home = home(BlockPos.containing(hexCenter));
        if (mode == HexBuild.NOTHING || home == null || !isLoaded(level, home)) {
            return OptionalInt.empty();
        }
        Survey survey = survey(level, home, hexCenter, WHOLE);
        return survey == null ? OptionalInt.empty() : OptionalInt.of(survey.ground() + 1);
    }

    /**
     * Raises the caster's home again on a lot of their choosing inside their Hex: what stands of it now dissolves, and
     * so does whatever else of the town stands where it goes, then it goes up there just as it did when the Hex was
     * cast, and the lot it stood on takes a house like any other.
     *
     * @return whether it could go up there: not while it is still going up, nor on land it can't stand on
     */
    public HomeRaise raiseHome(ServerLevel level, Vec3 hexCenter, TownPlan.HomeLot lot) {
        BlockPos center = BlockPos.containing(hexCenter);
        Part current = home(center);
        if (makingWay || current != null && (builds.containsKey(current.id()) || started.containsKey(current.id()))) {
            return HomeRaise.BUSY;
        }
        TownPlan before = plan(center);
        Part home = TownPlan.home(before.grid(0), 0, lot);
        if (current != null && current.equals(home)) {
            return HomeRaise.ALREADY;
        }
        if (!isLoaded(level, home)) {
            return HomeRaise.NO_ROOM;
        }
        Survey survey = survey(level, home, hexCenter, WHOLE);
        if (survey == null) {
            return HomeRaise.NO_ROOM;
        }
        homeLot = lot;
        this.home = lot.livingRoom(survey.ground() + 1);
        plan = null;
        partsById = null;
        TownPlan after = plan(center);
        Int2ObjectOpenHashMap<Part> staying = new Int2ObjectOpenHashMap<>();
        for (Part part : after.parts()) {
            staying.put(part.id(), part);
        }
        IntOpenHashSet going = new IntOpenHashSet();
        for (Part part : before.parts()) {
            int id = part.id();
            boolean standing = finished.contains(id) || builds.containsKey(id) || started.containsKey(id);
            if (standing && !part.equals(staying.get(id))) {
                going.add(id);
            }
        }
        makeWay(going, level.getGameTime());
        skipped.clear();
        makingWay = true;
        changed = true;
        return HomeRaise.RAISED;
    }

    /**
     * Takes parts of the town back down, each of them dissolving from the top down, all at once.
     */
    private void makeWay(IntOpenHashSet ids, long now) {
        if (ids.isEmpty()) {
            return;
        }
        LongArrayList blocks = new LongArrayList();
        for (Long2ObjectMap.Entry<Built> entry : built.long2ObjectEntrySet()) {
            if (ids.contains(entry.getValue().part())) {
                blocks.add(entry.getLongKey());
            }
        }
        for (int id : ids) {
            finished.remove(id);
            builds.remove(id);
            started.remove(id);
            levels.remove(id);
            partial.remove(id);
            adopted.remove(id);
        }
        long[] keys = blocks.toLongArray();
        Long[] order = Arrays.stream(keys).boxed().toArray(Long[]::new);
        Arrays.sort(order, (a, b) -> Integer.compare(BlockPos.getY(b), BlockPos.getY(a)));
        // after whatever was already on its way down, so the queue stays in order
        long from = takedown.isEmpty() ? now : Math.max(now, takedown.peekLast()[1]);
        for (int k = 0; k < order.length; k++) {
            takedown.add(new long[] {order[k], from + (long) MAKE_WAY_TICKS * k / Math.max(1, order.length)});
        }
    }

    /**
     * How raising a home somewhere new went.
     */
    public enum HomeRaise {
        RAISED, ALREADY, BUSY, NO_ROOM
    }

    public boolean isHomeFinished(BlockPos center) {
        Part home = home(center);
        return home == null || finished.contains(home.id()) || !started.containsKey(home.id()) && !builds.containsKey(home.id());
    }

    // ---------------------------------------------------------------- the tick

    /**
     * @param wall     how far the Hex's wall has spread, or is falling inward
     * @param radius   the Hex's size now, which what is built must fit inside
     * @param eraFront how far out from the middle its era has spread since it last changed
     * @param aloft    the caster, while they float in the middle of their rising home
     */
    public void tick(ServerLevel level, Vec3 hexCenter, float wall, float radius, Era era, float eraFront, boolean falling, @Nullable UUID aloft,
                     long now) {
        BlockPos center = BlockPos.containing(hexCenter);
        this.aloft = aloft;
        float seen = seen(wall, now);
        if (falling) {
            fall(level, hexCenter, wall);
            sendGone(level, hexCenter, wall);
            return;
        }
        if (seen > reach) {
            reach = seen;
            changed = true;
        }
        if (!swept) {
            swept = true;
            IntOpenHashSet orphaned = new IntOpenHashSet();
            for (Built block : built.values()) {
                if (!orphaned.contains(block.part()) && part(center, block.part()) == null) {
                    orphaned.add(block.part());
                }
            }
            if (!orphaned.isEmpty()) {
                makeWay(orphaned, now);
                makingWay = true;
            }
        }
        if (radius != lastRadius) {
            lastRadius = radius;
            radiusChangedAt = now;
        }
        int budget = BUDGET;
        budget = takeDown(level, budget, now);
        cutBack(level, hexCenter, seen, now);
        if (now % TRIGGER_INTERVAL == 0) {
            // while the wall spreads or grows, what is built needs only fit the Hex it is growing into
            shrinkTo(level, center, hexCenter, Math.max(seen, radius), now);
            trigger(level, center, hexCenter, seen, radius, now);
        }
        if (now % RETRY_SKIPPED == 0) {
            skipped.clear();
        }
        budget = advance(level, center, hexCenter, radius <= seen + 0.01F ? seen : -1.0F, era, now, budget);
        restyle(level, hexCenter, era, eraFront, now, budget);
        sendGone(level, hexCenter, seen);
    }

    /**
     * Where the wall stands as players see it: a moment behind where it truly is while it is being resized, as word
     * of each change takes a moment to reach them and their wall glides over to it.
     */
    private float seen(float wall, long now) {
        recent[(int) Math.floorMod(now, (long) recent.length)] = wall;
        if (recentTicks < recent.length) {
            recentTicks++;
            return wall;
        }
        return recent[(int) Math.floorMod(now - SEEN_LAG, (long) recent.length)];
    }

    /**
     * As the wall moves in, whatever of the town it passes goes back to what stood there before, right as the wall goes
     * by: from the outside in, a block at a time, each glitching out in red as it goes.
     */
    private void cutBack(ServerLevel level, Vec3 hexCenter, float wall, long now) {
        boolean closing = wall < lastSeen - 1.0E-4F;
        lastSeen = wall;
        if (closing) {
            lastClosing = now;
        }
        if (cut == null && !closing || built.isEmpty()) {
            return;
        }
        if (!closing && now - lastClosing > CUT_LINGER) {
            cut = null;
            return;
        }
        if (cut == null || wall < cutFloor + 1.0F || cutVersion != version && now - cutAt >= CUT_REFRESH) {
            cutFloor = Math.max(0.0F, wall - CUT_WINDOW);
            cut = Sweep.outsideIn(built, hexCenter, cutFloor);
            cutVersion = version;
            cutAt = now;
        }
        int budget = CUT_BUDGET;
        boolean any = false;
        while (cut.cursor < cut.order.length && budget > 0) {
            int at = cut.cursor;
            if (cut.reach[at] <= wall) {
                break;
            }
            cut.cursor++;
            long key = cut.order[at];
            Built entry = built.get(key);
            if (entry != null && restore(level, key, entry)) {
                budget--;
                gone(key);
                any = true;
            }
        }
        if (any && !kept.isEmpty()) {
            putBackKept(level, true, hexCenter, wall + 0.5F, null);
        }
    }

    /**
     * Marks where a bit of the town just went back, for clients to show it glitching out.
     */
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
     * Tells players nearby where bits of the town went this tick: every one of them if there are few, an even spread of
     * them if there are many.
     */
    private void sendGone(ServerLevel level, Vec3 hexCenter, float wall) {
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
        TownEvents.gone(level, hexCenter, wall, taken == keep ? cells : Arrays.copyOf(cells, taken * 4));
    }

    /**
     * Starts building whatever now fits inside the wall, nearest first. Once the wall has stood still a moment, the
     * parts it cuts through are built as far as it reaches too, and any built that way before it moved out are built
     * out to it.
     */
    private void trigger(ServerLevel level, BlockPos center, Vec3 hexCenter, float wall, float radius, long now) {
        if (mode == HexBuild.NOTHING || wall <= 0.0F) {
            return;
        }
        if (makingWay) {
            // a home raised somewhere new goes up once what stood in its way is all down
            if (!takedown.isEmpty()) {
                return;
            }
            makingWay = false;
        }
        boolean settled = mode.isTown() && wall >= radius - 0.01F && now - radiusChangedAt >= SETTLE_TICKS;
        int starts = 0;
        for (Part part : plan(center).parts()) {
            int id = part.id();
            if (finished.contains(id) || started.containsKey(id) || builds.containsKey(id)) {
                if (finished.contains(id) && partial.containsKey(id) && !adopted.contains(id) && wall > partial.get(id) + 1.0F
                        && (settled || TownPlan.fits(part, hexCenter, wall, center.getY())) && isLoaded(level, part)) {
                    // the wall has moved out past a garden or a street's end: build it out to where the wall is now, as
                    // soon as the wall has passed all of it, or wherever the wall comes to rest
                    float clip = TownPlan.fits(part, hexCenter, wall, center.getY()) ? WHOLE : wall;
                    Survey survey = survey(level, part, hexCenter, clip);
                    if (survey != null) {
                        finished.remove(id);
                        partial.remove(id);
                        start(level, hexCenter, part, survey, clip, now);
                        if (++starts >= STARTS_PER_TRIGGER) {
                            return;
                        }
                    }
                }
                continue;
            }
            if (skipped.contains(id) || mode == HexBuild.HOME && part.kind() != Kind.HOME) {
                continue;
            }
            float clip = WHOLE;
            if (!TownPlan.fits(part, hexCenter, wall, center.getY())) {
                if (!settled || !TownPlan.touches(part, hexCenter, wall, center.getY())) {
                    continue;
                }
                clip = wall;
            }
            if (!isLoaded(level, part)) {
                continue;
            }
            Survey survey = survey(level, part, hexCenter, clip);
            if (survey == null) {
                skipped.add(id);
                continue;
            }
            start(level, hexCenter, part, survey, clip, now);
            if (++starts >= STARTS_PER_TRIGGER) {
                return;
            }
        }
    }

    /**
     * Begins a part: first whatever stands on its land dissolves, from the top down, then it builds itself.
     *
     * @param clip how far out the wall reached, past which nothing of it is built; {@link #WHOLE} for all of it
     */
    private Build start(ServerLevel level, Vec3 hexCenter, Part part, Survey survey, float clip, long now) {
        Frame frame = new Frame(part.origin(), part.front());
        RandomSource random = RandomSource.create(seedOf(part) ^ (part.lookId() * 0x9E3779B97F4A7C15L) ^ 0x5EED);
        // what already stands here and is made over rather than cleared: any building on a lot, and the part of one
        // that a street or the square has to go around; in an orchard, the trees on a plot too, turned to fruit trees.
        // The caster's home is made of whatever house, or ruin of one, stands on its lot, finished around it.
        java.util.function.LongPredicate inFootprint = column -> survey.index(part, BlockPos.getX(column), BlockPos.getZ(column)) >= 0
                && within(hexCenter, clip, BlockPos.of(column));
        boolean homeLot = part.kind() == Kind.HOME;
        LongOpenHashSet buildings = homeLot
                ? Restyle.home(level, part.minX(), part.minZ(), part.maxX(), part.maxZ(), survey.ground(), inFootprint, built::containsKey)
                : Restyle.buildings(level, part.minX(), part.minZ(), part.maxX(), part.maxZ(), survey.ground(), inFootprint, built::containsKey);
        LongOpenHashSet trees = mode == HexBuild.ORCHARD && part.kind() == Kind.HOUSE
                ? Restyle.trees(level, part.minX(), part.minZ(), part.maxX(), part.maxZ(), survey.ground(), inFootprint, built::containsKey)
                : new LongOpenHashSet();
        LongOpenHashSet standing = new LongOpenHashSet(buildings);
        standing.addAll(trees);
        boolean[] occupied = new boolean[survey.columns().length];
        for (long key : standing) {
            int index = survey.index(part, BlockPos.getX(key), BlockPos.getZ(key));
            if (index >= 0) {
                occupied[index] = true;
            }
        }
        boolean adopted = (part.kind() == Kind.HOUSE || homeLot) && !buildings.isEmpty();
        if (!adopted && trees.isEmpty()) {
            for (int index = 0; index < occupied.length; index++) {
                survey.blocked()[index] |= occupied[index];
            }
        }
        int paint = random.nextInt(EraStyle.PAINTS);
        // what a building that stays was, it becomes again in the town: the library, the church, a shop, a barn
        Restyle.Identity identity = adopted && !homeLot ? Restyle.identify(level, buildings, part.front(), mode == HexBuild.ORCHARD) : null;
        Houses.Shell shell = adopted && homeLot ? Restyle.shell(level, buildings, frame, part.width(), part.depth()) : null;
        if (shell != null && shell.door() == Houses.NO_DOOR) {
            // a home needs a way in: a front door goes in its front, whatever stands there now
            int door = shell.doorAt();
            for (int y = shell.floor() + 1; y <= shell.floor() + 2; y++) {
                long key = frame.at(door, shell.front(), y).asLong();
                buildings.remove(key);
                standing.remove(key);
            }
        }
        Blueprint blueprint = shell != null ? Houses.finished(frame, survey.ground(), survey::height, occupied, shell, paint, random)
                : adopted ? Houses.around(frame, survey.ground(), survey::height, occupied)
                : blueprint(part, survey, clip != WHOLE, occupied);
        List<Piece> pieces = blueprint.schedule();
        // the caster floats in the middle of their home as it rises around them; nothing goes where they are but the rug
        // they come down onto, which lies lower than they float
        BlockPos caster = BlockPos.containing(hexCenter);
        int floor = caster.getY() + 1;
        boolean home = part.kind() == Kind.HOME && homeShift > 0 && founding;
        pieces.removeIf(piece -> survey.isBlocked(part, piece.pos()) || !within(hexCenter, clip, piece.pos())
                || standing.contains(piece.pos().asLong())
                || home && piece.pos().getX() == caster.getX() && piece.pos().getZ() == caster.getZ() && piece.pos().getY() >= floor
                && piece.pos().getY() <= floor + 2 && piece.role() != Role.CLEAR && piece.role() != Role.RUG);
        // whatever of this part stood here before and isn't in what it will be now gives way too
        LongOpenHashSet planned = new LongOpenHashSet();
        for (Piece piece : pieces) {
            planned.add(piece.pos().asLong());
        }
        List<Piece> clearing = new ArrayList<>();
        for (Long2ObjectMap.Entry<Built> entry : built.long2ObjectEntrySet()) {
            Built block = entry.getValue();
            if (block.part() == part.id() && block.role() != Role.CLEAR && block.role() != Role.PRESERVE && !planned.contains(entry.getLongKey())) {
                clearing.add(new Piece(BlockPos.of(entry.getLongKey()), Role.CLEAR, 0, AIR, 0, 0));
            }
        }
        int top = survey.ground();
        for (int b = 0; b < part.depth(); b++) {
            for (int a = 0; a < part.width(); a++) {
                int index = b * part.width() + a;
                Terrain.Column column = survey.columns()[index];
                BlockPos at = new Frame(part.origin(), part.front()).at(a, b, 0);
                if (column == null || survey.isBlocked(index) || !within(hexCenter, clip, at)) {
                    continue;
                }
                BlockPos.MutableBlockPos pos = at.mutable();
                for (int y = column.top(); y > Math.min(column.surface(), survey.ground()); y--) {
                    pos.setY(y);
                    long key = pos.asLong();
                    BlockState state = level.getBlockState(pos);
                    // beside a building that stays, its garden's water stays too
                    if (state.isAir() || built.containsKey(key) || standing.contains(key) || occupied[index] && !state.getFluidState().isEmpty()) {
                        continue;
                    }
                    clearing.add(new Piece(pos.immutable(), Role.CLEAR, 0, AIR, 0, 0));
                    top = Math.max(top, y);
                }
            }
        }
        // from the top down, so nothing is ever left hanging
        clearing.sort((x, y) -> Integer.compare(y.pos().getY(), x.pos().getY()));
        int clearTicks = clearing.isEmpty() ? 0 : Math.clamp(MIN_CLEAR_TICKS + clearing.size() / 40, MIN_CLEAR_TICKS, MAX_CLEAR_TICKS);
        List<Piece> timed = new ArrayList<>(clearing.size() + pieces.size() + standing.size());
        for (int k = 0; k < clearing.size(); k++) {
            timed.add(clearing.get(k).at(clearTicks * k / clearing.size()));
        }
        for (Piece piece : pieces) {
            timed.add(piece.at(piece.tick() + clearTicks));
        }
        // the buildings that stay take on the era, from the ground up, as the red magic climbs over them; a home made
        // of one takes its time, all the while it is finished around it
        int makeoverFrom = homeLot ? HOME_MAKEOVER_FROM : MAKEOVER_FROM;
        int makeoverTicks = homeLot ? HOME_MAKEOVER_TICKS : MAKEOVER_TICKS;
        List<Piece> restyled = new ArrayList<>();
        for (long key : standing) {
            BlockPos pos = BlockPos.of(key);
            if (!within(hexCenter, clip, pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            Role role;
            if (trees.contains(key)) {
                role = state.is(BlockTags.LOGS) ? Role.TREE_LOG : random.nextFloat() < 0.35F ? Role.ORCHARD_BLOSSOM : Role.ORCHARD_LEAVES;
            } else {
                // a home is read from its own floor, inside which what lies level with it is floor, not foundation
                role = Restyle.roleOf(level, pos, state, shell != null ? shell.floor() - 1 : survey.ground());
                if (shell != null && role == Role.FOUNDATION && pos.getY() == shell.floor()) {
                    int index = survey.index(part, pos.getX(), pos.getZ());
                    int a = index % part.width();
                    int b = index / part.width();
                    if (index >= 0 && a > shell.left() && a < shell.right() && b > shell.front() && b < shell.back()) {
                        role = Role.FLOOR;
                    }
                }
                if (role == Role.WALL && identity != null) {
                    role = identity.walls();
                }
            }
            if (role != null) {
                restyled.add(new Piece(pos, role, role == Role.FLOWER ? random.nextInt(EraStyle.FLOWERS) : paint, state, MAKEOVER, 0));
            }
        }
        restyled.sort((x, y) -> Integer.compare(x.pos().getY(), y.pos().getY()));
        for (int k = 0; k < restyled.size(); k++) {
            timed.add(restyled.get(k).at(clearTicks + makeoverFrom + makeoverTicks * k / restyled.size()));
        }
        int dressed = identity != null ? dress(level, identity, clearTicks + makeoverFrom + makeoverTicks + 2, timed) : 0;
        int duration = Math.max(Math.max(blueprint.duration(), restyled.isEmpty() ? 0 : makeoverFrom + makeoverTicks + 1), dressed - clearTicks)
                + clearTicks;
        timed.sort((x, y) -> Integer.compare(x.tick(), y.tick()));
        keep(level, part, survey, hexCenter, clip, top, occupied);
        Build build = new Build(part, survey.ground(), timed, now, duration);
        build.cars.addAll(blueprint.cars());
        if (adopted) {
            this.adopted.add(part.id());
        }
        builds.put(part.id(), build);
        started.put(part.id(), now);
        levels.put(part.id(), survey.ground());
        if (clip != WHOLE) {
            partial.put(part.id(), clip);
        } else {
            partial.remove(part.id());
        }
        changed = true;
        TownEvents.started(level, part, survey.ground(), now, duration, top, clearTicks);
        return build;
    }

    /**
     * Gives a building that stays the look of what it has become: its sign over the door, and a shop's awning.
     *
     * @return the tick it is done by
     */
    private static int dress(ServerLevel level, Restyle.Identity identity, int at, List<Piece> timed) {
        BlockPos door = identity.door();
        Direction out = identity.out();
        if (door == null || out == null) {
            return 0;
        }
        // an awning over the door, if the wall rises high enough for the sign to go above it
        boolean high = level.getBlockState(door.above(3)).isSolid();
        boolean awning = identity.awning() && high;
        if (awning) {
            Direction along = out.getClockWise();
            for (int d = -1; d <= 1; d++) {
                BlockPos spot = door.above(2).relative(out).relative(along, d);
                if (level.getBlockState(spot).canBeReplaced() && level.getBlockState(spot.relative(out.getOpposite())).isSolid()) {
                    timed.add(new Piece(spot, Role.AWNING, 0, Houses.stairs(out.getOpposite(), Half.BOTTOM), Houses.EXTERIOR, at));
                }
            }
        }
        if (identity.sign() >= 0) {
            BlockPos spot = (awning ? door.above(3) : door.above(2)).relative(out);
            if (level.getBlockState(spot).canBeReplaced() && level.getBlockState(spot.relative(out.getOpposite())).isSolid()) {
                timed.add(new Piece(spot, Role.SIGN, identity.sign(), Houses.sign(out), Houses.EXTERIOR, at + 1));
            }
        }
        return at + 2;
    }

    private static boolean within(Vec3 hexCenter, float clip, BlockPos pos) {
        return clip == WHOLE || HexShape.contains(hexCenter, clip - 0.5F, new Vec3(pos.getX() + 0.5, hexCenter.y, pos.getZ() + 0.5));
    }

    /**
     * Picks up builds that were under way when the world was last saved.
     */
    private void resume(ServerLevel level, BlockPos center, Vec3 hexCenter) {
        if (started.isEmpty() || started.size() == builds.size()) {
            return;
        }
        for (int id : started.keySet().toIntArray()) {
            if (builds.containsKey(id)) {
                continue;
            }
            Part part = part(center, id);
            if (part == null || !isLoaded(level, part)) {
                continue;
            }
            if (adopted.contains(id)) {
                // what is left of making over a building can't be worked out again once it is under way
                started.remove(id);
                finished.add(id);
                changed = true;
                continue;
            }
            float clip = partial.getOrDefault(id, WHOLE);
            Survey survey = survey(level, part, hexCenter, clip);
            int ground = levels.getOrDefault(id, survey != null ? survey.ground() : center.getY());
            Survey leveled = survey != null ? survey.at(ground) : Survey.flat(ground, part);
            Blueprint blueprint = blueprint(part, leveled, clip != WHOLE, new boolean[leveled.columns().length]);
            List<Piece> pieces = blueprint.schedule();
            pieces.removeIf(piece -> leveled.isBlocked(part, piece.pos()) || !within(hexCenter, clip, piece.pos()));
            Build build = new Build(part, ground, pieces, started.get(id), blueprint.duration());
            build.cars.addAll(blueprint.cars());
            builds.put(id, build);
        }
    }

    /**
     * @param wall where the wall stands, past which nothing more is put down; less than 0 while it spreads or grows,
     *             as everything building then fit inside it when it began
     */
    private int advance(ServerLevel level, BlockPos center, Vec3 hexCenter, float wall, Era era, long now, int budget) {
        resume(level, center, hexCenter);
        Iterator<Build> iterator = builds.values().iterator();
        while (iterator.hasNext() && budget > 0) {
            Build build = iterator.next();
            long elapsed = now - build.start;
            if (build.part.kind() == Kind.HOME) {
                announce(level, build, elapsed, era);
            }
            while (build.next < build.pieces.size() && budget > 0) {
                Piece piece = build.pieces.get(build.next);
                if (piece.tick() > elapsed) {
                    break;
                }
                build.next++;
                if (wall >= 0.0F && !HexShape.contains(hexCenter, wall, Vec3.atCenterOf(piece.pos()))) {
                    // the wall has moved in past it since this began
                    continue;
                }
                if (place(level, build, piece, era)) {
                    budget--;
                    version++;
                }
            }
            if (build.next >= build.pieces.size()) {
                iterator.remove();
                started.remove(build.part.id());
                finished.add(build.part.id());
                if (!build.restyled.isEmpty()) {
                    reshape(level, build.restyled);
                }
                park(level, build.cars);
                changed = true;
                TownEvents.finished(level, build.part);
            }
        }
        return budget;
    }

    /**
     * The caster's home goes up a block at a time for everyone near to watch: each block is told of a moment before it
     * lands, for the magic to reach out to where it goes. The leveling of its lot is left out, and so is anything that
     * only clears a space, or that went down long ago, as it would be after picking the build up again on a reload.
     */
    private static void announce(ServerLevel level, Build build, long elapsed, Era era) {
        IntArrayList blocks = new IntArrayList();
        while (build.announced < build.pieces.size()) {
            Piece piece = build.pieces.get(build.announced);
            if (piece.tick() > elapsed + FORM_LEAD) {
                break;
            }
            build.announced++;
            if (piece.tick() < elapsed - 2 || piece.stage() == Houses.GROUND || piece.role() == Role.CLEAR
                    || EraStyle.state(piece.role(), era, piece.paint(), piece.template()).isAir()) {
                continue;
            }
            BlockPos pos = piece.pos();
            blocks.add(pos.getX());
            blocks.add(pos.getY());
            blocks.add(pos.getZ());
            blocks.add((int) Math.max(0L, piece.tick() - elapsed));
        }
        TownEvents.forming(level, build.part, build.ground, blocks.toIntArray());
    }

    /**
     * Parks a part's cars once it stands, unless one is parked there already, as when the town comes back.
     */
    private static void park(ServerLevel level, List<Blueprint.Car> cars) {
        for (Blueprint.Car car : cars) {
            AABB spot = new AABB(car.at().x - 1.5, car.at().y - 0.5, car.at().z - 1.5, car.at().x + 1.5, car.at().y + 2.0, car.at().z + 1.5);
            if (level.hasChunkAt(BlockPos.containing(car.at())) && level.getEntitiesOfClass(ParkedCar.class, spot).isEmpty()) {
                ParkedCar.park(level, car.at(), car.facing().toYRot(), car.paint(), true);
            }
        }
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
        if (piece.role() == Role.CLEAR) {
            return clear(level, build.part.id(), pos);
        }
        if (piece.stage() == MAKEOVER) {
            return makeOver(level, build, piece, era);
        }
        BlockState target = EraStyle.state(piece.role(), era, piece.paint(), piece.template());
        if (target.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return placeDouble(level, build, piece, target);
        }
        if (target.hasProperty(BlockStateProperties.BED_PART)) {
            return placeBed(level, build, piece, target);
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
        take(level, pos, current, piece, build.part.id());
        level.setBlock(pos, shaped, BUILT_FLAGS);
        if (piece.role() == Role.SIGN && level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            sign.setText(Signs.text(piece.paint()), SignTextSlot.FRONT);
            sign.setChanged();
            level.sendBlockUpdated(pos, shaped, shaped, Block.UPDATE_CLIENTS);
        }
        nudge(level, build, pos, shaped, aloft);
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
        take(level, lower, lowerNow, piece, build.part.id());
        take(level, upper, upperNow, new Piece(upper, piece.role(), piece.paint(), piece.template(), piece.stage(), piece.tick()), build.part.id());
        level.setBlock(lower, target, QUIET_FLAGS);
        level.setBlock(upper, target.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), QUIET_FLAGS);
        level.getBlockState(lower).updateNeighbourShapes(level, lower, BUILT_FLAGS);
        level.getBlockState(upper).updateNeighbourShapes(level, upper, BUILT_FLAGS);
        changed = true;
        return true;
    }

    /**
     * A bed is put down whole, its head with its foot, or the first half down would fall apart waiting for the other.
     */
    private boolean placeBed(ServerLevel level, Build build, Piece piece, BlockState target) {
        if (target.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
            // the foot put this down already
            return false;
        }
        BlockPos foot = piece.pos();
        BlockPos head = foot.relative(target.getValue(BlockStateProperties.HORIZONTAL_FACING));
        BlockState footNow = level.getBlockState(foot);
        BlockState headNow = level.getBlockState(head);
        boolean footOurs = built.containsKey(foot.asLong());
        boolean headOurs = built.containsKey(head.asLong());
        if (!footOurs && !Terrain.isReplaceable(footNow) || !headOurs && !Terrain.isReplaceable(headNow)) {
            return false;
        }
        take(level, foot, footNow, piece, build.part.id());
        take(level, head, headNow, new Piece(head, piece.role(), piece.paint(), piece.template(), piece.stage(), piece.tick()), build.part.id());
        level.setBlock(foot, target, QUIET_FLAGS);
        level.setBlock(head, target.setValue(BlockStateProperties.BED_PART, BedPart.HEAD), QUIET_FLAGS);
        level.getBlockState(foot).updateNeighbourShapes(level, foot, BUILT_FLAGS);
        level.getBlockState(head).updateNeighbourShapes(level, head, BUILT_FLAGS);
        changed = true;
        return true;
    }

    /**
     * Dissolves whatever stands somewhere the town is going, remembering it whole: the block and anything kept in it,
     * a chest's contents or a sign's words, to be put back when the Hex falls. Nothing drops and nothing around it is
     * disturbed. Only what can't be broken stays.
     *
     * @return whether anything changed in the world
     */
    private boolean clear(ServerLevel level, int part, BlockPos pos) {
        long key = pos.asLong();
        BlockState current = level.getBlockState(pos);
        if (current.isAir()) {
            return false;
        }
        Built existing = built.get(key);
        if (existing != null) {
            if (existing.role() == Role.CLEAR || existing.role() != Role.PRESERVE && !EraStyle.isStyleOf(existing.role(), existing.paint(), current)) {
                // a player has put something of their own here since; it stays
                return false;
            }
            // the town's own block, or leaves it was holding fast: cleared, still remembering what first stood here
            built.put(key, new Built(existing.original(), Role.CLEAR, 0, part, existing.data()));
            level.setBlock(pos, AIR, CLEAR_FLAGS);
            changed = true;
            return true;
        }
        if (Terrain.isUntouchable(level, pos, current)) {
            return false;
        }
        CompoundTag data = null;
        BlockEntity entity = current.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        if (entity != null) {
            data = entity.saveWithFullMetadata(level.registryAccess());
        }
        built.put(key, new Built(HexPaint.unpainted(level, pos, current), Role.CLEAR, 0, part, data));
        level.setBlock(pos, AIR, CLEAR_FLAGS);
        if (current.is(BlockTags.LOGS)) {
            holdLeaves(level, pos, part);
        }
        changed = true;
        return true;
    }

    /**
     * Makes over a block of a building that stays, in the era's look for the part it plays, keeping its shape: the
     * same facing, half and hinge. What it was is remembered, to come back when the Hex falls.
     *
     * @return whether anything changed in the world
     */
    private boolean makeOver(ServerLevel level, Build build, Piece piece, Era era) {
        BlockPos pos = piece.pos();
        long key = pos.asLong();
        BlockState current = level.getBlockState(pos);
        if (current != piece.template() || built.containsKey(key)) {
            // changed since, or already the town's
            return false;
        }
        BlockState target = EraStyle.state(piece.role(), era, piece.paint(), current);
        BlockState shaped = target.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) ? target : Block.updateFromNeighbourShapes(target, level, pos);
        if (shaped.isAir() || shaped == current) {
            return false;
        }
        built.put(key, new Built(HexPaint.unpainted(level, pos, current), piece.role(), piece.paint(), build.part.id()));
        level.setBlock(pos, shaped, QUIET_FLAGS);
        build.restyled.add(key);
        changed = true;
        return true;
    }

    /**
     * Works out again how the blocks of a made-over building join their neighbors, now that all of them have changed:
     * fences to fences, panes to walls.
     */
    private static void reshape(ServerLevel level, LongArrayList positions) {
        for (long key : positions.toLongArray()) {
            BlockPos pos = BlockPos.of(key);
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (isPaired(state)) {
                continue;
            }
            BlockState shaped = Block.updateFromNeighbourShapes(state, level, pos);
            if (shaped != state && !shaped.isAir()) {
                level.setBlock(pos, shaped, QUIET_FLAGS);
            }
        }
    }

    /**
     * Whether a block is half of a pair, a door or a bed, whose halves only make sense of each other together.
     */
    private static boolean isPaired(BlockState state) {
        return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || state.hasProperty(BlockStateProperties.BED_PART);
    }

    /**
     * Holds fast the leaves of a tree whose trunk the town has just cut through, so what is left of it doesn't wither
     * away while the Hex stands. They let go again as the Hex falls.
     */
    private void holdLeaves(ServerLevel level, BlockPos trunk, int part) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        LongOpenHashSet seen = new LongOpenHashSet();
        queue.add(trunk);
        seen.add(trunk.asLong());
        int held = 0;
        while (!queue.isEmpty() && held < MAX_LEAVES_HELD) {
            BlockPos at = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = at.relative(direction);
                if (next.distManhattan(trunk) > LEAF_REACH * 2 || Math.abs(next.getX() - trunk.getX()) > LEAF_REACH
                        || Math.abs(next.getZ() - trunk.getZ()) > LEAF_REACH || !seen.add(next.asLong()) || !level.hasChunkAt(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (!state.hasProperty(LeavesBlock.PERSISTENT) || state.getValue(LeavesBlock.PERSISTENT)) {
                    continue;
                }
                queue.add(next);
                if (!built.containsKey(next.asLong())) {
                    built.put(next.asLong(), new Built(state, Role.PRESERVE, 0, part));
                    level.setBlock(next, state.setValue(LeavesBlock.PERSISTENT, true), CLEAR_FLAGS);
                    held++;
                }
            }
        }
    }

    /**
     * Takes up the paintings, item frames, armor stands, display blocks and parked carts and boats that stood where a
     * part is going, to set back down exactly where they were when the Hex falls. People, animals and anything ridden
     * stay where they are.
     */
    private void keep(ServerLevel level, Part part, Survey survey, Vec3 hexCenter, float clip, int top, boolean[] occupied) {
        AABB box = new AABB(part.minX(), survey.ground() - 1, part.minZ(), part.maxX() + 1, top + 2, part.maxZ() + 1);
        for (Entity entity : level.getEntities((Entity) null, box, HexTown::isKept)) {
            BlockPos at = entity.blockPosition();
            if (!within(hexCenter, clip, at)) {
                continue;
            }
            // what hangs on or stands in a building that stays, stays with it
            BlockPos on = entity instanceof BlockAttachedEntity attached && attached instanceof net.minecraft.world.entity.decoration.HangingEntity hanging
                    ? at.relative(hanging.getDirection().getOpposite()) : at;
            int index = survey.index(part, on.getX(), on.getZ());
            int own = survey.index(part, at.getX(), at.getZ());
            if (index >= 0 && occupied[index] || own >= 0 && occupied[own]) {
                continue;
            }
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            if (entity.saveAsPassenger(output)) {
                kept.add(new Kept(output.buildResult(), part.id(), entity.position()));
                entity.discard();
                changed = true;
            }
        }
    }

    private static boolean isKept(Entity entity) {
        return (entity instanceof BlockAttachedEntity || entity instanceof ArmorStand || entity instanceof Display || entity instanceof VehicleEntity)
                && entity.isAlive() && !entity.isVehicle() && !entity.isPassenger();
    }

    /**
     * Sets back down what was taken up, for every part that no longer stands, now that the blocks it hung on are back.
     * What lies in chunks that aren't loaded is handed over to be set down when they are.
     *
     * @param all      every one of them, whether its part stands or not, as the whole town comes down
     * @param outside  only those out past this far from the middle, as the wall rushes in; 0 for any
     * @param unloaded what to do with those in chunks that aren't loaded; null to keep them until later
     */
    private void putBackKept(ServerLevel level, boolean all, Vec3 hexCenter, float outside, java.util.function.@Nullable Consumer<Kept> unloaded) {
        Iterator<Kept> iterator = kept.iterator();
        while (iterator.hasNext()) {
            Kept entry = iterator.next();
            int id = entry.part();
            boolean standing = finished.contains(id) || started.containsKey(id) || builds.containsKey(id);
            if (!all && standing || outside > 0.0F && HexShape.level(hexCenter, 1.0F, entry.at()) < outside) {
                continue;
            }
            boolean loaded = level.hasChunkAt(BlockPos.containing(entry.at()));
            if (!loaded && unloaded == null) {
                continue;
            }
            iterator.remove();
            changed = true;
            if (loaded) {
                setDown(level, entry);
            } else {
                unloaded.accept(entry);
            }
        }
    }

    /**
     * Sets something taken up back down where it was.
     */
    public static void setDown(ServerLevel level, Kept entry) {
        Entity entity = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), entry.data()), level,
                EntitySpawnReason.LOAD, EntityProcessor.NOP);
        if (entity != null && level.getEntity(entity.getUUID()) == null) {
            level.addFreshEntity(entity);
        }
    }

    /**
     * Remembers what stood somewhere before the town built there. A plant two blocks tall is remembered whole, since
     * taking either half takes both.
     */
    /**
     * Makes a spot a piece's, about to be put down there: remembering what stands there now if the town hasn't touched
     * it yet, or else taking on the piece's part and role, as the door its caster steps out of is found by them, and
     * still remembering what first stood there and anything kept in it.
     */
    private void take(ServerLevel level, BlockPos pos, BlockState now, Piece piece, int part) {
        Built existing = built.get(pos.asLong());
        if (existing == null) {
            record(level, pos, now, piece, part);
        } else if (existing.part() != part || existing.role() != piece.role() || existing.paint() != piece.paint()) {
            built.put(pos.asLong(), new Built(existing.original(), piece.role(), piece.paint(), part, existing.data()));
        }
    }

    private void record(ServerLevel level, BlockPos pos, BlockState original, Piece piece, int part) {
        built.put(pos.asLong(), new Built(HexPaint.unpainted(level, pos, original), piece.role(), piece.paint(), part));
        if (original.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            BlockPos other = original.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            long otherKey = other.asLong();
            if (!built.containsKey(otherKey)) {
                built.put(otherKey, new Built(level.getBlockState(other), Role.CLEAR, 0, part));
            }
        }
    }

    /**
     * Moves anyone caught inside a new block: up onto it near the ground, otherwise out onto the lawn in front. A caster
     * held aloft while their home rises is left be, as it builds itself around them.
     */
    private static void nudge(ServerLevel level, Build build, BlockPos pos, BlockState state, @Nullable UUID aloft) {
        if (state.getCollisionShape(level, pos).isEmpty()) {
            return;
        }
        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos));
        for (LivingEntity entity : caught) {
            if (entity.getUUID().equals(aloft) || !entity.getBoundingBox().intersects(new AABB(pos))) {
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
     * Gives up what of the town no longer fits inside the Hex, what lay past the wall having already gone back as the
     * wall passed. A house the wall cuts into goes altogether, dissolving from the wall inward, and its lot becomes a
     * garden once the wall settles; a street, the square or a garden just ends at the wall.
     */
    private void shrinkTo(ServerLevel level, BlockPos center, Vec3 hexCenter, float radius, long now) {
        if (finished.isEmpty() && builds.isEmpty()) {
            return;
        }
        for (Part part : plan(center).parts()) {
            int id = part.id();
            boolean standing = finished.contains(id) || builds.containsKey(id) || started.containsKey(id);
            if (!standing) {
                continue;
            }
            boolean cutThrough = partial.containsKey(id);
            boolean stillFits = cutThrough ? radius >= partial.get(id) - 0.01F
                    : TownPlan.fits(part, hexCenter, radius, levels.getOrDefault(id, center.getY()));
            if (stillFits) {
                continue;
            }
            boolean house = part.kind() == Kind.HOME || part.kind() == Kind.HOUSE && !cutThrough || adopted.contains(id);
            if (!house) {
                partial.put(id, radius);
                changed = true;
                continue;
            }
            finished.remove(id);
            builds.remove(id);
            started.remove(id);
            levels.remove(id);
            partial.remove(id);
            adopted.remove(id);
            LongArrayList blocks = new LongArrayList();
            for (Long2ObjectMap.Entry<Built> entry : built.long2ObjectEntrySet()) {
                if (entry.getValue().part() == id) {
                    blocks.add(entry.getLongKey());
                }
            }
            // from the wall inward, over a moment
            long[] keys = blocks.toLongArray();
            float[] reach = new float[keys.length];
            Integer[] order = new Integer[keys.length];
            for (int k = 0; k < keys.length; k++) {
                reach[k] = (float) HexShape.level(hexCenter, 1.0F, Vec3.atCenterOf(BlockPos.of(keys[k])));
                order[k] = k;
            }
            Arrays.sort(order, (a, b) -> Float.compare(reach[b], reach[a]));
            for (int k = 0; k < order.length; k++) {
                takedown.add(new long[] {keys[order[k]], now + (long) TAKEDOWN_TICKS * k / Math.max(1, order.length)});
            }
            changed = true;
        }
    }

    private int takeDown(ServerLevel level, int budget, long now) {
        if (takedown.isEmpty()) {
            return budget;
        }
        while (!takedown.isEmpty() && budget > 0 && takedown.peek()[1] <= now) {
            long key = takedown.poll()[0];
            Built built = this.built.get(key);
            if (built != null && restore(level, key, built)) {
                budget--;
                gone(key);
            }
        }
        if (takedown.isEmpty() && !kept.isEmpty()) {
            // with their walls back, what hung on them can go back up
            putBackKept(level, false, Vec3.ZERO, 0.0F, null);
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
                gone(key);
            }
        }
        if (!kept.isEmpty() && wall > 0.0F) {
            putBackKept(level, true, hexCenter, wall + 2.0F, null);
        }
    }

    /**
     * As the falling wall reaches the caster's home, lets it go on its own rather than taking it down with the rest: its
     * blocks, and what was taken up from its lot, are handed over to stand on after the Hex has gone, and to go the way
     * they went up, backward.
     *
     * @param wall where the falling wall stands
     * @return the home left behind, the moment the wall reaches it; otherwise null
     */
    public @Nullable HomeRemnant leaveHome(ServerLevel level, Vec3 hexCenter, float wall, UUID caster, long now) {
        if (homeLeft || mode == HexBuild.NOTHING) {
            return null;
        }
        BlockPos center = BlockPos.containing(hexCenter);
        Part home = home(center);
        if (home == null) {
            homeLeft = true;
            return null;
        }
        int id = home.id();
        if (homeReach < 0.0F) {
            homeReach = 0.0F;
            for (Long2ObjectMap.Entry<Built> entry : built.long2ObjectEntrySet()) {
                if (entry.getValue().part() == id) {
                    homeReach = Math.max(homeReach, (float) HexShape.level(hexCenter, 1.0F, Vec3.atCenterOf(BlockPos.of(entry.getLongKey()))));
                }
            }
        }
        if (wall > homeReach + 1.0F) {
            return null;
        }
        homeLeft = true;
        Long2ObjectOpenHashMap<Built> blocks = new Long2ObjectOpenHashMap<>();
        for (Long2ObjectMap.Entry<Built> entry : built.long2ObjectEntrySet()) {
            if (entry.getValue().part() == id) {
                blocks.put(entry.getLongKey(), entry.getValue());
            }
        }
        if (blocks.isEmpty()) {
            return null;
        }
        for (long key : blocks.keySet()) {
            built.remove(key);
        }
        List<Kept> keptThere = new ArrayList<>();
        Iterator<Kept> keeping = kept.iterator();
        while (keeping.hasNext()) {
            Kept entry = keeping.next();
            if (entry.part() == id) {
                keptThere.add(entry);
                keeping.remove();
            }
        }
        int ground = levels.getOrDefault(id, center.getY());
        boolean madeOver = adopted.remove(id);
        finished.remove(id);
        started.remove(id);
        builds.remove(id);
        levels.remove(id);
        partial.remove(id);
        changed = true;
        // the way it went up, to go the same way backward; a home made of what stood there can't be worked out again,
        // and goes by what each of its blocks is for
        Long2IntOpenHashMap builtAt = new Long2IntOpenHashMap();
        builtAt.defaultReturnValue(-1);
        int duration = HomeRemnant.BUILD_TICKS;
        if (!madeOver) {
            Blueprint blueprint = blueprint(home, Survey.flat(ground, home), false, new boolean[home.width() * home.depth()]);
            for (Piece piece : blueprint.schedule()) {
                if (piece.role() != Role.CLEAR) {
                    builtAt.put(piece.pos().asLong(), piece.tick());
                }
            }
            duration = blueprint.duration();
        }
        return HomeRemnant.of(caster, hexCenter, ground, builtEra, blocks, keptThere, builtAt, duration, now);
    }

    /**
     * Puts everything back at once: for a Hex dispelled outright, or at the end of its fall. What lies in chunks that
     * aren't loaded is handed over to be put back when they are.
     */
    public void restoreAll(ServerLevel level, java.util.function.BiConsumer<Long, Built> unloaded, java.util.function.Consumer<Kept> unloadedKept) {
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
        partial.clear();
        adopted.clear();
        putBackKept(level, true, Vec3.ZERO, 0.0F, unloadedKept);
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
        if (!isStill(entry, current) && !HexPaint.paintedOver(level, pos, current)) {
            return false;
        }
        boolean moved = putBack(level, pos, current, entry);
        // the other half of a tall plant, a door or a bed comes back with it
        if (entry.original().hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            BlockPos other = entry.original().getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            Built pair = built.remove(other.asLong());
            if (pair != null) {
                BlockState otherNow = level.getBlockState(other);
                if (isStill(pair, otherNow)) {
                    putBack(level, other, otherNow, pair);
                }
            }
        }
        return moved;
    }

    /**
     * Whether a spot still holds what the town left there, rather than something a player has put in its place.
     */
    static boolean isStill(Built entry, BlockState current) {
        if (entry.role() == Role.PRESERVE) {
            return current.getBlock() == entry.original().getBlock();
        }
        if ((entry.role() == Role.LAWN || entry.role() == Role.FILL) && current.is(BlockTags.DIRT)) {
            // the town's turf goes to dirt under a tree's trunk, and its fill grows grass where the sun reaches it
            return true;
        }
        return current.isAir() || EraStyle.isStyleOf(entry.role(), entry.paint(), current);
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
        if (isStill(entry, current) || HexPaint.paintedOver(level, pos, current)) {
            putBack(level, pos, current, entry);
        }
        return true;
    }

    /**
     * Puts back what stood at a spot, and whatever was kept in it.
     */
    static boolean putBack(ServerLevel level, BlockPos pos, BlockState current, Built entry) {
        BlockState original = entry.original();
        CompoundTag data = entry.data();
        if (current == original && data == null) {
            return false;
        }
        level.setBlock(pos, original, QUIET_FLAGS);
        if (data != null) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity != null) {
                entity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), data));
                entity.setChanged();
                level.sendBlockUpdated(pos, original, original, Block.UPDATE_CLIENTS);
            }
        }
        original.updateNeighbourShapes(level, pos, BUILT_FLAGS);
        return true;
    }

    // ---------------------------------------------------------------- era makeovers

    /**
     * Sweeps the town into the Hex's era, outward from the middle as the era spreads: each block takes on its new look
     * as the front reaches it, still drained of color, which then blooms in over it.
     */
    private void restyle(ServerLevel level, Vec3 hexCenter, Era era, float front, long now, int budget) {
        if (era == builtEra && restyle == null) {
            return;
        }
        if (restyle == null || restyle.era != era) {
            restyle = Sweep.insideOut(built, hexCenter);
            restyle.era = era;
            restyle.start = now;
        }
        Sweep sweep = restyle;
        while (sweep.cursor < sweep.order.length && budget > 0) {
            int at = sweep.cursor;
            if (sweep.reach[at] > front) {
                break;
            }
            sweep.cursor++;
            long key = sweep.order[at];
            Built entry = built.get(key);
            if (entry == null || entry.role() == Role.CLEAR || entry.role() == Role.PRESERVE) {
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
                if (isPaired(state)) {
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

    // ---------------------------------------------------------------- slips

    /**
     * While its Hex is unsteady, now and then a part of the town slips: for a moment it jumps into another era, or
     * flickers out of existence, showing what stood there before. Only the players near enough to see it are told;
     * nothing really changes, so nothing can be left that way.
     *
     * @param unrest how unsteady the Hex is, 0 to 1
     */
    public void slip(ServerLevel level, BlockPos center, float unrest, long now) {
        slipping.int2LongEntrySet().removeIf(entry -> entry.getLongValue() <= now);
        RandomSource random = level.getRandom();
        if (unrest < 0.05F || restyle != null || finished.isEmpty() || slipping.size() >= 2 + Math.round(6 * unrest)
                || random.nextFloat() > unrest * 0.45F) {
            return;
        }
        // what someone is close enough to see slip
        List<Part> near = new ArrayList<>();
        for (int id : finished) {
            Part part = part(center, id);
            if (part != null && !slipping.containsKey(id) && nearestPlayer(level, middle(part, center)) < SLIP_NEAR) {
                near.add(part);
            }
        }
        if (near.isEmpty()) {
            return;
        }
        Part part = near.get(random.nextInt(near.size()));
        int id = part.id();
        Vec3 middle = middle(part, center);
        List<ServerPlayer> watching = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(middle) < SLIP_SEEN_FROM * SLIP_SEEN_FROM) {
                watching.add(player);
            }
        }
        // streets only ever change era; a house can be there one moment and gone the next
        boolean gone = !part.kind().isStreet() && random.nextFloat() < 0.4F;
        Era[] eras = Era.values();
        Era era = eras[(builtEra.ordinal() + 1 + random.nextInt(eras.length - 1)) % eras.length];
        int ground = levels.getOrDefault(id, Integer.MIN_VALUE);
        LongArrayList positions = new LongArrayList();
        IntArrayList states = new IntArrayList();
        for (var entry : built.long2ObjectEntrySet()) {
            Built block = entry.getValue();
            if (block.part() != id || block.role() == Role.PRESERVE) {
                continue;
            }
            BlockPos pos = BlockPos.of(entry.getLongKey());
            BlockState current = level.getBlockState(pos);
            BlockState shown;
            if (block.role() == Role.CLEAR) {
                // what the town cleared away comes back for a moment, the old village or forest showing through
                if (!gone || !current.isAir()) {
                    continue;
                }
                shown = block.original();
            } else if (!EraStyle.isStyleOf(block.role(), block.paint(), current)) {
                // a player's block now
                continue;
            } else if (gone) {
                // floors stay, so no one standing inside falls
                if (pos.getY() <= ground) {
                    continue;
                }
                shown = block.original();
            } else {
                shown = EraStyle.state(block.role(), era, block.paint(), current);
            }
            if (shown != current) {
                positions.add(entry.getLongKey());
                states.add(Block.getId(shown));
            }
        }
        if (positions.isEmpty()) {
            return;
        }
        TownGlitchPayload payload = new TownGlitchPayload(positions.toLongArray(), states.toIntArray(), flickers(random, gone));
        for (ServerPlayer player : watching) {
            Services.NETWORK.sendToPlayer(player, payload);
        }
        slipping.put(id, now + TownGlitchPayload.TICKS + 4);
    }

    /**
     * When a slipping part shows its other self, a bit for each tick: a few long jumps into another era, or a quick
     * stutter in and out of existence.
     */
    private static int flickers(RandomSource random, boolean gone) {
        int bits = 0;
        int tick = 0;
        int flicks = gone ? 3 + random.nextInt(2) : 1 + random.nextInt(2);
        for (int flick = 0; flick < flicks && tick < TownGlitchPayload.TICKS; flick++) {
            int on = gone ? 1 + random.nextInt(2) : 3 + random.nextInt(4);
            for (int k = 0; k < on && tick < TownGlitchPayload.TICKS; k++, tick++) {
                bits |= 1 << tick;
            }
            tick += 1 + random.nextInt(2);
        }
        return bits;
    }

    private @Nullable Part part(BlockPos center, int id) {
        TownPlan current = plan(center);
        if (partsById == null || partsOf != current) {
            partsById = new Int2ObjectOpenHashMap<>();
            for (Part part : current.parts()) {
                partsById.put(part.id(), part);
            }
            partsOf = current;
        }
        return partsById.get(id);
    }

    private Vec3 middle(Part part, BlockPos center) {
        return new Vec3((part.minX() + part.maxX() + 1) / 2.0, levels.getOrDefault(part.id(), center.getY()), (part.minZ() + part.maxZ() + 1) / 2.0);
    }

    private static double nearestPlayer(ServerLevel level, Vec3 at) {
        double nearest = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            nearest = Math.min(nearest, player.position().distanceTo(at));
        }
        return nearest;
    }

    // ---------------------------------------------------------------- the land

    private boolean isLoaded(ServerLevel level, Part part) {
        return level.hasChunkAt(new BlockPos(part.minX(), 0, part.minZ())) && level.hasChunkAt(new BlockPos(part.maxX(), 0, part.maxZ()))
                && level.hasChunkAt(new BlockPos(part.minX(), 0, part.maxZ())) && level.hasChunkAt(new BlockPos(part.maxX(), 0, part.minZ()));
    }

    /**
     * Works out the lie of the land under a part, seeing through whatever stands on it: the level to lay it at, at the
     * middle height of the ground under it, and the columns it must leave alone. A column it can't use is one under a
     * lake or the sea, too far above or below that level to cut down or fill in, or with something that can't be
     * broken. A house needs every column of its lot; streets, gardens and anything the wall cuts through leave those
     * out, as long as most of the land is good.
     *
     * @param clip how far out the wall reached, past which nothing of the part is looked at; {@link #WHOLE} for all of it
     * @return null if the part can't go there
     */
    private @Nullable Survey survey(ServerLevel level, Part part, Vec3 hexCenter, float clip) {
        Frame frame = new Frame(part.origin(), part.front());
        int width = part.width();
        int depth = part.depth();
        Terrain.Column[] columns = new Terrain.Column[width * depth];
        int[] land = new int[columns.length];
        int count = 0;
        int inside = 0;
        for (int b = 0; b < depth; b++) {
            for (int a = 0; a < width; a++) {
                BlockPos at = frame.at(a, b, 0);
                if (!within(hexCenter, clip, at)) {
                    continue;
                }
                inside++;
                Terrain.Column column = column(level, at.getX(), at.getZ());
                columns[b * width + a] = column;
                if (column.isLand(SHALLOW)) {
                    land[count++] = column.surface();
                }
            }
        }
        boolean forgiving = part.kind().isStreet() || clip != WHOLE;
        if (count == 0 || !forgiving && count < inside) {
            return null;
        }
        int[] sorted = Arrays.copyOf(land, count);
        Arrays.sort(sorted);
        int ground = sorted[count / 2];
        boolean[] blocked = new boolean[columns.length];
        int blockedCount = 0;
        for (int index = 0; index < columns.length; index++) {
            Terrain.Column column = columns[index];
            if (column == null) {
                blocked[index] = true;
                continue;
            }
            boolean usable = column.isLand(SHALLOW) && !column.untouchable() && column.surface() - ground <= MAX_CUT
                    && ground - column.surface() <= MAX_FILL;
            if (!usable) {
                if (!forgiving) {
                    return null;
                }
                blocked[index] = true;
                blockedCount++;
            }
        }
        if (blockedCount * 2 > inside) {
            return null;
        }
        return new Survey(ground, columns, blocked, width);
    }

    /**
     * Reads one column of land: where its natural ground lies under whatever stands on it, how deep any water over it
     * is, how high whatever stands there reaches, and whether any of it can't be cleared. The town's own blocks are seen
     * through, to what stood there before.
     */
    private Terrain.Column column(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
        int bottom = Math.max(level.getMinY(), top - SCAN_DEPTH);
        int water = 0;
        boolean untouchable = false;
        BlockState below = seen(level, pos.set(x, top, z));
        for (int y = top; y > bottom; y--) {
            BlockState state = below;
            below = seen(level, pos.set(x, y - 1, z));
            if (state.isAir()) {
                continue;
            }
            // ground with ground under it: the land itself, not a dirt roof or a block of sand someone put down
            if (Terrain.isGround(state) && Terrain.isGround(below)) {
                return new Terrain.Column(y, water, top, untouchable);
            }
            if (Terrain.isWater(state)) {
                water++;
            }
            if (!built.containsKey(BlockPos.asLong(x, y, z)) && Terrain.isUntouchable(level, pos.set(x, y, z), state)) {
                untouchable = true;
            }
        }
        return new Terrain.Column(Terrain.NO_GROUND, water, top, untouchable);
    }

    /**
     * A block as the town sees it: what stood there before, if the town has built or cleared there.
     */
    private BlockState seen(ServerLevel level, BlockPos pos) {
        Built entry = built.get(pos.asLong());
        return entry != null ? entry.original() : level.getBlockState(pos);
    }

    /**
     * @param occupied which of the part's columns something already stands in that stays
     */
    private Blueprint blueprint(Part part, Survey survey, boolean cutThrough, boolean[] occupied) {
        Frame frame = new Frame(part.origin(), part.front());
        int ground = survey.ground();
        Houses.Heights heights = survey::height;
        RandomSource random = RandomSource.create(seedOf(part) ^ (part.lookId() * 0x9E3779B97F4A7C15L));
        if (mode == HexBuild.ORCHARD) {
            return switch (part.kind()) {
                case HOME -> Houses.anchor(frame, ground, heights, random);
                case HOUSE -> random.nextFloat() < 0.18F ? Orchard.meadow(frame, ground, heights, random, occupied)
                        : Orchard.plot(frame, ground, heights, random, occupied);
                case SQUARE -> Orchard.farmyard(frame, part.width(), part.depth(), ground, heights, random);
                case STREET, CROSS_STREET -> Orchard.lane(frame, part.width(), ground, heights);
                case CROSSING -> Orchard.crossing(frame, ground, heights);
            };
        }
        return switch (part.kind()) {
            case HOME -> Houses.anchor(frame, ground, heights, random);
            case HOUSE -> cutThrough ? Houses.garden(frame, ground, heights, random) : lot(part, frame, ground, heights, random);
            case SQUARE -> Streets.square(frame, part.width(), part.depth(), ground, heights, random);
            case STREET -> Streets.street(frame, part.width(), ground, heights, 0);
            case CROSSING -> Streets.crossing(frame, ground, heights);
            case CROSS_STREET -> Streets.street(frame, part.width(), ground, heights, part.width() / 2);
        };
    }

    /**
     * What goes up on a lot, by what the town has zoned it for.
     */
    private Blueprint lot(Part part, Frame frame, int ground, Houses.Heights heights, RandomSource random) {
        return switch (zoning().getOrDefault(part.id(), Use.HOUSE)) {
            case SHOP -> Civic.shop(frame, ground, heights, shopKinds.get(part.id()), random);
            case LIBRARY -> Civic.library(frame, ground, heights, random);
            case CHAPEL -> Civic.chapel(frame, ground, heights, random);
            case SCHOOL -> Civic.school(frame, ground, heights, random);
            case PLAYGROUND -> Civic.playground(frame, ground, heights, random);
            case PARK -> Houses.park(frame, ground, heights, random);
            case HOUSE -> Houses.house(frame, ground, heights, random);
        };
    }

    // ---------------------------------------------------------------- zoning

    /** What a lot of the town is for. */
    private enum Use {
        HOUSE, PARK, PLAYGROUND, SHOP, LIBRARY, CHAPEL, SCHOOL
    }

    /**
     * What every lot of the town is for, worked out once from its plan and its seed, so it is the same each time: a
     * main street of shops either side of the square, a playground near the middle, and further out, where only a big
     * Hex reaches, the library, the chapel and the school, with more parks and playgrounds here and there among the
     * houses. Everything else is a house. A town the plan brings back is zoned just as it was when first raised.
     */
    private Int2ObjectOpenHashMap<Use> zoning() {
        if (zoning != null) {
            return zoning;
        }
        Int2ObjectOpenHashMap<Use> uses = new Int2ObjectOpenHashMap<>();
        if (plan == null) {
            return uses;
        }
        for (int layer = 0; layer < plan.grids().size(); layer++) {
            zone(uses, plan.grid(layer), layer << TownPlan.LAYER_SHIFT);
        }
        // past where a town was first laid out to, as a Hex cast off its middle can reach, the odd park or playground
        // among the houses as anywhere
        for (Part lot : plan.parts()) {
            if (lot.kind() == Kind.HOUSE && !uses.containsKey(lot.id())) {
                Use use = scattered(seedOf(lot), lot.gridId(), lot.distanceSqr());
                if (use != null) {
                    uses.put(lot.id(), use);
                }
            }
        }
        zoning = uses;
        return uses;
    }

    /**
     * Zones one of the plan's towns, laid out whole by itself as when it was first raised.
     *
     * @param layer its layer, as it is in the ids of its parts in the plan
     */
    private void zone(Int2ObjectOpenHashMap<Use> uses, TownPlan.Grid grid, int layer) {
        long seed = grid.seed();
        List<Part> lots = new ArrayList<>();
        for (Part part : TownPlan.whole(grid, Hexes.MAX_RADIUS).parts()) {
            if (part.kind() == Kind.HOUSE) {
                lots.add(part);
            }
        }
        List<Part> shops = new ArrayList<>();
        for (Part lot : lots) {
            int street = (lot.id() >> 16 & 0xFF) - 64;
            int block = (lot.id() >> 8 & 0xFF) - 64;
            boolean squareSide = (lot.id() & 1) == 1;
            if (street == 0 && Math.abs(block) == 1 && (squareSide || hash(seed, lot.id(), 3) < 0.5F)) {
                uses.put(layer | lot.id(), Use.SHOP);
                shops.add(lot);
            }
        }
        // every shop on main street sells something different, as far as the kinds go round
        shops.sort((a, b) -> Double.compare(a.distanceSqr(), b.distanceSqr()));
        int[] kinds = Signs.SHOPS.clone();
        RandomSource shuffle = RandomSource.create(seed ^ 0x5409);
        for (int i = kinds.length - 1; i > 0; i--) {
            int j = shuffle.nextInt(i + 1);
            int swap = kinds[i];
            kinds[i] = kinds[j];
            kinds[j] = swap;
        }
        for (int i = 0; i < shops.size(); i++) {
            shopKinds.put(layer | shops.get(i).id(), kinds[i % kinds.length]);
        }
        pick(uses, lots, layer, seed, Use.PLAYGROUND, 20.0, 60.0);
        pick(uses, lots, layer, seed, Use.LIBRARY, 40.0, 85.0);
        pick(uses, lots, layer, seed, Use.CHAPEL, 55.0, 105.0);
        pick(uses, lots, layer, seed, Use.SCHOOL, 70.0, 130.0);
        for (Part lot : lots) {
            if (!uses.containsKey(layer | lot.id())) {
                Use use = scattered(seed, lot.id(), lot.distanceSqr());
                if (use != null) {
                    uses.put(layer | lot.id(), use);
                }
            }
        }
    }

    /**
     * Whether a lot not zoned for anything else is one of the parks or playgrounds here and there among the houses.
     *
     * @param distanceSqr how far it is from the middle of its town
     */
    private static @Nullable Use scattered(long seed, int gridId, double distanceSqr) {
        float h = hash(seed, gridId, 7);
        if (Math.sqrt(distanceSqr) >= 35.0 && h < 0.06F) {
            return Use.PLAYGROUND;
        }
        return h >= 0.06F && h < 0.14F ? Use.PARK : null;
    }

    /**
     * Zones one lot, of those a set distance from the middle not yet zoned, for a use: the one this town's seed favors.
     */
    private static void pick(Int2ObjectOpenHashMap<Use> uses, List<Part> lots, int layer, long seed, Use use, double from, double to) {
        Part best = null;
        float lowest = 2.0F;
        for (Part lot : lots) {
            double distance = Math.sqrt(lot.distanceSqr());
            if (uses.containsKey(layer | lot.id()) || distance < from || distance > to) {
                continue;
            }
            float h = hash(seed, lot.id(), 101 + use.ordinal() * 31);
            if (h < lowest) {
                lowest = h;
                best = lot;
            }
        }
        if (best != null) {
            uses.put(layer | best.id(), use);
        }
    }

    private static float hash(long seed, int id, int salt) {
        long x = seed ^ id * 0x9E3779B97F4A7C15L ^ salt * 0xC2B2AE3D27D4EB4FL;
        x = (x ^ x >>> 30) * 0xBF58476D1CE4E5B9L;
        x = (x ^ x >>> 27) * 0x94D049BB133111EBL;
        x ^= x >>> 31;
        return (x >>> 40) / (float) (1 << 24);
    }

    // ---------------------------------------------------------------- records

    /**
     * A block the town put down or cleared away.
     *
     * @param original what stood there before
     * @param paint    which of its role's variants it is
     * @param part     the part of the plan it belongs to
     * @param data     what was kept in what stood there before, a chest's contents or a sign's words, if anything
     */
    public record Built(BlockState original, Role role, int paint, int part, @Nullable CompoundTag data) {

        public Built(BlockState original, Role role, int paint, int part) {
            this(original, role, paint, part, null);
        }
    }

    /**
     * Something that stood where the town now does, taken up to be set back down when the Hex falls.
     *
     * @param data everything about it, as it would be saved
     * @param part the part of the plan it made way for
     * @param at   where it stood
     */
    public record Kept(CompoundTag data, int part, Vec3 at) {

        public static final Codec<Kept> CODEC = RecordCodecBuilder.create(i -> i.group(
                CompoundTag.CODEC.fieldOf("data").forGetter(Kept::data),
                Codec.INT.fieldOf("part").forGetter(Kept::part),
                Vec3.CODEC.fieldOf("at").forGetter(Kept::at)
        ).apply(i, Kept::new));
    }

    /**
     * @param columns the land under each column, by b * width + a; null where the part isn't looked at
     * @param blocked columns to leave alone
     */
    private record Survey(int ground, Terrain.@Nullable Column[] columns, boolean[] blocked, int width) {

        static Survey flat(int ground, Part part) {
            return new Survey(ground, new Terrain.Column[part.width() * part.depth()], new boolean[part.width() * part.depth()], part.width());
        }

        Survey at(int ground) {
            return new Survey(ground, columns, blocked, width);
        }

        /**
         * The natural ground under a column, or the part's level where that isn't known.
         */
        int height(int a, int b) {
            int index = b * width + a;
            Terrain.Column column = index >= 0 && index < columns.length ? columns[index] : null;
            return column == null || column.surface() == Terrain.NO_GROUND ? ground : column.surface();
        }

        boolean isBlocked(int index) {
            return index >= 0 && index < blocked.length && blocked[index];
        }

        boolean isBlocked(Part part, BlockPos pos) {
            int index = index(part, pos.getX(), pos.getZ());
            return index >= 0 && isBlocked(index);
        }

        /**
         * Which column of the part a world column is, by b * width + a, or -1 if it isn't in the part.
         */
        int index(Part part, int x, int z) {
            Frame frame = new Frame(part.origin(), part.front());
            Direction right = frame.right();
            Direction back = frame.back();
            int dx = x - part.origin().getX();
            int dz = z - part.origin().getZ();
            int a = dx * right.getStepX() + dz * right.getStepZ();
            int b = dx * back.getStepX() + dz * back.getStepZ();
            int index = b * width + a;
            return a >= 0 && a < width && b >= 0 && index < blocked.length ? index : -1;
        }
    }

    private static final class Build {
        final Part part;
        final int ground;
        final List<Piece> pieces;
        final long start;
        final int duration;
        /** Blocks of a building that stays, made over so far. */
        final LongArrayList restyled = new LongArrayList();
        /** The cars parked on it once it stands. */
        final List<Blueprint.Car> cars = new ArrayList<>();
        int next;
        /** How many of the pieces players have been told are coming. */
        int announced;

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
            return sorted(built, center, true, 0.0F);
        }

        static Sweep outsideIn(Long2ObjectOpenHashMap<Built> built, Vec3 center) {
            return sorted(built, center, false, 0.0F);
        }

        /**
         * Only those lying at least {@code from} out.
         */
        static Sweep outsideIn(Long2ObjectOpenHashMap<Built> built, Vec3 center, float from) {
            return sorted(built, center, false, from);
        }

        private static Sweep sorted(Long2ObjectOpenHashMap<Built> built, Vec3 center, boolean ascending, float from) {
            long[] all = built.keySet().toLongArray();
            float[] allReach = new float[all.length];
            int count = 0;
            for (int k = 0; k < all.length; k++) {
                float reach = (float) HexShape.level(center, 1.0F, Vec3.atCenterOf(BlockPos.of(all[k])));
                if (reach >= from) {
                    all[count] = all[k];
                    allReach[count] = reach;
                    count++;
                }
            }
            long[] keys = Arrays.copyOf(all, count);
            float[] reaches = Arrays.copyOf(allReach, count);
            Integer[] index = new Integer[keys.length];
            for (int k = 0; k < keys.length; k++) {
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
    private record Saved(List<BlockState> palette, long[] positions, int[] originals, int[] roles, int[] parts, int[] dataAt,
                         List<CompoundTag> data) {

        static final Saved EMPTY = new Saved(List.of(), new long[0], new int[0], new int[0], new int[0], new int[0], List.of());

        static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.listOf().fieldOf("palette").forGetter(Saved::palette),
                LONGS.fieldOf("positions").forGetter(Saved::positions),
                INTS.fieldOf("originals").forGetter(Saved::originals),
                INTS.fieldOf("roles").forGetter(Saved::roles),
                INTS.fieldOf("parts").forGetter(Saved::parts),
                INTS.optionalFieldOf("data_at", new int[0]).forGetter(Saved::dataAt),
                CompoundTag.CODEC.listOf().optionalFieldOf("data", List.of()).forGetter(Saved::data)
        ).apply(i, Saved::new));

        static Saved of(Long2ObjectOpenHashMap<Built> built) {
            List<BlockState> palette = new ArrayList<>();
            Map<BlockState, Integer> indices = new HashMap<>();
            int size = built.size();
            long[] positions = new long[size];
            int[] originals = new int[size];
            int[] roles = new int[size];
            int[] parts = new int[size];
            IntArrayList dataAt = new IntArrayList();
            List<CompoundTag> data = new ArrayList<>();
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
                if (value.data() != null) {
                    dataAt.add(k);
                    data.add(value.data());
                }
                k++;
            }
            return new Saved(palette, positions, originals, roles, parts, dataAt.toIntArray(), data);
        }

        void into(Long2ObjectOpenHashMap<Built> built) {
            int count = Math.min(Math.min(positions.length, originals.length), Math.min(roles.length, parts.length));
            Int2ObjectOpenHashMap<CompoundTag> kept = new Int2ObjectOpenHashMap<>();
            for (int k = 0; k < Math.min(dataAt.length, data.size()); k++) {
                kept.put(dataAt[k], data.get(k));
            }
            for (int k = 0; k < count; k++) {
                int state = originals[k];
                if (state < 0 || state >= palette.size()) {
                    continue;
                }
                built.put(positions[k], new Built(palette.get(state), Role.byIndex(roles[k] & 0xFF), roles[k] >>> 8, parts[k], kept.get(k)));
            }
        }
    }
}
