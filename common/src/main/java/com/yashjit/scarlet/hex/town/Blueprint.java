package com.yashjit.scarlet.hex.town;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Everything one part of the town is made of, in the order it builds itself: each piece belongs to a stage (the
 * ground, then the frame, the walls, the roof and so on) and within a stage they rise from the bottom up.
 *
 * <p>Pieces are laid out on the part's own grid: a runs across its front from left to right as seen from the
 * street, b runs back from the street, and y is the world height.
 */
public final class Blueprint {

    private final Frame frame;
    private final List<Piece> pieces;
    private final int[] stageTicks;

    /**
     * @param stageTicks how long each stage takes to build
     */
    public Blueprint(Frame frame, int... stageTicks) {
        this(frame, new ArrayList<>(), stageTicks);
    }

    private Blueprint(Frame frame, List<Piece> pieces, int[] stageTicks) {
        this.frame = frame;
        this.pieces = pieces;
        this.stageTicks = stageTicks;
    }

    public Frame frame() {
        return frame;
    }

    /**
     * The same blueprint, laid out from further across and back into its part: what is put down through it is put
     * down in this one, shifted over.
     */
    public Blueprint shifted(int a, int b) {
        return new Blueprint(new Frame(frame.at(a, b, 0), frame.front()), pieces, stageTicks);
    }

    public void set(int a, int b, int y, Role role, int paint, BlockState template, int stage) {
        pieces.add(new Piece(frame.at(a, b, y), role, paint, template, stage, 0));
    }

    public void set(int a, int b, int y, Role role, BlockState template, int stage) {
        set(a, b, y, role, 0, template, stage);
    }

    public void fill(int a0, int a1, int b0, int b1, int y0, int y1, Role role, int paint, BlockState template, int stage) {
        for (int y = y0; y <= y1; y++) {
            for (int b = b0; b <= b1; b++) {
                for (int a = a0; a <= a1; a++) {
                    set(a, b, y, role, paint, template, stage);
                }
            }
        }
    }

    public void fill(int a0, int a1, int b0, int b1, int y0, int y1, Role role, BlockState template, int stage) {
        fill(a0, a1, b0, b1, y0, y1, role, 0, template, stage);
    }

    /**
     * Takes away whatever an earlier step put at a spot, so a later one can put something else there.
     */
    public void clear(int a, int b, int y) {
        BlockPos pos = frame.at(a, b, y);
        pieces.removeIf(piece -> piece.pos().equals(pos));
    }

    /**
     * The pieces in building order, each with the tick after the start of the build that it appears at. Where two
     * steps put something at the same spot, the later one wins.
     */
    public List<Piece> schedule() {
        List<Piece> latest = new ArrayList<>();
        java.util.Map<BlockPos, Integer> at = new java.util.HashMap<>();
        for (Piece piece : pieces) {
            Integer index = at.get(piece.pos());
            if (index != null) {
                latest.set(index, piece);
            } else {
                at.put(piece.pos(), latest.size());
                latest.add(piece);
            }
        }
        latest.sort(Comparator.comparingInt(Piece::stage).thenComparingInt(piece -> piece.pos().getY()));
        int[] counts = new int[stageTicks.length];
        for (Piece piece : latest) {
            counts[Math.min(piece.stage(), counts.length - 1)]++;
        }
        int[] starts = new int[stageTicks.length];
        for (int i = 1; i < starts.length; i++) {
            starts[i] = starts[i - 1] + stageTicks[i - 1];
        }
        int[] seen = new int[stageTicks.length];
        List<Piece> timed = new ArrayList<>(latest.size());
        for (Piece piece : latest) {
            int stage = Math.min(piece.stage(), counts.length - 1);
            int tick = starts[stage] + stageTicks[stage] * seen[stage]++ / Math.max(1, counts[stage]);
            timed.add(piece.at(tick));
        }
        return timed;
    }

    public int duration() {
        int total = 0;
        for (int ticks : stageTicks) {
            total += ticks;
        }
        return total;
    }

    /**
     * @param tick ticks after the build starts that the piece appears
     */
    public record Piece(BlockPos pos, Role role, int paint, BlockState template, int stage, int tick) {

        Piece at(int tick) {
            return new Piece(pos, role, paint, template, stage, tick);
        }
    }

    /**
     * Where a part's grid lies in the world.
     *
     * @param origin the front-left corner as seen from the street, at height 0
     * @param front  which way the part faces, toward its street
     */
    public record Frame(BlockPos origin, Direction front) {

        public Direction back() {
            return front.getOpposite();
        }

        /** To the right, as seen from the street looking at the part. */
        public Direction right() {
            return back().getClockWise();
        }

        public Direction left() {
            return right().getOpposite();
        }

        public BlockPos at(int a, int b, int y) {
            Direction right = right();
            Direction back = back();
            return new BlockPos(origin.getX() + right.getStepX() * a + back.getStepX() * b, y,
                    origin.getZ() + right.getStepZ() * a + back.getStepZ() * b);
        }

        /** The world axis running across the part's front. */
        public Direction.Axis acrossAxis() {
            return right().getAxis();
        }

        /** The world axis running back from its front. */
        public Direction.Axis depthAxis() {
            return back().getAxis();
        }

        /**
         * A template state turned to face a direction, for any block with a horizontal facing.
         */
        public static BlockState facing(BlockState state, Direction direction) {
            return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.setValue(BlockStateProperties.HORIZONTAL_FACING, direction) : state;
        }
    }
}
