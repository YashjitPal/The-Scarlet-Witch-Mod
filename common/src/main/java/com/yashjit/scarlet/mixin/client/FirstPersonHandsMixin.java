package com.yashjit.scarlet.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.client.anim.FirstPersonGestures;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class FirstPersonHandsMixin {

    @Shadow
    private void renderPlayerArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, float inverseArmHeight,
                                 float attackValue, HumanoidArm arm, PlayerRenderState playerState) {
    }

    @Inject(method = "renderPlayerArm", at = @At("HEAD"))
    private void scarlet$castGesture(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, float inverseArmHeight,
                                     float attackValue, HumanoidArm arm, PlayerRenderState playerState, CallbackInfo ci) {
        FirstPersonGestures.transform(poseStack, arm);
    }

    @Inject(method = "submitHandsWithItems", at = @At("TAIL"))
    private void scarlet$castingOffHand(float partialTicks, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, PlayerRenderState playerState,
                                        FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
        AvatarRenderState avatar = playerState.avatarRenderState;
        if (avatar == null || avatar.isInvisible || state.isScoping) {
            return;
        }
        FirstPersonGestures.offHand((lowered, arm) -> {
            poseStack.pushPose();
            renderPlayerArm(poseStack, submitNodeCollector, avatar.lightCoords, lowered, 0.0F, arm, playerState);
            poseStack.popPose();
        }, avatar.mainArm.getOpposite(), state.offHandItem.isEmpty());
    }
}
