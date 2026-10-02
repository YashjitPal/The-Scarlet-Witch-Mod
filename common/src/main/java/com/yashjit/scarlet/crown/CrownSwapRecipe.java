package com.yashjit.scarlet.crown;

import com.mojang.serialization.MapCodec;
import com.yashjit.scarlet.registry.ScarletItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A crown alone in the crafting grid becomes the other crown, keeping everything stored on it.
 */
public final class CrownSwapRecipe extends CustomRecipe {

    public static final CrownSwapRecipe INSTANCE = new CrownSwapRecipe();
    public static final MapCodec<CrownSwapRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, CrownSwapRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    public static final RecipeSerializer<CrownSwapRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private CrownSwapRecipe() {
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return findCrown(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack crown = findCrown(input);
        if (crown == null) {
            return ItemStack.EMPTY;
        }
        CrownStyle target = ((CrownItem) crown.getItem()).style().other();
        return crown.transmuteCopy(ScarletItems.crown(target), 1);
    }

    private static @Nullable ItemStack findCrown(CraftingInput input) {
        if (input.ingredientCount() != 1) {
            return null;
        }
        for (ItemStack stack : input.items()) {
            if (!stack.isEmpty()) {
                return stack.getItem() instanceof CrownItem ? stack : null;
            }
        }
        return null;
    }

    @Override
    public RecipeSerializer<CrownSwapRecipe> getSerializer() {
        return SERIALIZER;
    }
}
