package com.yashjit.scarlet.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.client.darkhold.CorruptionClient;
import com.yashjit.scarlet.client.fx.BoltVisuals;
import com.yashjit.scarlet.client.magic.Hands;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.entity.ChaosBolt;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Draws a chaos blast; see {@link BoltVisuals} for its layers.
 */
public final class ChaosBoltRenderer extends EntityRenderer<ChaosBolt, ChaosBoltRenderer.State> {

    /** How far off its line a palm may be and still be the one it left. */
    private static final double PALM_REACH = 0.8;

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
        state.darkness = CorruptionClient.darkness(bolt.getOwner());
        state.flown = flown(bolt, direction, partialTicks);
    }

    /**
     * How far it has come from the hand that threw it, as drawn, so its trail never reaches back past that hand. It is
     * drawn between where it was last tick and where it is now, a tick behind its age.
     */
    private static float flown(ChaosBolt bolt, Vec3 direction, float partialTicks) {
        float flown = Math.max(0.0F, bolt.tickCount - 1 + partialTicks) * ChaosBolt.SPEED;
        if (bolt.getOwner() instanceof Player caster) {
            Vec3 head = bolt.getPosition(partialTicks);
            for (HumanoidArm arm : HumanoidArm.values()) {
                Vec3 out = head.subtract(Hands.palm(caster, arm));
                double along = out.dot(direction);
                if (out.subtract(direction.scale(along)).lengthSqr() < PALM_REACH * PALM_REACH) {
                    flown = Math.min(flown, (float) Math.max(0.0, along));
                }
            }
        }
        return flown;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Vector3f direction = new Vector3f(state.direction);
        float age = state.age;
        float flown = state.flown;
        int seed = state.seed;
        float darkness = state.darkness;
        try (Glow.Darkening ignored = Glow.darkening(darkness)) {
            GlowPass.submitPixels(collector, poseStack, (pose, buffer) -> BoltVisuals.blast(buffer, pose, direction, age, flown, seed));
        }
        super.submit(state, poseStack, collector, camera);
    }

    public static final class State extends EntityRenderState {
        final Vector3f direction = new Vector3f(0, 0, 1);
        float age;
        float flown;
        int seed;
        float darkness;
    }
}
