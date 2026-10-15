package com.yashjit.scarlet.decor;

import com.yashjit.scarlet.registry.ScarletBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * What a refrigerator keeps, in its lower half: as much as a chest. It spills out when the refrigerator goes, whether
 * it is broken or a Hex's town takes it back.
 */
public class RefrigeratorBlockEntity extends BaseContainerBlockEntity {

    private static final int SIZE = 27;

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final ContainerOpenersCounter openers = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            // the door coming off its seal, and the light inside
            swing(state, true, SoundEvents.BARREL_OPEN, 0.5F, 1.4F);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            // the door swinging to on its seal
            swing(state, false, SoundEvents.BARREL_CLOSE, 0.45F, 1.25F);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int previous, int current) {
        }

        @Override
        public boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof ChestMenu menu && menu.getContainer() == RefrigeratorBlockEntity.this;
        }
    };

    public RefrigeratorBlockEntity(BlockPos pos, BlockState state) {
        super(ScarletBlockEntities.REFRIGERATOR.get(), pos, state);
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.scarlet.refrigerator");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return ChestMenu.threeRows(id, inventory, this);
    }

    @Override
    public void startOpen(ContainerUser user) {
        if (!remove && level != null && !user.getLivingEntity().isSpectator()) {
            openers.incrementOpeners(user.getLivingEntity(), level, worldPosition, getBlockState(), user.getContainerInteractionRange());
        }
    }

    @Override
    public void stopOpen(ContainerUser user) {
        if (!remove && level != null && !user.getLivingEntity().isSpectator()) {
            openers.decrementOpeners(user.getLivingEntity(), level, worldPosition, getBlockState());
        }
    }

    @Override
    public List<ContainerUser> getEntitiesWithContainerOpen() {
        return level == null ? List.of() : openers.getEntitiesWithContainerOpen(level, worldPosition);
    }

    /** Whether anyone still has it open: the door shuts behind the last of them, however they left. */
    public void recheckOpen() {
        if (!remove && level != null) {
            openers.recheckOpeners(level, worldPosition, getBlockState());
        }
    }

    /** The door swung open or shut, heard from in front of it; the top half follows the bottom. */
    private void swing(BlockState state, boolean open, SoundEvent sound, float volume, float pitch) {
        if (level == null || !state.hasProperty(RefrigeratorBlock.OPEN)) {
            return;
        }
        level.setBlockAndUpdate(worldPosition, state.setValue(RefrigeratorBlock.OPEN, open));
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        level.playSound(null, worldPosition.getX() + 0.5 + facing.getStepX() * 0.5, worldPosition.getY() + 1.0,
                worldPosition.getZ() + 0.5 + facing.getStepZ() * 0.5, sound, SoundSource.BLOCKS, volume, pitch);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
    }
}
