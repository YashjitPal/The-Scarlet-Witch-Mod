package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.client.render.PixelSprite;
import com.yashjit.scarlet.client.render.Pixels;
import com.yashjit.scarlet.config.ScarletClientConfig;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * World-space sparks, and the black smoke that rises off a corrupted caster's magic: simulated on the client tick,
 * drawn every frame with interpolation. A spark is a pixel or two of magic, starting at its color and stepping down the
 * ramp as it dies, now and then winking a step darker; smoke is a dithered puff of near-black pixels. Each keeps the
 * darkness it was spawned with. The particle budget follows the effects quality setting.
 */
public final class ScarletFx {

    /** Sparks nearer the eyes than this are not drawn, and up to the second distance only a pixel across. */
    private static final float NEAR = 0.9F;
    private static final float SMALL = 2.5F;
    private static final List<Spark> SPARKS = new ArrayList<>();
    private static final List<Spark> SMOKE = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private ScarletFx() {
    }

    public static RandomSource random() {
        return RANDOM;
    }

    /**
     * Whether the view is through this player's own eyes, where their body is not drawn.
     */
    public static boolean isFirstPersonViewOf(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        return player == minecraft.player && minecraft.getCameraEntity() == player && minecraft.options.getCameraType().isFirstPerson();
    }

    /**
     * Multiplier for how many particles emitters spawn.
     */
    public static float density() {
        return switch (ScarletClientConfig.get().effectsQuality) {
            case LOW -> 0.35F;
            case MEDIUM -> 0.65F;
            case HIGH -> 1.0F;
            case ULTRA -> 1.5F;
        };
    }

    private static int budget() {
        return switch (ScarletClientConfig.get().effectsQuality) {
            case LOW -> 250;
            case MEDIUM -> 700;
            case HIGH -> 1400;
            case ULTRA -> 2600;
        };
    }

    /**
     * @param gravity blocks per tick squared, negative to rise
     * @param drag    velocity kept each tick
     */
    public static void spark(Vec3 position, Vec3 velocity, int life, float size, int coreColor, int haloColor, float gravity, float drag) {
        if (SPARKS.size() < budget()) {
            SPARKS.add(new Spark(position, velocity, life, size, coreColor, haloColor, gravity, drag, RANDOM.nextFloat() * 100.0F, Glow.darkness()));
        }
    }

    /**
     * A puff of black smoke that swells as it rises and thins away.
     *
     * @param density how dark it is at its thickest, 0 to 1
     */
    public static void smoke(Vec3 position, Vec3 velocity, int life, float size, float density) {
        if (SMOKE.size() < budget() / 4) {
            SMOKE.add(new Spark(position, velocity, life, size, 0, 0, -0.0015F, 0.9F, density, 0.0F));
        }
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null) {
            SPARKS.clear();
            SMOKE.clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        for (List<Spark> list : List.of(SPARKS, SMOKE)) {
            Iterator<Spark> iterator = list.iterator();
            while (iterator.hasNext()) {
                if (!iterator.next().tick()) {
                    iterator.remove();
                }
            }
        }
    }

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        submitSmoke(collector, poseStack, camera, partialTick);
        if (SPARKS.isEmpty()) {
            return;
        }
        int count = SPARKS.size();
        float[] values = new float[count * 5];
        int[] colors = new int[count];
        for (int i = 0; i < count; i++) {
            Spark spark = SPARKS.get(i);
            float x = (float) (spark.lerpX(partialTick) - camera.x);
            float y = (float) (spark.lerpY(partialTick) - camera.y);
            float z = (float) (spark.lerpZ(partialTick) - camera.z);
            float distance2 = x * x + y * y + z * z;
            values[i * 5] = x;
            values[i * 5 + 1] = y;
            values[i * 5 + 2] = z;
            // right at the eyes a pixel would be a wall of color: there it is not drawn, and a little further a single one
            values[i * 5 + 3] = distance2 < NEAR * NEAR ? 0.0F : spark.size >= 0.03F && distance2 > SMALL * SMALL ? Pixels.SIZE : Pixels.SIZE * 0.5F;
            values[i * 5 + 4] = spark.darkness;
            colors[i] = spark.color(partialTick);
        }
        GlowPass.submitPixels(collector, poseStack, (pose, buffer) -> {
            Glow.Billboard axes = Glow.billboard(pose);
            float before = Glow.darkness();
            for (int i = 0; i < count; i++) {
                if (values[i * 5 + 3] <= 0.0F) {
                    continue;
                }
                Glow.darken(values[i * 5 + 4]);
                Pixels.square(buffer, pose, axes.right(), axes.up(), values[i * 5], values[i * 5 + 1], values[i * 5 + 2], values[i * 5 + 3],
                        colors[i]);
            }
            Glow.darken(before);
        });
    }

    private static void submitSmoke(SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera, float partialTick) {
        if (SMOKE.isEmpty()) {
            return;
        }
        int count = SMOKE.size();
        float[] values = new float[count * 5];
        for (int i = 0; i < count; i++) {
            Spark puff = SMOKE.get(i);
            float t = Math.min(1.0F, (puff.age + partialTick) / puff.life);
            values[i * 5] = (float) (puff.lerpX(partialTick) - camera.x);
            values[i * 5 + 1] = (float) (puff.lerpY(partialTick) - camera.y);
            values[i * 5 + 2] = (float) (puff.lerpZ(partialTick) - camera.z);
            values[i * 5 + 3] = puff.size * (1.0F + 1.6F * t);
            // thickens quickly, holds, then thins away as it swells
            values[i * 5 + 4] = puff.seed * Math.min(1.0F, t * 5.0F) * (1.0F - t * t);
        }
        GlowPass.submitPixels(collector, poseStack, (pose, buffer) -> {
            for (int i = 0; i < count; i++) {
                puff(buffer, pose, values[i * 5], values[i * 5 + 1], values[i * 5 + 2], values[i * 5 + 3], values[i * 5 + 4], i);
            }
        });
    }

    /**
     * A puff of smoke: a round, dithered patch of near-black pixels, densest in the middle.
     */
    private static void puff(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float radius, float density, int seed) {
        int reach = Math.max(1, Math.round(radius / Pixels.SIZE));
        PixelSprite sprite = new PixelSprite(pose, x, y, z);
        for (int i = -reach; i < reach; i++) {
            for (int j = -reach; j < reach; j++) {
                float r = (float) Math.sqrt((i + 0.5F) * (i + 0.5F) + (j + 0.5F) * (j + 0.5F)) / reach;
                float level = density * (1.0F - r * r) * 1.6F;
                if (r < 1.0F && Pixels.shows(level, i + seed, j + seed * 3)) {
                    int color = level > 0.9F ? ScarletPalette.VOID : level > 0.5F ? ScarletPalette.SHADOW : ScarletPalette.ABYSS;
                    sprite.cell(i, j, Glow.withAlpha(color, 0.75F), 0);
                }
            }
        }
        sprite.draw(buffer);
    }

    private static final class Spark {
        double x, y, z;
        double xo, yo, zo;
        double vx, vy, vz;
        int age;
        final int life;
        final float size;
        final int coreColor;
        final int haloColor;
        final float gravity;
        final float drag;
        /** For a spark, the phase of its flicker; for smoke, how dark it is. */
        final float seed;
        final float darkness;
        /** The step of the ramp it starts at. */
        final int start;

        Spark(Vec3 position, Vec3 velocity, int life, float size, int coreColor, int haloColor, float gravity, float drag, float seed, float darkness) {
            this.x = this.xo = position.x;
            this.y = this.yo = position.y;
            this.z = this.zo = position.z;
            this.vx = velocity.x;
            this.vy = velocity.y;
            this.vz = velocity.z;
            this.life = life;
            this.size = size;
            this.coreColor = coreColor;
            this.haloColor = haloColor;
            this.gravity = gravity;
            this.drag = drag;
            this.seed = seed;
            this.darkness = darkness;
            this.start = Pixels.stepOf(coreColor);
        }

        boolean tick() {
            xo = x;
            yo = y;
            zo = z;
            x += vx;
            y += vy;
            z += vz;
            vx *= drag;
            vy = vy * drag - gravity;
            vz *= drag;
            return ++age < life;
        }

        double lerpX(float t) {
            return xo + (x - xo) * t;
        }

        double lerpY(float t) {
            return yo + (y - yo) * t;
        }

        double lerpZ(float t) {
            return zo + (z - zo) * t;
        }

        /**
         * Its color now: its own at first, a step down the ramp past half its life and another near the end, winking a
         * step darker now and then, and thinning out as it goes.
         */
        int color(float partialTick) {
            float t = (age + partialTick) / life;
            int step = start + (t < 0.45F ? 0 : t < 0.8F ? 1 : 2);
            if (Pixels.hash(age, 0, Float.floatToIntBits(seed), 3) < 0.15F) {
                step++;
            }
            return Pixels.ramp(Math.min(step, Pixels.WINE), t > 0.9F ? 0.5F : 1.0F);
        }
    }
}
