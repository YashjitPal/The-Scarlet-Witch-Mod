package com.yashjit.scarlet.client.anim;

/**
 * Easing curves. Every animated value in the mod runs through one of these; nothing moves linearly.
 */
public final class Ease {

    private Ease() {
    }

    public static float clamp01(float t) {
        return Math.clamp(t, 0.0F, 1.0F);
    }

    public static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    public static float inOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5F ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    public static float outCubic(float t) {
        t = clamp01(t);
        return 1 - (float) Math.pow(1 - t, 3);
    }

    public static float inCubic(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    public static float outQuad(float t) {
        t = clamp01(t);
        return 1 - (1 - t) * (1 - t);
    }

    /**
     * Overshoots its target slightly and settles back: the snap of a strike.
     */
    public static float outBack(float t) {
        t = clamp01(t) - 1;
        float c1 = 1.70158F;
        return 1 + (c1 + 1) * t * t * t + c1 * t * t;
    }

    /**
     * 0 outside the window, rising and falling smoothly inside it. Used for brief flares.
     */
    public static float pulse(float t, float start, float end) {
        if (t <= start || t >= end) {
            return 0.0F;
        }
        float local = (t - start) / (end - start);
        return (float) Math.sin(local * Math.PI);
    }

    /**
     * Frame-rate independent approach of {@code target}: {@code rate} is how many times per second the remaining
     * distance shrinks by a factor of e.
     */
    public static float damp(float current, float target, float rate, float seconds) {
        return target + (current - target) * (float) Math.exp(-rate * seconds);
    }
}
