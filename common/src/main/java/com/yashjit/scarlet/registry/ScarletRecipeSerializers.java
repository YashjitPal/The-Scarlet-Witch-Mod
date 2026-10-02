package com.yashjit.scarlet.registry;

import com.yashjit.scarlet.crown.CrownSwapRecipe;
import com.yashjit.scarlet.platform.Services;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;

public final class ScarletRecipeSerializers {

    public static final Supplier<RecipeSerializer<CrownSwapRecipe>> CROWN_SWAP =
            Services.REGISTRY.register(BuiltInRegistries.RECIPE_SERIALIZER, "crown_swap", () -> CrownSwapRecipe.SERIALIZER);

    private ScarletRecipeSerializers() {
    }

    public static void bootstrap() {
    }
}
