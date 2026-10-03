package com.yashjit.scarlet.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.client.anim.CastPoses;
import com.yashjit.scarlet.client.anim.FirstPersonGestures;
import com.yashjit.scarlet.client.costume.CostumeRendering;
import com.yashjit.scarlet.client.costume.FirstPersonCostume;
import com.yashjit.scarlet.client.magic.Hands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerModelType;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
abstract class AvatarRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void scarlet$hideArmorUnderCostume(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        CostumeRendering.hideCoveredEquipment(entity, state, partialTicks);
        CastPoses.extract(entity, state, partialTicks);
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("TAIL"))
    private void scarlet$levitationLeanAndBob(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
        CastPoses.rotate(state, poseStack);
    }

    /**
     * After vanilla has reset the first-person arm's pose and tilted it, just before it is submitted.
     */
    @Inject(method = "renderHand", at = @At(value = "FIELD", target = "Lnet/minecraft/client/model/geom/ModelPart;zRot:F", opcode = Opcodes.PUTFIELD,
            ordinal = 1, shift = At.Shift.AFTER))
    private void scarlet$castGestureOnFirstPersonArm(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, Identifier skinTexture,
                                                     ModelPart arm, boolean hasSleeve, CallbackInfo ci) {
        FirstPersonGestures.poseArm(((AvatarRenderer<?>) (Object) this).getModel(), arm);
    }

    @Inject(method = "renderHand", at = @At("TAIL"))
    private void scarlet$costumeOnFirstPersonArm(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, Identifier skinTexture,
                                                 ModelPart arm, boolean hasSleeve, CallbackInfo ci) {
        FirstPersonCostume.render((AvatarRenderer<?>) (Object) this, poseStack, collector, lightCoords, arm);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            Hands.recordFirstPerson(arm == ((AvatarRenderer<?>) (Object) this).getModel().rightArm, player.getSkin().model() == PlayerModelType.SLIM,
                    poseStack, arm);
        }
    }
}
