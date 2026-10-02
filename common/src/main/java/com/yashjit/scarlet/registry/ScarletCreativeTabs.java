package com.yashjit.scarlet.registry;

import com.yashjit.scarlet.platform.Services;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ScarletCreativeTabs {

    public static final Supplier<CreativeModeTab> MAIN = Services.REGISTRY.register(BuiltInRegistries.CREATIVE_MODE_TAB, "main",
            () -> Services.PLATFORM.createCreativeTab(
                    Component.translatable("itemGroup.scarlet"),
                    () -> new ItemStack(ScarletItems.WITCH_TIARA.get()),
                    () -> List.of(
                            new ItemStack(ScarletItems.WITCH_TIARA.get()),
                            new ItemStack(ScarletItems.WARLOCK_CROWN.get()))));

    private ScarletCreativeTabs() {
    }

    public static void bootstrap() {
    }
}
