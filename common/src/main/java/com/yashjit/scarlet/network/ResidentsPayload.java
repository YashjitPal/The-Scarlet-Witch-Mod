package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Which mobs in the player's dimension are a Hex's townspeople, by entity id.
 */
public record ResidentsPayload(int[] ids) implements CustomPacketPayload {

    public static final Type<ResidentsPayload> TYPE = new Type<>(Scarlet.id("residents"));

    public static final StreamCodec<FriendlyByteBuf, ResidentsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeVarIntArray(payload.ids),
            buf -> new ResidentsPayload(buf.readVarIntArray()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
