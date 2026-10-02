package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
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
 * World-space glowing sparks: simulated on the client tick, drawn every frame with interpolation. The particle budget
 * follows the effects quality setting.
 */
public final class ScarletFx {

    private static final List<Spark> SPARKS = new ArrayList<>();
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
            SPARKS.add(new Spark(position, velocity, life, size, coreColor, haloColor, gravity, drag, RANDOM.nextFloat() * 100.0F));
        }
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null) {
            SPARKS.clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        Iterator<Spark> iterator = SPARKS.iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().tick()) {
                iterator.remove();
            }
        }
    }

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack) {
        if (SPARKS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        int count = SPARKS.size();
        float[] values = new float[count * 5];
        int[] colors = new int[count * 2];
        for (int i = 0; i < count; i++) {
            Spark spark = SPARKS.get(i);
            values[i * 5] = (float) (spark.lerpX(partialTick) - camera.x);
            values[i * 5 + 1] = (float) (spark.lerpY(partialTick) - camera.y);
            values[i * 5 + 2] = (float) (spark.lerpZ(partialTick) - camera.z);
            values[i * 5 + 3] = spark.size;
            values[i * 5 + 4] = spark.alpha(partialTick);
            colors[i * 2] = spark.coreColor;
            colors[i * 2 + 1] = spark.haloColor;
        }
        GlowPass.submit(collector, poseStack, (pose, buffer) -> {
            Glow.Billboard axes = Glow.billboard(pose);
            for (int i = 0; i < count; i++) {
                Glow.spark(buffer, pose, axes, values[i * 5], values[i * 5 + 1], values[i * 5 + 2], values[i * 5 + 3],
                        colors[i * 2], colors[i * 2 + 1], values[i * 5 + 4]);
            }
        });
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
        final float seed;

        Spark(Vec3 position, Vec3 velocity, int life, float size, int coreColor, int haloColor, float gravity, float drag, float seed) {
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

        float alpha(float partialTick) {
            float t = (age + partialTick) / life;
            float fadeIn = Math.min(1.0F, (age + partialTick) / 2.0F);
            float flicker = 0.75F + 0.25F * (float) Math.sin((age + partialTick) * 1.7F + seed);
            return fadeIn * (1.0F - t * t) * flicker;
        }
    }
}
