package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A player held with Mind Control pressed one of their own keys, fighting it.
 */
public record StrugglePayload() implements CustomPacketPayload {

    public static final StrugglePayload INSTANCE = new StrugglePayload();
    public static final Type<StrugglePayload> TYPE = new Type<>(Scarlet.id("struggle"));
    public static final StreamCodec<ByteBuf, StrugglePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<StrugglePayload> type() {
        return TYPE;
    }
}
