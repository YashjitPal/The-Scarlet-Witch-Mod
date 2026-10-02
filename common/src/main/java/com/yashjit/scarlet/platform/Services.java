package com.yashjit.scarlet.platform;

import java.util.ServiceLoader;

/**
 * Loads the loader-specific implementation of each platform interface. Each loader project lists its
 * implementations in {@code META-INF/services}.
 */
public final class Services {

    public static final PlatformHelper PLATFORM = load(PlatformHelper.class);
    public static final RegistryService REGISTRY = load(RegistryService.class);
    public static final NetworkService NETWORK = load(NetworkService.class);
    public static final PlayerDataService PLAYER_DATA = load(PlayerDataService.class);

    private Services() {
    }

    public static <T> T load(Class<T> type) {
        return ServiceLoader.load(type, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No implementation found for " + type.getName()));
    }
}
