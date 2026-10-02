package com.yashjit.scarlet.client.hex;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Sees a Hex's townspeople for who they are now: whatever mob they really are, it reads them into the same state a
 * player is drawn from, with their era's clothes as the skin, so the player renderer draws them like anyone else in
 * the sitcom. Held weapons and armor stay out of the picture.
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
        return new AvatarRenderState();
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
    }
}
