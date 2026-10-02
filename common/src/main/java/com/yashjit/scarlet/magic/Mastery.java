package com.yashjit.scarlet.magic;

import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.registry.ScarletDataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Mastery grows with use and lives on the crown. Ranks unlock spells and raise energy.
 */
public final class Mastery {

    /**
     * Total experience needed for each rank, starting at rank 1.
     */
    private static final int[] RANK_XP = {0, 120, 360, 720, 1200, 1800, 2600, 3600, 4800, 6400};

    public static final int MAX_RANK = RANK_XP.length;

    private Mastery() {
    }

    public static int xp(ItemStack crown) {
        return crown.getOrDefault(ScarletDataComponents.MASTERY.get(), 0);
    }

    public static int rankForXp(int xp) {
        int rank = 1;
        while (rank < MAX_RANK && xp >= RANK_XP[rank]) {
            rank++;
        }
        return rank;
    }

    /**
     * How far through the current rank, 0 to 1. Always 1 at the top rank.
     */
    public static float progress(int xp) {
        int rank = rankForXp(xp);
        if (rank >= MAX_RANK) {
            return 1.0F;
        }
        int from = RANK_XP[rank - 1];
        return (xp - from) / (float) (RANK_XP[rank] - from);
    }

    /**
     * The rank of the crown the entity wears, or 0 without one.
     */
    public static int rank(LivingEntity entity) {
        ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
        return head.getItem() instanceof CrownItem ? rankForXp(xp(head)) : 0;
    }

    public static void grant(LivingEntity entity, int amount) {
        ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (head.getItem() instanceof CrownItem) {
            head.set(ScarletDataComponents.MASTERY.get(), xp(head) + amount);
        }
    }

    public static float maxEnergy(int rank) {
        return 100.0F + Math.max(0, rank - 1) * 10.0F;
    }

    public static String numeral(int rank) {
        return switch (rank) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> Integer.toString(rank);
        };
    }
}
