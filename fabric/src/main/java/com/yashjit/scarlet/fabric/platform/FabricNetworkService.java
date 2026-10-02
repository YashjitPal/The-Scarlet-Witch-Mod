package com.yashjit.scarlet.fabric.platform;

import com.yashjit.scarlet.platform.NetworkService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class FabricNetworkService implements NetworkService {

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToTrackingAndSelf(Entity entity, CustomPacketPayload payload) {
        for (ServerPlayer tracking : PlayerLookup.tracking(entity)) {
            if (tracking != entity) {
                ServerPlayNetworking.send(tracking, payload);
            }
        }
        if (entity instanceof ServerPlayer self) {
            ServerPlayNetworking.send(self, payload);
        }
    }
}
