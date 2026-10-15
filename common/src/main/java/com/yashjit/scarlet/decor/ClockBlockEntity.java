package com.yashjit.scarlet.decor;

import com.yashjit.scarlet.registry.ScarletBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A wall clock's works: it keeps nothing, and is there so its hands can be drawn turning with the time.
 */
public class ClockBlockEntity extends BlockEntity {

    public ClockBlockEntity(BlockPos pos, BlockState state) {
        super(ScarletBlockEntities.WALL_CLOCK.get(), pos, state);
    }
}
