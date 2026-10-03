package com.yashjit.scarlet.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.client.anim.FirstPersonGestures;
import com.yashjit.scarlet.client.darkhold.DarkholdBook;
import com.yashjit.scarlet.client.magic.MindControlClient;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
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

    /**
     * Inside a mind you hold, your own hands are back with your body.
     */
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void scarlet$noHandsInsideAnotherMind(float partialTicks, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                                                  PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
        if (MindControlClient.inside()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderPlayerArm", at = @At("HEAD"))
    private void scarlet$castGesture(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, float inverseArmHeight,
                                     float attackValue, HumanoidArm arm, PlayerRenderState playerState, CallbackInfo ci) {
        FirstPersonGestures.transform(poseStack, arm);
    }

    /**
     * The Darkhold is held in your hand, the arm drawn holding it, rather than shown on its own as vanilla shows held
     * items; and it leaves the hand to float before you while you read it, the hand drawn empty, held up to it.
     */
    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void scarlet$darkholdInTheHand(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
                                           InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
                                           SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
        boolean out = DarkholdBook.outOf(itemStack, hand);
        if (!out && !DarkholdBook.carried(itemStack)) {
            return;
        }
        ci.cancel();
        AvatarRenderState avatar = playerState.avatarRenderState;
        if (state.isScoping || avatar == null || avatar.isInvisible) {
            return;
        }
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? avatar.mainArm : avatar.mainArm.getOpposite();
        DarkholdBook.holdIn(out ? null : arm);
        poseStack.pushPose();
        renderPlayerArm(poseStack, submitNodeCollector, lightCoords, inverseArmHeight, attack, arm, playerState);
        poseStack.popPose();
        DarkholdBook.holdIn(null);
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
        DarkholdBook.submitFirstPerson(poseStack, submitNodeCollector, avatar.lightCoords, partialTicks);
    }
}
