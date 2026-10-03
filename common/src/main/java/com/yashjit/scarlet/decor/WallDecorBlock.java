package com.yashjit.scarlet.decor;

import java.util.function.Function;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Something that hangs on a wall: a clock, a picture, a poster. Put against a wall's side, it faces out from it.
 */
public class WallDecorBlock extends DecorBlock {

    public WallDecorBlock(BlockBehaviour.Properties properties, Function<BlockState, VoxelShape> north) {
        super(properties, north);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        Direction face = context.getClickedFace();
        return state == null || !face.getAxis().isHorizontal() ? state : state.setValue(FACING, face);
    }
}
