package com.yashjit.scarlet.decor;

import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A decoration that switches on and off with a click: a television, a radio, a lamp, a range.
 */
public class SwitchedDecorBlock extends DecorBlock {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private final boolean placedOn;
    private final SoundEvent switchOn;
    private final SoundEvent switchOff;
    private final Kind kind;

    public SwitchedDecorBlock(BlockBehaviour.Properties properties, Function<BlockState, VoxelShape> north, Kind kind, boolean placedOn,
                              SoundEvent switchOn, SoundEvent switchOff) {
        super(properties, north);
        this.kind = kind;
        this.placedOn = placedOn;
        this.switchOn = switchOn;
        this.switchOff = switchOff;
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(LIT, placedOn);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockState next = state.cycle(LIT);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        boolean on = next.getValue(LIT);
        level.playSound(player, pos, on ? switchOn : switchOff, SoundSource.BLOCKS, 0.6F, on ? 1.1F : 0.9F);
        level.gameEvent(player, on ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, pos);
        if (on && kind == Kind.RADIO && level.isClientSide()) {
            RadioSounds.heard(level, pos, next);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        if (kind == Kind.STOVE && random.nextInt(4) == 0) {
            // something on the boil
            level.addParticle(ParticleTypes.WHITE_SMOKE, pos.getX() + 0.25 + random.nextDouble() * 0.5, pos.getY() + 1.05,
                    pos.getZ() + 0.25 + random.nextDouble() * 0.5, 0.0, 0.03, 0.0);
        }
        if (kind == Kind.RADIO) {
            RadioSounds.heard(level, pos, state);
        }
    }

    public enum Kind {
        TELEVISION,
        RADIO,
        LAMP,
        STOVE
    }
}
