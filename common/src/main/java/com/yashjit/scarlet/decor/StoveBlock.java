package com.yashjit.scarlet.decor;

import com.yashjit.scarlet.registry.ScarletBlockEntities;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A range: switched on and off with a click, its burners glowing as they heat. What a campfire would cook is put down
 * on a burner, one dish to each, and cooks there while the range is on, popping off done; a sneaking click with an
 * empty hand takes back what is on it.
 */
public class StoveBlock extends SwitchedDecorBlock implements EntityBlock {

    public StoveBlock(BlockBehaviour.Properties properties, Function<BlockState, VoxelShape> north) {
        super(properties, north, Kind.STOVE, false, SoundEvents.STONE_BUTTON_CLICK_ON, SoundEvents.STONE_BUTTON_CLICK_OFF);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (!level.recipeAccess().propertySet(RecipePropertySet.CAMPFIRE_INPUT).test(stack)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof StoveBlockEntity stove) {
            if (!stove.put(server, stack)) {
                // every burner taken
                return InteractionResult.CONSUME;
            }
            stack.consume(1, player);
            server.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.25F, 1.8F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive() && level.getBlockEntity(pos) instanceof StoveBlockEntity stove && !stove.isEmpty()) {
            if (level instanceof ServerLevel server) {
                for (ItemStack taken : stove.takeAll(server)) {
                    player.getInventory().placeItemBackInInventory(taken, Prediction.SERVER_ONLY);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StoveBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ScarletBlockEntities.STOVE.get()) {
            return null;
        }
        BlockEntityTicker<StoveBlockEntity> ticker = level instanceof ServerLevel
                ? (at, pos, current, stove) -> StoveBlockEntity.cookTick((ServerLevel) at, pos, current, stove)
                : StoveBlockEntity::particleTick;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> typed = (BlockEntityTicker<T>) ticker;
        return typed;
    }
}
