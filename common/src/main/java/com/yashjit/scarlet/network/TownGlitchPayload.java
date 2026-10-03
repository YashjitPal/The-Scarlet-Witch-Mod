package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A part of a Hex's town slipping for a moment: clients show each of {@code positions} as the block state with the
 * matching id in {@code states} on every tick whose bit is set in {@code pattern}, counting from when it arrives, and
 * as it really is in between and afterwards. Nothing really changes.
 */
public record TownGlitchPayload(long[] positions, int[] states, int pattern) implements CustomPacketPayload {

    /** Ticks a glitch can last, one bit of the pattern each. */
    public static final int TICKS = 16;

    public static final Type<TownGlitchPayload> TYPE = new Type<>(Scarlet.id("town_glitch"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TownGlitchPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeLongArray(payload.positions);
                buf.writeVarIntArray(payload.states);
                buf.writeVarInt(payload.pattern);
            },
            buf -> new TownGlitchPayload(buf.readLongArray(), buf.readVarIntArray(), buf.readVarInt()));

    @Override
    public Type<TownGlitchPayload> type() {
        return TYPE;
    }
}
