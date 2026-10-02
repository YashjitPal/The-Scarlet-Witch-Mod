package com.yashjit.scarlet.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public interface NetworkService {

    /**
     * Client only.
     */
    void sendToServer(CustomPacketPayload payload);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /**
     * Sends to every player tracking the entity, plus the entity itself when it is a player.
     */
    void sendToTrackingAndSelf(Entity entity, CustomPacketPayload payload);
}
