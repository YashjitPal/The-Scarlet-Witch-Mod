package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client asks to cast a spell ({@code start}) or to let go of a channeled one.
 */
public record CastPayload(boolean start, int spell) implements CustomPacketPayload {

    public static final Type<CastPayload> TYPE = new Type<>(Scarlet.id("cast"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CastPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, CastPayload::start,
            ByteBufCodecs.VAR_INT, CastPayload::spell,
            CastPayload::new);

    @Override
    public Type<CastPayload> type() {
        return TYPE;
    }
}
