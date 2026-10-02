package com.yashjit.scarlet.neoforge.platform;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.platform.RegistryService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class NeoForgeRegistryService implements RegistryService {

    private final Map<ResourceKey<? extends Registry<?>>, DeferredRegister<?>> registers = new LinkedHashMap<>();
    private boolean attached;

    @Override
    public <T, V extends T> Supplier<V> register(Registry<T> registry, String name, Supplier<V> factory) {
        if (attached) {
            throw new IllegalStateException("Registration of " + name + " came after the registers were attached to the mod bus");
        }
        return deferred(registry).register(name, factory);
    }

    @SuppressWarnings("unchecked")
    private <T> DeferredRegister<T> deferred(Registry<T> registry) {
        return (DeferredRegister<T>) registers.computeIfAbsent(registry.key(), key -> DeferredRegister.create(registry.key(), Scarlet.MOD_ID));
    }

    public void attach(IEventBus modBus) {
        attached = true;
        registers.values().forEach(register -> register.register(modBus));
    }
}
