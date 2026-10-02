package com.yashjit.scarlet.fabric.platform;

import com.yashjit.scarlet.platform.PlatformHelper;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class FabricPlatformHelper implements PlatformHelper {

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public CreativeModeTab createCreativeTab(Component title, Supplier<ItemStack> icon, Supplier<List<ItemStack>> items) {
        return FabricCreativeModeTab.builder()
                .title(title)
                .icon(icon)
                .displayItems((parameters, output) -> items.get().forEach(output::accept))
                .build();
    }
}
