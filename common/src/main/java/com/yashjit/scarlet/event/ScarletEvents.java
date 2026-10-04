package com.yashjit.scarlet.event;

import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.darkhold.Darkhold;
import com.yashjit.scarlet.darkhold.Dreamwalk;
import com.yashjit.scarlet.hex.HexEjection;
import com.yashjit.scarlet.hex.HexMending;
import com.yashjit.scarlet.hex.HexRipples;
import com.yashjit.scarlet.hex.HexTape;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.hex.Residents;
import com.yashjit.scarlet.magic.Magic;
import com.yashjit.scarlet.magic.MindControl;
import com.yashjit.scarlet.magic.RuneTraps;
import com.yashjit.scarlet.magic.Telekinesis;
import com.yashjit.scarlet.platform.Services;
import com.yashjit.scarlet.player.SuitUp;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
        if (entity instanceof Mob mob) {
            Residents.struck(mob, source.getEntity());
        }
        if (!(entity instanceof ServerPlayer player)) {
            return true;
        }
        if (Dreamwalk.isAway(player)) {
            // a spirit has nothing to hurt, though the command that kills anyone still kills it
            return source.is(DamageTypes.GENERIC_KILL);
        }
        if (source.is(DamageTypes.IN_WALL) && Hexes.isFounding(player)) {
            // carried in through the walls of what their home is made of
            return false;
        }
        if (Magic.shieldBlocks(player, source, amount)) {
            return false;
        }
        if (amount > 0.0F && !player.isInvulnerableTo(player.level(), source)) {
            Hexes.hurt(player, amount);
            MindControl.hurt(player);
            Dreamwalk.hurt(player);
        }
        return true;
    }

    public static void onServerTick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            // what the Hex does to the world itself is no part of the scene its tape records
            HexTape.offTape(() -> {
                Hexes.tick(level);
                HexMending.tick(level);
            });
            HexEjection.tick(level);
            RuneTraps.tick(level);
        }
        Telekinesis.sweep(server);
        MindControl.sweep(server);
    }

    public static void onPlayerTick(ServerPlayer player) {
        // Equipment events miss some inventory paths, so re-check once a second.
        if (player.tickCount % 20 == 0 && Services.PLAYER_DATA.get(player).suited() && !CrownItem.isWearingCrown(player)) {
            SuitUp.setSuited(player, false);
        }
        Magic.tick(player);
        Dreamwalk.tick(player);
        Darkhold.tick(player);
        Hexes.syncIfNeeded(player);
        Residents.syncIfNeeded(player);
        HexRipples.watch(player);
    }
}
