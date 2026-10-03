package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.darkhold.CorruptionClient;
import com.yashjit.scarlet.client.magic.Hands;
import com.yashjit.scarlet.client.magic.MindControlClient;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.client.render.PixelSprite;
import com.yashjit.scarlet.client.render.Pixels;
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
 * <p>Everything in the world is pixel art, stepped down the ramp of the mod's magic, so it keeps its red against a
 * bright sky.
 */
public final class MindControlFx {

    private static final Identifier GLOW = Scarlet.id("hud/glow");
    private static final int TENDRILS_PER_HAND = 2;
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
        GlowPass.submitPixels(collector, poseStack, (pose, buffer) -> {
            float before = Glow.darkness();
            for (Draw draw : draws) {
                Glow.darken(draw.darkness());
                Vector3f head = draw.head();
                PixelSprite sprite = Wisps.farther(pose, head, draw.inside() > 0.5F ? draw.brow()
                        : new Vector3f(draw.rightPalm()).add(draw.leftPalm()).mul(0.5F));
                tendrils(sprite, draw);
                thread(sprite, draw);
                if (!draw.viewedFromInside()) {
                    crown(sprite, head, draw.size(), draw.reach(), draw.seed(), draw.time());
                    if (draw.lightEyes()) {
                        Wisps.eyes(sprite, head, draw.size(), draw.out(), draw.yaw(), draw.reach(), draw.time() + draw.seed());
                    }
                }
                sprite.draw(buffer);
            }
            Glow.darken(before);
            for (Ember ember : embers) {
                Vector3f head = ember.head();
                PixelSprite sprite = PixelSprite.inWorld(pose, head.x, head.y, head.z);
                Wisps.eyes(sprite, head, ember.size(), ember.out(), ember.yaw(), 0.45F * ember.loyalty(), time);
                sprite.draw(buffer);
            }
            for (Snap snap : snaps) {
                // the thread snapping back: a ring of it bursting out of the head round a white-hot flash
                float k = (float) ((now - snap.at()) / SNAP_TICKS);
                Vector3f at = snap.head().subtract(camera).toVector3f();
                PixelSprite sprite = PixelSprite.inWorld(pose, at.x, at.y, at.z);
                int frame = (int) Math.floor(now);
                Wisps.burst(sprite, snap.size() * (0.2F + 0.9F * Ease.outCubic(k)), 0.1F + 0.2F * (1.0F - k), 1.0F - Ease.outCubic(k), frame);
                if (k < 0.4F) {
                    Wisps.orb(sprite, 3.5F * (1.0F - k / 0.4F) * snap.size(), 1.5F, frame, 31);
                }
                sprite.draw(buffer);
            }
        });
    }

    /**
     * The tendrils reaching from both palms into the head while the hold takes, giving way to the thread once the view
     * is inside. None are drawn into the view of whoever is inside it, nor more than one from each hand seen from the
     * caster's own eyes.
     */
    private static void tendrils(PixelSprite sprite, Draw draw) {
        float alpha = draw.reach() * (1.0F - draw.inside());
        if (alpha < 0.01F || draw.viewedFromInside()) {
            return;
        }
        int perHand = draw.ownFirstPerson() ? 1 : TENDRILS_PER_HAND;
        float reach = Math.clamp(draw.reach(), 0.0F, 1.0F);
        for (int hand = 0; hand < 2; hand++) {
            Vector3f palm = hand == 0 ? draw.rightPalm() : draw.leftPalm();
            for (int i = 0; i < perHand; i++) {
                int index = hand * TENDRILS_PER_HAND + i;
                float seed = draw.seed() + index * 1.913F;
                Vector3f grip = new Vector3f(draw.head()).add(Mth.sin(seed * 3.1F) * draw.size() * 0.18F,
                        Mth.sin(seed * 1.7F) * draw.size() * 0.12F + 0.05F, Mth.cos(seed * 2.3F) * draw.size() * 0.18F);
                float curl = Math.min(0.75F, grip.distance(palm) * 0.1F);
                // the bead races faster while it is still reaching in
                Wisps.tendril(sprite, palm, grip, reach, curl, seed, draw.time(), draw.ownFirstPerson() ? alpha * 0.8F : alpha,
                        reach < 0.999F ? 0.16F : 0.08F, index + 21);
            }
        }
    }

    /**
     * Once the view is inside: one trembling thread from the caster's brow to the head of what they hold, sagging a
     * little, swelling and flaring with each beat of a heart, a bead of light running along it. Seen from inside, it
     * thins out before it reaches the view, coming up to you rather than through you.
     */
    private static void thread(PixelSprite sprite, Draw draw) {
        if (draw.inside() < 0.01F) {
            return;
        }
        Vector3f from = new Vector3f(draw.brow()).add(0.0F, 0.1F, 0.0F);
        Vector3f path = new Vector3f(draw.head()).sub(from);
        float length = path.length();
        if (length < 0.1F) {
            return;
        }
        Vector3f[] side = Glow.planeAxes(new Vector3f(path).normalize());
        int count = Math.clamp(Math.round(length * 8.0F), 8, 96);
        float beat = heartbeat(draw.time());
        float run = (draw.time() * 0.05F + draw.seed()) % 1.0F;
        int frame = (int) Math.floor(draw.time());
        Vector3f previous = null;
        for (int i = 0; i <= count; i++) {
            float s = i / (float) count;
            float sag = Mth.sin(s * Mth.PI) * Math.min(0.6F, length * 0.06F);
            float shiver = Mth.sin(draw.time() * 0.9F + s * 13.0F + draw.seed()) * 0.03F * Mth.sin(s * Mth.PI);
            Vector3f at = new Vector3f(path).mul(s).add(from).add(side[0].x * shiver, sag + side[0].y * shiver, side[0].z * shiver);
            float fade = draw.viewedFromInside() ? 1.0F - Ease.clamp01((s - 0.55F) / 0.45F) : 1.0F;
            if (previous != null && Pixels.shows(draw.inside() * fade, i + frame, 7)) {
                int step = Math.abs(s - run) < 0.025F ? Pixels.HOT : beat > 0.5F ? Pixels.PINK : Pixels.BRIGHT;
                sprite.line(previous, at, Pixels.opaque(step), 1, beat > 0.5F && fade > 0.5F ? 2 : 1);
            }
            previous = at;
        }
    }

    private static Vector3f crownCenter(Vector3f head, float size) {
        return new Vector3f(head).add(0.0F, 0.12F * size, 0.0F);
    }

    /**
     * A crown of light gathering round a held head: two rings turning on tilted axes, closing round it as the hold
     * takes and flaring with each beat of a heart, a bead of light circling on each, and wisps curling up off it.
     */
    private static void crown(PixelSprite sprite, Vector3f head, float size, float reach, float seed, float time) {
        float grow = Ease.outCubic(reach);
        if (grow < 0.01F) {
            return;
        }
        boolean beat = heartbeat(time + seed) > 0.5F;
        Vector3f c = crownCenter(head, size);
        float radius = (0.32F + 0.08F * (1.0F - grow)) * size;
        int frame = (int) Math.floor(time);
        int beadSize = size > 1.5F ? 2 : 1;
        for (int i = 0; i < 2; i++) {
            float spin = time * (0.07F + 0.04F * i) * (i == 0 ? 1.0F : -1.0F) + seed;
            float tilt = 0.25F + 0.35F * i;
            Vector3f u = new Vector3f(Mth.cos(spin), 0.0F, Mth.sin(spin));
            Vector3f v = new Vector3f(-Mth.sin(spin) * Mth.cos(tilt), Mth.sin(tilt), Mth.cos(spin) * Mth.cos(tilt));
            int color = Pixels.opaque(beat ? Pixels.PINK : i == 0 ? Pixels.BRIGHT : Pixels.SCARLET);
            Wisps.ring(sprite, c, u, v, radius, spin, grow, color, 1, 1);
            float bead = time * 0.25F * (i == 0 ? 1.0F : -1.0F) + i * 2.5F;
            float bx = Mth.cos(bead) * radius;
            float by = Mth.sin(bead) * radius;
            sprite.plot(c.x + u.x * bx + v.x * by, c.y + u.y * bx + v.y * by, c.z + u.z * bx + v.z * by, Pixels.opaque(Pixels.HOT), 2, beadSize);
        }
        // wisps curling up off the head
        for (int w = 0; w < 3; w++) {
            float phase = (time * 0.03F + w / 3.0F + seed) % 1.0F;
            float angle = w * 2.094F + time * 0.02F + seed;
            float life = Mth.sin(phase * Mth.PI) * grow;
            Vector3f previous = null;
            for (int i = 0; i < 8; i++) {
                float s = i / 7.0F;
                float rise = (phase + s * 0.35F) * 0.9F * size;
                float swirl = angle + s * 2.2F;
                float r = radius * (0.9F - 0.5F * s);
                Vector3f at = new Vector3f(c.x + Mth.cos(swirl) * r, c.y + rise, c.z + Mth.sin(swirl) * r);
                if (previous != null && Pixels.shows(life * (1.0F - 0.8F * s), i + frame, w + 40)) {
                    sprite.line(previous, at, Pixels.opaque(s < 0.4F ? Pixels.SCARLET : Pixels.CRIMSON), 0, 1);
                }
                previous = at;
            }
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
