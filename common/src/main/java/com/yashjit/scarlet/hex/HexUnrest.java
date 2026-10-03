package com.yashjit.scarlet.hex;

/**
 * How unsteady a Hex is.
 *
 * <ul>
 *     <li>Parted: its caster has pulled its wall open, as wide as they choose. The wider it stands, the worse.</li>
 *     <li>Hurt: a blow to its caster shakes it, and it settles again over a few seconds. This is worked out the same
 *     on both sides from when the blow landed, so nothing needs sending as it settles.</li>
 * </ul>
 *
 * <p>The more unsteady it is, the more what it made slips: houses, people and clothes jump into other eras for a
 * moment, or flicker out of existence.
 */
public final class HexUnrest {

    /** How far apart the edges of an opening this wide count as fully parted, in blocks from the middle. */
    private static final float WIDE_OPEN = 10.0F;
    /** Ticks for the shake of a blow to settle by most of the way. */
    private static final float STRESS_FADE = 50.0F;

    private HexUnrest() {
    }

    /**
     * How shaken a Hex still is by blows to its caster, settling from {@code stress} at {@code since}.
     */
    public static float stress(float stress, long since, double now) {
        return stress <= 0.0F ? 0.0F : stress * (float) Math.exp(-Math.max(0.0, now - since) / STRESS_FADE);
    }

    /**
     * Its stress after a blow of {@code damage}, given what was left of the last.
     */
    public static float struck(float stressNow, float damage) {
        return Math.min(1.0F, stressNow + 0.15F + damage / 8.0F);
    }

    /**
     * @param opening how far each edge of an opening in its wall stands from the middle, in blocks, or less than 0 for
     *                none
     */
    public static float unrest(float opening, float stress) {
        float parted = opening < 0.0F ? 0.0F : 0.15F + 0.75F * Math.min(1.0F, opening / WIDE_OPEN);
        return Math.clamp(Math.max(parted, stress), 0.0F, 1.0F);
    }
}
