package com.yashjit.scarlet.decor;

import com.yashjit.scarlet.registry.ScarletItems;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A toaster: bread put in and the lever pushed down, it toasts a few seconds, glowing in its slots and ticking, then
 * pops two slices of toast up out of them with a ding. Pushed up early, it gives the bread back.
 */
public class ToasterBlock extends DecorBlock {

    /** Whether bread is in it, toasting. */
    public static final BooleanProperty LIT = SwitchedDecorBlock.LIT;

    /** How long the bread toasts, in ticks, and how many slices of toast a loaf of bread makes. */
    private static final int TOASTS_FOR = 110;
    private static final int SLICES = 2;

    public ToasterBlock(BlockBehaviour.Properties properties, Function<BlockState, VoxelShape> north) {
        super(properties, north);
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(LIT, false);
    }

    /** Bread put in, and the lever pushed down on it. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (!stack.is(Items.BREAD) || state.getValue(LIT)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel server) {
            stack.consume(1, player);
            server.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
            server.scheduleTick(pos, this, TOASTS_FOR);
            server.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 0.8F);
            server.playSound(null, pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.2F, 1.8F);
        }
        return InteractionResult.SUCCESS;
    }

    /** The lever pushed up before it is done: the bread comes back, barely warm; with nothing in it, the lever only clicks. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        level.playSound(player, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, state.getValue(LIT) ? 1.4F : 1.6F);
        if (level instanceof ServerLevel server && state.getValue(LIT)) {
            server.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
            pop(server, pos, new ItemStack(Items.BREAD));
        }
        return InteractionResult.SUCCESS;
    }

    /** Done: up pops the toast, with a ding. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
        pop(level, pos, new ItemStack(ScarletItems.TOAST.get(), SLICES));
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.7F, 2.0F);
        level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.45, pos.getZ() + 0.5, 5, 0.1, 0.02, 0.05, 0.01);
    }

    /** What is in it springing up out of its slots. */
    private static void pop(ServerLevel level, BlockPos pos, ItemStack stack) {
        level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.3F, 1.9F);
        RandomSource random = level.getRandom();
        ItemEntity popped = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.45, pos.getZ() + 0.5, stack,
                random.nextGaussian() * 0.02, 0.32 + random.nextDouble() * 0.06, random.nextGaussian() * 0.02);
        popped.setDefaultPickUpDelay();
        level.addFreshEntity(popped);
    }

    /** Toasting: the glow in its slots, a wisp of smoke now and then, and the tick of its timer. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        if (random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.4 + random.nextDouble() * 0.2, pos.getY() + 0.4, pos.getZ() + 0.45 + random.nextDouble() * 0.1,
                    0.0, 0.01, 0.0);
        }
        if (random.nextInt(4) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.08F, 2.0F, false);
        }
    }

    /** Broken while it toasts, the bread falls out. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel server && state.getValue(LIT) && !player.preventsBlockDrops()) {
            pop(server, pos, new ItemStack(Items.BREAD));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
