package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Which mobs in the player's dimension are a Hex's townspeople, by entity id; which of them stand out past where
 * their Hex's caster can see; and whether the player does.
 */
public record ResidentsPayload(int[] ids, int[] paused, boolean unwatched) implements CustomPacketPayload {

    public static final Type<ResidentsPayload> TYPE = new Type<>(Scarlet.id("residents"));

    public static final StreamCodec<FriendlyByteBuf, ResidentsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarIntArray(payload.ids);
                buf.writeVarIntArray(payload.paused);
                buf.writeBoolean(payload.unwatched);
            },
            buf -> new ResidentsPayload(buf.readVarIntArray(), buf.readVarIntArray(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
