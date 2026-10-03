package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * What a caster holds with Telekinesis, for everyone who can see them: an entity id, or {@link #NOTHING}.
 */
public record HoldPayload(int casterId, int targetId) implements CustomPacketPayload {

    public static final int NOTHING = -1;

    public static final Type<HoldPayload> TYPE = new Type<>(Scarlet.id("hold"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HoldPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HoldPayload::casterId,
            ByteBufCodecs.VAR_INT, HoldPayload::targetId,
            HoldPayload::new);

    @Override
    public Type<HoldPayload> type() {
        return TYPE;
    }
}
