package com.yashjit.scarlet.decor;

import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.hex.HexDecor;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A piece of furniture in the look of an era. It faces whoever put it down, and takes on the era of the Hex it was put
 * down in; from then on it follows that Hex's era, and goes back to the present day when the Hex falls.
 */
public class DecorBlock extends HorizontalDirectionalBlock {

    private final Function<BlockState, VoxelShape> shapes;

    /**
     * @param north the shape of a state facing north
     */
    public DecorBlock(BlockBehaviour.Properties properties, Function<BlockState, VoxelShape> north) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(EraDecor.ERA, Era.PRESENT));
        this.shapes = getShapeForEachState(state -> Shapes.rotateHorizontal(north.apply(state)).get(state.getValue(FACING)));
    }

    /** A shape for each era, facing north, in order from the 1950s. */
    public static Function<BlockState, VoxelShape> byEra(VoxelShape... north) {
        return state -> north[Math.min(state.getValue(EraDecor.ERA).ordinal(), north.length - 1)];
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, EraDecor.ERA);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(EraDecor.ERA, EraDecor.eraAt(context.getLevel(), context.getClickedPos()));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (level instanceof ServerLevel server) {
            HexDecor.placed(server, pos);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.apply(state);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
