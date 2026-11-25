package com.yashjit.scarlet.entity;

import com.yashjit.scarlet.registry.ScarletEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class WanderingCoven {
    private WanderingCoven() {}

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 6000 != 0 || level.getDifficulty() == Difficulty.PEACEFUL
                || !level.getGameRules().get(GameRules.SPAWN_MOBS) || !level.getRandom().nextBoolean()) return;
        var players = level.players().stream().filter(p -> p.isAlive() && !p.isSpectator()).toList();
        if (players.isEmpty()) return;
        var player = players.get(level.getRandom().nextInt(players.size()));
        visit(level,player);
    }

    public static List<WanderingWitch> visit(ServerLevel level, net.minecraft.world.entity.player.Player player) {
        if (!level.getEntitiesOfClass(WanderingWitch.class, player.getBoundingBox().inflate(112)).isEmpty()) return List.of();
        for (int i=0; i<8; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            int distance = 32 + level.getRandom().nextInt(25);
            var center = player.blockPosition().offset((int)(Math.cos(angle)*distance), 0, (int)(Math.sin(angle)*distance));
            var group=spawn(level, center, 2 + level.getRandom().nextInt(4));
            if (!group.isEmpty()) return group;
        }
        return List.of();
    }

    public static List<WanderingWitch> spawn(ServerLevel level, BlockPos center, int count) {
        if (count < 2 || count > 5 || !level.hasChunksAt(center.offset(-6,0,-6), center.offset(6,0,6))) return List.of();
        List<WanderingWitch> group = new ArrayList<>();
        UUID coven = UUID.randomUUID();
        boolean gold = level.getRandom().nextBoolean();
        for (int attempt=0; attempt<42 && group.size()<count; attempt++) {
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    center.offset(level.getRandom().nextInt(11)-5, 0, level.getRandom().nextInt(11)-5));
            if (!level.getWorldBorder().isWithinBounds(pos) || !level.getFluidState(pos).isEmpty()
                    || !level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP)
                    || group.stream().anyMatch(w -> w.position().distanceToSqr(Vec3.atBottomCenterOf(pos)) < 2)) continue;
            var witch = ScarletEntities.WANDERING_WITCH.get().create(level, EntitySpawnReason.EVENT);
            if (witch == null) return List.of();
            witch.snapTo(Vec3.atBottomCenterOf(pos), level.getRandom().nextInt(360), 0);
            if (!level.noCollision(witch) || !level.isUnobstructed(witch)) continue;
            int roll = level.getRandom().nextInt(100);
            witch.join(coven, roll<55 ? 1 : roll<88 ? 2 : 3, gold, level.getGameTime()+48000);
            group.add(witch);
        }
        if (group.size()!=count) return List.of();
        for (var witch : group) if (!level.addFreshEntity(witch)) {
            group.forEach(WanderingWitch::discard);
            return List.of();
        }
        return group;
    }
}
