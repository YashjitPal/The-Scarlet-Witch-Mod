package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A part of a Hex's town has begun building itself, so clients can trace its lot in light and follow it up as it
 * rises.
 *
 * @param kind     {@link com.yashjit.scarlet.hex.town.TownPlan.Kind} ordinal
 * @param ground   the level it is laid at
 * @param height   how tall it stands over that
 * @param start    game time it began
 * @param duration ticks it takes
 */
public record TownBuildPayload(int kind, int minX, int minZ, int maxX, int maxZ, int ground, int height, long start, int duration)
        implements CustomPacketPayload {

    public static final Type<TownBuildPayload> TYPE = new Type<>(Scarlet.id("town_build"));

    public static final StreamCodec<FriendlyByteBuf, TownBuildPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.kind);
                buf.writeVarInt(payload.minX);
                buf.writeVarInt(payload.minZ);
                buf.writeVarInt(payload.maxX);
                buf.writeVarInt(payload.maxZ);
                buf.writeVarInt(payload.ground);
                buf.writeVarInt(payload.height);
                buf.writeVarLong(payload.start);
                buf.writeVarInt(payload.duration);
            },
            buf -> new TownBuildPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarLong(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
