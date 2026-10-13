package com.yashjit.scarlet.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yashjit.scarlet.entity.CarShape;
import com.yashjit.scarlet.entity.ParkedCar;
import com.yashjit.scarlet.hex.Era;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Draws a car from its era's model: its wheels on the ground, turning as it rolls and the front ones steering; its body
 * riding over them, tipped to the slope they stand on and leaning out of its turns, in its paint, its glass and its trim,
 * its lamps lit; and its steering wheel, turned as its driver turns it.
 */
public class ParkedCarRenderer extends EntityRenderer<ParkedCar, ParkedCarRenderer.State> {

    /** How far past its box a car reaches, to be drawn whenever any of it is in view. */
    private static final double LENGTH_OVER = 1.8;
    /** How far the front wheels turn at full lock, and the steering wheel with them. */
    private static final float WHEEL_LOCK = 26.0F;
    private static final float STEERING_LOCK = 110.0F;
    /** How far the steering wheel's top leans away from its driver, from upright. */
    private static final float STEERING_LEAN = 32.0F;
    private static final float PIXEL = 1.0F / 16.0F;

    public ParkedCarRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.5F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ParkedCar car, State state, float partialTicks) {
        super.extractRenderState(car, state, partialTicks);
        state.era = car.era();
        state.paint = 0xFF000000 | car.color();
        state.yaw = Mth.rotLerp(partialTicks, car.yRotO, car.getYRot());
        state.spin = car.spin(partialTicks);
        state.steer = car.steer(partialTicks);
        state.front = car.frontRise(partialTicks);
        state.rear = car.rearRise(partialTicks);
        state.roll = car.roll(partialTicks);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        CarModels.Built car = CarModels.get(state.era);
        CarShape shape = car.shape();
        RenderType solid = RenderTypes.entityCutout(CarModels.TEXTURE);
        RenderType glass = RenderTypes.entityTranslucent(CarModels.TEXTURE);
        int light = state.lightCoords;
        int overlay = OverlayTexture.NO_OVERLAY;
        int outline = state.outlineColor;
        float radius = shape.wheelRadius() * PIXEL;
        poseStack.pushPose();
        // modeled with its nose to the north; turned the way it faces
        poseStack.rotateDegrees(Axis.YP, 180.0F - state.yaw);
        for (int k = 0; k < 4; k++) {
            boolean front = k < 2;
            poseStack.pushPose();
            poseStack.translate((k % 2 == 0 ? 1 : -1) * shape.track() * PIXEL, (front ? state.front : state.rear) + radius,
                    (front ? shape.frontAxle() : shape.rearAxle()) * PIXEL);
            if (front) {
                poseStack.rotateDegrees(Axis.YP, state.steer * WHEEL_LOCK);
            }
            poseStack.rotate(Axis.XP, state.spin);
            model(poseStack);
            collector.submitModelPart(car.wheel(), poseStack, solid, light, overlay, null, -1, outline);
            poseStack.popPose();
        }
        // the body over its wheels, tipped to the slope between them and leaning out of a turn
        float wheelbase = (shape.rearAxle() - shape.frontAxle()) * PIXEL;
        poseStack.translate(0.0F, (state.front + state.rear) / 2 + radius, 0.0F);
        poseStack.rotate(Axis.XP, (float) Math.atan2(state.front - state.rear, wheelbase));
        poseStack.rotateDegrees(Axis.ZP, state.roll);
        poseStack.translate(0.0F, -radius, 0.0F);
        poseStack.pushPose();
        model(poseStack);
        collector.submitModelPart(car.paint(), poseStack, solid, light, overlay, null, state.paint, outline);
        collector.submitModelPart(car.trim(), poseStack, solid, light, overlay, null, -1, outline);
        collector.submitModelPart(car.lights(), poseStack, solid, LightCoordsUtil.FULL_BRIGHT, overlay, null, -1, outline);
        collector.submitModelPart(car.glass(), poseStack, glass, light, overlay, null, -1, outline);
        poseStack.popPose();
        poseStack.translate(car.steeringX() * PIXEL, car.steeringY() * PIXEL, car.steeringZ() * PIXEL);
        poseStack.rotateDegrees(Axis.XP, -STEERING_LEAN);
        poseStack.rotateDegrees(Axis.ZP, state.steer * STEERING_LOCK);
        model(poseStack);
        collector.submitModelPart(car.steering(), poseStack, solid, light, overlay, null, -1, outline);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    /** Into the space car models are built in, upside down and mirrored across as entity models are; they draw themselves in sixteenths. */
    private static void model(PoseStack poseStack) {
        poseStack.scale(-1.0F, -1.0F, 1.0F);
    }

    @Override
    protected AABB getBoundingBoxForCulling(ParkedCar car, float partialTicks) {
        return super.getBoundingBoxForCulling(car, partialTicks).inflate(LENGTH_OVER, 0.4, LENGTH_OVER);
    }

    public static final class State extends EntityRenderState {
        Era era = Era.PRESENT;
        int paint;
        float yaw;
        float spin;
        float steer;
        /** How high the ground stands under each axle, over the car's own feet. */
        float front;
        float rear;
        float roll;
    }
}
