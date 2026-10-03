package com.yashjit.scarlet.magic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yashjit.scarlet.player.ScarletPlayerData;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.codec.StreamCodec;

/**
 * A player's spellcasting, saved with them and synced to everyone who can see them. Values are event stamps rather
 * than counters, so energy, cooldowns and animations all advance on every client without further updates.
 *
 * @param selected        spell chosen in the wheel
 * @param energy          energy at {@code energyAt}; see {@link Magic#energy}
 * @param energyAt        game time the energy was last settled
 * @param lastCastAt      game time of the last cast, which delays fast regeneration and drives the cast gesture
 * @param castCount       casts so far; its parity picks the hand, so rapid fire alternates hands
 * @param channel         spell being channeled, or -1
 * @param channelStart    game time the channel began
 * @param levitatingSince game time levitation began, or {@link ScarletPlayerData#NEVER} when not levitating
 * @param readyAt         game time each spell comes off cooldown
 */
public record MagicState(int selected, float energy, long energyAt, long lastCastAt, int castCount, int channel, long channelStart,
                         long levitatingSince, List<Long> readyAt) {

    public static final int NO_CHANNEL = -1;

    public static final MagicState DEFAULT = new MagicState(0, Mastery.maxEnergy(1), ScarletPlayerData.NEVER, ScarletPlayerData.NEVER, 0,
            NO_CHANNEL, ScarletPlayerData.NEVER, ScarletPlayerData.NEVER, Collections.nCopies(Spell.count(), ScarletPlayerData.NEVER));

    /**
     * Keeps the chosen spell, and whether you were levitating so flight resumes cleanly after a relog. Everything
     * else settles within seconds.
     */
    public static final MapCodec<MagicState> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("selected", 0).forGetter(MagicState::selected),
            Codec.BOOL.optionalFieldOf("levitating", false).forGetter(MagicState::levitating)
    ).apply(instance, (selected, levitating) -> {
        MagicState state = DEFAULT.withSelected(selected);
        return levitating ? state.withLevitation(true, state.energy(), 0L) : state;
    }));

    public static final Codec<MagicState> CODEC = MAP_CODEC.codec();

    public static final StreamCodec<ByteBuf, MagicState> STREAM_CODEC = StreamCodec.of((buf, state) -> {
        buf.writeByte(state.selected);
        buf.writeFloat(state.energy);
        buf.writeLong(state.energyAt);
        buf.writeLong(state.lastCastAt);
        buf.writeInt(state.castCount);
        buf.writeByte(state.channel);
        buf.writeLong(state.channelStart);
        buf.writeLong(state.levitatingSince);
        buf.writeByte(state.readyAt.size());
        for (long ready : state.readyAt) {
            buf.writeLong(ready);
        }
    }, buf -> {
        int selected = buf.readByte();
        float energy = buf.readFloat();
        long energyAt = buf.readLong();
        long lastCastAt = buf.readLong();
        int castCount = buf.readInt();
        int channel = buf.readByte();
        long channelStart = buf.readLong();
        long levitatingSince = buf.readLong();
        int count = buf.readByte();
        List<Long> readyAt = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            readyAt.add(buf.readLong());
        }
        return new MagicState(selected, energy, energyAt, lastCastAt, castCount, channel, channelStart, levitatingSince, List.copyOf(readyAt));
    });

    public Spell selectedSpell() {
        return Spell.choosable(selected);
    }

    public boolean channeling() {
        return channel != NO_CHANNEL;
    }

    public boolean channeling(Spell spell) {
        return channel == spell.ordinal();
    }

    public boolean levitating() {
        return levitatingSince != ScarletPlayerData.NEVER;
    }

    public long readyAt(Spell spell) {
        return spell.ordinal() < readyAt.size() ? readyAt.get(spell.ordinal()) : ScarletPlayerData.NEVER;
    }

    public MagicState withSelected(int selected) {
        return new MagicState(Math.floorMod(selected, Spell.count()), energy, energyAt, lastCastAt, castCount, channel, channelStart,
                levitatingSince, readyAt);
    }

    MagicState withCast(Spell spell, float energyAfter, long now) {
        return new MagicState(selected, energyAfter, now, now, castCount + 1, channel, channelStart, levitatingSince, withReady(spell, now + spell.cooldown()));
    }

    /**
     * Pays for a spell that is not a strike, so no hand is thrown forward for it.
     */
    MagicState withSpent(Spell spell, float energyAfter, long now) {
        return new MagicState(selected, energyAfter, now, now, castCount, channel, channelStart, levitatingSince, withReady(spell, now + spell.cooldown()));
    }

    /**
     * Starts or ends a channel. Ending one counts as casting, so fast regeneration waits a moment afterwards.
     */
    MagicState withChannel(int channel, float energyNow, long now) {
        return new MagicState(selected, energyNow, now, channel == NO_CHANNEL ? now : lastCastAt, castCount, channel,
                channel == NO_CHANNEL ? channelStart : now, levitatingSince, readyAt);
    }

    MagicState withLevitation(boolean on, float energyNow, long now) {
        return new MagicState(selected, energyNow, now, on ? lastCastAt : now, castCount, channel, channelStart,
                on ? now : ScarletPlayerData.NEVER, readyAt);
    }

    /**
     * Settles energy at {@code now}, for example after a channel pays for something it blocked.
     */
    MagicState withEnergy(float energyNow, long now) {
        return new MagicState(selected, energyNow, now, lastCastAt, castCount, channel, channelStart, levitatingSince, readyAt);
    }

    MagicState withCooldown(Spell spell, long readyTime) {
        return new MagicState(selected, energy, energyAt, lastCastAt, castCount, channel, channelStart, levitatingSince, withReady(spell, readyTime));
    }

    private List<Long> withReady(Spell spell, long readyTime) {
        List<Long> ready = new ArrayList<>(readyAt);
        while (ready.size() < Spell.count()) {
            ready.add(ScarletPlayerData.NEVER);
        }
        ready.set(spell.ordinal(), readyTime);
        return List.copyOf(ready);
    }
}
