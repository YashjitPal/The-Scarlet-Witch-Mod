package com.yashjit.scarlet;

import com.yashjit.scarlet.platform.Services;
import com.yashjit.scarlet.registry.ScarletCreativeTabs;
import com.yashjit.scarlet.registry.ScarletDataComponents;
import com.yashjit.scarlet.registry.ScarletEntities;
import com.yashjit.scarlet.registry.ScarletItems;
import com.yashjit.scarlet.registry.ScarletRecipeSerializers;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Scarlet {

    public static final String MOD_ID = "scarlet";
    public static final Logger LOG = LoggerFactory.getLogger("Scarlet");

    private Scarlet() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /**
     * Declares every registry entry. Must run before the loader opens its registries for mods.
     */
    public static void init() {
        ScarletDataComponents.bootstrap();
        ScarletItems.bootstrap();
        ScarletEntities.bootstrap();
        ScarletRecipeSerializers.bootstrap();
        ScarletCreativeTabs.bootstrap();
        LOG.info("Scarlet is awakening on {}", Services.PLATFORM.getPlatformName());
    }
}
