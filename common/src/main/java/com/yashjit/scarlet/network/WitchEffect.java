package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record WitchEffect(int caster, int target, Kind kind, Vec3 at, int ticks) implements CustomPacketPayload {
    public enum Kind {
        BLAST, MISS, SIPHON, ASH, RELEASE, THEFT, CHAINS, RUNE, SEAL, CURSE, DOUBLE, NIGHTMARE, FAMILIAR, SIGHT,
        TRANSMUTE, RITUAL, WARD, DARK, GLAMOUR, RIM, COVEN_GATHER, COVEN_BEAM, COVEN_RUNE, COVEN_BURST
    }
    public static final Type<WitchEffect> TYPE = new Type<>(Scarlet.id("witch_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WitchEffect> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WitchEffect::caster, ByteBufCodecs.VAR_INT, WitchEffect::target,
            ByteBufCodecs.VAR_INT.map(i -> Kind.values()[i], Kind::ordinal), WitchEffect::kind,
            Vec3.STREAM_CODEC, WitchEffect::at, ByteBufCodecs.VAR_INT, WitchEffect::ticks, WitchEffect::new);
    @Override public Type<WitchEffect> type() { return TYPE; }
}
