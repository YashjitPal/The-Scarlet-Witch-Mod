package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.hex.town.TownPlan;
import com.yashjit.scarlet.network.TownBuildPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A Hex's town building itself, as everyone nearby sees it: scarlet light races around the edge of a lot, then a
 * glowing outline climbs with the house as it rises, shedding sparks, while the frame knocks and the walls settle
 * into place.
 */
public final class TownFx {

    private static final float TRACE_TICKS = 12.0F;
    private static final float TRACE_FADE = 40.0F;
    private static final float LIFT_FROM = 8.0F;
    private static final float AFTERGLOW = 18.0F;
    private static final Vector3f FLAT_U = new Vector3f(1, 0, 0);
    private static final Vector3f FLAT_V = new Vector3f(0, 0, 1);

    private static final List<TownBuildPayload> BUILDS = new ArrayList<>();

    private TownFx() {
    }

    public static void onBuild(TownBuildPayload payload) {
        BUILDS.add(payload);
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            BUILDS.clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        long now = level.getGameTime();
        BUILDS.removeIf(build -> now - build.start() > build.duration() + AFTERGLOW + 2);
        RandomSource random = ScarletFx.random();
        float density = ScarletFx.density();
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        for (TownBuildPayload build : BUILDS) {
            float t = now - build.start();
            boolean street = TownPlan.Kind.values()[Math.clamp(build.kind(), 0, TownPlan.Kind.values().length - 1)].isStreet();
            if (camera.distanceToSqr(center(build)) > 128 * 128) {
                continue;
            }
            float rise = rise(build, t);
            double y = build.ground() + 1.0 + rise * build.height();
            // sparks shed from the edge of the rising outline
            int sparks = Math.round((street ? 1.0F : 4.0F) * density);
            if (t >= 0 && t < build.duration()) {
                for (int i = 0; i < sparks; i++) {
                    Vec3 at = edgePoint(build, random.nextFloat(), y);
                    ScarletFx.spark(at, new Vec3(random.nextGaussian() * 0.01, 0.02 + random.nextDouble() * 0.03, random.nextGaussian() * 0.01),
                            10 + random.nextInt(10), 0.03F, random.nextFloat() < 0.3F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET,
                            ScarletPalette.SCARLET, -0.001F, 0.92F);
                }
                if (!street && random.nextFloat() < 0.5F * density) {
                    double x = build.minX() + random.nextDouble() * (build.maxX() - build.minX() + 1);
                    double z = build.minZ() + random.nextDouble() * (build.maxZ() - build.minZ() + 1);
                    ChaosDust.spawn(new Vec3(x, y, z), new Vec3(0, -0.01, 0));
                }
            }
            // the frame knocks together and the walls settle in as it rises
            if (!street && t > LIFT_FROM && t < build.duration() && (now + build.minX()) % 5 == 0) {
                Vec3 at = edgePoint(build, random.nextFloat(), y);
                level.playLocalSound(at.x, at.y, at.z, random.nextBoolean() ? SoundEvents.WOOD_PLACE : SoundEvents.STONE_PLACE, SoundSource.BLOCKS,
                        0.35F, 0.8F + random.nextFloat() * 0.4F, false);
            }
            if (!street && t == (long) build.duration()) {
                for (int i = 0; i < Math.round(30 * density); i++) {
                    Vec3 at = edgePoint(build, random.nextFloat(), build.ground() + 1.0 + random.nextDouble() * build.height());
                    ScarletFx.spark(at, new Vec3(random.nextGaussian() * 0.02, 0.03 + random.nextDouble() * 0.04, random.nextGaussian() * 0.02),
                            14 + random.nextInt(12), 0.035F, ScarletPalette.CORE, ScarletPalette.BRIGHT_SCARLET, -0.0015F, 0.93F);
                }
            }
        }
    }

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack) {
        if (BUILDS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        double now = level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        List<TownBuildPayload> builds = List.copyOf(BUILDS);
        GlowPass.submit(collector, poseStack, (pose, buffer) -> {
            for (TownBuildPayload build : builds) {
                draw(buffer, pose, build, (float) (now - build.start()), camera);
            }
        });
    }

    private static void draw(VertexConsumer buffer, PoseStack.Pose pose, TownBuildPayload build, float t, Vec3 camera) {
        if (t < 0.0F || t > build.duration() + AFTERGLOW) {
            return;
        }
        boolean street = TownPlan.Kind.values()[Math.clamp(build.kind(), 0, TownPlan.Kind.values().length - 1)].isStreet();
        float x0 = (float) (build.minX() - camera.x);
        float x1 = (float) (build.maxX() + 1 - camera.x);
        float z0 = (float) (build.minZ() - camera.z);
        float z1 = (float) (build.maxZ() + 1 - camera.z);
        // the trace: light running around the lot's edge on the ground, then fading
        float traced = Ease.outCubic(Ease.clamp01(t / TRACE_TICKS));
        float traceFade = 1.0F - Ease.clamp01((t - TRACE_TICKS) / (TRACE_FADE - TRACE_TICKS));
        if (traceFade > 0.0F) {
            float ground = (float) (build.ground() + 1.03 - camera.y);
            int color = Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.85F * traceFade);
            outline(buffer, pose, x0, z0, x1, z1, ground, traced, street ? 0.09F : 0.14F, color);
            outline(buffer, pose, x0, z0, x1, z1, ground, traced, street ? 0.03F : 0.05F, Glow.withAlpha(ScarletPalette.CORE, 0.7F * traceFade));
        }
        if (street) {
            return;
        }
        // the rising outline, climbing with the walls and the roof
        float rise = rise(build, t);
        float lift = Ease.clamp01((t - LIFT_FROM) / 6.0F) * (1.0F - Ease.clamp01((t - build.duration()) / AFTERGLOW));
        if (lift > 0.0F) {
            float y = (float) (build.ground() + 1.0 + rise * build.height() - camera.y);
            outline(buffer, pose, x0, z0, x1, z1, y, 1.0F, 0.12F, Glow.withAlpha(ScarletPalette.SCARLET, 0.6F * lift));
            outline(buffer, pose, x0, z0, x1, z1, y, 1.0F, 0.04F, Glow.withAlpha(ScarletPalette.CORE, 0.45F * lift));
            Glow.planeDisc(buffer, pose, (x0 + x1) / 2, y, (z0 + z1) / 2, FLAT_U, FLAT_V, Math.max(x1 - x0, z1 - z0) * 0.55F,
                    Glow.withAlpha(ScarletPalette.SCARLET, 0.06F * lift), Glow.withAlpha(ScarletPalette.SCARLET, 0.0F), 24);
        }
    }

    /**
     * How far up the build the outline has climbed: from the ground to the top over the build.
     */
    private static float rise(TownBuildPayload build, float t) {
        return Ease.inOutCubic(Ease.clamp01((t - LIFT_FROM) / Math.max(1.0F, build.duration() - LIFT_FROM)));
    }

    /**
     * A rectangle's edge in light, drawn as far around as {@code amount} reaches, starting from its first corner.
     */
    private static void outline(VertexConsumer buffer, PoseStack.Pose pose, float x0, float z0, float x1, float z1, float y, float amount,
                                float width, int color) {
        float w = x1 - x0;
        float d = z1 - z0;
        float perimeter = 2 * (w + d);
        float left = perimeter * amount;
        float[][] corners = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}, {x0, z0}};
        for (int i = 0; i < 4 && left > 0.0F; i++) {
            float[] a = corners[i];
            float[] b = corners[i + 1];
            float length = Math.abs(b[0] - a[0]) + Math.abs(b[1] - a[1]);
            float k = Math.min(1.0F, left / length);
            Glow.planeLine(buffer, pose, 0, y, 0, FLAT_U, FLAT_V, a[0], a[1], a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, width, color);
            left -= length;
        }
    }

    private static Vec3 edgePoint(TownBuildPayload build, float along, double y) {
        double w = build.maxX() + 1 - build.minX();
        double d = build.maxZ() + 1 - build.minZ();
        double s = along * 2 * (w + d);
        if (s < w) {
            return new Vec3(build.minX() + s, y, build.minZ());
        }
        s -= w;
        if (s < d) {
            return new Vec3(build.maxX() + 1, y, build.minZ() + s);
        }
        s -= d;
        if (s < w) {
            return new Vec3(build.maxX() + 1 - s, y, build.maxZ() + 1);
        }
        s -= w;
        return new Vec3(build.minX(), y, build.maxZ() + 1 - s);
    }

    private static Vec3 center(TownBuildPayload build) {
        return new Vec3((build.minX() + build.maxX() + 1) / 2.0, build.ground() + 1, (build.minZ() + build.maxZ() + 1) / 2.0);
    }
}
