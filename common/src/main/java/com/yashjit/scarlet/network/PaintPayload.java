package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Blocks restyled inside a Hex, so clients can show each one being written in, and who is painting them.
 *
 * @param casterId who is painting, sent every few ticks while they are; -1 for blocks changing back on their own
 * @param blocks   three ints each: x, y and z of a block just painted, or just changed back
 */
public record PaintPayload(int casterId, int[] blocks) implements CustomPacketPayload {

    public static final Type<PaintPayload> TYPE = new Type<>(Scarlet.id("paint"));

    public static final StreamCodec<FriendlyByteBuf, PaintPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.casterId + 1);
                buf.writeVarIntArray(payload.blocks);
            },
            buf -> new PaintPayload(buf.readVarInt() - 1, buf.readVarIntArray()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
