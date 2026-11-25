package com.yashjit.scarlet.magic;

import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.registry.ScarletDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A field discovery teaches one witchcraft spell early, on the crown being worn. */
public final class GrimoireLeaf extends Item {
    public static final java.util.List<Spell> COVEN_SPELLS = java.util.List.of(Spell.STARFIRE, Spell.ARC_BEAM, Spell.RUNE_TRAP);
    private final Spell spell;
    public GrimoireLeaf(Properties properties, Spell spell) { super(properties); this.spell = spell; }

    public static boolean learned(Player player, Spell spell) {
        boolean agatha = com.yashjit.scarlet.player.AgathaForm.worn(player);
        return (agatha || CrownItem.isWiccan(player) || COVEN_SPELLS.contains(spell) && CrownItem.hasScarletMagic(player)) && ((agatha ? player.getOffhandItem() : player.getItemBySlot(EquipmentSlot.HEAD))
                .getOrDefault(ScarletDataComponents.GRIMOIRE.get(), 0) & 1 << spell.ordinal()) != 0;
    }

    @Override
    public InteractionResult use(Level level, Player user, InteractionHand hand) {
        boolean agatha = com.yashjit.scarlet.player.AgathaForm.worn(user);
        if (!agatha && !CrownItem.isWiccan(user) && !(COVEN_SPELLS.contains(spell) && CrownItem.hasScarletMagic(user))) return InteractionResult.PASS;
        if (user instanceof ServerPlayer p) {
            if (learned(p, spell)) { WiccanMagic.tip(p, "known"); return InteractionResult.FAIL; }
            ItemStack crown = agatha ? user.getOffhandItem() : user.getItemBySlot(EquipmentSlot.HEAD);
            crown.set(ScarletDataComponents.GRIMOIRE.get(), crown.getOrDefault(ScarletDataComponents.GRIMOIRE.get(), 0) | 1 << spell.ordinal());
            if (!COVEN_SPELLS.contains(spell)) Mastery.grant(p, 120);
            p.sendOverlayMessage(Component.translatable("wiccan.studied", spell.displayName()).withColor(0xB2E8FF));
            user.getItemInHand(hand).consume(1, user);
        }
        return InteractionResult.SUCCESS;
    }
}
