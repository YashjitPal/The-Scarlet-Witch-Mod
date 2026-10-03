package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.darkhold.CorruptionClient;
import com.yashjit.scarlet.client.render.FlatPixels;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.client.render.PixelSprite;
import com.yashjit.scarlet.client.render.Pixels;
import com.yashjit.scarlet.magic.RuneTraps;
import com.yashjit.scarlet.network.MagicEventPayload;
import com.yashjit.scarlet.network.RunePayload;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Rune Traps as everyone sees them.
 *
 * <ul>
 *     <li>Inscribed: the sigil writes itself onto the ground. An outer ring traces around, an inner one after it, a
 *     band of angular runes lights up one by one between them, and a hexagram fills the middle, turning against the
 *     runes. Then it lies in wait, breathing faintly, an ember now and then rising off it.</li>
 *     <li>Sprung: it flares, a column of light bursts up from its middle, and bands of light close in around everything
 *     caught on it, tightening at the ankles, the waist and the chest, held by chains of light running down into the
 *     sigil. The chains strain and flicker as they give out.</li>
 *     <li>Wiped away: the sigil sinks into the ground and its runes scatter as sparks.</li>
 * </ul>
 *
 * <p>All of it is pixel art: the sigil chalked on the ground in pixels on the world's own grid of texels, the light and
 * the chains in pixels facing the camera, each a step of the mod's ramp, so it keeps its red on sunlit ground.
 */
public final class RuneFx {

    private static final int GLYPHS = 18;
    private static final double FADE_TICKS = 12.0;
    /** A sigil not heard of for this long is let go, in case word of its end was missed. */
    private static final double FORGET_TICKS = 160.0;
    private static final float TAU = (float) (Math.PI * 2);

    private static final Int2ObjectMap<Sigil> SIGILS = new Int2ObjectOpenHashMap<>();
    private static @Nullable ClientLevel seenLevel;

    private RuneFx() {
    }

    public static void receive(RunePayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        double now = level.getGameTime();
        Sigil sigil = SIGILS.get(payload.id());
        if (sigil == null) {
            if (payload.stage() == RunePayload.FADED) {
                return;
            }
            sigil = new Sigil(payload.at(), now - payload.age(), payload.id(), CorruptionClient.darkness(level.getEntity(payload.caster())));
            SIGILS.put(payload.id(), sigil);
        }
        sigil.heardAt = now;
        try (Glow.Darkening ignored = Glow.darkening(sigil.darkness)) {
            if (payload.stage() == RunePayload.SPRUNG) {
                if (sigil.sprungAt < 0.0) {
                    sigil.sprungAt = now;
                    burst(sigil);
                }
                sigil.bound = List.copyOf(payload.bound());
            } else if (payload.stage() == RunePayload.FADED && sigil.fadedAt < 0.0) {
                sigil.fadedAt = now;
                scatter(sigil);
            }
        }
    }

    public static void onEvent(MagicEventPayload payload) {
        if (payload.kind() != MagicEventPayload.RUNE_FIZZLE) {
            return;
        }
        RandomSource random = ScarletFx.random();
        for (int i = 0, n = Math.round(12 * ScarletFx.density()); i < n; i++) {
            Vec3 out = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.5 + 0.4, random.nextGaussian()).normalize();
            ScarletFx.spark(payload.position(), out.scale(0.03 + random.nextDouble() * 0.05), 6 + random.nextInt(6), 0.018F,
                    ScarletPalette.BRIGHT_SCARLET, ScarletPalette.CRIMSON, 0.004F, 0.8F);
        }
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level != seenLevel) {
            seenLevel = level;
            SIGILS.clear();
        }
        if (level == null || minecraft.isPaused()) {
            return;
        }
        double now = level.getGameTime();
        SIGILS.values().removeIf(sigil -> sigil.fadedAt >= 0.0 && now - sigil.fadedAt > FADE_TICKS || now - sigil.heardAt > FORGET_TICKS);
        RandomSource random = ScarletFx.random();
        for (Sigil sigil : SIGILS.values()) {
            if (sigil.fadedAt >= 0.0) {
                continue;
            }
            // an ember now and then off a waiting sigil, more off a sprung one
            float chance = (sigil.sprungAt >= 0.0 ? 0.7F : 0.18F) * ScarletFx.density();
            if (random.nextFloat() < chance) {
                float angle = random.nextFloat() * TAU;
                float r = RuneTraps.RADIUS * (0.8F + random.nextFloat() * 0.17F);
                Vec3 at = sigil.at.add(Mth.cos(angle) * r, 0.05, Mth.sin(angle) * r);
                try (Glow.Darkening ignored = Glow.darkening(sigil.darkness)) {
                    ScarletFx.spark(at, new Vec3(0.0, 0.02 + random.nextDouble() * 0.03, 0.0), 14 + random.nextInt(12), 0.02F,
                            random.nextFloat() < 0.3F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET, -0.0008F, 0.93F);
                }
                if (random.nextFloat() < sigil.darkness * 0.5F) {
                    ScarletFx.smoke(at, new Vec3(0.0, 0.012, 0.0), 26 + random.nextInt(14), 0.12F, 0.3F + 0.4F * sigil.darkness);
                }
            }
        }
    }

    private static void burst(Sigil sigil) {
        RandomSource random = ScarletFx.random();
        for (int i = 0, n = Math.round(40 * ScarletFx.density()); i < n; i++) {
            float angle = random.nextFloat() * TAU;
            float r = random.nextFloat() * RuneTraps.RADIUS;
            Vec3 at = sigil.at.add(Mth.cos(angle) * r, 0.05, Mth.sin(angle) * r);
            ScarletFx.spark(at, new Vec3(Mth.cos(angle) * 0.04, 0.08 + random.nextDouble() * 0.12, Mth.sin(angle) * 0.04), 10 + random.nextInt(12),
                    0.026F, random.nextFloat() < 0.35F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET, 0.006F, 0.88F);
        }
    }

    private static void scatter(Sigil sigil) {
        RandomSource random = ScarletFx.random();
        for (int g = 0; g < GLYPHS; g++) {
            if (random.nextFloat() > ScarletFx.density()) {
                continue;
            }
            float angle = g / (float) GLYPHS * TAU;
            float r = RuneTraps.RADIUS * 0.88F;
            Vec3 at = sigil.at.add(Mth.cos(angle) * r, 0.05, Mth.sin(angle) * r);
            ScarletFx.spark(at, new Vec3(Mth.cos(angle) * 0.03, 0.03 + random.nextDouble() * 0.04, Mth.sin(angle) * 0.03), 8 + random.nextInt(8), 0.022F,
                    ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET, 0.002F, 0.86F);
        }
    }

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || SIGILS.isEmpty()) {
            return;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + partialTick;
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        List<Draw> draws = new ArrayList<>();
        for (Sigil sigil : SIGILS.values()) {
            if (sigil.at.distanceToSqr(camera) > 128.0 * 128.0) {
                continue;
            }
            float written = Ease.outCubic((float) ((now - sigil.inscribedAt) / RuneTraps.INSCRIBE_TICKS));
            float sprung = sigil.sprungAt < 0.0 ? 0.0F : (float) (now - sigil.sprungAt);
            float fade = sigil.fadedAt < 0.0 ? 1.0F : 1.0F - Ease.clamp01((float) ((now - sigil.fadedAt) / FADE_TICKS));
            List<Held> held = new ArrayList<>();
            if (sigil.sprungAt >= 0.0 && sigil.fadedAt < 0.0) {
                for (int id : sigil.bound) {
                    Entity entity = level.getEntity(id);
                    if (entity != null) {
                        Vec3 feet = entity.getPosition(partialTick);
                        held.add(new Held(feet.subtract(camera).toVector3f(), entity.getBbWidth(), entity.getBbHeight()));
                    }
                }
            }
            Vec3 c = sigil.at.subtract(camera);
            draws.add(new Draw(new Vector3f((float) c.x, (float) c.y + 0.03F, (float) c.z), written, sprung, fade, sigil.seed, held, sigil.darkness));
        }
        if (draws.isEmpty()) {
            return;
        }
        float time = (float) (now % 24000.0);
        GlowPass.submitPixels(collector, poseStack, (pose, buffer) -> {
            float before = Glow.darkness();
            for (Draw draw : draws) {
                Glow.darken(draw.darkness());
                sigil(buffer, pose, draw, time, camera);
                if (draw.sprung() > 0.0F) {
                    Vector3f c = draw.center();
                    PixelSprite sprite = PixelSprite.inWorld(pose, c.x, c.y + 1.0F, c.z);
                    column(sprite, draw, time);
                    for (Held held : draw.held()) {
                        chains(sprite, draw, held, time);
                    }
                    sprite.draw(buffer);
                }
            }
            Glow.darken(before);
        });
    }

    /**
     * How large a sigil stands: a little swell as it springs, sinking smaller as it is wiped away.
     */
    private static float radiusScale(Draw draw) {
        float swell = draw.sprung() > 0.0F ? 0.06F * Math.max(0.0F, 1.0F - draw.sprung() / 8.0F) : 0.0F;
        return (1.0F + swell) * (0.85F + 0.15F * draw.fade());
    }

    /**
     * The sigil itself, chalked on the ground in pixels on the world's own grid of texels, written in as far as it has
     * got. Its lines take a step of the ramp as bright as it burns: dim while it waits, white-hot as it springs.
     */
    private static void sigil(VertexConsumer buffer, PoseStack.Pose pose, Draw draw, float time, Vec3 camera) {
        Vector3f c = draw.center();
        double cx = c.x + camera.x;
        double cz = c.z + camera.z;
        float written = draw.written();
        float flare = draw.sprung() > 0.0F ? Math.max(0.0F, 1.0F - draw.sprung() / 10.0F) : 0.0F;
        float breathe = 0.85F + 0.15F * Mth.sin(time * 0.12F + draw.seed());
        float bright = (draw.sprung() > 0.0F ? 0.95F : 0.6F * breathe) + flare * 0.6F;
        int lit = bright > 1.2F ? Pixels.HOT : bright > 0.85F ? Pixels.PINK : bright > 0.55F ? Pixels.BRIGHT : Pixels.SCARLET;
        int dim = lit + 1;
        float r = RuneTraps.RADIUS * radiusScale(draw);
        float spin = time * 0.012F + draw.seed();
        FlatPixels.Sketch sketch = new FlatPixels.Sketch();
        // the rings trace round as the sigil is written, the outer two pixels wide
        sketch.arc(cx, cz, r, spin, written, Pixels.opaque(lit), 3);
        sketch.arc(cx, cz, r - Pixels.SIZE, spin, written, Pixels.opaque(dim), 2);
        float inner = Ease.clamp01((written - 0.2F) / 0.8F);
        sketch.arc(cx, cz, r * 0.78F, -spin * 1.3F, inner, Pixels.opaque(dim), 2);
        // the runes between them light up one by one, in the order the ring passes them
        for (int g = 0; g < GLYPHS; g++) {
            float at = g / (float) GLYPHS;
            float glow = Ease.clamp01((written - at) * 6.0F);
            if (glow > 0.0F) {
                glyph(sketch, cx, cz, spin + at * TAU, r * 0.885F, r * 0.08F, draw.seed() * 31.0F + g, Pixels.opaque(glow > 0.6F ? lit : dim));
            }
        }
        // a hexagram in the middle, turning against the runes, round a white-hot heart
        float star = Ease.clamp01((written - 0.5F) / 0.5F);
        if (star > 0.15F) {
            float points = r * 0.7F;
            for (int t = 0; t < 2; t++) {
                float base = -spin * 0.7F + t * (TAU / 6.0F);
                for (int k = 0; k < 3; k++) {
                    float a0 = base + k * TAU / 3.0F;
                    float a1 = base + (k + 1) * TAU / 3.0F;
                    sketch.line(cx + Mth.cos(a0) * points, cz + Mth.sin(a0) * points, cx + Mth.cos(a1) * points, cz + Mth.sin(a1) * points,
                            Pixels.opaque(dim), 1);
                }
            }
            sketch.arc(cx, cz, r * 0.18F, 0.0F, 1.0F, Pixels.opaque(lit), 2);
            for (int d = 0; d < 4; d++) {
                sketch.dot(cx + (d & 1) * Pixels.SIZE - Pixels.SIZE * 0.5, cz + (d >> 1) * Pixels.SIZE - Pixels.SIZE * 0.5, Pixels.opaque(Pixels.HOT), 4);
            }
        }
        sketch.draw(buffer, pose, camera, c.y + camera.y, draw.fade());
    }

    /**
     * One angular rune standing on the ring at {@code angle}: a stave across the band with two or three strokes off it,
     * chosen by {@code seed}, so every sigil reads differently.
     */
    private static void glyph(FlatPixels.Sketch sketch, double cx, double cz, float angle, float radius, float half, float seed, int color) {
        Rune rune = new Rune(sketch, cx + Mth.cos(angle) * radius, cz + Mth.sin(angle) * radius, Mth.cos(angle), Mth.sin(angle), color);
        int shape = Math.floorMod((int) (Mth.sin(seed * 12.9898F) * 43758.547F), 6);
        // the stave, out across the band
        rune.stroke(0.0F, -half, 0.0F, half);
        switch (shape) {
            case 0 -> {
                rune.stroke(0.0F, half, half * 0.7F, half * 0.3F);
                rune.stroke(0.0F, 0.0F, half * 0.7F, -half * 0.4F);
            }
            case 1 -> {
                rune.stroke(-half * 0.6F, half * 0.6F, half * 0.6F, -half * 0.2F);
                rune.stroke(-half * 0.6F, -half * 0.2F, half * 0.6F, half * 0.6F);
            }
            case 2 -> {
                rune.stroke(0.0F, half * 0.2F, -half * 0.6F, half);
                rune.stroke(0.0F, half * 0.2F, half * 0.6F, half);
            }
            case 3 -> {
                rune.stroke(0.0F, half, half * 0.6F, 0.0F);
                rune.stroke(half * 0.6F, 0.0F, 0.0F, -half * 0.5F);
            }
            case 4 -> {
                rune.stroke(-half * 0.55F, -half, -half * 0.55F, half * 0.5F);
                rune.stroke(-half * 0.55F, half * 0.5F, 0.0F, half);
            }
            default -> rune.stroke(-half * 0.6F, 0.0F, half * 0.6F, 0.0F);
        }
    }

    /**
     * Where a rune stands and which way its band runs there: {@code rx, rz} out from the middle of the sigil.
     */
    private record Rune(FlatPixels.Sketch sketch, double x, double z, float rx, float rz, int color) {

        /**
         * A stroke between two points given along the ring ({@code a}) and out across it ({@code b}).
         */
        void stroke(float a0, float b0, float a1, float b1) {
            float tx = -rz;
            float tz = rx;
            sketch.line(x + tx * a0 + rx * b0, z + tz * a0 + rz * b0, x + tx * a1 + rx * b1, z + tz * a1 + rz * b1, color, 2);
        }
    }

    /**
     * The column of light that bursts up out of the sigil's middle as it springs: white-hot down its heart, cooling to
     * its edges, narrowing and thinning away.
     */
    private static void column(PixelSprite sprite, Draw draw, float time) {
        float k = draw.sprung() / 12.0F;
        if (k >= 1.0F) {
            return;
        }
        Vector3f c = draw.center();
        float fade = 1.0F - Ease.outCubic(k);
        float height = 2.5F + 3.5F * Ease.outCubic(k);
        int frame = (int) Math.floor(time);
        int width = Math.max(1, Math.round(7.0F * fade));
        Vector3f right = sprite.right();
        for (int n = -width; n <= width; n++) {
            float edge = Math.abs(n) / (float) (width + 1);
            if (!Pixels.shows(fade * (1.15F - edge), n, frame)) {
                continue;
            }
            float off = n * Pixels.SIZE;
            Vector3f base = new Vector3f(c).add(right.x * off, right.y * off, right.z * off);
            Vector3f top = new Vector3f(base).add(0.0F, height * (1.0F - 0.5F * edge), 0.0F);
            sprite.line(base, top, Pixels.opaque(edge < 0.3F ? Pixels.HOT : edge < 0.65F ? Pixels.PINK : Pixels.BRIGHT), 2, 1);
        }
    }

    /**
     * What a sprung sigil holds: bands of it closing in around it at the ankles, the waist and the chest, held by
     * chains running down into the sigil, straining and flickering as they give out.
     */
    private static void chains(PixelSprite sprite, Draw draw, Held held, float time) {
        float close = Ease.outBack(Ease.clamp01(draw.sprung() / 7.0F));
        float strain = 0.65F + 0.35F * Mth.sin(time * 1.7F + held.feet().x * 3.0F);
        int frame = (int) Math.floor(time);
        int band = Pixels.opaque(strain > 0.85F ? Pixels.PINK : Pixels.BRIGHT);
        for (Band b : bands(held, close, time)) {
            Wisps.ring(sprite, new Vector3f(b.x(), b.y(), b.z()), b.u(), b.v(), b.radius(), 0.0F, 1.0F, band, 2, 2);
        }
        int n = 0;
        for (Vector3f[] link : links(draw, held, close)) {
            if (Pixels.shows(strain + 0.15F, n++, frame)) {
                sprite.line(link[0], link[1], Pixels.opaque(Pixels.SCARLET), 1, 2);
                sprite.plot(link[1].x, link[1].y, link[1].z, Pixels.opaque(Pixels.HOT), 3, 1);
            }
        }
    }

    /**
     * The three bands closing in around what is held, at its ankles, waist and chest, each tilting a little as it
     * strains.
     */
    private static List<Band> bands(Held held, float close, float time) {
        Vector3f feet = held.feet();
        float radius = held.width() * 0.62F + 0.12F;
        float[] heights = {0.12F, 0.5F, 0.82F};
        List<Band> bands = new ArrayList<>(heights.length);
        for (int i = 0; i < heights.length; i++) {
            float r = radius * (1.0F + 1.2F * (1.0F - close)) + 0.02F * Mth.sin(time * 2.1F + i);
            float wobble = Mth.sin(time * 0.6F + i * 2.0F) * 0.12F;
            bands.add(new Band(feet.x, feet.y + held.height() * heights[i], feet.z, new Vector3f(1.0F, wobble, 0.0F).normalize(),
                    new Vector3f(0.0F, -wobble, 1.0F).normalize(), r));
        }
        return bands;
    }

    /**
     * The links of the four chains running from the sigil up to the top band, as many as have risen so far.
     */
    private static List<Vector3f[]> links(Draw draw, Held held, float close) {
        Vector3f feet = held.feet();
        float radius = held.width() * 0.62F + 0.12F;
        float top = feet.y + held.height() * 0.82F;
        List<Vector3f[]> links = new ArrayList<>();
        for (int k = 0; k < 4; k++) {
            float angle = k * TAU / 4.0F + 0.4F;
            float ground = radius * 2.2F;
            Vector3f from = new Vector3f(feet.x + Mth.cos(angle) * ground, draw.center().y, feet.z + Mth.sin(angle) * ground);
            Vector3f to = new Vector3f(feet.x + Mth.cos(angle) * radius, top, feet.z + Mth.sin(angle) * radius);
            int count = 6;
            for (int l = 0; l < count; l++) {
                float s0 = (l + 0.12F) / count;
                float s1 = (l + 0.88F) / count;
                if (s1 > close) {
                    break;
                }
                links.add(new Vector3f[] {new Vector3f(from).lerp(to, s0), new Vector3f(from).lerp(to, s1)});
            }
        }
        return links;
    }

    private record Band(float x, float y, float z, Vector3f u, Vector3f v, float radius) {
    }

    private record Draw(Vector3f center, float written, float sprung, float fade, float seed, List<Held> held, float darkness) {
    }

    private record Held(Vector3f feet, float width, float height) {
    }

    private static final class Sigil {
        final Vec3 at;
        final double inscribedAt;
        final float seed;
        /** How dark its writer's magic had grown when it was first seen. */
        final float darkness;
        double sprungAt = -1.0;
        double fadedAt = -1.0;
        double heardAt;
        List<Integer> bound = List.of();

        Sigil(Vec3 at, double inscribedAt, int id, float darkness) {
            this.at = at;
            this.inscribedAt = inscribedAt;
            this.seed = id * 0.731F;
            this.darkness = darkness;
        }
    }
}
