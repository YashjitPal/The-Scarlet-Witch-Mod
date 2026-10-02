package com.yashjit.scarlet.event;

import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.magic.Magic;
import com.yashjit.scarlet.platform.Services;
import com.yashjit.scarlet.player.SuitUp;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side game hooks. Each loader forwards its own events here.
 */
public final class ScarletEvents {

    private ScarletEvents() {
    }

    public static void onEquipmentChange(LivingEntity entity, EquipmentSlot slot, ItemStack from, ItemStack to) {
        if (slot == EquipmentSlot.HEAD && entity instanceof ServerPlayer player && !(to.getItem() instanceof CrownItem)) {
            SuitUp.setSuited(player, false);
        }
    }

    /**
     * @return false to cancel the damage
     */
    public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        return !(entity instanceof ServerPlayer player) || !Magic.shieldBlocks(player, source, amount);
    }

    public static void onServerTick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            Hexes.tick(level);
        }
    }

    public static void onPlayerTick(ServerPlayer player) {
        // Equipment events miss some inventory paths, so re-check once a second.
        if (player.tickCount % 20 == 0 && Services.PLAYER_DATA.get(player).suited() && !CrownItem.isWearingCrown(player)) {
            SuitUp.setSuited(player, false);
        }
        Magic.tick(player);
        Hexes.syncIfNeeded(player);
    }
}
