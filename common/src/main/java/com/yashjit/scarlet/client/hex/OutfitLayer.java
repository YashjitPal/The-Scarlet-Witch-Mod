package com.yashjit.scarlet.client.hex;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.client.costume.CostumeModel;
import com.yashjit.scarlet.client.costume.CostumeModels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * Era clothes on players inside a Hex, fitted over their skin on the same slightly larger body as the costume.
 */
public final class OutfitLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    private final CostumeModel wide;
    private final CostumeModel slim;

    public OutfitLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet models) {
        super(parent);
        this.wide = new CostumeModel(models.bakeLayer(CostumeModels.WIDE), false);
        this.slim = new CostumeModel(models.bakeLayer(CostumeModels.SLIM), true);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (state.isInvisible || minecraft.level == null || !(minecraft.level.getEntity(state.id) instanceof Player player)) {
            return;
        }
        boolean isSlim = state.skin.model() == PlayerModelType.SLIM;
        Identifier outfit = Outfits.outfit(player, isSlim, minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        if (outfit == null) {
            return;
        }
        collector.submitModel(isSlim ? slim : wide, state, poseStack, RenderTypes.armorCutoutNoCull(outfit), light,
                LivingEntityRenderer.getOverlayCoords(state, 0.0F), -1, null, state.outlineColor);
    }
}
