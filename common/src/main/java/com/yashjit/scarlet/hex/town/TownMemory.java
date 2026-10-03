package com.yashjit.scarlet.hex.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yashjit.scarlet.hex.HexBuild;
import com.yashjit.scarlet.hex.HexShape;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A town a Hex left behind when it fell, remembered by the ground it stood on, so that the next Hex its caster casts
 * over that ground with the same build raises it there again just as it was: the same streets, the same houses in the
 * same colors. Only how the town was laid out is kept, never its blocks, as the same grid lays out the same town every
 * time.
 *
 * @param origin  where the town's grid was laid out from
 * @param home    where its caster's home rose around them
 * @param center  the middle of the Hex that stood over it
 * @param reach   how far out that Hex's wall stood, at its furthest
 * @param homeLot where its caster had raised their home off its grid, if they had
 */
public record TownMemory(UUID caster, HexBuild mode, BlockPos origin, Direction forward, long seed, Vec3 home, Vec3 center, float reach,
                         Optional<TownPlan.HomeLot> homeLot) {

    public static final Codec<TownMemory> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("caster").forGetter(TownMemory::caster),
            HexBuild.CODEC.fieldOf("mode").forGetter(TownMemory::mode),
            BlockPos.CODEC.fieldOf("origin").forGetter(TownMemory::origin),
            Direction.CODEC.fieldOf("forward").forGetter(TownMemory::forward),
            Codec.LONG.fieldOf("seed").forGetter(TownMemory::seed),
            Vec3.CODEC.fieldOf("home").forGetter(TownMemory::home),
            Vec3.CODEC.fieldOf("center").forGetter(TownMemory::center),
            Codec.FLOAT.fieldOf("reach").forGetter(TownMemory::reach),
            TownPlan.HomeLot.CODEC.optionalFieldOf("home_lot").forGetter(TownMemory::homeLot)
    ).apply(i, TownMemory::new));

    public TownPlan.Grid grid() {
        return new TownPlan.Grid(origin, forward, seed);
    }

    /**
     * Whether the town stood on the ground at a point.
     */
    public boolean covers(double x, double z) {
        return HexShape.contains(center, reach, new Vec3(x, center.y, z));
    }

    /**
     * Whether all the ground another town stood on, this one stood on too.
     */
    public boolean holds(TownMemory other) {
        double side = other.reach;
        double corner = side * HexShape.CORNER;
        double[][] corners = {{corner, 0.0}, {corner / 2.0, side}, {-corner / 2.0, side}, {-corner, 0.0}, {-corner / 2.0, -side},
                {corner / 2.0, -side}};
        for (double[] at : corners) {
            if (HexShape.level(center, reach, other.center.add(at[0], 0.0, at[1])) > 1.0 + 1.0E-6) {
                return false;
            }
        }
        return true;
    }

    /**
     * The first of some towns to stand on the ground at a point, given oldest first: the one that comes back there.
     */
    public static @Nullable TownMemory first(List<TownMemory> towns, double x, double z) {
        for (TownMemory town : towns) {
            if (town.covers(x, z)) {
                return town;
            }
        }
        return null;
    }
}
