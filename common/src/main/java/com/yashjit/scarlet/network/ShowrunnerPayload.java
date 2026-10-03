package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.hex.Hex;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A press on the Showrunner's remote: the caster changing something about their Hex, or what their next one builds.
 *
 * @param action one of the constants below
 * @param value  what it is changed to: an ordinal, or 0 or 1 for episodes
 * @param text   the new name, for {@link #NAME}
 */
public record ShowrunnerPayload(int action, int value, String text) implements CustomPacketPayload {

    public static final int ERA = 0;
    public static final int EPISODES = 1;
    public static final int TIME = 2;
    public static final int WEATHER = 3;
    public static final int NAME = 4;
    /** What the caster's next Hex builds as it spreads. */
    public static final int BUILD = 5;

    public static final Type<ShowrunnerPayload> TYPE = new Type<>(Scarlet.id("showrunner"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShowrunnerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShowrunnerPayload::action,
            ByteBufCodecs.VAR_INT, ShowrunnerPayload::value,
            ByteBufCodecs.stringUtf8(Hex.MAX_NAME_LENGTH * 4), ShowrunnerPayload::text,
            ShowrunnerPayload::new);

    public static ShowrunnerPayload of(int action, int value) {
        return new ShowrunnerPayload(action, value, "");
    }

    @Override
    public Type<ShowrunnerPayload> type() {
        return TYPE;
    }
}
