package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Blocks of a home left behind by its Hex about to go, a moment before they do, so clients can show each glitching out
 * the way it glitched in.
 *
 * @param blocks four ints each: x, y and z of the block, and the ticks from now until it goes
 */
public record TownUnformPayload(int[] blocks) implements CustomPacketPayload {

    public static final Type<TownUnformPayload> TYPE = new Type<>(Scarlet.id("town_unform"));

    public static final StreamCodec<FriendlyByteBuf, TownUnformPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeVarIntArray(payload.blocks),
            buf -> new TownUnformPayload(buf.readVarIntArray()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
