package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client asks the server to manifest or dismiss the costume.
 */
public record ToggleSuitPayload() implements CustomPacketPayload {

    public static final ToggleSuitPayload INSTANCE = new ToggleSuitPayload();
    public static final Type<ToggleSuitPayload> TYPE = new Type<>(Scarlet.id("toggle_suit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleSuitPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<ToggleSuitPayload> type() {
        return TYPE;
    }
}
