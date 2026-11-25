package com.yashjit.scarlet.magic;

import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.darkhold.Darkhold;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Player;

public final class WiccanSpells {
    private static final List<Spell> BASE = List.of(Spell.STARFIRE, Spell.ARC_BEAM, Spell.SHOCKWAVE, Spell.TELEKINESIS,
            Spell.WEAVE, Spell.WAYGATE, Spell.SECOND_SIGHT, Spell.ASTRAL, Spell.DEMIURGE);
    private static final List<Spell> CRAFT = List.of(Spell.AEGIS, Spell.GLAMOUR, Spell.TRANSMUTE, Spell.MIND_CONTROL, Spell.DREAMBIND);
    private static final List<Spell> DIVINE = List.of(Spell.FATE, Spell.RUNIC_DOMAIN, Spell.WITCHES_ROAD);

    private WiccanSpells() {}

    public static boolean ascended(Player player) {
        return CrownItem.isWiccan(player) && Magic.state(player).demiurgeUntil() > player.level().getGameTime();
    }

    public static List<Spell> wheel(Player player, boolean craft) {
        var slots = new ArrayList<>(craft ? CRAFT : BASE);
        if (craft && GrimoireLeaf.learned(player, Spell.RUNE_TRAP)) slots.add(Spell.RUNE_TRAP);
        if (craft && Darkhold.carries(player) && hasReadDarkhold(player)) slots.add(Spell.DREAMWALK);
        if (!craft && ascended(player)) slots.addAll(DIVINE);
        return slots;
    }

    public static boolean permits(Player player, Spell spell) {
        if (GrimoireLeaf.COVEN_SPELLS.contains(spell) && GrimoireLeaf.learned(player, spell)) return true;
        if (com.yashjit.scarlet.player.AgathaForm.worn(player)) return AgathaMagic.permits(player, spell);
        if (CrownItem.hasScarletMagic(player)) return !spell.billyOnly() && !spell.agathaOnly();
        if (!CrownItem.isWiccan(player)) return false;
        return spell == Spell.LEVITATION || spell == Spell.DREAMWALK && hasReadDarkhold(player) || BASE.contains(spell) || CRAFT.contains(spell)
                || DIVINE.contains(spell) && ascended(player);
    }

    public static boolean unlocked(Player player, Spell spell) {
        return Mastery.rank(player) >= spell.rank() || GrimoireLeaf.learned(player, spell) || AgathaMagic.stolen(player, spell);
    }

    private static boolean hasReadDarkhold(Player player) {
        return com.yashjit.scarlet.platform.Services.PLAYER_DATA.corruption(player).opened();
    }
}
