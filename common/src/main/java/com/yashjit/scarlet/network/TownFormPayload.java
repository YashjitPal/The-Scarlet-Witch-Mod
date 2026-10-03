package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Blocks of a caster's home about to be put down, a moment before they are, so clients can show the magic reaching out
 * for each and the block forming where it goes.
 *
 * @param blocks four ints each: x, y and z of the block, and the ticks from now until it lands
 */
public record TownFormPayload(int[] blocks) implements CustomPacketPayload {

    public static final Type<TownFormPayload> TYPE = new Type<>(Scarlet.id("town_form"));

    public static final StreamCodec<FriendlyByteBuf, TownFormPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeVarIntArray(payload.blocks),
            buf -> new TownFormPayload(buf.readVarIntArray()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
