package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.hex.LaughTrack;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.network.RewritePayload;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * What a Hex rewrites, seen being rewritten: it glitches red for a moment in a pop of scarlet sparks, and comes out
 * as what fits the show. Petals flutter down where an arrow was, a fireball bursts into fireworks in the era's colors, a
 * thrown potion turns to soap bubbles that drift off and pop, and lit TNT is a cake under a shower of confetti.
 */
public final class RewriteFx {

    /** Ticks the red glitch of a rewrite lasts. */
    private static final float FLASH_TICKS = 9.0F;
    private static final int MAX_BUBBLES = 160;

    /** Each era's fireworks: pastels, then silver, harvest gold, neon, the bright 2000s, and red, white and blue. */
    private static final int[][] FIREWORKS = {
            {0xF7C6D9, 0xBDE8D7, 0xFFF4D6},
            {0xFFFFFF, 0xC8C8C8, 0x8FA9C4},
            {0xF28C2C, 0xE0B53A, 0x9C5A2B},
            {0xFF3EA5, 0x2EE6F0, 0xB45FFF},
            {0x3E7BFA, 0xC0C8D8, 0x7FE36A},
            {0xE0143C, 0xFFFFFF, 0x3E6CF0}
    };
    private static final int[] CONFETTI = {0xFF3E6C, 0xFFD43B, 0x3EC6FF, 0x7FE36A, 0xB45FFF, 0xFF8C2C, 0xFFFFFF};

    private static final List<Flash> FLASHES = new ArrayList<>();
    private static final List<Bubble> BUBBLES = new ArrayList<>();
    private static @Nullable ClientLevel seenLevel;

    private RewriteFx() {
    }

    public static void receive(RewritePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        double now = level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        RandomSource random = ScarletFx.random();
        Vec3 at = payload.at();
        float size = switch (payload.kind()) {
            case RewritePayload.CAKE -> 1.2F;
            case RewritePayload.FIREWORK -> 1.0F;
            case RewritePayload.BUBBLES -> 0.7F;
            default -> 0.6F;
        };
        FLASHES.add(new Flash(at, now, size, random.nextInt()));
        Vec3 half = new Vec3(size * 0.5, size * 0.5, size * 0.5);
        Glitch.crawl(at.subtract(half), at.add(half), 1.4F);
        sparks(at, size, random);
        switch (payload.kind()) {
            case RewritePayload.FLOWER -> petals(minecraft, at, payload.variant(), random);
            case RewritePayload.FIREWORK -> level.createFireworks(at.x, at.y, at.z, 0.0, 0.0, 0.0, List.of(firework(payload)), true);
            case RewritePayload.BUBBLES -> bubbles(at, payload.motion(), payload.variant(), random);
            case RewritePayload.CAKE -> {
                confetti(minecraft, at, random);
                FireworkExplosion popper = new FireworkExplosion(FireworkExplosion.Shape.BURST, IntList.of(CONFETTI), IntList.of(), false, true);
                level.createFireworks(at.x, at.y + 1.4, at.z, 0.0, 0.0, 0.0, List.of(popper), true);
                LaughTrack.gag(minecraft, at);
            }
            default -> {
            }
        }
    }

    /**
     * The pop of a rewrite: scarlet sparks thrown out from where it happened, quickly slowing.
     */
    private static void sparks(Vec3 at, float size, RandomSource random) {
        for (int i = 0, n = Math.round(12 * size * ScarletFx.density()) + 3; i < n; i++) {
            Vec3 direction = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
            int color = random.nextFloat() < 0.3F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET;
            ScarletFx.spark(at, direction.scale(0.08 + random.nextFloat() * 0.1 * size), 7 + random.nextInt(7), 0.045F + random.nextFloat() * 0.03F,
                    color, ScarletPalette.SCARLET, 0.0F, 0.8F);
        }
    }

    /**
     * Petals where an arrow was, fluttering down around it.
     */
    private static void petals(Minecraft minecraft, Vec3 at, int color, RandomSource random) {
        // the leaf a petal is drawn with is mid-grey, which would darken it: lightened to make up for it
        int light = Glow.mix(color, 0xFFFFFF, 0.35F);
        for (int i = 0, n = Math.round(16 * ScarletFx.density()) + 4; i < n; i++) {
            Vec3 offset = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.6, random.nextGaussian()).scale(0.28);
            int shade = Glow.mix(light, random.nextBoolean() ? 0xFFFFFF : color, random.nextFloat() * 0.3F);
            flutter(minecraft, at.add(offset), shade, 50 + random.nextInt(40), 0.012, random);
        }
    }

    /**
     * Confetti showering down over a cake.
     */
    private static void confetti(Minecraft minecraft, Vec3 at, RandomSource random) {
        for (int i = 0, n = Math.round(44 * ScarletFx.density()) + 8; i < n; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double reach = Math.sqrt(random.nextDouble()) * 1.7;
            Vec3 from = at.add(Math.cos(angle) * reach, 1.0 + random.nextDouble() * 1.8, Math.sin(angle) * reach);
            flutter(minecraft, from, CONFETTI[random.nextInt(CONFETTI.length)], 70 + random.nextInt(50), 0.02, random);
        }
    }

    /**
     * A scrap of color falling like a leaf, drifting a little to the side.
     */
    private static void flutter(Minecraft minecraft, Vec3 at, int color, int life, double drift, RandomSource random) {
        Particle particle = minecraft.particleEngine.createParticle(ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, 0xFF000000 | color),
                at.x, at.y, at.z, 0.0, 0.0, 0.0);
        if (particle != null) {
            // it stops when it stops moving sideways, so it never drifts straight down
            double dx = (random.nextBoolean() ? 1.0 : -1.0) * (0.002 + random.nextDouble() * drift);
            double dz = (random.nextBoolean() ? 1.0 : -1.0) * (0.002 + random.nextDouble() * drift);
            particle.setParticleSpeed(dx, -0.01 - random.nextDouble() * 0.02, dz);
            particle.setLifetime(life);
        }
    }

    private static FireworkExplosion firework(RewritePayload payload) {
        int[] colors = FIREWORKS[Math.clamp(payload.era(), 0, FIREWORKS.length - 1)];
        FireworkExplosion.Shape[] shapes = FireworkExplosion.Shape.values();
        FireworkExplosion.Shape shape = shapes[Math.clamp(payload.variant(), 0, shapes.length - 1)];
        return new FireworkExplosion(shape, IntList.of(colors), IntList.of(colors[0]), shape == FireworkExplosion.Shape.LARGE_BALL, true);
    }

    /**
     * Soap bubbles, carried on a little the way the potion was going and rising slowly as they drift apart.
     */
    private static void bubbles(Vec3 at, Vec3 motion, int color, RandomSource random) {
        Vec3 carry = motion.scale(0.06);
        for (int i = 0, n = Math.round(14 * ScarletFx.density()) + 5; i < n && BUBBLES.size() < MAX_BUBBLES; i++) {
            Vec3 offset = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(0.25);
            Vec3 velocity = carry.add(random.nextGaussian() * 0.025, 0.01 + random.nextFloat() * 0.025, random.nextGaussian() * 0.025);
            float radius = 0.07F + random.nextFloat() * random.nextFloat() * 0.2F;
            BUBBLES.add(new Bubble(at.add(offset), velocity, radius, 40 + random.nextInt(70), color, random.nextFloat() * 100.0F));
        }
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level != seenLevel) {
            seenLevel = level;
            FLASHES.clear();
            BUBBLES.clear();
        }
        if (level == null || minecraft.isPaused()) {
            return;
        }
        double now = level.getGameTime();
        FLASHES.removeIf(flash -> now - flash.start() > FLASH_TICKS + 1);
        Iterator<Bubble> iterator = BUBBLES.iterator();
        while (iterator.hasNext()) {
            Bubble bubble = iterator.next();
            if (!bubble.tick()) {
                bubble.pop(level);
                iterator.remove();
            }
        }
    }

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack) {
        if (FLASHES.isEmpty() && BUBBLES.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = minecraft.level.getGameTime() + partialTick;
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        List<Flash> flashes = List.copyOf(FLASHES);
        int count = BUBBLES.size();
        float[] bubbles = new float[count * 5];
        int[] colors = new int[count];
        for (int i = 0; i < count; i++) {
            Bubble bubble = BUBBLES.get(i);
            float age = bubble.age + partialTick;
            bubbles[i * 5] = (float) (Mth.lerp(partialTick, bubble.xo, bubble.x) - camera.x);
            bubbles[i * 5 + 1] = (float) (Mth.lerp(partialTick, bubble.yo, bubble.y) - camera.y);
            bubbles[i * 5 + 2] = (float) (Mth.lerp(partialTick, bubble.zo, bubble.z) - camera.z);
            // blown out to size, then wobbling a little as it drifts
            bubbles[i * 5 + 3] = bubble.radius * Ease.outBack(Ease.clamp01(age / 5.0F)) * (1.0F + 0.05F * Mth.sin(age * 0.5F + bubble.seed));
            bubbles[i * 5 + 4] = age + bubble.seed;
            colors[i] = bubble.color;
        }
        GlowPass.submit(collector, poseStack, (pose, buffer) -> {
            Glow.Billboard axes = Glow.billboard(pose);
            Vector3f right = axes.right();
            Vector3f up = axes.up();
            for (Flash flash : flashes) {
                float t = (float) ((now - flash.start()) / FLASH_TICKS);
                if (t < 0.0F || t >= 1.0F) {
                    continue;
                }
                Vector3f center = new Vector3f((float) (flash.at().x - camera.x), (float) (flash.at().y - camera.y), (float) (flash.at().z - camera.z));
                Glitch.draw(buffer, pose, axes, center, flash.size(), flash.size(), (float) Math.pow(1.0F - t, 1.4), Glitch.frame(now), flash.seed());
            }
            for (int i = 0; i < count; i++) {
                float x = bubbles[i * 5];
                float y = bubbles[i * 5 + 1];
                float z = bubbles[i * 5 + 2];
                float radius = bubbles[i * 5 + 3];
                float phase = bubbles[i * 5 + 4];
                // a soap film's sheen, sliding between pink and blue over the bubble's own color
                int sheen = Glow.mix(0xFF8AE6, 0x8AE8FF, 0.5F + 0.5F * Mth.sin(phase * 0.13F));
                int rim = Glow.mix(Glow.mix(colors[i], 0xFFFFFF, 0.45F), sheen, 0.4F);
                Glow.ring(buffer, pose, x, y, z, right, up, radius, radius * 0.3F, Glow.withAlpha(rim, 0.55F), 18);
                Glow.disc(buffer, pose, axes, x, y, z, radius * 0.95F, Glow.withAlpha(rim, 0.06F));
                float hx = x + (up.x * 0.42F - right.x * 0.38F) * radius;
                float hy = y + (up.y * 0.42F - right.y * 0.38F) * radius;
                float hz = z + (up.z * 0.42F - right.z * 0.38F) * radius;
                Glow.disc(buffer, pose, axes, hx, hy, hz, radius * 0.2F, Glow.withAlpha(0xFFFFFF, 0.75F));
            }
        });
    }

    private record Flash(Vec3 at, double start, float size, int seed) {
    }

    private static final class Bubble {
        double x, y, z;
        double xo, yo, zo;
        double vx, vy, vz;
        final float radius;
        final int life;
        final int color;
        final float seed;
        int age;

        Bubble(Vec3 at, Vec3 velocity, float radius, int life, int color, float seed) {
            this.x = this.xo = at.x;
            this.y = this.yo = at.y;
            this.z = this.zo = at.z;
            this.vx = velocity.x;
            this.vy = velocity.y;
            this.vz = velocity.z;
            this.radius = radius;
            this.life = life;
            this.color = color;
            this.seed = seed;
        }

        /**
         * @return false once it has popped
         */
        boolean tick() {
            xo = x;
            yo = y;
            zo = z;
            // slowing as the air takes it, rising a little, and weaving
            vx *= 0.94;
            vz *= 0.94;
            vy = vy * 0.94 + 0.0024;
            x += vx + Mth.sin(age * 0.21F + seed) * 0.006;
            y += vy;
            z += vz + Mth.cos(age * 0.17F + seed) * 0.006;
            return ++age < life;
        }

        void pop(ClientLevel level) {
            level.addParticle(ParticleTypes.BUBBLE_POP, x, y, z, 0.0, 0.0, 0.0);
            if (ScarletFx.random().nextFloat() < 0.35F) {
                level.playLocalSound(x, y, z, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.NEUTRAL, 0.25F,
                        1.5F + ScarletFx.random().nextFloat() * 0.5F, false);
            }
        }
    }
}
