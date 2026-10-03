package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.anim.Meditation;
import com.yashjit.scarlet.client.darkhold.CorruptionClient;
import com.yashjit.scarlet.client.darkhold.DreamwalkClient;
import com.yashjit.scarlet.client.magic.Hands;
import com.yashjit.scarlet.client.magic.MindControlClient;
import com.yashjit.scarlet.client.render.FlatPixels;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.client.render.PixelSprite;
import com.yashjit.scarlet.client.render.Pixels;
import com.yashjit.scarlet.config.ScarletClientConfig;
import com.yashjit.scarlet.entity.DreamBody;
import com.yashjit.scarlet.network.MagicEventPayload;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
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
 * Dreamwalking as everyone sees it, and as the dreamwalker feels it.
 *
 * <ul>
 *     <li>Sitting down to it: wisps gather and begin to turn about the body as it settles cross-legged and rises.</li>
 *     <li>The body left behind: hovering cross-legged in a slow swirl of scarlet wisps rising and turning about it, a
 *     faint ring of light on the ground beneath, and sparks drifting up off it and off its hands.</li>
 *     <li>The spirit leaving: light streaking up out of the head, and a ring bursting out from the chest.</li>
 *     <li>The spirit coming back: light drawn in from all around into the head, and a flash.</li>
 *     <li>The creature it is in: its eyes burn red and embers rise off its head; when the spirit leaves, a burst.</li>
 *     <li>For the dreamwalker: the dark closes in as the body rises, until the view goes black and opens out of it into
 *     the creature's; waking, a flash, and the view is their own again.</li>
 * </ul>
 *
 * <p>All of it darkens with the dreamwalker's corruption, as all their magic does.
 */
public final class DreamFx {

    private static final int WISPS = 4;
    private static final int WISP_POINTS = 14;
    private static final double BURST_TICKS = 16.0;
    private static final double OPEN_TICKS = 18.0;
    private static final double WAKE_TICKS = 14.0;
    private static final float CHEST = 0.95F;
    private static final Vector3f FLAT_U = new Vector3f(1.0F, 0.0F, 0.0F);
    private static final Vector3f FLAT_V = new Vector3f(0.0F, 0.0F, 1.0F);

    private static final List<Burst> BURSTS = new ArrayList<>();

    private DreamFx() {
    }

    // ---------------------------------------------------------------- events

    /**
     * The spirit leaving a body or coming back to it, at its feet.
     */
    public static void onEvent(MagicEventPayload payload) {
        boolean departing = payload.kind() == MagicEventPayload.DREAM_DEPART;
        if (!departing && payload.kind() != MagicEventPayload.DREAM_WAKE) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 feet = payload.position();
        Entity entity = minecraft.level.getEntity(payload.entityId());
        float darkness = entity == null ? 0.0F : darkness(entity);
        BURSTS.add(new Burst(feet, minecraft.level.getGameTime(), departing, darkness));
        Vec3 head = feet.add(0.0, Meditation.HOVER + 1.62 - Meditation.SIT_DROP, 0.0);
        RandomSource random = ScarletFx.random();
        try (Glow.Darkening ignored = Glow.darkening(darkness)) {
            int count = Math.round((departing ? 40 : 30) * ScarletFx.density());
            for (int i = 0; i < count; i++) {
                if (departing) {
                    // the spirit streaking up out of the head
                    Vec3 up = new Vec3(random.nextGaussian() * 0.04, 0.22 + random.nextDouble() * 0.28, random.nextGaussian() * 0.04);
                    ScarletFx.spark(head, up, 10 + random.nextInt(14), 0.024F + random.nextFloat() * 0.012F,
                            random.nextFloat() < 0.4F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET, -0.004F, 0.9F);
                } else {
                    // light drawn in from all around into the head
                    Vec3 out = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.6, random.nextGaussian()).normalize();
                    int life = 9 + random.nextInt(6);
                    Vec3 from = head.add(out.scale(1.4 + random.nextDouble() * 0.6));
                    ScarletFx.spark(from, head.subtract(from).scale(1.0 / life), life, 0.02F,
                            random.nextFloat() < 0.4F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET, 0.0F, 1.0F);
                }
            }
        }
    }

    /**
     * The spirit gone out of a creature: a puff of it off its head.
     */
    public static void released(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.level.getEntity(entityId) instanceof LivingEntity creature)) {
            return;
        }
        Vec3 head = creature.getEyePosition();
        RandomSource random = ScarletFx.random();
        for (int i = 0, n = Math.round(22 * ScarletFx.density()); i < n; i++) {
            Vec3 out = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.5 + 0.6, random.nextGaussian()).normalize();
            ScarletFx.spark(head, out.scale(0.06 + random.nextDouble() * 0.12), 10 + random.nextInt(10), 0.022F,
                    random.nextFloat() < 0.3F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET, -0.002F, 0.88F);
        }
    }

    // ---------------------------------------------------------------- every tick

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            BURSTS.clear();
            return;
        }
        double now = level.getGameTime();
        BURSTS.removeIf(burst -> now - burst.at() > BURST_TICKS);
        if (minecraft.isPaused()) {
            return;
        }
        RandomSource random = ScarletFx.random();
        float density = ScarletFx.density();
        for (Entity entity : level.entitiesForRendering()) {
            float sit = entity instanceof DreamBody || entity instanceof Player ? Meditation.sit(entity, now) : 0.0F;
            if (sit < 0.05F || entity.isInvisible() || entity == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson()) {
                continue;
            }
            float darkness = darkness(entity);
            try (Glow.Darkening ignored = Glow.darkening(darkness)) {
                // sparks lifting off the swirl, and off the hands
                if (random.nextFloat() < 0.7F * sit * density) {
                    double angle = random.nextDouble() * Math.PI * 2.0;
                    double radius = 0.45 + random.nextDouble() * 0.2;
                    Vec3 at = entity.position().add(Math.cos(angle) * radius, 0.1 + random.nextDouble() * 1.4, Math.sin(angle) * radius);
                    ScarletFx.spark(at, new Vec3(-Math.sin(angle) * 0.01, 0.012 + random.nextDouble() * 0.02, Math.cos(angle) * 0.01),
                            16 + random.nextInt(14), 0.016F, random.nextFloat() < 0.3F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET,
                            ScarletPalette.SCARLET, -0.0008F, 0.95F);
                }
                for (HumanoidArm arm : HumanoidArm.values()) {
                    Vec3 palm = entity instanceof Player player ? Hands.palm(player, arm) : Hands.drawnPalm(entity, arm);
                    if (palm != null && random.nextFloat() < 0.3F * sit * density) {
                        ScarletFx.spark(palm.add(random.nextGaussian() * 0.04, 0.0, random.nextGaussian() * 0.04),
                                new Vec3(random.nextGaussian() * 0.004, 0.015 + random.nextDouble() * 0.015, random.nextGaussian() * 0.004),
                                12 + random.nextInt(10), 0.014F, ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET, -0.0006F, 0.95F);
                    }
                }
                if (darkness > 0.2F && random.nextFloat() < 0.25F * darkness * density) {
                    Vec3 at = entity.position().add(random.nextGaussian() * 0.3, 0.4 + random.nextDouble(), random.nextGaussian() * 0.3);
                    ScarletFx.smoke(at, new Vec3(0.0, 0.01 + random.nextDouble() * 0.01, 0.0), 26 + random.nextInt(14), 0.07F, 0.4F + 0.3F * darkness);
                }
            }
        }
        for (Int2FloatMap.Entry entry : DreamwalkClient.possessed().int2FloatEntrySet()) {
            // embers rising off a head with a spirit in it, unless your own view looks out of it
            if (!(level.getEntity(entry.getIntKey()) instanceof LivingEntity creature) || creature == minecraft.getCameraEntity()) {
                continue;
            }
            if (random.nextFloat() < 0.5F * density) {
                Vec3 head = creature.getEyePosition().add(random.nextGaussian() * 0.15, 0.1, random.nextGaussian() * 0.15);
                try (Glow.Darkening ignored = Glow.darkening(entry.getFloatValue())) {
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
        if (level == null) {
            return;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + partialTick;
        float time = (float) (now % 24000.0);
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        Entity viewer = minecraft.getCameraEntity();
        List<Sitter> sitters = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof DreamBody || entity instanceof Player) || entity.isInvisible()) {
                continue;
            }
            float sit = Meditation.sit(entity, now);
            if (sit < 0.01F) {
                continue;
            }
            boolean ownEyes = entity == viewer && minecraft.options.getCameraType().isFirstPerson();
            Vec3 feet = entity.getPosition(partialTick);
            sitters.add(new Sitter(feet.subtract(camera).toVector3f(), sit, Meditation.lift(entity, now), ownEyes, entity.getId() * 0.618F, darkness(entity)));
        }
        List<Gaze> gazes = new ArrayList<>();
        for (Int2FloatMap.Entry entry : DreamwalkClient.possessed().int2FloatEntrySet()) {
            if (level.getEntity(entry.getIntKey()) instanceof LivingEntity creature && creature != viewer) {
                gazes.add(new Gaze(creature.getEyePosition(partialTick).subtract(camera).toVector3f(), MindControlFx.size(creature),
                        MindControlFx.faceOut(creature), creature.getViewYRot(partialTick), entry.getFloatValue()));
            }
        }
        List<Burst> bursts = List.copyOf(BURSTS);
        if (sitters.isEmpty() && gazes.isEmpty() && bursts.isEmpty()) {
            return;
        }
        GlowPass.submitPixels(collector, poseStack, (pose, buffer) -> {
            float before = Glow.darkness();
            int frame = (int) Math.floor(now);
            for (Sitter sitter : sitters) {
                Glow.darken(sitter.darkness());
                Vector3f feet = sitter.feet();
                // not through your own eyes, where they would rise straight through the view
                if (!sitter.ownEyes()) {
                    PixelSprite sprite = PixelSprite.inWorld(pose, feet.x, feet.y + CHEST, feet.z);
                    for (int w = 0; w < WISPS; w++) {
                        Wisp wisp = wisp(sitter, w, time);
                        if (wisp != null) {
                            drawWisp(sprite, wisp, w, frame);
                        }
                    }
                    sprite.draw(buffer);
                }
                // a faint ring on the ground beneath, its marks turning with the swirl
                float shown = sitter.sit() * (0.8F + 0.2F * Mth.sin(time * 0.09F + sitter.seed()));
                float turn = time * 0.02F + sitter.seed();
                FlatPixels.ring(buffer, pose, camera, feet.x + camera.x, feet.y + camera.y + 0.03, feet.z + camera.z, 0.44F, 0.76F,
                        (i, k, r, theta) -> {
                            if (!Pixels.shows(shown * (r < 0.5F ? 0.6F : 0.9F), i, k) || r >= 0.5F && r < 0.63F) {
                                return 0;
                            }
                            if (r < 0.5F) {
                                return Pixels.opaque(Pixels.CRIMSON);
                            }
                            int mark = Mth.floor((theta - turn) / (Mth.TWO_PI / 24.0F));
                            return Pixels.opaque(Math.floorMod(mark, 3) == 0 ? Pixels.BRIGHT : Pixels.SCARLET);
                        });
            }
            for (Gaze gaze : gazes) {
                Glow.darken(gaze.darkness());
                Vector3f head = gaze.head();
                PixelSprite sprite = PixelSprite.inWorld(pose, head.x, head.y, head.z);
                Wisps.eyes(sprite, head, gaze.size(), gaze.out(), gaze.yaw(), 1.0F, time);
                sprite.draw(buffer);
            }
            for (Burst burst : bursts) {
                // a ring bursting out of the chest as the spirit leaves, or closing in on it as it comes back, round a flash
                Glow.darken(burst.darkness());
                float k = (float) ((now - burst.at()) / BURST_TICKS);
                Vector3f chest = burst.feet().subtract(camera).toVector3f().add(0.0F, CHEST, 0.0F);
                float fade = 1.0F - Ease.outCubic(k);
                float size = burst.departing() ? 0.3F + 1.5F * Ease.outCubic(k) : 1.6F - 1.3F * Ease.outCubic(k);
                PixelSprite sprite = PixelSprite.inWorld(pose, chest.x, chest.y, chest.z);
                Wisps.burst(sprite, size, 0.25F, fade, frame);
                Wisps.ring(sprite, chest, FLAT_U, FLAT_V, size * 0.9F, 0.0F, 1.0F, Pixels.opaque(fade > 0.5F ? Pixels.BRIGHT : Pixels.SCARLET), 0, 1);
                if (fade > 0.4F) {
                    Wisps.orb(sprite, 4.0F * fade, 1.5F, frame, 37);
                }
                sprite.draw(buffer);
            }
            Glow.darken(before);
        });
    }

    /**
     * One wisp of the swirl as a line of pixels, whole along its middle and thinning out at its ends and as it fades, a
     * brighter fleck here and there along it.
     */
    private static void drawWisp(PixelSprite sprite, Wisp wisp, int index, int frame) {
        Vector3f[] points = wisp.points();
        for (int i = 1; i < points.length; i++) {
            if (Pixels.shows(wisp.alphas()[i] / 0.65F * 1.8F, i + frame, index + 50)) {
                sprite.line(points[i - 1], points[i], Pixels.opaque(i % 5 == 2 ? Pixels.BRIGHT : Pixels.SCARLET), 1, wisp.widths()[i] > 0.08F ? 2 : 1);
            }
        }
    }

    /**
     * One wisp of the swirl: rising from the ground around the body, turning as it goes and narrowing in about it, then
     * fading out above its head, each a little behind the last.
     */
    private static @Nullable Wisp wisp(Sitter sitter, int index, float time) {
        float phase = (time * 0.011F + index / (float) WISPS + sitter.seed()) % 1.0F;
        float life = Mth.sin(phase * Mth.PI) * sitter.sit();
        if (life < 0.02F) {
            return null;
        }
        Vector3f[] points = new Vector3f[WISP_POINTS];
        float[] widths = new float[WISP_POINTS];
        float[] alphas = new float[WISP_POINTS];
        float base = index * Mth.TWO_PI / WISPS + time * 0.03F + sitter.seed();
        float rise = phase * 1.1F;
        for (int i = 0; i < WISP_POINTS; i++) {
            float s = i / (WISP_POINTS - 1.0F);
            float angle = base + s * 2.6F;
            float radius = (0.66F - 0.26F * s) * (0.7F + 0.3F * sitter.sit());
            float y = 0.05F + rise + s * 0.85F;
            points[i] = new Vector3f(sitter.feet()).add(Mth.cos(angle) * radius, y, Mth.sin(angle) * radius);
            widths[i] = 0.11F * (1.0F - 0.5F * s);
            alphas[i] = 0.65F * life * Mth.sin(s * Mth.PI);
        }
        return new Wisp(points, widths, alphas);
    }

    private static float darkness(Entity entity) {
        return entity instanceof DreamBody body ? CorruptionClient.darkness(body.corruption()) : CorruptionClient.darkness(entity);
    }

    // ---------------------------------------------------------------- on the screen

    /**
     * How far your own eyes have dropped and risen with your body, as you sit down to dreamwalk and as you get up.
     */
    public static float eyeDrop(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.getCameraEntity() != player) {
            return 0.0F;
        }
        return Meditation.sit(player, player.level().getGameTime() + partialTick) > 0.0F
                ? Meditation.offset(player, player.level().getGameTime() + partialTick) : 0.0F;
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        double now = minecraft.level.getGameTime() + partialTick;
        float strength = ScarletClientConfig.get().reduceFlashing ? 0.55F : 1.0F;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float rise = DreamwalkClient.rise(player, partialTick);
        if (rise > 0.001F) {
            // the dark closing in as the body rises, until the view is gone
            MindControlFx.vignette(graphics, width, height, Math.max(width, height) * (0.1F + 0.42F * Ease.inOutCubic(rise)), 0.85F * rise,
                    ScarletPalette.VOID);
            MindControlFx.vignette(graphics, width, height, Math.max(width, height) * 0.12F * rise, 0.45F * rise * strength, ScarletPalette.SICKLY);
            float black = Ease.clamp01((rise - 0.78F) / 0.22F);
            if (black > 0.0F) {
                graphics.fill(0, 0, width, height, ARGB.color(0.95F * Ease.inOutCubic(black), ScarletPalette.VOID));
            }
        }
        if (DreamwalkClient.away() && !MindControlClient.dreaming()) {
            // on the way: nothing to see until the creature is here
            graphics.fill(0, 0, width, height, ARGB.color(0.95F, ScarletPalette.VOID));
        }
        if (MindControlClient.dreaming() && MindControlClient.movedIn()) {
            double since = MindControlClient.sinceMoved(now);
            if (since < OPEN_TICKS) {
                // the view opening out of the dark from the middle, into the creature's
                float k = Ease.clamp01((float) (since / OPEN_TICKS));
                graphics.fill(0, 0, width, height, ARGB.color(0.9F * (1.0F - Ease.outCubic(k)), ScarletPalette.VOID));
                MindControlFx.vignette(graphics, width, height, Math.max(width, height) * (0.5F - 0.4F * k), 1.0F - k, ScarletPalette.VOID);
                MindControlFx.sprite(graphics, width / 2.0F, height / 2.0F, Math.max(width, height) * (0.2F + 1.8F * k),
                        ARGB.color(0.55F * (1.0F - k) * strength, ScarletPalette.SICKLY));
            }
        }
        double woke = now - DreamwalkClient.wokeAt();
        if (woke >= 0.0 && woke < WAKE_TICKS) {
            // waking with a start
            float k = (float) (woke / WAKE_TICKS);
            float alpha = (1.0F - Ease.outCubic(k)) * strength;
            graphics.fill(0, 0, width, height, ARGB.color(alpha * 0.5F, ScarletPalette.CRIMSON));
            MindControlFx.vignette(graphics, width, height, Math.max(width, height) * (0.45F - 0.3F * k), alpha, ScarletPalette.VOID);
            MindControlFx.sprite(graphics, width / 2.0F, height / 2.0F, Math.max(width, height) * (0.3F + 1.4F * k),
                    ARGB.color(alpha * 0.5F, ScarletPalette.BRIGHT_SCARLET));
        }
    }

    /**
     * Someone sat dreamwalking, or rising to, as laid out this frame relative to the camera.
     *
     * @param ownEyes the view is their own, in the first person
     */
    private record Sitter(Vector3f feet, float sit, float lift, boolean ownEyes, float seed, float darkness) {
    }

    private record Wisp(Vector3f[] points, float[] widths, float[] alphas) {
    }

    private record Gaze(Vector3f head, float size, float out, float yaw, float darkness) {
    }

    private record Burst(Vec3 feet, double at, boolean departing, float darkness) {
    }
}
