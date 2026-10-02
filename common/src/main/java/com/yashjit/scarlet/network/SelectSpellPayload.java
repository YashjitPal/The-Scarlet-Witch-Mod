package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client picked a spell in the wheel.
 */
public record SelectSpellPayload(int spell) implements CustomPacketPayload {

    public static final Type<SelectSpellPayload> TYPE = new Type<>(Scarlet.id("select_spell"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectSpellPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, SelectSpellPayload::spell, SelectSpellPayload::new);

    @Override
    public Type<SelectSpellPayload> type() {
        return TYPE;
    }
}
