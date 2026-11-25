package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.magic.Mastery;
import com.yashjit.scarlet.player.AgathaForm;
import com.yashjit.scarlet.registry.ScarletDataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record AgathaOutfitPayload(int form, boolean hair) implements CustomPacketPayload {
    public static final Type<AgathaOutfitPayload> TYPE = new Type<>(Scarlet.id("agatha_outfit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AgathaOutfitPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AgathaOutfitPayload::form, ByteBufCodecs.BOOL, AgathaOutfitPayload::hair, AgathaOutfitPayload::new);

    public void apply(ServerPlayer player) {
        if (!player.isAlive() || !AgathaForm.worn(player) || form < 0 || form >= AgathaForm.values().length) return;
        AgathaForm outfit = AgathaForm.values()[form];
        if (outfit != AgathaForm.of(player) && Mastery.rank(player) < outfit.requiredRank()) return;
        player.getOffhandItem().set(ScarletDataComponents.AGATHA_FORM.get(), outfit);
        player.getOffhandItem().set(ScarletDataComponents.AGATHA_HAIR.get(), hair);
    }

    @Override public Type<AgathaOutfitPayload> type() { return TYPE; }
}
