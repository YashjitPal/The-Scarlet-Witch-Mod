package com.yashjit.scarlet.decor;

import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * A decoration that switches on and off with a click: a television, a radio, a lamp, a range. A range that is on cooks
 * what is held to it, the way a campfire would, there and then.
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
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        Item cooked = kind == Kind.STOVE && state.getValue(LIT) ? Cooking.COOKED.get(stack.getItem()) : null;
        if (cooked == null) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel server) {
            stack.consume(1, player);
            player.getInventory().placeItemBackInInventory(new ItemStack(cooked), Prediction.SERVER_ONLY);
            server.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.35F, 1.6F);
            server.playSound(null, pos, SoundEvents.SMOKER_SMOKE, SoundSource.BLOCKS, 0.8F, 1.0F);
            server.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 8, 0.2, 0.05, 0.2, 0.01);
        }
        return InteractionResult.SUCCESS;
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

    /** What a range cooks, and into what: whatever a campfire would. */
    private static final class Cooking {
        static final Map<Item, Item> COOKED = Map.of(Items.BEEF, Items.COOKED_BEEF, Items.PORKCHOP, Items.COOKED_PORKCHOP, Items.CHICKEN,
                Items.COOKED_CHICKEN, Items.MUTTON, Items.COOKED_MUTTON, Items.RABBIT, Items.COOKED_RABBIT, Items.COD, Items.COOKED_COD,
                Items.SALMON, Items.COOKED_SALMON, Items.POTATO, Items.BAKED_POTATO, Items.KELP, Items.DRIED_KELP);
    }
}
