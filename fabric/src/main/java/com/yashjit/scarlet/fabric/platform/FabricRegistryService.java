package com.yashjit.scarlet.fabric.platform;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.platform.RegistryService;
import java.util.function.Supplier;
import net.minecraft.core.Registry;

public final class FabricRegistryService implements RegistryService {

    @Override
    public <T, V extends T> Supplier<V> register(Registry<T> registry, String name, Supplier<V> factory) {
        V value = Registry.register(registry, Scarlet.id(name), factory.get());
        return () -> value;
    }
}
