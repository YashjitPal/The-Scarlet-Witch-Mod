package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Something struck or passed through a Hex wall at {@code at}: the wall flares red there and ripples out from it,
 * more the greater the {@code strength} (1 for a chaos blast).
 */
public record HexRipplePayload(Vec3 at, float strength) implements CustomPacketPayload {

    public static final Type<HexRipplePayload> TYPE = new Type<>(Scarlet.id("hex_ripple"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HexRipplePayload> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, HexRipplePayload::at,
            ByteBufCodecs.FLOAT, HexRipplePayload::strength,
            HexRipplePayload::new);

    @Override
    public Type<HexRipplePayload> type() {
        return TYPE;
    }
}
