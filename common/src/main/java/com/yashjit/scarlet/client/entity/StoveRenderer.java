package com.yashjit.scarlet.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yashjit.scarlet.decor.DecorBlock;
import com.yashjit.scarlet.decor.StoveBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What is cooking on a range: each dish lying on its burner.
 */
public final class StoveRenderer implements BlockEntityRenderer<StoveBlockEntity, StoveRenderer.State> {

    /** How big a dish looks on a burner, from the size it is drawn at, and how much it turns with the range. */
    private static final float DISH = 0.3F;

    private final ItemModelResolver items;

    public StoveRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(StoveBlockEntity stove, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(stove, state, partialTick, camera, breaking);
        state.dishes.clear();
        state.burners.clear();
        state.facing = stove.getBlockState().getValue(DecorBlock.FACING).toYRot();
        int seed = (int) stove.getBlockPos().asLong();
        for (int k = 0; k < StoveBlockEntity.BURNERS; k++) {
            ItemStack dish = stove.items().get(k);
            if (dish.isEmpty()) {
                continue;
            }
            ItemStackRenderState drawn = new ItemStackRenderState();
            items.updateForTopItem(drawn, dish, ItemDisplayContext.FIXED, stove.getLevel(), null, seed + k);
            state.dishes.add(drawn);
            state.burners.add(StoveBlockEntity.burner(stove.getBlockState(), k));
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        for (int i = 0; i < state.dishes.size(); i++) {
            Vec3 at = state.burners.get(i);
            poseStack.pushPose();
            poseStack.translate(at.x, at.y + 0.005, at.z);
            // lying flat on the burner, turned with the range
            poseStack.rotateDegrees(Axis.YP, -state.facing);
            poseStack.rotateDegrees(Axis.XP, 90.0F);
            poseStack.scale(DISH, DISH, DISH);
            state.dishes.get(i).submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    public static final class State extends BlockEntityRenderState {
        final List<ItemStackRenderState> dishes = new ArrayList<>();
        final List<Vec3> burners = new ArrayList<>();
        float facing;
    }
}
