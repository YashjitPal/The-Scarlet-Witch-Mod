package com.yashjit.scarlet.decor;

import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A couch, a block of it at a time: couches side by side facing the same way join into one long sofa, an arm only at
 * either end.
 */
public class CouchBlock extends SeatBlock {

    public static final EnumProperty<CouchPart> PART = EnumProperty.create("part", CouchPart.class);

    public CouchBlock(BlockBehaviour.Properties properties, Function<BlockState, VoxelShape> north, ToDoubleFunction<BlockState> seatHeight) {
        super(properties, north, seatHeight);
        registerDefaultState(defaultBlockState().setValue(PART, CouchPart.SINGLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(PART, partAt(context.getLevel(), context.getClickedPos(), state.getValue(FACING)));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        if (direction.getAxis().isHorizontal()) {
            return state.setValue(PART, partAt(level, pos, state.getValue(FACING)));
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
    }

    /**
     * Which length of sofa a couch here is, by whether couches facing the same way stand to its left and right.
     */
    public static CouchPart partAt(LevelReader level, BlockPos pos, Direction facing) {
        boolean left = joins(level.getBlockState(pos.relative(facing.getCounterClockWise())), facing);
        boolean right = joins(level.getBlockState(pos.relative(facing.getClockWise())), facing);
        if (left && right) {
            return CouchPart.MIDDLE;
        }
        if (left) {
            return CouchPart.RIGHT;
        }
        return right ? CouchPart.LEFT : CouchPart.SINGLE;
    }

    private static boolean joins(BlockState state, Direction facing) {
        return state.getBlock() instanceof CouchBlock && state.getValue(FACING) == facing;
    }
}
