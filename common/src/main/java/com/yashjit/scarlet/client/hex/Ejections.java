package com.yashjit.scarlet.client.hex;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.fx.ChaosDust;
import com.yashjit.scarlet.client.fx.ScarletFx;
import com.yashjit.scarlet.client.magic.Hands;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.hex.HexEjection;
import com.yashjit.scarlet.network.EjectPayload;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Being thrown out of a Hex, as this client sees it.
 *
 * <ul>
 *     <li>Held: tendrils of scarlet wind out of both of the caster's palms and wrap around whoever they hold, who hangs
 *     in the air in a red haze, light rings turning around them.</li>
 *     <li>Thrown: they streak away in a long arc of red light, high over the town and out through the wall, shedding
 *     sparks and dust as they go.</li>
 * </ul>
 *
 * <p>If you are the one held or thrown, your own game carries you, so you hang there and fly along the same arc the
 * server has worked out.
 */
public final class Ejections {

    private static final int TENDRILS = 4;
    private static final int POINTS = 12;
    private static final int TRAIL = 14;

    private static final Int2ObjectMap<Held> HELD = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<Throw> THROWS = new Int2ObjectOpenHashMap<>();
    private static @Nullable ClientLevel seenLevel;

    private Ejections() {
    }

    public static void receive(EjectPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        double now = level.getGameTime();
        int target = payload.targetId();
        switch (payload.stage()) {
            case EjectPayload.SEIZED -> {
                THROWS.remove(target);
                Entity entity = level.getEntity(target);
                HELD.put(target, new Held(payload.casterId(), payload.from(), entity == null ? payload.from() : entity.position(), now));
            }
            case EjectPayload.FLUNG -> {
                HELD.remove(target);
                THROWS.put(target, new Throw(payload.from(), payload.to(), now, payload.ticks()));
                if (level.getEntity(payload.casterId()) instanceof Player caster) {
                    // the caster's arms fly open as they throw
                    HexClient.flingFrom(caster, now);
                }
            }
            default -> HELD.remove(target);
        }
    }

    /**
     * Whether a caster is holding someone up to throw them, for their pose.
     */
    public static boolean holds(Player caster) {
        for (Held held : HELD.values()) {
            if (held.casterId == caster.getId()) {
                return true;
            }
        }
        return false;
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level != seenLevel) {
            seenLevel = level;
            HELD.clear();
            THROWS.clear();
        }
        if (level == null || minecraft.isPaused()) {
            return;
        }
        double now = level.getGameTime();
        HELD.int2ObjectEntrySet().removeIf(entry -> now - entry.getValue().since > 80 || level.getEntity(entry.getIntKey()) == null);
        THROWS.int2ObjectEntrySet().removeIf(entry -> now - entry.getValue().start > entry.getValue().ticks + 4 || level.getEntity(entry.getIntKey()) == null);
        LocalPlayer player = minecraft.player;
        if (player != null) {
            carry(player, now);
        }
        RandomSource random = ScarletFx.random();
        for (Int2ObjectMap.Entry<Throw> entry : THROWS.int2ObjectEntrySet()) {
            Entity entity = level.getEntity(entry.getIntKey());
            if (entity == null) {
                continue;
            }
            Throw thrown = entry.getValue();
            Vec3 at = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
            thrown.trail.addFirst(at);
            while (thrown.trail.size() > TRAIL) {
                thrown.trail.removeLast();
            }
            for (int i = 0, n = Math.round(3 * ScarletFx.density()); i < n; i++) {
                ScarletFx.spark(at.add(random.nextGaussian() * 0.25, random.nextGaussian() * 0.25, random.nextGaussian() * 0.25),
                        new Vec3(random.nextGaussian() * 0.03, random.nextGaussian() * 0.03, random.nextGaussian() * 0.03), 8 + random.nextInt(8), 0.026F,
                        random.nextFloat() < 0.3F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET, 0.002F, 0.88F);
            }
            if (random.nextFloat() < 0.6F * ScarletFx.density()) {
                ChaosDust.spawn(at, new Vec3(random.nextGaussian() * 0.02, -0.01, random.nextGaussian() * 0.02));
            }
        }
        for (Int2ObjectMap.Entry<Held> entry : HELD.int2ObjectEntrySet()) {
            Entity entity = level.getEntity(entry.getIntKey());
            if (entity != null && random.nextFloat() < 0.7F * ScarletFx.density()) {
                Vec3 at = entity.position().add(random.nextGaussian() * 0.35, random.nextDouble() * entity.getBbHeight(), random.nextGaussian() * 0.35);
                ScarletFx.spark(at, new Vec3(0.0, 0.015, 0.0), 10 + random.nextInt(8), 0.02F, ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET,
                        -0.001F, 0.9F);
            }
        }
    }

    /**
     * Carries you, if you are the one held or thrown: up into the air to hang there, or along the arc of the throw.
     */
    private static void carry(LocalPlayer player, double now) {
        Throw thrown = THROWS.get(player.getId());
        if (thrown != null) {
            float t = (float) ((now - thrown.start) / thrown.ticks);
            if (t <= 1.0F) {
                Vec3 at = HexEjection.position(thrown.from, thrown.to, t);
                player.setPos(at.x, at.y, at.z);
                player.setDeltaMovement(Vec3.ZERO);
                player.resetFallDistance();
            } else if (!thrown.landed) {
                // touching down skidding on, away from the Hex
                thrown.landed = true;
                Vec3 skid = thrown.to.subtract(thrown.from).multiply(1.0, 0.0, 1.0).normalize().scale(0.6);
                player.setDeltaMovement(skid.x, 0.15, skid.z);
            }
            return;
        }
        Held held = HELD.get(player.getId());
        if (held != null) {
            float rise = Ease.outCubic((float) Math.min(1.0, (now - held.since) / 8.0));
            Vec3 at = held.start.lerp(held.hang, rise).add(0.0, Math.sin((now - held.since) * 0.15) * 0.08 * rise, 0.0);
            player.setPos(at.x, at.y, at.z);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
        }
    }

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || HELD.isEmpty() && THROWS.isEmpty()) {
            return;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + partialTick;
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        float time = (float) (now % 24000.0);
        List<HeldDraw> held = new ArrayList<>();
        for (Int2ObjectMap.Entry<Held> entry : HELD.int2ObjectEntrySet()) {
            Entity entity = level.getEntity(entry.getIntKey());
            if (entity == null || !(level.getEntity(entry.getValue().casterId) instanceof Player caster)) {
                continue;
            }
            float shown = Ease.clamp01((float) ((now - entry.getValue().since) / 6.0));
            Vec3 center = entity.getPosition(partialTick).add(0.0, entity.getBbHeight() * 0.5, 0.0);
            held.add(new HeldDraw(center.subtract(camera).toVector3f(), Math.max(entity.getBbWidth(), entity.getBbHeight()) * 0.5F,
                    Hands.palm(caster, HumanoidArm.RIGHT).subtract(camera).toVector3f(), Hands.palm(caster, HumanoidArm.LEFT).subtract(camera).toVector3f(),
                    shown, entity == minecraft.getCameraEntity(), entry.getIntKey() * 0.618F));
        }
        List<Vector3f[]> trails = new ArrayList<>();
        for (Int2ObjectMap.Entry<Throw> entry : THROWS.int2ObjectEntrySet()) {
            Entity entity = level.getEntity(entry.getIntKey());
            if (entity == null || entity == minecraft.getCameraEntity() || entry.getValue().trail.size() < 2) {
                continue;
            }
            List<Vec3> points = new ArrayList<>(entry.getValue().trail);
            points.set(0, entity.getPosition(partialTick).add(0.0, entity.getBbHeight() * 0.5, 0.0));
            Vector3f[] trail = new Vector3f[points.size()];
            for (int i = 0; i < points.size(); i++) {
                trail[i] = points.get(i).subtract(camera).toVector3f();
            }
            trails.add(trail);
        }
        GlowPass.submitTint(collector, poseStack, (pose, buffer) -> {
            Glow.Billboard axes = Glow.billboard(pose);
            for (HeldDraw draw : held) {
                if (!draw.own()) {
                    Vector3f c = draw.center();
                    Glow.tintDisc(buffer, pose, axes, c.x, c.y, c.z, draw.size() * 2.0F, GlowPass.tint(ScarletPalette.GLASS, 0.5F * draw.shown()));
                }
            }
            for (Vector3f[] trail : trails) {
                float[] widths = new float[trail.length];
                int[] tints = new int[trail.length];
                for (int i = 0; i < trail.length; i++) {
                    float s = i / (float) (trail.length - 1);
                    widths[i] = 1.4F * (1.0F - s);
                    tints[i] = GlowPass.tint(ScarletPalette.GLASS, 0.6F * (1.0F - s));
                }
                Glow.tintRibbon(buffer, pose, axes, trail, widths, tints);
            }
        });
        GlowPass.submit(collector, poseStack, (pose, buffer) -> {
            Glow.Billboard axes = Glow.billboard(pose);
            for (HeldDraw draw : held) {
                holdLight(buffer, pose, axes, draw, time);
            }
            for (Vector3f[] trail : trails) {
                float[] widths = new float[trail.length];
                float[] cores = new float[trail.length];
                int[] colors = new int[trail.length];
                int[] coreColors = new int[trail.length];
                for (int i = 0; i < trail.length; i++) {
                    float s = i / (float) (trail.length - 1);
                    widths[i] = 0.9F * (1.0F - s);
                    cores[i] = 0.25F * (1.0F - s);
                    colors[i] = Glow.withAlpha(ScarletPalette.SCARLET, 0.65F * (1.0F - s));
                    coreColors[i] = Glow.withAlpha(ScarletPalette.CORE, 0.8F * (1.0F - s));
                }
                Glow.ribbon(buffer, pose, axes, trail, widths, colors);
                Glow.ribbon(buffer, pose, axes, trail, cores, coreColors);
                Vector3f head = trail[0];
                Glow.spark(buffer, pose, axes, head.x, head.y, head.z, 0.35F, ScarletPalette.CORE, ScarletPalette.BRIGHT_SCARLET, 0.9F);
            }
        });
    }

    /**
     * The light whoever is held hangs in: tendrils wrapping them from both of the caster's palms, and rings turning
     * around them.
     */
    private static void holdLight(VertexConsumer buffer, PoseStack.Pose pose, Glow.Billboard axes, HeldDraw draw, float time) {
        Vector3f c = draw.center();
        float fade = draw.shown();
        if (!draw.own()) {
            Glow.disc(buffer, pose, axes, c.x, c.y, c.z, draw.size() * 1.8F, Glow.withAlpha(ScarletPalette.SCARLET, 0.2F * fade));
            for (int i = 0; i < 2; i++) {
                float spin = time * (0.09F + 0.05F * i) * (i == 0 ? 1.0F : -1.0F) + draw.seed();
                float tilt = 0.45F + 0.7F * i;
                Vector3f u = new Vector3f(Mth.cos(spin), 0.0F, Mth.sin(spin));
                Vector3f v = new Vector3f(-Mth.sin(spin) * Mth.cos(tilt), Mth.sin(tilt), Mth.cos(spin) * Mth.cos(tilt));
                float radius = draw.size() * 1.3F + 0.2F;
                Glow.ring(buffer, pose, c.x, c.y, c.z, u, v, radius, 0.1F, Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.6F * fade), 40);
                Glow.ring(buffer, pose, c.x, c.y, c.z, u, v, radius, 0.03F, Glow.withAlpha(ScarletPalette.CORE, 0.45F * fade), 40);
            }
        }
        for (int i = 0; i < TENDRILS; i++) {
            Vector3f palm = i % 2 == 0 ? draw.rightPalm() : draw.leftPalm();
            float seed = draw.seed() + i * 1.7F;
            Vector3f grip = new Vector3f(c).add(Mth.sin(seed * 2.9F) * draw.size() * 0.6F, Mth.cos(seed * 1.3F) * draw.size() * 0.8F,
                    Mth.cos(seed * 2.1F) * draw.size() * 0.6F);
            Vector3f path = new Vector3f(grip).sub(palm);
            float length = path.length();
            if (length < 0.1F) {
                continue;
            }
            Vector3f[] side = Glow.planeAxes(new Vector3f(path).normalize());
            Vector3f[] points = new Vector3f[POINTS];
            float[] widths = new float[POINTS];
            int[] colors = new int[POINTS];
            float curl = Math.min(0.9F, length * 0.12F);
            for (int k = 0; k < POINTS; k++) {
                float s = k / (float) (POINTS - 1);
                float envelope = Mth.sin(s * (float) Math.PI);
                float a = time * 0.4F + s * 7.0F + seed;
                points[k] = new Vector3f(palm).add(new Vector3f(path).mul(s)).add(new Vector3f(side[0]).mul(Mth.sin(a) * curl * envelope))
                        .add(new Vector3f(side[1]).mul(Mth.cos(a * 0.8F) * curl * envelope));
                widths[k] = 0.12F - 0.07F * s;
                colors[k] = Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, fade * (0.75F - 0.25F * s));
            }
            Glow.ribbon(buffer, pose, axes, points, widths, colors);
        }
    }

    private record HeldDraw(Vector3f center, float size, Vector3f rightPalm, Vector3f leftPalm, float shown, boolean own, float seed) {
    }

    private record Held(int casterId, Vec3 hang, Vec3 start, double since) {
    }

    private static final class Throw {
        final Vec3 from;
        final Vec3 to;
        final double start;
        final int ticks;
        final ArrayDeque<Vec3> trail = new ArrayDeque<>();
        boolean landed;

        Throw(Vec3 from, Vec3 to, double start, int ticks) {
            this.from = from;
            this.to = to;
            this.start = start;
            this.ticks = ticks;
        }
    }
}
