package com.yashjit.scarlet.neoforge;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.client.ScarletClient;
import com.yashjit.scarlet.client.ScarletKeyMappings;
import com.yashjit.scarlet.client.config.SettingsScreen;
import com.yashjit.scarlet.neoforge.client.NeoForgeClientPlatform;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Scarlet.MOD_ID, dist = Dist.CLIENT)
public final class ScarletNeoForgeClient {

    public ScarletNeoForgeClient(IEventBus modBus, ModContainer container) {
        ScarletClient.init();
        NeoForgeClientPlatform.attach(modBus);
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));
        modBus.addListener(ScarletNeoForgeClient::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(ScarletNeoForgeClient::onClientTick);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(ScarletKeyMappings.CATEGORY);
        for (var mapping : ScarletKeyMappings.all()) {
            mapping.setKeyConflictContext(KeyConflictContext.IN_GAME);
            event.register(mapping);
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        ScarletClient.onClientTick(Minecraft.getInstance());
    }
}
