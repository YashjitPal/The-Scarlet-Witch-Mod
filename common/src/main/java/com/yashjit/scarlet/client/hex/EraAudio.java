package com.yashjit.scarlet.client.hex;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.config.ScarletClientConfig;
import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.hex.HexSnapshot;
import com.yashjit.scarlet.hex.Hexes;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

/**
 * Inside a Hex in an older era everything sounds the way it would through an old television's speaker: the highs fall
 * away, thinner in the 1950s, warmer by the 1970s, nearly clear by the 1980s. The 2000s and today sound as they are.
 * Crossing the wall the sound eases into its new era over a moment.
 *
 * <p>Done with an OpenAL low-pass filter on every sound as it starts, and again on streamed sounds as they play, so
 * music changes with the rest. Where the sound device has no effects extension nothing is filtered.
 */
public final class EraAudio {

    /** How much of the highs each era keeps, from the 1950s. */
    private static final float[] HIGHS = {0.24F, 0.32F, 0.5F, 0.66F, 1.0F, 1.0F};
    /** And how loud it is overall: a little quieter for the oldest sets. */
    private static final float[] LOUDNESS = {0.88F, 0.9F, 0.95F, 0.97F, 1.0F, 1.0F};
    /** How far toward its new era the sound moves each tick. */
    private static final float EASE = 0.12F;

    private static volatile float highs = 1.0F;
    private static volatile float loudness = 1.0F;
    /** Whether the sound device can filter, worked out on the sound thread the first time it is asked. */
    private static volatile int support = -1;
    private static int filter;

    private EraAudio() {
    }

    /** Eases the sound toward the era the listener is in. */
    public static void tick(Minecraft minecraft) {
        float targetHighs = 1.0F;
        float targetLoudness = 1.0F;
        if (minecraft.level != null && ScarletClientConfig.get().eraAudio) {
            Entity listener = minecraft.getCameraEntity() != null ? minecraft.getCameraEntity() : minecraft.player;
            Vec3 ear = listener != null ? listener.getEyePosition() : null;
            if (ear != null) {
                double now = minecraft.level.getGameTime();
                HexSnapshot hex = Hexes.clientHexAt(ear, now);
                if (hex != null) {
                    Era era = Hexes.eraAt(hex, ear, now);
                    targetHighs = HIGHS[era.ordinal()];
                    targetLoudness = LOUDNESS[era.ordinal()];
                }
            }
        }
        highs += (targetHighs - highs) * EASE;
        loudness += (targetLoudness - loudness) * EASE;
        if (Math.abs(targetHighs - highs) < 0.002F) {
            highs = targetHighs;
        }
        if (Math.abs(targetLoudness - loudness) < 0.002F) {
            loudness = targetLoudness;
        }
    }

    /**
     * Puts the era's filter on a sound, or takes it off. Called on the sound thread, as a sound starts and as a
     * streamed one plays.
     */
    public static void apply(int source) {
        if (support == -1) {
            support = supported() ? 1 : 0;
            if (support == 1) {
                filter = EXTEfx.alGenFilters();
                EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
                Scarlet.LOG.info("Era sound ready: older eras will sound as if through an old television");
            } else {
                Scarlet.LOG.info("The sound device has no effects extension; eras will sound as they are");
            }
        }
        if (support != 1) {
            return;
        }
        float h = highs;
        float l = loudness;
        if (h >= 0.999F && l >= 0.999F) {
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
            return;
        }
        EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, l);
        EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, h);
        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filter);
        if (!checked) {
            checked = true;
            int error = AL10.alGetError();
            if (error != AL10.AL_NO_ERROR) {
                // the device claims effects but won't take the filter: leave every sound as it is from now on
                support = 0;
                AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
                Scarlet.LOG.warn("The sound device refused the era filter (OpenAL error {}); eras will sound as they are", error);
            } else {
                Scarlet.LOG.info("Era sound filtering a sound: highs {}, loudness {}", h, l);
            }
        }
    }

    /** Whether the first filtered sound has been checked for errors. */
    private static boolean checked;

    private static boolean supported() {
        long context = ALC10.alcGetCurrentContext();
        return context != 0 && ALC10.alcIsExtensionPresent(ALC10.alcGetContextsDevice(context), "ALC_EXT_EFX");
    }
}
