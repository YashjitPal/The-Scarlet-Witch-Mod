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
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
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
import java.util.function.LongPredicate;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
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

    /**
     * Most blocks the town changes in a tick, building, restyling and taking down together: more than the time it has
     * for them lets it, which is what holds it. What its wall goes by is held only by time.
     */
    private static final int BUDGET = 8192;
    /**
     * How often the town looks for parts the wall has reached to start, and how many it starts at once: as many as keep
     * up with the wall spreading out, within half its share of the tick, the rest of which goes to building them.
     */
    private static final int TRIGGER_INTERVAL = 2;
    private static final int STARTS_PER_TRIGGER = 12;
    private static final int RETRY_SKIPPED = 400;
    /**
     * Most parts out where no one is whose land is asked to be loaded at once, for them to be built as the Hex grows; how
     * long one's land is given to come before another has its turn; and how often what builds there is kept loaded.
     */
    private static final int MAX_FETCHING = 2;
    private static final int FETCH_PATIENCE = 400;
    private static final int KEEP_LOADED_INTERVAL = 10;
    /** How near a player must be to a slipping part to be shown it. */
    private static final double SLIP_SEEN_FROM = 160.0;
    /** Parts slip only this near someone, where they can be seen to. */
    private static final double SLIP_NEAR = 48.0;
    private static final int BUILT_FLAGS = Block.UPDATE_ALL;
    private static final int QUIET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Clearing touches nothing around it: no neighbors told, nothing dropped, no chest spilling its contents. */
    private static final int CLEAR_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static long placedEver;
    private static long putBackEver;
    private static final int CUTTING = 0;
    private static final int STARTING = 1;
    private static final int PLACING = 2;
    private static final int RESTYLING = 3;
    private static final int SURVEYS = 4;
    private static final int SURVEYING = 5;
    private static final long[] STAGE_NANOS = new long[6];
    /** The order a block's neighbors take in its shape, as the game's own, and how far a change may ripple on. */
    private static final Direction[] SHAPE_ORDER = {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.DOWN, Direction.UP};
    private static final int SHAPE_UPDATE_LIMIT = 512;
    /** What a townsperson may be found standing on, and how far over and under a part's ground they are looked for. */
    private static final java.util.Set<Role> STANDS_ON = java.util.EnumSet.of(Role.FLOOR, Role.RUG, Role.PORCH, Role.PLAZA, Role.SIDEWALK,
            Role.WALKWAY, Role.LAWN);
    private static final int SPAWN_ABOVE = 16;
    private static final int SPAWN_BELOW = 4;
    private static final int SPAWN_ATTEMPTS = 12;
    private static final LongPredicate NONE_GOING_BACK = key -> false;

    /** How far the town will cut a hill down, or fill a hollow in, to level a part. */
    private static final int MAX_CUT = 8;
    private static final int MAX_FILL = 6;
    /** How far a run of street keeps to the land, either way from the even line along it. */
    private static final int FOLLOW = 5;
    /** How many columns either way the heights along a run of street are smoothed over. */
    private static final int SMOOTH = 3;
    /** Most stretches looked along for the crossing at either end of a run of street. */
    private static final int RUN_REACH = 4;
    /** Fewest columns a tunnel runs for: a shorter hill is cut through. */
    private static final int SHORTEST_TUNNEL = 3;
    /** Furthest a lot can lie over or under its sidewalk for what stands on it to be come to from the street. */
    private static final int UNREACHABLE = Houses.FRONT_RISE + 3;
    /** How far a lot's land may climb from its front to its back, past which what can't go on it is laid in terraces. */
    private static final int TERRACE_CLIMB = 4;
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
    /** How long after the wall stops moving in its sweep is let go. */
    private static final int CUT_LINGER = 40;
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
                    town.remembered, Optional.ofNullable(town.homeLot), town.endsToSave(), town.streetLevelsToSave(), town.plannedCrossingsToSave(),
                    new int[0], town.groundsToSave()))
    ).apply(i, HexTown::load));

    /**
     * Where a town's grid is laid out from, where its home rose, how far out its wall has stood, the towns it gives way
     * to, where its caster raised their home off its grid, where each stretch of street laid along the land meets the
     * road past its ends, and how high every stretch and crossing worked out with its run is to be laid, saved with
     * the rest; towns from before these were kept are laid out from their Hex's middle.
     *
     * @param ends         each stretch's id, then the heights it meets the road at past its start and past its far end
     * @param streetLevels each stretch's id, how many columns it has, then how high each is laid
     * @param crossings    each crossing's id, then how high it is to be laid
     * @param gardens      the lots laid out as gardens, their land not taking a house, as towns kept them before there
     *                     were other grounds
     * @param grounds      each lot laid out as what suits its land rather than what it was zoned for, then which
     */
    private record Recall(Optional<BlockPos> origin, Optional<Vec3> home, float reach, List<TownMemory> remembered,
                          Optional<TownPlan.HomeLot> homeLot, int[] ends, int[] streetLevels, int[] crossings, int[] gardens, int[] grounds) {

        static final MapCodec<Recall> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BlockPos.CODEC.optionalFieldOf("origin").forGetter(Recall::origin),
                Vec3.CODEC.optionalFieldOf("home").forGetter(Recall::home),
                Codec.FLOAT.optionalFieldOf("reach", 0.0F).forGetter(Recall::reach),
                TownMemory.CODEC.listOf().optionalFieldOf("remembered", List.of()).forGetter(Recall::remembered),
                TownPlan.HomeLot.CODEC.optionalFieldOf("home_lot").forGetter(Recall::homeLot),
                INTS.optionalFieldOf("street_ends", new int[0]).forGetter(Recall::ends),
                INTS.optionalFieldOf("street_levels", new int[0]).forGetter(Recall::streetLevels),
                INTS.optionalFieldOf("crossing_levels", new int[0]).forGetter(Recall::crossings),
                INTS.optionalFieldOf("gardens", new int[0]).forGetter(Recall::gardens),
                INTS.optionalFieldOf("grounds", new int[0]).forGetter(Recall::grounds)
        ).apply(i, Recall::new));
    }

    private HexBuild mode;
    private final Direction forward;
    private final long seed;
    private final Long2ObjectOpenHashMap<Built> built = new Long2ObjectOpenHashMap<>();
    /**
     * Where each part's blocks are among {@link #built}, for a part to find its own without going through the whole
     * town, which can run to hundreds of thousands of blocks. Kept by {@link #putBuilt} and {@link #removeBuilt}.
     */
    private final Int2ObjectOpenHashMap<LongOpenHashSet> byPart = new Int2ObjectOpenHashMap<>();
    /** The ground level each part was laid at, from when it was begun. */
    private final Int2IntOpenHashMap levels = new Int2IntOpenHashMap();
    /**
     * Where each stretch of street laid along the land meets the road past its start and past its far end, packed, for
     * the stretches either side of it to meet it there.
     */
    private final Int2LongOpenHashMap ends = new Int2LongOpenHashMap();
    /**
     * How high each column of a stretch of street is to be laid, worked out with its whole run from one crossing to
     * the next, for every stretch of it to meet the next as each is begun.
     */
    private final Int2ObjectOpenHashMap<int[]> streetLevels = new Int2ObjectOpenHashMap<>();
    /** How high crossings the runs meeting them were worked out to meet are to be laid, before they are begun. */
    private final Int2IntOpenHashMap plannedCrossings = new Int2IntOpenHashMap();
    /**
     * Lots whose land won't take what they were zoned for, under water, at the edge of a drop, too steep or out of reach
     * of their street, laid out as what suits it instead.
     */
    private final Int2ObjectOpenHashMap<Use> grounds = new Int2ObjectOpenHashMap<>();
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
    /**
     * The town's blocks from the outside in, while the wall is moving in over them: those players could see when it
     * began, to go first, and the rest.
     */
    private @Nullable Sweep cut;
    private @Nullable Sweep cutFar;
    /** Blocks the town has put down since {@link #cut} was sorted, which it would miss. */
    private final LongArrayList cutFresh = new LongArrayList();
    /**
     * Blocks the wall has gone by where players can see, waiting to go back, by chunk: what the wall passes goes here the
     * moment it does, however far behind putting it back falls.
     */
    private final Long2ObjectOpenHashMap<LongArrayList> due = new Long2ObjectOpenHashMap<>();
    /** When this tick's share of the server's time for the town runs out, in {@link System#nanoTime} nanoseconds. */
    private long deadline = Long.MAX_VALUE;
    /** The same for putting back what its wall has passed, which goes before anything else. */
    private long wallDeadline = Long.MAX_VALUE;
    /** When starting parts must stop for this tick: half way through its share, for those started to go on building. */
    private long startDeadline = Long.MAX_VALUE;
    /** Whether anyone stands about the part building now, to be moved out of the way of what it puts down. */
    private boolean anyoneThere = true;
    /** Where the players in the town's world are this tick, across and along, and how near one must be to see a block. */
    private double[] watchers = new double[0];
    private double watchedReach;
    private double watchedSq;
    /** Parts found not to reach the wall where it stands, and where it stood then: not looked at again till it moves. */
    private final Int2FloatOpenHashMap untouchedAt = new Int2FloatOpenHashMap();
    /** How far in the town was last given up to, for nothing to be looked over again until the Hex shrinks past it. */
    private float shrunkTo = Float.MAX_VALUE;
    /** Whether the town has been looked over since it was loaded for anything left standing out past its wall. */
    private boolean lookedOutside;
    /** Where what the wall takes back in chunks that aren't loaded goes, to be put back once they are. */
    private @Nullable Leftovers leftovers;
    /** Parts out where no one is whose land the town has asked to be loaded, to build them, and when it asked. */
    private final Int2LongOpenHashMap fetching = new Int2LongOpenHashMap();
    /** Where bits of the town went back this tick, by two-by-two cell, with the lowest and highest y that went. */
    private final Long2LongOpenHashMap gone = new Long2LongOpenHashMap();

    {
        gone.defaultReturnValue(NOT_GONE);
        untouchedAt.defaultReturnValue(Float.NaN);
    }
    private @Nullable Sweep restyle;
    /** The same as the Hex falls. */
    private @Nullable Sweep collapse;
    private @Nullable Sweep collapseFar;
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
    /**
     * Whether the wall, falling or drawn in, has reached the caster's home and let it go on its own. Drawn in over it,
     * the Hex raises it again once it is grown back over all of it.
     */
    private boolean homeLeft;
    /** How far out the outermost block of the caster's home stands, once worked out as the Hex falls. */
    private float homeReach = -1.0F;
    /** The home the wall let go of as it was drawn in, for the Hex to hand on to stand alone. */
    private @Nullable HomeRemnant released;
    /** Whether the home going up next is raised by its caster, who reaches out for it, rather than coming back by itself. */
    private boolean homeRaised;

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
     * A seed for a town raised somewhere new, drawn again until its home is another house from any raised from the seeds
     * given, as far as there are other houses to be.
     */
    public static long freshSeed(long seed, List<Long> lately) {
        IntOpenHashSet had = new IntOpenHashSet();
        for (long old : lately) {
            had.add(homeKind(old));
        }
        seed = Houses.marked(seed);
        for (int tries = 0; tries < 32 && had.contains(homeKind(seed)); tries++) {
            seed = Houses.marked(seed * 0x5851F42D4C957F2DL + 0x14057B7EF767814FL);
        }
        return seed;
    }

    /**
     * Which kind of home a town's seed raises.
     */
    private static int homeKind(long seed) {
        return Houses.homeKind(looks(seed, TownPlan.HOME_LOOK), Houses.homeKinds(seed));
    }

    /**
     * What a part of a town looks like is drawn from: its town's seed and which part it is.
     */
    private static RandomSource looks(long seed, int lookId) {
        return RandomSource.create(seed ^ (lookId * 0x9E3779B97F4A7C15L));
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
        for (Long2ObjectMap.Entry<Built> entry : town.built.long2ObjectEntrySet()) {
            town.index(entry.getLongKey(), entry.getValue().part());
        }
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
        int[] ends = recall.ends();
        for (int k = 0; k + 2 < ends.length; k += 3) {
            town.ends.put(ends[k], pack(ends[k + 1], ends[k + 2]));
        }
        int[] streetLevels = recall.streetLevels();
        for (int k = 0; k + 1 < streetLevels.length; ) {
            int id = streetLevels[k];
            int count = streetLevels[k + 1];
            if (count <= 0 || k + 2 + count > streetLevels.length) {
                break;
            }
            town.streetLevels.put(id, Arrays.copyOfRange(streetLevels, k + 2, k + 2 + count));
            k += 2 + count;
        }
        int[] crossings = recall.crossings();
        for (int k = 0; k + 1 < crossings.length; k += 2) {
            town.plannedCrossings.put(crossings[k], crossings[k + 1]);
        }
        for (int id : recall.gardens()) {
            town.grounds.put(id, Use.GARDEN);
        }
        int[] grounds = recall.grounds();
        for (int k = 0; k + 1 < grounds.length; k += 2) {
            town.grounds.put(grounds[k], Use.byIndex(grounds[k + 1]));
        }
        return town;
    }

    private int[] groundsToSave() {
        int[] saved = new int[grounds.size() * 2];
        int k = 0;
        for (Int2ObjectMap.Entry<Use> entry : grounds.int2ObjectEntrySet()) {
            saved[k++] = entry.getIntKey();
            saved[k++] = entry.getValue().ordinal();
        }
        return saved;
    }

    private int[] streetLevelsToSave() {
        IntArrayList saved = new IntArrayList();
        for (Int2ObjectMap.Entry<int[]> entry : streetLevels.int2ObjectEntrySet()) {
            saved.add(entry.getIntKey());
            saved.add(entry.getValue().length);
            saved.addElements(saved.size(), entry.getValue());
        }
        return saved.toIntArray();
    }

    private int[] plannedCrossingsToSave() {
        int[] saved = new int[plannedCrossings.size() * 2];
        int k = 0;
        for (Int2IntMap.Entry entry : plannedCrossings.int2IntEntrySet()) {
            saved[k++] = entry.getIntKey();
            saved[k++] = entry.getIntValue();
        }
        return saved;
    }

    private int[] endsToSave() {
        int[] saved = new int[ends.size() * 3];
        int k = 0;
        for (Int2LongMap.Entry entry : ends.int2LongEntrySet()) {
            saved[k++] = entry.getIntKey();
            saved[k++] = (int) (entry.getLongValue() >> 32);
            saved[k++] = (int) entry.getLongValue();
        }
        return saved;
    }

    private static long pack(int from, int to) {
        return (long) from << 32 | to & 0xFFFFFFFFL;
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

    /** How many blocks all towns have put down, and put back, since the game started: for seeing how they keep up. */
    public static long placedEver() {
        return placedEver;
    }

    public static long putBackEver() {
        return putBackEver;
    }

    /**
     * Somewhere in the town for a townsperson to be found, between so near a spot and so far from it: on the floor of a
     * room, a porch or a plaza, or out on a sidewalk, a walk or a lawn, with room to stand. Never on a roof, nor in the
     * road.
     *
     * @return where they would stand, or null if nowhere would do this time
     */
    public @Nullable BlockPos spawnSpot(ServerLevel level, Vec3 near, double nearest, double furthest, RandomSource random) {
        if (plan == null || finished.isEmpty()) {
            return null;
        }
        List<Part> parts = new ArrayList<>();
        for (Part part : plan.parts()) {
            if (!finished.contains(part.id()) || !levels.containsKey(part.id())) {
                continue;
            }
            double dx = (part.minX() + part.maxX()) / 2.0 - near.x;
            double dz = (part.minZ() + part.maxZ()) / 2.0 - near.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance >= nearest && distance <= furthest) {
                parts.add(part);
            }
        }
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS && !parts.isEmpty(); attempt++) {
            Part part = parts.get(random.nextInt(parts.size()));
            int x = part.minX() + random.nextInt(part.maxX() - part.minX() + 1);
            int z = part.minZ() + random.nextInt(part.maxZ() - part.minZ() + 1);
            int ground = levels.get(part.id());
            // down through the house from the top, to the first floor with room over it, upstairs as like as not
            for (int y = ground + SPAWN_ABOVE; y >= ground - SPAWN_BELOW; y--) {
                Built entry = built.get(BlockPos.asLong(x, y, z));
                if (entry == null || !STANDS_ON.contains(entry.role()) || !Terrain.isReady(level, x >> 4, z >> 4)) {
                    continue;
                }
                if (level.getBlockState(above.set(x, y + 1, z)).getCollisionShape(level, above).isEmpty()
                        && level.getBlockState(above.set(x, y + 2, z)).getCollisionShape(level, above).isEmpty()) {
                    return new BlockPos(x, y + 1, z);
                }
                break;
            }
        }
        return null;
    }

    /** How many of the town's blocks stand out past its wall, still to go back: for seeing how well it keeps up. */
    public int standingOutside(Vec3 hexCenter, float wall) {
        return standingOutside(hexCenter, wall, Vec3.ZERO, Double.MAX_VALUE);
    }

    /** The same, only so far from a spot: for seeing how well it keeps up where someone there could see. */
    public int standingOutside(Vec3 hexCenter, float wall, Vec3 near, double within) {
        int outside = 0;
        double withinSq = within == Double.MAX_VALUE ? Double.MAX_VALUE : within * within;
        for (long key : built.keySet()) {
            double dx = BlockPos.getX(key) + 0.5 - near.x;
            double dz = BlockPos.getZ(key) + 0.5 - near.z;
            if ((withinSq == Double.MAX_VALUE || dx * dx + dz * dz <= withinSq) && reachOf(hexCenter, key) > wall + 0.5F) {
                outside++;
            }
        }
        return outside;
    }

    /**
     * The middle of every lot begun that isn't a house, by what it is: what it was zoned for, or what the town made of
     * land that wouldn't take that. For seeing how a town turned out.
     */
    public java.util.Map<String, List<BlockPos>> lotsByUse() {
        java.util.Map<String, List<BlockPos>> found = new java.util.TreeMap<>();
        if (plan == null) {
            return found;
        }
        for (Part part : plan.parts()) {
            if (part.kind() != Kind.HOUSE || !levels.containsKey(part.id())) {
                continue;
            }
            Use use = grounds.getOrDefault(part.id(), zoning().getOrDefault(part.id(), Use.HOUSE));
            if (use != Use.HOUSE) {
                BlockPos middle = new Frame(part.origin(), part.front()).at(part.width() / 2, part.depth() / 2, levels.get(part.id()));
                found.computeIfAbsent(use.name().toLowerCase(java.util.Locale.ROOT), key -> new ArrayList<>()).add(middle);
            }
        }
        return found;
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
        homeRaised = true;
        changed = true;
        return HomeRaise.RAISED;
    }

    /**
     * Makes over what the town has built into another build while its Hex stands, its caster having chosen it on their
     * remote: everything the new build doesn't have dissolves, and what it does goes up in its place once that is all
     * down. Between a town and an orchard the home stays and all round it changes; to just the home everything else goes;
     * to nothing, everything goes; from nothing or just the home, the rest goes up round it.
     *
     * @return whether anything changes
     */
    public boolean rebuildAs(HexBuild build, BlockPos center, long now) {
        if (build == mode) {
            return false;
        }
        Part home = home(center);
        IntOpenHashSet going = new IntOpenHashSet();
        for (Part part : plan(center).parts()) {
            int id = part.id();
            boolean standing = finished.contains(id) || builds.containsKey(id) || started.containsKey(id);
            boolean kept = part.equals(home) && build != HexBuild.NOTHING;
            if (standing && !kept) {
                going.add(id);
            }
        }
        boolean homeStands = home != null && (finished.contains(home.id()) || builds.containsKey(home.id()) || started.containsKey(home.id()));
        if (!going.isEmpty()) {
            makeWay(going, now);
            makingWay = true;
        }
        if (build != HexBuild.NOTHING && !homeStands) {
            // the home rises again as its caster chose, their magic reaching out for it
            homeRaised = true;
            homeLeft = false;
        }
        skipped.clear();
        mode = build;
        changed = true;
        return true;
    }

    /** The home going up next is raised by its caster, who reaches out for it. */
    public void raisedByCaster() {
        homeRaised = true;
    }

    /**
     * Takes parts of the town back down, each of them dissolving from the top down, all at once.
     */
    private void makeWay(IntOpenHashSet ids, long now) {
        if (ids.isEmpty()) {
            return;
        }
        LongArrayList blocks = new LongArrayList();
        for (int id : ids) {
            blocks.addElements(blocks.size(), blocksOf(id));
            finished.remove(id);
            builds.remove(id);
            started.remove(id);
            forgetLevel(id);
            partial.remove(id);
            adopted.remove(id);
        }
        long[] order = byHeight(blocks.toLongArray(), false);
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
     * @param caster   whose Hex it is
     * @param aloft    the caster, while they float in the middle of their rising home
     * @param deadline     when its share of this tick runs out, in {@link System#nanoTime} nanoseconds: whatever is left
     *                     of building, taking down and making over carries on next tick
     * @param wallDeadline when its share of this tick for putting back what its wall has passed runs out, which goes
     *                     first; what is left of that, the wall waits for
     * @param leftovers    where what the wall takes back in chunks that aren't loaded goes, to be put back once they are
     */
    public void tick(ServerLevel level, Vec3 hexCenter, float wall, float radius, Era era, float eraFront, boolean falling, UUID caster,
                     @Nullable UUID aloft, long now, long deadline, long wallDeadline, Leftovers leftovers) {
        BlockPos center = BlockPos.containing(hexCenter);
        this.aloft = aloft;
        this.deadline = deadline;
        this.wallDeadline = wallDeadline;
        long begun = System.nanoTime();
        this.startDeadline = begun + Math.max(0L, deadline - begun) / 2;
        this.leftovers = leftovers;
        List<ServerPlayer> players = level.players();
        watchers = new double[players.size() * 2];
        for (int i = 0; i < players.size(); i++) {
            watchers[i * 2] = players.get(i).getX();
            watchers[i * 2 + 1] = players.get(i).getZ();
        }
        watchedReach = (level.getServer().getPlayerList().getViewDistance() + 1) * 16.0;
        watchedSq = watchedReach * watchedReach;
        float seen = seen(wall, now);
        if (falling) {
            long fallFrom = System.nanoTime();
            fall(level, hexCenter, wall, now);
            STAGE_NANOS[CUTTING] += System.nanoTime() - fallFrom;
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
            for (int id : byPart.keySet()) {
                if (part(center, id) == null) {
                    orphaned.add(id);
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
        long from = System.nanoTime();
        cutBack(level, hexCenter, seen, now);
        int budget = BUDGET;
        budget = takeDown(level, budget, now);
        long cutDone = System.nanoTime();
        STAGE_NANOS[CUTTING] += cutDone - from;
        if (now % TRIGGER_INTERVAL == 0) {
            // while the wall spreads or grows, what is built needs only fit the Hex it is growing into
            float holds = Math.max(seen, radius);
            drawnIn(level, hexCenter, holds, caster, now);
            shrinkTo(level, center, hexCenter, holds, now);
            trigger(level, center, hexCenter, seen, radius, now);
        }
        if (now % RETRY_SKIPPED == 0) {
            skipped.clear();
        }
        long triggered = System.nanoTime();
        STAGE_NANOS[STARTING] += triggered - cutDone;
        budget = advance(level, center, hexCenter, radius <= seen + 0.01F ? seen : -1.0F, era, now, budget);
        long advanced = System.nanoTime();
        STAGE_NANOS[PLACING] += advanced - triggered;
        restyle(level, hexCenter, era, eraFront, now, budget);
        STAGE_NANOS[RESTYLING] += System.nanoTime() - advanced;
        sendGone(level, hexCenter, seen);
    }

    /**
     * How long all towns have spent at each part of their ticks since the game started, in nanoseconds: putting back
     * what their walls have passed, starting parts, putting blocks down and restyling; and how many lots they surveyed
     * and how long that took. For seeing where the time goes.
     */
    public static long[] stageNanos() {
        return STAGE_NANOS.clone();
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
     * As the wall moves in, whatever of the town it passes goes back to what stood there before, as the wall goes by: from
     * the outside in, each block glitching out in red as it goes. The wall never waits for it: what it has passed goes
     * back a chunk at a time, nearest anyone first, however far behind that falls, and what stands where no one can see
     * it goes back a moment later with what towns gone have left.
     */
    private void cutBack(ServerLevel level, Vec3 hexCenter, float wall, long now) {
        boolean closing = wall < lastSeen - 1.0E-4F;
        boolean opening = wall > lastSeen + 1.0E-4F;
        if (!lookedOutside) {
            // loaded again: whatever the wall had gone by and hadn't yet gone back is looked for once, as if it had moved
            lookedOutside = true;
            closing = true;
            opening = false;
        }
        lastSeen = wall;
        if (closing) {
            lastClosing = now;
        }
        LongPredicate passed = other -> built.containsKey(other) && reachOf(hexCenter, other) > wall;
        // what the wall has passed already goes first
        boolean any = putBackDue(level, hexCenter, wall, false, passed);
        if (opening && cut != null) {
            // grown back out: what it had gone by and now stands inside it again stays, and the town is looked over afresh
            // the next time it is drawn in
            cut = null;
            cutFar = null;
            cutFresh.clear();
        }
        if (cut == null && !closing || built.isEmpty()) {
            if (any && !kept.isEmpty()) {
                putBackKept(level, true, hexCenter, wall + 0.5F, null);
            }
            return;
        }
        int inner = Sweep.ring(wall);
        boolean caughtUp = cut == null || cut.caughtUp(inner, false) && cutFar.caughtUp(inner, false);
        if (!closing && now - lastClosing > CUT_LINGER && caughtUp) {
            cut = null;
            cutFar = null;
            cutFresh.clear();
            return;
        }
        if (cut == null) {
            // sorted once, however far it is drawn in, and kept up with what goes up meanwhile; what players can see
            // apart from the rest
            Sweep[] sweeps = Sweep.outsideIn(built, hexCenter, this::watched);
            cut = sweeps[0];
            cutFar = sweeps[1];
            cutFresh.clear();
        }
        // a ring at a time, those outside the wall's own: within one the sweep keeps no order. What players can see goes
        // back with the time there is, and only then is the rest handed over
        sweepOut(level, cut, inner, false);
        any |= putBackDue(level, hexCenter, wall, false, passed);
        sweepOut(level, cutFar, inner, false);
        // and whatever has gone up since the sweep was sorted, once the wall has passed it too
        for (int k = cutFresh.size() - 1; k >= 0 && !wallOutOfTime(); k--) {
            long key = cutFresh.getLong(k);
            Built entry = built.get(key);
            if (entry != null && reachOf(hexCenter, key) <= wall) {
                continue;
            }
            cutFresh.set(k, cutFresh.getLong(cutFresh.size() - 1));
            cutFresh.removeLong(cutFresh.size() - 1);
            if (entry != null) {
                pass(level, key, entry);
            }
        }
        if (any && !kept.isEmpty()) {
            putBackKept(level, true, hexCenter, wall + 0.5F, null);
        }
    }

    /**
     * Hands over what a sweep has reached out past the wall, from the outside in, for as long as there is time this tick.
     *
     * @param inner     the ring the wall stands in
     * @param inclusive whether what lies in the wall's own ring goes too
     */
    private void sweepOut(ServerLevel level, Sweep sweep, int inner, boolean inclusive) {
        while (sweep.cursor < sweep.order.length && !wallOutOfTime()) {
            int ring = Sweep.ring(sweep.reach[sweep.cursor]);
            if (inclusive ? ring < inner : ring <= inner) {
                break;
            }
            long key = sweep.order[sweep.cursor++];
            Built entry = built.get(key);
            if (entry != null) {
                pass(level, key, entry);
            }
        }
    }

    /**
     * A block of the town the wall has just gone by: where players can see, due to go back right away; out past that, or
     * where its land isn't loaded, to go back a moment later with what towns gone have left, nearest anyone first.
     */
    private void pass(ServerLevel level, long key, Built entry) {
        if (leftovers != null && (!watched(key) || !Terrain.canPutBack(level, key))) {
            removeBuilt(key);
            leftovers.leave(key, entry);
            changed = true;
            return;
        }
        long chunk = ChunkPos.pack(BlockPos.getX(key) >> 4, BlockPos.getZ(key) >> 4);
        LongArrayList keys = due.get(chunk);
        if (keys == null) {
            keys = new LongArrayList();
            due.put(chunk, keys);
        }
        keys.add(key);
    }

    /**
     * Puts back what the wall has gone by, a chunk at a time, nearest anyone first, for as long as there is time this
     * tick: the blocks of a chunk found together go back many times faster than the same scattered round the wall. What
     * the wall has grown back out over since stays.
     *
     * @param inclusive whether what stands in the wall's own ring goes too, as when the Hex falls
     * @return whether anything went back
     */
    private boolean putBackDue(ServerLevel level, Vec3 hexCenter, float wall, boolean inclusive, LongPredicate goingBack) {
        if (due.isEmpty()) {
            return false;
        }
        long[] chunks = due.keySet().toLongArray();
        long[] order = new long[chunks.length];
        for (int i = 0; i < chunks.length; i++) {
            order[i] = (long) nearestSq(chunks[i]) << 32 | i;
        }
        Arrays.sort(order);
        int inner = Sweep.ring(wall);
        boolean any = false;
        for (int k = 0; k < order.length && !wallOutOfTime(); k++) {
            long chunk = chunks[(int) order[k]];
            LongArrayList keys = due.get(chunk);
            while (!keys.isEmpty() && !wallOutOfTime()) {
                long key = keys.removeLong(keys.size() - 1);
                Built entry = built.get(key);
                if (entry == null) {
                    continue;
                }
                int ring = Sweep.ring(reachOf(hexCenter, key));
                if (inclusive ? ring < inner : ring <= inner) {
                    continue;
                }
                if (!Terrain.canPutBack(level, key)) {
                    if (leftovers != null) {
                        removeBuilt(key);
                        leftovers.leave(key, entry);
                        changed = true;
                    }
                    continue;
                }
                if (restore(level, key, entry, goingBack)) {
                    gone(key);
                    any = true;
                }
            }
            if (keys.isEmpty()) {
                due.remove(chunk);
            }
        }
        return any;
    }

    /** How far a chunk's middle is from the nearest player, squared, in blocks: as far as can be with none about. */
    private int nearestSq(long chunk) {
        double x = (ChunkPos.getX(chunk) << 4) + 8.0;
        double z = (ChunkPos.getZ(chunk) << 4) + 8.0;
        double nearest = Double.MAX_VALUE;
        for (int i = 0; i < watchers.length; i += 2) {
            double dx = x - watchers[i];
            double dz = z - watchers[i + 1];
            nearest = Math.min(nearest, dx * dx + dz * dz);
        }
        return (int) Math.min(Integer.MAX_VALUE, nearest);
    }

    /** Whether anyone is near enough a part to see any of it. */
    private boolean watched(Part part) {
        double x = (part.minX() + part.maxX() + 1) * 0.5;
        double z = (part.minZ() + part.maxZ() + 1) * 0.5;
        // half its width and depth together, never less than half across it from corner to corner
        double reach = watchedReach + (part.maxX() - part.minX() + part.maxZ() - part.minZ() + 2) * 0.5;
        double reachSq = reach * reach;
        for (int i = 0; i < watchers.length; i += 2) {
            double dx = x - watchers[i];
            double dz = z - watchers[i + 1];
            if (dx * dx + dz * dz <= reachSq) {
                return true;
            }
        }
        return false;
    }

    /** Whether anyone is near enough a block to see it, within the distance the server sends them the world to. */
    private boolean watched(long key) {
        double x = BlockPos.getX(key) + 0.5;
        double z = BlockPos.getZ(key) + 0.5;
        for (int i = 0; i < watchers.length; i += 2) {
            double dx = x - watchers[i];
            double dz = z - watchers[i + 1];
            if (dx * dx + dz * dz <= watchedSq) {
                return true;
            }
        }
        return false;
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
        // whether each chunk is loaded, as found this time round: neighboring parts share them
        Long2BooleanOpenHashMap loaded = new Long2BooleanOpenHashMap();
        // what players can see goes up first, nearest the middle first, and then the rest
        List<Part> parts = plan(center).parts();
        List<Part> order = new ArrayList<>(parts.size());
        List<Part> unseen = new ArrayList<>();
        for (Part part : parts) {
            (watched(part) ? order : unseen).add(part);
        }
        order.addAll(unseen);
        for (Part part : order) {
            if (System.nanoTime() > startDeadline) {
                // the rest wait for the next time there is time for them
                return;
            }
            int id = part.id();
            if (finished.contains(id) || started.containsKey(id) || builds.containsKey(id)) {
                if (finished.contains(id) && partial.containsKey(id) && !adopted.contains(id) && wall > partial.get(id) + 1.0F
                        && (settled || TownPlan.fits(part, hexCenter, wall, center.getY())) && isLoaded(level, part, loaded)) {
                    // the wall has moved out past a garden or a street's end: build it out to where the wall is now, as
                    // soon as the wall has passed all of it, or wherever the wall comes to rest
                    float clip = TownPlan.fits(part, hexCenter, wall, center.getY()) ? WHOLE : wall;
                    putBackLeftovers(level, part);
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
                // the caster's home goes up whole or not at all, never cut off at the wall
                if (!settled || part.kind() == Kind.HOME || untouchedAt.get(id) == wall) {
                    continue;
                }
                if (!TownPlan.touches(part, hexCenter, wall, center.getY())) {
                    // looked at once for wherever the wall stands
                    untouchedAt.put(id, wall);
                    continue;
                }
                clip = wall;
            }
            if (!isLoaded(level, part, loaded)) {
                fetch(level, part, now);
                continue;
            }
            fetching.remove(id);
            if (part.kind() == Kind.HOUSE && clip == WHOLE) {
                // a lot waits for its street to be worked out, to be laid near its height
                Part street = frontStreet(center, part);
                if (street != null && laidStretch(street) == null && !isLoaded(level, street, loaded)) {
                    fetch(level, street, now);
                    continue;
                }
            }
            putBackLeftovers(level, part);
            Survey survey = survey(level, part, hexCenter, clip);
            if (survey == null && part.kind() == Kind.HOUSE && !grounds.containsKey(id)) {
                // land a house won't go on is still the town's, laid out as what suits it, or failing that a garden, as
                // much of it as will take one
                Use suits = groundsFor(level, hexCenter, part);
                grounds.put(id, suits);
                survey = survey(level, part, hexCenter, clip);
                if (survey == null && suits != Use.GARDEN) {
                    grounds.put(id, Use.GARDEN);
                    survey = survey(level, part, hexCenter, clip);
                }
                if (survey == null) {
                    grounds.remove(id);
                } else {
                    changed = true;
                }
            }
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
                : blueprint(part, survey, clip != WHOLE, occupied, other -> levelOf(level, hexCenter, other));
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
        for (long key : blocksOf(part.id())) {
            Built block = built.get(key);
            if (block != null && block.role() != Role.CLEAR && block.role() != Role.PRESERVE && !planned.contains(key)) {
                clearing.add(new Piece(BlockPos.of(key), Role.CLEAR, 0, AIR, 0, 0));
            }
        }
        int top = survey.ground();
        boolean pond = clip == WHOLE && grounds.get(part.id()) == Use.POND;
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
                    // beside a building that stays, its garden's water stays too, and a pond's water is the pond
                    if (state.isAir() || built.containsKey(key) || standing.contains(key)
                            || (occupied[index] || pond) && !state.getFluidState().isEmpty()) {
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
        if (survey.profile() != null) {
            ends.put(part.id(), pack(survey.profile().from(), survey.profile().to()));
        }
        if (clip != WHOLE) {
            partial.put(part.id(), clip);
        } else {
            partial.remove(part.id());
        }
        changed = true;
        boolean ownHome = part.kind() == Kind.HOME && part.layer() == 0;
        boolean raisedByCaster = ownHome && (founding || homeRaised);
        if (ownHome) {
            // up again, for the wall to let go of again
            homeRaised = false;
            homeLeft = false;
            homeReach = -1.0F;
        }
        TownEvents.started(level, part, survey.ground(), now, duration, top, clearTicks, raisedByCaster);
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
    private void resume(ServerLevel level, BlockPos center, Vec3 hexCenter, long now) {
        if (started.isEmpty() || started.size() == builds.size()) {
            return;
        }
        for (int id : started.keySet().toIntArray()) {
            if (builds.containsKey(id)) {
                continue;
            }
            Part part = part(center, id);
            if (part == null) {
                continue;
            }
            if (!isLoaded(level, part)) {
                fetch(level, part, now);
                continue;
            }
            fetching.remove(id);
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
            Blueprint blueprint = blueprint(part, leveled, clip != WHOLE, new boolean[leveled.columns().length],
                    other -> levelOf(level, hexCenter, other));
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
        resume(level, center, hexCenter, now);
        if (now % KEEP_LOADED_INTERVAL == 0) {
            // what builds where no one is stays loaded until it is done
            for (Build build : builds.values()) {
                keepLoaded(level, build.part);
            }
        }
        Iterator<Build> iterator = builds.values().iterator();
        while (iterator.hasNext() && budget > 0 && !outOfTime()) {
            Build build = iterator.next();
            long elapsed = now - build.start;
            if (build.part.kind() == Kind.HOME) {
                announce(level, build, elapsed, era);
            }
            boolean looked = false;
            while (build.next < build.pieces.size() && budget > 0 && !outOfTime()) {
                Piece piece = build.pieces.get(build.next);
                if (piece.tick() > elapsed) {
                    break;
                }
                if (!looked) {
                    // whether anyone is about to be moved out of the way, looked for once, not for every block put down
                    anyoneThere = anyoneIn(level, build);
                    looked = true;
                }
                build.next++;
                if (wall >= 0.0F && !HexShape.contains(hexCenter, wall, Vec3.atCenterOf(piece.pos()))) {
                    // the wall has moved in past it since this began
                    continue;
                }
                if (place(level, build, piece, era)) {
                    budget--;
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
            if (Terrain.isReady(level, BlockPos.containing(car.at())) && level.getEntitiesOfClass(ParkedCar.class, spot).isEmpty()) {
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
        if (!Terrain.isReady(level, pos)) {
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
        take(level, pos, current, piece, build.part.id(), existing);
        level.setBlock(pos, shaped, BUILT_FLAGS);
        placedEver++;
        if (piece.role() == Role.SIGN && level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            sign.setText(Signs.text(piece.paint()), SignTextSlot.FRONT);
            sign.setChanged();
            level.sendBlockUpdated(pos, shaped, shaped, Block.UPDATE_CLIENTS);
        }
        if (anyoneThere) {
            nudge(level, build, pos, shaped, aloft);
        }
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
            putBuilt(key, new Built(existing.original(), Role.CLEAR, 0, part, existing.data()));
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
        putBuilt(key, new Built(HexPaint.unpainted(level, pos, current), Role.CLEAR, 0, part, data));
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
        putBuilt(key, new Built(HexPaint.unpainted(level, pos, current), piece.role(), piece.paint(), build.part.id()));
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
            if (!Terrain.isReady(level, pos)) {
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
                        || Math.abs(next.getZ() - trunk.getZ()) > LEAF_REACH || !seen.add(next.asLong()) || !Terrain.isReady(level, next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (!state.hasProperty(LeavesBlock.PERSISTENT) || state.getValue(LeavesBlock.PERSISTENT)) {
                    continue;
                }
                queue.add(next);
                if (!built.containsKey(next.asLong())) {
                    putBuilt(next.asLong(), new Built(state, Role.PRESERVE, 0, part));
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
            boolean loaded = Terrain.isReady(level, BlockPos.containing(entry.at()));
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
     * Makes a spot a piece's, about to be put down there: remembering what stands there now if the town hasn't touched
     * it yet, or else taking on the piece's part and role, as the door its caster steps out of is found by them, and
     * still remembering what first stood there and anything kept in it.
     */
    private void take(ServerLevel level, BlockPos pos, BlockState now, Piece piece, int part) {
        take(level, pos, now, piece, part, built.get(pos.asLong()));
    }

    /**
     * @param existing what the town already has there, as just looked up
     */
    private void take(ServerLevel level, BlockPos pos, BlockState now, Piece piece, int part, @Nullable Built existing) {
        if (existing == null) {
            record(level, pos, now, piece, part);
        } else if (existing.part() != part || existing.role() != piece.role() || existing.paint() != piece.paint()) {
            putBuilt(pos.asLong(), new Built(existing.original(), piece.role(), piece.paint(), part, existing.data()));
        }
    }

    /**
     * Remembers what stood somewhere before the town built there. A plant two blocks tall is remembered whole, since
     * taking either half takes both.
     */
    private void record(ServerLevel level, BlockPos pos, BlockState original, Piece piece, int part) {
        putBuilt(pos.asLong(), new Built(HexPaint.unpainted(level, pos, original), piece.role(), piece.paint(), part));
        if (original.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            BlockPos other = original.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            long otherKey = other.asLong();
            if (!built.containsKey(otherKey)) {
                putBuilt(otherKey, new Built(level.getBlockState(other), Role.CLEAR, 0, part));
            }
        }
    }

    /** Makes a spot the town's, or changes what it is to the town, keeping {@link #byPart} in step. */
    private void putBuilt(long key, Built entry) {
        Built old = built.put(key, entry);
        if (old == null) {
            index(key, entry.part());
            if (cut != null) {
                cutFresh.add(key);
            }
        } else if (old.part() != entry.part()) {
            unindex(key, old.part());
            index(key, entry.part());
        }
    }

    private @Nullable Built removeBuilt(long key) {
        Built old = built.remove(key);
        if (old != null) {
            unindex(key, old.part());
        }
        return old;
    }

    private void index(long key, int part) {
        LongOpenHashSet keys = byPart.get(part);
        if (keys == null) {
            keys = new LongOpenHashSet();
            byPart.put(part, keys);
        }
        keys.add(key);
    }

    private void unindex(long key, int part) {
        LongOpenHashSet keys = byPart.get(part);
        if (keys != null && keys.remove(key) && keys.isEmpty()) {
            byPart.remove(part);
        }
    }

    /** Where a part's blocks are, as a copy that changing them leaves alone. */
    private long[] blocksOf(int part) {
        LongOpenHashSet keys = byPart.get(part);
        return keys == null ? new long[0] : keys.toLongArray();
    }

    /** Whether this tick's share of the server's time for the town has run out. */
    private boolean outOfTime() {
        return System.nanoTime() > deadline;
    }

    private boolean wallOutOfTime() {
        return System.nanoTime() > wallDeadline;
    }

    /**
     * Asks for the land of a part out where no one is to be loaded, for it to be built as the Hex grows out over it
     * rather than once someone goes there: a few parts at a time, the rest waiting their turn.
     */
    private void fetch(ServerLevel level, Part part, long now) {
        long asked = fetching.getOrDefault(part.id(), Long.MIN_VALUE);
        if (asked == Long.MIN_VALUE) {
            // making new land is hard work for the server: only so much at once, and only while it has time to spare
            if (fetching.size() >= MAX_FETCHING || !Hexes.hasRoomToLoad()) {
                return;
            }
            fetching.put(part.id(), now);
        } else if (now - asked > FETCH_PATIENCE) {
            // its land never came; another's turn, and its own again later
            fetching.remove(part.id());
            return;
        }
        keepLoaded(level, part);
    }

    /**
     * Keeps a part's land loaded a moment longer: while it is fetched, or while it builds where no one is to keep it
     * loaded.
     */
    private static void keepLoaded(ServerLevel level, Part part) {
        // a chunk round it too, for what is put down at its edge to read and update its neighbors there
        for (int cx = (part.minX() >> 4) - 1; cx <= (part.maxX() >> 4) + 1; cx++) {
            for (int cz = (part.minZ() >> 4) - 1; cz <= (part.maxZ() >> 4) + 1; cz++) {
                level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_SPAWN, new ChunkPos(cx, cz), 0);
            }
        }
    }

    /** Puts back first whatever a town left on a part's ground where it couldn't then, for the part to go up on the land. */
    private void putBackLeftovers(ServerLevel level, Part part) {
        if (leftovers != null) {
            leftovers.putBackWithin(level, part.minX(), part.minZ(), part.maxX(), part.maxZ());
        }
    }

    /**
     * Moves anyone caught inside a new block: up onto it near the ground, otherwise out onto the lawn in front. A caster
     * held aloft while their home rises is left be, as it builds itself around them.
     */
    /** Whether anyone stands about a part as it builds, for what it puts down to move them out of its way. */
    private static boolean anyoneIn(ServerLevel level, Build build) {
        Part part = build.part;
        AABB around = new AABB(part.minX() - 1, build.ground - 12, part.minZ() - 1, part.maxX() + 2, build.ground + 48, part.maxZ() + 2);
        return !level.getEntitiesOfClass(LivingEntity.class, around).isEmpty();
    }

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
     * garden once the wall settles; a street, the square or a garden just ends at the wall. The caster's home is let go
     * of instead, to stand alone (see {@link #drawnIn}).
     */
    private void shrinkTo(ServerLevel level, BlockPos center, Vec3 hexCenter, float radius, long now) {
        // only a Hex drawn in leaves anything not fitting it
        boolean shrunk = radius < shrunkTo;
        shrunkTo = radius;
        if (!shrunk || finished.isEmpty() && builds.isEmpty()) {
            return;
        }
        for (Part part : plan(center).parts()) {
            int id = part.id();
            boolean standing = finished.contains(id) || builds.containsKey(id) || started.containsKey(id);
            if (!standing || part.kind() == Kind.HOME) {
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
            forgetLevel(id);
            partial.remove(id);
            adopted.remove(id);
            // from the wall inward, over a moment
            Sweep order = Sweep.sorted(blocksOf(id), hexCenter, false, 0.0F);
            for (int k = 0; k < order.order.length; k++) {
                takedown.add(new long[] {order.order[k], now + (long) TAKEDOWN_TICKS * k / Math.max(1, order.order.length)});
            }
            changed = true;
        }
    }

    private int takeDown(ServerLevel level, int budget, long now) {
        if (takedown.isEmpty()) {
            return budget;
        }
        while (!takedown.isEmpty() && budget > 0 && takedown.peek()[1] <= now && !outOfTime()) {
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
     * Brings the town down behind the wall as it rushes in, never holding it up. A town too big to keep up with the wall
     * goes on coming down a moment after it has gone in, rather than all at once.
     */
    private void fall(ServerLevel level, Vec3 hexCenter, float wall, long now) {
        builds.clear();
        started.clear();
        if (collapse == null) {
            Sweep[] sweeps = Sweep.outsideIn(built, hexCenter, this::watched);
            collapse = sweeps[0];
            collapseFar = sweeps[1];
        }
        // a ring at a time, the wall's own with it: within one the sweep keeps no order. What players can see goes back
        // with the time there is, and only then is the rest handed over
        int last = Sweep.ring(wall);
        sweepOut(level, collapse, last, true);
        putBackDue(level, hexCenter, wall, true, built::containsKey);
        sweepOut(level, collapseFar, last, true);
        if (!kept.isEmpty() && wall > 0.0F) {
            putBackKept(level, true, hexCenter, wall + 2.0F, null);
        }
    }

    /**
     * Whether the falling wall has taken back everything of the town it has passed, as far as loaded chunks go, for the
     * Hex to be gone: what lies in the rest goes back when they are loaded.
     */
    public boolean hasFallen() {
        return collapse != null && collapse.cursor >= collapse.order.length && collapseFar.cursor >= collapseFar.order.length && due.isEmpty();
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
            for (long key : blocksOf(id)) {
                homeReach = Math.max(homeReach, reachOf(hexCenter, key));
            }
        }
        if (wall > homeReach + 1.0F) {
            return null;
        }
        return release(level, hexCenter, home, caster, now);
    }

    /**
     * As the wall is drawn in over the caster's home, lets it go the way the falling wall does, rather than taking it
     * down with the houses it cuts into: it holds on alone, glitching through the eras, then goes the way it went up,
     * backward. It goes up again once the Hex is grown back over all of it.
     *
     * @param radius how far the wall holds now
     */
    private void drawnIn(ServerLevel level, Vec3 hexCenter, float radius, UUID caster, long now) {
        if (homeLeft || released != null || mode == HexBuild.NOTHING) {
            return;
        }
        BlockPos center = BlockPos.containing(hexCenter);
        Part home = home(center);
        if (home == null) {
            return;
        }
        int id = home.id();
        boolean standing = finished.contains(id) || builds.containsKey(id) || started.containsKey(id);
        if (standing && !TownPlan.fits(home, hexCenter, radius, levels.getOrDefault(id, center.getY()))) {
            released = release(level, hexCenter, home, caster, now);
        }
    }

    /**
     * The home the wall let go of as it was drawn in, once.
     */
    public @Nullable HomeRemnant takeReleased() {
        HomeRemnant left = released;
        released = null;
        return left;
    }

    /**
     * Hands the caster's home over to stand on alone: its blocks, and what was taken up from its lot, to go the way they
     * went up, backward.
     */
    private @Nullable HomeRemnant release(ServerLevel level, Vec3 hexCenter, Part home, UUID caster, long now) {
        BlockPos center = BlockPos.containing(hexCenter);
        int id = home.id();
        homeLeft = true;
        Long2ObjectOpenHashMap<Built> blocks = new Long2ObjectOpenHashMap<>();
        for (long key : blocksOf(id)) {
            Built entry = removeBuilt(key);
            if (entry != null) {
                blocks.put(key, entry);
            }
        }
        if (blocks.isEmpty()) {
            return null;
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
        forgetLevel(id);
        partial.remove(id);
        changed = true;
        // the way it went up, to go the same way backward; a home made of what stood there can't be worked out again,
        // and goes by what each of its blocks is for
        Long2IntOpenHashMap builtAt = new Long2IntOpenHashMap();
        builtAt.defaultReturnValue(-1);
        int duration = HomeRemnant.BUILD_TICKS;
        if (!madeOver) {
            Blueprint blueprint = blueprint(home, Survey.flat(ground, home), false, new boolean[home.width() * home.depth()],
                    other -> OptionalInt.empty());
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
        // bottom up, so plants come back onto their ground
        for (long key : byHeight(built.keySet().toLongArray(), true)) {
            Built entry = built.get(key);
            if (entry == null) {
                continue;
            }
            if (!Terrain.isReady(level, BlockPos.of(key))) {
                removeBuilt(key);
                unloaded.accept(key, entry);
                continue;
            }
            restore(level, key, entry, built::containsKey);
        }
        built.clear();
        byPart.clear();
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
        return restore(level, key, entry, NONE_GOING_BACK);
    }

    /**
     * @param goingBack which of its neighbors go back themselves in a moment, and take it in then: they aren't told of
     *                  it now
     */
    private boolean restore(ServerLevel level, long key, Built entry, LongPredicate goingBack) {
        BlockPos pos = BlockPos.of(key);
        if (!Terrain.isReady(level, pos)) {
            if (leftovers != null) {
                // passed where no one is: it goes back as soon as its chunk is loaded again, rather than staying the
                // town's outside its wall
                removeBuilt(key);
                leftovers.leave(key, entry);
                changed = true;
            }
            return false;
        }
        removeBuilt(key);
        changed = true;
        BlockState current = level.getBlockState(pos);
        if (!isStill(entry, current) && !HexPaint.paintedOver(level, pos, current)) {
            return false;
        }
        boolean moved = putBack(level, pos, current, entry, goingBack);
        // the other half of a tall plant, a door or a bed comes back with it
        if (entry.original().hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            BlockPos other = entry.original().getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            Built pair = removeBuilt(other.asLong());
            if (pair != null) {
                BlockState otherNow = level.getBlockState(other);
                if (isStill(pair, otherNow)) {
                    putBack(level, other, otherNow, pair, goingBack);
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
    public static boolean putBackLeftover(ServerLevel level, long key, Built entry, LongPredicate goingBack) {
        BlockPos pos = BlockPos.of(key);
        if (!Terrain.isReady(level, pos)) {
            return false;
        }
        BlockState current = level.getBlockState(pos);
        if (isStill(entry, current) || HexPaint.paintedOver(level, pos, current)) {
            putBack(level, pos, current, entry, goingBack);
        }
        return true;
    }

    /**
     * Puts back what stood at a spot, and whatever was kept in it.
     */
    static boolean putBack(ServerLevel level, BlockPos pos, BlockState current, Built entry) {
        return putBack(level, pos, current, entry, NONE_GOING_BACK);
    }

    private static boolean putBack(ServerLevel level, BlockPos pos, BlockState current, Built entry, LongPredicate goingBack) {
        BlockState original = entry.original();
        CompoundTag data = entry.data();
        if (current == original && data == null) {
            return false;
        }
        level.setBlock(pos, original, QUIET_FLAGS);
        putBackEver++;
        if (data != null) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity != null) {
                entity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), data));
                entity.setChanged();
                level.sendBlockUpdated(pos, original, original, Block.UPDATE_CLIENTS);
            }
        }
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        for (Direction direction : SHAPE_ORDER) {
            neighbor.setWithOffset(pos, direction);
            // air takes no shape from what is beside it, and what goes back itself in a moment takes it in then
            if (!level.getBlockState(neighbor).isAir() && !goingBack.test(neighbor.asLong())) {
                level.neighborShapeChanged(direction.getOpposite(), neighbor, pos, original, BUILT_FLAGS, SHAPE_UPDATE_LIMIT);
            }
        }
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
        while (sweep.cursor < sweep.order.length && budget > 0 && !outOfTime()) {
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
            if (!Terrain.isReady(level, pos)) {
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
            while (sweep.reshaped < sweep.touched.size() && !outOfTime()) {
                BlockPos pos = BlockPos.of(sweep.touched.getLong(sweep.reshaped++));
                if (!Terrain.isReady(level, pos)) {
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
            if (sweep.reshaped >= sweep.touched.size()) {
                builtEra = era;
                restyle = null;
                changed = true;
            }
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
        for (long key : blocksOf(id)) {
            Built block = built.get(key);
            if (block == null || block.role() == Role.PRESERVE) {
                continue;
            }
            BlockPos pos = BlockPos.of(key);
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
                positions.add(key);
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

    /**
     * Where the road past one end of a stretch of street is laid, for the stretch to climb or step down to: a
     * crossing's, which stays level itself, or another stretch's laid higher, which leaves the climbing to the lower one.
     * Otherwise the stretch's own ground.
     *
     * @param a the column past that end, along the stretch
     */
    private int meet(Part part, Frame frame, int a, int ground, java.util.function.Function<Part, OptionalInt> levelOf) {
        if (plan == null) {
            return ground;
        }
        BlockPos past = frame.at(a, TownPlan.STREET_WIDTH / 2, 0);
        for (Part other : plan.parts()) {
            if (other.id() == part.id() || !other.kind().isStreet() || past.getX() < other.minX() || past.getX() > other.maxX()
                    || past.getZ() < other.minZ() || past.getZ() > other.maxZ()) {
                continue;
            }
            OptionalInt laid = levelOf.apply(other);
            if (laid.isEmpty()) {
                return ground;
            }
            return other.kind() == Kind.CROSSING || laid.getAsInt() > ground ? laid.getAsInt() : ground;
        }
        return ground;
    }

    /**
     * Where a part of the plan is laid: as it was begun, or else as it would be begun now.
     */
    private OptionalInt levelOf(ServerLevel level, Vec3 hexCenter, Part part) {
        if (levels.containsKey(part.id())) {
            return OptionalInt.of(levels.get(part.id()));
        }
        if (!isLoaded(level, part)) {
            return OptionalInt.empty();
        }
        Survey survey = survey(level, part, hexCenter, WHOLE);
        return survey == null ? OptionalInt.empty() : OptionalInt.of(survey.ground());
    }

    /**
     * Whether a part's land is loaded, and the land a chunk round it: a block put down at its edge reads and updates its
     * neighbors, and one of them in a chunk not wholly loaded holds the server up until that chunk is made.
     */
    private boolean isLoaded(ServerLevel level, Part part) {
        return isLoaded(level, part, null);
    }

    /**
     * @param loaded whether each chunk is loaded, as already found, and filled in with what is found now
     */
    private static boolean isLoaded(ServerLevel level, Part part, @Nullable Long2BooleanOpenHashMap loaded) {
        for (int cx = (part.minX() >> 4) - 1; cx <= (part.maxX() >> 4) + 1; cx++) {
            for (int cz = (part.minZ() >> 4) - 1; cz <= (part.maxZ() >> 4) + 1; cz++) {
                boolean ready;
                if (loaded == null) {
                    ready = Terrain.isReady(level, cx, cz);
                } else {
                    long chunk = ChunkPos.pack(cx, cz);
                    if (loaded.containsKey(chunk)) {
                        ready = loaded.get(chunk);
                    } else {
                        ready = Terrain.isReady(level, cx, cz);
                        loaded.put(chunk, ready);
                    }
                }
                if (!ready) {
                    return false;
                }
            }
        }
        return true;
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
        long from = System.nanoTime();
        Survey survey = surveyed(level, part, hexCenter, clip);
        STAGE_NANOS[SURVEYS]++;
        STAGE_NANOS[SURVEYING] += System.nanoTime() - from;
        return survey;
    }

    private @Nullable Survey surveyed(ServerLevel level, Part part, Vec3 hexCenter, float clip) {
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
        boolean road = graded(part) || part.kind() == Kind.CROSSING;
        Use suits = clip == WHOLE ? grounds.get(part.id()) : null;
        boolean forgiving = part.kind().isStreet() || clip != WHOLE || suits != null;
        if (count == 0 && !road) {
            return null;
        }
        int[] sorted = Arrays.copyOf(land, count);
        Arrays.sort(sorted);
        int ground = count == 0 ? BlockPos.containing(hexCenter).getY() : sorted[count / 2];
        boolean[] blocked = new boolean[columns.length];
        if (road) {
            // laid along the land, over a drop or water and through a hill: only what can't be broken is left alone
            int unbreakable = 0;
            for (int index = 0; index < columns.length; index++) {
                Terrain.Column column = columns[index];
                blocked[index] = column == null || column.untouchable();
                unbreakable += column != null && column.untouchable() ? 1 : 0;
            }
            if (inside == 0 || unbreakable * 2 > inside) {
                return null;
            }
            Streets.Profile profile = graded(part) ? profile(level, hexCenter, part, columns) : crossingProfile(level, hexCenter, part, columns);
            return new Survey(graded(part) ? ground : profile.levels()[0], columns, blocked, width, profile, null);
        }
        if (suits == Use.TERRACES) {
            // laid up the slope as it goes: only what can't be broken, or lies under deep water, is left alone
            int left = 0;
            for (int index = 0; index < columns.length; index++) {
                Terrain.Column column = columns[index];
                blocked[index] = column == null || column.untouchable() || column.water() > SHALLOW;
                left += column != null && blocked[index] ? 1 : 0;
            }
            return left * 2 <= inside ? new Survey(ground, columns, blocked, width, null, sidewalk(level, hexCenter, part)) : null;
        }
        boolean pond = suits == Use.POND;
        int[] sidewalk = part.kind() == Kind.HOUSE && clip == WHOLE && suits == null ? sidewalk(level, hexCenter, part) : null;
        if (sidewalk != null) {
            // laid near its street's height, for its front to come down to the sidewalk, or up to it, in a few steps:
            // anything but a house within a step of it. Where the land won't have that, as it was
            int at = sidewalk[width / 2];
            boolean house = zoning().getOrDefault(part.id(), Use.HOUSE) == Use.HOUSE;
            int near = Math.clamp(ground, at - (house ? Houses.FRONT_DROP : 1), at + (house ? Houses.FRONT_RISE : 1));
            if (near != ground && fits(columns, width, depth, near, inside, forgiving, false, blocked)) {
                return new Survey(near, columns, blocked, width, null, sidewalk);
            }
            if (Math.abs(ground - at) > UNREACHABLE) {
                // its street goes by far over it, on a bridge, or far under it, in a cutting: nothing on it could be come
                // to, so it is laid out as a garden instead
                return null;
            }
        }
        return fits(columns, width, depth, ground, inside, forgiving, pond, blocked) ? new Survey(ground, columns, blocked, width, null, sidewalk) : null;
    }

    /**
     * Whether a part laid at a height can go on its land: a house needs every column of its lot, a garden or anything
     * the wall cuts through only most of them. A hole in a lot, a pit or a cave's mouth, is capped over like the town's
     * ground anywhere, as long as there isn't too much of it; where most of a side of the lot falls away, it is the edge
     * of a drop instead.
     *
     * @param pond    whether water is part of what goes there, kept as it is, rather than left alone
     * @param blocked filled in with the columns it leaves alone
     */
    private static boolean fits(Terrain.@Nullable Column[] columns, int width, int depth, int ground, int inside, boolean forgiving,
                                boolean pond, boolean[] blocked) {
        Arrays.fill(blocked, false);
        boolean drop = dropsAway(columns, width, depth, ground);
        int left = 0;
        int holes = 0;
        for (int index = 0; index < columns.length; index++) {
            Terrain.Column column = columns[index];
            if (column == null) {
                blocked[index] = true;
                continue;
            }
            boolean hole = !drop && falls(column, ground);
            boolean usable = hole || pond && column.water() > 0 && !column.untouchable()
                    || column.isLand(SHALLOW) && !column.untouchable() && column.surface() - ground <= MAX_CUT && ground - column.surface() <= MAX_FILL;
            holes += hole ? 1 : 0;
            if (!usable) {
                if (!forgiving) {
                    return false;
                }
                blocked[index] = true;
                left++;
            }
        }
        return left * 2 <= inside && holes * 10 <= inside * 3;
    }

    /** Whether most of a side of a part laid at a height falls away from it: the edge of a drop. */
    private static boolean dropsAway(Terrain.@Nullable Column[] columns, int width, int depth, int ground) {
        // front, back, left, right
        int[] sides = new int[4];
        for (int index = 0; index < columns.length; index++) {
            if (columns[index] != null && falls(columns[index], ground)) {
                int a = index % width;
                int b = index / width;
                sides[0] += b == 0 ? 1 : 0;
                sides[1] += b == depth - 1 ? 1 : 0;
                sides[2] += a == 0 ? 1 : 0;
                sides[3] += a == width - 1 ? 1 : 0;
            }
        }
        return sides[0] * 2 > width || sides[1] * 2 > width || sides[2] * 2 > depth || sides[3] * 2 > depth;
    }

    /**
     * What suits a lot whose land won't take what it was zoned for: a pond kept as a park where a good part of it lies
     * under water with land round it, a lookout where a side of it falls away, terraces where it climbs too steeply to
     * lay level, a ball court or a picnic grove where its street passes far over or under it, and otherwise a garden,
     * or now and then a grove. In an orchard, a pond or a meadow.
     */
    private Use groundsFor(ServerLevel level, Vec3 hexCenter, Part lot) {
        Frame frame = new Frame(lot.origin(), lot.front());
        int width = lot.width();
        int depth = lot.depth();
        Terrain.Column[] columns = new Terrain.Column[width * depth];
        int[] land = new int[columns.length];
        int count = 0;
        int wet = 0;
        for (int b = 0; b < depth; b++) {
            for (int a = 0; a < width; a++) {
                BlockPos at = frame.at(a, b, 0);
                Terrain.Column column = column(level, at.getX(), at.getZ());
                columns[b * width + a] = column;
                wet += column.water() > 0 && column.surface() != Terrain.NO_GROUND ? 1 : 0;
                if (column.isLand(SHALLOW)) {
                    land[count++] = column.surface();
                }
            }
        }
        if (wet * 5 >= columns.length && count * 4 >= columns.length) {
            return Use.POND;
        }
        if (mode == HexBuild.ORCHARD || count == 0) {
            return Use.GARDEN;
        }
        Arrays.sort(land, 0, count);
        int ground = land[count / 2];
        // a slope climbing steadily, however steep, is laid in terraces; a drop all at once is the edge of a cliff
        int[] climb = climb(columns, width, depth);
        boolean steady = climb[1] <= 2 * Grounds.TERRACE_WALL;
        if (steady && climb[0] > TERRACE_CLIMB) {
            return Use.TERRACES;
        }
        if (dropsAway(columns, width, depth, ground)) {
            return Use.LOOKOUT;
        }
        if (climb[0] > TERRACE_CLIMB) {
            return Use.TERRACES;
        }
        int[] sidewalk = sidewalk(level, hexCenter, lot);
        float pick = hash(seedOf(lot), lot.id(), 13);
        if (sidewalk != null && Math.abs(ground - sidewalk[width / 2]) > UNREACHABLE) {
            return pick < 0.5F ? Use.COURT : Use.GROVE;
        }
        return pick < 0.3F ? Use.GROVE : Use.GARDEN;
    }

    /**
     * How a lot's land climbs or falls from its front to its back, band by band, each band a terrace's rows deep: how
     * far between its highest band and its lowest, and the most from one band to the next. A band mostly over a drop
     * isn't counted, nor is the step past it.
     */
    private static int[] climb(Terrain.@Nullable Column[] columns, int width, int depth) {
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        int steepest = 0;
        int last = Integer.MIN_VALUE;
        int[] band = new int[width * Grounds.TERRACE];
        for (int from = 0; from < depth; from += Grounds.TERRACE) {
            int n = 0;
            for (int b = from; b < Math.min(depth, from + Grounds.TERRACE); b++) {
                for (int a = 0; a < width; a++) {
                    Terrain.Column column = columns[b * width + a];
                    if (column != null && column.isLand(SHALLOW)) {
                        band[n++] = column.surface();
                    }
                }
            }
            if (n * 2 < width * Math.min(Grounds.TERRACE, depth - from)) {
                last = Integer.MIN_VALUE;
                continue;
            }
            Arrays.sort(band, 0, n);
            int middle = band[n / 2];
            lowest = Math.min(lowest, middle);
            highest = Math.max(highest, middle);
            if (last != Integer.MIN_VALUE) {
                steepest = Math.max(steepest, Math.abs(middle - last));
            }
            last = middle;
        }
        return new int[] {highest < lowest ? 0 : highest - lowest, steepest};
    }

    /** The stretch of street a lot faces, if it faces one. */
    private @Nullable Part frontStreet(BlockPos center, Part lot) {
        Part street = streetAt(center, new Frame(lot.origin(), lot.front()).at(lot.width() / 2, -1, 0), lot);
        return street != null && graded(street) ? street : null;
    }

    /** Whether a column of land is a dry hole too deep under a part laid at a height to fill in: one to cap over. */
    private static boolean falls(Terrain.Column column, int ground) {
        return column.water() == 0 && !column.untouchable() && (column.surface() == Terrain.NO_GROUND || ground - column.surface() > MAX_FILL);
    }

    /**
     * How high the sidewalk past each column of a lot's front is laid: its street's, worked out with its run now if it
     * hasn't been yet, for the lot to be laid near it and its front to meet it. Null for a lot with no stretch of street
     * in front of it, or whose street's land isn't loaded.
     */
    private int @Nullable [] sidewalk(ServerLevel level, Vec3 hexCenter, Part lot) {
        Frame frame = new Frame(lot.origin(), lot.front());
        Part street = frontStreet(BlockPos.containing(hexCenter), lot);
        if (street == null) {
            return null;
        }
        int[] laid = laidStretch(street);
        if (laid == null) {
            if (!isLoaded(level, street)) {
                return null;
            }
            survey(level, street, hexCenter, WHOLE);
            laid = laidStretch(street);
            if (laid == null) {
                return null;
            }
        }
        Frame along = new Frame(street.origin(), street.front());
        Direction right = along.right();
        int[] sidewalk = new int[lot.width()];
        for (int a = 0; a < lot.width(); a++) {
            BlockPos at = frame.at(a, -1, 0);
            int column = (at.getX() - street.origin().getX()) * right.getStepX() + (at.getZ() - street.origin().getZ()) * right.getStepZ();
            sidewalk[a] = laid[Math.clamp(column, 0, laid.length - 1)];
        }
        return sidewalk;
    }

    /** Whether a part is a stretch of street, or of a lane, laid along the land rather than level. */
    private static boolean graded(Part part) {
        return part.kind() == Kind.STREET || part.kind() == Kind.CROSS_STREET;
    }

    // ---------------------------------------------------------------- streets along the land

    /**
     * How a stretch of street runs along the land, worked out with the whole run of street it is part of, from the
     * crossing before it to the crossing after it: see {@link #planRun}. Where the land stands far over it, it goes
     * through in a tunnel; where it falls far away under it, or there is water, it goes over on a bridge.
     */
    private Streets.Profile profile(ServerLevel level, Vec3 hexCenter, Part part, Terrain.@Nullable Column[] columns) {
        // worked out already, with its run, or begun by an older town
        int[] laid = laidStretch(part);
        int[] meets = new int[2];
        if (laid == null || !runMeets(hexCenter, part, laid, meets)) {
            laid = planRun(level, hexCenter, part, columns, meets);
        }
        return new Streets.Profile(laid, spans(columns, part.width(), part.depth(), laid), meets[0], meets[1]);
    }

    /**
     * How a crossing is laid: level, at the height the runs meeting it were worked out to meet it at, or else at the
     * middle height of its land, and never lower than a block over any water there.
     */
    private Streets.Profile crossingProfile(ServerLevel level, Vec3 hexCenter, Part part, Terrain.@Nullable Column[] columns) {
        int at = crossingLevel(level, hexCenter, part, columns);
        int size = part.width();
        int[] heights = new int[size];
        Arrays.fill(heights, at);
        Streets.Span[] spans = spans(columns, size, part.depth(), heights);
        // a crossing is carried one way all over, by however most of it is
        int bridges = 0;
        int tunnels = 0;
        for (Streets.Span span : spans) {
            bridges += span == Streets.Span.BRIDGE ? 1 : 0;
            tunnels += span == Streets.Span.TUNNEL ? 1 : 0;
        }
        Arrays.fill(spans, bridges * 2 > size ? Streets.Span.BRIDGE : tunnels * 2 > size ? Streets.Span.TUNNEL : Streets.Span.LAND);
        return new Streets.Profile(heights, spans, at, at);
    }

    /**
     * Where a stretch already worked out meets the road past its ends, and whether both are still as it was worked out
     * to meet them: one not begun yet is worked out again if a crossing has been laid since at another height, as one
     * from an older town may be. One already begun keeps to how it was begun.
     */
    private boolean runMeets(Vec3 hexCenter, Part part, int[] laid, int[] meets) {
        BlockPos center = BlockPos.containing(hexCenter);
        Frame frame = new Frame(part.origin(), part.front());
        boolean begun = levels.containsKey(part.id());
        boolean meeting = true;
        for (int end = 0; end < 2; end++) {
            Part next = streetAt(center, frame.at(end == 0 ? -1 : part.width(), TownPlan.STREET_WIDTH / 2, 0), part);
            int own = laid[end == 0 ? 0 : laid.length - 1];
            OptionalInt there = next == null ? OptionalInt.empty() : laidLevel(next, end == 0);
            if (there.isPresent() && Math.abs(there.getAsInt() - own) > 1) {
                meeting = false;
            }
            meets[end] = there.orElse(own);
        }
        return begun || meeting;
    }

    /**
     * How high a part of a run is laid or worked out to be, at its end facing another: a crossing's height, or the
     * column of a stretch at that end.
     *
     * @param facingEnd whether it is the far end of the part that faces the other, rather than its start
     */
    private OptionalInt laidLevel(Part part, boolean facingEnd) {
        if (part.kind() == Kind.CROSSING) {
            if (levels.containsKey(part.id())) {
                return OptionalInt.of(levels.get(part.id()));
            }
            return plannedCrossings.containsKey(part.id()) ? OptionalInt.of(plannedCrossings.get(part.id())) : OptionalInt.empty();
        }
        int[] laid = laidStretch(part);
        return laid == null ? OptionalInt.empty() : OptionalInt.of(laid[facingEnd ? laid.length - 1 : 0]);
    }

    /**
     * How high each column of a stretch is laid, if it is: worked out with its run, or, for a stretch laid by an older
     * town, evenly between where it met the road past its ends, or level.
     */
    private int @Nullable [] laidStretch(Part part) {
        int[] planned = streetLevels.get(part.id());
        if (planned != null) {
            return planned;
        }
        if (!levels.containsKey(part.id())) {
            return null;
        }
        int length = part.width();
        int[] laid = new int[length];
        if (!ends.containsKey(part.id())) {
            Arrays.fill(laid, levels.get(part.id()));
            return laid;
        }
        long packed = ends.get(part.id());
        int from = (int) (packed >> 32);
        int to = (int) packed;
        for (int a = 0; a < length; a++) {
            laid[a] = Math.round(from + (to - from) * (a + 0.5F) / length);
            if (a > 0) {
                laid[a] = Math.clamp(laid[a], laid[a - 1] - 1, laid[a - 1] + 1);
            }
        }
        return laid;
    }

    /**
     * Works out a run of street from the crossing before a stretch to the crossing after it, every stretch of it whose
     * land is loaded and the crossings either end, so each meets the next. It keeps to the even line between what is
     * laid already, or between the crossings, as long as the land is within a few blocks of it, and follows the land
     * otherwise only as far as that: a valley is crossed high on a bridge, a hill gone through in a tunnel, rather than
     * the road dipping into every hollow and climbing every rise. It climbs a block a column at most, and is laid at
     * least a block over any water.
     *
     * @param meets filled in with where the road past either end of the stretch is laid
     * @return how high each column of the stretch is laid
     */
    private int[] planRun(ServerLevel level, Vec3 hexCenter, Part part, Terrain.@Nullable Column[] columns, int[] meets) {
        BlockPos center = BlockPos.containing(hexCenter);
        List<Part> stops = run(center, part);
        // the stretch in front of the caster's home is laid level with it, the rest of its run climbing or falling to it,
        // for the home to be come to from its street wherever it rose
        Part home = home(center);
        Part homeStreet = home != null && levels.containsKey(home.id()) ? frontStreet(center, home) : null;
        // every column along the run, a crossing counting as one: how high it must be laid, if it already is or has been
        // worked out to be, and else how the land lies there
        IntArrayList fixed = new IntArrayList();
        DoubleArrayList land = new DoubleArrayList();
        IntArrayList lowest = new IntArrayList();
        int[] firstColumn = new int[stops.size()];
        boolean[] known = new boolean[stops.size()];
        for (int k = 0; k < stops.size(); k++) {
            Part stop = stops.get(k);
            firstColumn[k] = fixed.size();
            int span = stop.kind() == Kind.CROSSING ? 1 : stop.width();
            int[] laid = stop.kind() == Kind.CROSSING ? laidLevel(stop, false).stream().toArray() : laidStretch(stop);
            if (stop.kind() == Kind.CROSSING && laid.length == 0 && isLoaded(level, stop)) {
                // a crossing is worked out by itself, with the crossings round it, and the run meets it there
                laid = new int[] {crossingLevel(level, hexCenter, stop, read(level, stop))};
            }
            boolean homeFront = homeStreet != null && stop.id() == homeStreet.id();
            if (laid == null && homeFront) {
                laid = new int[span];
                Arrays.fill(laid, levels.get(home.id()));
            }
            if (laid != null && laid.length == span && (stop.id() != part.id() || homeFront)) {
                for (int y : laid) {
                    fixed.add(y);
                    land.add(Double.NaN);
                    lowest.add(Integer.MIN_VALUE);
                }
                continue;
            }
            Terrain.Column[] read = stop.id() == part.id() ? columns : isLoaded(level, stop) ? read(level, stop) : null;
            known[k] = read != null;
            for (int a = 0; a < span; a++) {
                fixed.add(Integer.MIN_VALUE);
                if (read == null) {
                    land.add(Double.NaN);
                    lowest.add(Integer.MIN_VALUE);
                } else {
                    int[] lie = lie(read, stop.width(), stop.depth(), stop.kind() == Kind.CROSSING ? -1 : a);
                    land.add(lie[0] == Integer.MIN_VALUE ? Double.NaN : lie[0]);
                    lowest.add(lie[1]);
                }
            }
        }
        int count = fixed.size();
        int[] laidAt = new int[count];
        double fallback = BlockPos.containing(hexCenter).getY();
        for (int i = count - 1; i >= 0; i--) {
            if (fixed.getInt(i) != Integer.MIN_VALUE) {
                laidAt[i] = fixed.getInt(i);
                fallback = laidAt[i];
            }
        }
        // an end of the run where nothing is laid starts, or finishes, where the land lies there
        int left = (int) Math.round(landNear(land, 0, count, fallback));
        int right = (int) Math.round(landNear(land, count - 1, -1, fallback));
        // each length of the run not laid yet, between what is laid either side of it or the ends of the run
        int i = 0;
        while (i < count) {
            if (fixed.getInt(i) != Integer.MIN_VALUE) {
                i++;
                continue;
            }
            int from = i;
            while (i < count && fixed.getInt(i) == Integer.MIN_VALUE) {
                i++;
            }
            int to = i - 1;
            int before = from > 0 ? laidAt[from - 1] : Math.max(left, lowest.getInt(from));
            int after = to < count - 1 ? laidAt[to + 1] : Math.max(right, lowest.getInt(to));
            plan(land, lowest, laidAt, from, to, before, after, from == 0, to == count - 1);
        }
        // kept for every stretch and crossing of the run whose land was read, for each to meet the next as it is begun
        int[] own = new int[part.width()];
        for (int k = 0; k < stops.size(); k++) {
            Part stop = stops.get(k);
            int first = firstColumn[k];
            if (stop.id() == part.id()) {
                own = Arrays.copyOfRange(laidAt, first, first + stop.width());
                meets[0] = first > 0 ? laidAt[first - 1] : own[0];
                meets[1] = first + stop.width() < count ? laidAt[first + stop.width()] : own[own.length - 1];
                streetLevels.put(stop.id(), own);
                changed = true;
                continue;
            }
            if (!known[k]) {
                continue;
            }
            if (stop.kind() == Kind.CROSSING) {
                plannedCrossings.put(stop.id(), laidAt[first]);
            } else {
                streetLevels.put(stop.id(), Arrays.copyOfRange(laidAt, first, first + stop.width()));
            }
            changed = true;
        }
        return own;
    }

    /**
     * Works out the columns of a run not laid yet, between what stands either side: along the even line from one to
     * the other, keeping to the land when it is near that line, smoothed, never so steep the far side can't be met, a
     * block a column at most, and over water.
     *
     * @param openStart whether nothing is laid before them, the run ending there
     */
    private static void plan(DoubleArrayList land, IntArrayList lowest, int[] out, int from, int to, int before, int after, boolean openStart,
                             boolean openEnd) {
        int n = to - from + 1;
        double[] wanted = new double[n];
        for (int j = 0; j < n; j++) {
            double even = before + (after - before) * (j + 1) / (double) (n + 1);
            double lies = land.getDouble(from + j);
            if (Double.isNaN(lies)) {
                wanted[j] = even;
                continue;
            }
            // land near the line is followed; a hill or a hollow well off it is gone through, or over, on the line
            double off = lies - even;
            wanted[j] = even + (Math.abs(off) <= FOLLOW ? off : Math.signum(off) * Math.max(0.0, 2 * FOLLOW - Math.abs(off)));
        }
        int[] laid = new int[n];
        for (int j = 0; j < n; j++) {
            double sum = 0.0;
            for (int k = j - SMOOTH; k <= j + SMOOTH; k++) {
                sum += k < 0 ? (openStart ? wanted[0] : before) : k >= n ? (openEnd ? wanted[n - 1] : after) : wanted[k];
            }
            int y = (int) Math.round(sum / (2 * SMOOTH + 1));
            // an even climb a block a column from what is laid either side can still reach it
            int low = Math.max(openStart ? Integer.MIN_VALUE : before - (j + 1), openEnd ? Integer.MIN_VALUE : after - (n - j));
            int high = Math.min(openStart ? Integer.MAX_VALUE : before + (j + 1), openEnd ? Integer.MAX_VALUE : after + (n - j));
            if (low <= high) {
                y = Math.clamp(y, low, high);
            }
            laid[j] = Math.max(y, lowest.getInt(from + j));
        }
        // a block a column at most, raised where it has to clear water rather than lowered into it
        int previous = openStart ? laid[0] : before;
        for (int j = 0; j < n; j++) {
            laid[j] = Math.max(laid[j], previous - 1);
            previous = laid[j];
        }
        int next = openEnd ? laid[n - 1] : after;
        for (int j = n - 1; j >= 0; j--) {
            laid[j] = Math.max(laid[j], next - 1);
            next = laid[j];
        }
        System.arraycopy(laid, 0, out, from, n);
    }

    /** The nearest height the land is known at along a run from a column, going one way; else what is given. */
    private static double landNear(DoubleArrayList land, int from, int stopBefore, double otherwise) {
        int step = stopBefore > from ? 1 : -1;
        for (int i = from; i != stopBefore; i += step) {
            if (!Double.isNaN(land.getDouble(i))) {
                return land.getDouble(i);
            }
        }
        return otherwise;
    }

    /**
     * How the land lies across a column of a part, or across the whole of it: the middle height of its dry ground, or
     * {@link Integer#MIN_VALUE} for none, and the lowest a road may be laid there, a block over the top of any water.
     *
     * @param a the column, or less than 0 for all of them
     */
    private static int[] lie(Terrain.@Nullable Column[] columns, int width, int depth, int a) {
        int[] dry = new int[columns.length];
        int count = 0;
        int lowest = Integer.MIN_VALUE;
        for (int b = 0; b < depth; b++) {
            for (int x = a < 0 ? 0 : a; x <= (a < 0 ? width - 1 : a); x++) {
                Terrain.Column column = columns[b * width + x];
                if (column == null || column.surface() == Terrain.NO_GROUND) {
                    continue;
                }
                if (column.water() > 0) {
                    lowest = Math.max(lowest, column.surface() + column.water() + 1);
                } else {
                    dry[count++] = column.surface();
                }
            }
        }
        if (count == 0) {
            return new int[] {Integer.MIN_VALUE, lowest};
        }
        Arrays.sort(dry, 0, count);
        return new int[] {dry[count / 2], lowest};
    }

    /** Reads the land under every column of a part, as {@link #survey} does. */
    private Terrain.Column[] read(ServerLevel level, Part part) {
        Frame frame = new Frame(part.origin(), part.front());
        Terrain.Column[] columns = new Terrain.Column[part.width() * part.depth()];
        for (int b = 0; b < part.depth(); b++) {
            for (int a = 0; a < part.width(); a++) {
                BlockPos at = frame.at(a, b, 0);
                columns[b * part.width() + a] = column(level, at.getX(), at.getZ());
            }
        }
        return columns;
    }

    /**
     * The run of street a stretch is part of, in order along it: from the crossing before it, by way of every stretch
     * between, to the crossing after it, as far as the plan has them.
     */
    private List<Part> run(BlockPos center, Part stretch) {
        ArrayDeque<Part> stops = new ArrayDeque<>();
        stops.add(stretch);
        for (int way = 0; way < 2; way++) {
            Part at = stretch;
            for (int k = 0; k < RUN_REACH; k++) {
                Frame frame = new Frame(at.origin(), at.front());
                Part next = streetAt(center, frame.at(way == 0 ? -1 : at.width(), TownPlan.STREET_WIDTH / 2, 0), at);
                boolean along = next != null && next.kind() == stretch.kind() && next.front() == stretch.front();
                if (next == null || next.kind() != Kind.CROSSING && !along) {
                    break;
                }
                if (way == 0) {
                    stops.addFirst(next);
                } else {
                    stops.addLast(next);
                }
                if (next.kind() == Kind.CROSSING) {
                    break;
                }
                at = next;
            }
        }
        return new ArrayList<>(stops);
    }

    /**
     * How high a crossing is laid: as it was begun, or as the runs meeting it were worked out to meet it; else at the
     * middle height of its own land and the land of the crossings round it, never lower than a block over any water
     * there, kept for the runs meeting it. With the crossings round it, a canyon or a ridge no wider than a block of
     * the town is crossed over, or gone through, at the height of the town either side, while the town still climbs a
     * hillside, or keeps to a plateau or a valley as wide as the blocks around.
     */
    private int crossingLevel(ServerLevel level, Vec3 hexCenter, Part crossing, Terrain.@Nullable Column[] columns) {
        if (levels.containsKey(crossing.id())) {
            return levels.get(crossing.id());
        }
        if (plannedCrossings.containsKey(crossing.id())) {
            return plannedCrossings.get(crossing.id());
        }
        int[] lie = lie(columns, crossing.width(), crossing.depth(), -1);
        IntArrayList around = new IntArrayList();
        if (lie[0] != Integer.MIN_VALUE) {
            around.add(lie[0]);
        }
        BlockPos center = BlockPos.containing(hexCenter);
        for (int ds = -1; ds <= 1; ds++) {
            for (int dg = -1; dg <= 1; dg++) {
                if (ds == 0 && dg == 0) {
                    continue;
                }
                Part next = part(center, crossing.id() + (ds << 16) + (dg << 8));
                if (next == null || next.kind() != Kind.CROSSING) {
                    continue;
                }
                OptionalInt laid = laidLevel(next, false);
                if (laid.isPresent()) {
                    around.add(laid.getAsInt());
                } else if (isLoaded(level, next)) {
                    int lies = lie(read(level, next), next.width(), next.depth(), -1)[0];
                    if (lies != Integer.MIN_VALUE) {
                        around.add(lies);
                    }
                }
            }
        }
        int at;
        if (around.size() >= 5) {
            int[] sorted = around.toIntArray();
            Arrays.sort(sorted);
            at = sorted[sorted.length / 2];
        } else {
            at = lie[0] != Integer.MIN_VALUE ? lie[0] : center.getY();
        }
        if (lie[1] != Integer.MIN_VALUE) {
            at = Math.max(at, lie[1]);
        }
        plannedCrossings.put(crossing.id(), at);
        changed = true;
        return at;
    }

    /**
     * How each column of a street or crossing laid at its heights is carried: through a hill in a tunnel where most of
     * the land across it stands well over it, on a bridge where most of it falls far away under it or is water, and on
     * the land otherwise. A hill too short to tunnel through is cut through.
     */
    private static Streets.Span[] spans(Terrain.@Nullable Column[] columns, int length, int depth, int[] levels) {
        Streets.Span[] spans = new Streets.Span[length];
        for (int a = 0; a < length; a++) {
            int cells = 0;
            int deep = 0;
            int high = 0;
            for (int b = 0; b < depth; b++) {
                Terrain.Column column = columns[b * length + a];
                if (column == null) {
                    continue;
                }
                cells++;
                if (column.surface() == Terrain.NO_GROUND || column.water() > 0 || levels[a] - column.surface() > MAX_FILL) {
                    deep++;
                } else if (column.surface() >= levels[a] + Streets.TUNNEL_HEIGHT + 2) {
                    high++;
                }
            }
            spans[a] = deep * 2 > cells ? Streets.Span.BRIDGE : high * 2 > cells ? Streets.Span.TUNNEL : Streets.Span.LAND;
        }
        for (int a = 0; a < length; ) {
            if (spans[a] != Streets.Span.TUNNEL) {
                a++;
                continue;
            }
            int from = a;
            while (a < length && spans[a] == Streets.Span.TUNNEL) {
                a++;
            }
            // a tunnel running on into the next stretch is long enough
            boolean open = from == 0 || a == length;
            if (a - from < SHORTEST_TUNNEL && !open) {
                Arrays.fill(spans, from, a, Streets.Span.LAND);
            }
        }
        return spans;
    }

    /** The street, crossing or stretch, in the plan at a column, other than the one given. */
    private @Nullable Part streetAt(BlockPos center, BlockPos at, Part not) {
        for (Part other : plan(center).parts()) {
            if (other.id() != not.id() && other.kind().isStreet() && at.getX() >= other.minX() && at.getX() <= other.maxX()
                    && at.getZ() >= other.minZ() && at.getZ() <= other.maxZ()) {
                return other;
            }
        }
        return null;
    }

    private void forgetLevel(int id) {
        levels.remove(id);
        ends.remove(id);
        streetLevels.remove(id);
        grounds.remove(id);
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
     * @param levelOf  where another part of the plan is laid, or would be, for a street to meet the next stretch of it
     */
    private Blueprint blueprint(Part part, Survey survey, boolean cutThrough, boolean[] occupied,
                                java.util.function.Function<Part, OptionalInt> levelOf) {
        Frame frame = new Frame(part.origin(), part.front());
        int ground = survey.ground();
        Houses.Heights heights = survey::height;
        RandomSource random = looks(seedOf(part), part.lookId());
        int homes = Houses.homeKinds(seedOf(part));
        if (mode == HexBuild.ORCHARD) {
            return switch (part.kind()) {
                case HOME -> Houses.anchor(frame, ground, heights, random, homes);
                case HOUSE -> plot(part, frame, survey, random, occupied);
                case SQUARE -> Orchard.farmyard(frame, part.width(), part.depth(), ground, heights, random);
                case STREET, CROSS_STREET -> survey.profile() != null ? Streets.lane(frame, part.width(), survey.profile(), heights)
                        : Orchard.lane(frame, part.width(), ground, heights);
                case CROSSING -> survey.profile() != null ? Streets.crossing(frame, survey.profile(), heights, true) : Orchard.crossing(frame, ground, heights);
            };
        }
        return switch (part.kind()) {
            case HOME -> Houses.anchor(frame, ground, heights, random, homes);
            case HOUSE -> cutThrough ? Houses.garden(frame, ground, heights, random)
                    : grounds.containsKey(part.id()) ? grounds(grounds.get(part.id()), frame, survey, random)
                    : lot(part, frame, ground, heights, random, survey.sidewalk());
            case SQUARE -> Streets.square(frame, part.width(), part.depth(), ground, heights, random);
            case STREET -> survey.profile() != null ? Streets.graded(frame, part.width(), survey.profile(), heights, 0)
                    : Streets.street(frame, part.width(), ground, heights, meet(part, frame, -1, ground, levelOf), meet(part, frame, part.width(), ground, levelOf), 0);
            case CROSSING -> survey.profile() != null ? Streets.crossing(frame, survey.profile(), heights, false) : Streets.crossing(frame, ground, heights);
            case CROSS_STREET -> survey.profile() != null ? Streets.graded(frame, part.width(), survey.profile(), heights, part.width() / 2)
                    : Streets.street(frame, part.width(), ground, heights, meet(part, frame, -1, ground, levelOf), meet(part, frame, part.width(), ground, levelOf),
                    part.width() / 2);
        };
    }

    /**
     * What goes up on a lot, by what the town has zoned it for.
     */
    private Blueprint lot(Part part, Frame frame, int ground, Houses.Heights heights, RandomSource random, int @Nullable [] sidewalk) {
        return switch (zoning().getOrDefault(part.id(), Use.HOUSE)) {
            case SHOP -> Civic.shop(frame, ground, heights, shopKinds.get(part.id()), random);
            case LIBRARY -> Civic.library(frame, ground, heights, random);
            case CHAPEL -> Civic.chapel(frame, ground, heights, random);
            case SCHOOL -> Civic.school(frame, ground, heights, random);
            case PLAYGROUND -> Civic.playground(frame, ground, heights, random);
            case PARK -> Houses.park(frame, ground, heights, random);
            case COURT -> Grounds.court(frame, ground, heights, random);
            case GROVE -> Grounds.grove(frame, ground, heights, random);
            case GAS_STATION -> Civic.gasStation(frame, ground, heights, random);
            case GARDEN, POND, LOOKOUT, TERRACES -> Houses.garden(frame, ground, heights, random);
            case HOUSE -> Houses.house(frame, ground, heights, random, sidewalk);
        };
    }

    /**
     * What goes up on a lot laid out as what suits its land rather than what it was zoned for.
     */
    private static Blueprint grounds(Use use, Frame frame, Survey survey, RandomSource random) {
        int ground = survey.ground();
        Houses.Heights heights = survey::height;
        return switch (use) {
            case POND -> Grounds.pond(frame, ground, heights, survey::water, random);
            case LOOKOUT -> Grounds.lookout(frame, ground, heights, random);
            case TERRACES -> Grounds.terraces(frame, survey.sidewalk() != null ? survey.sidewalk()[survey.width() / 2] : Grounds.NO_STREET, heights, random);
            case COURT -> Grounds.court(frame, ground, heights, random);
            case GROVE -> Grounds.grove(frame, ground, heights, random);
            default -> Houses.garden(frame, ground, heights, random);
        };
    }

    /**
     * What goes up on a plot of the orchard: mostly rows of fruit trees, now and then a meadow, and where nothing
     * stands on it already, here and there a greenhouse, a chicken coop, a pumpkin patch or beehives. Land that won't
     * take a plot is a meadow, or a pond kept as it is with a dock out over it.
     */
    private Blueprint plot(Part part, Frame frame, Survey survey, RandomSource random, boolean[] occupied) {
        int ground = survey.ground();
        Houses.Heights heights = survey::height;
        Use use = grounds.get(part.id());
        if (use == Use.POND) {
            return Grounds.pond(frame, ground, heights, survey::water, random);
        }
        if (use != null) {
            return Orchard.meadow(frame, ground, heights, random, occupied);
        }
        boolean clear = true;
        for (boolean taken : occupied) {
            clear &= !taken;
        }
        float pick = hash(seedOf(part), part.id(), 17);
        if (clear && pick < 0.24F) {
            return pick < 0.06F ? Orchard.greenhouse(frame, ground, heights, random)
                    : pick < 0.12F ? Orchard.coop(frame, ground, heights, random)
                    : pick < 0.18F ? Orchard.pumpkins(frame, ground, heights, random)
                    : Orchard.hives(frame, ground, heights, random);
        }
        return random.nextFloat() < 0.18F ? Orchard.meadow(frame, ground, heights, random, occupied) : Orchard.plot(frame, ground, heights, random, occupied);
    }

    // ---------------------------------------------------------------- zoning

    /**
     * What a lot of the town is for. Saved by position, for lots laid out as what suits their land, so new ones come
     * last.
     */
    private enum Use {
        HOUSE, PARK, PLAYGROUND, SHOP, LIBRARY, CHAPEL, SCHOOL,
        // what suits land that won't take what a lot was zoned for
        GARDEN, POND, LOOKOUT, TERRACES,
        // here and there among the houses, and on lots out of reach of their street
        COURT, GROVE, GAS_STATION;

        private static final Use[] VALUES = values();

        static Use byIndex(int index) {
            return VALUES[Math.clamp(index, 0, VALUES.length - 1)];
        }
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
        pick(uses, lots, layer, seed, Use.GAS_STATION, 40.0, 70.0);
        pick(uses, lots, layer, seed, Use.GAS_STATION, 120.0, 220.0);
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
     * Whether a lot not zoned for anything else is one of the parks, playgrounds, ball courts or picnic groves here and
     * there among the houses.
     *
     * @param distanceSqr how far it is from the middle of its town
     */
    private static @Nullable Use scattered(long seed, int gridId, double distanceSqr) {
        float h = hash(seed, gridId, 7);
        boolean out = Math.sqrt(distanceSqr) >= 35.0;
        if (out && h < 0.06F) {
            return Use.PLAYGROUND;
        }
        if (h < 0.14F) {
            return h >= 0.06F ? Use.PARK : null;
        }
        return out && h < 0.17F ? Use.COURT : h >= 0.17F && h < 0.20F ? Use.GROVE : null;
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
     * Where a town leaves what it can't put back yet, in chunks that aren't loaded, and puts back first what any town
     * left where it is about to build.
     */
    public interface Leftovers {
        void leave(long key, Built entry);

        void putBackWithin(ServerLevel level, int minX, int minZ, int maxX, int maxZ);
    }

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
     * @param profile  how a stretch of street runs along the land, for one; null for anything laid level
     * @param sidewalk how high the sidewalk past each column of a lot's front is laid, for one with a street before it
     */
    private record Survey(int ground, Terrain.@Nullable Column[] columns, boolean[] blocked, int width, Streets.@Nullable Profile profile,
                          int @Nullable [] sidewalk) {

        /** How far down a column no ground was found under is taken to go: deep enough to be capped over, not filled. */
        private static final int BOTTOMLESS = 64;

        static Survey flat(int ground, Part part) {
            return new Survey(ground, new Terrain.Column[part.width() * part.depth()], new boolean[part.width() * part.depth()], part.width(),
                    null, null);
        }

        Survey at(int ground) {
            return new Survey(ground, columns, blocked, width, profile, sidewalk);
        }

        /**
         * The natural ground under a column: the part's level where nothing was looked at there, and far below it where
         * no ground was found, a cave or a gorge.
         */
        int height(int a, int b) {
            int index = b * width + a;
            Terrain.Column column = index >= 0 && index < columns.length ? columns[index] : null;
            return column == null ? ground : column.surface() == Terrain.NO_GROUND ? ground - BOTTOMLESS : column.surface();
        }

        /** How high the water over a column comes, or {@link Grounds#DRY} where there is none. */
        int water(int a, int b) {
            int index = b * width + a;
            Terrain.Column column = a >= 0 && a < width && index >= 0 && index < columns.length ? columns[index] : null;
            return column == null || column.water() == 0 || column.surface() == Terrain.NO_GROUND ? Grounds.DRY : column.surface() + column.water();
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
        /** How finely a sweep tells blocks apart by how far out they stand: a quarter of a block. */
        private static final int RINGS_PER_BLOCK = 4;
        private static final int MAX_RING = 1 << 16;

        final long[] order;
        final float[] reach;
        final it.unimi.dsi.fastutil.longs.LongArrayList touched = new it.unimi.dsi.fastutil.longs.LongArrayList();
        int cursor;
        /** How many of {@link #touched} have had their connections worked out again. */
        int reshaped;
        @Nullable Era era;
        long start;

        private Sweep(long[] order, float[] reach) {
            this.order = order;
            this.reach = reach;
        }

        /** Which ring a block that far out falls in; a sweep orders its rings, but not what lies within each. */
        static int ring(float reach) {
            return Math.min(MAX_RING, (int) (reach * RINGS_PER_BLOCK));
        }

        static Sweep insideOut(Long2ObjectOpenHashMap<Built> built, Vec3 center) {
            return sorted(built, center, true, 0.0F);
        }

        static Sweep outsideIn(Long2ObjectOpenHashMap<Built> built, Vec3 center) {
            return sorted(built, center, false, 0.0F);
        }

        /** Those that pass a test, and then the rest, each from the outside in. */
        static Sweep[] outsideIn(Long2ObjectOpenHashMap<Built> built, Vec3 center, LongPredicate first) {
            long[] keys = built.keySet().toLongArray();
            long[] passing = new long[keys.length];
            long[] rest = new long[keys.length];
            int passed = 0;
            int others = 0;
            for (long key : keys) {
                if (first.test(key)) {
                    passing[passed++] = key;
                } else {
                    rest[others++] = key;
                }
            }
            return new Sweep[] {sorted(Arrays.copyOf(passing, passed), center, false, 0.0F), sorted(Arrays.copyOf(rest, others), center, false, 0.0F)};
        }

        /** Whether it has gone as far in as the ring the wall stands in, the wall's own ring with it if inclusive. */
        boolean caughtUp(int inner, boolean inclusive) {
            if (cursor >= order.length) {
                return true;
            }
            int ring = ring(reach[cursor]);
            return inclusive ? ring < inner : ring <= inner;
        }

        /**
         * Only those lying at least {@code from} out.
         */
        static Sweep outsideIn(Long2ObjectOpenHashMap<Built> built, Vec3 center, float from) {
            return sorted(built, center, false, from);
        }

        private static Sweep sorted(Long2ObjectOpenHashMap<Built> built, Vec3 center, boolean ascending, float from) {
            return sorted(built.keySet().toLongArray(), center, ascending, from);
        }

        static Sweep sorted(long[] keys, Vec3 center, boolean ascending, float from) {
            // into rings a fraction of a block wide, by how far out each block stands, rather than sorted one against
            // another: a town can have a million blocks and more
            int n = keys.length;
            float[] reaches = new float[n];
            int[] rings = new int[n];
            int top = 0;
            int count = 0;
            for (int k = 0; k < n; k++) {
                float reach = reachOf(center, keys[k]);
                reaches[k] = reach;
                if (reach >= from) {
                    int ring = ring(reach);
                    rings[k] = ring;
                    top = Math.max(top, ring);
                    count++;
                } else {
                    rings[k] = -1;
                }
            }
            int[] starts = new int[top + 2];
            for (int k = 0; k < n; k++) {
                if (rings[k] >= 0) {
                    starts[(ascending ? rings[k] : top - rings[k]) + 1]++;
                }
            }
            for (int ring = 1; ring < starts.length; ring++) {
                starts[ring] += starts[ring - 1];
            }
            long[] order = new long[count];
            float[] reach = new float[count];
            for (int k = 0; k < n; k++) {
                if (rings[k] >= 0) {
                    int at = starts[ascending ? rings[k] : top - rings[k]]++;
                    order[at] = keys[k];
                    reach[at] = reaches[k];
                }
            }
            return new Sweep(order, reach);
        }
    }

    /** How far out from the middle of the Hex a block of the town stands, as {@link HexShape#level} measures. */
    private static float reachOf(Vec3 center, long key) {
        return (float) HexShape.level(center, 1.0F, BlockPos.getX(key) + 0.5, BlockPos.getZ(key) + 0.5);
    }

    /** Blocks in order of height: from the bottom up, or from the top down. */
    private static long[] byHeight(long[] keys, boolean upward) {
        long[] packed = new long[keys.length];
        for (int k = 0; k < keys.length; k++) {
            // heights are within a few thousand of 0 either way; shifted up, they sort as plain numbers
            packed[k] = (long) (BlockPos.getY(keys[k]) + (1 << 20)) << 32 | k;
        }
        Arrays.sort(packed);
        long[] order = new long[keys.length];
        for (int i = 0; i < keys.length; i++) {
            order[i] = keys[(int) packed[upward ? i : keys.length - 1 - i]];
        }
        return order;
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
