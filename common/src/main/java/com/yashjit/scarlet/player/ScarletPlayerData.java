package com.yashjit.scarlet.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yashjit.scarlet.hex.HexBuild;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Immutable per-player state. Powers live on the crown, so this only holds things about the person: whether the
 * costume is manifested and when it last changed (game time), which drives the transformation animation on every
 * client, and what their Hex builds when they cast it.
 */
public record ScarletPlayerData(boolean suited, long suitChangedAt, HexBuild hexBuild) {

    /**
     * Far enough in the past that no transformation is ever mid-animation for a fresh player.
     */
    public static final long NEVER = Long.MIN_VALUE / 4;

    public static final ScarletPlayerData DEFAULT = new ScarletPlayerData(false, NEVER, HexBuild.TOWN);

    public static final MapCodec<ScarletPlayerData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("suited", false).forGetter(ScarletPlayerData::suited),
            Codec.LONG.optionalFieldOf("suit_changed_at", NEVER).forGetter(ScarletPlayerData::suitChangedAt),
            HexBuild.CODEC.optionalFieldOf("hex_build", HexBuild.TOWN).forGetter(ScarletPlayerData::hexBuild)
    ).apply(instance, ScarletPlayerData::new));

    public static final Codec<ScarletPlayerData> CODEC = MAP_CODEC.codec();

    public static final StreamCodec<ByteBuf, ScarletPlayerData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ScarletPlayerData::suited,
            ByteBufCodecs.LONG, ScarletPlayerData::suitChangedAt,
            ByteBufCodecs.VAR_INT.map(HexBuild::byIndex, HexBuild::ordinal), ScarletPlayerData::hexBuild,
            ScarletPlayerData::new);

    public ScarletPlayerData withSuited(boolean suited, long gameTime) {
        return new ScarletPlayerData(suited, gameTime, hexBuild);
    }

    public ScarletPlayerData withHexBuild(HexBuild hexBuild) {
        return new ScarletPlayerData(suited, suitChangedAt, hexBuild);
    }
}
