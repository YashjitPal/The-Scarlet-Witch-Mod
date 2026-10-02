package com.yashjit.scarlet.client.platform;

import com.yashjit.scarlet.platform.Services;

/**
 * Client-only services. Kept apart from {@link Services} so dedicated servers never load client classes.
 */
public final class ClientServices {

    public static final ClientPlatform PLATFORM = Services.load(ClientPlatform.class);

    private ClientServices() {
    }
}
