package com.yashjit.scarlet.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yashjit.scarlet.decor.CarBodyBlock;
import com.yashjit.scarlet.decor.EraDecor;
import com.yashjit.scarlet.entity.ParkedCar;
import com.yashjit.scarlet.registry.ScarletBlocks;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Draws a parked car from its era's front and rear halves, put back together where the asset generator cut them
 * apart, its body tinted its paint.
 */
public class ParkedCarRenderer extends EntityRenderer<ParkedCar, ParkedCarRenderer.State> {

    private static final BlockDisplayContext CONTEXT = BlockDisplayContext.create();
    /** Where each half's model was moved to, in sixteenths: see the asset generator's car. */
    private static final float FRONT_SHIFT = 21;
    private static final float REAR_SHIFT = -5;
    private static final float ACROSS_SHIFT = 8;
    /** How far past its hitbox a car reaches, to be drawn whenever any of it is in view. */
    private static final double LENGTH_OVER = 1.6;

    private final BlockModelResolver models;

    public ParkedCarRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.models = context.getBlockModelResolver();
        this.shadowRadius = 1.3F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ParkedCar car, State state, float partialTicks) {
        super.extractRenderState(car, state, partialTicks);
        state.yaw = car.getYRot();
        BlockState body = ScarletBlocks.CAR_BODY.get().defaultBlockState().setValue(EraDecor.ERA, car.era());
        int paint = 0xFF000000 | car.color();
        models.update(state.front, body.setValue(CarBodyBlock.PART, CarBodyBlock.Half.FRONT), CONTEXT);
        state.front.tintLayers().add(paint);
        models.update(state.rear, body.setValue(CarBodyBlock.PART, CarBodyBlock.Half.REAR), CONTEXT);
        state.rear.tintLayers().add(paint);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        // modeled facing north; turned to face the way it is parked
        poseStack.rotateDegrees(Axis.YP, 180.0F - state.yaw);
        half(state.front, FRONT_SHIFT, state, poseStack, collector);
        half(state.rear, REAR_SHIFT, state, poseStack, collector);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    private static void half(BlockModelRenderState half, float shift, State state, PoseStack poseStack, SubmitNodeCollector collector) {
        if (half.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(-ACROSS_SHIFT / 16.0F, 0.0F, -shift / 16.0F);
        half.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }

    @Override
    protected AABB getBoundingBoxForCulling(ParkedCar car, float partialTicks) {
        return super.getBoundingBoxForCulling(car, partialTicks).inflate(LENGTH_OVER, 0.2, LENGTH_OVER);
    }

    public static final class State extends EntityRenderState {
        final BlockModelRenderState front = new BlockModelRenderState();
        final BlockModelRenderState rear = new BlockModelRenderState();
        float yaw;
    }
}
