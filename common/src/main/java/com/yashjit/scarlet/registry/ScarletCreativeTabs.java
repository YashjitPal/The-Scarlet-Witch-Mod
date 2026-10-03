package com.yashjit.scarlet.registry;

import com.yashjit.scarlet.platform.Services;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ScarletCreativeTabs {

    public static final Supplier<CreativeModeTab> MAIN = Services.REGISTRY.register(BuiltInRegistries.CREATIVE_MODE_TAB, "main",
            () -> Services.PLATFORM.createCreativeTab(
                    Component.translatable("itemGroup.scarlet"),
                    () -> new ItemStack(ScarletItems.WITCH_TIARA.get()),
                    () -> {
                        List<ItemStack> stacks = new ArrayList<>();
                        stacks.add(new ItemStack(ScarletItems.WITCH_TIARA.get()));
                        stacks.add(new ItemStack(ScarletItems.WARLOCK_CROWN.get()));
                        for (Supplier<Item> decor : ScarletBlocks.DECOR_ITEMS) {
                            stacks.add(new ItemStack(decor.get()));
                        }
                        stacks.add(new ItemStack(ScarletItems.PARKED_CAR.get()));
                        return stacks;
                    }));

    private ScarletCreativeTabs() {
    }

    public static void bootstrap() {
    }
}
