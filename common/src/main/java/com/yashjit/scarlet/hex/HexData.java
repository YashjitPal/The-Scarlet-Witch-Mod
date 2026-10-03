package com.yashjit.scarlet.hex;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.hex.town.HexTown;
import com.yashjit.scarlet.hex.town.HomeRemnant;
import com.yashjit.scarlet.hex.town.TownMemory;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Hexes standing in one dimension, saved with it, whatever their towns still have to put back in chunks that
 * weren't loaded when they fell, and the towns fallen Hexes left behind, for their casters' next Hexes to raise again.
 * Also the homes fallen Hexes have left standing for now, glitching before they go.
 */
public final class HexData extends SavedData {

    private static final Codec<HexData> CURRENT = RecordCodecBuilder.create(i -> i.group(
            Hex.CODEC.listOf().fieldOf("hexes").forGetter(data -> List.copyOf(data.hexes.values())),
            HexTown.BLOCKS_CODEC.optionalFieldOf("pending", new Long2ObjectOpenHashMap<>()).forGetter(HexData::pendingToSave),
            HexTown.Kept.CODEC.listOf().optionalFieldOf("pending_kept", List.of()).forGetter(HexData::pendingKeptToSave),
            TownMemory.CODEC.listOf().optionalFieldOf("towns", List.of()).forGetter(data -> List.copyOf(data.towns)),
            HexPaint.BLOCKS_CODEC.optionalFieldOf("pending_paint", new Long2ObjectOpenHashMap<>()).forGetter(data -> data.pendingPaint),
            Codec.LONG_STREAM.xmap(LongStream::toArray, LongStream::of).optionalFieldOf("decor", new long[0])
                    .forGetter(data -> data.decor.toLongArray())
    ).apply(i, HexData::new));

    // saves from before towns held only the list of Hexes
    private static final Codec<HexData> CODEC = Codec.withAlternative(CURRENT,
            Hex.CODEC.listOf().xmap(hexes -> new HexData(hexes, new Long2ObjectOpenHashMap<>(), List.of(), List.of(), new Long2ObjectOpenHashMap<>(),
                            new long[0]),
                    data -> List.copyOf(data.hexes.values())));

    /** Most towns remembered for each caster and build; past that, the oldest are forgotten. */
    private static final int MAX_TOWNS = 32;

    // saved data must name a data fixer type; command storage's has no fixes, so ours passes through untouched
    public static final SavedDataType<HexData> TYPE = new SavedDataType<>(Scarlet.id("hexes"), HexData::new, CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private static final AtomicLong INSTANCES = new AtomicLong();

    private final Map<UUID, Hex> hexes = new LinkedHashMap<>();
    /** Town blocks waiting for their chunk to load to be put back. */
    private final Long2ObjectOpenHashMap<HexTown.Built> pending;
    /** Paintings, armor stands and the like a town took up, waiting for their chunk to load to be set back down. */
    private final List<HexTown.Kept> pendingKept;
    /** The towns fallen Hexes left behind, oldest first. */
    private final List<TownMemory> towns;
    /** Painted blocks waiting for their chunk to load to change back. */
    private final Long2ObjectOpenHashMap<HexPaint.Painted> pendingPaint;
    /** Era decorations people have put down inside Hexes here, which follow their Hex's era: see {@link HexDecor}. */
    private final LongOpenHashSet decor;
    /** The homes fallen Hexes have left standing for a while, saved only as what is waiting to be put back. */
    private final List<HomeRemnant> remnants = new ArrayList<>();
    private final long instance = INSTANCES.incrementAndGet();
    private int version;

    private HexData() {
        this.pending = new Long2ObjectOpenHashMap<>();
        this.pendingKept = new ArrayList<>();
        this.towns = new ArrayList<>();
        this.pendingPaint = new Long2ObjectOpenHashMap<>();
        this.decor = new LongOpenHashSet();
    }

    private HexData(List<Hex> loaded, Long2ObjectOpenHashMap<HexTown.Built> pending, List<HexTown.Kept> pendingKept, List<TownMemory> towns,
                    Long2ObjectOpenHashMap<HexPaint.Painted> pendingPaint, long[] decor) {
        for (Hex hex : loaded) {
            hexes.put(hex.caster, hex);
        }
        this.pending = new Long2ObjectOpenHashMap<>(pending);
        this.pendingKept = new ArrayList<>(pendingKept);
        this.towns = new ArrayList<>(towns);
        this.pendingPaint = new Long2ObjectOpenHashMap<>(pendingPaint);
        this.decor = new LongOpenHashSet(decor);
    }

    public static HexData of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public @Nullable Hex byCaster(UUID caster) {
        return hexes.get(caster);
    }

    public Collection<Hex> all() {
        return hexes.values();
    }

    /**
     * Whether a Hex's town put down the block at a position, so breaking it gives nothing and pistons can't move it.
     */
    public boolean isBuilt(BlockPos pos) {
        long key = pos.asLong();
        if (pending.containsKey(key)) {
            return true;
        }
        for (Hex hex : hexes.values()) {
            if (hex.town != null && hex.town.isBuilt(key)) {
                return true;
            }
        }
        for (HomeRemnant remnant : remnants) {
            if (remnant.isBuilt(key)) {
                return true;
            }
        }
        return false;
    }

    List<HomeRemnant> remnants() {
        return remnants;
    }

    void addRemnant(HomeRemnant remnant) {
        remnants.add(remnant);
        changed();
    }

    void removeRemnant(HomeRemnant remnant) {
        remnants.remove(remnant);
        changed();
    }

    /**
     * What is waiting to be put back, as saved: with whatever is left of the homes fallen Hexes left standing, to go back
     * as soon as the world is loaded again.
     */
    private Long2ObjectOpenHashMap<HexTown.Built> pendingToSave() {
        if (remnants.isEmpty()) {
            return pending;
        }
        Long2ObjectOpenHashMap<HexTown.Built> all = new Long2ObjectOpenHashMap<>(pending);
        for (HomeRemnant remnant : remnants) {
            remnant.saveBlocksInto(all);
        }
        return all;
    }

    private List<HexTown.Kept> pendingKeptToSave() {
        List<HexTown.Kept> all = new ArrayList<>(pendingKept);
        for (HomeRemnant remnant : remnants) {
            remnant.saveKeptInto(all);
        }
        return all;
    }

    Long2ObjectOpenHashMap<HexTown.Built> pending() {
        return pending;
    }

    List<HexTown.Kept> pendingKept() {
        return pendingKept;
    }

    Long2ObjectOpenHashMap<HexPaint.Painted> pendingPaint() {
        return pendingPaint;
    }

    LongOpenHashSet decor() {
        return decor;
    }

    /**
     * The towns a caster's fallen Hexes of a build left behind here, oldest first.
     */
    List<TownMemory> towns(UUID caster, HexBuild mode) {
        List<TownMemory> found = new ArrayList<>();
        for (TownMemory town : towns) {
            if (town.caster().equals(caster) && town.mode() == mode) {
                found.add(town);
            }
        }
        return found;
    }

    /**
     * Those of them a Hex of the given size around a point could come over, oldest first.
     */
    List<TownMemory> towns(UUID caster, HexBuild mode, Vec3 center, float radius) {
        List<TownMemory> found = towns(caster, mode);
        found.removeIf(town -> {
            double reach = (town.reach() + radius) * HexShape.CORNER;
            double dx = town.center().x - center.x;
            double dz = town.center().z - center.z;
            return dx * dx + dz * dz >= reach * reach;
        });
        return found;
    }

    /**
     * Remembers the town a Hex left behind, unless an older town of its caster's with the same build stood on all the
     * ground it did: that one is what comes back there.
     */
    void remember(TownMemory town) {
        List<TownMemory> theirs = towns(town.caster(), town.mode());
        for (TownMemory old : theirs) {
            if (old.holds(town)) {
                return;
            }
        }
        towns.add(town);
        int over = theirs.size() + 1 - MAX_TOWNS;
        for (int k = 0; k < over; k++) {
            towns.remove(theirs.get(k));
        }
        setDirty();
    }

    /**
     * Forgets every town a caster's Hexes left behind here, so the next one they cast raises a new town wherever it is.
     *
     * @return how many were forgotten
     */
    public int forget(UUID caster) {
        int before = towns.size();
        towns.removeIf(town -> town.caster().equals(caster));
        if (towns.size() != before) {
            setDirty();
        }
        return before - towns.size();
    }

    void add(Hex hex) {
        hexes.put(hex.caster, hex);
        changed();
    }

    void remove(Hex hex) {
        hexes.remove(hex.caster);
        changed();
    }

    /**
     * Marks the Hexes as needing to be saved and sent to the players here again.
     */
    void changed() {
        version++;
        setDirty();
    }

    /**
     * Counts every change, so players can tell when what they were last sent is out of date.
     */
    int version() {
        return version;
    }

    /**
     * Tells this dimension's Hexes apart from those of a world loaded earlier, whose versions count from the same
     * start.
     */
    long instance() {
        return instance;
    }

    List<HexSnapshot> snapshots() {
        List<HexSnapshot> snapshots = new ArrayList<>(hexes.size());
        for (Hex hex : hexes.values()) {
            snapshots.add(hex.snapshot());
        }
        return snapshots;
    }

    List<RemnantSnapshot> remnantSnapshots() {
        List<RemnantSnapshot> snapshots = new ArrayList<>(remnants.size());
        for (HomeRemnant remnant : remnants) {
            snapshots.add(remnant.snapshot());
        }
        return snapshots;
    }
}
