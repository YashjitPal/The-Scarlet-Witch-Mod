package com.yashjit.scarlet.mixin;

import com.yashjit.scarlet.darkhold.Dreamwalk;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A dreamwalker's spirit goes about as a spectator, but cannot use a spectator's freedom to go wherever it likes.
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleTeleportToEntityPacket", cancellable = true, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V"))
    private void scarlet$staysInTheCreature(ServerboundTeleportToEntityPacket packet, CallbackInfo ci) {
        if (Dreamwalk.isAway(player)) {
            ci.cancel();
        }
    }
}
