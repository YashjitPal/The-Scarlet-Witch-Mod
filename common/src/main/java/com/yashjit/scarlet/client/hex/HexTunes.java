package com.yashjit.scarlet.client.hex;

import com.yashjit.scarlet.hex.Era;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * The theme each era's title card plays, scored for note blocks: a dreamy harp in the 1950s, a bouncing cartoon
 * xylophone in the 1960s, soft rock in the 1970s, synths in the 1980s, punk guitars in the 2000s and an easy banjo
 * today.
 *
 * <p>Notes are named as they sound on a harp, the note blocks' middle voice. Each instrument plays them in its own
 * register: bells, chimes and xylophones two octaves higher, flutes one higher, guitars one lower and basses two lower.
 */
public final class HexTunes {

    private static final int G3 = -11;
    private static final int A3 = -9;
    private static final int AS3 = -8;
    private static final int B3 = -7;
    private static final int C4 = -6;
    private static final int D4 = -4;
    private static final int E4 = -2;
    private static final int F4 = -1;
    private static final int G4 = 1;
    private static final int A4 = 3;
    private static final int B4 = 5;
    private static final int C5 = 6;
    private static final int D5 = 8;
    private static final int E5 = 10;
    private static final int F5 = 11;
    /** For drums, which have no notes: their natural pitch. */
    private static final int DRUM = 0;

    private HexTunes() {
    }

    public static void play(Era era) {
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        for (Note note : score(era)) {
            float pitch = (float) Math.pow(2.0, note.semitone / 12.0);
            sounds.playDelayed(SimpleSoundInstance.forUI(note.instrument.value(), pitch, note.volume), note.tick);
        }
    }

    private static List<Note> score(Era era) {
        Score s = new Score();
        switch (era) {
            case FIFTIES -> {
                // a gentle rise and fall on the harp, then a chord rung on bells
                s.voice(SoundEvents.NOTE_BLOCK_HARP, 0.55F, 0, E4, 3, G4, 6, C5, 9, E5, 12, D5, 18, C5, 24, A4, 27, C5, 30, B4, 33, G4, 36, C5);
                s.voice(SoundEvents.NOTE_BLOCK_HARP, 0.35F, 36, E4, 36, G4);
                s.voice(SoundEvents.NOTE_BLOCK_BASS, 0.5F, 0, C4, 12, F4, 24, G4, 36, C4);
                s.voice(SoundEvents.NOTE_BLOCK_BELL, 0.25F, 36, C4, 38, E4, 40, G4);
                s.voice(SoundEvents.NOTE_BLOCK_CHIME, 0.2F, 42, C4);
            }
            case SIXTIES -> {
                // da-da-da-dum, over a walking bass, with a cymbal at the end
                s.voice(SoundEvents.NOTE_BLOCK_XYLOPHONE, 0.5F, 0, C4, 3, C4, 6, E4, 9, G4, 12, A4, 15, G4, 18, E4, 21, C4, 24, D4, 27, G3, 30, C4);
                s.voice(SoundEvents.NOTE_BLOCK_XYLOPHONE, 0.35F, 30, E4, 30, G4);
                s.voice(SoundEvents.NOTE_BLOCK_BASS, 0.55F, 0, C4, 6, E4, 12, F4, 18, G4, 24, G4, 30, C4);
                s.voice(SoundEvents.NOTE_BLOCK_SNARE, 0.25F, 6, DRUM, 18, DRUM, 30, DRUM);
                s.voice(SoundEvents.NOTE_BLOCK_HAT, 0.2F, 0, DRUM, 3, DRUM, 9, DRUM, 12, DRUM, 15, DRUM, 21, DRUM, 24, DRUM, 27, DRUM, 30, 6, 31, 6);
            }
            case SEVENTIES -> {
                // strummed guitar chords under a breezy flute
                s.strum(SoundEvents.NOTE_BLOCK_GUITAR, 0.45F, 0, G4, C5, E5);
                s.strum(SoundEvents.NOTE_BLOCK_GUITAR, 0.45F, 10, A4, C5, F5);
                s.strum(SoundEvents.NOTE_BLOCK_GUITAR, 0.45F, 20, G4, B4, D5);
                s.strum(SoundEvents.NOTE_BLOCK_GUITAR, 0.5F, 30, G4, C5, E5);
                s.voice(SoundEvents.NOTE_BLOCK_FLUTE, 0.45F, 0, E4, 5, G4, 10, A4, 15, C5, 20, B4, 25, G4, 30, C5);
                s.voice(SoundEvents.NOTE_BLOCK_BASS, 0.5F, 0, C4, 10, F4, 20, G4, 30, C4);
            }
            case EIGHTIES -> {
                // a bright synth arpeggio over four chords, a lead on top and a drum machine
                int[][] chords = {{C4, E4, G4, C5}, {A3, C4, E4, A4}, {F4, A4, C5, F5}, {G4, B4, D5, B4}};
                for (int bar = 0; bar < chords.length; bar++) {
                    for (int step = 0; step < 4; step++) {
                        s.voice(SoundEvents.NOTE_BLOCK_BIT, 0.3F, bar * 8 + step * 2, chords[bar][step]);
                    }
                }
                s.voice(SoundEvents.NOTE_BLOCK_PLING, 0.45F, 0, E5, 8, C5, 16, A4, 24, B4, 32, C5);
                s.voice(SoundEvents.NOTE_BLOCK_PLING, 0.3F, 32, E4, 32, G4);
                s.voice(SoundEvents.NOTE_BLOCK_BASS, 0.55F, 0, C4, 8, A3, 16, F4, 24, G4, 32, C4);
                s.voice(SoundEvents.NOTE_BLOCK_BASEDRUM, 0.5F, 0, DRUM, 8, DRUM, 16, DRUM, 24, DRUM, 32, DRUM);
                s.voice(SoundEvents.NOTE_BLOCK_SNARE, 0.35F, 4, DRUM, 12, DRUM, 20, DRUM, 28, DRUM);
            }
            case TWO_THOUSANDS -> {
                // chugging power chords, fast drums and a crash to finish
                int[] roots = {C4, AS3, F4, G4};
                for (int bar = 0; bar < roots.length; bar++) {
                    for (int step = 0; step < 4; step++) {
                        int tick = bar * 8 + step * 2;
                        s.voice(SoundEvents.NOTE_BLOCK_GUITAR, 0.4F, tick, roots[bar], tick, roots[bar] + 7);
                    }
                    s.voice(SoundEvents.NOTE_BLOCK_BASEDRUM, 0.55F, bar * 8, DRUM, bar * 8 + 3, DRUM);
                    s.voice(SoundEvents.NOTE_BLOCK_SNARE, 0.4F, bar * 8 + 4, DRUM);
                    s.voice(SoundEvents.NOTE_BLOCK_HAT, 0.2F, bar * 8, DRUM, bar * 8 + 2, DRUM, bar * 8 + 4, DRUM, bar * 8 + 6, DRUM);
                }
                s.voice(SoundEvents.NOTE_BLOCK_GUITAR, 0.5F, 32, C4, 32, G4, 32, C5);
                s.voice(SoundEvents.NOTE_BLOCK_BASEDRUM, 0.6F, 32, DRUM);
                s.voice(SoundEvents.NOTE_BLOCK_SNARE, 0.45F, 32, 6, 33, 9);
                s.voice(SoundEvents.NOTE_BLOCK_HAT, 0.4F, 32, 10, 34, 8);
            }
            case PRESENT -> {
                // plucked banjo, a soft whistle and handclaps
                int[][] picks = {{C4, E4, G4, E4}, {F4, A4, C5, A4}, {G4, B4, D5, B4}};
                for (int bar = 0; bar < picks.length; bar++) {
                    for (int step = 0; step < 4; step++) {
                        s.voice(SoundEvents.NOTE_BLOCK_BANJO, 0.4F, bar * 12 + step * 3, picks[bar][step]);
                    }
                }
                s.voice(SoundEvents.NOTE_BLOCK_BANJO, 0.45F, 36, C4, 36, E4, 36, G4);
                s.voice(SoundEvents.NOTE_BLOCK_FLUTE, 0.3F, 0, G3, 12, A3, 24, B3, 36, C4);
                s.voice(SoundEvents.NOTE_BLOCK_SNARE, 0.2F, 6, 10, 18, 10, 30, 10);
                s.voice(SoundEvents.NOTE_BLOCK_HAT, 0.2F, 6, 12, 18, 12, 30, 12);
            }
        }
        return s.notes;
    }

    private record Note(int tick, Holder<SoundEvent> instrument, int semitone, float volume) {
    }

    private static final class Score {
        final List<Note> notes = new ArrayList<>();

        /**
         * Notes for one instrument, as pairs of tick and semitone.
         */
        void voice(Holder<SoundEvent> instrument, float volume, int... ticksAndSemitones) {
            for (int i = 0; i + 1 < ticksAndSemitones.length; i += 2) {
                notes.add(new Note(ticksAndSemitones[i], instrument, Math.clamp(ticksAndSemitones[i + 1], -12, 12), volume));
            }
        }

        /**
         * A chord strummed upward from its lowest string, a tick between strings.
         */
        void strum(Holder<SoundEvent> instrument, float volume, int tick, int... semitones) {
            for (int i = 0; i < semitones.length; i++) {
                notes.add(new Note(tick + i, instrument, Math.clamp(semitones[i], -12, 12), volume * (1.0F - i * 0.12F)));
            }
        }
    }
}
