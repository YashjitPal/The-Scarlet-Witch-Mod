package com.yashjit.scarlet.client.hex;

import com.yashjit.scarlet.client.fx.ScarletFx;
import com.yashjit.scarlet.decor.EraDecor;
import com.yashjit.scarlet.decor.SwitchedDecorBlock;
import com.yashjit.scarlet.hex.Era;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What a television that is on sounds like: its show playing. Two voices talking back and forth, small and boxy, a few
 * words at a time; a laugh track rolling now and then in the shows filmed before an audience, which the 2000s and today
 * are not; and a few notes of music between scenes, in the era's sound. Only the nearest set that is on is heard, near
 * it, and none out past where a Hex's caster can see, where the show has stopped.
 */
public final class TelevisionAudio {

    /** How far off a set is still heard. */
    private static final double HEARD_FROM = 14.0;
    /** A set not reported for this long is taken to be out of hearing. */
    private static final int FORGET = 200;
    /** What the voices say, in the game's own murmurs. */
    private static final SoundEvent[] WORDS = {SoundEvents.VILLAGER_AMBIENT, SoundEvents.VILLAGER_YES, SoundEvents.VILLAGER_NO,
            SoundEvents.VILLAGER_TRADE, SoundEvents.WANDERING_TRADER_AMBIENT};
    private static final SoundEvent[] LAUGHS = {SoundEvents.VINDICATOR_CELEBRATE, SoundEvents.PILLAGER_CELEBRATE, SoundEvents.EVOKER_CELEBRATE,
            SoundEvents.WITCH_CELEBRATE, SoundEvents.VILLAGER_CELEBRATE};

    /** The sets that are on, by position, with when each last reported itself. */
    private static final Long2LongOpenHashMap ON = new Long2LongOpenHashMap();
    private static @Nullable Level watchedIn;
    private static long nextLine;
    private static long nextLaugh;
    private static long nextCue;
    private static boolean highVoice;

    private TelevisionAudio() {
    }

    /** A set near the player is on. */
    public static void watched(Level level, BlockPos pos, BlockState state) {
        if (level != watchedIn) {
            ON.clear();
            watchedIn = level;
        }
        ON.put(pos.asLong(), level.getGameTime());
    }

    public static void tick(Minecraft minecraft) {
        Level level = minecraft.level;
        if (level == null || minecraft.player == null || level != watchedIn) {
            ON.clear();
            return;
        }
        long now = level.getGameTime();
        ON.long2LongEntrySet().removeIf(entry -> now - entry.getLongValue() > FORGET || !isOn(level.getBlockState(BlockPos.of(entry.getLongKey()))));
        if (ON.isEmpty() || ResidentsClient.unwatched() > 0.0F) {
            return;
        }
        Vec3 ear = minecraft.player.position();
        BlockPos nearest = null;
        double best = HEARD_FROM * HEARD_FROM;
        for (long key : ON.keySet()) {
            BlockPos pos = BlockPos.of(key);
            double d = Vec3.atCenterOf(pos).distanceToSqr(ear);
            if (d < best) {
                best = d;
                nearest = pos;
            }
        }
        if (nearest == null) {
            return;
        }
        Era era = level.getBlockState(nearest).getValue(EraDecor.ERA);
        Vec3 at = Vec3.atCenterOf(nearest);
        SoundManager sounds = minecraft.getSoundManager();
        RandomSource random = ScarletFx.random();
        if (now >= nextLine) {
            line(sounds, at, random);
            nextLine = now + 20 + random.nextInt(40);
        }
        if (now >= nextLaugh) {
            if (era.ordinal() <= Era.EIGHTIES.ordinal()) {
                laugh(sounds, at, random);
            }
            nextLaugh = now + 240 + random.nextInt(420);
        }
        if (now >= nextCue) {
            cue(sounds, at, era);
            nextCue = now + 500 + random.nextInt(700);
        }
    }

    /** A line: a few words in one voice, and the other voice's turn next. */
    private static void line(SoundManager sounds, Vec3 at, RandomSource random) {
        highVoice = !highVoice;
        float pitch = highVoice ? 1.35F + random.nextFloat() * 0.15F : 0.9F + random.nextFloat() * 0.12F;
        int words = 2 + random.nextInt(3);
        int delay = 0;
        for (int k = 0; k < words; k++) {
            SoundEvent word = WORDS[random.nextInt(WORDS.length)];
            sounds.playDelayed(new SimpleSoundInstance(word, SoundSource.BLOCKS, 0.14F, pitch + random.nextFloat() * 0.08F, random, at.x, at.y, at.z), delay);
            delay += 4 + random.nextInt(3);
        }
    }

    /** The studio audience, small through the set's speaker. */
    private static void laugh(SoundManager sounds, Vec3 at, RandomSource random) {
        int voices = 8 + random.nextInt(4);
        for (int k = 0; k < voices; k++) {
            boolean late = k > voices * 0.6F;
            int delay = 2 + random.nextInt(12) + (late ? 8 : 0);
            float pitch = 1.15F + random.nextFloat() * 0.4F;
            float volume = (0.07F + random.nextFloat() * 0.06F) * (late ? 0.6F : 1.0F);
            sounds.playDelayed(new SimpleSoundInstance(LAUGHS[random.nextInt(LAUGHS.length)], SoundSource.BLOCKS, volume, pitch, random, at.x, at.y, at.z),
                    delay);
        }
    }

    /** A few notes between scenes, in the instruments of the era: harp, then guitar, bass, synth, chimes. */
    private static void cue(SoundManager sounds, Vec3 at, Era era) {
        SoundEvent instrument = switch (era) {
            case FIFTIES -> SoundEvents.NOTE_BLOCK_HARP.value();
            case SIXTIES -> SoundEvents.NOTE_BLOCK_GUITAR.value();
            case SEVENTIES -> SoundEvents.NOTE_BLOCK_BASS.value();
            case EIGHTIES -> SoundEvents.NOTE_BLOCK_BIT.value();
            case TWO_THOUSANDS -> SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value();
            case PRESENT -> SoundEvents.NOTE_BLOCK_CHIME.value();
        };
        float[] notes = {1.0F, 1.26F, 1.5F};
        RandomSource random = ScarletFx.random();
        for (int k = 0; k < notes.length; k++) {
            sounds.playDelayed(new SimpleSoundInstance(instrument, SoundSource.BLOCKS, 0.18F, notes[k], random, at.x, at.y, at.z), k * 4);
        }
    }

    private static boolean isOn(BlockState state) {
        return state.getBlock() instanceof SwitchedDecorBlock set && set.kind() == SwitchedDecorBlock.Kind.TELEVISION && state.getValue(SwitchedDecorBlock.LIT);
    }
}
