package com.yashjit.scarlet.decor;

import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.hex.Hex;
import com.yashjit.scarlet.hex.HexData;
import com.yashjit.scarlet.hex.HexShape;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.magic.Magic;
import com.yashjit.scarlet.magic.Mastery;
import com.yashjit.scarlet.magic.Spell;
import com.yashjit.scarlet.registry.ScarletBlocks;
import com.yashjit.scarlet.registry.ScarletItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Set dressing: a caster standing in their own Hex can take any of its era decorations, or a car, out of thin air, for
 * a little of their chaos energy each. Their inventory shows them all beside it while they stand there.
 */
public final class Props {

    /** Chaos energy a decoration takes to make, and a car. */
    public static final float DECOR_COST = 4.0F;
    public static final float CAR_COST = 15.0F;

    private Props() {
    }

    /**
     * Everything there is to take, in the order the inventory shows it: the decorations as the creative tab has them,
     * then the car.
     */
    public static List<Item> items() {
        List<Item> items = new ArrayList<>();
        for (Supplier<Item> decor : ScarletBlocks.DECOR_ITEMS) {
            items.add(decor.get());
        }
        items.add(ScarletItems.PARKED_CAR.get());
        return items;
    }

    public static float cost(Item item) {
        return item == ScarletItems.PARKED_CAR.get() ? CAR_COST : DECOR_COST;
    }

    /**
     * Whether someone could take something now, given whether they stand in their own Hex: they wear a crown that has
     * mastered the Hex.
     */
    public static boolean crowned(Player player) {
        return CrownItem.isWearingCrown(player) && Mastery.rank(player) >= Spell.HEX.rank();
    }

    /**
     * Whether a player stands in their own Hex, wearing a crown that has mastered it, while it stands.
     */
    public static boolean canDress(ServerPlayer player) {
        if (!crowned(player)) {
            return false;
        }
        Hex hex = HexData.of(player.level()).byCaster(player.getUUID());
        return hex != null && hex.phase() != Hex.Phase.COLLAPSING
                && HexShape.contains(hex.center(), Hexes.wallRadius(hex, player.level().getGameTime()), player.position());
    }

    /**
     * Makes the thing asked for, into the caster's inventory or at their feet if it is full, for its chaos energy; one
     * who can't spare it hears the magic fizzle out.
     */
    public static void conjure(ServerPlayer player, int index) {
        List<Item> items = items();
        if (index < 0 || index >= items.size() || !canDress(player)) {
            return;
        }
        ServerLevel level = player.level();
        long now = level.getGameTime();
        Item item = items.get(index);
        float cost = cost(item);
        if (!player.isCreative()) {
            if (Magic.energy(player, now) < cost) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.35F, 1.7F);
                return;
            }
            Magic.spend(player, cost, now);
        }
        player.getInventory().placeItemBackInInventory(new ItemStack(item), Prediction.SERVER_ONLY);
        player.containerMenu.broadcastChanges();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.3F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.25F, 1.8F);
        level.sendParticles(new DustParticleOptions(ScarletPalette.SCARLET, 0.9F), player.getX(), player.getY() + 1.0, player.getZ(), 14, 0.35,
                0.45, 0.35, 0.0);
    }
}
