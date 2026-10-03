package com.yashjit.scarlet.client.hex;

import com.yashjit.scarlet.network.GesturePayload;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Sees a Hex's townspeople for who they are now: whatever mob they really are, it reads them into the same state a
 * player is drawn from, with their era's clothes as the skin, so the player renderer draws them like anyone else in
 * the sitcom. Held weapons and armor stay out of the picture. They wave to whoever comes by and talk with their hands.
 */
public final class ResidentRenderer extends LivingEntityRenderer<LivingEntity, AvatarRenderState, PlayerModel> {

    private static @Nullable ResidentRenderer instance;

    private ResidentRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    /**
     * Made afresh with the vanilla renderers, whenever resources reload.
     */
    public static void bake(EntityRendererProvider.Context context) {
        instance = new ResidentRenderer(context);
    }

    public static @Nullable ResidentRenderer instance() {
        return instance;
    }

    @Override
    public AvatarRenderState createRenderState() {
        return new ResidentRenderState();
    }

    @Override
    public Identifier getTextureLocation(AvatarRenderState state) {
        return state.skin.body().texturePath();
    }

    @Override
    public void extractRenderState(LivingEntity entity, AvatarRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.skin = ResidentsClient.skin(entity);
        state.id = entity.getId();
        state.isSpectator = false;
        state.showHat = true;
        state.showJacket = true;
        state.showLeftPants = true;
        state.showRightPants = true;
        state.showLeftSleeve = true;
        state.showRightSleeve = true;
        state.showCape = false;
        state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        state.isCrouching = false;
        state.speedValue = 1.0F;
        state.swimAmount = entity.getSwimAmount(partialTicks);
        state.isVisuallySwimming = entity.isVisuallySwimming();
        if (state instanceof ResidentRenderState resident) {
            ResidentsClient.Gesturing gesturing = ResidentsClient.gesture(entity);
            resident.gesture = gesturing == null ? null : gesturing.kind();
            resident.gestureAge = gesturing == null ? 0.0F : entity.level().getGameTime() - gesturing.start() + partialTicks;
        }
    }

    /**
     * Poses the arms of a townsperson making a gesture, once the player model has posed them as it would a player's:
     * townspeople are drawn by the player renderer, from the state read here.
     */
    public static void gesture(PlayerModel model, AvatarRenderState state) {
        if (!(state instanceof ResidentRenderState resident) || resident.gesture == null) {
            return;
        }
        float age = resident.gestureAge;
        // up over the first few ticks and back down over the last
        float in = Mth.clamp(Math.min(age / 5.0F, (resident.gesture.ticks - age) / 6.0F), 0.0F, 1.0F);
        float blend = in * in * (3.0F - 2.0F * in);
        switch (resident.gesture) {
            case WAVE -> {
                // the hand up high beside the head, going from side to side
                model.rightArm.xRot = Mth.lerp(blend, model.rightArm.xRot, -0.2F);
                model.rightArm.yRot = Mth.lerp(blend, model.rightArm.yRot, 0.0F);
                model.rightArm.zRot = Mth.lerp(blend, model.rightArm.zRot, 2.55F + Mth.sin(age * 0.9F) * 0.32F);
            }
            case TALK_RIGHT -> talk(model.rightArm, 1.0F, age, blend);
            case TALK_LEFT -> talk(model.leftArm, -1.0F, age, blend);
        }
    }

    /** A hand held out in front, making the point. */
    private static void talk(ModelPart arm, float side, float age, float blend) {
        arm.xRot = Mth.lerp(blend, arm.xRot, -0.9F + Mth.sin(age * 0.7F) * 0.12F);
        arm.yRot = Mth.lerp(blend, arm.yRot, -0.35F * side);
        arm.zRot = Mth.lerp(blend, arm.zRot, 0.12F * side);
    }

    static final class ResidentRenderState extends AvatarRenderState {
        GesturePayload.@Nullable Kind gesture;
        /** Ticks into the gesture. */
        float gestureAge;
    }
}
