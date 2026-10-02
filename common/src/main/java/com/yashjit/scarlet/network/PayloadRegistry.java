package com.yashjit.scarlet.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Loader-neutral view of payload registration. Each loader adapts it to its own networking API at the right time.
 */
public interface PayloadRegistry {

    <T extends CustomPacketPayload> void serverbound(CustomPacketPayload.Type<T> type,
                                                     StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                     ServerHandler<T> handler);

    /**
     * Declares a clientbound payload. Its handler is registered separately from client-only code.
     */
    <T extends CustomPacketPayload> void clientbound(CustomPacketPayload.Type<T> type,
                                                     StreamCodec<? super RegistryFriendlyByteBuf, T> codec);

    /**
     * Runs on the server thread.
     */
    @FunctionalInterface
    interface ServerHandler<T> {
        void handle(T payload, ServerPlayer player);
    }
}
