package com.yashjit.scarlet.hex.town;

/**
 * What a block of a Hex's town is for. Every era builds each role from a block of its own (see {@link EraStyle}),
 * always of the same shape, so a makeover keeps every block's facing, half and connections.
 */
public enum Role {
    /** Whatever stood where the town goes, and ground cut away to level it. Always air. */
    CLEAR,
    /** Ground raised to level a lot. */
    FILL,
    FOUNDATION,
    FLOOR,
    PORCH,
    STEP,
    CEILING,
    /** Siding, painted per house. */
    WALL,
    /** Corner posts and beams, the frame the house rises on. */
    TRIM,
    ROOF,
    ROOF_SLAB,
    ROOF_RIDGE,
    WINDOW,
    DOOR,
    SHUTTER,
    PORCH_POST,
    CHIMNEY,
    LAWN,
    WALKWAY,
    PICKET,
    GATE,
    HEDGE,
    /** A flower bed's flower, its kind picked per bed. */
    FLOWER,
    MAILBOX_POST,
    MAILBOX,
    ROAD,
    ROAD_LINE,
    SIDEWALK,
    LAMP_POST,
    LAMP,
    PLAZA,
    GAZEBO_POST,
    BENCH,
    TREE_LOG,
    TREE_LEAVES,
    RUG,
    COUCH,
    TABLE,
    SHELF,
    FRIDGE,
    COUNTER,
    SINK,
    PLANT,
    /**
     * Not built at all: leaves of a tree the town cut through, kept from withering while the Hex stands. The block is
     * left as it was, only held fast. Saved roles count by position, so every role added since comes after it.
     */
    PRESERVE,
    /** Brick for the town's public buildings: its library, chapel and school. */
    CIVIC_WALL,
    /** A shop's awning over its window. */
    AWNING,
    SAND,
    /** The painted frame of a playground's climbing frame and swings. */
    PLAY_FRAME,
    /** A playground slide. */
    SLIDE,
    CHAIN,
    BELL,
    /** A sign on a shop or a public building saying what it is, which by its paint: see {@link Signs}. */
    SIGN,
    /** A fruit tree's leaves, and its blossom. */
    ORCHARD_LEAVES,
    ORCHARD_BLOSSOM,
    /** A country lane's packed earth. */
    LANE,
    HAY,
    /** A barn's red boards. */
    BARN_WALL,
    /** A bed upstairs, both halves of it. */
    BED,
    // the era decorations a home is furnished with, which change model with the era rather than block
    TELEVISION,
    RADIO,
    TELEPHONE,
    STOVE,
    TOASTER,
    ARMCHAIR,
    TABLE_LAMP,
    CLOCK,
    PICTURE,
    POSTER;

    private static final Role[] VALUES = values();

    public static Role byIndex(int index) {
        return VALUES[Math.clamp(index, 0, VALUES.length - 1)];
    }
}
