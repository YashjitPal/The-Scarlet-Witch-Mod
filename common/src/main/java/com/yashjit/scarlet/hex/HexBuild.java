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
    NOTHING("nothing"),
    /**
     * The caster's farmhouse among orchards in blossom, with country lanes and a red barn, every tree it covers turned
     * into a fruit tree: the peaceful life Wanda hides away in.
     */
    ORCHARD("orchard");

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

    /**
     * Whether it builds out over the whole Hex as it spreads, rather than just the caster's home or nothing.
     */
    public boolean isTown() {
        return this == TOWN || this == ORCHARD;
    }

    public Component displayName() {
        return Component.translatable("hex_build.scarlet." + id);
    }

    public static HexBuild byIndex(int index) {
        return VALUES[Math.clamp(index, 0, VALUES.length - 1)];
    }
}
