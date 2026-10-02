package com.yashjit.scarlet.hex;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * A Hex as clients see it.
 *
 * @param era        {@link Era} ordinal
 * @param phase      {@link Hex.Phase} ordinal
 * @param phaseSince game time the phase began
 */
public record HexSnapshot(UUID caster, String casterName, Vec3 center, float radius, int era, String name, int phase, long phaseSince,
                          float phaseRadius) {

    public static final StreamCodec<ByteBuf, HexSnapshot> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, HexSnapshot::caster,
            ByteBufCodecs.STRING_UTF8, HexSnapshot::casterName,
            Vec3.STREAM_CODEC, HexSnapshot::center,
            ByteBufCodecs.FLOAT, HexSnapshot::radius,
            ByteBufCodecs.VAR_INT, HexSnapshot::era,
            ByteBufCodecs.STRING_UTF8, HexSnapshot::name,
            ByteBufCodecs.VAR_INT, HexSnapshot::phase,
            ByteBufCodecs.VAR_LONG, HexSnapshot::phaseSince,
            ByteBufCodecs.FLOAT, HexSnapshot::phaseRadius,
            HexSnapshot::new);

    public Hex.Phase phaseValue() {
        return Hex.Phase.byIndex(phase);
    }

    public Era eraValue() {
        return Era.byIndex(era);
    }
}
