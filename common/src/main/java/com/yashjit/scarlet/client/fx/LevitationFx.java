package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.CastPoses;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.anim.PoseBlends;
import com.yashjit.scarlet.client.darkhold.CorruptionClient;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.network.MagicEventPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Levitation as everyone sees it.
 *
 * <ul>
 *     <li>A pad of light under the feet, ringed with slowly turning marks.</li>
 *     <li>Threads of energy spiraling up around the legs, and sparks circling the feet and falling away.</li>
 *     <li>A shock ring across the ground at lift-off, and a softer one at touch-down.</li>
 * </ul>
 */
public final class LevitationFx {

    private static final float LIFT_OFF_TICKS = 13.0F;
    private static final float TOUCH_DOWN_TICKS = 10.0F;
    private static final float TAU = (float) (Math.PI * 2);
    private static final Vector3f FLAT_U = new Vector3f(1, 0, 0);
    private static final Vector3f FLAT_V = new Vector3f(0, 0, 1);

    private static final List<Burst> BURSTS = new ArrayList<>();

    private LevitationFx() {
    }

    public static void onEvent(MagicEventPayload payload) {
        boolean liftOff = payload.kind() == MagicEventPayload.LIFT_OFF;
        if (!liftOff && payload.kind() != MagicEventPayload.TOUCH_DOWN) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        double now = minecraft.level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 at = payload.position();
        float darkness = CorruptionClient.darkness(minecraft.level.getEntity(payload.entityId()));
        BURSTS.add(new Burst(at, liftOff, now, darkness));
        try (Glow.Darkening ignored = Glow.darkening(darkness)) {
            burstSparks(at, liftOff);
        }
    }

    private static void burstSparks(Vec3 at, boolean liftOff) {
        RandomSource random = ScarletFx.random();
        float density = ScarletFx.density();
        int sparks = Math.round((liftOff ? 28 : 14) * density);
        for (int i = 0; i < sparks; i++) {
            float angle = random.nextFloat() * TAU;
            double speed = (liftOff ? 0.12 : 0.08) + random.nextDouble() * 0.12;
            Vec3 velocity = new Vec3(Mth.cos(angle) * speed, (liftOff ? 0.04 : 0.015) + random.nextDouble() * 0.05, Mth.sin(angle) * speed);
            ScarletFx.spark(at.add(Mth.cos(angle) * 0.3, 0.08, Mth.sin(angle) * 0.3), velocity, 7 + random.nextInt(8), 0.026F,
                    random.nextFloat() < 0.35F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET, 0.006F, 0.86F);
        }
        for (int i = 0, n = Math.round((liftOff ? 16 : 9) * density); i < n; i++) {
            float angle = random.nextFloat() * TAU;
            ChaosDust.spawn(at.add(Mth.cos(angle) * 0.4, 0.05, Mth.sin(angle) * 0.4),
                    new Vec3(Mth.cos(angle) * 0.05, 0.03 + random.nextDouble() * 0.03, Mth.sin(angle) * 0.05));
        }
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            BURSTS.clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        double now = level.getGameTime();
        BURSTS.removeIf(burst -> now - burst.start() > LIFT_OFF_TICKS + 1);
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        RandomSource random = ScarletFx.random();
        float density = ScarletFx.density();
        for (Player player : level.players()) {
            if (player.isInvisible() || player.distanceToSqr(camera) > 64 * 64 || PoseBlends.of(player).levitate < 0.3F) {
                continue;
            }
            Vec3 feet = feet(player, 1.0F);
            float time = player.tickCount;
            float darkness = CorruptionClient.darkness(player);
            try (Glow.Darkening ignored = Glow.darkening(darkness)) {
                for (int i = 0; i < 2; i++) {
                    if (random.nextFloat() >= density) {
                        continue;
                    }
                    float angle = time * 0.35F + i * (float) Math.PI;
                    Vec3 out = new Vec3(Mth.cos(angle), 0, Mth.sin(angle));
                    Vec3 tangent = new Vec3(-Mth.sin(angle), 0, Mth.cos(angle));
                    ScarletFx.spark(feet.add(out.scale(0.42)).add(0, 0.04 + random.nextDouble() * 0.1, 0),
                            tangent.scale(0.03).add(0, -0.012, 0), 10 + random.nextInt(7), 0.022F,
                            random.nextFloat() < 0.3F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET, 0.002F, 0.9F);
                }
                if (random.nextFloat() < 0.5F * density) {
                    ChaosDust.spawn(feet.add(random.nextGaussian() * 0.18, -0.05, random.nextGaussian() * 0.18), new Vec3(0, -0.02, 0));
                }
            }
            if (random.nextFloat() < 0.6F * darkness * density) {
                // black smoke trailing down off the pad
                ScarletFx.smoke(feet.add(random.nextGaussian() * 0.25, -0.12, random.nextGaussian() * 0.25), new Vec3(0, -0.006, 0),
                        22 + random.nextInt(12), 0.1F, 0.3F + 0.35F * darkness);
            }
        }
    }

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + partialTick;
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        List<Hover> hovers = new ArrayList<>();
        for (Player player : level.players()) {
            if (player.isInvisible() || player.distanceToSqr(camera) > 96 * 96) {
                continue;
            }
            float levitate = PoseBlends.of(player).levitate;
            if (levitate > 0.01F) {
                hovers.add(new Hover(feet(player, partialTick).subtract(camera).toVector3f(), levitate, player.tickCount + partialTick,
                        (player.getId() * 0.618F) % 1.0F * TAU, CorruptionClient.darkness(player)));
            }
        }
        List<Burst> bursts = List.copyOf(BURSTS);
        if (hovers.isEmpty() && bursts.isEmpty()) {
            return;
        }
        GlowPass.submitTint(collector, poseStack, (pose, buffer) -> {
            float before = Glow.darkness();
            for (Hover hover : hovers) {
                Glow.darken(hover.darkness());
                Vector3f c = new Vector3f(hover.feet()).add(0, -0.1F, 0);
                float fade = Ease.outCubic(Ease.clamp01(hover.levitate()));
                Glow.planeDisc(buffer, pose, c.x, c.y, c.z, FLAT_U, FLAT_V, 0.62F, GlowPass.tint(ScarletPalette.GLASS, 0.5F * fade),
                        GlowPass.tint(ScarletPalette.GLASS, 0.0F), 28);
            }
            for (Burst burst : bursts) {
                float duration = burst.liftOff() ? LIFT_OFF_TICKS : TOUCH_DOWN_TICKS;
                float t = (float) (now - burst.start());
                if (t < 0.0F || t > duration) {
                    continue;
                }
                Glow.darken(burst.darkness());
                float k = t / duration;
                Vector3f c = burst.position().subtract(camera).toVector3f().add(0, 0.04F, 0);
                float reach = 0.25F + ((burst.liftOff() ? 2.4F : 1.5F) - 0.25F) * Ease.outCubic(k);
                Glow.planeDisc(buffer, pose, c.x, c.y, c.z, FLAT_U, FLAT_V, reach, GlowPass.tint(ScarletPalette.GLASS, 0.0F),
                        GlowPass.tint(ScarletPalette.GLASS, 0.55F * (float) Math.pow(1.0F - k, 1.5)), 32);
            }
            Glow.darken(before);
        });
        GlowPass.submit(collector, poseStack, (pose, buffer) -> {
            Glow.Billboard axes = Glow.billboard(pose);
            float before = Glow.darkness();
            for (Hover hover : hovers) {
                Glow.darken(hover.darkness());
                drawHover(buffer, pose, axes, hover);
            }
            for (Burst burst : bursts) {
                Glow.darken(burst.darkness());
                drawBurst(buffer, pose, burst, now, camera);
            }
            Glow.darken(before);
        });
    }

    private static void drawHover(VertexConsumer buffer, PoseStack.Pose pose, Glow.Billboard axes, Hover hover) {
        float fade = Ease.outCubic(Ease.clamp01(hover.levitate()));
        float time = hover.age();
        Vector3f c = new Vector3f(hover.feet()).add(0, -0.1F, 0);
        float breathe = Mth.sin(time * 0.2F);
        Glow.planeDisc(buffer, pose, c.x, c.y, c.z, FLAT_U, FLAT_V, 0.55F, Glow.withAlpha(ScarletPalette.SCARLET, 0.12F * fade),
                Glow.withAlpha(ScarletPalette.SCARLET, 0.0F), 28);
        Glow.ring(buffer, pose, c.x, c.y, c.z, FLAT_U, FLAT_V, 0.48F + 0.04F * breathe, 0.12F, Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.45F * fade), 32);
        Glow.ring(buffer, pose, c.x, c.y, c.z, FLAT_U, FLAT_V, 0.28F, 0.05F, Glow.withAlpha(ScarletPalette.CORE, 0.16F * fade), 24);
        // six marks turning slowly around the pad
        int mark = Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.38F * fade);
        for (int i = 0; i < 6; i++) {
            float start = time * 0.05F + i * TAU / 6.0F + hover.seed();
            for (int j = 0; j < 3; j++) {
                float a0 = start + j * 0.16F;
                float a1 = a0 + 0.16F;
                Glow.planeLine(buffer, pose, c.x, c.y, c.z, FLAT_U, FLAT_V, Mth.cos(a0) * 0.64F, Mth.sin(a0) * 0.64F,
                        Mth.cos(a1) * 0.64F, Mth.sin(a1) * 0.64F, 0.045F, mark);
            }
        }
        // threads of energy winding up around the legs
        int count = 9;
        for (int i = 0; i < 3; i++) {
            Vector3f[] points = new Vector3f[count];
            float[] widths = new float[count];
            int[] colors = new int[count];
            for (int j = 0; j < count; j++) {
                float s = j / (float) (count - 1);
                float angle = time * 0.2F + i * TAU / 3.0F + s * 2.5F + hover.seed();
                float radius = 0.36F - 0.12F * s;
                points[j] = new Vector3f(c.x + Mth.cos(angle) * radius, c.y + 0.05F + s * 0.6F, c.z + Mth.sin(angle) * radius);
                float envelope = Mth.sin(s * (float) Math.PI);
                widths[j] = 0.015F + 0.045F * envelope;
                colors[j] = Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.3F * fade * envelope);
            }
            Glow.ribbon(buffer, pose, axes, points, widths, colors);
        }
    }

    private static void drawBurst(VertexConsumer buffer, PoseStack.Pose pose, Burst burst, double now, Vec3 camera) {
        float duration = burst.liftOff() ? LIFT_OFF_TICKS : TOUCH_DOWN_TICKS;
        float t = (float) (now - burst.start());
        if (t < 0.0F || t > duration) {
            return;
        }
        float k = t / duration;
        float fade = (float) Math.pow(1.0F - k, 1.5);
        Vector3f c = burst.position().subtract(camera).toVector3f().add(0, 0.05F, 0);
        float reach = burst.liftOff() ? 2.4F : 1.5F;
        Glow.planeDisc(buffer, pose, c.x, c.y, c.z, FLAT_U, FLAT_V, 1.1F * (1.0F - k * 0.5F), Glow.withAlpha(ScarletPalette.SCARLET, 0.28F * fade),
                Glow.withAlpha(ScarletPalette.SCARLET, 0.0F), 28);
        Glow.ring(buffer, pose, c.x, c.y, c.z, FLAT_U, FLAT_V, 0.25F + (reach - 0.25F) * Ease.outCubic(k), 0.32F * (1.0F - k) + 0.05F,
                Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.75F * fade), 40);
        if (burst.liftOff()) {
            float late = Ease.clamp01((t - 2.0F) / (duration - 2.0F));
            if (t > 2.0F && late < 1.0F) {
                Glow.ring(buffer, pose, c.x, c.y, c.z, FLAT_U, FLAT_V, 0.2F + 1.2F * Ease.outCubic(late), 0.18F * (1.0F - late) + 0.03F,
                        Glow.withAlpha(ScarletPalette.SCARLET, 0.6F * (float) Math.pow(1.0F - late, 1.5)), 32);
            }
        }
    }

    /**
     * Where the soles are, following the body as it bobs.
     */
    private static Vec3 feet(Player player, float partialTick) {
        float levitate = PoseBlends.of(player).levitate;
        double bob = CastPoses.bob(player.tickCount + partialTick, levitate);
        return player.getPosition(partialTick).add(0, bob, 0);
    }

    private record Hover(Vector3f feet, float levitate, float age, float seed, float darkness) {
    }

    private record Burst(Vec3 position, boolean liftOff, double start, float darkness) {
    }
}
