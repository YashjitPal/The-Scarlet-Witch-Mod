package com.yashjit.scarlet.client.costume;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.client.render.ScarletRenderTypes;
import com.yashjit.scarlet.entity.DreamBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * Draws the costume and cape on players. Both dissolve in and out with the transformation; their tint alpha carries
 * the progress to the dissolve shader.
 */
public final class CostumeLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    private final CostumeModel wide;
    private final CostumeModel slim;
    private final PlayerCapeModel cape;

    public CostumeLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet models) {
        super(parent);
        this.wide = new CostumeModel(models.bakeLayer(CostumeModels.WIDE), false);
        this.slim = new CostumeModel(models.bakeLayer(CostumeModels.SLIM), true);
        this.cape = new PlayerCapeModel(models.bakeLayer(CostumeModels.CAPE));
        FirstPersonCostume.bake(models);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(state.id);
        if (state.isInvisible || !(entity instanceof Player || entity instanceof DreamBody)) {
            return;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        CostumeView view = entity instanceof Player player ? CostumeView.of(player, partialTick) : CostumeView.of((DreamBody) entity);
        if (view == null) {
            return;
        }
        boolean isSlim = state.skin.model() == PlayerModelType.SLIM;
        int tint = ARGB.white(view.progress());
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
        collector.submitModel(isSlim ? slim : wide, state, poseStack, ScarletRenderTypes.costume(view.style(), isSlim),
                light, overlay, tint, null, state.outlineColor);
        if (!state.chestEquipment.has(DataComponents.GLIDER)) {
            collector.submitModel(cape, state, poseStack, ScarletRenderTypes.cape(view.style()), light, overlay, tint, null, state.outlineColor);
        }
    }
}
