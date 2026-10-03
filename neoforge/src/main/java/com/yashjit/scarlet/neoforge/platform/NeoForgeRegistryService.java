package com.yashjit.scarlet.neoforge.platform;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.platform.RegistryService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class NeoForgeRegistryService implements RegistryService {

    private final Map<ResourceKey<? extends Registry<?>>, DeferredRegister<?>> registers = new LinkedHashMap<>();
    private final List<Attributes<?>> attributes = new ArrayList<>();
    private boolean attached;

    @Override
    public <T, V extends T> Supplier<V> register(Registry<T> registry, String name, Supplier<V> factory) {
        if (attached) {
            throw new IllegalStateException("Registration of " + name + " came after the registers were attached to the mod bus");
        }
        return deferred(registry).register(name, factory);
    }

    @Override
    public <E extends LivingEntity> void registerAttributes(Supplier<EntityType<E>> type, Supplier<AttributeSupplier.Builder> attributes) {
        this.attributes.add(new Attributes<>(type, attributes));
    }

    @SuppressWarnings("unchecked")
    private <T> DeferredRegister<T> deferred(Registry<T> registry) {
        return (DeferredRegister<T>) registers.computeIfAbsent(registry.key(), key -> DeferredRegister.create(registry.key(), Scarlet.MOD_ID));
    }

    public void attach(IEventBus modBus) {
        attached = true;
        registers.values().forEach(register -> register.register(modBus));
        modBus.addListener((EntityAttributeCreationEvent event) -> attributes.forEach(entry -> event.put(entry.type().get(), entry.attributes().get().build())));
    }

    private record Attributes<E extends LivingEntity>(Supplier<EntityType<E>> type, Supplier<AttributeSupplier.Builder> attributes) {
    }
}
