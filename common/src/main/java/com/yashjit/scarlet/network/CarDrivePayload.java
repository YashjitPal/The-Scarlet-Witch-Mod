package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A driver's own game, which moves their car, telling the server how fast it goes and how hard it just ran into a wall,
 * if it did: all the server sees of a car someone drives is where it has got to.
 *
 * @param speed how fast it goes, in blocks a tick, forward for positive
 * @param crash how fast it was going when it ran into a wall this tick, or 0
 */
public record CarDrivePayload(float speed, float crash) implements CustomPacketPayload {

    public static final Type<CarDrivePayload> TYPE = new Type<>(Scarlet.id("car_drive"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CarDrivePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, CarDrivePayload::speed,
            ByteBufCodecs.FLOAT, CarDrivePayload::crash,
            CarDrivePayload::new);

    @Override
    public Type<CarDrivePayload> type() {
        return TYPE;
    }
}
