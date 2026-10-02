package com.yashjit.scarlet.hex;

import com.mojang.serialization.Codec;
import com.yashjit.scarlet.Scarlet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * The Hexes standing in one dimension, saved with it.
 */
public final class HexData extends SavedData {

    private static final Codec<HexData> CODEC = Hex.CODEC.listOf().xmap(HexData::new, data -> List.copyOf(data.hexes.values()));

    // saved data must name a data fixer type; command storage's has no fixes, so ours passes through untouched
    public static final SavedDataType<HexData> TYPE = new SavedDataType<>(Scarlet.id("hexes"), HexData::new, CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private static final AtomicLong INSTANCES = new AtomicLong();

    private final Map<UUID, Hex> hexes = new LinkedHashMap<>();
    private final long instance = INSTANCES.incrementAndGet();
    private int version;

    private HexData() {
    }

    private HexData(List<Hex> loaded) {
        for (Hex hex : loaded) {
            hexes.put(hex.caster, hex);
        }
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
