package com.yashjit.scarlet.decor;

import com.yashjit.scarlet.entity.Seat;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Something to sit on: a click sits you down facing the way it does, and sneaking stands you up again.
 */
public class SeatBlock extends DecorBlock {

    private final ToDoubleFunction<BlockState> seatHeight;

    /**
     * @param seatHeight how high over the floor the cushion is, in blocks
     */
    public SeatBlock(BlockBehaviour.Properties properties, Function<BlockState, VoxelShape> north, ToDoubleFunction<BlockState> seatHeight) {
        super(properties, north);
        this.seatHeight = seatHeight;
    }

    public double seatHeight(BlockState state) {
        return seatHeight.applyAsDouble(state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive() || player.isPassenger()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server && !Seat.sit(server, pos, seatHeight(state), state.getValue(FACING), player)) {
            return InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }
}
