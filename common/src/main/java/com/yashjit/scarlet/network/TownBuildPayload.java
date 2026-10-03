package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A part of a Hex's town has begun building itself, so clients can show whatever stood there dissolving, trace its lot
 * in light and follow it up as it rises.
 *
 * @param kind       {@link com.yashjit.scarlet.hex.town.TownPlan.Kind} ordinal
 * @param ground     the level it is laid at
 * @param height     how tall it stands over that
 * @param start      game time it began
 * @param duration   ticks it takes, dissolving and building
 * @param clearTop   the height of the highest block of what stood there, dissolving first
 * @param clearTicks ticks the dissolving takes before it builds; 0 if the land was open
 * @param front      which way it faces, out toward its street, as a {@link net.minecraft.core.Direction} 2D data value
 * @param ownHome    whether it is its caster's own home, rather than one of a town their Hex brings back
 */
public record TownBuildPayload(int kind, int minX, int minZ, int maxX, int maxZ, int ground, int height, long start, int duration, int clearTop,
                               int clearTicks, int front, boolean ownHome) implements CustomPacketPayload {

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
                buf.writeVarInt(payload.clearTop);
                buf.writeVarInt(payload.clearTicks);
                buf.writeByte(payload.front);
                buf.writeBoolean(payload.ownHome);
            },
            buf -> new TownBuildPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
