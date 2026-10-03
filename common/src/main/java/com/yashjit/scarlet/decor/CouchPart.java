package com.yashjit.scarlet.decor;

import net.minecraft.util.StringRepresentable;

/**
 * Which length of a couch a block is, as whoever sits on it sees it: on its own with an arm either side, the left end,
 * somewhere in the middle, or the right end.
 */
public enum CouchPart implements StringRepresentable {
    SINGLE("single"),
    LEFT("left"),
    MIDDLE("middle"),
    RIGHT("right");

    private final String id;

    CouchPart(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
