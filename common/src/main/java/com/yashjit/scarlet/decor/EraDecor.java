package com.yashjit.scarlet.decor;

import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.hex.Hex;
import com.yashjit.scarlet.hex.HexSnapshot;
import com.yashjit.scarlet.hex.Hexes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;

/**
 * Era decorations: furniture that changes model with the era of the Hex it stands in, the way Wanda's living room
 * changes every episode. Outside a Hex each shows its present-day self.
 */
public final class EraDecor {

    /** The era a decoration is in. */
    public static final EnumProperty<Era> ERA = EnumProperty.create("era", Era.class);

    private EraDecor() {
    }

    /**
     * The era something put down at a place takes on: its Hex's inside one, the present day anywhere else.
     */
    public static Era eraAt(Level level, BlockPos pos) {
        Vec3 at = Vec3.atCenterOf(pos);
        if (level instanceof ServerLevel server) {
            Hex hex = Hexes.at(server, at);
            return hex != null ? hex.era() : Era.PRESENT;
        }
        double now = level.getGameTime();
        HexSnapshot hex = Hexes.clientHexAt(at, now);
        return hex != null ? Hexes.eraAt(hex, at, now) : Era.PRESENT;
    }

    public static boolean is(BlockState state) {
        return state.hasProperty(ERA);
    }

    /** The same decoration in another era, anything else as it is. */
    public static BlockState inEra(BlockState state, Era era) {
        return state.hasProperty(ERA) ? state.setValue(ERA, era) : state;
    }
}
