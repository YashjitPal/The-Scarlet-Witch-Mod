package com.yashjit.scarlet.crown;

import com.yashjit.scarlet.ScarletPalette;
import net.minecraft.util.StringRepresentable;

/**
 * Which identity a crown grants. Both have the same powers; only the crown and costume differ.
 */
public enum CrownStyle implements StringRepresentable {
    WITCH("witch", ScarletPalette.BRIGHT_SCARLET),
    WARLOCK("warlock", ScarletPalette.SCARLET);

    private final String name;
    private final int nameColor;

    CrownStyle(String name, int nameColor) {
        this.name = name;
        this.nameColor = nameColor;
    }

    public CrownStyle other() {
        return this == WITCH ? WARLOCK : WITCH;
    }

    public int nameColor() {
        return nameColor;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
