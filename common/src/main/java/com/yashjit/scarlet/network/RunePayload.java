package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * A Rune Trap's sigil, for everyone near it: where it lies, what has become of it, and what it binds.
 *
 * @param age   ticks since it was inscribed, so a sigil first seen late is drawn as far along as it really is
 * @param stage one of the constants below
 * @param bound the ids of everything it holds, once sprung
 */
public record RunePayload(int id, Vec3 at, int age, int stage, List<Integer> bound) implements CustomPacketPayload {

    /** Writing itself onto the ground, then lying in wait. */
    public static final int INSCRIBED = 0;
    /** Sprung: chains of light holding everything on it. */
    public static final int SPRUNG = 1;
    /** Wiped away. */
    public static final int FADED = 2;

    public static final Type<RunePayload> TYPE = new Type<>(Scarlet.id("rune"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RunePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RunePayload::id,
            Vec3.STREAM_CODEC, RunePayload::at,
            ByteBufCodecs.VAR_INT, RunePayload::age,
            ByteBufCodecs.VAR_INT, RunePayload::stage,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64)), RunePayload::bound,
            RunePayload::new);

    @Override
    public Type<RunePayload> type() {
        return TYPE;
    }
}
