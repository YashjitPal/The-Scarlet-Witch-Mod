package com.yashjit.scarlet.client.hex;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.client.costume.CostumeView;
import com.yashjit.scarlet.client.fx.ScarletFx;
import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.hex.HexSnapshot;
import com.yashjit.scarlet.hex.Hexes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Era clothes for everyone inside a Hex, like Wanda and Vision each episode. Each person always gets the same outfit
 * for an era, picked from its wardrobe by their UUID; the caster's own costume takes priority. Stepping in or out,
 * the change flickers through a burst of TV static.
 */
public final class Outfits {

    /** Outfits in each era's wardrobe. */
    public static final int WARDROBE = 4;
    private static final int FLICKER_TICKS = 10;

    private static final Map<UUID, Crossing> CROSSINGS = new HashMap<>();
    private static @Nullable ClientLevel seenLevel;

    private Outfits() {
    }

    /**
     * The outfit a player is wearing at this moment, or null for their own clothes.
     */
    public static @Nullable Identifier outfit(Player player, boolean slim, float partialTick) {
        if (player.isSpectator() || CostumeView.of(player, partialTick) != null) {
            return null;
        }
        Crossing crossing = CROSSINGS.get(player.getUUID());
        if (crossing == null) {
            return null;
        }
        boolean wearing = crossing.era != null;
        if (seenLevel != null) {
            long age = seenLevel.getGameTime() - crossing.changedAt;
            if (age >= 0 && age < FLICKER_TICKS - 2 && (age / 2) % 2 == 0) {
                // mid-change, flicking between both
                wearing = !wearing;
            }
        }
        Era era = crossing.era != null ? crossing.era : crossing.previous;
        if (!wearing || era == null) {
            return null;
        }
        int pick = Math.floorMod(player.getUUID().hashCode(), WARDROBE);
        return Scarlet.id("textures/entity/outfit/" + ResidentsClient.name(era) + "_" + pick + (slim ? "_slim" : "") + ".png");
    }

    /**
     * Whether a player's armor is hidden under an outfit.
     */
    public static boolean wearing(Player player, float partialTick) {
        return outfit(player, false, partialTick) != null;
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level != seenLevel) {
            seenLevel = level;
            CROSSINGS.clear();
        }
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        RandomSource random = ScarletFx.random();
        for (Player player : level.players()) {
            HexSnapshot hex = Hexes.clientHexAt(player.position(), now);
            Era era = hex != null ? hex.eraValue() : null;
            Crossing crossing = CROSSINGS.get(player.getUUID());
            if (crossing == null) {
                CROSSINGS.put(player.getUUID(), new Crossing(era, null, Long.MIN_VALUE / 4));
                continue;
            }
            if (crossing.era != era) {
                CROSSINGS.put(player.getUUID(), new Crossing(era, crossing.era, now));
            } else if (now - crossing.changedAt < FLICKER_TICKS && !minecraft.isPaused()) {
                for (int i = 0; i < Math.round(3 * ScarletFx.density()); i++) {
                    Vec3 at = player.position().add((random.nextFloat() - 0.5F) * 0.8F, random.nextFloat() * 1.8F, (random.nextFloat() - 0.5F) * 0.8F);
                    int grey = 0x9A9A9A + random.nextInt(0x40) * 0x010101;
                    ScarletFx.spark(at, new Vec3(0, 0.004, 0), 3 + random.nextInt(4), 0.03F, 0xFFFFFF, grey, 0.0F, 0.8F);
                }
            }
        }
        CROSSINGS.keySet().removeIf(id -> level.getPlayerByUUID(id) == null);
    }

    /**
     * @param era      the era of the Hex they're in, or null outside
     * @param previous the era they were in before, for the flicker on the way out
     */
    private record Crossing(@Nullable Era era, @Nullable Era previous, long changedAt) {
    }
}
