package com.yashjit.scarlet.darkhold;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yashjit.scarlet.player.ScarletPlayerData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * How far the Darkhold has taken hold of someone, saved with them and synced to everyone who can see them, since it
 * darkens their magic for all to see. Like the energy bar it is an event stamp: the level it was raised to and when,
 * so it fades on every client without further updates.
 *
 * @param level    0 to 1, as it was at {@code raisedAt}
 * @param raisedAt game time the Darkhold was last used
 * @param opened   whether they have ever read it
 */
public record Corruption(float level, long raisedAt, boolean opened) {

    public static final Corruption NONE = new Corruption(0.0F, ScarletPlayerData.NEVER, false);

    /** How long after the Darkhold was last used before the corruption begins to fade: a minute. */
    public static final long GRACE = 1200;
    /** How much fades each tick after that: all of it in about forty minutes. */
    public static final float FADE = 1.0F / (40 * 60 * 20);

    public static final MapCodec<Corruption> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("level", 0.0F).forGetter(Corruption::level),
            Codec.LONG.optionalFieldOf("raised_at", ScarletPlayerData.NEVER).forGetter(Corruption::raisedAt),
            Codec.BOOL.optionalFieldOf("opened", false).forGetter(Corruption::opened)
    ).apply(instance, Corruption::new));

    public static final Codec<Corruption> CODEC = MAP_CODEC.codec();

    public static final StreamCodec<ByteBuf, Corruption> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, Corruption::level,
            ByteBufCodecs.LONG, Corruption::raisedAt,
            ByteBufCodecs.BOOL, Corruption::opened,
            Corruption::new);

    /**
     * The corruption at {@code now}: as it was raised, until a minute after the Darkhold was last used, then fading.
     */
    public float at(double now) {
        double fading = now - raisedAt - GRACE;
        return fading <= 0.0 ? level : (float) Math.max(0.0, level - fading * FADE);
    }

    public Corruption raised(float amount, long now) {
        return new Corruption(Math.clamp(at(now) + amount, 0.0F, 1.0F), now, opened);
    }

    /**
     * Set to a level outright, fading from now on as if the Darkhold had just been used.
     */
    public Corruption withLevel(float level, long now) {
        return new Corruption(Math.clamp(level, 0.0F, 1.0F), now, opened);
    }

    public Corruption withOpened() {
        return new Corruption(level, raisedAt, true);
    }
}
