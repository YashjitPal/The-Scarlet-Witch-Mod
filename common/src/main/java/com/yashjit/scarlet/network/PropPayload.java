package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A caster taking a decoration, or a car, out of thin air inside their own Hex.
 *
 * @param index which, in the order {@link com.yashjit.scarlet.decor.Props#items} lists them
 */
public record PropPayload(int index) implements CustomPacketPayload {

    public static final Type<PropPayload> TYPE = new Type<>(Scarlet.id("prop"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PropPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PropPayload::index,
            PropPayload::new);

    @Override
    public Type<PropPayload> type() {
        return TYPE;
    }
}
