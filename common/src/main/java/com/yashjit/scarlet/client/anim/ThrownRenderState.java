package com.yashjit.scarlet.client.anim;

/**
 * How a living thing thrown out of a Hex is laid, carried in its render state; mixed into every living thing's.
 */
public interface ThrownRenderState {

    /** How far it lies back from upright, in degrees: 0 unless it is thrown. */
    float scarlet$thrownTilt();

    /** How far it has rolled over about its own length, in degrees. */
    float scarlet$thrownRoll();

    /** How wildly its arms and legs are flung about, from 0 to 1. */
    float scarlet$thrownFlail();

    /** How far down on the ground it lies rather than in the air, from 0 to 1. */
    float scarlet$thrownLie();

    void scarlet$setThrown(float tilt, float roll, float flail, float lie);
}
