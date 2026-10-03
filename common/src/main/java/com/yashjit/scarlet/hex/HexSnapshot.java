package com.yashjit.scarlet.hex;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Hex as clients see it.
 *
 * @param era         {@link Era} ordinal
 * @param phase       {@link Hex.Phase} ordinal
 * @param phaseSince  game time the phase began
 * @param eraSince    game time the era last changed
 * @param previousEra {@link Era} ordinal of the era before, still showing wherever the new one hasn't spread to yet
 * @param episode     the episode on, counting from 1 each season
 * @param tearAt      where its caster took hold of its wall to part it, while it stands parted
 * @param opening     how far each edge of the opening stands from there, in blocks
 * @param parting     whether its caster has hold of the opening right now
 * @param stress      how shaken it was by the last blow to its caster, at {@code stressAt}
 * @param sky         the time of day and weather its caster has set inside it
 * @param episodes    whether the era moves on by itself every morning
 */
public record HexSnapshot(UUID caster, String casterName, Vec3 center, float radius, int era, String name, int phase, long phaseSince,
                          float phaseRadius, long eraSince, int previousEra, int episode, int season, @Nullable Vec3 tearAt, float opening,
                          boolean parting, float stress, long stressAt, HexSky sky, boolean episodes) {

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
                ByteBufCodecs.VAR_INT.encode(buf, hex.previousEra);
                ByteBufCodecs.VAR_INT.encode(buf, hex.episode);
                ByteBufCodecs.VAR_INT.encode(buf, hex.season);
                buf.writeBoolean(hex.tearAt != null);
                if (hex.tearAt != null) {
                    Vec3.STREAM_CODEC.encode(buf, hex.tearAt);
                    buf.writeFloat(hex.opening);
                    buf.writeBoolean(hex.parting);
                }
                buf.writeFloat(hex.stress);
                buf.writeLong(hex.stressAt);
                buf.writeByte(hex.sky.time().ordinal());
                buf.writeByte(hex.sky.weather().ordinal());
                buf.writeBoolean(hex.episodes);
            },
            buf -> {
                UUID caster = UUIDUtil.STREAM_CODEC.decode(buf);
                String casterName = ByteBufCodecs.STRING_UTF8.decode(buf);
                Vec3 center = Vec3.STREAM_CODEC.decode(buf);
                float radius = buf.readFloat();
                int era = ByteBufCodecs.VAR_INT.decode(buf);
                String name = ByteBufCodecs.STRING_UTF8.decode(buf);
                int phase = ByteBufCodecs.VAR_INT.decode(buf);
                long phaseSince = ByteBufCodecs.VAR_LONG.decode(buf);
                float phaseRadius = buf.readFloat();
                long eraSince = ByteBufCodecs.VAR_LONG.decode(buf);
                int previousEra = ByteBufCodecs.VAR_INT.decode(buf);
                int episode = ByteBufCodecs.VAR_INT.decode(buf);
                int season = ByteBufCodecs.VAR_INT.decode(buf);
                Vec3 tearAt = null;
                float opening = 0.0F;
                boolean parting = false;
                if (buf.readBoolean()) {
                    tearAt = Vec3.STREAM_CODEC.decode(buf);
                    opening = buf.readFloat();
                    parting = buf.readBoolean();
                }
                float stress = buf.readFloat();
                long stressAt = buf.readLong();
                HexSky sky = new HexSky(HexSky.Time.byIndex(buf.readByte()), HexSky.Weather.byIndex(buf.readByte()));
                boolean episodes = buf.readBoolean();
                return new HexSnapshot(caster, casterName, center, radius, era, name, phase, phaseSince, phaseRadius, eraSince, previousEra, episode,
                        season, tearAt, opening, parting, stress, stressAt, sky, episodes);
            });

    public Hex.Phase phaseValue() {
        return Hex.Phase.byIndex(phase);
    }

    public Era eraValue() {
        return Era.byIndex(era);
    }

    public Era previousEraValue() {
        return Era.byIndex(previousEra);
    }

    public boolean parted() {
        return tearAt != null;
    }

    public float unrest(double now) {
        return HexUnrest.unrest(tearAt == null ? -1.0F : opening, HexUnrest.stress(stress, stressAt, now));
    }
}
