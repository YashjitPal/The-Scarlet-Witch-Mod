package com.yashjit.scarlet.magic;

import java.util.Arrays;
import java.util.List;
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
    HEX("hex", 10, Input.CHANNEL, 0.1F, 10),
    /** The Darkhold's. Cast to choose where the spirit goes and again to wake; the cost is per tick of being away. */
    DREAMWALK("dreamwalk", 6, Input.TOGGLE, 0.08F, 200),
    STARFIRE("starfire", 1, Input.TAP, 7, 7),
    ARC_BEAM("arc_beam", 2, Input.CHANNEL, 0.65F, 16),
    WEAVE("weave", 1, Input.TAP, 15, 24),
    SECOND_SIGHT("second_sight", 2, Input.TAP, 12, 60),
    ASTRAL("astral", 5, Input.TOGGLE, 0.25F, 160),
    WAYGATE("waygate", 4, Input.TAP, 22, 45),
    DEMIURGE("demiurge", 10, Input.TAP, 45, 2400),
    FATE("fate", 10, Input.TAP, 18, 80),
    RUNIC_DOMAIN("runic_domain", 10, Input.TAP, 32, 120),
    WITCHES_ROAD("witches_road", 10, Input.TAP, 25, 40),
    DREAMBIND("dreambind", 5, Input.TAP, 20, 60),
    TRANSMUTE("transmute", 3, Input.TAP, 12, 28),
    GLAMOUR("glamour", 4, Input.TAP, 22, 140),
    AEGIS("aegis", 1, Input.TAP, 14, 70),
    VIOLET_BLAST("violet_blast", 1, Input.TAP, 6, 7),
    SIPHON("siphon", 1, Input.CHANNEL, 0.25F, 36),
    BINDING("binding", 2, Input.TAP, 16, 80),
    WITCH_RUNE("witch_rune", 3, Input.TAP, 22, 110),
    MALEDICTION("malediction", 4, Input.TAP, 19, 100),
    DOUBLE("double", 3, Input.TAP, 24, 180),
    NIGHTMARE("nightmare", 5, Input.TAP, 23, 140),
    FAMILIAR("familiar", 4, Input.TAP, 28, 240),
    WITCH_SIGHT("witch_sight", 1, Input.TAP, 12, 80);

    private static final Spell[] VALUES = values();

    /**
     * The spells on the wheel, clockwise from the top. Levitation is not among them: it is a gift of the crown itself,
     * there to be used at any time alongside whatever spell is chosen.
     */
    public static final List<Spell> WHEEL = Arrays.stream(VALUES).filter(spell -> spell != LEVITATION && !spell.billyOnly() && !spell.agathaOnly()).toList();

    public boolean agathaOnly() { return ordinal() >= VIOLET_BLAST.ordinal(); }

    public boolean billyOnly() {
        return ordinal() >= STARFIRE.ordinal() && !agathaOnly();
    }

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
        return true;
    }

    /**
     * Whether it is one of the Darkhold's spells, which only show on the wheel, and can only be cast, while the book is
     * carried.
     */
    public boolean darkhold() {
        return this == DREAMWALK;
    }

    /**
     * Whether casting it throws a hand forward. The others move the whole body, each in its own way.
     */
    public boolean strikes() {
        return this == CHAOS_BOLT || this == RUNE_TRAP || this == STARFIRE || this == VIOLET_BLAST;
    }

    /**
     * Ticks from the cast to its effect: a strike lands at once, the bigger spells gather themselves first.
     */
    public int windUp() {
        return switch (this) {
            case SHOCKWAVE -> 7;
            case RED_MIST -> 5;
            default -> Magic.RELEASE_DELAY;
        };
    }

    public Component displayName() {
        return Component.translatable("spell.scarlet." + id);
    }

    public static Spell byIndex(int index) {
        return VALUES[Math.floorMod(index, VALUES.length)];
    }

    /**
     * The spell chosen with this index, if it can be chosen at all; otherwise the first spell on the wheel.
     */
    public static Spell choosable(int index) {
        Spell spell = byIndex(index);
        return spell != LEVITATION ? spell : WHEEL.getFirst();
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
