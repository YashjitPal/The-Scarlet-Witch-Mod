package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Where a dreamwalker's spirit could go: each dimension, and where in it they would arrive.
 */
public record DreamOptionsPayload(List<Destination> destinations) implements CustomPacketPayload {

    /** Near where they last stood there. */
    public static final int LAST_STOOD = 0;
    /** Near where they would wake from death, there. */
    public static final int RESPAWN = 1;
    /** Near the world's spawn. */
    public static final int WORLD_SPAWN = 2;
    /** Somewhere they have never been: near where a portal would take them, or the End's landing. */
    public static final int UNKNOWN = 3;

    public static final Type<DreamOptionsPayload> TYPE = new Type<>(Scarlet.id("dream_options"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DreamOptionsPayload> STREAM_CODEC = StreamCodec.composite(
            Destination.STREAM_CODEC.apply(ByteBufCodecs.list(64)), DreamOptionsPayload::destinations,
            DreamOptionsPayload::new);

    @Override
    public Type<DreamOptionsPayload> type() {
        return TYPE;
    }

    /**
     * @param arrival one of the constants above
     */
    public record Destination(ResourceKey<Level> dimension, int arrival) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Destination> STREAM_CODEC = StreamCodec.composite(
                ResourceKey.streamCodec(Registries.DIMENSION), Destination::dimension,
                ByteBufCodecs.VAR_INT, Destination::arrival,
                Destination::new);
    }
}
