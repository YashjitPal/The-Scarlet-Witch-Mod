package com.yashjit.scarlet.platform;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

public interface RegistryService {

    /**
     * Registers an entry under the mod namespace. The returned supplier must not be queried before the loader has
     * populated the registry.
     */
    <T, V extends T> Supplier<V> register(Registry<T> registry, String name, Supplier<V> factory);

    /**
     * Gives a living entity type the attributes every one of it is made with, which it cannot be made without.
     */
    <E extends LivingEntity> void registerAttributes(Supplier<EntityType<E>> type, Supplier<AttributeSupplier.Builder> attributes);
}
