package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Where a chaos bolt burst, exactly as the server saw it, so every client bursts it in the same place.
 *
 * @param normal    the surface it hit, or back along its flight
 * @param direction which way it was flying
 */
public record ChaosImpactPayload(Vec3 position, Vec3 normal, Vec3 direction, boolean hitEntity) implements CustomPacketPayload {

    public static final Type<ChaosImpactPayload> TYPE = new Type<>(Scarlet.id("chaos_impact"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChaosImpactPayload> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, ChaosImpactPayload::position,
            Vec3.STREAM_CODEC, ChaosImpactPayload::normal,
            Vec3.STREAM_CODEC, ChaosImpactPayload::direction,
            ByteBufCodecs.BOOL, ChaosImpactPayload::hitEntity,
            ChaosImpactPayload::new);

    @Override
    public Type<ChaosImpactPayload> type() {
        return TYPE;
    }
}
