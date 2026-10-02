package com.yashjit.scarlet.client.anim;

/**
 * Casting pose values carried on a player's render state from extraction to model posing.
 */
public interface CastPoseState {

    float scarlet$rightStrike();

    float scarlet$leftStrike();

    float scarlet$shield();

    /** Kick of the last blow the shield took: 1 at impact, fading to 0. */
    float scarlet$recoil();

    float scarlet$levitate();

    /** Degrees of forward lean while flying. */
    float scarlet$lean();

    /** Arms flung wide as a Hex bursts out: 0 to 1 and back. */
    float scarlet$burst();

    void scarlet$set(float rightStrike, float leftStrike, float shield, float recoil, float levitate, float lean, float burst);
}
