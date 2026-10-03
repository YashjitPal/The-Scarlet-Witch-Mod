package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Whose mind a caster has hold of with Mind Control, for everyone who can see them, and for whoever is held.
 *
 * @param stage one of the constants below
 */
public record PossessPayload(int casterId, int targetId, int stage) implements CustomPacketPayload {

    /** Let go: the tendrils snap back out of {@code targetId}. */
    public static final int RELEASED = 0;
    /** Tendrils reaching into its head. */
    public static final int SEIZING = 1;
    /** The caster's view has moved into it, and they steer it. */
    public static final int INSIDE = 2;
    /** Set free, but still loyal to the caster for a while. */
    public static final int LOYAL = 3;

    public static final Type<PossessPayload> TYPE = new Type<>(Scarlet.id("possess"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PossessPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PossessPayload::casterId,
            ByteBufCodecs.VAR_INT, PossessPayload::targetId,
            ByteBufCodecs.VAR_INT, PossessPayload::stage,
            PossessPayload::new);

    @Override
    public Type<PossessPayload> type() {
        return TYPE;
    }
}
