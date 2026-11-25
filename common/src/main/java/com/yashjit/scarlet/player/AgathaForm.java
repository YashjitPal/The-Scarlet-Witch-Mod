package com.yashjit.scarlet.player;

import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.registry.ScarletDataComponents;
import com.yashjit.scarlet.registry.ScarletItems;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;

public enum AgathaForm implements StringRepresentable {
    TRAVELING("traveling"), AGNES("agnes"), FINALE("finale");

    private final String name;

    AgathaForm(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static boolean worn(Player player) {
        return player.getOffhandItem().is(ScarletItems.AGATHA_BROOCH.get()) && !player.isSpectator()
                && !CrownItem.isWearingCrown(player) && !Speed.wearsGoggles(player);
    }

    public static AgathaForm of(Player player) {
        return player.getOffhandItem().getOrDefault(ScarletDataComponents.AGATHA_FORM.get(), TRAVELING);
    }

    public static boolean styledHair(Player player) {
        return player.getOffhandItem().getOrDefault(ScarletDataComponents.AGATHA_HAIR.get(), false);
    }

    public int requiredRank() {
        return switch (this) { case TRAVELING -> 1; case AGNES -> 5; case FINALE -> 9; };
    }

    public AgathaForm next() {
        return switch (this) {
            case TRAVELING -> AGNES;
            case AGNES -> FINALE;
            case FINALE -> TRAVELING;
        };
    }
}
