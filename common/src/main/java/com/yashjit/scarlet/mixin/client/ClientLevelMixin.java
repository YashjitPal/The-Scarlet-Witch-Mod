package com.yashjit.scarlet.mixin.client;

import com.yashjit.scarlet.client.hex.TownSlips;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {

    /**
     * A block the server changes is what it really is now, whatever a slip of the town is showing there.
     */
    @Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"))
    private void scarlet$letSlipsGo(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        TownSlips.changed(pos);
    }
}
