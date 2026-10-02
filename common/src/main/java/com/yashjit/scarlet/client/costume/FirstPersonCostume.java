package com.yashjit.scarlet.client.costume;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.client.fx.ArmMagic;
import com.yashjit.scarlet.client.hex.Outfits;
import com.yashjit.scarlet.client.magic.MagicVisuals;
import com.yashjit.scarlet.client.render.ScarletRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import org.jspecify.annotations.Nullable;

/**
 * The costume or Hex outfit sleeve, gauntlet and arm magic on your own hands in first person. Uses its own models:
 * parts drawn with {@code submitModelPart} keep whatever pose they hold at draw time, so they must not be shared with
 * other players.
 */
public final class FirstPersonCostume {

    private static @Nullable CostumeModel wide;
    private static @Nullable CostumeModel slim;

    private FirstPersonCostume() {
    }

    static void bake(EntityModelSet models) {
        wide = new CostumeModel(models.bakeLayer(CostumeModels.WIDE), false);
        slim = new CostumeModel(models.bakeLayer(CostumeModels.SLIM), true);
    }

    /**
     * Called right after vanilla submits a first-person arm.
     */
    public static void render(AvatarRenderer<?> renderer, PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart arm) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || wide == null || slim == null) {
            return;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        boolean isSlim = player.getSkin().model() == PlayerModelType.SLIM;
        boolean right = arm == renderer.getModel().rightArm;
        CostumeView view = CostumeView.of(player, partialTick);
        Identifier outfit = view == null ? Outfits.outfit(player, isSlim, partialTick) : null;
        if (view != null || outfit != null) {
            CostumeModel model = isSlim ? slim : wide;
            ModelPart costumeArm = right ? model.rightArm : model.leftArm;
            costumeArm.loadPose(arm.storePose());
            costumeArm.visible = true;
            (right ? model.rightSleeve : model.leftSleeve).visible = true;
            if (view != null) {
                collector.submitModelPart(costumeArm, poseStack, ScarletRenderTypes.costume(view.style(), isSlim), light,
                        OverlayTexture.NO_OVERLAY, null, ARGB.white(view.progress()), 0);
            } else {
                collector.submitModelPart(costumeArm, poseStack, RenderTypes.armorCutoutNoCull(outfit), light,
                        OverlayTexture.NO_OVERLAY, null, -1, 0);
            }
        }

        float intensity = MagicVisuals.armIntensity(player, right ? HumanoidArm.RIGHT : HumanoidArm.LEFT, partialTick);
        if (intensity > 0.01F) {
            float time = (float) (minecraft.level.getGameTime() % 24000L) + partialTick;
            poseStack.pushPose();
            arm.translateAndRotate(poseStack);
            collector.submitCustomGeometry(poseStack, ScarletRenderTypes.glow(), ArmMagic.arm(right, isSlim, time, intensity));
            poseStack.popPose();
        }
    }
}
