package com.yashjit.scarlet.client.costume;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.client.render.ScarletRenderTypes;
import com.yashjit.scarlet.crown.CrownStyle;
import com.yashjit.scarlet.entity.DreamBody;
import com.yashjit.scarlet.magic.WiccanSpells;
import com.yashjit.scarlet.player.AgathaForm;
import java.util.EnumMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
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
    private final EnumMap<AgathaForm, AgathaLook> agathaWide;
    private final EnumMap<AgathaForm, AgathaLook> agathaSlim;

    public CostumeLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet models) {
        super(parent);
        this.wide = new CostumeModel(models.bakeLayer(CostumeModels.WIDE), false);
        this.slim = new CostumeModel(models.bakeLayer(CostumeModels.SLIM), true);
        this.cape = new PlayerCapeModel(models.bakeLayer(CostumeModels.CAPE));
        this.agathaWide = AgathaLook.bake(models, false);
        this.agathaSlim = AgathaLook.bake(models, true);
        FirstPersonCostume.bake(models);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(state.id);
        Player illusion = entity instanceof net.minecraft.world.entity.decoration.Mannequin ? com.yashjit.scarlet.client.fx.AgathaFx.doubleOf(state.id) : null;
        if (state.isInvisible || !(entity instanceof Player || entity instanceof DreamBody || illusion != null)) {
            return;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        boolean isSlim = state.skin.model() == PlayerModelType.SLIM;
        if (((SpeedLook.Pose) state).scarlet$speedSuit()) {
            collector.submitModel(isSlim ? slim : wide, state, poseStack, RenderTypes.armorCutoutNoCull(SpeedLook.texture(isSlim)),
                    light, LivingEntityRenderer.getOverlayCoords(state, 0), -1, null, state.outlineColor);
            return;
        }
        CostumeView view = entity instanceof Player player ? CostumeView.of(player, partialTick) : entity instanceof DreamBody body ? CostumeView.of(body) : null;
        if (view == null) {
            Player player = entity instanceof Player wearer ? wearer : illusion;
            if (player != null && AgathaForm.worn(player)) {
                AgathaForm form = AgathaForm.of(player);
                ((AgathaLook.Pose)state).scarlet$setAgathaHair(AgathaForm.styledHair(player));
                AgathaLook model = (isSlim ? agathaSlim : agathaWide).get(form);
                int overlay = LivingEntityRenderer.getOverlayCoords(state, 0);
                collector.submitModel(model, state, poseStack, RenderTypes.armorCutoutNoCull(model.texture),
                        light, overlay, -1, null, state.outlineColor);
                if (form == AgathaForm.FINALE) {
                    int shine = (int) (117 + 40 * Mth.sin(state.ageInTicks * 0.075F));
                    collector.submitModel(model, state, poseStack, RenderTypes.eyes(AgathaLook.EMBROIDERY), LightCoordsUtil.FULL_BRIGHT,
                            overlay, ARGB.color(255, shine, shine, shine), null, 0);
                }
            }
            return;
        }
        int tint = ARGB.white(view.progress());
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
        boolean ascended = entity instanceof Player player && WiccanSpells.ascended(player);
        collector.submitModel(isSlim ? slim : wide, state, poseStack, ScarletRenderTypes.costume(view.style(), isSlim, ascended),
                light, overlay, tint, null, state.outlineColor);
        if (!state.chestEquipment.has(DataComponents.GLIDER)) {
            if (view.style() == CrownStyle.WICCAN) {
                WiccanCloak.submit(cape, state, poseStack, collector, ascended, light, overlay, tint);
            } else {
                collector.submitModel(cape, state, poseStack, ScarletRenderTypes.cape(view.style()), light, overlay, tint, null, state.outlineColor);
            }
        }
    }
}
