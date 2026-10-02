package com.yashjit.scarlet.neoforge;

import com.yashjit.scarlet.network.PayloadRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

record NeoForgePayloadRegistry(PayloadRegistrar registrar) implements PayloadRegistry {

    @Override
    public <T extends CustomPacketPayload> void serverbound(CustomPacketPayload.Type<T> type,
                                                            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                            ServerHandler<T> handler) {
        registrar.playToServer(type, codec, (payload, context) -> handler.handle(payload, (ServerPlayer) context.player()));
    }

    @Override
    public <T extends CustomPacketPayload> void clientbound(CustomPacketPayload.Type<T> type,
                                                            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        registrar.playToClient(type, codec);
    }
}
