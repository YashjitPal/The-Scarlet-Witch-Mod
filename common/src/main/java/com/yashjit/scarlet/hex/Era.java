package com.yashjit.scarlet.hex;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * The sitcom eras a Hex can be set in, oldest first.
 */
public enum Era implements StringRepresentable {
    FIFTIES("1950s"),
    SIXTIES("1960s"),
    SEVENTIES("1970s"),
    EIGHTIES("1980s"),
    TWO_THOUSANDS("2000s"),
    PRESENT("present");

    public static final Codec<Era> CODEC = StringRepresentable.fromEnum(Era::values);
    private static final Era[] VALUES = values();

    private final String id;

    Era(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public Component displayName() {
        return Component.translatable("era.scarlet." + id);
    }

    public static Era byIndex(int index) {
        return VALUES[Math.clamp(index, 0, VALUES.length - 1)];
    }
}
