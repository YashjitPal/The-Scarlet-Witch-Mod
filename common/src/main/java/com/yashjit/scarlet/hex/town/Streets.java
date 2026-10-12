package com.yashjit.scarlet.hex.town;

import static com.yashjit.scarlet.hex.town.Houses.AIR;
import static com.yashjit.scarlet.hex.town.Houses.FENCE;
import static com.yashjit.scarlet.hex.town.Houses.FLOWER;
import static com.yashjit.scarlet.hex.town.Houses.FULL;
import static com.yashjit.scarlet.hex.town.Houses.LEAVES;
import static com.yashjit.scarlet.hex.town.Houses.lantern;
import static com.yashjit.scarlet.hex.town.Houses.pillar;
import static com.yashjit.scarlet.hex.town.Houses.slab;
import static com.yashjit.scarlet.hex.town.Houses.stairs;

import com.yashjit.scarlet.hex.town.Blueprint.Frame;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The streets of the town, and its square. A street is a {@value TownPlan#STREET_WIDTH}-wide strip: a sidewalk each
 * side of a road with a dashed center line, and a street lamp at every lot line.
 */
public final class Streets {

    private static final int GROUND = 0;
    private static final int PAVE = 1;
    private static final int FURNISH = 2;
    private static final int[] STREET_TICKS = {6, 14, 8};

    private static final int SQUARE_GROUND = 0;
    private static final int PATHS = 1;
    private static final int GAZEBO = 2;
    private static final int GAZEBO_ROOF = 3;
    private static final int GARDEN = 4;
    private static final int[] SQUARE_TICKS = {8, 10, 14, 16, 14};

    private Streets() {
    }

    /**
     * A stretch of street, running across the part: its grid's b crosses the street. Where the road it runs on into
     * is laid higher or lower, its end climbs or steps down to meet it in a flight of steps the color of the road and
     * the sidewalk, rather than leaving a ledge to climb.
     *
     * @param before where the road past its first column is laid; its own ground for level with it
     * @param after  and past its last
     * @param lamps  where along the stretch its lamps stand
     */
    public static Blueprint street(Frame frame, int length, int ground, Houses.Heights heights, int before, int after, int... lamps) {
        Blueprint blueprint = new Blueprint(frame, STREET_TICKS);
        Houses.level(blueprint, length, TownPlan.STREET_WIDTH, ground, heights);
        int last = TownPlan.STREET_WIDTH - 1;
        int middle = last / 2;
        // how high each column along it is laid, and which way it climbs if it is a step; each end has half to climb in
        int[] levels = new int[length];
        Direction[] climbs = new Direction[length];
        Arrays.fill(levels, ground);
        int reach = Math.max(1, length / 2);
        steps(levels, climbs, ground, before, reach, 0, 1, frame.left(), frame.right());
        steps(levels, climbs, ground, after, reach, length - 1, -1, frame.right(), frame.left());
        for (int a = 0; a < length; a++) {
            int y = levels[a];
            if (climbs[a] != null) {
                for (int b = 0; b <= last; b++) {
                    boolean side = b == 0 || b == last;
                    if (y > ground) {
                        blueprint.fill(a, a, b, b, ground + 1, y - 1, side ? Role.SIDEWALK : Role.ROAD, FULL, PAVE);
                    } else {
                        blueprint.fill(a, a, b, b, y + 1, ground, Role.CLEAR, AIR, PAVE);
                    }
                    blueprint.set(a, b, y, side ? Role.SIDEWALK_STEP : Role.ROAD_STEP, stairs(climbs[a], Half.BOTTOM), PAVE);
                    blueprint.fill(a, a, b, b, y + 1, y + 2, Role.CLEAR, AIR, PAVE);
                }
                if (y <= ground) {
                    continue;
                }
            }
            blueprint.set(a, 0, ground, Role.SIDEWALK, FULL, PAVE);
            blueprint.set(a, last, ground, Role.SIDEWALK, FULL, PAVE);
            for (int b = 1; b < last; b++) {
                boolean line = climbs[a] == null && b == middle && Math.floorMod(along(frame, a), 4) < 2;
                blueprint.set(a, b, ground, line ? Role.ROAD_LINE : Role.ROAD, FULL, PAVE);
            }
        }
        for (int a : lamps) {
            int y = levels[Math.clamp(a, 0, length - 1)];
            lamp(blueprint, a, 0, y);
            lamp(blueprint, a, last, y);
        }
        return blueprint;
    }

    /**
     * Lays a flight of steps into one end of a stretch of street, from its own ground to where the road past that end
     * is laid, as far as it has room for.
     *
     * @param end  the column at that end
     * @param in   which way along the stretch inward is, from it
     * @param out  the way out past that end
     * @param back the way back in from it
     */
    private static void steps(int[] levels, Direction[] climbs, int ground, int meet, int reach, int end, int in, Direction out,
                              Direction back) {
        int rise = Math.clamp(meet - ground, -reach, reach);
        for (int k = 0; k < Math.abs(rise); k++) {
            int a = end + in * k;
            if (rise > 0) {
                // up to the road past the end, the step at the very end the highest
                levels[a] = ground + rise - k;
                climbs[a] = out;
            } else {
                // down to it, the step at the very end the lowest, and climbing back in
                levels[a] = ground + rise + 1 + k;
                climbs[a] = back;
            }
        }
    }

    /**
     * Where two streets cross: all road, with the sidewalks' corners at the four corners.
     */
    public static Blueprint crossing(Frame frame, int ground, Houses.Heights heights) {
        Blueprint blueprint = new Blueprint(frame, STREET_TICKS);
        int size = TownPlan.STREET_WIDTH;
        Houses.level(blueprint, size, size, ground, heights);
        for (int b = 0; b < size; b++) {
            for (int a = 0; a < size; a++) {
                boolean corner = (a == 0 || a == size - 1) && (b == 0 || b == size - 1);
                blueprint.set(a, b, ground, corner ? Role.SIDEWALK : Role.ROAD, FULL, PAVE);
            }
        }
        return blueprint;
    }

    /**
     * How far along the world a column of a part lies, so dashes line up from one stretch to the next.
     */
    private static int along(Frame frame, int a) {
        BlockPos pos = frame.at(a, 0, 0);
        return frame.acrossAxis() == Direction.Axis.X ? pos.getX() : pos.getZ();
    }

    private static void lamp(Blueprint blueprint, int a, int b, int ground) {
        blueprint.fill(a, a, b, b, ground + 1, ground + 3, Role.LAMP_POST, FENCE, FURNISH);
        blueprint.set(a, b, ground + 4, Role.LAMP, lantern(false), FURNISH);
    }

    // ---------------------------------------------------------------- the square

    /**
     * The town square across the main street from the caster's home: a lawn ringed by a hedge, paths crossing at a
     * gazebo, benches along them, street lamps at the entrances, flower beds and a pair of trees.
     */
    public static Blueprint square(Frame frame, int width, int depth, int g, Houses.Heights heights, RandomSource random) {
        Blueprint blueprint = new Blueprint(frame, SQUARE_TICKS);
        Houses.level(blueprint, width, depth, g, heights);
        int ca = width / 2;
        int cb = depth / 2;

        for (int b = 0; b < depth; b++) {
            for (int a = ca - 1; a <= ca + 1; a++) {
                blueprint.set(a, b, g, Role.PLAZA, FULL, PATHS);
            }
        }
        for (int a = 0; a < width; a++) {
            for (int b = cb - 1; b <= cb + 1; b++) {
                blueprint.set(a, b, g, Role.PLAZA, FULL, PATHS);
            }
        }

        // the gazebo: a raised floor with a railing between its posts, open where the paths come in
        int r = 3;
        for (int b = cb - r; b <= cb + r; b++) {
            for (int a = ca - r; a <= ca + r; a++) {
                boolean corner = Math.abs(a - ca) == r && Math.abs(b - cb) == r;
                if (!corner) {
                    blueprint.set(a, b, g, Role.FOUNDATION, FULL, GAZEBO);
                    blueprint.set(a, b, g + 1, Role.PORCH, FULL, GAZEBO);
                }
            }
        }
        for (int b = cb - r; b <= cb + r; b++) {
            for (int a = ca - r; a <= ca + r; a++) {
                int da = Math.abs(a - ca);
                int db = Math.abs(b - cb);
                boolean edge = da == r && db < r || db == r && da < r;
                boolean entrance = da == 0 || db == 0;
                boolean post = da == r && db == r - 1 || db == r && da == r - 1;
                if (post) {
                    blueprint.fill(a, a, b, b, g + 2, g + 4, Role.GAZEBO_POST, FENCE, GAZEBO);
                } else if (edge && !entrance) {
                    blueprint.set(a, b, g + 2, Role.GAZEBO_POST, FENCE, GAZEBO);
                }
            }
        }
        Direction[] inward = {frame.back(), frame.front(), frame.right(), frame.left()};
        blueprint.set(ca, cb - r - 1, g + 1, Role.STEP, stairs(inward[0], Half.BOTTOM), GAZEBO);
        blueprint.set(ca, cb + r + 1, g + 1, Role.STEP, stairs(inward[1], Half.BOTTOM), GAZEBO);
        blueprint.set(ca - r - 1, cb, g + 1, Role.STEP, stairs(inward[2], Half.BOTTOM), GAZEBO);
        blueprint.set(ca + r + 1, cb, g + 1, Role.STEP, stairs(inward[3], Half.BOTTOM), GAZEBO);
        // its roof: rings of stairs climbing to a point, a lantern hanging from the top
        for (int ring = 0; ring <= r; ring++) {
            int reach = r + 1 - ring;
            int y = g + 5 + ring;
            for (int b = cb - reach; b <= cb + reach; b++) {
                for (int a = ca - reach; a <= ca + reach; a++) {
                    int da = a - ca;
                    int db = b - cb;
                    if (Math.max(Math.abs(da), Math.abs(db)) != reach) {
                        continue;
                    }
                    Direction facing;
                    if (Math.abs(db) >= Math.abs(da)) {
                        facing = db < 0 ? frame.back() : frame.front();
                    } else {
                        facing = da < 0 ? frame.right() : frame.left();
                    }
                    blueprint.set(a, b, y, Role.ROOF, stairs(facing, Half.BOTTOM), GAZEBO_ROOF);
                }
            }
        }
        blueprint.set(ca, cb, g + 6 + r, Role.ROOF_SLAB, slab(SlabType.BOTTOM), GAZEBO_ROOF);
        blueprint.set(ca, cb, g + 5 + r, Role.LAMP, lantern(true), GAZEBO_ROOF);

        // a hedge around it all, open where the paths lead out
        for (int a = 0; a < width; a++) {
            for (int b : new int[] {0, depth - 1}) {
                if (Math.abs(a - ca) > 1) {
                    blueprint.set(a, b, g + 1, Role.HEDGE, LEAVES, GARDEN);
                }
            }
        }
        for (int b = 1; b < depth - 1; b++) {
            for (int a : new int[] {0, width - 1}) {
                if (Math.abs(b - cb) > 1) {
                    blueprint.set(a, b, g + 1, Role.HEDGE, LEAVES, GARDEN);
                }
            }
        }
        for (int a : new int[] {ca - 2, ca + 2}) {
            lamp(blueprint, a, 1, g);
            lamp(blueprint, a, depth - 2, g);
        }
        // benches facing the cross path
        for (int a0 : new int[] {ca - 10, ca + 8}) {
            for (int a = a0; a <= a0 + 2; a++) {
                blueprint.set(a, cb - 2, g + 1, Role.BENCH, stairs(frame.front(), Half.BOTTOM), GARDEN);
                blueprint.set(a, cb + 2, g + 1, Role.BENCH, stairs(frame.back(), Half.BOTTOM), GARDEN);
            }
        }
        // flower beds in the four lawns
        for (int quadrant = 0; quadrant < 4; quadrant++) {
            int fa = (quadrant & 1) == 0 ? ca / 2 - 2 : ca + ca / 2 - 1;
            int fb = (quadrant & 2) == 0 ? 2 : depth - 3;
            int kind = random.nextInt(EraStyle.FLOWERS);
            for (int a = fa; a < fa + 4; a++) {
                blueprint.set(a, fb, g + 1, Role.FLOWER, kind, FLOWER, GARDEN);
            }
        }
        tree(blueprint, 3, depth - 4, g);
        tree(blueprint, width - 4, depth - 4, g);
        return blueprint;
    }

    private static void tree(Blueprint blueprint, int a, int b, int g) {
        for (int dy = 3; dy <= 5; dy++) {
            int spread = dy == 5 ? 1 : 2;
            for (int db = -spread; db <= spread; db++) {
                for (int da = -spread; da <= spread; da++) {
                    if (Math.abs(da) == spread && Math.abs(db) == spread && spread == 2) {
                        continue;
                    }
                    blueprint.set(a + da, b + db, g + dy, Role.TREE_LEAVES, LEAVES, GARDEN);
                }
            }
        }
        blueprint.fill(a, a, b, b, g + 1, g + 4, Role.TREE_LOG, pillar(Direction.Axis.Y), GARDEN);
    }
}
