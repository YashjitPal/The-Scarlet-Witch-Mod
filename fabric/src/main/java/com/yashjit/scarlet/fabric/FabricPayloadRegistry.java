package com.yashjit.scarlet.fabric;

import com.yashjit.scarlet.network.PayloadRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

final class FabricPayloadRegistry implements PayloadRegistry {

    @Override
    public <T extends CustomPacketPayload> void serverbound(CustomPacketPayload.Type<T> type,
                                                            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                            ServerHandler<T> handler) {
        PayloadTypeRegistry.serverboundPlay().register(type, codec);
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.handle(payload, context.player()));
    }

    @Override
    public <T extends CustomPacketPayload> void clientbound(CustomPacketPayload.Type<T> type,
                                                            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        PayloadTypeRegistry.clientboundPlay().register(type, codec);
    }
}
