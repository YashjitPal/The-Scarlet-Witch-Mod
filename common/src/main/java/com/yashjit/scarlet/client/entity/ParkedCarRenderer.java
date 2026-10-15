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
    /** What its paint scuffs toward as it is knocked about, and how far at most before it is wrecked. */
    private static final int SCUFFED = 0xFF5C5650;
    private static final float MOST_SCUFF = 0.35F;
    /** A wreck, burnt out: its paint, its trim and its tires charred. */
    private static final int CHARRED = 0xFF241F1D;
    private static final int CHARRED_TRIM = 0xFF3A3734;
    private static final int CHARRED_TIRES = 0xFF2B2A29;
    /** Its windows crazed and dulled once it is badly knocked about, and below how much of it left. */
    private static final int CRAZED = 0xFFB4B8BB;
    private static final float CRAZES_BELOW = 0.5F;
    /** Its lamps dead, and below how much of it left they fail. */
    private static final int DEAD_LAMPS = 0xFF6E6C69;
    private static final float LAMPS_FAIL_BELOW = 0.2F;

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
        state.condition = car.condition();
        state.wrecked = car.wrecked();
        state.shake = car.shake(partialTicks);
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
        int paint = state.wrecked ? mix(state.paint, CHARRED, 0.88F) : mix(state.paint, SCUFFED, (1.0F - state.condition) * MOST_SCUFF);
        int trim = state.wrecked ? CHARRED_TRIM : -1;
        int tires = state.wrecked ? CHARRED_TIRES : -1;
        boolean lit = !state.wrecked && state.condition >= LAMPS_FAIL_BELOW;
        poseStack.pushPose();
        // modeled with its nose to the north; turned the way it faces
        poseStack.rotateDegrees(Axis.YP, 180.0F - state.yaw);
        if (state.shake != 0.0F) {
            // rocking on its springs from a knock
            poseStack.translate(0.0F, 0.4F, 0.0F);
            poseStack.rotateDegrees(Axis.ZP, state.shake);
            poseStack.translate(0.0F, -0.4F, 0.0F);
        }
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
            collector.submitModelPart(car.wheel(), poseStack, solid, light, overlay, null, tires, outline);
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
        collector.submitModelPart(car.paint(), poseStack, solid, light, overlay, null, paint, outline);
        collector.submitModelPart(car.trim(), poseStack, solid, light, overlay, null, trim, outline);
        collector.submitModelPart(car.lights(), poseStack, solid, lit ? LightCoordsUtil.FULL_BRIGHT : light, overlay, null, lit ? -1 : state.wrecked ? CHARRED_TRIM : DEAD_LAMPS,
                outline);
        if (!state.wrecked) {
            // a wreck's windows are gone
            collector.submitModelPart(car.glass(), poseStack, glass, light, overlay, null, state.condition < CRAZES_BELOW ? CRAZED : -1, outline);
        }
        poseStack.popPose();
        poseStack.translate(car.steeringX() * PIXEL, car.steeringY() * PIXEL, car.steeringZ() * PIXEL);
        poseStack.rotateDegrees(Axis.XP, -STEERING_LEAN);
        poseStack.rotateDegrees(Axis.ZP, state.steer * STEERING_LOCK);
        model(poseStack);
        collector.submitModelPart(car.steering(), poseStack, solid, light, overlay, null, trim, outline);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    /** Partway from one color to another, channel by channel. */
    private static int mix(int from, int to, float amount) {
        float t = Math.clamp(amount, 0.0F, 1.0F);
        int a = Math.round(Mth.lerp(t, from >>> 24, to >>> 24));
        int r = Math.round(Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF));
        int g = Math.round(Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF));
        int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
        return a << 24 | r << 16 | g << 8 | b;
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
        /** How much of it is left, from 1 whole to 0. */
        float condition = 1.0F;
        boolean wrecked;
        /** How far it rocks from a knock, in degrees. */
        float shake;
    }
}
