package com.yashjit.scarlet.registry;

import com.yashjit.scarlet.decor.RefrigeratorBlockEntity;
import com.yashjit.scarlet.platform.Services;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ScarletBlockEntities {

    public static final Supplier<BlockEntityType<RefrigeratorBlockEntity>> REFRIGERATOR = Services.REGISTRY.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, "refrigerator",
            () -> new BlockEntityType<>(RefrigeratorBlockEntity::new, Set.of(ScarletBlocks.REFRIGERATOR.get())));

    private ScarletBlockEntities() {
    }

    public static void bootstrap() {
    }
}
