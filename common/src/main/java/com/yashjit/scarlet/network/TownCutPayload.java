package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Bits of a Hex's town have just gone back to what stood there before, as its wall passed over them moving in or as it
 * fell, so clients can show them glitching out in red where they went.
 *
 * @param cells two-by-two columns where something went, four ints each: x and z of the cell's corner, and the lowest
 *              and highest y of what went from it
 */
public record TownCutPayload(int[] cells) implements CustomPacketPayload {

    public static final Type<TownCutPayload> TYPE = new Type<>(Scarlet.id("town_cut"));

    public static final StreamCodec<FriendlyByteBuf, TownCutPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeVarIntArray(payload.cells),
            buf -> new TownCutPayload(buf.readVarIntArray()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
