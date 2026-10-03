package com.yashjit.scarlet.fabric.platform;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.platform.RegistryService;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

public final class FabricRegistryService implements RegistryService {

    @Override
    public <T, V extends T> Supplier<V> register(Registry<T> registry, String name, Supplier<V> factory) {
        V value = Registry.register(registry, Scarlet.id(name), factory.get());
        return () -> value;
    }

    @Override
    public <E extends LivingEntity> void registerAttributes(Supplier<EntityType<E>> type, Supplier<AttributeSupplier.Builder> attributes) {
        FabricDefaultAttributeRegistry.register(type.get(), attributes.get());
    }
}
