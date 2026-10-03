package com.yashjit.scarlet.client.hex;

import com.yashjit.scarlet.decor.EraDecor;
import com.yashjit.scarlet.decor.SwitchedDecorBlock;
import com.yashjit.scarlet.hex.Era;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What a radio that is on plays: its era's theme, over and over with a breath between, from the nearest one switched
 * on. A tube radio in the 1950s plays the harp and bells of that era's title card, a boombox the synths of the 1980s.
 */
public final class RadioMusic {

    /** How far off a radio is still heard. */
    private static final double HEARD_FROM = 20.0;
    /** Ticks of quiet between one time through the theme and the next. */
    private static final int BETWEEN = 30;
    /** A radio not reported for this long is taken to be out of hearing. */
    private static final int FORGET = 200;

    /** The radios that are on, by position, with when each last reported itself. */
    private static final Long2LongOpenHashMap ON = new Long2LongOpenHashMap();
    private static long nextAt;
    private static @Nullable Level heardIn;

    private RadioMusic() {
    }

    /** A radio near the player is on. */
    public static void heard(Level level, BlockPos pos, BlockState state) {
        if (level != heardIn) {
            ON.clear();
            heardIn = level;
            nextAt = 0;
        }
        boolean fresh = !ON.containsKey(pos.asLong());
        ON.put(pos.asLong(), level.getGameTime());
        if (fresh) {
            // just switched on, or just come in hearing of it: start the tune at once
            nextAt = Math.min(nextAt, level.getGameTime());
        }
    }

    public static void tick(Minecraft minecraft) {
        Level level = minecraft.level;
        if (level == null || minecraft.player == null || level != heardIn) {
            ON.clear();
            return;
        }
        long now = level.getGameTime();
        ON.long2LongEntrySet().removeIf(entry -> now - entry.getLongValue() > FORGET || !isOn(level.getBlockState(BlockPos.of(entry.getLongKey()))));
        if (ON.isEmpty() || now < nextAt) {
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
        HexTunes.playAt(era, Vec3.atCenterOf(nearest), 1.4F);
        nextAt = now + HexTunes.length(era) + BETWEEN;
    }

    private static boolean isOn(BlockState state) {
        return state.getBlock() instanceof SwitchedDecorBlock radio && radio.kind() == SwitchedDecorBlock.Kind.RADIO
                && state.getValue(SwitchedDecorBlock.LIT);
    }
}
