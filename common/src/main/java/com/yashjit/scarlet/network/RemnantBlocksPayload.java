package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * What a home left behind by a fallen Hex is built of, sent once to those near enough to see it, so their clients can
 * show it glitching through the eras without being told every flicker.
 *
 * @param id        which home left behind it is, as {@link com.yashjit.scarlet.hex.RemnantSnapshot} has it
 * @param era       {@link com.yashjit.scarlet.hex.Era} ordinal it was built in
 * @param positions each of its blocks
 * @param roles     what each is for, a {@link com.yashjit.scarlet.hex.town.Role} ordinal
 * @param paints    which of its role's variants each is
 */
public record RemnantBlocksPayload(int id, int era, long[] positions, byte[] roles, byte[] paints) implements CustomPacketPayload {

    public static final Type<RemnantBlocksPayload> TYPE = new Type<>(Scarlet.id("remnant_blocks"));

    public static final StreamCodec<FriendlyByteBuf, RemnantBlocksPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.id);
                buf.writeVarInt(payload.era);
                buf.writeLongArray(payload.positions);
                buf.writeByteArray(payload.roles);
                buf.writeByteArray(payload.paints);
            },
            buf -> new RemnantBlocksPayload(buf.readVarInt(), buf.readVarInt(), buf.readLongArray(), buf.readByteArray(), buf.readByteArray()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
