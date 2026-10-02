package com.yashjit.scarlet.registry;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.entity.ChaosBolt;
import com.yashjit.scarlet.platform.Services;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ScarletEntities {

    public static final Supplier<EntityType<ChaosBolt>> CHAOS_BOLT = register("chaos_bolt",
            EntityType.Builder.<ChaosBolt>of(ChaosBolt::new, MobCategory.MISC).noLootTable().sized(0.5F, 0.5F).clientTrackingRange(6).updateInterval(10));

    private ScarletEntities() {
    }

    public static void bootstrap() {
    }

    private static <T extends Entity> Supplier<EntityType<T>> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Scarlet.id(name));
        return Services.REGISTRY.register(BuiltInRegistries.ENTITY_TYPE, name, () -> builder.build(key));
    }
}
