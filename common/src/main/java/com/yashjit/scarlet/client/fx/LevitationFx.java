package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.CastPoses;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.anim.PoseBlends;
import com.yashjit.scarlet.client.darkhold.CorruptionClient;
import com.yashjit.scarlet.client.render.FlatPixels;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.client.render.PixelSprite;
import com.yashjit.scarlet.client.render.Pixels;
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
 * Levitation as everyone sees it, as pixel art.
 *
 * <ul>
 *     <li>A pad of magic churning under the feet, ringed with slowly turning marks, lying on the world's own grid of
 *     texels.</li>
 *     <li>Strands of it winding up around the legs, and sparks circling the feet and falling away.</li>
 *     <li>A shock ring across the ground at lift-off, and a softer one at touch-down.</li>
 * </ul>
 */
public final class LevitationFx {

    private static final float LIFT_OFF_TICKS = 13.0F;
    private static final float TOUCH_DOWN_TICKS = 10.0F;
    private static final float TAU = (float) (Math.PI * 2);

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
        GlowPass.submitPixels(collector, poseStack, (pose, buffer) -> {
            float before = Glow.darkness();
            for (Hover hover : hovers) {
                Glow.darken(hover.darkness());
                drawHover(buffer, pose, hover, camera);
            }
            for (Burst burst : bursts) {
                Glow.darken(burst.darkness());
                drawBurst(buffer, pose, burst, now, camera);
            }
            Glow.darken(before);
        });
    }

    /**
     * A pad of magic churning under the feet on the world's own grid of texels, white-hot at its heart and licking out
     * at its rim, six marks turning slowly round it, and strands of it winding up round the legs.
     */
    private static void drawHover(VertexConsumer buffer, PoseStack.Pose pose, Hover hover, Vec3 camera) {
        float fade = Ease.outCubic(Ease.clamp01(hover.levitate()));
        float time = hover.age();
        int frame = (int) Math.floor(time);
        Vector3f c = new Vector3f(hover.feet()).add(0, -0.1F, 0);
        float rim = 0.46F + 0.04F * Mth.sin(time * 0.2F);
        float turn = time * 0.05F + hover.seed();
        FlatPixels.ring(buffer, pose, camera, c.x + camera.x, c.y + camera.y, c.z + camera.z, 0.0F, 0.7F, (i, k, r, theta) -> {
            if (r > 0.57F) {
                // the marks, short arcs of six turning round it
                float sector = (theta - turn) / (TAU / 6.0F);
                return r < 0.66F && sector - Mth.floor(sector) < 0.3F && Pixels.shows(fade, i, k) ? Pixels.opaque(Pixels.BRIGHT) : 0;
            }
            float lick = 0.09F * Pixels.noise(Mth.cos(theta) * 2.0F + frame * 0.4F, Mth.sin(theta) * 2.0F - frame * 0.3F, 62);
            if (r > rim + lick) {
                return 0;
            }
            float churn = Pixels.churn(i * 0.22F + frame * 0.18F, k * 0.22F - frame * 0.13F, 61);
            float heat = (1.0F - r / rim) * 0.75F + churn * 0.45F;
            if (!Pixels.shows(fade * (0.45F + heat), i, k)) {
                return 0;
            }
            int step = heat > 0.95F ? Pixels.HOT : heat > 0.75F ? Pixels.PINK : heat > 0.55F ? Pixels.BRIGHT : heat > 0.3F ? Pixels.SCARLET : Pixels.CRIMSON;
            return step >= Pixels.SCARLET ? Pixels.ramp(step, 0.75F) : Pixels.opaque(step);
        });
        PixelSprite sprite = PixelSprite.inWorld(pose, c.x, c.y + 0.35F, c.z);
        Wisps.helix(sprite, c, 0.36F, 0.24F, 0.05F, 0.65F, 3, 0.4F, 0.2F, time, fade, hover.seed(), 0);
        sprite.draw(buffer);
    }

    /**
     * A shock ring racing out across the ground at lift-off, a softer one at touch-down: white-hot at its leading edge
     * and cooling behind it, on the world's own grid of texels, breaking up as it fades.
     */
    private static void drawBurst(VertexConsumer buffer, PoseStack.Pose pose, Burst burst, double now, Vec3 camera) {
        float duration = burst.liftOff() ? LIFT_OFF_TICKS : TOUCH_DOWN_TICKS;
        float t = (float) (now - burst.start());
        if (t < 0.0F || t > duration) {
            return;
        }
        float k = t / duration;
        Vec3 at = burst.position().add(0.0, 0.03, 0.0);
        float reach = burst.liftOff() ? 2.4F : 1.5F;
        shock(buffer, pose, camera, at, 0.25F + (reach - 0.25F) * Ease.outCubic(k), 0.32F * (1.0F - k) + 0.08F, (float) Math.pow(1.0F - k, 1.5));
        if (burst.liftOff()) {
            float late = Ease.clamp01((t - 2.0F) / (duration - 2.0F));
            if (t > 2.0F && late < 1.0F) {
                shock(buffer, pose, camera, at, 0.2F + 1.2F * Ease.outCubic(late), 0.18F * (1.0F - late) + 0.06F, 0.7F * (float) Math.pow(1.0F - late, 1.5));
            }
        }
    }

    private static void shock(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, Vec3 at, float front, float depth, float fade) {
        FlatPixels.ring(buffer, pose, camera, at.x, at.y, at.z, Math.max(0.0F, front - depth), front, (i, k, r, theta) -> {
            float into = (front - r) / depth;
            if (!Pixels.shows(fade * (1.1F - into), i, k)) {
                return 0;
            }
            return Pixels.opaque(into < 0.2F ? Pixels.HOT : into < 0.45F ? Pixels.PINK : into < 0.7F ? Pixels.BRIGHT : Pixels.SCARLET);
        });
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
