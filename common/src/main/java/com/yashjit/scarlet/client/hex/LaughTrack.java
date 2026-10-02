package com.yashjit.scarlet.client.hex;

import com.yashjit.scarlet.client.fx.ScarletFx;
import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.hex.HexSnapshot;
import com.yashjit.scarlet.hex.Hexes;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The studio audience. When someone gets hurt inside the Hex you're in, a laugh track rolls, now and then. Only the
 * shows filmed before a live audience have one: the 2000s and the present day are shot single-camera, in silence.
 */
public final class LaughTrack {

    private static final int COOLDOWN_TICKS = 400;
    private static final float CHANCE = 0.75F;
    private static final SoundEvent[] LAUGHS = {SoundEvents.VINDICATOR_CELEBRATE, SoundEvents.PILLAGER_CELEBRATE, SoundEvents.EVOKER_CELEBRATE,
            SoundEvents.WITCH_CELEBRATE, SoundEvents.WITCH_AMBIENT, SoundEvents.VILLAGER_CELEBRATE};

    /** Each player's hurt time last tick, to catch the moment it jumps. */
    private static final Int2IntOpenHashMap HURT = new Int2IntOpenHashMap();
    private static @Nullable ClientLevel seenLevel;
    private static long lastLaugh = Long.MIN_VALUE / 2;

    private LaughTrack() {
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        LocalPlayer self = minecraft.player;
        if (level != seenLevel) {
            seenLevel = level;
            HURT.clear();
            lastLaugh = Long.MIN_VALUE / 2;
        }
        if (level == null || self == null) {
            return;
        }
        long now = level.getGameTime();
        HexSnapshot around = Hexes.clientHexAt(self.position(), now);
        boolean audience = around != null && around.eraValue().ordinal() <= Era.EIGHTIES.ordinal();
        boolean funny = false;
        Int2IntOpenHashMap seen = new Int2IntOpenHashMap();
        for (Player player : level.players()) {
            int before = HURT.getOrDefault(player.getId(), 0);
            seen.put(player.getId(), player.hurtTime);
            if (audience && player.hurtTime > before && !player.isDeadOrDying()) {
                HexSnapshot theirs = Hexes.clientHexAt(player.position(), now);
                funny |= theirs != null && theirs.caster().equals(around.caster());
            }
        }
        HURT.clear();
        HURT.putAll(seen);
        RandomSource random = ScarletFx.random();
        if (funny && now - lastLaugh >= COOLDOWN_TICKS && random.nextFloat() < CHANCE) {
            lastLaugh = now;
            roll(minecraft.getSoundManager(), random);
        }
    }

    /**
     * A roomful of laughter: a burst of voices at first, trailing off.
     */
    private static void roll(SoundManager sounds, RandomSource random) {
        int voices = 9 + random.nextInt(5);
        for (int i = 0; i < voices; i++) {
            boolean late = i > voices * 0.6F;
            int delay = 3 + random.nextInt(14) + (late ? 10 : 0);
            float pitch = 1.1F + random.nextFloat() * 0.45F;
            float volume = (0.2F + random.nextFloat() * 0.18F) * (late ? 0.6F : 1.0F);
            sounds.playDelayed(SimpleSoundInstance.forUI(LAUGHS[random.nextInt(LAUGHS.length)], pitch, volume), delay);
        }
    }
}
