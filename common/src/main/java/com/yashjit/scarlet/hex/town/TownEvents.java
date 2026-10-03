package com.yashjit.scarlet.hex.town;

import com.yashjit.scarlet.hex.HexShape;
import com.yashjit.scarlet.hex.town.TownPlan.Part;
import com.yashjit.scarlet.network.TownBuildPayload;
import com.yashjit.scarlet.network.TownCutPayload;
import com.yashjit.scarlet.network.TownFormPayload;
import com.yashjit.scarlet.platform.Services;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Tells everyone nearby when a part of the town begins building itself, and where bits of it go back as the wall
 * passes over them, so their clients can draw it.
 */
final class TownEvents {

    private static final double REACH = 160.0;

    private TownEvents() {
    }

    /**
     * @param clearTop   the height of the highest block of what stood there before
     * @param clearTicks how long that takes to dissolve, before the part builds; 0 if the land was open
     */
    static void started(ServerLevel level, Part part, int ground, long now, int duration, int clearTop, int clearTicks) {
        TownBuildPayload payload = new TownBuildPayload(part.kind().ordinal(), part.minX(), part.minZ(), part.maxX(), part.maxZ(), ground,
                part.kind().height(), now, duration, clearTop, clearTicks, part.front().get2DDataValue(),
                part.kind() == TownPlan.Kind.HOME && part.layer() == 0);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(part.centerX(), ground, part.centerZ()) < REACH * REACH) {
                Services.NETWORK.sendToPlayer(player, payload);
            }
        }
        if (clearTicks > 0) {
            // what stood here comes apart: a hollow rush of air and the crackle of a picture losing its signal
            level.playSound(null, part.centerX(), ground + 2, part.centerZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.BLOCKS, 0.9F,
                    0.5F);
            level.playSound(null, part.centerX(), ground + 2, part.centerZ(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 0.7F,
                    1.4F);
        }
        if (!part.kind().isStreet()) {
            level.playSound(null, part.centerX(), ground + 1, part.centerZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.6F, 0.7F);
        }
    }

    /**
     * Bits of the town just went back to what stood there before, as the wall passed over them: everyone near enough to
     * see it is told where, to show them glitching out.
     */
    static void gone(ServerLevel level, Vec3 hexCenter, float wall, int[] cells) {
        if (cells.length == 0) {
            return;
        }
        TownCutPayload payload = new TownCutPayload(cells);
        double reach = HexShape.reach(wall) + REACH;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - hexCenter.x;
            double dz = player.getZ() - hexCenter.z;
            if (dx * dx + dz * dz < reach * reach) {
                Services.NETWORK.sendToPlayer(player, payload);
            }
        }
    }

    /**
     * Blocks of the caster's home about to land: everyone near enough to see it is told where, and when.
     */
    static void forming(ServerLevel level, Part part, int ground, int[] blocks) {
        if (blocks.length == 0) {
            return;
        }
        TownFormPayload payload = new TownFormPayload(blocks);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(part.centerX(), ground, part.centerZ()) < REACH * REACH) {
                Services.NETWORK.sendToPlayer(player, payload);
            }
        }
    }

    static void finished(ServerLevel level, Part part) {
        if (!part.kind().isStreet()) {
            level.playSound(null, part.centerX(), part.origin().getY() + 2, part.centerZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
                    2.0F, 1.2F);
        }
    }
}
