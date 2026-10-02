package com.yashjit.scarlet.client.magic;

import com.yashjit.scarlet.client.fx.ScarletFx;
import com.yashjit.scarlet.magic.SpellCasts;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Where each player's palms were when they were last drawn, so effects can leave the real hands of the animated
 * model. Falls back to an estimate from the view while a player is not drawn, such as yourself in first person.
 */
public final class Hands {

    private static final Int2ObjectMap<Recorded> RECORDED = new Int2ObjectOpenHashMap<>();

    private Hands() {
    }

    static void record(Player player, Vec3 right, Vec3 left) {
        RECORDED.put(player.getId(), new Recorded(right, left, player.level().getGameTime()));
    }

    public static Vec3 palm(Player player, HumanoidArm arm) {
        Recorded recorded = RECORDED.get(player.getId());
        if (recorded != null && !ScarletFx.isFirstPersonViewOf(player) && player.level().getGameTime() - recorded.gameTime() <= 2) {
            return arm == HumanoidArm.RIGHT ? recorded.right() : recorded.left();
        }
        return SpellCasts.handPosition(player, arm != player.getMainArm());
    }

    public static void prune(ClientLevel level) {
        RECORDED.int2ObjectEntrySet().removeIf(entry -> level.getEntity(entry.getIntKey()) == null);
    }

    private record Recorded(Vec3 right, Vec3 left, long gameTime) {
    }
}
