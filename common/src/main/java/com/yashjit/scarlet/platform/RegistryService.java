package com.yashjit.scarlet.platform;

import java.util.function.Supplier;
import net.minecraft.core.Registry;

public interface RegistryService {

    /**
     * Registers an entry under the mod namespace. The returned supplier must not be queried before the loader has
     * populated the registry.
     */
    <T, V extends T> Supplier<V> register(Registry<T> registry, String name, Supplier<V> factory);
}
