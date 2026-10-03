package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.darkhold.CorruptionClient;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.magic.Spell;
import com.yashjit.scarlet.network.MagicEventPayload;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Red Mist as everyone sees it. The caster comes apart into a crimson fog that spirals up around them, streams to where
 * they are going in a long smear of red, and pulls back together into them there. The fog reddens and darkens what is
 * behind it like real mist, with embers burning through it. Arriving, your own view clears out of a red blink.
 */
public final class MistFx {

    private static final int BLINK_TICKS = 9;
    private static final float TAU = (float) (Math.PI * 2);
    private static final int MAX_PUFFS = 900;

    private static final Int2ObjectMap<Track> TRACKS = new Int2ObjectOpenHashMap<>();
    private static final List<Puff> PUFFS = new ArrayList<>();
    private static double blinkAt = -1.0E9;

    private MistFx() {
    }

    public static void onEvent(MagicEventPayload payload) {
        int kind = payload.kind();
        if (kind != MagicEventPayload.MIST_OUT && kind != MagicEventPayload.MIST_IN) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        try (Glow.Darkening ignored = Glow.darkening(CorruptionClient.darkness(level.getEntity(payload.entityId())))) {
            misted(minecraft, level, payload, kind);
        }
    }

    private static void misted(Minecraft minecraft, ClientLevel level, MagicEventPayload payload, int kind) {
        double now = level.getGameTime();
        Track track = TRACKS.computeIfAbsent(payload.entityId(), id -> new Track());
        float height = level.getEntity(payload.entityId()) instanceof Player player ? player.getBbHeight() : 1.8F;
        if (kind == MagicEventPayload.MIST_OUT) {
            track.from = payload.position();
            track.outAt = now;
            track.arrived = false;
            dissolve(payload.position(), height, 1.0F);
            return;
        }
        track.arrived = true;
        Vec3 to = payload.position();
        Vec3 from = track.from == null ? to : track.from;
        if (from.distanceToSqr(to) > 1.0) {
            dissolve(from, height, 0.7F);
            streak(from, to, height);
        }
        reform(to, height);
        if (minecraft.player != null && payload.entityId() == minecraft.player.getId()) {
            blinkAt = now;
        }
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            TRACKS.clear();
            PUFFS.clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        double now = level.getGameTime();
        Iterator<Puff> puffs = PUFFS.iterator();
        while (puffs.hasNext()) {
            if (!puffs.next().tick()) {
                puffs.remove();
            }
        }
        TRACKS.int2ObjectEntrySet().removeIf(entry -> now - entry.getValue().outAt > 60.0);
        for (Int2ObjectMap.Entry<Track> entry : TRACKS.int2ObjectEntrySet()) {
            Track track = entry.getValue();
            if (!track.arrived && now - track.outAt <= Spell.RED_MIST.windUp() && level.getEntity(entry.getIntKey()) instanceof Player player) {
                // coming apart: the fog winds up around the body, thickening
                float k = (float) (now - track.outAt) / Spell.RED_MIST.windUp();
                try (Glow.Darkening ignored = Glow.darkening(CorruptionClient.darkness(player))) {
                    swirl(player.position(), player.getBbHeight(), 0.6F + 0.8F * k);
                }
            }
        }
    }

    /**
     * A body coming apart where it stands: a column of fog, thickest at the middle, drifting up and out.
     */
    private static void dissolve(Vec3 feet, float height, float amount) {
        RandomSource random = ScarletFx.random();
        float density = ScarletFx.density() * amount;
        for (int i = 0, n = Math.round(46 * density); i < n; i++) {
            float angle = random.nextFloat() * TAU;
            float reach = 0.15F + random.nextFloat() * 0.4F;
            Vec3 at = feet.add(Mth.cos(angle) * reach, random.nextFloat() * height, Mth.sin(angle) * reach);
            Vec3 out = new Vec3(Mth.cos(angle), 0.0, Mth.sin(angle)).scale(0.02 + random.nextDouble() * 0.05).add(0.0, 0.01 + random.nextDouble() * 0.02, 0.0);
            puff(at, out, 24 + random.nextInt(18), 0.25F + random.nextFloat() * 0.2F, 0.7F + random.nextFloat() * 0.5F);
        }
        for (int i = 0, n = Math.round(18 * density); i < n; i++) {
            Vec3 at = feet.add((random.nextFloat() - 0.5F) * 0.7F, random.nextFloat() * height, (random.nextFloat() - 0.5F) * 0.7F);
            ScarletFx.spark(at, new Vec3(random.nextGaussian() * 0.03, 0.02 + random.nextDouble() * 0.04, random.nextGaussian() * 0.03), 10 + random.nextInt(10),
                    0.022F, random.nextFloat() < 0.3F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET, -0.002F, 0.9F);
        }
    }

    private static void swirl(Vec3 feet, float height, float amount) {
        RandomSource random = ScarletFx.random();
        for (int i = 0, n = Math.round(9 * ScarletFx.density() * amount); i < n; i++) {
            float angle = random.nextFloat() * TAU;
            float reach = 0.35F + random.nextFloat() * 0.25F;
            float y = random.nextFloat() * height;
            Vec3 at = feet.add(Mth.cos(angle) * reach, y, Mth.sin(angle) * reach);
            // around and up, the way smoke winds off a spinning body
            Vec3 around = new Vec3(-Mth.sin(angle), 0.0, Mth.cos(angle)).scale(0.06).add(0.0, 0.03, 0.0);
            puff(at, around, 14 + random.nextInt(8), 0.18F + random.nextFloat() * 0.12F, 0.6F + random.nextFloat() * 0.3F);
        }
    }

    /**
     * The smear of fog left along the way, already thinning as it appears.
     */
    private static void streak(Vec3 from, Vec3 to, float height) {
        RandomSource random = ScarletFx.random();
        Vec3 path = to.subtract(from);
        double length = path.length();
        float density = ScarletFx.density();
        for (int i = 0, n = Math.round((float) length * 5.0F * density); i < n; i++) {
            double s = random.nextDouble();
            Vec3 at = from.add(path.scale(s)).add(random.nextGaussian() * 0.2, height * (0.3 + random.nextDouble() * 0.5), random.nextGaussian() * 0.2);
            puff(at, path.normalize().scale(0.05).add(random.nextGaussian() * 0.01, 0.005, random.nextGaussian() * 0.01), 10 + random.nextInt(10),
                    0.2F + random.nextFloat() * 0.15F, 0.45F);
            if (random.nextFloat() < 0.35F) {
                ScarletFx.spark(at, path.normalize().scale(0.12), 6 + random.nextInt(4), 0.02F, ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET, 0.0F, 0.8F);
            }
        }
    }

    /**
     * A body pulling back together: fog rushing in from all around, then a last breath of it blown off.
     */
    private static void reform(Vec3 feet, float height) {
        RandomSource random = ScarletFx.random();
        float density = ScarletFx.density();
        Vec3 middle = feet.add(0.0, height * 0.5, 0.0);
        for (int i = 0, n = Math.round(40 * density); i < n; i++) {
            Vec3 at = middle.add(randomUnit(random).multiply(1.3, 1.0, 1.3).scale(0.8 + random.nextDouble() * 0.6));
            puff(at, middle.subtract(at).scale(0.12), 8 + random.nextInt(5), 0.3F + random.nextFloat() * 0.2F, 0.8F);
        }
        for (int i = 0, n = Math.round(20 * density); i < n; i++) {
            float angle = random.nextFloat() * TAU;
            Vec3 at = feet.add(Mth.cos(angle) * 0.3, random.nextFloat() * height, Mth.sin(angle) * 0.3);
            puff(at, new Vec3(Mth.cos(angle), 0.1, Mth.sin(angle)).scale(0.05), 20 + random.nextInt(12), 0.2F + random.nextFloat() * 0.15F, 0.5F);
        }
        for (int i = 0, n = Math.round(24 * density); i < n; i++) {
            Vec3 at = middle.add(randomUnit(random).scale(0.9));
            ScarletFx.spark(at, middle.subtract(at).scale(0.15), 6, 0.026F, random.nextFloat() < 0.4F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET,
                    ScarletPalette.SCARLET, 0.0F, 0.9F);
        }
    }

    private static void puff(Vec3 at, Vec3 velocity, int life, float size, float alpha) {
        if (PUFFS.size() < MAX_PUFFS) {
            PUFFS.add(new Puff(at, velocity, life, size, alpha, ScarletFx.random().nextFloat(), Glow.darkness()));
        }
    }

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack) {
        if (PUFFS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        int count = PUFFS.size();
        float[] values = new float[count * 7];
        for (int i = 0; i < count; i++) {
            Puff puff = PUFFS.get(i);
            float t = (puff.age + partialTick) / puff.life;
            values[i * 7] = (float) (Mth.lerp(partialTick, puff.xo, puff.x) - camera.x);
            values[i * 7 + 1] = (float) (Mth.lerp(partialTick, puff.yo, puff.y) - camera.y);
            values[i * 7 + 2] = (float) (Mth.lerp(partialTick, puff.zo, puff.z) - camera.z);
            values[i * 7 + 3] = puff.size * (0.6F + 1.2F * Ease.outCubic(t));
            values[i * 7 + 4] = puff.alpha * Ease.clamp01(t * 6.0F) * (1.0F - t) * (1.0F - t);
            values[i * 7 + 5] = puff.shade;
            values[i * 7 + 6] = puff.darkness;
        }
        GlowPass.submitTint(collector, poseStack, (pose, buffer) -> {
            Glow.Billboard axes = Glow.billboard(pose);
            float before = Glow.darkness();
            for (int i = 0; i < count; i++) {
                Glow.darken(values[i * 7 + 6]);
                Glow.tintDisc(buffer, pose, axes, values[i * 7], values[i * 7 + 1], values[i * 7 + 2], values[i * 7 + 3],
                        GlowPass.tint(ScarletPalette.GLASS, 0.55F * values[i * 7 + 4]));
            }
            Glow.darken(before);
        });
        GlowPass.submit(collector, poseStack, (pose, buffer) -> {
            Glow.Billboard axes = Glow.billboard(pose);
            float before = Glow.darkness();
            for (int i = 0; i < count; i++) {
                Glow.darken(values[i * 7 + 6]);
                int color = Glow.mix(ScarletPalette.WINE, ScarletPalette.CRIMSON, values[i * 7 + 5]);
                Glow.disc(buffer, pose, axes, values[i * 7], values[i * 7 + 1], values[i * 7 + 2], values[i * 7 + 3],
                        Glow.withAlpha(color, 0.28F * values[i * 7 + 4]));
            }
            Glow.darken(before);
        });
    }

    /**
     * Your own arrival: the view comes back out of a red blink, from the edges in.
     */
    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float t = (float) (minecraft.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false) - blinkAt) / BLINK_TICKS;
        if (t < 0.0F || t >= 1.0F) {
            return;
        }
        float fade = (1.0F - t) * (1.0F - t);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        graphics.fill(0, 0, width, height, ARGB.color(0.3F * fade, ScarletPalette.CRIMSON));
        int band = height / 3;
        graphics.fillGradient(0, 0, width, band, ARGB.color(0.6F * fade, ScarletPalette.WINE), ARGB.color(0.0F, ScarletPalette.WINE));
        graphics.fillGradient(0, height - band, width, height, ARGB.color(0.0F, ScarletPalette.WINE), ARGB.color(0.6F * fade, ScarletPalette.WINE));
    }

    private static Vec3 randomUnit(RandomSource random) {
        return new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
    }

    private static final class Track {
        @Nullable Vec3 from;
        double outAt = -1.0E9;
        boolean arrived = true;
    }

    private static final class Puff {
        double x, y, z;
        double xo, yo, zo;
        double vx, vy, vz;
        int age;
        final int life;
        final float size;
        final float alpha;
        final float shade;
        final float darkness;

        Puff(Vec3 at, Vec3 velocity, int life, float size, float alpha, float shade, float darkness) {
            this.x = this.xo = at.x;
            this.y = this.yo = at.y;
            this.z = this.zo = at.z;
            this.vx = velocity.x;
            this.vy = velocity.y;
            this.vz = velocity.z;
            this.life = life;
            this.size = size;
            this.alpha = alpha;
            this.shade = shade;
            this.darkness = darkness;
        }

        boolean tick() {
            xo = x;
            yo = y;
            zo = z;
            x += vx;
            y += vy;
            z += vz;
            vx *= 0.9;
            vy = vy * 0.9 + 0.001;
            vz *= 0.9;
            return ++age < life;
        }
    }
}
