package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.darkhold.CorruptionClient;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.client.render.PixelSprite;
import com.yashjit.scarlet.client.render.Pixels;
import com.yashjit.scarlet.entity.ChaosBolt;
import com.yashjit.scarlet.network.ChaosImpactPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Everything a chaos blast leaves behind, as pixel art: the flash in the palm as it is released, sparks shed along its
 * flight, its stream drawing back into the point of impact, and the impact itself. That bursts in a white-hot star, then
 * a ball of energy that blooms and hollows into a ring of red smoke, while tendrils of it splash out curling, rings race
 * out across the surface it struck, and a burn is left on that surface, cooling from scarlet to wine before it fades.
 */
public final class BoltFx {

    private static final float IMPACT_TICKS = 11.0F;
    private static final float SCORCH_TICKS = 44.0F;
    private static final float RELEASE_TICKS = 4.0F;
    private static final float TAIL_TICKS = 3.5F;
    private static final int TENDRILS = 7;
    private static final float TAU = (float) (Math.PI * 2);

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
        float darkness = CorruptionClient.darkness(minecraft.level.getEntity(payload.caster()));
        IMPACTS.add(new Impact(at, normal, payload.direction(), payload.hitEntity(), minecraft.level.getGameTime() + partialTick(), darkness,
                random.nextInt()));
        float density = ScarletFx.density();
        try (Glow.Darkening ignored = Glow.darkening(darkness)) {
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
        // a corrupted caster's blast leaves a pall of black smoke where it burst
        for (int i = 0, n = Math.round(12 * darkness * density); i < n; i++) {
            ScarletFx.smoke(at.add(randomUnit(random).scale(0.25)), normal.scale(0.02).add(randomUnit(random).scale(0.02)), 30 + random.nextInt(20),
                    0.22F + random.nextFloat() * 0.12F, 0.45F + 0.4F * darkness);
        }
    }

    /**
     * Magic leaving a palm toward {@code forward}.
     *
     * @param nearCamera your own hand in first person: nothing drawn over the view, sparks start a little ahead
     * @param darkness   how far the caster's corruption darkens it
     */
    public static void release(Vec3 palm, Vec3 forward, boolean nearCamera, float darkness) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        RandomSource random = ScarletFx.random();
        Vec3 from = nearCamera ? palm.add(forward.scale(0.8)) : palm.add(forward.scale(0.15));
        if (!nearCamera) {
            RELEASES.add(new Release(from, forward, minecraft.level.getGameTime() + partialTick(), darkness));
        }
        float density = ScarletFx.density();
        try (Glow.Darkening ignored = Glow.darkening(darkness)) {
            for (int i = 0, n = Math.round(10 * density); i < n; i++) {
                Vec3 direction = forward.add(randomUnit(random).scale(0.38)).normalize();
                int core = random.nextFloat() < 0.4F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET;
                ScarletFx.spark(from, direction.scale(0.16 + random.nextDouble() * 0.18), 4 + random.nextInt(5), 0.024F, core, ScarletPalette.SCARLET, 0.0F,
                        0.8F);
            }
            for (int i = 0, n = Math.round(6 * density); i < n; i++) {
                ChaosDust.spawn(from.add(randomUnit(random).scale(0.06)), forward.scale(0.05).add(randomUnit(random).scale(0.04)));
            }
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
        IMPACTS.removeIf(impact -> now - impact.start() > (impact.hitEntity() ? IMPACT_TICKS : SCORCH_TICKS) + 1);
        RELEASES.removeIf(release -> now - release.start() > RELEASE_TICKS + 1);
        RandomSource random = ScarletFx.random();
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        float density = ScarletFx.density();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof ChaosBolt bolt) || bolt.distanceToSqr(camera) > 96 * 96 || bolt.tickCount < 1) {
                continue;
            }
            Vec3 velocity = bolt.getDeltaMovement();
            float darkness = CorruptionClient.darkness(bolt.getOwner());
            try (Glow.Darkening ignored = Glow.darkening(darkness)) {
                for (int i = 0, n = Math.round(2 * density); i < n; i++) {
                    Vec3 at = bolt.position().subtract(velocity.scale(random.nextDouble())).add(randomUnit(random).scale(0.15));
                    int core = random.nextFloat() < 0.25F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET;
                    ScarletFx.spark(at, randomUnit(random).scale(0.035).subtract(velocity.scale(0.03)), 6 + random.nextInt(6), 0.026F,
                            core, ScarletPalette.SCARLET, 0.0F, 0.88F);
                }
                // embers left hanging in the air behind it, cooling as they drift
                if (random.nextFloat() < 0.8F * density) {
                    Vec3 at = bolt.position().subtract(velocity.scale(0.5 + random.nextDouble())).add(randomUnit(random).scale(0.2));
                    ScarletFx.spark(at, randomUnit(random).scale(0.012).add(0, 0.006, 0), 14 + random.nextInt(10), 0.032F,
                            ScarletPalette.SCARLET, ScarletPalette.CRIMSON, -0.0005F, 0.9F);
                }
                if (random.nextFloat() < 0.5F * density) {
                    ChaosDust.spawn(bolt.position().subtract(velocity.scale(random.nextDouble() * 0.8)).add(randomUnit(random).scale(0.12)),
                            randomUnit(random).scale(0.02));
                }
            }
            if (random.nextFloat() < darkness * density) {
                ScarletFx.smoke(bolt.position().subtract(velocity.scale(random.nextDouble())), randomUnit(random).scale(0.01), 16 + random.nextInt(10),
                        0.12F, 0.3F + 0.4F * darkness);
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
        GlowPass.submitPixels(collector, poseStack, (pose, buffer) -> {
            float before = Glow.darkness();
            for (Impact impact : impacts) {
                float t = (float) (now - impact.start());
                if (t >= 0.0F) {
                    Glow.darken(impact.darkness());
                    drawImpact(buffer, pose, impact, t, camera);
                }
            }
            for (Release release : releases) {
                float t = (float) (now - release.start());
                if (t >= 0.0F && t <= RELEASE_TICKS) {
                    Glow.darken(release.darkness());
                    drawRelease(buffer, pose, release, t, camera);
                }
            }
            Glow.darken(before);
        });
    }

    private static void drawImpact(VertexConsumer buffer, PoseStack.Pose pose, Impact impact, float t, Vec3 camera) {
        Vector3f at = impact.position().subtract(camera).toVector3f();
        Vector3f normal = impact.normal().toVector3f();
        int seed = impact.seed();
        if (t < TAIL_TICKS) {
            // the stream catches up with its head and is gone
            Vector3f direction = impact.direction().toVector3f();
            BoltVisuals.trail(buffer, pose, at, direction, BoltVisuals.TRAIL_LENGTH * (1.0F - Ease.outCubic(t / TAIL_TICKS)), t,
                    1.0F - t / TAIL_TICKS, seed);
        }
        Vector3f u;
        Vector3f v;
        if (impact.hitEntity()) {
            Glow.Billboard axes = Glow.billboard(pose);
            u = new Vector3f(axes.right()).normalize();
            v = new Vector3f(axes.up()).normalize();
        } else {
            Vector3f[] plane = Glow.planeAxes(normal);
            u = plane[0];
            v = plane[1];
            scorch(buffer, pose, new Vector3f(normal).mul(0.02F).add(at), u, v, t, seed);
        }
        if (t > IMPACT_TICKS) {
            return;
        }
        Vector3f lifted = new Vector3f(normal).mul(0.04F).add(at);
        shockRing(buffer, pose, lifted, u, v, t / 9.0F, 0.2F, 2.1F, Pixels.BRIGHT, seed);
        shockRing(buffer, pose, lifted, u, v, (t - 2.0F) / 8.0F, 0.1F, 1.3F, Pixels.PINK, seed + 1);

        // the blast itself, drawn a little out from the surface and toward you so the surface does not cut it in half
        float distance = at.length();
        Vector3f toward = distance > 1.0E-3F ? new Vector3f(at).mul(-Math.min(0.6F, distance * 0.15F) / distance) : new Vector3f();
        Vector3f center = new Vector3f(normal).mul(0.2F).add(at).add(toward);
        PixelSprite sprite = new PixelSprite(pose, center.x, center.y, center.z);
        burst(sprite, t, seed);
        if (t < 3.0F) {
            star(sprite, 15.0F * (1.0F - t / 3.0F), 4);
        }
        tendrils(sprite, at, normal, t, seed);
        sprite.draw(buffer);
    }

    /**
     * A ball of energy blooming out from the point of impact, white-hot at first, then hollowing out from the middle into
     * a ring of red smoke that burns down the ramp and breaks up.
     */
    private static void burst(PixelSprite sprite, float t, int seed) {
        if (t > 9.0F) {
            return;
        }
        float k = t / 9.0F;
        int frame = (int) Math.floor(t);
        float radius = (0.35F + 0.95F * Ease.outCubic(Math.min(1.0F, t / 5.0F))) / Pixels.SIZE;
        float hollow = radius * Math.clamp((t - 2.0F) / 6.0F, 0.0F, 0.85F);
        int reach = (int) Math.ceil(radius * 1.15F) + 1;
        for (int i = -reach; i < reach; i++) {
            for (int j = -reach; j < reach; j++) {
                float x = i + 0.5F;
                float y = j + 0.5F;
                float r = (float) Math.sqrt(x * x + y * y);
                float cos = r > 1.0E-4F ? x / r : 1.0F;
                float sin = r > 1.0E-4F ? y / r : 0.0F;
                // a ragged edge, licking like flame
                float edge = radius * (0.85F + 0.3F * Pixels.noise(cos * 2.5F + frame * 0.3F, sin * 2.5F - frame * 0.25F, seed));
                if (r >= edge || r < hollow) {
                    continue;
                }
                float churn = Pixels.churn(x * 0.22F + frame * 0.2F, y * 0.22F - frame * 0.15F, seed + 3);
                float heat = (1.0F - k) * (0.55F + 0.6F * churn);
                if (t < 3.0F && r < radius * 0.35F) {
                    heat += 0.4F;
                }
                // hottest along the rim of the hollow as it opens
                if (hollow > 0.0F && r < hollow + 1.5F) {
                    heat += 0.2F;
                }
                if (!Pixels.shows(heat * 1.6F, i, j)) {
                    continue;
                }
                int color = heat > 0.95F ? Pixels.opaque(Pixels.HOT) : heat > 0.8F ? Pixels.opaque(Pixels.PINK) : heat > 0.62F ? Pixels.opaque(Pixels.BRIGHT)
                        : heat > 0.45F ? Pixels.opaque(Pixels.SCARLET) : heat > 0.3F ? Pixels.ramp(Pixels.CRIMSON, 0.75F) : Pixels.ramp(Pixels.WINE, 0.5F);
                sprite.cell(i, j, color, 1);
            }
        }
    }

    /**
     * A star of light: a white-hot heart with four arms {@code arm} pixels long and four shorter diagonal ones.
     */
    private static void star(PixelSprite sprite, float length, int priority) {
        int arm = Math.round(length);
        if (arm < 1) {
            return;
        }
        for (int d = -arm; d < arm; d++) {
            float out = Math.abs(d + 0.5F);
            int color = Pixels.opaque(out < 2.5F ? Pixels.HOT : out < arm * 0.6F ? Pixels.PINK : Pixels.BRIGHT);
            sprite.cell(d, -1, color, priority);
            sprite.cell(d, 0, color, priority);
            sprite.cell(-1, d, color, priority);
            sprite.cell(0, d, color, priority);
        }
        for (int d = 1; d <= arm / 2; d++) {
            int color = Pixels.opaque(d < 2 ? Pixels.HOT : Pixels.PINK);
            sprite.cell(d, d, color, priority);
            sprite.cell(-1 - d, d, color, priority);
            sprite.cell(d, -1 - d, color, priority);
            sprite.cell(-1 - d, -1 - d, color, priority);
        }
    }

    /**
     * Tendrils of the blast splashing out from where it struck, each arcing up off the surface and curling around as it
     * goes, its tail chasing its head until it is gone.
     */
    private static void tendrils(PixelSprite sprite, Vector3f at, Vector3f normal, float t, int seed) {
        if (t > 11.0F) {
            return;
        }
        Vector3f[] plane = Glow.planeAxes(normal);
        int cool = (int) (t / 4.0F);
        for (int w = 0; w < TENDRILS; w++) {
            float start = Pixels.hash(w, 0, seed, 11) * TAU;
            float lift = 0.35F + 0.55F * Pixels.hash(w, 1, seed, 11);
            float curl = (Pixels.hash(w, 2, seed, 11) - 0.5F) * 4.0F;
            float speed = 0.17F + 0.1F * Pixels.hash(w, 3, seed, 11);
            float head = speed * 7.0F * Ease.outCubic(Math.min(1.0F, t / 7.0F));
            float tail = Math.max(0.0F, speed * (t - 3.0F) * 1.25F);
            if (head - tail < 0.05F) {
                continue;
            }
            int steps = Math.max(2, Math.round((head - tail) / (Pixels.SIZE * 0.5F)));
            for (int k = 0; k <= steps; k++) {
                float s = k / (float) steps;
                float d = tail + (head - tail) * s;
                float angle = start + curl * d;
                float ox = Mth.cos(angle) * d;
                float oy = Mth.sin(angle) * d;
                float rise = lift * d * (1.0F - 0.35F * d);
                float x = at.x + plane[0].x * ox + plane[1].x * oy + normal.x * rise;
                float y = at.y + plane[0].y * ox + plane[1].y * oy + normal.y * rise;
                float z = at.z + plane[0].z * ox + plane[1].z * oy + normal.z * rise;
                int step = (s > 0.8F ? Pixels.PINK : s > 0.5F ? Pixels.BRIGHT : s > 0.25F ? Pixels.SCARLET : Pixels.CRIMSON) + cool;
                sprite.plot(x, y, z, Pixels.ramp(Math.min(step, Pixels.WINE), step >= Pixels.WINE ? 0.75F : 1.0F), 2, s > 0.7F && t < 6.0F ? 2 : 1);
            }
        }
    }

    /**
     * A ring racing outward across a plane from {@code from} to {@code to} blocks, cooling down the ramp from
     * {@code step} and thinning out as {@code progress} goes 0 to 1.
     */
    private static void shockRing(VertexConsumer buffer, PoseStack.Pose pose, Vector3f at, Vector3f u, Vector3f v, float progress, float from,
                                  float to, int step, int seed) {
        if (progress < 0.0F || progress >= 1.0F) {
            return;
        }
        float p = Pixels.SIZE;
        float radius = from + (to - from) * Ease.outCubic(progress);
        float half = (progress < 0.35F ? 1.0F : 0.55F) * p;
        int color = Pixels.opaque(Math.min(step + (int) (progress * 3.0F), Pixels.CRIMSON));
        int reach = (int) Math.ceil((radius + half) / p) + 1;
        for (int i = -reach; i < reach; i++) {
            for (int j = -reach; j < reach; j++) {
                float x = (i + 0.5F) * p;
                float y = (j + 0.5F) * p;
                if (Math.abs((float) Math.sqrt(x * x + y * y) - radius) < half && Pixels.shows(1.4F * (1.0F - progress), i + seed, j)) {
                    Pixels.cell(buffer, pose, at, u, v, i, j, p, color);
                }
            }
        }
    }

    /**
     * The burn a blast leaves on what it struck: a scorched ring with a churned blot inside it and four marks across
     * it like a rune, glowing scarlet at first and cooling to wine before it thins away.
     */
    private static void scorch(VertexConsumer buffer, PoseStack.Pose pose, Vector3f at, Vector3f u, Vector3f v, float t, int seed) {
        if (t > SCORCH_TICKS) {
            return;
        }
        float k = t / SCORCH_TICKS;
        int step = t < 6.0F ? Pixels.BRIGHT : t < 14.0F ? Pixels.SCARLET : t < 26.0F ? Pixels.CRIMSON : Pixels.WINE;
        float alpha = t < 26.0F ? 1.0F : 0.75F;
        float turn = Pixels.hash(seed, 0, 0, 21) * TAU;
        float grow = Ease.outCubic(Math.min(1.0F, t / 3.0F));
        for (int i = -10; i < 10; i++) {
            for (int j = -10; j < 10; j++) {
                float x = i + 0.5F;
                float y = j + 0.5F;
                float r = (float) Math.sqrt(x * x + y * y);
                float theta = (float) Math.atan2(y, x) - turn;
                boolean ring = Math.abs(r - 7.0F * grow) < 0.75F;
                boolean blot = r < 4.0F * grow && Pixels.noise(x * 0.45F, y * 0.45F, seed + 5) > 0.55F;
                float mark = Math.abs(Mth.sin(theta * 2.0F));
                boolean rune = r > 4.5F * grow && r < 9.5F * grow && mark < 0.12F;
                if (!(ring || blot || rune) || !Pixels.shows(1.5F * (1.0F - k), i, j + seed)) {
                    continue;
                }
                int s = blot ? step + 1 : step;
                Pixels.cell(buffer, pose, at, u, v, i, j, Pixels.SIZE, Pixels.ramp(Math.min(s, Pixels.WINE), alpha));
            }
        }
    }

    private static void drawRelease(VertexConsumer buffer, PoseStack.Pose pose, Release release, float t, Vec3 camera) {
        Vector3f at = release.position().subtract(camera).toVector3f();
        float fade = 1.0F - t / RELEASE_TICKS;
        PixelSprite sprite = new PixelSprite(pose, at.x, at.y, at.z);
        star(sprite, 5.0F * fade, 1);
        sprite.draw(buffer);
        Vector3f[] plane = Glow.planeAxes(release.forward().toVector3f());
        shockRing(buffer, pose, at, plane[0], plane[1], t / RELEASE_TICKS, 0.12F, 0.6F, Pixels.BRIGHT, 0);
    }

    private static Vec3 randomUnit(RandomSource random) {
        return new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
    }

    private static float partialTick() {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    private record Impact(Vec3 position, Vec3 normal, Vec3 direction, boolean hitEntity, double start, float darkness, int seed) {
    }

    private record Release(Vec3 position, Vec3 forward, double start, float darkness) {
    }
}
