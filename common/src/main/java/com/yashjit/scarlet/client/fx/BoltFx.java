package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.entity.ChaosBolt;
import com.yashjit.scarlet.network.ChaosImpactPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Everything a chaos blast leaves behind: the burst in the palm as it is released, sparks and dust shed along its
 * flight, its trail drawing back into the point of impact, and the impact itself, a flash with two shock rings that
 * lie flat on the surface it hit.
 */
public final class BoltFx {

    private static final float IMPACT_TICKS = 11.0F;
    private static final float RELEASE_TICKS = 4.0F;
    private static final float TAIL_TICKS = 3.5F;

    private static final List<Impact> IMPACTS = new ArrayList<>();
    private static final List<Release> RELEASES = new ArrayList<>();

    private BoltFx() {
    }

    public static void impact(ChaosImpactPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        RandomSource random = ScarletFx.random();
        Vec3 at = payload.position();
        Vec3 normal = payload.normal();
        IMPACTS.add(new Impact(at, normal, payload.direction(), payload.hitEntity(), minecraft.level.getGameTime() + partialTick()));
        float density = ScarletFx.density();
        for (int i = 0, n = Math.round(40 * density); i < n; i++) {
            Vec3 direction = normal.scale(0.85).add(randomUnit(random)).normalize();
            int core = random.nextFloat() < 0.35F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET;
            ScarletFx.spark(at, direction.scale(0.14 + random.nextDouble() * 0.32), 7 + random.nextInt(10), 0.026F + random.nextFloat() * 0.03F,
                    core, ScarletPalette.SCARLET, 0.012F, 0.84F);
        }
        for (int i = 0, n = Math.round(12 * density); i < n; i++) {
            Vec3 direction = normal.scale(0.5).add(randomUnit(random).scale(0.6)).normalize();
            ScarletFx.spark(at, direction.scale(0.04 + random.nextDouble() * 0.06), 20 + random.nextInt(14), 0.035F,
                    ScarletPalette.CRIMSON, ScarletPalette.WINE, 0.004F, 0.9F);
        }
        for (int i = 0, n = Math.round(14 * density); i < n; i++) {
            ChaosDust.spawn(at, normal.scale(0.06).add(randomUnit(random).scale(0.08)));
        }
    }

    /**
     * Magic leaving a palm toward {@code forward}.
     *
     * @param nearCamera your own hand in first person: nothing drawn over the view, sparks start a little ahead
     */
    public static void release(Vec3 palm, Vec3 forward, boolean nearCamera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        RandomSource random = ScarletFx.random();
        Vec3 from = nearCamera ? palm.add(forward.scale(0.8)) : palm.add(forward.scale(0.15));
        if (!nearCamera) {
            RELEASES.add(new Release(from, forward, minecraft.level.getGameTime() + partialTick()));
        }
        float density = ScarletFx.density();
        for (int i = 0, n = Math.round(10 * density); i < n; i++) {
            Vec3 direction = forward.add(randomUnit(random).scale(0.38)).normalize();
            int core = random.nextFloat() < 0.4F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET;
            ScarletFx.spark(from, direction.scale(0.16 + random.nextDouble() * 0.18), 4 + random.nextInt(5), 0.024F, core, ScarletPalette.SCARLET, 0.0F, 0.8F);
        }
        for (int i = 0, n = Math.round(6 * density); i < n; i++) {
            ChaosDust.spawn(from.add(randomUnit(random).scale(0.06)), forward.scale(0.05).add(randomUnit(random).scale(0.04)));
        }
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null) {
            IMPACTS.clear();
            RELEASES.clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        double now = minecraft.level.getGameTime();
        IMPACTS.removeIf(impact -> now - impact.start() > IMPACT_TICKS + 1);
        RELEASES.removeIf(release -> now - release.start() > RELEASE_TICKS + 1);
        RandomSource random = ScarletFx.random();
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        float density = ScarletFx.density();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof ChaosBolt bolt) || bolt.distanceToSqr(camera) > 96 * 96 || bolt.tickCount < 1) {
                continue;
            }
            Vec3 velocity = bolt.getDeltaMovement();
            for (int i = 0, n = Math.round(2 * density); i < n; i++) {
                Vec3 at = bolt.position().subtract(velocity.scale(random.nextDouble())).add(randomUnit(random).scale(0.15));
                int core = random.nextFloat() < 0.25F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET;
                ScarletFx.spark(at, randomUnit(random).scale(0.035).subtract(velocity.scale(0.03)), 6 + random.nextInt(6), 0.026F,
                        core, ScarletPalette.SCARLET, 0.0F, 0.88F);
            }
            if (random.nextFloat() < 0.5F * density) {
                ChaosDust.spawn(bolt.position().subtract(velocity.scale(random.nextDouble() * 0.8)).add(randomUnit(random).scale(0.12)),
                        randomUnit(random).scale(0.02));
            }
        }
    }

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack) {
        if (IMPACTS.isEmpty() && RELEASES.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        double now = minecraft.level.getGameTime() + partialTick();
        List<Impact> impacts = List.copyOf(IMPACTS);
        List<Release> releases = List.copyOf(RELEASES);
        GlowPass.submit(collector, poseStack, (pose, buffer) -> {
            Glow.Billboard axes = Glow.billboard(pose);
            for (Impact impact : impacts) {
                float t = (float) (now - impact.start());
                if (t < 0.0F || t > IMPACT_TICKS) {
                    continue;
                }
                Vector3f normal = impact.normal().toVector3f();
                Vector3f at = impact.position().subtract(camera).toVector3f();
                if (t < TAIL_TICKS) {
                    // the trail catches up with its head and is gone
                    Vector3f direction = impact.direction().toVector3f();
                    float length = 4.2F * (1.0F - Ease.outCubic(t / TAIL_TICKS));
                    BoltVisuals.trail(buffer, pose, axes, at, direction, Glow.planeAxes(direction), length, t, 1.0F - t / TAIL_TICKS);
                }
                if (t < 5.0F) {
                    float fade = 1.0F - Ease.outCubic(t / 5.0F);
                    float grow = 0.7F + 0.3F * Ease.outCubic(t / 2.0F);
                    Glow.disc(buffer, pose, axes, at.x, at.y, at.z, 1.45F * grow, Glow.withAlpha(ScarletPalette.SCARLET, fade * 0.45F));
                    Glow.disc(buffer, pose, axes, at.x, at.y, at.z, 0.75F * grow, Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, fade * 0.8F));
                    Glow.disc(buffer, pose, axes, at.x, at.y, at.z, 0.36F * grow, Glow.withAlpha(ScarletPalette.CORE, fade));
                }
                Vector3f u;
                Vector3f v;
                if (impact.hitEntity()) {
                    u = axes.right();
                    v = axes.up();
                } else {
                    Vector3f[] plane = Glow.planeAxes(normal);
                    u = plane[0];
                    v = plane[1];
                }
                Vector3f lifted = new Vector3f(normal).mul(0.06F).add(at);
                shockRing(buffer, pose, lifted, u, v, t / 9.0F, 0.2F, 2.1F, 0.34F, ScarletPalette.SCARLET);
                shockRing(buffer, pose, lifted, u, v, (t - 2.0F) / 8.0F, 0.1F, 1.3F, 0.2F, ScarletPalette.BRIGHT_SCARLET);
            }
            for (Release release : releases) {
                float t = (float) (now - release.start());
                if (t < 0.0F || t > RELEASE_TICKS) {
                    continue;
                }
                float fade = 1.0F - Ease.outQuad(t / RELEASE_TICKS);
                Vector3f at = release.position().subtract(camera).toVector3f();
                Glow.disc(buffer, pose, axes, at.x, at.y, at.z, 0.55F, Glow.withAlpha(ScarletPalette.SCARLET, fade * 0.5F));
                Glow.disc(buffer, pose, axes, at.x, at.y, at.z, 0.26F, Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, fade * 0.85F));
                Glow.disc(buffer, pose, axes, at.x, at.y, at.z, 0.12F, Glow.withAlpha(ScarletPalette.CORE, fade));
                Vector3f[] plane = Glow.planeAxes(release.forward().toVector3f());
                shockRing(buffer, pose, at, plane[0], plane[1], t / RELEASE_TICKS, 0.12F, 0.6F, 0.12F, ScarletPalette.BRIGHT_SCARLET);
            }
        });
    }

    /**
     * A ring racing outward from {@code from} to {@code to}, thinning and fading as {@code progress} goes 0 to 1.
     */
    private static void shockRing(VertexConsumer buffer, PoseStack.Pose pose, Vector3f at, Vector3f u, Vector3f v, float progress, float from,
                                  float to, float thickness, int rgb) {
        if (progress < 0.0F || progress >= 1.0F) {
            return;
        }
        float radius = from + (to - from) * Ease.outCubic(progress);
        float width = thickness * (1.0F - progress) + 0.04F;
        int color = Glow.withAlpha(rgb, (float) Math.pow(1.0F - progress, 1.5) * 0.8F);
        Glow.ring(buffer, pose, at.x, at.y, at.z, u, v, radius, width, color, 28);
    }

    private static Vec3 randomUnit(RandomSource random) {
        return new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
    }

    private static float partialTick() {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    private record Impact(Vec3 position, Vec3 normal, Vec3 direction, boolean hitEntity, double start) {
    }

    private record Release(Vec3 position, Vec3 forward, double start) {
    }
}
