package com.yashjit.scarlet.hex;

import com.yashjit.scarlet.decor.EraDecor;
import com.yashjit.scarlet.decor.RefrigeratorBlock;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Era decorations people have put down inside a Hex themselves. The town's own are made over with the town; these are
 * kept track of here, so they follow their Hex's era as it spreads, and go back to the present day as its wall falls
 * past them.
 */
public final class HexDecor {

    /** Era decorations change only how they look, so nothing around them is told. */
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** How long after a change of era every decoration is looked at each tick, for the sweep to reach them. */
    private static final int SWEEP_MARGIN = 40;

    private HexDecor() {
    }

    /**
     * A decoration was put down: inside a Hex, it follows that Hex's era from now on.
     */
    public static void placed(ServerLevel level, BlockPos pos) {
        Vec3 at = Vec3.atCenterOf(pos);
        HexData data = HexData.of(level);
        for (Hex hex : data.all()) {
            if (hex.contains(at)) {
                data.decor().add(pos.asLong());
                data.setDirty();
                return;
            }
        }
    }

    /**
     * Brings every decoration put down inside a Hex into the era showing where it stands: while an era spreads or a
     * wall falls, every tick, and otherwise once a second, which also lets go of any broken since.
     */
    static void tick(ServerLevel level, HexData data, long now) {
        if (data.decor().isEmpty()) {
            return;
        }
        boolean busy = false;
        for (Hex hex : data.all()) {
            if (hex.phase == Hex.Phase.COLLAPSING || now - hex.eraSince <= Hexes.eraSweepTicks(hex.radius) + SWEEP_MARGIN) {
                busy = true;
                break;
            }
        }
        if (!busy && now % 20 != 0) {
            return;
        }
        LongIterator positions = data.decor().iterator();
        while (positions.hasNext()) {
            BlockPos pos = BlockPos.of(positions.nextLong());
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!EraDecor.is(state)) {
                positions.remove();
                data.setDirty();
                continue;
            }
            Hex hex = around(data, pos, now);
            Era era = hex == null ? Era.PRESENT : showing(hex, pos, now);
            if (state.getValue(EraDecor.ERA) != era) {
                set(level, pos, state, era);
            }
            if (hex == null) {
                positions.remove();
                data.setDirty();
            }
        }
    }

    /** The Hex whose wall stands round a place right now, if any. */
    private static @Nullable Hex around(HexData data, BlockPos pos, long now) {
        Vec3 at = Vec3.atCenterOf(pos);
        for (Hex hex : data.all()) {
            if (HexShape.contains(hex.center, Hexes.wallRadius(hex, now), at)) {
                return hex;
            }
        }
        return null;
    }

    /** The era a Hex shows at a place: the new one where it has spread to, the old one further out. */
    private static Era showing(Hex hex, BlockPos pos, long now) {
        if (hex.previousEra == hex.era) {
            return hex.era;
        }
        float front = Hexes.eraFront(hex.radius, now - hex.eraSince);
        return HexShape.level(hex.center, 1.0F, Vec3.atCenterOf(pos)) <= front ? hex.era : hex.previousEra;
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state, Era era) {
        level.setBlock(pos, state.setValue(EraDecor.ERA, era), QUIET);
        if (state.hasProperty(RefrigeratorBlock.HALF) && state.getValue(RefrigeratorBlock.HALF) == DoubleBlockHalf.LOWER) {
            BlockPos upper = pos.above();
            BlockState top = level.getBlockState(upper);
            if (top.is(state.getBlock())) {
                level.setBlock(upper, top.setValue(EraDecor.ERA, era), QUIET);
            }
        }
    }
}
