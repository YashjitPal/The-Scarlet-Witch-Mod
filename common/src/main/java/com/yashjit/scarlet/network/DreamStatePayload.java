package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A dreamwalker's spirit arriving in a creature, or leaving it: for the dreamwalker, whose view goes into it and comes
 * back, and for everyone who can see the creature, whose eyes burn while it is held.
 *
 * @param stage    one of the constants below
 * @param entityId the creature the spirit is in
 * @param darkness how far the Darkhold has darkened the dreamwalker's magic, 0 to 1
 */
public record DreamStatePayload(int stage, int entityId, float darkness) implements CustomPacketPayload {

    /** To the dreamwalker: look out through this creature, once it is here. */
    public static final int AWAY = 0;
    /** To everyone who can see it: a spirit is in this creature. */
    public static final int POSSESSED = 1;
    /** To everyone who can see it: the spirit has left this creature. */
    public static final int RELEASED = 2;
    /** To the dreamwalker: the view goes back to the body. */
    public static final int WOKE = 3;

    public static final Type<DreamStatePayload> TYPE = new Type<>(Scarlet.id("dream_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DreamStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DreamStatePayload::stage,
            ByteBufCodecs.VAR_INT, DreamStatePayload::entityId,
            ByteBufCodecs.FLOAT, DreamStatePayload::darkness,
            DreamStatePayload::new);

    @Override
    public Type<DreamStatePayload> type() {
        return TYPE;
    }
}
