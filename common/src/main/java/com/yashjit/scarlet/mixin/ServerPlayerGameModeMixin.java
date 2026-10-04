package com.yashjit.scarlet.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yashjit.scarlet.hex.HexTape;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

/**
 * What a player does with their hands inside a standing Hex is theirs on its tape: what they put down from a hand goes
 * back into it in a rewind, and what they filled or emptied stays as they left it.
 */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin {

    @WrapMethod(method = "useItemOn")
    private InteractionResult scarlet$tapeUseOn(ServerPlayer player, Level level, ItemStack itemStack, InteractionHand hand, BlockHitResult hitResult,
                                                Operation<InteractionResult> original) {
        HexTape.Use use = HexTape.using(player);
        try {
            return original.call(player, level, itemStack, hand, hitResult);
        } finally {
            HexTape.used(use);
        }
    }

    @WrapMethod(method = "useItem")
    private InteractionResult scarlet$tapeUse(ServerPlayer player, Level level, ItemStack itemStack, InteractionHand hand,
                                              Operation<InteractionResult> original) {
        HexTape.Use use = HexTape.using(player);
        try {
            return original.call(player, level, itemStack, hand);
        } finally {
            HexTape.used(use);
        }
    }
}
