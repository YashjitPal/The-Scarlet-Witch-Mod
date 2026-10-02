package com.yashjit.scarlet.hex.town;

import com.yashjit.scarlet.hex.town.TownPlan.Part;
import com.yashjit.scarlet.network.TownBuildPayload;
import com.yashjit.scarlet.platform.Services;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Tells everyone nearby when a part of the town begins building itself, so their clients can draw it.
 */
final class TownEvents {

    private static final double REACH = 160.0;

    private TownEvents() {
    }

    static void started(ServerLevel level, Part part, int ground, long now, int duration) {
        TownBuildPayload payload = new TownBuildPayload(part.kind().ordinal(), part.minX(), part.minZ(), part.maxX(), part.maxZ(), ground,
                part.kind().height(), now, duration);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(part.centerX(), ground, part.centerZ()) < REACH * REACH) {
                Services.NETWORK.sendToPlayer(player, payload);
            }
        }
        if (!part.kind().isStreet()) {
            level.playSound(null, part.centerX(), ground + 1, part.centerZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.6F, 0.7F);
        }
    }

    static void finished(ServerLevel level, Part part) {
        if (!part.kind().isStreet()) {
            level.playSound(null, part.centerX(), part.origin().getY() + 2, part.centerZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
                    2.0F, 1.2F);
        }
    }
}
