package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.darkhold.CorruptionClient;
import com.yashjit.scarlet.client.magic.Hands;
import com.yashjit.scarlet.client.magic.MindControlClient;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.config.ScarletClientConfig;
import com.yashjit.scarlet.magic.MindControl;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Mind Control as everyone sees it, and as the two minds in it feel it.
 *
 * <ul>
 *     <li>Taking hold: scarlet tendrils wind out of both of the caster's palms and grow into the creature's head, beads
 *     of light racing along them into it. A crown of light gathers around its head, two rings turning on tilted axes,
 *     and a creature's eyes kindle red.</li>
 *     <li>Held: the tendrils give way to a single trembling thread from the caster's brow to its head, pulsing like a
 *     heartbeat, and wisps curl up off its head.</li>
 *     <li>Let go: the thread snaps back in a burst of sparks. A creature set free stays loyal a while, a faint ember in
 *     its eyes fading as its loyalty runs out.</li>
 *     <li>Inside: the view dives in through a red flash, and the edges of the picture stay scarlet, curling with wisps
 *     and beating with a heart that is not yours, with the name and health of what you hold above, under a low hum.</li>
 *     <li>Held yourself: the picture is tinged red and closes in, beating fast, with how hard you are fighting it.</li>
 * </ul>
 *
 * <p>Everything in the world is drawn over a scarlet tint, so it keeps its red against a bright sky.
 */
public final class MindControlFx {

    private static final Identifier GLOW = Scarlet.id("hud/glow");
    private static final int TENDRILS_PER_HAND = 2;
    private static final int POINTS = 14;
    private static final double SNAP_TICKS = 12.0;
    private static final double DIVE_TICKS = 14.0;
    private static final double RETURN_TICKS = 10.0;

    private static final List<Snap> SNAPS = new ArrayList<>();
    private static @Nullable Hum hum;
    private static int beatIn;
    private static double struggledAt = -1.0E9;

    private MindControlFx() {
    }

    /**
     * The thread snapping back out of what was held.
     */
    public static void released(int casterId, int targetId) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !(level.getEntity(targetId) instanceof LivingEntity target)) {
            return;
        }
        Vec3 head = target.getEyePosition();
        SNAPS.add(new Snap(head, level.getGameTime(), size(target)));
        RandomSource random = ScarletFx.random();
        for (int i = 0, n = Math.round(26 * ScarletFx.density()); i < n; i++) {
            Vec3 out = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
            ScarletFx.spark(head, out.scale(0.08 + random.nextDouble() * 0.16), 8 + random.nextInt(10), 0.026F,
                    random.nextFloat() < 0.35F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET, 0.004F, 0.86F);
        }
        // your own view comes back to your eyes, where these would be
        if (level.getEntity(casterId) instanceof Player caster && caster != minecraft.player) {
            Vec3 brow = caster.getEyePosition();
            for (int i = 0, n = Math.round(10 * ScarletFx.density()); i < n; i++) {
                Vec3 out = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.5 + 0.5, random.nextGaussian()).normalize();
                ScarletFx.spark(brow, out.scale(0.05 + random.nextDouble() * 0.06), 6 + random.nextInt(6), 0.02F,
                        ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET, -0.002F, 0.85F);
            }
        }
    }

    /**
     * Each key a held player presses against it: their view jolts.
     */
    public static void struggled() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            struggledAt = minecraft.level.getGameTime();
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_HIT, 0.7F + ScarletFx.random().nextFloat() * 0.3F, 0.5F));
        }
    }

    public static void tick(Minecraft minecraft, boolean inside, boolean held) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        double now = level.getGameTime();
        SNAPS.removeIf(snap -> now - snap.at() > SNAP_TICKS);
        if (minecraft.isPaused()) {
            return;
        }
        // the hum of being inside another mind, and a heart beating that is not quite yours
        if (inside && (hum == null || hum.isStopped())) {
            hum = new Hum();
            minecraft.getSoundManager().play(hum);
        } else if (!inside && hum != null) {
            hum.end();
            hum = null;
        }
        if (inside || held) {
            if (--beatIn <= 0) {
                beatIn = held ? 15 : 24;
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, held ? 1.15F : 0.9F, held ? 0.55F : 0.35F));
            }
        } else {
            beatIn = 0;
        }
        RandomSource random = ScarletFx.random();
        for (Int2ObjectMap.Entry<MindControlClient.Link> entry : MindControlClient.links().int2ObjectEntrySet()) {
            // not off a head your own view is about to move into, where they would rise straight through it
            if (!(level.getEntity(entry.getValue().targetId) instanceof LivingEntity target) || target == minecraft.getCameraEntity()
                    || minecraft.player != null && entry.getIntKey() == minecraft.player.getId()) {
                continue;
            }
            // embers rising off a held head
            if (random.nextFloat() < 0.6F * ScarletFx.density()) {
                Vec3 head = target.getEyePosition().add(random.nextGaussian() * 0.15, 0.1, random.nextGaussian() * 0.15);
                try (Glow.Darkening ignored = Glow.darkening(CorruptionClient.darkness(level.getEntity(entry.getIntKey())))) {
                    ScarletFx.spark(head, new Vec3(random.nextGaussian() * 0.01, 0.02 + random.nextDouble() * 0.02, random.nextGaussian() * 0.01),
                            12 + random.nextInt(10), 0.02F, random.nextFloat() < 0.3F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET,
                            ScarletPalette.SCARLET, -0.0015F, 0.92F);
                }
            }
        }
    }

    // ---------------------------------------------------------------- in the world

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || MindControlClient.links().isEmpty() && MindControlClient.loyal().isEmpty() && SNAPS.isEmpty()) {
            return;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + partialTick;
        float time = (float) (now % 24000.0);
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        Entity viewer = minecraft.getCameraEntity();
        List<Draw> draws = new ArrayList<>();
        for (Int2ObjectMap.Entry<MindControlClient.Link> entry : MindControlClient.links().int2ObjectEntrySet()) {
            if (!(level.getEntity(entry.getIntKey()) instanceof Player caster) || caster.isInvisible()
                    || !(level.getEntity(entry.getValue().targetId) instanceof LivingEntity target)) {
                continue;
            }
            MindControlClient.Link link = entry.getValue();
            float reach = Ease.outCubic((float) ((now - link.since) / MindControl.SEIZE_TICKS));
            float inside = link.insideAt < 0.0 ? 0.0F : Ease.clamp01((float) ((now - link.insideAt) / 10.0));
            Vec3 head = target.getEyePosition(partialTick);
            // a player's eyes are wherever their skin puts them, so only a creature's can be lit
            draws.add(new Draw(head.subtract(camera).toVector3f(), size(target), faceOut(target), !(target instanceof Player), target.getViewYRot(partialTick),
                    Hands.palm(caster, HumanoidArm.RIGHT).subtract(camera).toVector3f(), Hands.palm(caster, HumanoidArm.LEFT).subtract(camera).toVector3f(),
                    caster.getEyePosition(partialTick).subtract(camera).toVector3f(), reach, inside,
                    target == viewer, ScarletFx.isFirstPersonViewOf(caster), entry.getIntKey() * 0.618F, time, CorruptionClient.darkness(caster)));
        }
        List<Ember> embers = new ArrayList<>();
        for (Int2DoubleMap.Entry entry : MindControlClient.loyal().int2DoubleEntrySet()) {
            if (level.getEntity(entry.getIntKey()) instanceof LivingEntity creature && creature != viewer) {
                float loyalty = MindControlClient.loyalty(creature, now);
                if (loyalty > 0.01F) {
                    embers.add(new Ember(creature.getEyePosition(partialTick).subtract(camera).toVector3f(), size(creature), faceOut(creature),
                            creature.getViewYRot(partialTick), loyalty));
                }
            }
        }
        List<Snap> snaps = List.copyOf(SNAPS);
        if (draws.isEmpty() && embers.isEmpty() && snaps.isEmpty()) {
            return;
        }
        GlowPass.submitTint(collector, poseStack, (pose, buffer) -> {
            Glow.Billboard axes = Glow.billboard(pose);
            float before = Glow.darkness();
            for (Draw draw : draws) {
                Glow.darken(draw.darkness());
                for (Path tendril : tendrils(draw)) {
                    float[] widths = new float[tendril.count()];
                    int[] tints = new int[tendril.count()];
                    for (int i = 0; i < tendril.count(); i++) {
                        widths[i] = tendril.widths()[i] * 2.6F;
                        tints[i] = GlowPass.tint(ScarletPalette.GLASS, tendril.alpha() * (0.5F - 0.2F * tendril.along()[i]));
                    }
                    Glow.tintRibbon(buffer, pose, axes, tendril.points(), widths, tints);
                }
                Path thread = thread(draw);
                if (thread != null) {
                    float[] widths = new float[thread.count()];
                    int[] tints = new int[thread.count()];
                    for (int i = 0; i < thread.count(); i++) {
                        widths[i] = thread.widths()[i] * 2.8F;
                        tints[i] = GlowPass.tint(ScarletPalette.GLASS, thread.alpha() * thread.fades()[i] * 0.55F);
                    }
                    Glow.tintRibbon(buffer, pose, axes, thread.points(), widths, tints);
                }
                if (!draw.viewedFromInside()) {
                    Vector3f c = crownCenter(draw.head(), draw.size());
                    Glow.tintDisc(buffer, pose, axes, c.x, c.y, c.z, 0.6F * draw.size(), GlowPass.tint(ScarletPalette.GLASS, 0.42F * draw.reach()));
                }
            }
            Glow.darken(before);
            for (Snap snap : snaps) {
                float k = (float) ((now - snap.at()) / SNAP_TICKS);
                Vector3f at = snap.head().subtract(camera).toVector3f();
                Glow.tintDisc(buffer, pose, axes, at.x, at.y, at.z, snap.size() * (0.5F + 1.4F * Ease.outCubic(k)),
                        GlowPass.tint(ScarletPalette.GLASS, 0.5F * (1.0F - k)));
            }
        });
        GlowPass.submit(collector, poseStack, (pose, buffer) -> {
            Glow.Billboard axes = Glow.billboard(pose);
            float before = Glow.darkness();
            for (Draw draw : draws) {
                Glow.darken(draw.darkness());
                for (Path tendril : tendrils(draw)) {
                    drawTendril(buffer, pose, axes, tendril, draw.time());
                }
                Path thread = thread(draw);
                if (thread != null) {
                    drawThread(buffer, pose, axes, thread, draw);
                }
                if (!draw.viewedFromInside()) {
                    crown(buffer, pose, axes, draw.head(), draw.size(), draw.reach(), draw.seed(), draw.time());
                    if (draw.lightEyes()) {
                        eyes(buffer, pose, axes, draw.head(), draw.size(), draw.out(), draw.yaw(), draw.reach(), draw.time() + draw.seed());
                    }
                }
            }
            Glow.darken(before);
            for (Ember ember : embers) {
                eyes(buffer, pose, axes, ember.head(), ember.size(), ember.out(), ember.yaw(), 0.45F * ember.loyalty(), time);
            }
            for (Snap snap : snaps) {
                float k = (float) ((now - snap.at()) / SNAP_TICKS);
                Vector3f at = snap.head().subtract(camera).toVector3f();
                float fade = 1.0F - Ease.outCubic(k);
                Glow.disc(buffer, pose, axes, at.x, at.y, at.z, snap.size() * (0.4F + 1.4F * Ease.outCubic(k)),
                        Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.5F * fade));
                Glow.disc(buffer, pose, axes, at.x, at.y, at.z, snap.size() * 0.35F, Glow.withAlpha(ScarletPalette.CORE, 0.7F * fade * fade));
            }
        });
    }

    /**
     * The tendrils reaching from both palms into the head while the hold takes, giving way to the thread once the view
     * is inside. None are drawn into the view of whoever is inside it, nor more than one from each hand seen from the
     * caster's own eyes.
     */
    private static List<Path> tendrils(Draw draw) {
        float alpha = draw.reach() * (1.0F - draw.inside());
        if (alpha < 0.01F || draw.viewedFromInside()) {
            return List.of();
        }
        int perHand = draw.ownFirstPerson() ? 1 : TENDRILS_PER_HAND;
        List<Path> paths = new ArrayList<>(perHand * 2);
        for (int hand = 0; hand < 2; hand++) {
            Vector3f palm = hand == 0 ? draw.rightPalm() : draw.leftPalm();
            for (int i = 0; i < perHand; i++) {
                Path path = tendril(palm, draw, hand * TENDRILS_PER_HAND + i, draw.ownFirstPerson() ? alpha * 0.8F : alpha);
                if (path != null) {
                    paths.add(path);
                }
            }
        }
        return paths;
    }

    /**
     * One tendril from a palm into the head, grown as far as the hold has reached along its way: thick leaving the hand,
     * curling as it goes, thinning as it sinks in.
     */
    private static @Nullable Path tendril(Vector3f palm, Draw draw, int index, float alpha) {
        float seed = draw.seed() + index * 1.913F;
        Vector3f grip = new Vector3f(draw.head()).add(Mth.sin(seed * 3.1F) * draw.size() * 0.18F,
                Mth.sin(seed * 1.7F) * draw.size() * 0.12F + 0.05F, Mth.cos(seed * 2.3F) * draw.size() * 0.18F);
        Vector3f path = new Vector3f(grip).sub(palm);
        float length = path.length();
        if (length < 0.05F) {
            return null;
        }
        Vector3f[] side = Glow.planeAxes(new Vector3f(path).normalize());
        float reach = Math.clamp(draw.reach(), 0.0F, 1.0F);
        int count = Math.max(2, Math.round(POINTS * reach));
        Vector3f[] points = new Vector3f[count];
        float[] widths = new float[count];
        float[] along = new float[count];
        float curl = Math.min(0.75F, length * 0.1F);
        for (int i = 0; i < count; i++) {
            float s = reach * i / (count - 1);
            float envelope = Mth.sin(Math.min(1.0F, s) * (float) Math.PI);
            float a = draw.time() * 0.42F + s * 8.0F + seed;
            float u = (Mth.sin(a) * curl + Mth.sin(seed * 5.0F) * curl * 0.9F) * envelope;
            float v = (Mth.cos(a * 0.8F + seed) * curl + Mth.cos(seed * 4.0F) * curl * 0.9F) * envelope;
            points[i] = new Vector3f(palm).add(new Vector3f(path).mul(s)).add(new Vector3f(side[0]).mul(u)).add(new Vector3f(side[1]).mul(v));
            widths[i] = 0.1F - 0.06F * s;
            along[i] = s;
        }
        return new Path(points, widths, along, null, alpha, index);
    }

    private static void drawTendril(VertexConsumer buffer, PoseStack.Pose pose, Glow.Billboard axes, Path tendril, float time) {
        int count = tendril.count();
        float[] cores = new float[count];
        int[] colors = new int[count];
        int[] coreColors = new int[count];
        for (int i = 0; i < count; i++) {
            float s = tendril.along()[i];
            cores[i] = tendril.widths()[i] * 0.3F;
            colors[i] = Glow.withAlpha(ScarletPalette.SCARLET, tendril.alpha() * (0.5F - 0.15F * s));
            coreColors[i] = Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, tendril.alpha() * (0.85F - 0.25F * s));
        }
        Glow.ribbon(buffer, pose, axes, tendril.points(), tendril.widths(), colors);
        Glow.ribbon(buffer, pose, axes, tendril.points(), cores, coreColors);
        Vector3f tip = tendril.points()[count - 1];
        Glow.spark(buffer, pose, axes, tip.x, tip.y, tip.z, 0.04F, ScarletPalette.CORE, ScarletPalette.BRIGHT_SCARLET, tendril.alpha() * 0.7F);
        // a bead of light racing along it into the head, faster while it is still reaching in
        boolean reaching = tendril.along()[count - 1] < 0.999F;
        float bead = (time * (reaching ? 0.16F : 0.08F) + tendril.index() * 0.41F) % 1.0F;
        Vector3f p = tendril.points()[Math.min(count - 1, Math.round(bead * (count - 1)))];
        Glow.spark(buffer, pose, axes, p.x, p.y, p.z, 0.035F, ScarletPalette.CORE, ScarletPalette.BRIGHT_SCARLET,
                tendril.alpha() * Mth.sin(bead * (float) Math.PI));
    }

    /**
     * Once the view is inside: one trembling thread from the caster's brow to the head of what they hold, sagging a
     * little, pulsing like a heartbeat. Seen from inside, it fades out before it reaches the view, coming up to you
     * rather than through you.
     */
    private static @Nullable Path thread(Draw draw) {
        if (draw.inside() < 0.01F) {
            return null;
        }
        Vector3f from = new Vector3f(draw.brow()).add(0.0F, 0.1F, 0.0F);
        Vector3f path = new Vector3f(draw.head()).sub(from);
        float length = path.length();
        if (length < 0.1F) {
            return null;
        }
        Vector3f[] side = Glow.planeAxes(new Vector3f(path).normalize());
        int count = Math.clamp(Math.round(length * 2.0F), 8, 40);
        Vector3f[] points = new Vector3f[count];
        float[] widths = new float[count];
        float[] along = new float[count];
        float[] fades = new float[count];
        float beat = heartbeat(draw.time());
        for (int i = 0; i < count; i++) {
            float s = i / (float) (count - 1);
            float sag = Mth.sin(s * (float) Math.PI) * Math.min(0.6F, length * 0.06F);
            float shiver = Mth.sin(draw.time() * 0.9F + s * 13.0F + draw.seed()) * 0.03F * Mth.sin(s * (float) Math.PI);
            points[i] = new Vector3f(from).add(new Vector3f(path).mul(s)).add(0.0F, sag, 0.0F).add(new Vector3f(side[0]).mul(shiver));
            fades[i] = draw.viewedFromInside() ? 1.0F - Ease.clamp01((s - 0.55F) / 0.45F) : 1.0F;
            widths[i] = (0.06F + 0.03F * beat) * fades[i];
            along[i] = s;
        }
        return new Path(points, widths, along, fades, draw.inside(), 0);
    }

    private static void drawThread(VertexConsumer buffer, PoseStack.Pose pose, Glow.Billboard axes, Path thread, Draw draw) {
        int count = thread.count();
        float beat = heartbeat(draw.time());
        int[] colors = new int[count];
        float[] cores = new float[count];
        int[] coreColors = new int[count];
        for (int i = 0; i < count; i++) {
            float fade = thread.fades()[i];
            colors[i] = Glow.withAlpha(ScarletPalette.SCARLET, thread.alpha() * (0.5F + 0.3F * beat) * fade);
            cores[i] = thread.widths()[i] * 0.35F;
            coreColors[i] = Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, thread.alpha() * (0.75F + 0.25F * beat) * fade);
        }
        Glow.ribbon(buffer, pose, axes, thread.points(), thread.widths(), colors);
        Glow.ribbon(buffer, pose, axes, thread.points(), cores, coreColors);
        float run = (draw.time() * 0.05F + draw.seed()) % 1.0F;
        int at = Math.min(count - 1, Math.round(run * (count - 1)));
        Vector3f p = thread.points()[at];
        Glow.spark(buffer, pose, axes, p.x, p.y, p.z, 0.05F, ScarletPalette.CORE, ScarletPalette.BRIGHT_SCARLET, thread.alpha() * thread.fades()[at]);
    }

    private static Vector3f crownCenter(Vector3f head, float size) {
        return new Vector3f(head).add(0.0F, 0.12F * size, 0.0F);
    }

    /**
     * A crown of light gathering around a held head: a soft red halo, two rings turning on tilted axes, beads circling
     * on them, and wisps curling up off it.
     */
    private static void crown(VertexConsumer buffer, PoseStack.Pose pose, Glow.Billboard axes, Vector3f head, float size, float reach, float seed,
                              float time) {
        float grow = Ease.outCubic(reach);
        if (grow < 0.01F) {
            return;
        }
        float pulse = 0.85F + 0.15F * heartbeat(time + seed);
        Vector3f c = crownCenter(head, size);
        Glow.disc(buffer, pose, axes, c.x, c.y, c.z, 0.55F * size * grow, Glow.withAlpha(ScarletPalette.SCARLET, 0.22F * grow * pulse));
        float radius = (0.32F + 0.08F * (1.0F - grow)) * size;
        for (int i = 0; i < 2; i++) {
            float spin = time * (0.07F + 0.04F * i) * (i == 0 ? 1.0F : -1.0F) + seed;
            float tilt = 0.25F + 0.35F * i;
            Vector3f u = new Vector3f(Mth.cos(spin), 0.0F, Mth.sin(spin));
            Vector3f v = new Vector3f(-Mth.sin(spin) * Mth.cos(tilt), Mth.sin(tilt), Mth.cos(spin) * Mth.cos(tilt));
            Glow.ring(buffer, pose, c.x, c.y, c.z, u, v, radius, 0.08F * size, Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.6F * grow * pulse), 36);
            Glow.ring(buffer, pose, c.x, c.y, c.z, u, v, radius, 0.022F * size, Glow.withAlpha(ScarletPalette.CORE, 0.45F * grow), 36);
            float bead = time * 0.25F * (i == 0 ? 1.0F : -1.0F) + i * 2.5F;
            float bx = Mth.cos(bead) * radius;
            float by = Mth.sin(bead) * radius;
            Glow.spark(buffer, pose, axes, c.x + u.x * bx + v.x * by, c.y + u.y * bx + v.y * by, c.z + u.z * bx + v.z * by, 0.035F * size,
                    ScarletPalette.CORE, ScarletPalette.BRIGHT_SCARLET, grow);
        }
        // wisps curling up off the head
        for (int w = 0; w < 3; w++) {
            float phase = (time * 0.03F + w / 3.0F + seed) % 1.0F;
            Vector3f[] points = new Vector3f[7];
            float[] widths = new float[7];
            int[] colors = new int[7];
            float angle = w * 2.094F + time * 0.02F + seed;
            float life = Mth.sin(phase * (float) Math.PI);
            for (int i = 0; i < 7; i++) {
                float s = i / 6.0F;
                float rise = (phase + s * 0.35F) * 0.9F * size;
                float swirl = angle + s * 2.2F;
                float r = radius * (0.9F - 0.5F * s);
                points[i] = new Vector3f(c.x + Mth.cos(swirl) * r, c.y + rise, c.z + Mth.sin(swirl) * r);
                widths[i] = 0.06F * size * (1.0F - s * 0.7F);
                colors[i] = Glow.withAlpha(ScarletPalette.SCARLET, 0.38F * grow * life * (1.0F - s));
            }
            Glow.ribbon(buffer, pose, axes, points, widths, colors);
        }
    }

    /**
     * Two red points of light where a creature's eyes are, a little out from its face.
     *
     * @param out how far in front of where it looks from its face is; see {@link #faceOut}
     */
    static void eyes(VertexConsumer buffer, PoseStack.Pose pose, Glow.Billboard axes, Vector3f head, float size, float out, float yaw, float strength,
                     float time) {
        if (strength < 0.01F) {
            return;
        }
        float rad = yaw * Mth.DEG_TO_RAD;
        float fx = -Mth.sin(rad);
        float fz = Mth.cos(rad);
        float apart = 0.11F * size;
        float flicker = 0.85F + 0.15F * Mth.sin(time * 1.3F);
        for (int side = -1; side <= 1; side += 2) {
            float x = head.x + fx * out + fz * apart * side;
            float y = head.y + 0.06F * size;
            float z = head.z + fz * out - fx * apart * side;
            Glow.disc(buffer, pose, axes, x, y, z, 0.09F * size, Glow.withAlpha(ScarletPalette.SCARLET, 0.4F * strength * flicker));
            Glow.disc(buffer, pose, axes, x, y, z, 0.032F * size, Glow.withAlpha(ScarletPalette.CORE, 0.95F * strength));
        }
    }

    /**
     * A heart beating: two quick pulses and a rest, from 0 to 1.
     */
    private static float heartbeat(float time) {
        float t = (time / 22.0F) % 1.0F;
        float first = Math.max(0.0F, 1.0F - Math.abs(t - 0.08F) / 0.07F);
        float second = Math.max(0.0F, 1.0F - Math.abs(t - 0.24F) / 0.07F) * 0.7F;
        return Math.max(first, second);
    }

    static float size(LivingEntity entity) {
        return Math.clamp(entity.getBbWidth() / 0.6F, 0.6F, 2.5F);
    }

    /**
     * How far in front of where a creature looks from its face is: just before a head carried on shoulders, further out
     * for the long heads of beasts on all fours, which reach out past their bodies, and on the front of anything that is
     * all face, like a slime or a ghast.
     */
    static float faceOut(LivingEntity entity) {
        float width = entity.getBbWidth();
        if (entity.getBbHeight() >= width * 2.2F) {
            return Math.min(0.3F, 0.25F * size(entity)) + 0.03F;
        }
        return Math.max(Math.min(1.0F, width + 0.03F), width * 0.5F + 0.05F);
    }

    // ---------------------------------------------------------------- on the screen

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        double now = minecraft.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);
        float strength = ScarletClientConfig.get().reduceFlashing ? 0.55F : 1.0F;
        double moved = MindControlClient.sinceMoved(now);
        if (MindControlClient.inside()) {
            boolean dream = MindControlClient.dreaming();
            insideView(graphics, minecraft, now, strength, dream);
            if (!dream && MindControlClient.movedIn() && moved < DIVE_TICKS) {
                // diving in: a flash of red the new view opens out of, from the middle
                float k = (float) (moved / DIVE_TICKS);
                flash(graphics, (1.0F - Ease.outCubic(k)) * 0.75F * strength, k);
            }
        } else if (!MindControlClient.movedIn() && !MindControlClient.wasDreaming() && moved < RETURN_TICKS) {
            float k = (float) (moved / RETURN_TICKS);
            flash(graphics, (1.0F - Ease.outCubic(k)) * 0.55F * strength, k);
        }
        if (MindControlClient.held()) {
            heldView(graphics, minecraft, now, strength);
        }
    }

    /**
     * @param dream your spirit is in it, dreamwalking: the edges run darker, black-crimson with sickly light
     */
    private static void insideView(GuiGraphicsExtractor graphics, Minecraft minecraft, double now, float strength, boolean dream) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float beat = heartbeat((float) now);
        float edge = (0.42F + 0.18F * beat) * strength;
        int lit = dream ? ScarletPalette.SICKLY : ScarletPalette.SCARLET;
        vignette(graphics, width, height, Math.max(width, height) / 4.0F, dream ? edge * 1.4F : edge, dream ? ScarletPalette.VOID : ScarletPalette.WINE);
        vignette(graphics, width, height, Math.max(width, height) / 9.0F, edge * (dream ? 0.3F : 0.6F), lit);
        // wisps curling at the edges of the view
        for (int i = 0; i < 12; i++) {
            float along = (float) ((now * 0.004 + i * 0.0833) % 1.0);
            float wave = Mth.sin((float) now * 0.05F + i * 1.7F);
            float x;
            float y;
            if (i % 4 == 0) {
                x = along * width;
                y = 6.0F + wave * 5.0F;
            } else if (i % 4 == 1) {
                x = (1.0F - along) * width;
                y = height - 6.0F + wave * 5.0F;
            } else if (i % 4 == 2) {
                x = 6.0F + wave * 5.0F;
                y = along * height;
            } else {
                x = width - 6.0F + wave * 5.0F;
                y = (1.0F - along) * height;
            }
            sprite(graphics, x, y, 70.0F + 30.0F * Mth.sin(i * 2.3F + (float) now * 0.03F), ARGB.color((dream ? 0.14F : 0.22F) * strength, lit));
        }
        Entity held = MindControlClient.insideOf();
        if (held instanceof LivingEntity living) {
            float shown = Ease.clamp01((float) (MindControlClient.sinceMoved(now) - 6.0) / 10.0F);
            Component name = living.getDisplayName();
            int y = 14;
            graphics.centeredText(minecraft.font, name, width / 2, y, ARGB.color(0.95F * shown, dream ? 0xD9425E : ScarletPalette.BRIGHT_SCARLET));
            float health = Math.clamp(living.getHealth() / Math.max(1.0F, living.getMaxHealth()), 0.0F, 1.0F);
            float barWidth = 100.0F;
            float x0 = (width - barWidth) / 2.0F;
            rect(graphics, x0 - 1, y + 11, barWidth + 2, 4, ARGB.color(0.7F * shown, ScarletPalette.SHADOW));
            rect(graphics, x0, y + 12, barWidth, 2, ARGB.color(0.8F * shown, ScarletPalette.WINE));
            rect(graphics, x0, y + 12, barWidth * health, 1, ARGB.color(shown, ScarletPalette.BRIGHT_SCARLET));
            rect(graphics, x0, y + 13, barWidth * health, 1, ARGB.color(shown, ScarletPalette.CRIMSON));
            double since = MindControlClient.sinceMoved(now);
            float hint = Math.min(Ease.clamp01((float) (since - 12.0) / 10.0F), Ease.clamp01((float) (110.0 - since) / 20.0F));
            if (hint > 0.02F) {
                Component text = Component.translatable(dream ? "hud.scarlet.dreamwalk.wake" : "hud.scarlet.mind_control.return",
                        minecraft.options.keyUse.getTranslatedKeyMessage());
                graphics.pose().pushMatrix();
                graphics.pose().translate(width / 2.0F, height - 52.0F);
                graphics.pose().scale(0.75F);
                graphics.centeredText(minecraft.font, text, 0, 0, ARGB.color(0.85F * hint, 0xE6D6DA));
                graphics.pose().popMatrix();
            }
        }
    }

    private static void heldView(GuiGraphicsExtractor graphics, Minecraft minecraft, double now, float strength) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float beat = heartbeat((float) now * 1.6F);
        float shown = Ease.clamp01((float) (now - MindControlClient.heldSince()) / 8.0F);
        float jolt = (float) Math.max(0.0, 1.0 - (now - struggledAt) / 5.0);
        graphics.fill(0, 0, width, height, ARGB.color((0.16F + 0.08F * beat + 0.12F * jolt) * shown * strength, ScarletPalette.CRIMSON));
        vignette(graphics, width, height, Math.max(width, height) / 3.0F, (0.6F + 0.2F * beat) * shown * strength, ScarletPalette.SHADOW);
        vignette(graphics, width, height, Math.max(width, height) / 7.0F, 0.5F * shown * strength, ScarletPalette.SCARLET);
        Component text = Component.translatable("hud.scarlet.mind_control.held");
        int y = height / 2 + 28;
        float shake = jolt * 2.0F;
        graphics.centeredText(minecraft.font, text, Math.round(width / 2.0F + Mth.sin((float) now * 3.3F) * shake), y,
                ARGB.color(shown, ScarletPalette.CORE));
        Component hint = Component.translatable("hud.scarlet.mind_control.struggle");
        graphics.pose().pushMatrix();
        graphics.pose().translate(width / 2.0F, y + 12.0F);
        graphics.pose().scale(0.75F);
        graphics.centeredText(minecraft.font, hint, 0, 0, ARGB.color(0.8F * shown, 0xE6D6DA));
        graphics.pose().popMatrix();
        int fought = Math.min(MindControl.STRUGGLE_PRESSES, MindControlClient.struggles());
        float x0 = width / 2.0F - (MindControl.STRUGGLE_PRESSES - 1) * 3.5F;
        for (int i = 0; i < MindControl.STRUGGLE_PRESSES; i++) {
            boolean lit = i < fought;
            float size = lit ? 4.0F : 3.0F;
            rect(graphics, x0 + i * 7.0F - size / 2, y + 26.0F - size / 2, size, size,
                    ARGB.color(shown * (lit ? 1.0F : 0.4F), lit ? ScarletPalette.BRIGHT_SCARLET : ScarletPalette.WINE));
        }
    }

    /**
     * A red flash opening out from the middle of the view as {@code k} runs from 0 to 1.
     */
    private static void flash(GuiGraphicsExtractor graphics, float alpha, float k) {
        if (alpha < 0.01F) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        graphics.fill(0, 0, width, height, ARGB.color(alpha * 0.55F, ScarletPalette.CRIMSON));
        vignette(graphics, width, height, Math.max(width, height) * (0.5F - 0.35F * k), alpha, ScarletPalette.SHADOW);
        sprite(graphics, width / 2.0F, height / 2.0F, Math.max(width, height) * (0.3F + 1.6F * k), ARGB.color(alpha * 0.6F, ScarletPalette.BRIGHT_SCARLET));
    }

    /**
     * Darkens the view from all four edges in, over {@code depth} GUI pixels.
     */
    static void vignette(GuiGraphicsExtractor graphics, int width, int height, float depth, float alpha, int rgb) {
        if (alpha < 0.01F) {
            return;
        }
        int band = Math.max(1, Math.round(depth));
        int edge = ARGB.color(alpha, rgb);
        int clear = ARGB.color(0.0F, rgb);
        graphics.fillGradient(0, 0, width, band, edge, clear);
        graphics.fillGradient(0, height - band, width, height, clear, edge);
        // the sides are the same gradient turned on its side
        graphics.pose().pushMatrix();
        graphics.pose().rotate((float) (-Math.PI / 2.0));
        graphics.fillGradient(-height, 0, 0, band, edge, clear);
        graphics.pose().popMatrix();
        graphics.pose().pushMatrix();
        graphics.pose().translate(width, 0.0F);
        graphics.pose().rotate((float) (Math.PI / 2.0));
        graphics.fillGradient(0, 0, height, band, edge, clear);
        graphics.pose().popMatrix();
    }

    private static void rect(GuiGraphicsExtractor graphics, float x, float y, float width, float height, int color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(width, height);
        graphics.fill(0, 0, 1, 1, color);
        graphics.pose().popMatrix();
    }

    static void sprite(GuiGraphicsExtractor graphics, float x, float y, float size, int color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(size / 16.0F);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, GLOW, -8, -8, 16, 16, color);
        graphics.pose().popMatrix();
    }

    /**
     * Where one held mind and its caster stand in this frame, relative to the camera.
     *
     * @param viewedFromInside the view is looking out through the held head itself
     * @param ownFirstPerson   the view is the caster's own eyes
     */
    private record Draw(Vector3f head, float size, float out, boolean lightEyes, float yaw, Vector3f rightPalm, Vector3f leftPalm, Vector3f brow, float reach,
                        float inside, boolean viewedFromInside, boolean ownFirstPerson, float seed, float time, float darkness) {
    }

    /**
     * A tendril or the thread as laid out this frame: its points, how wide it is and how far along it each point is,
     * how much of it shows at each point when it fades toward the view, and how strongly it shows overall.
     */
    private record Path(Vector3f[] points, float[] widths, float[] along, float @Nullable [] fades, float alpha, int index) {

        int count() {
            return points.length;
        }
    }

    private record Ember(Vector3f head, float size, float out, float yaw, float loyalty) {
    }

    private record Snap(Vec3 head, double at, float size) {
    }

    /**
     * The low hum of being inside another mind.
     */
    private static final class Hum extends AbstractTickableSoundInstance {

        private boolean ending;

        Hum() {
            super(SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001F;
            this.pitch = 0.55F;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        void end() {
            ending = true;
        }

        @Override
        public void tick() {
            volume = ending ? volume * 0.6F : Math.min(0.35F, volume + 0.05F);
            if (ending && volume < 0.01F) {
                stop();
            }
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
