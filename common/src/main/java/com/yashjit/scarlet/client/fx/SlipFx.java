package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The red magic of an unsteady Hex, taking hold of whatever slips. When a house jumps into another era or flickers out
 * of existence, or a townsperson's clothes or disguise slip, the same scarlet magic that made them seizes them for a
 * moment: a scarlet line runs round their foot and sweeps up over them, red bars tear across them and red static
 * crawls over them while they flicker, and sparks drift off them as they settle back.
 */
public final class SlipFx {

    /** How long a person's slip takes hold of them, in ticks, and how long its sparks take to settle after. */
    private static final int BODY_TICKS = 8;
    private static final float SETTLE = 10.0F;
    /** How long the scarlet line takes to sweep up over what slipped. */
    private static final float SWEEP_TICKS = 6.0F;
    private static final Vector3f FLAT_U = new Vector3f(1, 0, 0);
    private static final Vector3f FLAT_V = new Vector3f(0, 0, 1);

    private static final List<Wave> WAVES = new ArrayList<>();
    private static @Nullable ClientLevel seenLevel;

    private SlipFx() {
    }

    /**
     * A part of the town slipping, flickering as {@code pattern} has it: one bit a tick, set while it shows its other
     * self.
     */
    public static void part(AABB box, long start, int ticks, int pattern) {
        add(new Wave(-1, box, start, ticks, pattern, true));
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            Vec3 at = box.getCenter();
            minecraft.level.playLocalSound(at.x, box.minY + 1.0, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.9F, 1.5F, false);
            minecraft.level.playLocalSound(at.x, box.minY + 1.0, at.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 0.35F, 1.8F,
                    false);
        }
    }

    /**
     * Someone slipping: a townsperson, or a player in their era's clothes.
     */
    public static void body(Entity entity, long start) {
        for (Wave wave : WAVES) {
            if (wave.entity == entity.getId() && start - wave.start < BODY_TICKS) {
                return;
            }
        }
        add(new Wave(entity.getId(), entity.getBoundingBox(), start, BODY_TICKS, -1, false));
        entity.level().playLocalSound(entity.getX(), entity.getY() + 1.0, entity.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.5F,
                1.7F + entity.getRandom().nextFloat() * 0.2F, false);
    }

    private static void add(Wave wave) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != seenLevel) {
            seenLevel = minecraft.level;
            WAVES.clear();
        }
        WAVES.add(wave);
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level != seenLevel) {
            seenLevel = level;
            WAVES.clear();
        }
        if (level == null || minecraft.isPaused() || WAVES.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        WAVES.removeIf(wave -> now - wave.start > wave.ticks + SETTLE || wave.entity >= 0 && level.getEntity(wave.entity) == null);
        RandomSource random = ScarletFx.random();
        float density = ScarletFx.density();
        for (Wave wave : WAVES) {
            AABB box = wave.box(level, 1.0F);
            float t = now - wave.start;
            float hold = wave.hold(t);
            if (hold > 0.0F) {
                Glitch.crawl(new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.maxY, box.maxZ), hold * (wave.big ? 2.5F : 0.9F));
            }
            // sparks rising off it from its foot, thickest as it seizes and thinning as it settles
            float shed = t < wave.ticks ? 1.0F : 1.0F - (t - wave.ticks) / SETTLE;
            int sparks = Math.round((wave.big ? 5.0F : 1.5F) * shed * density);
            for (int i = 0; i < sparks; i++) {
                Vec3 at = edgePoint(box, random.nextFloat(), box.minY + random.nextDouble() * (box.maxY - box.minY) * (wave.big ? 0.6 : 0.9));
                int color = random.nextFloat() < 0.3F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET;
                ScarletFx.spark(at, new Vec3(random.nextGaussian() * 0.01, 0.025 + random.nextDouble() * 0.035, random.nextGaussian() * 0.01),
                        10 + random.nextInt(12), wave.big ? 0.04F : 0.025F, color, ScarletPalette.SCARLET, -0.0012F, 0.92F);
            }
            if (wave.big && t < wave.ticks && random.nextFloat() < 0.25F * density) {
                ChaosDust.spawn(new Vec3(box.minX + random.nextDouble() * (box.maxX - box.minX), box.maxY,
                        box.minZ + random.nextDouble() * (box.maxZ - box.minZ)), new Vec3(0.0, -0.015, 0.0));
            }
        }
    }

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || WAVES.isEmpty() || level != seenLevel) {
            return;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + partialTick;
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        List<Wave> waves = List.copyOf(WAVES);
        long frame = Glitch.frame(now);
        GlowPass.submit(collector, poseStack, (pose, buffer) -> {
            Glow.Billboard axes = Glow.billboard(pose);
            for (Wave wave : waves) {
                AABB box = wave.box(level, partialTick).move(-camera.x, -camera.y, -camera.z);
                float t = (float) (now - wave.start);
                draw(buffer, pose, axes, wave, box, t, frame);
            }
        });
    }

    private static void draw(VertexConsumer buffer, PoseStack.Pose pose, Glow.Billboard axes, Wave wave, AABB box, float t, long frame) {
        float x0 = (float) box.minX;
        float x1 = (float) box.maxX;
        float z0 = (float) box.minZ;
        float z1 = (float) box.maxZ;
        float height = (float) (box.maxY - box.minY);
        float fade = t < wave.ticks ? 1.0F : 1.0F - Ease.clamp01((t - wave.ticks) / SETTLE);
        if (fade <= 0.0F) {
            return;
        }
        // the line round its foot, racing all the way round as it seizes
        float traced = Ease.outCubic(Ease.clamp01(t / 4.0F));
        float foot = (float) box.minY + 0.04F;
        float line = wave.big ? 0.14F : 0.06F;
        TownFx.outline(buffer, pose, x0, z0, x1, z1, foot, traced, line, Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.85F * fade));
        TownFx.outline(buffer, pose, x0, z0, x1, z1, foot, traced, line * 0.35F, Glow.withAlpha(ScarletPalette.CORE, 0.75F * fade));
        // then sweeps up over it, and fades at the top
        float swept = Ease.inOutCubic(Ease.clamp01(t / SWEEP_TICKS));
        float sweepFade = (1.0F - Ease.clamp01((t - SWEEP_TICKS) / 4.0F)) * fade;
        if (sweepFade > 0.0F) {
            float y = (float) box.minY + swept * height;
            TownFx.outline(buffer, pose, x0, z0, x1, z1, y, 1.0F, line * 0.9F, Glow.withAlpha(ScarletPalette.SCARLET, 0.7F * sweepFade));
            TownFx.outline(buffer, pose, x0, z0, x1, z1, y, 1.0F, line * 0.3F, Glow.withAlpha(ScarletPalette.CORE, 0.6F * sweepFade));
            Glow.planeDisc(buffer, pose, (x0 + x1) / 2, y, (z0 + z1) / 2, FLAT_U, FLAT_V, Math.max(x1 - x0, z1 - z0) * 0.6F,
                    Glow.withAlpha(ScarletPalette.SCARLET, 0.1F * sweepFade), Glow.withAlpha(ScarletPalette.SCARLET, 0.0F), 20);
        }
        // the red glitch over all of it while it flickers
        float hold = wave.hold(t);
        if (hold > 0.0F) {
            Vector3f center = new Vector3f((x0 + x1) / 2, (float) (box.minY + height / 2), (z0 + z1) / 2);
            Glitch.draw(buffer, pose, axes, center, Math.max(x1 - x0, z1 - z0) * 1.1F, height * 1.05F, hold, frame,
                    wave.entity >= 0 ? wave.entity : (int) (wave.start * 31 + (long) box.minX));
        }
    }

    private static Vec3 edgePoint(AABB box, float along, double y) {
        double w = box.maxX - box.minX;
        double d = box.maxZ - box.minZ;
        double s = along * 2 * (w + d);
        if (s < w) {
            return new Vec3(box.minX + s, y, box.minZ);
        }
        s -= w;
        if (s < d) {
            return new Vec3(box.maxX, y, box.minZ + s);
        }
        s -= d;
        if (s < w) {
            return new Vec3(box.maxX - s, y, box.maxZ);
        }
        s -= w;
        return new Vec3(box.minX, y, box.maxZ - s);
    }

    /**
     * @param entity  who is slipping, or -1 for a part of the town
     * @param box     what of the town is slipping, or where the person was when they began to
     * @param ticks   how long it lasts before it settles back
     * @param pattern which ticks it shows its other self, a bit a tick; -1 for all of them
     */
    private record Wave(int entity, AABB box, long start, int ticks, int pattern, boolean big) {

        AABB box(ClientLevel level, float partialTick) {
            if (entity >= 0 && level.getEntity(entity) instanceof Entity alive) {
                Vec3 at = alive.getPosition(partialTick);
                double half = alive.getBbWidth() * 0.5 + 0.1;
                return new AABB(at.x - half, at.y, at.z - half, at.x + half, at.y + alive.getBbHeight() + 0.1, at.z + half);
            }
            return box;
        }

        /**
         * How hard the glitch has hold of it a moment into its slip: hard while it shows its other self, softer
         * between, and gone once it settles.
         */
        float hold(float t) {
            if (t < 0.0F || t >= ticks) {
                return 0.0F;
            }
            int tick = (int) t;
            boolean other = pattern == -1 || tick < 31 && (pattern >> tick & 1) != 0;
            float onset = Math.min(1.0F, (t + 1.0F) / 2.0F);
            return onset * (other ? 1.0F : 0.5F);
        }
    }
}
