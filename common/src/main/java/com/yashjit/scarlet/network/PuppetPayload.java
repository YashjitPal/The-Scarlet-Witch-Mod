package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Passed on to a player held with Mind Control every tick: the controls of whoever holds them, which their own game
 * plays as if they were pressing them.
 */
public record PuppetPayload(ControlPayload control) implements CustomPacketPayload {

    public static final Type<PuppetPayload> TYPE = new Type<>(Scarlet.id("puppet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PuppetPayload> STREAM_CODEC = ControlPayload.STREAM_CODEC.map(PuppetPayload::new,
            PuppetPayload::control);

    @Override
    public Type<PuppetPayload> type() {
        return TYPE;
    }
}
