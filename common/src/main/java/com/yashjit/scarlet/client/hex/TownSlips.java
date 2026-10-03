package com.yashjit.scarlet.client.hex;

import com.yashjit.scarlet.client.fx.Glitch;
import com.yashjit.scarlet.client.fx.SlipFx;
import com.yashjit.scarlet.network.TownGlitchPayload;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Parts of a Hex's town slipping, as this client shows them: for a moment a house stands in another era, or is not
 * there at all, then it is as it really is again. Only shown here; the world itself never changes, and a block the
 * world changes meanwhile is left as the world has it.
 *
 * <p>Slips may come over the same blocks before the last has let go of them; each block keeps what it really is until
 * the last of them does.
 */
public final class TownSlips {

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE | Block.UPDATE_KNOWN_SHAPE;
    /** For slips coming thick and fast, whose blocks needn't all change on the very same frame. */
    private static final int QUICK_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final List<Slip> SLIPS = new ArrayList<>();
    /** What each block showing something else really is, and which slip it shows. */
    private static final Long2ObjectOpenHashMap<BlockState> REAL = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<Slip> OWNER = new Long2ObjectOpenHashMap<>();
    /** When the world itself last changed each block slips cover, which no slip begun before then shows again. */
    private static final Long2LongOpenHashMap CHANGED = new Long2LongOpenHashMap();
    private static @Nullable ClientLevel seenLevel;

    private TownSlips() {
    }

    public static void receive(TownGlitchPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        forgetIfLeft(minecraft.level);
        if (minecraft.level == null || payload.positions().length != payload.states().length) {
            return;
        }
        BlockState[] shown = new BlockState[payload.states().length];
        for (int i = 0; i < shown.length; i++) {
            shown[i] = Block.stateById(payload.states()[i]);
        }
        slip(minecraft.level, payload.positions(), shown, payload.pattern(), true, false);
    }

    /**
     * Shows blocks as something else on each of the next {@link TownGlitchPayload#TICKS} ticks whose bit is set in
     * {@code pattern}, and as they really are in between and after.
     *
     * @param marked whether the slip is marked over the part it covers, red bars tearing across it and red static on it
     * @param quick  whether its blocks may change a frame or so apart, for slips that come thick and fast
     */
    public static void slip(ClientLevel level, long[] positions, BlockState[] shown, int pattern, boolean marked, boolean quick) {
        forgetIfLeft(level);
        if (positions.length == 0 || positions.length != shown.length) {
            return;
        }
        long start = level.getGameTime();
        SLIPS.add(new Slip(positions, shown, pattern, start, quick));
        if (marked) {
            BlockPos first = BlockPos.of(positions[0]);
            AABB box = new AABB(first);
            for (long key : positions) {
                box = box.minmax(new AABB(BlockPos.of(key)));
            }
            SlipFx.part(box.inflate(0.15), start, TownGlitchPayload.TICKS, pattern);
        }
    }

    /**
     * The world itself has changed a block, so whatever slip is showing there lets go of it, and leaves it as the world
     * has it once it is over, even should the world have changed it into just what the slip showed.
     */
    public static void changed(BlockPos pos) {
        long key = pos.asLong();
        if (OWNER.remove(key) != null) {
            REAL.remove(key);
        }
        if (seenLevel != null && !SLIPS.isEmpty()) {
            CHANGED.put(key, seenLevel.getGameTime());
        }
    }

    public static void tick(Minecraft minecraft) {
        forgetIfLeft(minecraft.level);
        ClientLevel level = minecraft.level;
        if (level == null || SLIPS.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        CHANGED.long2LongEntrySet().removeIf(entry -> now - entry.getLongValue() > TownGlitchPayload.TICKS + 1);
        Iterator<Slip> iterator = SLIPS.iterator();
        while (iterator.hasNext()) {
            Slip slip = iterator.next();
            int tick = (int) (now - slip.start);
            boolean other = tick >= 0 && tick < TownGlitchPayload.TICKS && (slip.pattern >> tick & 1) != 0;
            if (other != slip.showing) {
                show(level, slip, other);
            }
            if (tick >= TownGlitchPayload.TICKS) {
                if (slip.showing) {
                    show(level, slip, false);
                }
                iterator.remove();
            }
        }
    }

    private static void show(ClientLevel level, Slip slip, boolean other) {
        slip.showing = other;
        int flags = slip.quick ? QUICK_FLAGS : FLAGS;
        for (int i = 0; i < slip.positions.length; i++) {
            long key = slip.positions[i];
            BlockPos pos = BlockPos.of(key);
            BlockState current = level.getBlockState(pos);
            Slip owner = OWNER.get(key);
            if (other) {
                if (CHANGED.containsKey(key) && CHANGED.get(key) >= slip.start) {
                    continue;
                }
                // unless it is still showing another slip, what it shows now is what it really is
                if (owner == null || current != owner.shown(key)) {
                    REAL.put(key, current);
                }
                OWNER.put(key, slip);
                if (current != slip.shown[i]) {
                    level.setBlock(pos, slip.shown[i], flags);
                }
            } else if (owner == slip) {
                OWNER.remove(key);
                BlockState real = REAL.remove(key);
                // a block the world has changed meanwhile is left as the world has it
                if (real != null && current == slip.shown[i] && real != current) {
                    level.setBlock(pos, real, flags);
                }
            }
        }
        if (slip.quick) {
            return;
        }
        // the static of a changed channel, here and there over it, under the red magic that has hold of it
        RandomSource random = level.getRandom();
        for (int i = 0; i < 3; i++) {
            Vec3 at = Vec3.atCenterOf(BlockPos.of(slip.positions[random.nextInt(slip.positions.length)]));
            Glitch.puff(at.subtract(0.6, 0.6, 0.6), at.add(0.6, 0.6, 0.6));
        }
        Vec3 at = Vec3.atCenterOf(BlockPos.of(slip.positions[random.nextInt(slip.positions.length)]));
        level.playLocalSound(at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.2F, 1.8F + random.nextFloat() * 0.2F, false);
    }

    private static void forgetIfLeft(@Nullable ClientLevel level) {
        if (level != seenLevel) {
            seenLevel = level;
            SLIPS.clear();
            REAL.clear();
            OWNER.clear();
            CHANGED.clear();
        }
    }

    /**
     * @param shown what each block shows while the part has slipped
     * @param quick whether its blocks may change a frame or so apart
     */
    private static final class Slip {

        final long[] positions;
        final BlockState[] shown;
        final int pattern;
        final long start;
        final boolean quick;
        boolean showing;
        private @Nullable Long2ObjectOpenHashMap<BlockState> byPosition;

        Slip(long[] positions, BlockState[] shown, int pattern, long start, boolean quick) {
            this.positions = positions;
            this.shown = shown;
            this.pattern = pattern;
            this.start = start;
            this.quick = quick;
        }

        @Nullable BlockState shown(long key) {
            if (byPosition == null) {
                byPosition = new Long2ObjectOpenHashMap<>(positions.length);
                for (int i = 0; i < positions.length; i++) {
                    byPosition.put(positions[i], shown[i]);
                }
            }
            return byPosition.get(key);
        }
    }
}
