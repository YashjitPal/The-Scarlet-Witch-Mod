package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * A caster throwing someone out of their Hex, for everyone who can see it, and for whoever is thrown.
 *
 * @param stage one of the constants below
 * @param from  where they hang while held, and where the throw starts from
 * @param to    where the throw lands them, outside the wall
 * @param ticks how long the throw takes
 */
public record EjectPayload(int casterId, int targetId, int stage, Vec3 from, Vec3 to, int ticks) implements CustomPacketPayload {

    /** Lifted off the ground and held hanging there. */
    public static final int SEIZED = 0;
    /** Flung out through the wall. */
    public static final int FLUNG = 1;
    /** Let down again without being thrown. */
    public static final int DROPPED = 2;

    public static final Type<EjectPayload> TYPE = new Type<>(Scarlet.id("eject"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EjectPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EjectPayload::casterId,
            ByteBufCodecs.VAR_INT, EjectPayload::targetId,
            ByteBufCodecs.VAR_INT, EjectPayload::stage,
            Vec3.STREAM_CODEC, EjectPayload::from,
            Vec3.STREAM_CODEC, EjectPayload::to,
            ByteBufCodecs.VAR_INT, EjectPayload::ticks,
            EjectPayload::new);

    @Override
    public Type<EjectPayload> type() {
        return TYPE;
    }
}
