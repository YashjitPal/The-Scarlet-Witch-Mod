package com.yashjit.scarlet.decor;

import com.yashjit.scarlet.registry.ScarletBlockEntities;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * What a range has cooking on its four burners: whatever a campfire would cook, a dish at a time on each, done in a
 * third of the time a campfire takes while the range is on, and popping off the burner done. Switched off, it cools.
 */
public class StoveBlockEntity extends BlockEntity {

    public static final int BURNERS = 4;
    /** How much faster than a campfire it cooks. */
    private static final int HOTTER = 3;

    private final NonNullList<ItemStack> items = NonNullList.withSize(BURNERS, ItemStack.EMPTY);
    private final int[] cooked = new int[BURNERS];
    private final int[] cookTime = new int[BURNERS];
    private final RecipeManager.CachedCheck<SingleRecipeInput, CampfireCookingRecipe> recipes = RecipeManager.createCheck(RecipeType.CAMPFIRE_COOKING);

    public StoveBlockEntity(BlockPos pos, BlockState state) {
        super(ScarletBlockEntities.STOVE.get(), pos, state);
    }

    public NonNullList<ItemStack> items() {
        return items;
    }

    /**
     * Puts a dish down on a free burner, if there is one and it is something to cook.
     *
     * @return whether it was put down
     */
    public boolean put(ServerLevel level, ItemStack stack) {
        Optional<RecipeHolder<CampfireCookingRecipe>> recipe = recipes.getRecipeFor(new SingleRecipeInput(stack), level);
        if (recipe.isEmpty()) {
            return false;
        }
        for (int k = 0; k < BURNERS; k++) {
            if (items.get(k).isEmpty()) {
                items.set(k, stack.copyWithCount(1));
                cooked[k] = 0;
                cookTime[k] = Math.max(1, recipe.get().value().cookingTime() / HOTTER);
                changed(level);
                return true;
            }
        }
        return false;
    }

    /** Takes back what is on the burners, cooked or not, into whoever reaches for it. */
    public NonNullList<ItemStack> takeAll(ServerLevel level) {
        NonNullList<ItemStack> taken = NonNullList.create();
        for (int k = 0; k < BURNERS; k++) {
            if (!items.get(k).isEmpty()) {
                taken.add(items.get(k));
                items.set(k, ItemStack.EMPTY);
                cooked[k] = 0;
            }
        }
        if (!taken.isEmpty()) {
            changed(level);
        }
        return taken;
    }

    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    /** A tick of cooking, while the range is on: what is done pops off its burner. */
    public static void cookTick(ServerLevel level, BlockPos pos, BlockState state, StoveBlockEntity stove) {
        boolean on = state.getValue(SwitchedDecorBlock.LIT);
        boolean changed = false;
        for (int k = 0; k < BURNERS; k++) {
            ItemStack stack = stove.items.get(k);
            if (stack.isEmpty()) {
                continue;
            }
            if (!on) {
                // cooling down
                stove.cooked[k] = Math.max(0, stove.cooked[k] - 2);
                continue;
            }
            if (++stove.cooked[k] < stove.cookTime[k]) {
                if ((level.getGameTime() + k * 13) % 40 == 0) {
                    level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.12F, 1.6F + level.getRandom().nextFloat() * 0.3F);
                }
                continue;
            }
            SingleRecipeInput input = new SingleRecipeInput(stack);
            ItemStack done = stove.recipes.getRecipeFor(input, level).map(recipe -> recipe.value().assemble(input)).orElse(stack);
            stove.items.set(k, ItemStack.EMPTY);
            stove.cooked[k] = 0;
            Vec3 at = burner(state, k).add(pos.getX(), pos.getY(), pos.getZ());
            ItemEntity popped = new ItemEntity(level, at.x, at.y + 0.1, at.z, done, 0.0, 0.18, 0.0);
            popped.setDefaultPickUpDelay();
            level.addFreshEntity(popped);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3F, 1.9F);
            changed = true;
        }
        if (changed) {
            stove.changed(level);
        }
    }

    /** Steam and smoke off whatever is cooking, while the range is on. */
    public static void particleTick(Level level, BlockPos pos, BlockState state, StoveBlockEntity stove) {
        if (!state.getValue(SwitchedDecorBlock.LIT)) {
            return;
        }
        RandomSource random = level.getRandom();
        for (int k = 0; k < BURNERS; k++) {
            if (!stove.items.get(k).isEmpty() && random.nextFloat() < 0.12F) {
                Vec3 at = burner(state, k).add(pos.getX(), pos.getY(), pos.getZ());
                level.addParticle(random.nextBoolean() ? ParticleTypes.SMOKE : ParticleTypes.WHITE_SMOKE, at.x + random.nextGaussian() * 0.04,
                        at.y + 0.05, at.z + random.nextGaussian() * 0.04, 0.0, 0.03, 0.0);
            }
        }
    }

    /**
     * Where the middle of a burner's top is, in the block, the range turned the way it faces: its burners laid out
     * across the top of the model, which faces north.
     */
    public static Vec3 burner(BlockState state, int k) {
        float[][] middles = {{4.25F, 5.75F}, {11.75F, 5.75F}, {4.25F, 11.25F}, {11.75F, 11.25F}};
        double x = middles[k][0] / 16.0 - 0.5;
        double z = middles[k][1] / 16.0 - 0.5;
        double turn = Math.toRadians(state.getValue(DecorBlock.FACING).toYRot() + 180.0);
        double cos = Math.cos(turn);
        double sin = Math.sin(turn);
        // on a coil, on the glass of a smooth top, or on today's gas grates
        int era = state.getValue(EraDecor.ERA).ordinal();
        double top = era == 5 ? 16.55 / 16.0 : era == 4 ? 16.05 / 16.0 : 15.8 / 16.0;
        return new Vec3(0.5 + x * cos - z * sin, top, 0.5 + x * sin + z * cos);
    }

    private void changed(ServerLevel level) {
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            Containers.dropContents(level, pos, items);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
        int[] progress = input.getIntArray("cooked").orElse(new int[BURNERS]);
        int[] times = input.getIntArray("cook_time").orElse(new int[BURNERS]);
        for (int k = 0; k < BURNERS; k++) {
            cooked[k] = k < progress.length ? progress[k] : 0;
            cookTime[k] = k < times.length ? times[k] : 0;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items, true);
        output.putIntArray("cooked", cooked);
        output.putIntArray("cook_time", cookTime);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
}
