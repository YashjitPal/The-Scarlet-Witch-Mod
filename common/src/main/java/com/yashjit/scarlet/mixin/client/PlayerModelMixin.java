package com.yashjit.scarlet.mixin.client;

import com.yashjit.scarlet.client.anim.CastPoses;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
abstract class PlayerModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void scarlet$castPose(AvatarRenderState state, CallbackInfo ci) {
        CastPoses.apply((PlayerModel) (Object) this, state);
    }
}
