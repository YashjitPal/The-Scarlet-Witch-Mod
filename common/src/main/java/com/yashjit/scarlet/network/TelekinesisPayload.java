package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client steers what it holds with Telekinesis: {@link #PULL} it nearer or push it away by {@code amount} (scroll
 * steps, positive to push), or {@link #THROW} it.
 */
public record TelekinesisPayload(int action, float amount) implements CustomPacketPayload {

    public static final int PULL = 0;
    public static final int THROW = 1;

    public static final Type<TelekinesisPayload> TYPE = new Type<>(Scarlet.id("telekinesis"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TelekinesisPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TelekinesisPayload::action,
            ByteBufCodecs.FLOAT, TelekinesisPayload::amount,
            TelekinesisPayload::new);

    @Override
    public Type<TelekinesisPayload> type() {
        return TYPE;
    }
}
