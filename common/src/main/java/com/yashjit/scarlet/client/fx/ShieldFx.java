package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.anim.PoseBlends;
import com.yashjit.scarlet.client.magic.Hands;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.magic.Magic;
import com.yashjit.scarlet.magic.Spell;
import com.yashjit.scarlet.network.MagicEventPayload;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The Chaos Shield as everyone sees it: a disc of scarlet energy held out before the caster.
 *
 * <ul>
 *     <li>A faint membrane brightening toward a burning rim, with motes circling it.</li>
 *     <li>A honeycomb lattice that shimmers in slow waves and rings out from every blow it takes.</li>
 *     <li>Wisps spiraling across it, and ribbons of energy feeding it from both palms.</li>
 *     <li>It flares open from the hands when raised and folds away when lowered; when it breaks it bursts into
 *     tumbling shards.</li>
 * </ul>
 *
 * <p>Your own shield sits further out and fainter in first person, framing the view instead of covering it.
 */
public final class ShieldFx {

    private static final float FIRST_PERSON_DISTANCE = 1.5F;
    private static final float FIRST_PERSON_ALPHA = 0.55F;
    private static final float FIRST_PERSON_TINT = 0.45F;
    private static final float CELL = 0.15F;
    private static final float RIPPLE_TICKS = 14.0F;
    private static final float RECOIL_TICKS = 7.0F;
    private static final float SHATTER_TICKS = 16.0F;
    private static final float FORM_TICKS = 6.0F;
    private static final int MAX_RIPPLES = 6;
    private static final int SHARDS = 22;
    private static final float TAU = (float) (Math.PI * 2);
    private static final float SQRT3 = (float) Math.sqrt(3.0);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final float[] HEX_X = new float[7];
    private static final float[] HEX_Y = new float[7];

    static {
        for (int k = 0; k < 7; k++) {
            double angle = Math.toRadians(30 + 60 * k);
            HEX_X[k] = (float) Math.cos(angle) * CELL;
            HEX_Y[k] = (float) Math.sin(angle) * CELL;
        }
    }

    private static final Int2ObjectMap<Track> TRACKS = new Int2ObjectOpenHashMap<>();
    private static final List<Shatter> SHATTERS = new ArrayList<>();

    private ShieldFx() {
    }

    public static void onEvent(MagicEventPayload payload) {
        boolean hit = payload.kind() == MagicEventPayload.SHIELD_HIT;
        if (!hit && payload.kind() != MagicEventPayload.SHIELD_SHATTER) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.level.getEntity(payload.entityId()) instanceof Player player)) {
            return;
        }
        float partialTick = partialTick();
        double now = minecraft.level.getGameTime() + partialTick;
        Track track = TRACKS.computeIfAbsent(player.getId(), id -> new Track());
        Frame frame = frame(player, partialTick, ownFirstPerson(player));
        track.lastHit = now;
        if (hit) {
            Vec3 offset = payload.position().subtract(frame.center());
            float x = (float) offset.dot(frame.right());
            float y = (float) offset.dot(frame.up());
            float distance = (float) Math.sqrt(x * x + y * y);
            float limit = Magic.SHIELD_RADIUS * 0.85F;
            if (distance > limit) {
                x *= limit / distance;
                y *= limit / distance;
            }
            if (track.ripples.size() >= MAX_RIPPLES) {
                track.ripples.removeFirst();
            }
            track.ripples.add(new Ripple(x, y, now));
            hitSparks(frame, x, y);
        } else {
            track.shattered = true;
            SHATTERS.add(Shatter.of(frame, now, ownFirstPerson(player), ScarletFx.random()));
            shatterSparks(frame);
        }
    }

    /**
     * How hard the last blow knocked the player's shield arms back: 1 at impact, easing to 0.
     */
    public static float recoil(Player player, double now) {
        Track track = TRACKS.get(player.getId());
        if (track == null) {
            return 0.0F;
        }
        float age = (float) (now - track.lastHit);
        if (age < 0.0F || age >= RECOIL_TICKS) {
            return 0.0F;
        }
        float k = age / RECOIL_TICKS;
        return (1.0F - k) * (1.0F - k) * Ease.clamp01(age * 2.0F + 0.4F);
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            TRACKS.clear();
            SHATTERS.clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        double now = level.getGameTime();
        SHATTERS.removeIf(shatter -> now - shatter.start() > SHATTER_TICKS + 1);
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        for (Player player : level.players()) {
            boolean shielding = Magic.state(player).channeling(Spell.CHAOS_SHIELD);
            Track track = TRACKS.get(player.getId());
            if (track == null) {
                if (!shielding) {
                    continue;
                }
                track = new Track();
                TRACKS.put(player.getId(), track);
            }
            if (shielding && !track.shielding) {
                raised(minecraft, player, track, now);
            }
            track.shielding = shielding;
            track.ripples.removeIf(ripple -> now - ripple.start() > RIPPLE_TICKS);
            if (shielding && !track.shattered && !player.isInvisible() && player.distanceToSqr(camera) < 64 * 64) {
                ambient(player);
            }
        }
    }

    public static void prune(ClientLevel level) {
        TRACKS.int2ObjectEntrySet().removeIf(entry -> level.getEntity(entry.getIntKey()) == null);
    }

    private static void raised(Minecraft minecraft, Player player, Track track, double now) {
        track.raisedAt = now;
        track.shattered = false;
        track.ripples.clear();
        if (track.hum == null || track.hum.isStopped()) {
            track.hum = new Hum(player, track, player == minecraft.player);
            minecraft.getSoundManager().play(track.hum);
        }
        RandomSource random = ScarletFx.random();
        Frame frame = frame(player, 1.0F, ownFirstPerson(player));
        float density = ScarletFx.density();
        if (!ownFirstPerson(player)) {
            for (HumanoidArm arm : HumanoidArm.values()) {
                Vec3 palm = Hands.palm(player, arm);
                Vec3 toward = frame.center().subtract(palm);
                for (int i = 0, n = Math.round(8 * density); i < n; i++) {
                    ScarletFx.spark(palm, toward.scale(0.22).add(randomUnit(random).scale(0.03)), 5 + random.nextInt(4), 0.024F,
                            ScarletPalette.CORE, ScarletPalette.BRIGHT_SCARLET, 0.0F, 0.82F);
                }
            }
        }
        for (int i = 0, n = Math.round(26 * density); i < n; i++) {
            float angle = random.nextFloat() * TAU;
            Vec3 out = disc(frame, Mth.cos(angle), Mth.sin(angle));
            Vec3 tangent = disc(frame, -Mth.sin(angle), Mth.cos(angle));
            ScarletFx.spark(frame.center().add(out.scale(Magic.SHIELD_RADIUS)), tangent.scale(0.05 + random.nextDouble() * 0.04).add(out.scale(0.02)),
                    6 + random.nextInt(6), 0.024F, random.nextFloat() < 0.4F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET,
                    ScarletPalette.SCARLET, 0.0F, 0.86F);
        }
    }

    private static void ambient(Player player) {
        if (PoseBlends.of(player).shield < 0.5F) {
            return;
        }
        RandomSource random = ScarletFx.random();
        Frame frame = frame(player, 1.0F, ownFirstPerson(player));
        float density = ScarletFx.density();
        for (int i = 0, n = count(random, 1.4F * density); i < n; i++) {
            float angle = random.nextFloat() * TAU;
            Vec3 out = disc(frame, Mth.cos(angle), Mth.sin(angle));
            Vec3 at = frame.center().add(out.scale(Magic.SHIELD_RADIUS * (0.95 + random.nextDouble() * 0.07)));
            ScarletFx.spark(at, out.scale(0.02).add(frame.normal().scale(0.01)).add(randomUnit(random).scale(0.008)), 8 + random.nextInt(7),
                    0.022F, random.nextFloat() < 0.3F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET, -0.001F, 0.9F);
        }
        if (random.nextFloat() < 0.3F * density) {
            float angle = random.nextFloat() * TAU;
            Vec3 out = disc(frame, Mth.cos(angle), Mth.sin(angle));
            ChaosDust.spawn(frame.center().add(out.scale(Magic.SHIELD_RADIUS)), out.scale(0.02));
        }
    }

    private static void hitSparks(Frame frame, float x, float y) {
        RandomSource random = ScarletFx.random();
        Vec3 at = frame.center().add(disc(frame, x, y));
        float density = ScarletFx.density();
        for (int i = 0, n = Math.round(16 * density); i < n; i++) {
            Vec3 spray = disc(frame, (float) random.nextGaussian(), (float) random.nextGaussian()).scale(0.06);
            ScarletFx.spark(at, frame.normal().scale(0.06 + random.nextDouble() * 0.14).add(spray), 5 + random.nextInt(6), 0.026F,
                    random.nextFloat() < 0.4F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET, 0.008F, 0.84F);
        }
        for (int i = 0, n = Math.round(6 * density); i < n; i++) {
            ScarletFx.spark(at, randomUnit(random).scale(0.04).add(frame.normal().scale(0.03)), 14 + random.nextInt(10), 0.03F,
                    ScarletPalette.CRIMSON, ScarletPalette.WINE, 0.004F, 0.9F);
        }
        for (int i = 0, n = Math.round(5 * density); i < n; i++) {
            ChaosDust.spawn(at, frame.normal().scale(0.04).add(randomUnit(random).scale(0.04)));
        }
    }

    private static void shatterSparks(Frame frame) {
        RandomSource random = ScarletFx.random();
        float density = ScarletFx.density();
        for (int i = 0, n = Math.round(46 * density); i < n; i++) {
            float angle = random.nextFloat() * TAU;
            float reach = (float) Math.sqrt(random.nextFloat()) * Magic.SHIELD_RADIUS;
            Vec3 out = disc(frame, Mth.cos(angle), Mth.sin(angle));
            Vec3 at = frame.center().add(out.scale(reach));
            ScarletFx.spark(at, out.scale(0.1 + random.nextDouble() * 0.15).add(frame.normal().scale(0.05 + random.nextDouble() * 0.1)),
                    7 + random.nextInt(9), 0.028F, random.nextFloat() < 0.35F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET,
                    ScarletPalette.SCARLET, 0.01F, 0.86F);
        }
        for (int i = 0, n = Math.round(14 * density); i < n; i++) {
            ScarletFx.spark(frame.center().add(randomUnit(random).scale(0.4)), randomUnit(random).scale(0.05), 18 + random.nextInt(12), 0.034F,
                    ScarletPalette.CRIMSON, ScarletPalette.WINE, 0.004F, 0.9F);
        }
        for (int i = 0, n = Math.round(18 * density); i < n; i++) {
            ChaosDust.spawn(frame.center().add(randomUnit(random).scale(0.6)), randomUnit(random).scale(0.06));
        }
    }

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || TRACKS.isEmpty() && SHATTERS.isEmpty()) {
            return;
        }
        float partialTick = partialTick();
        double now = level.getGameTime() + partialTick;
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        List<Draw> draws = new ArrayList<>();
        for (Player player : level.players()) {
            Track track = TRACKS.get(player.getId());
            if (track == null || track.shattered || player.isInvisible() || player.distanceToSqr(camera) > 96 * 96) {
                continue;
            }
            float presence = PoseBlends.of(player).shield;
            if (presence < 0.01F) {
                continue;
            }
            boolean own = ownFirstPerson(player);
            Frame frame = frame(player, partialTick, own);
            Vector3f rightPalm = own ? null : Hands.palm(player, HumanoidArm.RIGHT).subtract(camera).toVector3f();
            Vector3f leftPalm = own ? null : Hands.palm(player, HumanoidArm.LEFT).subtract(camera).toVector3f();
            draws.add(new Draw(frame.center().subtract(camera).toVector3f(), frame.normal().toVector3f(), frame.right().toVector3f(),
                    frame.up().toVector3f(), presence, own, List.copyOf(track.ripples), track.raisedAt, rightPalm, leftPalm,
                    (player.getId() * 0.618F) % 1.0F * TAU));
        }
        List<Shatter> shatters = List.copyOf(SHATTERS);
        if (draws.isEmpty() && shatters.isEmpty()) {
            return;
        }
        GlowPass.submitTint(collector, poseStack, (pose, buffer) -> {
            for (Draw draw : draws) {
                tintShield(buffer, pose, draw);
            }
            for (Shatter shatter : shatters) {
                tintShatter(buffer, pose, shatter, now, camera);
            }
        });
        GlowPass.submit(collector, poseStack, (pose, buffer) -> {
            Glow.Billboard axes = Glow.billboard(pose);
            for (Draw draw : draws) {
                drawShield(buffer, pose, axes, draw, now);
            }
            for (Shatter shatter : shatters) {
                drawShatter(buffer, pose, axes, shatter, now, camera);
            }
        });
    }

    /**
     * Red glass under the light: deepest at the rim, fading out just past it.
     */
    private static void tintShield(VertexConsumer buffer, PoseStack.Pose pose, Draw draw) {
        float presence = Ease.clamp01(draw.presence());
        float radius = radius(presence);
        float fade = Ease.outCubic(presence) * (draw.own() ? FIRST_PERSON_TINT : 1.0F);
        Vector3f c = draw.center();
        Vector3f u = draw.right();
        Vector3f v = draw.up();
        Glow.planeDisc(buffer, pose, c.x, c.y, c.z, u, v, radius * 0.8F, GlowPass.tint(ScarletPalette.GLASS, 0.3F * fade),
                GlowPass.tint(ScarletPalette.GLASS, 0.5F * fade), 36);
        Glow.annulus(buffer, pose, c.x, c.y, c.z, u, v, radius * 0.8F, radius, GlowPass.tint(ScarletPalette.GLASS, 0.5F * fade),
                GlowPass.tint(ScarletPalette.GLASS, 0.72F * fade), 36);
        Glow.annulus(buffer, pose, c.x, c.y, c.z, u, v, radius, radius * 1.05F, GlowPass.tint(ScarletPalette.GLASS, 0.72F * fade),
                GlowPass.tint(ScarletPalette.GLASS, 0.0F), 36);
    }

    private static void tintShatter(VertexConsumer buffer, PoseStack.Pose pose, Shatter shatter, double now, Vec3 camera) {
        float t = (float) (now - shatter.start());
        if (t < 0.0F || t > SHATTER_TICKS) {
            return;
        }
        float fade = (float) Math.pow(1.0F - t / SHATTER_TICKS, 1.3) * (shatter.own() ? FIRST_PERSON_TINT : 1.0F);
        for (Shard shard : shatter.shards()) {
            ShardPose at = ShardPose.of(shatter, shard, t, camera);
            Glow.planeDisc(buffer, pose, at.x(), at.y(), at.z(), at.u(), at.v(), at.size(), GlowPass.tint(ScarletPalette.GLASS, 0.6F * fade),
                    GlowPass.tint(ScarletPalette.GLASS, 0.4F * fade), 6);
        }
    }

    private static float radius(float presence) {
        return Magic.SHIELD_RADIUS * (0.3F + 0.7F * Ease.outBack(presence));
    }

    private static void drawShield(VertexConsumer buffer, PoseStack.Pose pose, Glow.Billboard axes, Draw draw, double now) {
        float time = (float) (now % 24000.0);
        float presence = Ease.clamp01(draw.presence());
        float radius = radius(presence);
        float fade = Ease.outCubic(presence) * (draw.own() ? FIRST_PERSON_ALPHA : 1.0F);
        Vector3f c = draw.center();
        Vector3f u = draw.right();
        Vector3f v = draw.up();
        Vector3f n = draw.normal();

        Glow.planeDisc(buffer, pose, c.x, c.y, c.z, u, v, radius * 0.8F, Glow.withAlpha(ScarletPalette.SCARLET, 0.02F * fade),
                Glow.withAlpha(ScarletPalette.SCARLET, 0.05F * fade), 36);
        Glow.annulus(buffer, pose, c.x, c.y, c.z, u, v, radius * 0.8F, radius, Glow.withAlpha(ScarletPalette.SCARLET, 0.05F * fade),
                Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.14F * fade), 36);
        lattice(buffer, pose, c, u, v, radius, fade, time, draw.ripples(), now);
        wisps(buffer, pose, axes, c, u, v, n, radius, fade, time, draw.seed());

        float pulse = 0.85F + 0.15F * Mth.sin(time * 0.35F);
        Glow.ring(buffer, pose, c.x, c.y, c.z, u, v, radius, 0.4F, Glow.withAlpha(ScarletPalette.SCARLET, 0.16F * fade), 44);
        Glow.ring(buffer, pose, c.x, c.y, c.z, u, v, radius, 0.085F, Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.75F * fade * pulse), 44);
        Glow.ring(buffer, pose, c.x, c.y, c.z, u, v, radius * 0.962F, 0.022F, Glow.withAlpha(ScarletPalette.CORE, 0.32F * fade), 44);
        for (int i = 0; i < 6; i++) {
            float angle = time * (0.07F + 0.015F * (i % 3)) * (i % 2 == 0 ? 1.0F : -1.0F) + i * TAU / 6.0F + draw.seed();
            float reach = radius * (1.0F + 0.015F * Mth.sin(time * 0.3F + i));
            float x = Mth.cos(angle) * reach;
            float y = Mth.sin(angle) * reach;
            Glow.spark(buffer, pose, axes, c.x + u.x * x + v.x * y, c.y + u.y * x + v.y * y, c.z + u.z * x + v.z * y, 0.032F,
                    ScarletPalette.CORE, ScarletPalette.BRIGHT_SCARLET, fade * 0.9F);
        }

        float formed = (float) (now - draw.raisedAt());
        if (formed >= 0.0F && formed < FORM_TICKS) {
            float k = formed / FORM_TICKS;
            float flash = (float) Math.pow(1.0F - k, 1.5);
            Glow.ring(buffer, pose, c.x, c.y, c.z, u, v, radius * (0.5F + 0.8F * Ease.outCubic(k)), 0.25F * (1.0F - k) + 0.04F,
                    Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.8F * flash * fade), 44);
            if (!draw.own()) {
                Glow.disc(buffer, pose, axes, c.x, c.y, c.z, 0.9F, Glow.withAlpha(ScarletPalette.SCARLET, 0.3F * flash));
                Glow.disc(buffer, pose, axes, c.x, c.y, c.z, 0.35F, Glow.withAlpha(ScarletPalette.CORE, 0.5F * flash));
            }
        }

        for (Ripple ripple : draw.ripples()) {
            float age = (float) (now - ripple.start());
            if (age < 0.0F || age > RIPPLE_TICKS) {
                continue;
            }
            float k = age / RIPPLE_TICKS;
            float x = ripple.x();
            float y = ripple.y();
            float px = c.x + u.x * x + v.x * y + n.x * 0.01F;
            float py = c.y + u.y * x + v.y * y + n.y * 0.01F;
            float pz = c.z + u.z * x + v.z * y + n.z * 0.01F;
            Glow.ring(buffer, pose, px, py, pz, u, v, 0.05F + 0.5F * Ease.outCubic(k), 0.12F * (1.0F - k) + 0.02F,
                    Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, (float) Math.pow(1.0F - k, 1.5) * 0.85F * fade), 24);
            if (age < 3.0F) {
                float flash = 1.0F - age / 3.0F;
                Glow.disc(buffer, pose, axes, px, py, pz, 0.35F * (0.6F + 0.4F * flash), Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.7F * flash * fade));
                Glow.disc(buffer, pose, axes, px, py, pz, 0.14F, Glow.withAlpha(ScarletPalette.CORE, flash * fade));
            }
        }

        if (draw.rightPalm() != null) {
            link(buffer, pose, axes, draw.rightPalm(), c, u, v, n, radius, fade, time, 1.0F);
        }
        if (draw.leftPalm() != null) {
            link(buffer, pose, axes, draw.leftPalm(), c, u, v, n, radius, fade, time, -1.0F);
        }
    }

    /**
     * The honeycomb: each cell draws three of its six edges so every shared edge is drawn once.
     */
    private static void lattice(VertexConsumer buffer, PoseStack.Pose pose, Vector3f c, Vector3f u, Vector3f v, float radius, float fade,
                                float time, List<Ripple> ripples, double now) {
        float rowStep = CELL * 1.5F;
        float columnStep = CELL * SQRT3;
        float reach = radius + CELL;
        int rows = (int) Math.ceil(reach / rowStep);
        for (int r = -rows; r <= rows; r++) {
            float cy = r * rowStep;
            int qMin = (int) Math.floor(-reach / columnStep - r * 0.5F);
            int qMax = (int) Math.ceil(reach / columnStep - r * 0.5F);
            for (int q = qMin; q <= qMax; q++) {
                float cx = columnStep * (q + r * 0.5F);
                if (cx * cx + cy * cy > reach * reach) {
                    continue;
                }
                float phase = hash(q, r) * TAU;
                for (int k = 0; k < 3; k++) {
                    float ax = cx + HEX_X[k];
                    float ay = cy + HEX_Y[k];
                    float bx = cx + HEX_X[k + 1];
                    float by = cy + HEX_Y[k + 1];
                    float mx = (ax + bx) * 0.5F;
                    float my = (ay + by) * 0.5F;
                    float d = (float) Math.sqrt(mx * mx + my * my);
                    float edge = 1.0F - smoothstep(radius * 0.78F, radius * 0.97F, d);
                    if (edge <= 0.0F) {
                        continue;
                    }
                    float rim = d / radius;
                    float fresnel = 0.25F + 0.75F * rim * rim;
                    float wave = 0.55F + 0.45F * Mth.sin(d * 6.5F - time * 0.25F + phase * 0.3F);
                    float flicker = 0.8F + 0.2F * Mth.sin(time * 0.5F + phase);
                    float ripple = 0.0F;
                    for (Ripple hit : ripples) {
                        float age = (float) (now - hit.start());
                        if (age < 0.0F || age > RIPPLE_TICKS) {
                            continue;
                        }
                        float front = 0.05F + 1.7F * Ease.outCubic(age / RIPPLE_TICKS);
                        float dx = mx - hit.x();
                        float dy = my - hit.y();
                        float band = ((float) Math.sqrt(dx * dx + dy * dy) - front) / 0.13F;
                        float envelope = 1.0F - age / RIPPLE_TICKS;
                        ripple += envelope * envelope * (float) Math.exp(-band * band);
                    }
                    ripple = Math.min(1.0F, ripple);
                    float alpha = fade * edge * (0.17F * fresnel * wave * flicker + 0.9F * ripple);
                    if (alpha < 0.004F) {
                        continue;
                    }
                    // a small gap at every corner, so the cells read as tiles
                    float ix = (bx - ax) * 0.08F;
                    float iy = (by - ay) * 0.08F;
                    int color = Glow.mix(ScarletPalette.BRIGHT_SCARLET, ScarletPalette.CORE, ripple * 0.7F);
                    Glow.planeLine(buffer, pose, c.x, c.y, c.z, u, v, ax + ix, ay + iy, bx - ix, by - iy, 0.035F, Glow.withAlpha(color, alpha));
                }
            }
        }
    }

    private static void wisps(VertexConsumer buffer, PoseStack.Pose pose, Glow.Billboard axes, Vector3f c, Vector3f u, Vector3f v, Vector3f n,
                              float radius, float fade, float time, float seed) {
        int arms = 5;
        int count = 11;
        for (int i = 0; i < arms; i++) {
            float base = i * TAU / arms + time * 0.045F + seed;
            Vector3f[] points = new Vector3f[count];
            float[] widths = new float[count];
            float[] cores = new float[count];
            int[] colors = new int[count];
            int[] coreColors = new int[count];
            for (int j = 0; j < count; j++) {
                float s = j / (float) (count - 1);
                float reach = radius * (0.1F + 0.82F * s);
                float theta = base + s * 2.2F + 0.3F * Mth.sin(time * 0.06F + i * 1.7F + s * 4.0F);
                float depth = 0.025F * Mth.sin(time * 0.09F + i + s * 5.0F);
                float x = Mth.cos(theta) * reach;
                float y = Mth.sin(theta) * reach;
                points[j] = new Vector3f(c.x + u.x * x + v.x * y + n.x * depth, c.y + u.y * x + v.y * y + n.y * depth,
                        c.z + u.z * x + v.z * y + n.z * depth);
                float envelope = Mth.sin(s * (float) Math.PI);
                widths[j] = 0.02F + 0.1F * envelope;
                cores[j] = widths[j] * 0.3F;
                colors[j] = Glow.withAlpha(ScarletPalette.SCARLET, fade * 0.22F * envelope);
                coreColors[j] = Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, fade * 0.35F * envelope);
            }
            Glow.ribbon(buffer, pose, axes, points, widths, colors);
            Glow.ribbon(buffer, pose, axes, points, cores, coreColors);
        }
    }

    /**
     * A ribbon of energy from a palm into the shield, with beads of light running along it.
     */
    private static void link(VertexConsumer buffer, PoseStack.Pose pose, Glow.Billboard axes, Vector3f palm, Vector3f c, Vector3f u, Vector3f v,
                             Vector3f n, float radius, float fade, float time, float side) {
        Vector3f target = new Vector3f(c).add(new Vector3f(u).mul(side * radius * 0.22F)).add(new Vector3f(v).mul(-0.08F));
        Vector3f control = new Vector3f(palm).add(target).mul(0.5F).add(new Vector3f(v).mul(0.06F)).add(new Vector3f(n).mul(-0.04F));
        int count = 7;
        Vector3f[] points = new Vector3f[count];
        float[] widths = new float[count];
        int[] colors = new int[count];
        for (int i = 0; i < count; i++) {
            float s = i / (float) (count - 1);
            float wobble = 0.025F * Mth.sin(time * 0.4F + s * 6.0F + side) * Mth.sin(s * (float) Math.PI);
            points[i] = bezier(palm, control, target, s).add(new Vector3f(u).mul(wobble));
            widths[i] = 0.05F + 0.11F * s;
            colors[i] = Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, fade * (0.45F - 0.25F * s));
        }
        Glow.ribbon(buffer, pose, axes, points, widths, colors);
        for (int i = 0; i < 2; i++) {
            float s = (time * 0.12F + i * 0.5F + side * 0.25F) % 1.0F;
            if (s < 0.0F) {
                s += 1.0F;
            }
            Vector3f bead = bezier(palm, control, target, s);
            Glow.spark(buffer, pose, axes, bead.x, bead.y, bead.z, 0.03F, ScarletPalette.CORE, ScarletPalette.BRIGHT_SCARLET,
                    fade * Mth.sin(s * (float) Math.PI));
        }
    }

    private static void drawShatter(VertexConsumer buffer, PoseStack.Pose pose, Glow.Billboard axes, Shatter shatter, double now, Vec3 camera) {
        float t = (float) (now - shatter.start());
        if (t < 0.0F || t > SHATTER_TICKS) {
            return;
        }
        float k = t / SHATTER_TICKS;
        float fade = (float) Math.pow(1.0F - k, 1.3) * (shatter.own() ? FIRST_PERSON_ALPHA : 1.0F);
        Vector3f c = shatter.center().subtract(camera).toVector3f();
        Vector3f u = shatter.right().toVector3f();
        Vector3f v = shatter.up().toVector3f();
        if (t < 4.0F) {
            float flash = 1.0F - t / 4.0F;
            if (!shatter.own()) {
                Glow.disc(buffer, pose, axes, c.x, c.y, c.z, 1.5F, Glow.withAlpha(ScarletPalette.SCARLET, 0.35F * flash));
            }
            Glow.disc(buffer, pose, axes, c.x, c.y, c.z, 0.7F, Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.6F * flash * fade));
            Glow.disc(buffer, pose, axes, c.x, c.y, c.z, 0.3F, Glow.withAlpha(ScarletPalette.CORE, flash * fade));
        }
        Glow.ring(buffer, pose, c.x, c.y, c.z, u, v, Magic.SHIELD_RADIUS * (1.0F + 1.1F * Ease.outCubic(k)), 0.18F * (1.0F - k) + 0.03F,
                Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.8F * fade), 44);
        for (Shard shard : shatter.shards()) {
            ShardPose at = ShardPose.of(shatter, shard, t, camera);
            float size = at.size();
            Glow.planeDisc(buffer, pose, at.x(), at.y(), at.z(), at.u(), at.v(), size, Glow.withAlpha(ScarletPalette.SCARLET, 0.18F * fade),
                    Glow.withAlpha(ScarletPalette.SCARLET, 0.06F * fade), 6);
            int edge = Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.9F * fade);
            for (int i = 0; i < 6; i++) {
                double a0 = TAU * i / 6.0;
                double a1 = TAU * (i + 1) / 6.0;
                Glow.planeLine(buffer, pose, at.x(), at.y(), at.z(), at.u(), at.v(), (float) Math.cos(a0) * size, (float) Math.sin(a0) * size,
                        (float) Math.cos(a1) * size, (float) Math.sin(a1) * size, 0.03F, edge);
            }
        }
    }

    private static Frame frame(Player player, float partialTick, boolean ownFirstPerson) {
        Vec3 look = player.getViewVector(partialTick);
        Vec3 right = look.cross(UP);
        if (right.lengthSqr() < 1.0E-4) {
            double yaw = Math.toRadians(player.getViewYRot(partialTick));
            right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        }
        right = right.normalize();
        Vec3 up = right.cross(look).normalize();
        Vec3 center = ownFirstPerson
                ? player.getEyePosition(partialTick).add(look.scale(FIRST_PERSON_DISTANCE)).add(up.scale(-0.12))
                : Magic.shieldCenter(player, partialTick);
        return new Frame(center, look, right, up);
    }

    /**
     * A point on the shield's plane, in plane coordinates.
     */
    private static Vec3 disc(Frame frame, float x, float y) {
        return frame.right().scale(x).add(frame.up().scale(y));
    }

    private static boolean ownFirstPerson(Player player) {
        return ScarletFx.isFirstPersonViewOf(player);
    }

    private static Vector3f bezier(Vector3f a, Vector3f b, Vector3f c, float t) {
        float s = 1.0F - t;
        return new Vector3f(a).mul(s * s).add(new Vector3f(b).mul(2.0F * s * t)).add(new Vector3f(c).mul(t * t));
    }

    private static float smoothstep(float from, float to, float x) {
        float t = Ease.clamp01((x - from) / (to - from));
        return t * t * (3.0F - 2.0F * t);
    }

    private static float hash(int q, int r) {
        int h = q * 73856093 ^ r * 19349663;
        h ^= h >>> 13;
        h *= 0x5BD1E995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535.0F;
    }

    private static int count(RandomSource random, float expected) {
        return (int) expected + (random.nextFloat() < expected % 1.0F ? 1 : 0);
    }

    private static Vec3 randomUnit(RandomSource random) {
        return new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
    }

    private static float partialTick() {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    private record Frame(Vec3 center, Vec3 normal, Vec3 right, Vec3 up) {
    }

    private record Ripple(float x, float y, double start) {
    }

    private record Draw(Vector3f center, Vector3f normal, Vector3f right, Vector3f up, float presence, boolean own, List<Ripple> ripples,
                        double raisedAt, @Nullable Vector3f rightPalm, @Nullable Vector3f leftPalm, float seed) {
    }

    private record Shard(float x, float y, Vector3f velocity, float angle, float spin, float size) {
    }

    /**
     * Where a shard has flown, relative to the camera, and the plane it now lies in as it tumbles.
     */
    private record ShardPose(float x, float y, float z, Vector3f u, Vector3f v, float size) {

        static ShardPose of(Shatter shatter, Shard shard, float t, Vec3 camera) {
            Vector3f c = shatter.center().subtract(camera).toVector3f();
            Vector3f u = shatter.right().toVector3f();
            Vector3f v = shatter.up().toVector3f();
            Vector3f n = shatter.normal().toVector3f();
            Vector3f velocity = shard.velocity();
            float travel = 10.0F * (1.0F - (float) Math.pow(0.9, t));
            float x = c.x + u.x * shard.x() + v.x * shard.y() + velocity.x * travel;
            float y = c.y + u.y * shard.x() + v.y * shard.y() + velocity.y * travel - 0.0025F * t * t;
            float z = c.z + u.z * shard.x() + v.z * shard.y() + velocity.z * travel;
            float tumble = shard.spin() * t;
            Vector3f tilted = new Vector3f(u).mul(Mth.cos(tumble)).add(new Vector3f(n).mul(Mth.sin(tumble)));
            float turn = shard.angle() + shard.spin() * t * 0.6F;
            Vector3f su = new Vector3f(tilted).mul(Mth.cos(turn)).add(new Vector3f(v).mul(Mth.sin(turn)));
            Vector3f sv = new Vector3f(v).mul(Mth.cos(turn)).sub(new Vector3f(tilted).mul(Mth.sin(turn)));
            return new ShardPose(x, y, z, su, sv, shard.size() * (1.0F - 0.4F * t / SHATTER_TICKS));
        }
    }

    private record Shatter(Vec3 center, Vec3 normal, Vec3 right, Vec3 up, double start, boolean own, Shard[] shards) {

        static Shatter of(Frame frame, double now, boolean own, RandomSource random) {
            Shard[] shards = new Shard[SHARDS];
            for (int i = 0; i < SHARDS; i++) {
                float reach = Magic.SHIELD_RADIUS * 0.9F * (float) Math.sqrt(random.nextFloat());
                float angle = random.nextFloat() * TAU;
                float x = Mth.cos(angle) * reach;
                float y = Mth.sin(angle) * reach;
                float out = 0.05F + random.nextFloat() * 0.07F;
                Vec3 velocity = disc(frame, Mth.cos(angle) * out, Mth.sin(angle) * out)
                        .add(frame.normal().scale(0.03 + random.nextDouble() * 0.06)).add(0, 0.02, 0);
                shards[i] = new Shard(x, y, velocity.toVector3f(), random.nextFloat() * TAU, (random.nextFloat() - 0.5F) * 0.5F,
                        CELL * (0.6F + random.nextFloat() * 0.5F));
            }
            return new Shatter(frame.center(), frame.normal(), frame.right(), frame.up(), now, own, shards);
        }
    }

    private static final class Track {
        boolean shielding;
        boolean shattered;
        double raisedAt = -1.0E9;
        double lastHit = -1.0E9;
        final List<Ripple> ripples = new ArrayList<>();
        @Nullable Hum hum;
    }

    /**
     * The shield's hum, swelling and fading with it.
     */
    private static final class Hum extends AbstractTickableSoundInstance {

        private final Player player;
        private final Track track;

        Hum(Player player, Track track, boolean own) {
            super(SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.track = track;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001F;
            this.pitch = 1.6F;
            this.relative = own;
            if (!own) {
                follow();
            }
        }

        @Override
        public void tick() {
            if (player.isRemoved() || track.shattered) {
                stop();
                return;
            }
            float presence = PoseBlends.of(player).shield;
            if (!track.shielding && presence < 0.02F) {
                stop();
                return;
            }
            volume = 0.38F * presence + 0.001F;
            pitch = 1.55F + 0.15F * presence;
            if (!relative) {
                follow();
            }
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        private void follow() {
            x = player.getX();
            y = player.getEyeY();
            z = player.getZ();
        }
    }
}
