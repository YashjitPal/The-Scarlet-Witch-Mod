package com.yashjit.scarlet.fabric;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.event.ScarletEvents;
import com.yashjit.scarlet.fabric.platform.FabricPlayerDataService;
import com.yashjit.scarlet.network.ScarletNetwork;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class ScarletFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        Scarlet.init();
        FabricPlayerDataService.bootstrap();
        ScarletNetwork.registerPayloads(new FabricPayloadRegistry());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ScarletEvents.onServerTick(server);
            server.getPlayerList().getPlayers().forEach(ScarletEvents::onPlayerTick);
        });
        ServerEntityEvents.EQUIPMENT_CHANGE.register(ScarletEvents::onEquipmentChange);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(ScarletEvents::allowDamage);
    }
}
