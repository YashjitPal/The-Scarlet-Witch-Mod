package com.yashjit.scarlet.platform;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public interface PlatformHelper {

    String getPlatformName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    Path getConfigDir();

    /**
     * Builds a creative tab. Each loader positions mod tabs itself, so the vanilla row/column builder is not usable.
     */
    CreativeModeTab createCreativeTab(Component title, Supplier<ItemStack> icon, Supplier<List<ItemStack>> items);
}
