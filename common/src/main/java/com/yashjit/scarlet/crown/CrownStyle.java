package com.yashjit.scarlet.crown;

import com.yashjit.scarlet.ScarletPalette;
import net.minecraft.util.StringRepresentable;

/**
 * The identity manifested by a crown. Only the two Scarlet crowns share chaos magic and mastery.
 */
public enum CrownStyle implements StringRepresentable {
    WITCH("witch", ScarletPalette.BRIGHT_SCARLET),
    WARLOCK("warlock", ScarletPalette.SCARLET),
    WICCAN("wiccan", 0x82CFFF);

    private final String name;
    private final int nameColor;

    CrownStyle(String name, int nameColor) {
        this.name = name;
        this.nameColor = nameColor;
    }

    public boolean grantsScarletMagic() {
        return this == WITCH || this == WARLOCK;
    }

    public int nameColor() {
        return nameColor;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
