package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A townsperson making a gesture, for everyone who can see them: waving to someone, or talking with their hands.
 */
public record GesturePayload(int entityId, Kind kind) implements CustomPacketPayload {

    public enum Kind {
        WAVE(40),
        TALK_RIGHT(18),
        TALK_LEFT(18);

        /** How long it lasts, in ticks. */
        public final int ticks;

        Kind(int ticks) {
            this.ticks = ticks;
        }
    }

    public static final Type<GesturePayload> TYPE = new Type<>(Scarlet.id("gesture"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GesturePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GesturePayload::entityId,
            ByteBufCodecs.VAR_INT.map(i -> Kind.values()[Math.floorMod(i, Kind.values().length)], Kind::ordinal), GesturePayload::kind,
            GesturePayload::new);

    @Override
    public Type<GesturePayload> type() {
        return TYPE;
    }
}
