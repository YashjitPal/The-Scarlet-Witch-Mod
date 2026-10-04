package com.yashjit.scarlet.hex;

import com.yashjit.scarlet.config.ScarletServerConfig;
import com.yashjit.scarlet.entity.DreamBody;
import com.yashjit.scarlet.magic.MindControl;
import com.yashjit.scarlet.magic.Telekinesis;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The last ten seconds inside a standing Hex, kept for its caster to rewind: where every creature, player and thrown
 * thing went and how hurt it was, every block broken or placed and what it dropped, every creature killed and what it
 * dropped, and everything that came into the world. It counts its own ticks, which only run while it records, so a
 * rewind leaves no gap behind it: the scene plays on from where it was wound back to. Not saved; a reload starts a
 * fresh tape.
 *
 * <p>Nothing is handed out twice. What someone has picked up can't be taken back, so a block whose drops are gone stays
 * broken, and so does everything that happened at its spot before; a creature whose drops are gone gets back up with
 * nothing left to drop. What a player put down from their hand goes back into it, and whatever they filled or emptied
 * doing something stays as they left it.
 */
public final class HexTape {

    /** Ticks it keeps: ten seconds. */
    public static final int LENGTH = 200;
    /** Ticks wound back each tick at most, once the rewind has gathered speed. */
    public static final int TOP_SPEED = 4;
    /** Ticks a held rewind takes to gather to its top speed. */
    public static final int GATHER_TICKS = 20;
    /** The tag on a creature brought back whose drops someone had already picked up: it has nothing left to drop. */
    public static final String SPENT_TAG = "scarlet.rewound_spent";
    private static final int CAPACITY = LENGTH + 2;
    /** Most block changes kept: the oldest go first past this. */
    private static final int MOST_CHANGES = 16384;
    /** How far from where something dropped it is looked for to take back. */
    private static final double RECLAIM_REACH = 3.0;
    private static final int PUT_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** The tapes recording in each level, for the hooks in the world to find quickly. */
    private static final Map<ServerLevel, HexTape[]> RECORDING = new WeakHashMap<>();
    /** How deep the Hex's own work is: what it does to the world itself is no part of the scene. */
    private static int quiet;
    /** The creature dying right now, whose drops are its. */
    private static final ArrayDeque<Death> DYING = new ArrayDeque<>();
    /** What a player is doing with their hands right now, whose changes are theirs. */
    private static @Nullable Use using;

    private final Hex hex;
    /** Its own clock: the tick it is recording now. */
    private int now;
    /** The tick it started recording, which it can't wind back past. */
    private int first;
    private final Map<UUID, Track> tracks = new HashMap<>();
    /** What happened, in order: blocks changing, creatures dying, things coming into the world. */
    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private int changeCount;
    /** Drops let fall this tick at a block that hasn't changed yet this tick, by block. */
    private final Long2ObjectMap<Change> changedThisTick = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<List<ItemStack>> looseThisTick = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<int[]> looseExperience = new Long2ObjectOpenHashMap<>();
    /** Spots a rewind couldn't put back, and so leaves alone for the rest of it. */
    private final LongOpenHashSet locked = new LongOpenHashSet();
    /** Creatures stood still while it winds back, to let go again after. */
    private final Set<UUID> frozen = new HashSet<>();
    private boolean recording = true;

    HexTape(Hex hex) {
        this.hex = hex;
    }

    // ---------------------------------------------------------------- the tapes in a level

    /**
     * Keeps the tapes of a level's standing Hexes where the world's hooks can find them. Called every tick.
     */
    static void gather(ServerLevel level, Iterable<Hex> hexes) {
        List<HexTape> tapes = new ArrayList<>(1);
        for (Hex hex : hexes) {
            if (hex.tape != null) {
                tapes.add(hex.tape);
            }
        }
        if (tapes.isEmpty()) {
            RECORDING.remove(level);
        } else {
            RECORDING.put(level, tapes.toArray(HexTape[]::new));
        }
    }

    private static @Nullable HexTape recordingAt(Level level, Vec3 point) {
        if (quiet > 0 || level.isClientSide() || !(level instanceof ServerLevel server)) {
            return null;
        }
        HexTape[] tapes = RECORDING.get(server);
        if (tapes == null || !server.getServer().isSameThread()) {
            return null;
        }
        for (HexTape tape : tapes) {
            if (tape.recording && tape.hex.contains(point)) {
                return tape;
            }
        }
        return null;
    }

    /**
     * Runs something the Hex does to the world itself, which no tape records.
     */
    public static void offTape(Runnable work) {
        quiet++;
        try {
            work.run();
        } finally {
            quiet--;
        }
    }

    public static <T> T offTape(Supplier<T> work) {
        quiet++;
        try {
            return work.get();
        } finally {
            quiet--;
        }
    }

    // ---------------------------------------------------------------- recording

    /**
     * Every tick of every creature, player and thrown thing: where it is now and how hurt.
     */
    public static void ticked(ServerLevel level, Entity entity) {
        if (!(entity instanceof LivingEntity) && !(entity instanceof Projectile)) {
            return;
        }
        HexTape tape = recordingAt(level, entity.position());
        if (tape != null && !entity.getUUID().equals(tape.hex.caster) && !(entity instanceof DreamBody)
                && !(entity instanceof Player player && player.isSpectator())) {
            tape.tracks.computeIfAbsent(entity.getUUID(), id -> new Track(id)).record(tape.now, entity);
        }
    }

    /**
     * Before a block changes: what is kept in it now, should the change take it away.
     */
    public static @Nullable Object changing(Level level, BlockPos pos, BlockState next) {
        HexTape tape = recordingAt(level, Vec3.atCenterOf(pos));
        if (tape == null) {
            return null;
        }
        BlockState before = level.getBlockState(pos);
        CompoundTag data = null;
        if (before.hasBlockEntity() && before.getBlock() != next.getBlock()) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity != null) {
                data = entity.saveWithFullMetadata(level.registryAccess());
            }
        }
        return new Pending(tape, data);
    }

    /**
     * After a block changed, if it did.
     */
    public static void changed(@Nullable Object pending, BlockPos pos, @Nullable BlockState before, BlockState after) {
        if (!(pending instanceof Pending(HexTape tape, CompoundTag data)) || before == null || before == after) {
            return;
        }
        Change change = new Change(tape.now, pos.immutable(), before, data, after);
        tape.events.addLast(change);
        tape.changeCount++;
        long key = change.pos.asLong();
        tape.changedThisTick.put(key, change);
        // drops let fall just before the block went are its too
        List<ItemStack> loose = tape.looseThisTick.remove(key);
        if (loose != null) {
            change.drops.addAll(loose);
        }
        int[] experience = tape.looseExperience.remove(key);
        if (experience != null) {
            change.experience += experience[0];
        }
        if (using != null) {
            using.made.add(change);
        }
    }

    /**
     * A creature beginning to die inside a Hex: kept whole, to get back up, and what it drops is its.
     */
    public static @Nullable Death dying(LivingEntity entity) {
        if (entity instanceof Player || entity.isRemoved() || entity.isDeadOrDying() && entity.deathTime > 0) {
            return null;
        }
        HexTape tape = recordingAt(entity.level(), entity.position());
        if (tape == null || entity instanceof DreamBody) {
            return null;
        }
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
        if (!entity.saveAsPassenger(output)) {
            return null;
        }
        Death death = new Death(tape.now, entity.getUUID(), output.buildResult(), entity.position());
        tape.events.addLast(death);
        DYING.push(death);
        return death;
    }

    public static void died(@Nullable Death death) {
        if (death != null) {
            DYING.remove(death);
        }
    }

    /**
     * Something new in the world: a creature, a thrown thing or a falling block that wasn't there before, or something
     * dropped by a block or a creature.
     */
    public static void added(ServerLevel level, Entity entity) {
        if (quiet > 0) {
            return;
        }
        if (entity instanceof ItemEntity item && !DYING.isEmpty()) {
            // a dying creature's drops are its, wherever they fall
            DYING.peek().drops.add(item.getItem().copy());
            return;
        }
        HexTape tape = recordingAt(level, entity.position());
        if (tape == null) {
            return;
        }
        if (entity instanceof ItemEntity item) {
            ItemStack stack = item.getItem().copy();
            long key = BlockPos.containing(entity.position()).asLong();
            Change change = tape.changedThisTick.get(key);
            if (change != null) {
                change.drops.add(stack);
            } else {
                tape.looseThisTick.computeIfAbsent(key, k -> new ArrayList<>(1)).add(stack);
            }
            return;
        }
        boolean fromHand = using != null;
        if (fromHand || entity instanceof LivingEntity && !(entity instanceof Player) || entity instanceof Projectile
                || entity instanceof FallingBlockEntity || entity instanceof PrimedTnt) {
            Birth birth = new Birth(tape.now, entity.getUUID());
            tape.events.addLast(birth);
            if (using != null) {
                using.born.add(birth);
            }
        }
    }

    /**
     * Experience let fall: by a creature dying, or at a block mined.
     */
    public static void experience(ServerLevel level, Vec3 at, int amount) {
        if (!DYING.isEmpty()) {
            DYING.peek().experience += amount;
            return;
        }
        HexTape tape = recordingAt(level, at);
        if (tape == null) {
            return;
        }
        long key = BlockPos.containing(at).asLong();
        Change change = tape.changedThisTick.get(key);
        if (change != null) {
            change.experience += amount;
        } else {
            tape.looseExperience.computeIfAbsent(key, k -> new int[1])[0] += amount;
        }
    }

    /**
     * A player beginning to use what is in their hand, on a block or not.
     */
    public static @Nullable Use using(ServerPlayer player) {
        if (RECORDING.isEmpty()) {
            return null;
        }
        Use use = new Use(player, player.getMainHandItem().copy(), player.getOffhandItem().copy(), using);
        using = use;
        return use;
    }

    /**
     * Done using it: whatever it put down from their hand they get back if it is rewound, and whatever they filled or
     * emptied doing it stays as they left it.
     */
    public static void used(@Nullable Use use) {
        if (use == null) {
            return;
        }
        using = use.outer;
        if (use.made.isEmpty() && use.born.isEmpty()) {
            return;
        }
        ItemStack spent = spentOne(use.main, use.player.getMainHandItem());
        if (spent == null) {
            spent = spentOne(use.off, use.player.getOffhandItem());
        }
        boolean mainSame = sameHand(use.main, use.player.getMainHandItem());
        boolean offSame = sameHand(use.off, use.player.getOffhandItem());
        if (spent != null && (mainSame || offSame)) {
            // one thing went from their hand into the world: it goes back to them
            for (Change change : use.made) {
                if (!change.after.isAir() && change.after.getBlock().asItem() == spent.getItem()) {
                    change.giveBack(use.player.getUUID(), spent);
                    return;
                }
            }
            if (!use.born.isEmpty()) {
                use.born.getFirst().giveBack(use.player.getUUID(), spent);
                return;
            }
        }
        if (mainSame && offSame) {
            return;
        }
        // filled, emptied or used up something doing it: none of it can be wound back
        for (Change change : use.made) {
            change.exchanged = true;
        }
        for (Birth birth : use.born) {
            birth.kept = true;
        }
    }

    /**
     * One of what was in a hand before, if just one of it went.
     */
    private static @Nullable ItemStack spentOne(ItemStack before, ItemStack after) {
        if (before.isEmpty()) {
            return null;
        }
        boolean oneLess = after.isEmpty() ? before.getCount() == 1
                : ItemStack.isSameItemSameComponents(before, after) && after.getCount() == before.getCount() - 1;
        return oneLess ? before.copyWithCount(1) : null;
    }

    private static boolean sameHand(ItemStack before, ItemStack after) {
        return before.isEmpty() ? after.isEmpty() : ItemStack.isSameItem(before, after) && before.getCount() == after.getCount();
    }

    /**
     * The end of a tick recorded: the clock moves on, and what is older than the tape keeps is let go.
     */
    void advance() {
        changedThisTick.clear();
        looseThisTick.clear();
        looseExperience.clear();
        now++;
        int oldest = now - LENGTH;
        first = Math.max(first, oldest);
        while (!events.isEmpty() && (events.peekFirst().tick() < oldest || changeCount > MOST_CHANGES)) {
            if (events.pollFirst() instanceof Change) {
                changeCount--;
            }
        }
        if (now % 20 == 0) {
            tracks.values().removeIf(track -> track.latest() < oldest);
        }
    }

    // ---------------------------------------------------------------- winding back

    /** How many ticks it can still wind back. */
    int left() {
        return now - first;
    }

    /**
     * The rewind begins: recording stops, and every creature on the tape stands still.
     */
    void beginRewind(ServerLevel level) {
        recording = false;
        locked.clear();
        for (Track track : tracks.values()) {
            if (level.getEntity(track.id) instanceof Mob mob) {
                freeze(mob);
            }
        }
    }

    /**
     * Winds back as many ticks as asked, undoing what happened in them, the last first, then puts everything on the
     * tape where it was then.
     *
     * @return how many ticks it wound back
     */
    int windBack(ServerLevel level, int ticks) {
        int wound = Math.min(ticks, left());
        for (int i = 0; i < wound; i++) {
            now--;
            while (!events.isEmpty() && events.peekLast().tick() >= now) {
                Event event = events.pollLast();
                switch (event) {
                    case Change change -> {
                        changeCount--;
                        undo(level, change);
                    }
                    case Death death -> revive(level, death);
                    case Birth birth -> unbirth(level, birth);
                }
            }
        }
        pose(level, false);
        return wound;
    }

    /**
     * The rewind ends: the scene plays on from where it was wound back to, everything moving on as it was moving then,
     * and what was rewound past is gone from the tape.
     */
    void endRewind(ServerLevel level) {
        pose(level, true);
        for (Track track : tracks.values()) {
            track.truncate(now - 1);
        }
        for (UUID id : frozen) {
            if (level.getEntity(id) instanceof Mob mob) {
                mob.setNoAi(false);
            }
        }
        frozen.clear();
        locked.clear();
        recording = true;
    }

    private void freeze(Mob mob) {
        if (!mob.isNoAi() && frozen.add(mob.getUUID())) {
            mob.setNoAi(true);
        }
    }

    /**
     * Puts everything on the tape where it was, as hurt as it was, at the tick wound back to.
     *
     * @param moving whether to set it moving as it was then, for the scene to play on
     */
    private void pose(ServerLevel level, boolean moving) {
        boolean players = ScarletServerConfig.get().rewindPlayers;
        for (Track track : tracks.values()) {
            int i = track.at(now - 1);
            if (i < 0) {
                continue;
            }
            Entity entity = level.getEntity(track.id);
            if (entity == null || !entity.isAlive() || entity.isPassenger() || Telekinesis.isHeld(entity)
                    || entity instanceof Mob mob && MindControl.steers(mob) || entity.getUUID().equals(hex.caster)) {
                continue;
            }
            Vec3 velocity = moving ? new Vec3(track.vx[i], track.vy[i], track.vz[i]) : Vec3.ZERO;
            if (entity instanceof ServerPlayer player) {
                if (!players || player.isSpectator()) {
                    continue;
                }
                player.connection.teleport(track.x[i], track.y[i], track.z[i], track.yRot[i], track.xRot[i]);
                // wounds close, but a rewind never hurts anyone
                player.setHealth(Math.max(player.getHealth(), track.health[i]));
                continue;
            }
            entity.snapTo(track.x[i], track.y[i], track.z[i], track.yRot[i], track.xRot[i]);
            entity.setYHeadRot(track.head[i]);
            if (entity instanceof AbstractArrow arrow && moving && velocity.lengthSqr() > 0.0) {
                // frees an arrow that had stuck in something since
                arrow.lerpMotion(velocity);
            } else {
                entity.setDeltaMovement(velocity);
            }
            if (entity instanceof LivingEntity living && track.health[i] > 0.0F) {
                living.setHealth(track.health[i]);
            }
        }
    }

    /**
     * Puts a block back as it was before it changed, unless what it dropped has been picked up, or something has
     * changed it since that no tape saw. Either way that spot is left alone for the rest of the rewind.
     */
    private void undo(ServerLevel level, Change change) {
        long key = change.pos.asLong();
        if (locked.contains(key)) {
            return;
        }
        if (change.exchanged || level.getBlockState(change.pos) != change.after
                || !reclaim(level, Vec3.atCenterOf(change.pos), change.drops, change.experience)) {
            locked.add(key);
            return;
        }
        level.setBlock(change.pos, change.before, PUT_FLAGS);
        if (change.data != null) {
            BlockEntity entity = level.getBlockEntity(change.pos);
            if (entity != null) {
                entity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), change.data));
                entity.setChanged();
                level.sendBlockUpdated(change.pos, change.before, change.before, Block.UPDATE_CLIENTS);
            }
        }
        lift(level, change.pos, change.before);
        giveBack(level, change.giver, change.given, Vec3.atCenterOf(change.pos));
    }

    /**
     * Brings back a creature killed: whole, as hurt as it was a moment before, with its drops taken back. If someone
     * picked them up, it gets back up with nothing left to drop.
     */
    private void revive(ServerLevel level, Death death) {
        Entity body = level.getEntity(death.id);
        if (body != null) {
            if (body.isAlive()) {
                return;
            }
            body.discard();
        }
        boolean spent = !reclaim(level, death.at, death.drops, death.experience);
        Entity entity = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), death.data), level,
                EntitySpawnReason.LOAD, EntityProcessor.NOP);
        if (!(entity instanceof LivingEntity living) || level.getEntity(entity.getUUID()) != null) {
            return;
        }
        Track track = tracks.get(death.id);
        int i = track == null ? -1 : track.at(now - 1);
        living.setHealth(i >= 0 && track.health[i] > 0.0F ? track.health[i] : living.getMaxHealth() * 0.5F);
        living.deathTime = 0;
        if (spent) {
            living.addTag(SPENT_TAG);
        }
        level.addFreshEntity(living);
        if (living instanceof Mob mob) {
            freeze(mob);
        }
    }

    /**
     * Takes away something that came into the world since: an arrow goes back to its archer, and what was put down from
     * a hand back into it.
     */
    private void unbirth(ServerLevel level, Birth birth) {
        if (birth.kept) {
            return;
        }
        Entity entity = level.getEntity(birth.id);
        if (entity != null && entity.isAlive()) {
            if (birth.given.isEmpty() && entity instanceof AbstractArrow arrow && arrow.pickup == AbstractArrow.Pickup.ALLOWED
                    && arrow.getOwner() instanceof ServerPlayer archer) {
                archer.getInventory().placeItemBackInInventory(arrow.getPickupItemStackOrigin().copy(), Prediction.SERVER_ONLY);
            }
            entity.discard();
        }
        tracks.remove(birth.id);
        giveBack(level, birth.giver, birth.given, entity != null ? entity.position() : null);
    }

    /**
     * Hands back what was put into the world from someone's hand: to them, if they are here, else where it was.
     */
    private static void giveBack(ServerLevel level, @Nullable UUID giver, ItemStack given, @Nullable Vec3 where) {
        if (giver == null || given.isEmpty()) {
            return;
        }
        if (level.getPlayerByUUID(giver) instanceof ServerPlayer player) {
            player.getInventory().placeItemBackInInventory(given.copy(), Prediction.SERVER_ONLY);
        } else if (where != null) {
            level.addFreshEntity(new ItemEntity(level, where.x, where.y, where.z, given.copy()));
        }
    }

    /**
     * Takes back what something dropped, from where it fell, all of it or none: items the same as each it let fall, and
     * experience as much as it let fall.
     *
     * @return whether all of it was there to take back
     */
    private static boolean reclaim(ServerLevel level, Vec3 at, List<ItemStack> drops, int experience) {
        if (drops.isEmpty() && experience <= 0) {
            return true;
        }
        AABB around = new AABB(at, at).inflate(RECLAIM_REACH);
        List<ItemEntity> items = drops.isEmpty() ? List.of() : level.getEntitiesOfClass(ItemEntity.class, around, ItemEntity::isAlive);
        int[] taking = new int[items.size()];
        for (ItemStack drop : drops) {
            int need = drop.getCount();
            for (int k = 0; k < items.size() && need > 0; k++) {
                ItemStack lying = items.get(k).getItem();
                if (ItemStack.isSameItemSameComponents(lying, drop)) {
                    int take = Math.min(need, lying.getCount() - taking[k]);
                    taking[k] += take;
                    need -= take;
                }
            }
            if (need > 0) {
                return false;
            }
        }
        List<ExperienceOrb> orbs = List.of();
        if (experience > 0) {
            orbs = level.getEntitiesOfClass(ExperienceOrb.class, around, ExperienceOrb::isAlive);
            int found = 0;
            for (ExperienceOrb orb : orbs) {
                found += orb.getValue();
            }
            if (found < experience) {
                return false;
            }
        }
        for (int k = 0; k < items.size(); k++) {
            if (taking[k] > 0) {
                ItemEntity item = items.get(k);
                ItemStack left = item.getItem().copy();
                left.shrink(taking[k]);
                if (left.isEmpty()) {
                    item.discard();
                } else {
                    item.setItem(left);
                }
            }
        }
        int owed = experience;
        Iterator<ExperienceOrb> iterator = orbs.iterator();
        while (owed > 0 && iterator.hasNext()) {
            ExperienceOrb orb = iterator.next();
            owed -= orb.getValue();
            orb.discard();
        }
        return true;
    }

    /**
     * Lifts anyone standing where a block comes back up onto it.
     */
    private static void lift(ServerLevel level, BlockPos pos, BlockState state) {
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

    // ---------------------------------------------------------------- what is kept

    /**
     * Where one creature, player or thrown thing went, tick by tick, and how hurt it was: its last ten seconds, newest
     * last.
     */
    private static final class Track {
        final UUID id;
        final int[] tick = new int[CAPACITY];
        final double[] x = new double[CAPACITY];
        final double[] y = new double[CAPACITY];
        final double[] z = new double[CAPACITY];
        final double[] vx = new double[CAPACITY];
        final double[] vy = new double[CAPACITY];
        final double[] vz = new double[CAPACITY];
        final float[] yRot = new float[CAPACITY];
        final float[] xRot = new float[CAPACITY];
        final float[] head = new float[CAPACITY];
        final float[] health = new float[CAPACITY];
        int newest = -1;
        int size;

        Track(UUID id) {
            this.id = id;
        }

        void record(int at, Entity entity) {
            if (size == 0 || tick[newest] != at) {
                newest = (newest + 1) % CAPACITY;
                size = Math.min(size + 1, CAPACITY);
            }
            int i = newest;
            tick[i] = at;
            x[i] = entity.getX();
            y[i] = entity.getY();
            z[i] = entity.getZ();
            Vec3 motion = entity.getDeltaMovement();
            vx[i] = motion.x;
            vy[i] = motion.y;
            vz[i] = motion.z;
            yRot[i] = entity.getYRot();
            xRot[i] = entity.getXRot();
            head[i] = entity.getYHeadRot();
            health[i] = entity instanceof LivingEntity living ? living.getHealth() : 0.0F;
        }

        /**
         * Where it was at a tick: its latest record then or before, or -1 if it has none that early.
         */
        int at(int t) {
            for (int k = 0; k < size; k++) {
                int i = Math.floorMod(newest - k, CAPACITY);
                if (tick[i] <= t) {
                    return i;
                }
            }
            return -1;
        }

        int latest() {
            return size == 0 ? Integer.MIN_VALUE : tick[newest];
        }

        /**
         * Lets go of everything after a tick, rewound past.
         */
        void truncate(int t) {
            while (size > 0 && tick[newest] > t) {
                newest = Math.floorMod(newest - 1, CAPACITY);
                size--;
            }
        }
    }

    private sealed interface Event permits Change, Death, Birth {
        int tick();
    }

    /**
     * A block changed, and what it dropped doing so.
     *
     * @param data what was kept in it, a chest's contents or a sign's words, if the change took it away
     */
    private static final class Change implements Event {
        final int tick;
        final BlockPos pos;
        final BlockState before;
        final @Nullable CompoundTag data;
        final BlockState after;
        final List<ItemStack> drops = new ArrayList<>(1);
        int experience;
        /** Whoever put it down from their hand, and what, to give back. */
        @Nullable UUID giver;
        ItemStack given = ItemStack.EMPTY;
        /** Whether something was filled, emptied or used up doing it, which can't be given back. */
        boolean exchanged;

        Change(int tick, BlockPos pos, BlockState before, @Nullable CompoundTag data, BlockState after) {
            this.tick = tick;
            this.pos = pos;
            this.before = before;
            this.data = data;
            this.after = after;
        }

        void giveBack(UUID player, ItemStack item) {
            giver = player;
            given = item;
        }

        @Override
        public int tick() {
            return tick;
        }
    }

    /**
     * A creature killed: itself whole as it died, and what it dropped.
     */
    public static final class Death implements Event {
        final int tick;
        final UUID id;
        final CompoundTag data;
        final Vec3 at;
        final List<ItemStack> drops = new ArrayList<>(2);
        int experience;

        Death(int tick, UUID id, CompoundTag data, Vec3 at) {
            this.tick = tick;
            this.id = id;
            this.data = data;
            this.at = at;
        }

        @Override
        public int tick() {
            return tick;
        }
    }

    /**
     * Something that came into the world.
     */
    private static final class Birth implements Event {
        final int tick;
        final UUID id;
        @Nullable UUID giver;
        ItemStack given = ItemStack.EMPTY;
        /** Whether it came of filling or emptying something, so stays. */
        boolean kept;

        Birth(int tick, UUID id) {
            this.tick = tick;
            this.id = id;
        }

        void giveBack(UUID player, ItemStack item) {
            giver = player;
            given = item;
        }

        @Override
        public int tick() {
            return tick;
        }
    }

    /**
     * A player using what is in their hands: what they held before, and what came of it.
     */
    public static final class Use {
        final ServerPlayer player;
        final ItemStack main;
        final ItemStack off;
        final @Nullable Use outer;
        final List<Change> made = new ArrayList<>(1);
        final List<Birth> born = new ArrayList<>(0);

        Use(ServerPlayer player, ItemStack main, ItemStack off, @Nullable Use outer) {
            this.player = player;
            this.main = main;
            this.off = off;
            this.outer = outer;
        }
    }

    private record Pending(HexTape tape, @Nullable CompoundTag data) {
    }
}
