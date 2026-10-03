package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * How a caster inside a mind they hold with Mind Control wants it to move, every tick: their movement keys as
 * {@code forward} and {@code strafe} (from -1 to 1, positive strafe to the left), the keys in {@code flags}, and where
 * they look.
 */
public record ControlPayload(float forward, float strafe, int flags, float yaw, float pitch) implements CustomPacketPayload {

    public static final int JUMP = 1;
    public static final int SNEAK = 2;
    public static final int SPRINT = 4;
    /** Attack was pressed since the last one sent. */
    public static final int ATTACK = 8;

    public static final ControlPayload IDLE = new ControlPayload(0.0F, 0.0F, 0, 0.0F, 0.0F);

    public static final Type<ControlPayload> TYPE = new Type<>(Scarlet.id("control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ControlPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ControlPayload::forward,
            ByteBufCodecs.FLOAT, ControlPayload::strafe,
            ByteBufCodecs.VAR_INT, ControlPayload::flags,
            ByteBufCodecs.FLOAT, ControlPayload::yaw,
            ByteBufCodecs.FLOAT, ControlPayload::pitch,
            ControlPayload::new);

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    @Override
    public Type<ControlPayload> type() {
        return TYPE;
    }
}
