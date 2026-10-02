package com.yashjit.scarlet.hex;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.hex.town.HexTown;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * The Hexes standing in one dimension, saved with it, and whatever their towns still have to put back in chunks that
 * weren't loaded when they fell.
 */
public final class HexData extends SavedData {

    private static final Codec<HexData> CURRENT = RecordCodecBuilder.create(i -> i.group(
            Hex.CODEC.listOf().fieldOf("hexes").forGetter(data -> List.copyOf(data.hexes.values())),
            HexTown.BLOCKS_CODEC.optionalFieldOf("pending", new Long2ObjectOpenHashMap<>()).forGetter(data -> data.pending)
    ).apply(i, HexData::new));

    // saves from before towns held only the list of Hexes
    private static final Codec<HexData> CODEC = Codec.withAlternative(CURRENT,
            Hex.CODEC.listOf().xmap(hexes -> new HexData(hexes, new Long2ObjectOpenHashMap<>()), data -> List.copyOf(data.hexes.values())));

    // saved data must name a data fixer type; command storage's has no fixes, so ours passes through untouched
    public static final SavedDataType<HexData> TYPE = new SavedDataType<>(Scarlet.id("hexes"), HexData::new, CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private static final AtomicLong INSTANCES = new AtomicLong();

    private final Map<UUID, Hex> hexes = new LinkedHashMap<>();
    /** Town blocks waiting for their chunk to load to be put back. */
    private final Long2ObjectOpenHashMap<HexTown.Built> pending;
    private final long instance = INSTANCES.incrementAndGet();
    private int version;

    private HexData() {
        this.pending = new Long2ObjectOpenHashMap<>();
    }

    private HexData(List<Hex> loaded, Long2ObjectOpenHashMap<HexTown.Built> pending) {
        for (Hex hex : loaded) {
            hexes.put(hex.caster, hex);
        }
        this.pending = new Long2ObjectOpenHashMap<>(pending);
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
        return false;
    }

    Long2ObjectOpenHashMap<HexTown.Built> pending() {
        return pending;
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
}
