package com.yashjit.scarlet.hex;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * What a Hex builds as it spreads, chosen by its caster before casting.
 */
public enum HexBuild implements StringRepresentable {
    /** The caster's home and a whole sitcom suburb around it, wherever the land is open. */
    TOWN("town"),
    /** Just the caster's home, which the Hex bursts out from. */
    HOME("home"),
    NOTHING("nothing");

    public static final Codec<HexBuild> CODEC = StringRepresentable.fromEnum(HexBuild::values);
    private static final HexBuild[] VALUES = values();

    private final String id;

    HexBuild(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public Component displayName() {
        return Component.translatable("hex_build.scarlet." + id);
    }

    public static HexBuild byIndex(int index) {
        return VALUES[Math.clamp(index, 0, VALUES.length - 1)];
    }
}
