package com.yashjit.scarlet.magic;

import net.minecraft.network.chat.Component;

/**
 * The crown's spells, in spell wheel order (clockwise from the top).
 *
 * @param rank     mastery rank that unlocks the spell
 * @param cost     energy per cast, or per tick while channeling
 * @param cooldown ticks before the spell can be cast again
 */
public enum Spell {
    CHAOS_BOLT("chaos_bolt", 1, Input.TAP, 7.0F, 4),
    CHAOS_SHIELD("chaos_shield", 1, Input.CHANNEL, 0.3F, 16),
    LEVITATION("levitation", 2, Input.TOGGLE, 0.08F, 10),
    TELEKINESIS("telekinesis", 3, Input.CHANNEL, 0.25F, 20),
    RED_MIST("red_mist", 4, Input.TAP, 25.0F, 60),
    SHOCKWAVE("shockwave", 5, Input.TAP, 35.0F, 100),
    MIND_CONTROL("mind_control", 6, Input.CHANNEL, 0.4F, 200),
    RUNE_TRAP("rune_trap", 7, Input.TAP, 30.0F, 80),
    /** Cast once to raise it; after that, holding the cast resizes it, and the cost is per tick of resizing. */
    HEX("hex", 10, Input.CHANNEL, 0.25F, 10);

    private static final Spell[] VALUES = values();

    private final String id;
    private final int rank;
    private final Input input;
    private final float cost;
    private final int cooldown;

    Spell(String id, int rank, Input input, float cost, int cooldown) {
        this.id = id;
        this.rank = rank;
        this.input = input;
        this.cost = cost;
        this.cooldown = cooldown;
    }

    public String id() {
        return id;
    }

    public int rank() {
        return rank;
    }

    public Input input() {
        return input;
    }

    public float cost() {
        return cost;
    }

    public int cooldown() {
        return cooldown;
    }

    /**
     * Whether casting it does anything yet. Spells still being built show in the wheel but cannot be chosen.
     */
    public boolean available() {
        return this == CHAOS_BOLT || this == CHAOS_SHIELD || this == LEVITATION || this == HEX;
    }

    public Component displayName() {
        return Component.translatable("spell.scarlet." + id);
    }

    public static Spell byIndex(int index) {
        return VALUES[Math.floorMod(index, VALUES.length)];
    }

    public static int count() {
        return VALUES.length;
    }

    public enum Input {
        /** Fires once per click; holding the button keeps firing. */
        TAP,
        /** Lasts while the button is held. */
        CHANNEL,
        /** Click to turn on, click again to turn off. */
        TOGGLE
    }
}
