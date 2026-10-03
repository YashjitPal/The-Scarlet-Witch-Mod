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

    /** Arms flung wide as a Hex or a Shockwave bursts out: 0 to 1 and back. */
    float scarlet$burst();

    /** Hands drawn in together before the chest, gathering a Shockwave: 0 to 1. */
    float scarlet$gather();

    /** Both arms reaching out to hold something with Telekinesis: 0 to 1. */
    float scarlet$hold();

    /** Gripping the wall of their Hex to tear it open: 0 to 1. */
    float scarlet$tear();

    /** How far apart the hands have pulled the tear: 0 to 1. */
    float scarlet$spread();

    void scarlet$set(float rightStrike, float leftStrike, float shield, float recoil, float levitate, float lean, float burst, float gather,
                     float hold, float tear, float spread);

    /** Floating over their home as it rises around them, founding a Hex: 0 to 1 and back. */
    float scarlet$found();

    void scarlet$setFound(float found);

    /** Both arms working the strings of a mind held with Mind Control: 0 to 1. */
    float scarlet$control();

    void scarlet$setControl(float control);

    /** One arm flung out at what a beam of magic from it lands on, painting: 0 to 1. */
    float scarlet$beam();

    /** Whether the beam comes from the right hand. */
    boolean scarlet$beamRight();

    /** Both arms raised to a home going up far off, the magic pouring out of both: 0 to 1. */
    float scarlet$raise();

    void scarlet$setBeam(float beam, boolean right, float raise);

    /** Reading the Darkhold, held open before the chest in both hands: 0 to 1. */
    float scarlet$read();

    void scarlet$setRead(float read);
}
