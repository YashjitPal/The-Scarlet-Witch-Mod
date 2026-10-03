package com.yashjit.scarlet.decor;

import com.yashjit.scarlet.hex.Era;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Not a block anyone puts down: what a parked car is drawn from, each era's car in a front half and a rear half, since
 * a car is longer than one block's model can reach.
 */
public class CarBodyBlock extends Block {

    public static final EnumProperty<Half> PART = EnumProperty.create("part", Half.class);

    public CarBodyBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(EraDecor.ERA, Era.PRESENT).setValue(PART, Half.FRONT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(EraDecor.ERA, PART);
    }

    public enum Half implements StringRepresentable {
        FRONT("front"),
        REAR("rear");

        private final String id;

        Half(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }
}
