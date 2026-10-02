package com.yashjit.scarlet.hex.town;

/**
 * What a block of a Hex's town is for. Every era builds each role from a block of its own (see {@link EraStyle}),
 * always of the same shape, so a makeover keeps every block's facing, half and connections.
 */
public enum Role {
    /** Ground cut away to level a lot. Always air. */
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
    PLANT;

    private static final Role[] VALUES = values();

    public static Role byIndex(int index) {
        return VALUES[Math.clamp(index, 0, VALUES.length - 1)];
    }
}
