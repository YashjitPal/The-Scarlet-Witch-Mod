package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.darkhold.CorruptionClient;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.magic.Spell;
import com.yashjit.scarlet.magic.SpellCasts;
import com.yashjit.scarlet.network.MagicEventPayload;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The Shockwave as everyone sees it. Energy gathers into a burning knot between the caster's hands, then bursts out of
 * them as a ring racing across the ground: a bright leading edge with a wall of light standing on it, echoes behind it,
 * a flash at the heart, sparks flung outward, and the dirt it passes over kicked up into the air.
 */
public final class ShockwaveFx {

    private static final float WAVE_TICKS = 12.0F;
    private static final float FLING_TICKS = 24.0F;
    private static final float RADIUS = (float) SpellCasts.SHOCKWAVE_RADIUS;
    private static final float TAU = (float) (Math.PI * 2);
    private static final Vector3f FLAT_U = new Vector3f(1, 0, 0);
    private static final Vector3f FLAT_V = new Vector3f(0, 0, 1);

    private static final Int2DoubleMap GATHERS = new Int2DoubleOpenHashMap();
    private static final Int2DoubleMap FLINGS = new Int2DoubleOpenHashMap();
    private static final List<Wave> WAVES = new ArrayList<>();

    private ShockwaveFx() {
    }

    public static void onEvent(MagicEventPayload payload) {
        int kind = payload.kind();
        if (kind != MagicEventPayload.SHOCKWAVE_GATHER && kind != MagicEventPayload.SHOCKWAVE) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        double now = level.getGameTime() + partialTick();
        if (kind == MagicEventPayload.SHOCKWAVE_GATHER) {
            GATHERS.put(payload.entityId(), now);
            FLINGS.remove(payload.entityId());
            return;
        }
        FLINGS.put(payload.entityId(), now);
        float darkness = CorruptionClient.darkness(level.getEntity(payload.entityId()));
        WAVES.add(new Wave(payload.position(), now, darkness));
        try (Glow.Darkening ignored = Glow.darkening(darkness)) {
            blastSparks(payload.position());
        }
        RandomSource random = ScarletFx.random();
        for (int i = 0, n = Math.round(12 * darkness * ScarletFx.density()); i < n; i++) {
            ScarletFx.smoke(payload.position().add(randomUnit(random).scale(0.6)).add(0.0, 0.6, 0.0), randomUnit(random).scale(0.04), 30 + random.nextInt(20),
                    0.3F, 0.4F + 0.4F * darkness);
        }
    }

    /**
     * How far a player's hands have drawn in to gather a Shockwave: 0 to 1, letting go as it bursts.
     */
    public static float gather(Player player, double now) {
        double at = GATHERS.getOrDefault(player.getId(), Double.NaN);
        if (Double.isNaN(at)) {
            return 0.0F;
        }
        float t = (float) (now - at);
        float windUp = Spell.SHOCKWAVE.windUp();
        if (t < 0.0F || t > windUp + 4.0F) {
            return 0.0F;
        }
        float rise = Ease.outCubic(Ease.clamp01(t / (windUp * 0.7F)));
        double burst = FLINGS.getOrDefault(player.getId(), Double.NaN);
        float release = Double.isNaN(burst) ? 1.0F : 1.0F - Ease.clamp01((float) (now - burst) / 2.5F);
        return rise * release;
    }

    /**
     * How far a player's arms are flung out by a Shockwave bursting from them: 0 before and after, peaking at 1.
     */
    public static float fling(Player player, double now) {
        double at = FLINGS.getOrDefault(player.getId(), Double.NaN);
        if (Double.isNaN(at)) {
            return 0.0F;
        }
        float t = (float) (now - at);
        if (t < 0.0F || t > FLING_TICKS) {
            return 0.0F;
        }
        float rise = Ease.outBack(Ease.clamp01(t / 3.0F));
        float fall = 1.0F - Ease.inOutCubic(Ease.clamp01((t - 8.0F) / (FLING_TICKS - 8.0F)));
        return rise * fall;
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            GATHERS.clear();
            FLINGS.clear();
            WAVES.clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        double now = level.getGameTime();
        GATHERS.int2DoubleEntrySet().removeIf(entry -> now - entry.getDoubleValue() > 40.0);
        FLINGS.int2DoubleEntrySet().removeIf(entry -> now - entry.getDoubleValue() > FLING_TICKS + 5.0);
        WAVES.removeIf(wave -> now - wave.start() > WAVE_TICKS + 5.0);
        for (Int2DoubleMap.Entry entry : GATHERS.int2DoubleEntrySet()) {
            if (level.getEntity(entry.getIntKey()) instanceof Player player && !FLINGS.containsKey(entry.getIntKey())) {
                try (Glow.Darkening ignored = Glow.darkening(CorruptionClient.darkness(player))) {
                    gathering(player);
                }
            }
        }
        for (Wave wave : WAVES) {
            try (Glow.Darkening ignored = Glow.darkening(wave.darkness())) {
                kickUpDust(level, wave, now);
            }
        }
    }

    /**
     * Sparks drawn in from all around to the knot between the hands.
     */
    private static void gathering(Player player) {
        RandomSource random = ScarletFx.random();
        Vec3 knot = knot(player, 1.0F);
        for (int i = 0, n = Math.round(5 * ScarletFx.density()); i < n; i++) {
            Vec3 from = knot.add(randomUnit(random).scale(1.1 + random.nextDouble() * 0.6));
            ScarletFx.spark(from, knot.subtract(from).scale(0.2), 5, 0.022F, random.nextFloat() < 0.4F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET,
                    ScarletPalette.SCARLET, 0.0F, 0.95F);
        }
        if (random.nextFloat() < 0.5F * ScarletFx.density()) {
            ChaosDust.spawn(knot.add(randomUnit(random).scale(0.8)), randomUnit(random).scale(0.01));
        }
    }

    /**
     * The ground the edge is passing over, thrown up in a spray of its own dust.
     */
    private static void kickUpDust(ClientLevel level, Wave wave, double now) {
        float t = (float) (now - wave.start());
        if (t < 0.0F || t > WAVE_TICKS) {
            return;
        }
        RandomSource random = ScarletFx.random();
        float radius = RADIUS * Ease.outCubic(t / WAVE_TICKS);
        Vec3 center = wave.center();
        float density = ScarletFx.density();
        for (int i = 0, n = Math.round(16 * density); i < n; i++) {
            float angle = random.nextFloat() * TAU;
            double x = center.x + Mth.cos(angle) * radius;
            double z = center.z + Mth.sin(angle) * radius;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int dy = 1; dy >= -4; dy--) {
                pos.set(x, center.y + dy - 0.5, z);
                BlockState state = level.getBlockState(pos);
                if (!state.isAir() && !state.getCollisionShape(level, pos).isEmpty()) {
                    level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), x, pos.getY() + 1.05, z,
                            Mth.cos(angle) * 0.4, 0.5, Mth.sin(angle) * 0.4);
                    break;
                }
            }
        }
        for (int i = 0, n = Math.round(4 * density); i < n; i++) {
            float angle = random.nextFloat() * TAU;
            Vec3 at = center.add(Mth.cos(angle) * radius, 0.15, Mth.sin(angle) * radius);
            ScarletFx.spark(at, new Vec3(Mth.cos(angle) * 0.12, 0.06 + random.nextDouble() * 0.08, Mth.sin(angle) * 0.12), 6 + random.nextInt(5),
                    0.024F, ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET, 0.006F, 0.86F);
        }
    }

    private static void blastSparks(Vec3 feet) {
        RandomSource random = ScarletFx.random();
        float density = ScarletFx.density();
        for (int i = 0, n = Math.round(44 * density); i < n; i++) {
            float angle = random.nextFloat() * TAU;
            double speed = 0.45 + random.nextDouble() * 0.5;
            Vec3 at = feet.add(0.0, 0.2 + random.nextDouble() * 1.2, 0.0);
            ScarletFx.spark(at, new Vec3(Mth.cos(angle) * speed, 0.02 + random.nextDouble() * 0.12, Mth.sin(angle) * speed), 7 + random.nextInt(8),
                    0.03F, random.nextFloat() < 0.35F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET, 0.01F, 0.84F);
        }
        for (int i = 0, n = Math.round(14 * density); i < n; i++) {
            ScarletFx.spark(feet.add(randomUnit(random).scale(0.5)).add(0.0, 1.0, 0.0), randomUnit(random).scale(0.08).add(0.0, 0.1, 0.0),
                    16 + random.nextInt(10), 0.034F, ScarletPalette.CRIMSON, ScarletPalette.WINE, 0.004F, 0.9F);
        }
        for (int i = 0, n = Math.round(12 * density); i < n; i++) {
            ChaosDust.spawn(feet.add(randomUnit(random).scale(1.2)).add(0.0, 0.8, 0.0), randomUnit(random).scale(0.12));
        }
    }

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || WAVES.isEmpty() && GATHERS.isEmpty()) {
            return;
        }
        float partialTick = partialTick();
        double now = level.getGameTime() + partialTick;
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        List<Wave> waves = List.copyOf(WAVES);
        List<Knot> knots = new ArrayList<>();
        for (Int2DoubleMap.Entry entry : GATHERS.int2DoubleEntrySet()) {
            if (level.getEntity(entry.getIntKey()) instanceof Player player && !player.isInvisible()) {
                float gather = gather(player, now);
                if (gather > 0.01F) {
                    knots.add(new Knot(knot(player, partialTick).subtract(camera).toVector3f(), gather, ScarletFx.isFirstPersonViewOf(player),
                            CorruptionClient.darkness(player)));
                }
            }
        }
        GlowPass.submitTint(collector, poseStack, (pose, buffer) -> {
            float before = Glow.darkness();
            for (Wave wave : waves) {
                Glow.darken(wave.darkness());
                tintWave(buffer, pose, wave, now, camera);
            }
            Glow.darken(before);
        });
        GlowPass.submit(collector, poseStack, (pose, buffer) -> {
            Glow.Billboard axes = Glow.billboard(pose);
            float before = Glow.darkness();
            for (Knot knot : knots) {
                Glow.darken(knot.darkness());
                drawKnot(buffer, pose, axes, knot, now);
            }
            for (Wave wave : waves) {
                Glow.darken(wave.darkness());
                drawWave(buffer, pose, axes, wave, now, camera);
            }
            Glow.darken(before);
        });
    }

    /**
     * The red glass of the wave, a band just inside its edge.
     */
    private static void tintWave(VertexConsumer buffer, PoseStack.Pose pose, Wave wave, double now, Vec3 camera) {
        float t = (float) (now - wave.start());
        if (t < 0.0F || t > WAVE_TICKS) {
            return;
        }
        float k = t / WAVE_TICKS;
        float radius = RADIUS * Ease.outCubic(k);
        float fade = (float) Math.pow(1.0F - k, 1.4) * 0.5F;
        Vector3f c = wave.center().subtract(camera).toVector3f().add(0.0F, 0.06F, 0.0F);
        Glow.annulus(buffer, pose, c.x, c.y, c.z, FLAT_U, FLAT_V, radius * 0.7F, radius, GlowPass.tint(ScarletPalette.GLASS, 0.0F),
                GlowPass.tint(ScarletPalette.GLASS, fade), 72);
    }

    private static void drawWave(VertexConsumer buffer, PoseStack.Pose pose, Glow.Billboard axes, Wave wave, double now, Vec3 camera) {
        float t = (float) (now - wave.start());
        if (t < 0.0F || t > WAVE_TICKS + 4.0F) {
            return;
        }
        float k = Ease.clamp01(t / WAVE_TICKS);
        float fade = (float) Math.pow(1.0F - k, 1.2);
        float radius = RADIUS * Ease.outCubic(k);
        Vector3f c = wave.center().subtract(camera).toVector3f().add(0.0F, 0.08F, 0.0F);

        Glow.annulus(buffer, pose, c.x, c.y, c.z, FLAT_U, FLAT_V, radius * 0.55F, radius, Glow.withAlpha(ScarletPalette.SCARLET, 0.0F),
                Glow.withAlpha(ScarletPalette.SCARLET, 0.2F * fade), 72);
        Glow.ring(buffer, pose, c.x, c.y, c.z, FLAT_U, FLAT_V, radius, 0.5F * (1.0F - k) + 0.15F, Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.85F * fade), 72);
        Glow.ring(buffer, pose, c.x, c.y, c.z, FLAT_U, FLAT_V, radius, 0.1F, Glow.withAlpha(ScarletPalette.CORE, 0.6F * fade), 72);
        for (int i = 1; i <= 2; i++) {
            float echo = Ease.clamp01((t - i * 2.5F) / WAVE_TICKS);
            if (echo <= 0.0F) {
                continue;
            }
            Glow.ring(buffer, pose, c.x, c.y, c.z, FLAT_U, FLAT_V, RADIUS * Ease.outCubic(echo) * (1.0F - 0.12F * i), 0.2F,
                    Glow.withAlpha(ScarletPalette.SCARLET, 0.45F * (float) Math.pow(1.0F - echo, 1.5)), 64);
        }

        // a wall of light standing on the edge, lower as it spreads
        float height = 1.7F * (1.0F - k) * Ease.clamp01(t / 2.0F);
        if (height > 0.05F) {
            int count = 49;
            Vector3f[] points = new Vector3f[count];
            float[] widths = new float[count];
            int[] colors = new int[count];
            for (int i = 0; i < count; i++) {
                float angle = i * TAU / (count - 1);
                points[i] = new Vector3f(c.x + Mth.cos(angle) * radius, c.y + height * 0.5F, c.z + Mth.sin(angle) * radius);
                widths[i] = height;
                colors[i] = Glow.withAlpha(ScarletPalette.SCARLET, 0.32F * fade);
            }
            Glow.ribbon(buffer, pose, axes, points, widths, colors);
        }

        if (t < 5.0F) {
            float flash = 1.0F - t / 5.0F;
            Glow.disc(buffer, pose, axes, c.x, c.y + 1.0F, c.z, 2.8F * (0.5F + 0.5F * flash), Glow.withAlpha(ScarletPalette.SCARLET, 0.4F * flash));
            Glow.disc(buffer, pose, axes, c.x, c.y + 1.0F, c.z, 0.9F, Glow.withAlpha(ScarletPalette.CORE, 0.75F * flash));
        }
    }

    private static void drawKnot(VertexConsumer buffer, PoseStack.Pose pose, Glow.Billboard axes, Knot knot, double now) {
        float time = (float) (now % 24000.0);
        float g = knot.gather() * (knot.own() ? 0.6F : 1.0F);
        Vector3f c = knot.at();
        float flicker = 0.85F + 0.15F * Mth.sin(time * 2.3F);
        Glow.disc(buffer, pose, axes, c.x, c.y, c.z, 0.5F * g, Glow.withAlpha(ScarletPalette.SCARLET, 0.35F * g * flicker));
        Glow.spark(buffer, pose, axes, c.x, c.y, c.z, 0.06F + 0.1F * g, ScarletPalette.CORE, ScarletPalette.BRIGHT_SCARLET, g * flicker);
        Vector3f[] plane = Glow.planeAxes(new Vector3f(axes.right()).cross(axes.up()));
        for (int i = 0; i < 2; i++) {
            float spin = time * (0.6F + 0.3F * i) * (i == 0 ? 1.0F : -1.0F);
            Vector3f u = new Vector3f(plane[0]).mul(Mth.cos(spin)).add(new Vector3f(plane[1]).mul(Mth.sin(spin)));
            Vector3f v = new Vector3f(axes.up()).mul(0.4F).add(new Vector3f(u).cross(axes.up()).mul(0.9F)).normalize();
            Glow.ring(buffer, pose, c.x, c.y, c.z, u, v, 0.18F + 0.12F * g, 0.05F, Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.6F * g), 24);
        }
    }

    /**
     * Where the energy gathers: between the hands, held out before the chest.
     */
    private static Vec3 knot(Player player, float partialTick) {
        Vec3 look = player.getViewVector(partialTick).multiply(1.0, 0.0, 1.0);
        Vec3 forward = look.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getViewYRot(partialTick)) : look.normalize();
        return player.getEyePosition(partialTick).add(forward.scale(0.6)).add(0.0, -0.55, 0.0);
    }

    private static Vec3 randomUnit(RandomSource random) {
        return new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
    }

    private static float partialTick() {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    private record Wave(Vec3 center, double start, float darkness) {
    }

    private record Knot(Vector3f at, float gather, boolean own, float darkness) {
    }
}
