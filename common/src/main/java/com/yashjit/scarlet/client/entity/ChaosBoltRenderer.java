package com.yashjit.scarlet.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.client.fx.BoltVisuals;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.entity.ChaosBolt;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Draws a chaos blast; see {@link BoltVisuals} for its layers.
 */
public final class ChaosBoltRenderer extends EntityRenderer<ChaosBolt, ChaosBoltRenderer.State> {

    public ChaosBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ChaosBolt bolt, State state, float partialTicks) {
        super.extractRenderState(bolt, state, partialTicks);
        Vec3 velocity = bolt.getDeltaMovement();
        Vec3 direction = velocity.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : velocity.normalize();
        state.direction.set((float) direction.x, (float) direction.y, (float) direction.z);
        state.age = bolt.tickCount + partialTicks;
        state.seed = bolt.getId();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Vector3f direction = new Vector3f(state.direction);
        float age = state.age;
        int seed = state.seed;
        GlowPass.submit(collector, poseStack, (pose, buffer) -> BoltVisuals.blast(buffer, pose, direction, age, age * ChaosBolt.SPEED, seed));
        super.submit(state, poseStack, collector, camera);
    }

    public static final class State extends EntityRenderState {
        final Vector3f direction = new Vector3f(0, 0, 1);
        float age;
        int seed;
    }
}
