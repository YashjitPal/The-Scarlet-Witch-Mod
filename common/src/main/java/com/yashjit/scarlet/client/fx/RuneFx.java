package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
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
 * <p>Everything lies over a scarlet tint, so it keeps its red on sunlit ground.
 */
public final class RuneFx {

    private static final Vector3f FLAT_U = new Vector3f(1.0F, 0.0F, 0.0F);
    private static final Vector3f FLAT_V = new Vector3f(0.0F, 0.0F, 1.0F);
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
            sigil = new Sigil(payload.at(), now - payload.age(), payload.id());
            SIGILS.put(payload.id(), sigil);
        }
        sigil.heardAt = now;
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
                ScarletFx.spark(at, new Vec3(0.0, 0.02 + random.nextDouble() * 0.03, 0.0), 14 + random.nextInt(12), 0.02F,
                        random.nextFloat() < 0.3F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET, -0.0008F, 0.93F);
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
            draws.add(new Draw(new Vector3f((float) c.x, (float) c.y + 0.03F, (float) c.z), written, sprung, fade, sigil.seed, held));
        }
        if (draws.isEmpty()) {
            return;
        }
        float time = (float) (now % 24000.0);
        GlowPass.submitTint(collector, poseStack, (pose, buffer) -> {
            for (Draw draw : draws) {
                Vector3f c = draw.center();
                float density = (draw.sprung() > 0.0F ? 0.42F : 0.24F) * draw.written() * draw.fade();
                float r = RuneTraps.RADIUS * radiusScale(draw);
                Glow.planeDisc(buffer, pose, c.x, c.y, c.z, FLAT_U, FLAT_V, r, GlowPass.tint(ScarletPalette.GLASS, density * 0.6F),
                        GlowPass.tint(ScarletPalette.GLASS, density), 48);
                Glow.annulus(buffer, pose, c.x, c.y, c.z, FLAT_U, FLAT_V, r, r * 1.08F, GlowPass.tint(ScarletPalette.GLASS, density),
                        GlowPass.tint(ScarletPalette.GLASS, 0.0F), 48);
                if (draw.sprung() > 0.0F) {
                    Glow.Billboard axes = Glow.billboard(pose);
                    for (Held held : draw.held()) {
                        chainTints(buffer, pose, axes, draw, held);
                    }
                }
            }
        });
        GlowPass.submit(collector, poseStack, (pose, buffer) -> {
            Glow.Billboard axes = Glow.billboard(pose);
            for (Draw draw : draws) {
                sigil(buffer, pose, draw, time);
                if (draw.sprung() > 0.0F) {
                    column(buffer, pose, axes, draw);
                    for (Held held : draw.held()) {
                        chains(buffer, pose, axes, draw, held, time);
                    }
                }
            }
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
     * The sigil itself, lying on the ground, written in as far as it has got.
     */
    private static void sigil(VertexConsumer buffer, PoseStack.Pose pose, Draw draw, float time) {
        Vector3f c = draw.center();
        float written = draw.written();
        float flare = draw.sprung() > 0.0F ? Math.max(0.0F, 1.0F - draw.sprung() / 10.0F) : 0.0F;
        float breathe = 0.85F + 0.15F * Mth.sin(time * 0.12F + draw.seed());
        float bright = (draw.sprung() > 0.0F ? 0.95F : 0.6F * breathe) * draw.fade() + flare * 0.6F;
        float r = RuneTraps.RADIUS * radiusScale(draw);
        float spin = time * 0.012F + draw.seed();
        // the rings trace around as the sigil is written
        arc(buffer, pose, c, r, 0.07F, spin, written, Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.9F * bright));
        arc(buffer, pose, c, r, 0.025F, spin, written, Glow.withAlpha(ScarletPalette.CORE, 0.6F * bright));
        float inner = Ease.clamp01((written - 0.2F) / 0.8F);
        arc(buffer, pose, c, r * 0.78F, 0.045F, -spin * 1.3F, inner, Glow.withAlpha(ScarletPalette.SCARLET, 0.85F * bright));
        // the runes between them light up one by one, in the order the ring passes them
        for (int g = 0; g < GLYPHS; g++) {
            float at = g / (float) GLYPHS;
            float lit = Ease.clamp01((written - at) * 6.0F);
            if (lit <= 0.0F) {
                continue;
            }
            float angle = spin + at * TAU;
            glyph(buffer, pose, c, angle, r * 0.885F, r * 0.08F, draw.seed() * 31.0F + g, Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.85F * bright * lit));
        }
        // a hexagram in the middle, turning against the runes
        float star = Ease.clamp01((written - 0.5F) / 0.5F);
        if (star > 0.0F) {
            float points = r * 0.7F;
            int color = Glow.withAlpha(ScarletPalette.SCARLET, 0.8F * bright * star);
            for (int t = 0; t < 2; t++) {
                float base = -spin * 0.7F + t * (TAU / 6.0F);
                for (int k = 0; k < 3; k++) {
                    float a0 = base + k * TAU / 3.0F;
                    float a1 = base + (k + 1) * TAU / 3.0F;
                    Glow.planeLine(buffer, pose, c.x, c.y, c.z, FLAT_U, FLAT_V, Mth.cos(a0) * points, Mth.sin(a0) * points, Mth.cos(a1) * points,
                            Mth.sin(a1) * points, 0.05F, color);
                }
            }
            Glow.ring(buffer, pose, c.x, c.y, c.z, FLAT_U, FLAT_V, r * 0.18F, 0.04F, Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.8F * bright * star), 24);
            Glow.planeDisc(buffer, pose, c.x, c.y, c.z, FLAT_U, FLAT_V, r * 0.08F, Glow.withAlpha(ScarletPalette.CORE, 0.8F * bright * star),
                    Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.0F), 16);
        }
    }

    /**
     * A ring traced around as far as {@code reach}, from 0 none to 1 all the way, starting at {@code from} radians.
     */
    private static void arc(VertexConsumer buffer, PoseStack.Pose pose, Vector3f c, float radius, float width, float from, float reach, int color) {
        if (reach <= 0.0F) {
            return;
        }
        int segments = Math.max(1, Math.round(48 * reach));
        float step = TAU * reach / segments;
        for (int i = 0; i < segments; i++) {
            float a0 = from + i * step;
            float a1 = a0 + step;
            Glow.planeLine(buffer, pose, c.x, c.y, c.z, FLAT_U, FLAT_V, Mth.cos(a0) * radius, Mth.sin(a0) * radius, Mth.cos(a1) * radius,
                    Mth.sin(a1) * radius, width, color);
        }
    }

    /**
     * One angular rune standing on the ring at {@code angle}: a stave across the band with two or three strokes off it,
     * chosen by {@code seed}, so every sigil reads differently.
     */
    private static void glyph(VertexConsumer buffer, PoseStack.Pose pose, Vector3f c, float angle, float radius, float half, float seed, int color) {
        float ox = Mth.cos(angle) * radius;
        float oz = Mth.sin(angle) * radius;
        // the band's own directions at the rune: out from the middle, and along the ring
        float rx = Mth.cos(angle);
        float rz = Mth.sin(angle);
        float tx = -rz;
        float tz = rx;
        int shape = Math.floorMod((int) (Mth.sin(seed * 12.9898F) * 43758.547F), 6);
        float width = 0.03F;
        // the stave, out across the band
        stroke(buffer, pose, c, ox, oz, rx, rz, tx, tz, 0.0F, -half, 0.0F, half, width, color);
        switch (shape) {
            case 0 -> {
                stroke(buffer, pose, c, ox, oz, rx, rz, tx, tz, 0.0F, half, half * 0.7F, half * 0.3F, width, color);
                stroke(buffer, pose, c, ox, oz, rx, rz, tx, tz, 0.0F, 0.0F, half * 0.7F, -half * 0.4F, width, color);
            }
            case 1 -> {
                stroke(buffer, pose, c, ox, oz, rx, rz, tx, tz, -half * 0.6F, half * 0.6F, half * 0.6F, -half * 0.2F, width, color);
                stroke(buffer, pose, c, ox, oz, rx, rz, tx, tz, -half * 0.6F, -half * 0.2F, half * 0.6F, half * 0.6F, width, color);
            }
            case 2 -> {
                stroke(buffer, pose, c, ox, oz, rx, rz, tx, tz, 0.0F, half * 0.2F, -half * 0.6F, half, width, color);
                stroke(buffer, pose, c, ox, oz, rx, rz, tx, tz, 0.0F, half * 0.2F, half * 0.6F, half, width, color);
            }
            case 3 -> {
                stroke(buffer, pose, c, ox, oz, rx, rz, tx, tz, 0.0F, half, half * 0.6F, 0.0F, width, color);
                stroke(buffer, pose, c, ox, oz, rx, rz, tx, tz, half * 0.6F, 0.0F, 0.0F, -half * 0.5F, width, color);
            }
            case 4 -> {
                stroke(buffer, pose, c, ox, oz, rx, rz, tx, tz, -half * 0.55F, -half, -half * 0.55F, half * 0.5F, width, color);
                stroke(buffer, pose, c, ox, oz, rx, rz, tx, tz, -half * 0.55F, half * 0.5F, 0.0F, half, width, color);
            }
            default -> stroke(buffer, pose, c, ox, oz, rx, rz, tx, tz, -half * 0.6F, 0.0F, half * 0.6F, 0.0F, width, color);
        }
    }

    /**
     * A stroke of a rune between two points given along the ring ({@code a}) and out across it ({@code b}).
     */
    private static void stroke(VertexConsumer buffer, PoseStack.Pose pose, Vector3f c, float ox, float oz, float rx, float rz, float tx, float tz,
                               float a0, float b0, float a1, float b1, float width, int color) {
        Glow.planeLine(buffer, pose, c.x, c.y, c.z, FLAT_U, FLAT_V, ox + tx * a0 + rx * b0, oz + tz * a0 + rz * b0, ox + tx * a1 + rx * b1,
                oz + tz * a1 + rz * b1, width, color);
    }

    /**
     * The column of light that bursts up out of the sigil's middle as it springs, thinning away.
     */
    private static void column(VertexConsumer buffer, PoseStack.Pose pose, Glow.Billboard axes, Draw draw) {
        float k = draw.sprung() / 12.0F;
        if (k >= 1.0F) {
            return;
        }
        Vector3f c = draw.center();
        float fade = 1.0F - Ease.outCubic(k);
        float height = 2.5F + 3.5F * Ease.outCubic(k);
        Vector3f[] points = {new Vector3f(c), new Vector3f(c.x, c.y + height * 0.5F, c.z), new Vector3f(c.x, c.y + height, c.z)};
        float[] widths = {0.9F * fade, 0.6F * fade, 0.05F};
        int[] colors = {Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.7F * fade), Glow.withAlpha(ScarletPalette.SCARLET, 0.45F * fade),
                Glow.withAlpha(ScarletPalette.SCARLET, 0.0F)};
        Glow.ribbon(buffer, pose, axes, points, widths, colors);
        Glow.disc(buffer, pose, axes, c.x, c.y + 0.2F, c.z, 1.4F * fade, Glow.withAlpha(ScarletPalette.CORE, 0.5F * fade));
    }

    /**
     * What a sprung sigil holds: bands of light closing in around it at the ankles, the waist and the chest, held by
     * chains of light running down into the sigil, straining and flickering as they give out.
     */
    private static void chains(VertexConsumer buffer, PoseStack.Pose pose, Glow.Billboard axes, Draw draw, Held held, float time) {
        float close = Ease.outBack(Ease.clamp01(draw.sprung() / 7.0F));
        float strain = 0.65F + 0.35F * Mth.sin(time * 1.7F + held.feet().x * 3.0F);
        for (Band band : bands(held, close, time)) {
            Glow.ring(buffer, pose, band.x(), band.y(), band.z(), band.u(), band.v(), band.radius(), 0.15F,
                    Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.85F * close * strain), 32);
            Glow.ring(buffer, pose, band.x(), band.y(), band.z(), band.u(), band.v(), band.radius(), 0.04F,
                    Glow.withAlpha(ScarletPalette.CORE, 0.7F * close), 32);
        }
        for (Vector3f[] link : links(draw, held, close)) {
            Glow.ribbon(buffer, pose, axes, link, new float[] {0.11F, 0.11F},
                    new int[] {Glow.withAlpha(ScarletPalette.SCARLET, 0.85F * strain), Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.85F * strain)});
            Glow.ribbon(buffer, pose, axes, link, new float[] {0.035F, 0.035F},
                    new int[] {Glow.withAlpha(ScarletPalette.CORE, 0.6F * strain), Glow.withAlpha(ScarletPalette.CORE, 0.6F * strain)});
            Vector3f b = link[1];
            Glow.spark(buffer, pose, axes, b.x, b.y, b.z, 0.035F, ScarletPalette.CORE, ScarletPalette.BRIGHT_SCARLET, 0.7F * strain);
        }
    }

    /**
     * The scarlet beneath the bands and chains, so they keep their red against bright ground and sky.
     */
    private static void chainTints(VertexConsumer buffer, PoseStack.Pose pose, Glow.Billboard axes, Draw draw, Held held) {
        float close = Ease.outBack(Ease.clamp01(draw.sprung() / 7.0F));
        int clear = GlowPass.tint(ScarletPalette.GLASS, 0.0F);
        int glass = GlowPass.tint(ScarletPalette.GLASS, 0.6F * Math.clamp(close, 0.0F, 1.0F));
        for (Band band : bands(held, close, 0.0F)) {
            Glow.annulus(buffer, pose, band.x(), band.y(), band.z(), band.u(), band.v(), band.radius() - 0.14F, band.radius(), clear, glass, 32);
            Glow.annulus(buffer, pose, band.x(), band.y(), band.z(), band.u(), band.v(), band.radius(), band.radius() + 0.14F, glass, clear, 32);
        }
        for (Vector3f[] link : links(draw, held, close)) {
            Glow.tintRibbon(buffer, pose, axes, link, new float[] {0.26F, 0.26F}, new int[] {glass, glass});
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

    private record Draw(Vector3f center, float written, float sprung, float fade, float seed, List<Held> held) {
    }

    private record Held(Vector3f feet, float width, float height) {
    }

    private static final class Sigil {
        final Vec3 at;
        final double inscribedAt;
        final float seed;
        double sprungAt = -1.0;
        double fadedAt = -1.0;
        double heardAt;
        List<Integer> bound = List.of();

        Sigil(Vec3 at, double inscribedAt, int id) {
            this.at = at;
            this.inscribedAt = inscribedAt;
            this.seed = id * 0.731F;
        }
    }
}
