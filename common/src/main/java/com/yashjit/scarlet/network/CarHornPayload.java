package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A driver sounding their car's horn.
 */
public record CarHornPayload() implements CustomPacketPayload {

    public static final Type<CarHornPayload> TYPE = new Type<>(Scarlet.id("car_horn"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CarHornPayload> STREAM_CODEC = StreamCodec.unit(new CarHornPayload());

    @Override
    public Type<CarHornPayload> type() {
        return TYPE;
    }
}
