package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * A moment in a player's magic that every client nearby should see happen at the same place.
 *
 * @param kind one of the constants below
 */
public record MagicEventPayload(int entityId, int kind, Vec3 position) implements CustomPacketPayload {

    public static final int SHIELD_HIT = 0;
    public static final int SHIELD_SHATTER = 1;
    public static final int LIFT_OFF = 2;
    public static final int TOUCH_DOWN = 3;

    public static final Type<MagicEventPayload> TYPE = new Type<>(Scarlet.id("magic_event"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MagicEventPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MagicEventPayload::entityId,
            ByteBufCodecs.VAR_INT, MagicEventPayload::kind,
            Vec3.STREAM_CODEC, MagicEventPayload::position,
            MagicEventPayload::new);

    @Override
    public Type<MagicEventPayload> type() {
        return TYPE;
    }
}
