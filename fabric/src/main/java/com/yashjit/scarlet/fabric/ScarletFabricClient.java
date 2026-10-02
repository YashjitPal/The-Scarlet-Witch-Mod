package com.yashjit.scarlet.fabric;

import com.yashjit.scarlet.client.ScarletClient;
import com.yashjit.scarlet.client.ScarletKeyMappings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public final class ScarletFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ScarletClient.init();
        KeyMapping.Category.register(ScarletKeyMappings.CATEGORY.id());
        ScarletKeyMappings.all().forEach(KeyMappingHelper::registerKeyMapping);
        ClientTickEvents.END_CLIENT_TICK.register(ScarletClient::onClientTick);
    }
}
