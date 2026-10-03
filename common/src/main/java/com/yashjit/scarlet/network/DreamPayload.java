package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A dreamwalker asking where their spirit could go, sending it there, or waking.
 *
 * @param action    one of the constants below
 * @param dimension where to go, for {@link #GO}
 */
public record DreamPayload(int action, Optional<ResourceKey<Level>> dimension) implements CustomPacketPayload {

    /** The dimension picker has opened: which dimensions can be reached, and where in each the spirit would arrive. */
    public static final int ASK = 0;
    public static final int GO = 1;
    public static final int WAKE = 2;

    public static final DreamPayload ASKING = new DreamPayload(ASK, Optional.empty());
    public static final DreamPayload WAKING = new DreamPayload(WAKE, Optional.empty());

    public static final Type<DreamPayload> TYPE = new Type<>(Scarlet.id("dream"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DreamPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DreamPayload::action,
            ByteBufCodecs.optional(ResourceKey.streamCodec(Registries.DIMENSION)), DreamPayload::dimension,
            DreamPayload::new);

    public static DreamPayload go(ResourceKey<Level> dimension) {
        return new DreamPayload(GO, Optional.of(dimension));
    }

    @Override
    public Type<DreamPayload> type() {
        return TYPE;
    }
}
