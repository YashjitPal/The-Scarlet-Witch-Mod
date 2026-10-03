package com.yashjit.scarlet.mixin;

import com.yashjit.scarlet.darkhold.Dreamwalk;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A dreamwalker leaving the game comes back to their body first, before they are saved.
 */
@Mixin(PlayerList.class)
abstract class PlayerListMixin {

    @Inject(method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"))
    private void scarlet$wakeBeforeLeaving(ServerPlayer player, CallbackInfo ci) {
        Dreamwalk.onLeave(player);
    }
}
