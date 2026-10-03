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
    /** Energy gathering between the hands, at the caster's feet. */
    public static final int SHOCKWAVE_GATHER = 4;
    /** The wave bursting out, from the caster's feet. */
    public static final int SHOCKWAVE = 5;
    /** The caster dissolving, where they stand. */
    public static final int MIST_OUT = 6;
    /** The caster forming again, where they arrive. */
    public static final int MIST_IN = 7;
    /** A block torn out of the ground by Telekinesis, at the hole it left. */
    public static final int TORN_OUT = 8;
    /** Something thrown by Telekinesis slamming into the world, where it hit. */
    public static final int SLAM = 9;
    /** A Rune Trap cast with nowhere to write it, sputtering out in the hand. */
    public static final int RUNE_FIZZLE = 10;
    /** A dreamwalker's spirit leaving their body, at the body's feet. */
    public static final int DREAM_DEPART = 11;
    /** A dreamwalker's spirit coming back into their body, at its feet. */
    public static final int DREAM_WAKE = 12;

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
