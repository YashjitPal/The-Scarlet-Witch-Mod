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
 * @param eraSince   game time the era last changed
 * @param episode    the episode on, counting from 1 each season
 */
public record HexSnapshot(UUID caster, String casterName, Vec3 center, float radius, int era, String name, int phase, long phaseSince,
                          float phaseRadius, long eraSince, int episode, int season) {

    public static final StreamCodec<ByteBuf, HexSnapshot> STREAM_CODEC = StreamCodec.of(
            (buf, hex) -> {
                UUIDUtil.STREAM_CODEC.encode(buf, hex.caster);
                ByteBufCodecs.STRING_UTF8.encode(buf, hex.casterName);
                Vec3.STREAM_CODEC.encode(buf, hex.center);
                buf.writeFloat(hex.radius);
                ByteBufCodecs.VAR_INT.encode(buf, hex.era);
                ByteBufCodecs.STRING_UTF8.encode(buf, hex.name);
                ByteBufCodecs.VAR_INT.encode(buf, hex.phase);
                ByteBufCodecs.VAR_LONG.encode(buf, hex.phaseSince);
                buf.writeFloat(hex.phaseRadius);
                ByteBufCodecs.VAR_LONG.encode(buf, hex.eraSince);
                ByteBufCodecs.VAR_INT.encode(buf, hex.episode);
                ByteBufCodecs.VAR_INT.encode(buf, hex.season);
            },
            buf -> new HexSnapshot(UUIDUtil.STREAM_CODEC.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), Vec3.STREAM_CODEC.decode(buf),
                    buf.readFloat(), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf), buf.readFloat(), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf)));

    public Hex.Phase phaseValue() {
        return Hex.Phase.byIndex(phase);
    }

    public Era eraValue() {
        return Era.byIndex(era);
    }
}
