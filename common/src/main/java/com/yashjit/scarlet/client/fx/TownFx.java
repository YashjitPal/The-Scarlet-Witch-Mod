package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.hex.HexClient;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.hex.Hex;
import com.yashjit.scarlet.hex.HexShape;
import com.yashjit.scarlet.hex.HexSnapshot;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.hex.town.TownPlan;
import com.yashjit.scarlet.network.TownBuildPayload;
import com.yashjit.scarlet.network.TownCutPayload;
import com.yashjit.scarlet.network.TownFormPayload;
import com.yashjit.scarlet.network.TownUnformPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * A Hex's town building itself, as everyone nearby sees it: scarlet light races around the edge of a lot, and whatever
 * stood there, a cottage, a tree, a pile of wool, dissolves under a scarlet line sweeping down through it, shedding red
 * static and sparks. Then a glowing outline climbs with the house as it rises, while the frame knocks and the walls
 * settle into place. All the while the lot glitches red, as the Hex rewrites what was there into what it wants: red
 * bars tear across it, red pixels flicker over it, and red static crawls across it.
 *
 * <p>The caster's home, going up slowly around them, is written a block at a time: each block flickers in as an
 * outline where it will stand as their magic reaches for it, then snaps in glitching red, as {@link BlockGlitch} has
 * it.
 *
 * <p>None of it shows past the Hex's wall, where the town isn't. And as the wall moves in, or falls, the town glitches
 * out right where it passes, in red bars, static and sparks, as what stood there before comes back.
 */
public final class TownFx {

    private static final float TRACE_TICKS = 12.0F;
    private static final float TRACE_FADE = 40.0F;
    private static final float LIFT_FROM = 8.0F;
    private static final float AFTERGLOW = 18.0F;
    /** How long a bit of the town takes to glitch out where it went back. */
    private static final float CUT_TICKS = 12.0F;
    private static final int MAX_CUTS = 1200;
    /** Most crackles a tick from bits of the town going, however many go. */
    private static final int CUT_SOUNDS = 2;
    private static final double CUT_REACH = 96.0;
    private static final int MAX_FORMS = 900;
    private static final double FORM_REACH = 80.0;
    /** How hard the glitch over the whole of the caster's home is, against the rest, as its blocks glitch on their own. */
    private static final float HOME_GLITCH = 0.3F;
    private static final Vector3f FLAT_U = new Vector3f(1, 0, 0);
    private static final Vector3f FLAT_V = new Vector3f(0, 0, 1);

    private static final List<TownBuildPayload> BUILDS = new ArrayList<>();
    private static final List<Cut> CUTS = new ArrayList<>();
    private static final List<Form> FORMS = new ArrayList<>();
    private static final List<Unform> UNFORMS = new ArrayList<>();
    private static final AABB FULL = new AABB(0, 0, 0, 1, 1, 1);
    private static int formsTold;

    private TownFx() {
    }

    public static void onBuild(TownBuildPayload payload) {
        BUILDS.add(payload);
    }

    public static void onForm(TownFormPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        int[] blocks = payload.blocks();
        for (int i = 0; i + 3 < blocks.length; i += 4) {
            FORMS.add(new Form(++formsTold, new BlockPos(blocks[i], blocks[i + 1], blocks[i + 2]), now, now + blocks[i + 3]));
        }
        if (FORMS.size() > MAX_FORMS) {
            FORMS.subList(0, FORMS.size() - MAX_FORMS).clear();
        }
    }

    /**
     * Blocks of a home left behind by its Hex about to go: each glitches out the way it glitched in. Its shape is taken
     * now, while it still stands, to draw what is left of it after.
     */
    public static void onUnform(TownUnformPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        int[] blocks = payload.blocks();
        for (int i = 0; i + 3 < blocks.length; i += 4) {
            BlockPos pos = new BlockPos(blocks[i], blocks[i + 1], blocks[i + 2]);
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getShape(level, pos);
            UNFORMS.add(new Unform(pos, state, shape.isEmpty() ? FULL : shape.bounds(), now, now + blocks[i + 3]));
        }
        if (UNFORMS.size() > MAX_FORMS) {
            UNFORMS.subList(0, UNFORMS.size() - MAX_FORMS).clear();
        }
    }

    /**
     * Blocks of a caster's home told of since the one counted {@code after}, for their magic to reach out to.
     */
    public static List<Form> formsAfter(int after) {
        List<Form> found = new ArrayList<>();
        for (int i = FORMS.size() - 1; i >= 0 && FORMS.get(i).id() > after; i--) {
            found.add(FORMS.get(i));
        }
        return found;
    }

    /**
     * How many blocks of homes have been told of so far.
     */
    public static int formsTold() {
        return formsTold;
    }

    /**
     * The caster's home building itself near a point, if it is, or has lately.
     */
    public static @Nullable TownBuildPayload home(Vec3 near) {
        TownBuildPayload found = null;
        for (TownBuildPayload build : BUILDS) {
            if (build.kind() == TownPlan.Kind.HOME.ordinal() && center(build).distanceToSqr(near.x, build.ground() + 1, near.z) < 24 * 24
                    && (found == null || build.start() > found.start())) {
                found = build;
            }
        }
        return found;
    }

    /**
     * A caster's home going up inside their Hex right now, raised again somewhere new after the Hex was cast.
     */
    public static @Nullable TownBuildPayload homeRising(HexSnapshot hex, double now) {
        TownBuildPayload found = null;
        for (TownBuildPayload build : BUILDS) {
            if (build.ownHome() && now >= build.start() && now <= build.start() + build.duration()
                    && HexShape.contains(hex.center(), hex.radius(), center(build)) && (found == null || build.start() > found.start())) {
                found = build;
            }
        }
        return found;
    }

    public static Vec3 middle(TownBuildPayload build) {
        return center(build);
    }

    /**
     * Somewhere on the walls of a part as high as they have risen so far.
     */
    public static Vec3 risingPoint(TownBuildPayload build, double now, RandomSource random) {
        float t = (float) (now - build.start() - build.clearTicks());
        double y = build.ground() + 1.0 + Math.max(0.5, rise(build, t) * build.height()) * random.nextDouble();
        return edgePoint(build, random.nextFloat(), y);
    }

    public static void onCut(TownCutPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        int[] cells = payload.cells();
        for (int i = 0; i + 3 < cells.length; i += 4) {
            CUTS.add(new Cut(cells[i], cells[i + 1], cells[i + 2], cells[i + 3], now));
        }
        if (CUTS.size() > MAX_CUTS) {
            CUTS.subList(0, CUTS.size() - MAX_CUTS).clear();
        }
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            BUILDS.clear();
            CUTS.clear();
            FORMS.clear();
            UNFORMS.clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        long now = level.getGameTime();
        BUILDS.removeIf(build -> now - build.start() > build.duration() + AFTERGLOW + 2);
        CUTS.removeIf(cut -> now - cut.start > CUT_TICKS || now < cut.start);
        FORMS.removeIf(form -> now - form.lands() > BlockGlitch.SETTLE + 1 || now < form.told());
        UNFORMS.removeIf(unform -> now - unform.goes() > BlockGlitch.GONE + 1 || now < unform.told());
        RandomSource random = ScarletFx.random();
        float density = ScarletFx.density();
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        formTick(level, now, camera);
        unformTick(level, now, camera, random);
        for (TownBuildPayload build : BUILDS) {
            boolean street = TownPlan.Kind.values()[Math.clamp(build.kind(), 0, TownPlan.Kind.values().length - 1)].isStreet();
            boolean home = build.kind() == TownPlan.Kind.HOME.ordinal();
            if (camera.distanceToSqr(center(build)) > 128 * 128) {
                continue;
            }
            Clip clip = Clip.of(center(build), now);
            Area area = clip.over(build);
            if (area == null) {
                continue;
            }
            float dissolve = dissolve(build, now - build.start());
            if (dissolve > 0.0F) {
                // what stood here comes apart into red static and sparks, below the line sweeping down through it
                double sweep = sweep(build, now - build.start());
                Vec3 min = new Vec3(area.minX, build.ground() + 1.0, area.minZ);
                Vec3 max = new Vec3(area.maxX, Math.max(build.ground() + 2.0, sweep), area.maxZ);
                Glitch.crawl(min, max, dissolve * 3.0F, clip::inside);
                for (int i = 0, n = Math.round(6 * dissolve * density); i < n; i++) {
                    Vec3 at = new Vec3(min.x + random.nextDouble() * (max.x - min.x), sweep - random.nextDouble() * 1.5,
                            min.z + random.nextDouble() * (max.z - min.z));
                    if (!clip.inside(at)) {
                        continue;
                    }
                    int color = random.nextFloat() < 0.3F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET;
                    ScarletFx.spark(at, new Vec3(random.nextGaussian() * 0.02, 0.04 + random.nextDouble() * 0.05, random.nextGaussian() * 0.02),
                            12 + random.nextInt(12), 0.04F + random.nextFloat() * 0.03F, color, ScarletPalette.SCARLET, -0.002F, 0.9F);
                }
                if (random.nextFloat() < 0.35F * dissolve) {
                    Vec3 at = new Vec3(min.x + random.nextDouble() * (max.x - min.x), sweep, min.z + random.nextDouble() * (max.z - min.z));
                    if (clip.inside(at)) {
                        level.playLocalSound(at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.25F, 1.6F + random.nextFloat() * 0.4F,
                                false);
                    }
                }
            }
            // the house builds once the land is clear
            float t = now - build.start() - build.clearTicks();
            float rise = rise(build, t);
            double y = build.ground() + 1.0 + rise * build.height();
            // sparks shed from the edge of the rising outline; the caster's home has its blocks glitching instead
            int sparks = home ? 0 : Math.round((street ? 1.0F : 4.0F) * density);
            if (t >= 0 && t < building(build)) {
                for (int i = 0; i < sparks; i++) {
                    Vec3 at = edgePoint(build, random.nextFloat(), y);
                    if (!clip.inside(at)) {
                        continue;
                    }
                    ScarletFx.spark(at, new Vec3(random.nextGaussian() * 0.01, 0.02 + random.nextDouble() * 0.03, random.nextGaussian() * 0.01),
                            10 + random.nextInt(10), 0.03F, random.nextFloat() < 0.3F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET,
                            ScarletPalette.SCARLET, -0.001F, 0.92F);
                }
                if (!street && !home && random.nextFloat() < 0.5F * density) {
                    Vec3 at = new Vec3(area.minX + random.nextDouble() * (area.maxX - area.minX), y,
                            area.minZ + random.nextDouble() * (area.maxZ - area.minZ));
                    if (clip.inside(at)) {
                        ChaosDust.spawn(at, new Vec3(0, -0.01, 0));
                    }
                }
            }
            // the frame knocks together and the walls settle in as it rises
            if (!street && !home && t > LIFT_FROM && t < building(build) && (now + build.minX()) % 5 == 0) {
                Vec3 at = edgePoint(build, random.nextFloat(), y);
                if (clip.inside(at)) {
                    level.playLocalSound(at.x, at.y, at.z, random.nextBoolean() ? SoundEvents.WOOD_PLACE : SoundEvents.STONE_PLACE, SoundSource.BLOCKS,
                            0.35F, 0.8F + random.nextFloat() * 0.4F, false);
                }
            }
            float glitch = glitch(build, t) * (home ? HOME_GLITCH : 1.0F);
            if (glitch > 0.02F) {
                // red static crawling over what is being rewritten, and the crackle of it
                double top = build.ground() + 1.0 + Math.max(1.5, rise * build.height());
                Glitch.crawl(new Vec3(area.minX, build.ground() + 1.0, area.minZ), new Vec3(area.maxX, top, area.maxZ), glitch * (street ? 0.6F : 2.0F),
                        clip::inside);
                if (random.nextFloat() < 0.12F * glitch) {
                    Vec3 at = edgePoint(build, random.nextFloat(), top * 0.5 + (build.ground() + 1.0) * 0.5);
                    if (clip.inside(at)) {
                        level.playLocalSound(at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.2F, 1.8F + random.nextFloat() * 0.2F,
                                false);
                    }
                }
            }
            if (!street && !home && t == (long) building(build)) {
                for (int i = 0; i < Math.round(30 * density); i++) {
                    Vec3 at = edgePoint(build, random.nextFloat(), build.ground() + 1.0 + random.nextDouble() * build.height());
                    if (!clip.inside(at)) {
                        continue;
                    }
                    ScarletFx.spark(at, new Vec3(random.nextGaussian() * 0.02, 0.03 + random.nextDouble() * 0.04, random.nextGaussian() * 0.02),
                            14 + random.nextInt(12), 0.035F, ScarletPalette.CORE, ScarletPalette.BRIGHT_SCARLET, -0.0015F, 0.93F);
                }
            }
        }
        cutTick(level, now, random, density, camera);
    }

    /**
     * Each block of a home that lands this tick sets itself down with its own sound, softly, a couple at most a tick so a
     * whole course of a wall landing at once is not a din.
     */
    private static void formTick(ClientLevel level, long now, Vec3 camera) {
        int sounds = 0;
        for (Form form : FORMS) {
            if (form.lands() != now || sounds >= 2 || camera.distanceToSqr(Vec3.atCenterOf(form.pos())) > FORM_REACH * FORM_REACH) {
                continue;
            }
            BlockState state = level.getBlockState(form.pos());
            if (state.isAir()) {
                continue;
            }
            sounds++;
            SoundType sound = state.getSoundType();
            level.playLocalSound(form.pos(), sound.getPlaceSound(), SoundSource.BLOCKS, 0.35F, sound.getPitch() * (0.85F + ScarletFx.random().nextFloat() * 0.3F),
                    false);
        }
    }

    /**
     * Each block of a home left behind that goes this tick comes apart with its own sound, softly, a couple at most a
     * tick, and a puff of static where it stood.
     */
    private static void unformTick(ClientLevel level, long now, Vec3 camera, RandomSource random) {
        int sounds = 0;
        for (Unform unform : UNFORMS) {
            if (unform.goes() != now || camera.distanceToSqr(Vec3.atCenterOf(unform.pos())) > FORM_REACH * FORM_REACH) {
                continue;
            }
            Vec3 at = Vec3.atCenterOf(unform.pos());
            if (random.nextFloat() < 0.4F) {
                Glitch.puff(at.subtract(0.55, 0.55, 0.55), at.add(0.55, 0.55, 0.55));
            }
            if (sounds < 2 && !unform.state().isAir()) {
                sounds++;
                SoundType sound = unform.state().getSoundType();
                level.playLocalSound(unform.pos(), sound.getBreakSound(), SoundSource.BLOCKS, 0.3F,
                        sound.getPitch() * (0.9F + random.nextFloat() * 0.3F), false);
            }
        }
    }

    /**
     * Where bits of the town just went: red static bursting over them and a few sparks drifting up, and here and there the
     * crackle of a picture losing its signal.
     */
    private static void cutTick(ClientLevel level, long now, RandomSource random, float density, Vec3 camera) {
        int sounds = 0;
        for (Cut cut : CUTS) {
            if (cut.shown) {
                continue;
            }
            cut.shown = true;
            Vec3 min = new Vec3(cut.x, cut.low, cut.z);
            Vec3 max = new Vec3(cut.x + 2.0, cut.high + 1.0, cut.z + 2.0);
            if (camera.distanceToSqr((min.x + max.x) / 2, (min.y + max.y) / 2, (min.z + max.z) / 2) > CUT_REACH * CUT_REACH) {
                continue;
            }
            Glitch.crawl(min, max, 1.2F + 0.25F * (cut.high - cut.low));
            for (int i = 0, n = Math.round(2 * density); i < n; i++) {
                Vec3 at = new Vec3(min.x + random.nextDouble() * 2.0, min.y + random.nextDouble() * (max.y - min.y), min.z + random.nextDouble() * 2.0);
                int color = random.nextFloat() < 0.3F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET;
                ScarletFx.spark(at, new Vec3(random.nextGaussian() * 0.015, 0.03 + random.nextDouble() * 0.04, random.nextGaussian() * 0.015),
                        10 + random.nextInt(10), 0.035F + random.nextFloat() * 0.025F, color, ScarletPalette.SCARLET, -0.0015F, 0.9F);
            }
            if (sounds < CUT_SOUNDS && random.nextFloat() < 0.15F) {
                sounds++;
                level.playLocalSound((min.x + max.x) / 2, (min.y + max.y) / 2, (min.z + max.z) / 2, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                        0.22F, 1.7F + random.nextFloat() * 0.3F, false);
            }
        }
    }

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack) {
        if (BUILDS.isEmpty() && CUTS.isEmpty() && FORMS.isEmpty() && UNFORMS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        double now = level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        List<TownBuildPayload> builds = List.copyOf(BUILDS);
        List<Cut> cuts = List.copyOf(CUTS);
        List<Form> forms = List.copyOf(FORMS);
        List<Unform> unforms = List.copyOf(UNFORMS);
        long frame = Glitch.frame(now);
        GlowPass.submit(collector, poseStack, (pose, buffer) -> {
            Glow.Billboard axes = Glow.billboard(pose);
            for (Form form : forms) {
                BlockPos pos = form.pos();
                if (camera.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > FORM_REACH * FORM_REACH) {
                    continue;
                }
                BlockGlitch.draw(buffer, pose, level, pos, camera, (float) (now - form.lands()), form.lands() - form.told(), frame,
                        pos.getX() * 73 + pos.getY() * 31 + pos.getZ() * 17);
            }
            for (Unform unform : unforms) {
                BlockPos pos = unform.pos();
                if (camera.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > FORM_REACH * FORM_REACH) {
                    continue;
                }
                BlockGlitch.leave(buffer, pose, level, pos, camera, (float) (now - unform.goes()), unform.goes() - unform.told(), unform.box(),
                        frame, pos.getX() * 73 + pos.getY() * 31 + pos.getZ() * 17);
            }
            for (TownBuildPayload build : builds) {
                boolean home = build.kind() == TownPlan.Kind.HOME.ordinal();
                Clip clip = Clip.of(center(build), now);
                Area area = clip.over(build);
                if (area == null) {
                    continue;
                }
                float since = (float) (now - build.start());
                trace(buffer, pose, build, since, camera, clip);
                float dissolve = dissolve(build, since);
                if (dissolve > 0.0F) {
                    // the scarlet line sweeping down through what stood here, and the red glitch over all of it
                    float top = build.clearTop() + 1.0F - build.ground() - 1.0F;
                    float y = (float) (sweep(build, since) - camera.y);
                    float x0 = (float) (build.minX() - camera.x);
                    float x1 = (float) (build.maxX() + 1 - camera.x);
                    float z0 = (float) (build.minZ() - camera.z);
                    float z1 = (float) (build.maxZ() + 1 - camera.z);
                    outline(buffer, pose, x0, z0, x1, z1, y, 1.0F, 0.16F, Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.8F * dissolve), clip, camera);
                    outline(buffer, pose, x0, z0, x1, z1, y, 1.0F, 0.05F, Glow.withAlpha(ScarletPalette.CORE, 0.7F * dissolve), clip, camera);
                    if (area.whole) {
                        Glow.planeDisc(buffer, pose, (x0 + x1) / 2, y, (z0 + z1) / 2, FLAT_U, FLAT_V, Math.max(x1 - x0, z1 - z0) * 0.6F,
                                Glow.withAlpha(ScarletPalette.SCARLET, 0.12F * dissolve), Glow.withAlpha(ScarletPalette.SCARLET, 0.0F), 24);
                    }
                    Vector3f center = new Vector3f((float) (area.centerX() - camera.x), (float) (build.ground() + 1.0 + top / 2.0 - camera.y),
                            (float) (area.centerZ() - camera.z));
                    Glitch.draw(buffer, pose, axes, center, area.size(), Math.max(1.5F, top), dissolve, frame, build.minX() * 17 + build.minZ() * 5);
                }
                float t = since - build.clearTicks();
                draw(buffer, pose, build, t, camera, clip, area);
                // up close, the glitch over a whole lot would only be a flat red slab across the view
                float glitch = glitch(build, t) * area.closeness(camera) * (home ? HOME_GLITCH : 1.0F);
                if (glitch > 0.02F) {
                    float height = Math.max(1.5F, rise(build, t) * build.height());
                    Vector3f center = new Vector3f((float) (area.centerX() - camera.x), (float) (build.ground() + 1.0 + height / 2.0 - camera.y),
                            (float) (area.centerZ() - camera.z));
                    Glitch.draw(buffer, pose, axes, center, area.size(), height, glitch, frame, build.minX() * 31 + build.minZ());
                }
            }
            for (Cut cut : cuts) {
                float age = (float) (now - cut.start);
                if (age < 0.0F || age > CUT_TICKS) {
                    continue;
                }
                double cx = cut.x + 1.0;
                double cz = cut.z + 1.0;
                if ((cx - camera.x) * (cx - camera.x) + (cz - camera.z) * (cz - camera.z) > CUT_REACH * CUT_REACH) {
                    continue;
                }
                // flaring up the moment it goes, then guttering out
                float k = age / CUT_TICKS;
                float intensity = Math.min(1.0F, age * 2.0F + 0.4F) * (1.0F - Ease.inCubic(k));
                float height = cut.high - cut.low + 1.2F;
                Vector3f center = new Vector3f((float) (cx - camera.x), (float) (cut.low + height / 2.0 - camera.y), (float) (cz - camera.z));
                Glitch.draw(buffer, pose, axes, center, 2.4F, height, intensity, frame, cut.x * 73 + cut.z * 19);
            }
        });
    }

    /**
     * How hard what stood on a lot is dissolving, 0 to 1: rising fast as the Hex reaches it and fading as the last of
     * it goes.
     */
    private static float dissolve(TownBuildPayload build, float since) {
        if (build.clearTicks() <= 0 || since < 0.0F || since > build.clearTicks() + 4.0F) {
            return 0.0F;
        }
        float k = since / (build.clearTicks() + 4.0F);
        return Math.min(1.0F, k * 6.0F) * (1.0F - Ease.inCubic(k));
    }

    /**
     * How high the line dissolving what stood on a lot has come down to: from just over the top of it to the ground,
     * as the blocks go from the top down.
     */
    private static double sweep(TownBuildPayload build, float since) {
        float k = Ease.clamp01(since / Math.max(1.0F, build.clearTicks()));
        double top = build.clearTop() + 1.2;
        double bottom = build.ground() + 1.0;
        return top + (bottom - top) * k;
    }

    /**
     * How hard a part glitches red as it is rewritten: strongest while it rises, a quick flicker for a street.
     */
    private static float glitch(TownBuildPayload build, float t) {
        boolean street = TownPlan.Kind.values()[Math.clamp(build.kind(), 0, TownPlan.Kind.values().length - 1)].isStreet();
        float k = Ease.clamp01(t / Math.max(1.0F, building(build) + 6.0F));
        return t < 0.0F ? 0.0F : (float) Math.sin(Math.PI * k) * (street ? 0.45F : 1.0F);
    }

    /**
     * Ticks a part takes to build, once whatever stood there has dissolved.
     */
    private static int building(TownBuildPayload build) {
        return build.duration() - build.clearTicks();
    }

    /**
     * Light running around a lot's edge on the ground the moment the Hex reaches it, then fading.
     */
    private static void trace(VertexConsumer buffer, PoseStack.Pose pose, TownBuildPayload build, float since, Vec3 camera, Clip clip) {
        if (since < 0.0F || since > TRACE_FADE) {
            return;
        }
        boolean street = TownPlan.Kind.values()[Math.clamp(build.kind(), 0, TownPlan.Kind.values().length - 1)].isStreet();
        float x0 = (float) (build.minX() - camera.x);
        float x1 = (float) (build.maxX() + 1 - camera.x);
        float z0 = (float) (build.minZ() - camera.z);
        float z1 = (float) (build.maxZ() + 1 - camera.z);
        float traced = Ease.outCubic(Ease.clamp01(since / TRACE_TICKS));
        float traceFade = 1.0F - Ease.clamp01((since - TRACE_TICKS) / (TRACE_FADE - TRACE_TICKS));
        float ground = (float) (build.ground() + 1.03 - camera.y);
        outline(buffer, pose, x0, z0, x1, z1, ground, traced, street ? 0.09F : 0.14F, Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.85F * traceFade),
                clip, camera);
        outline(buffer, pose, x0, z0, x1, z1, ground, traced, street ? 0.03F : 0.05F, Glow.withAlpha(ScarletPalette.CORE, 0.7F * traceFade), clip,
                camera);
    }

    private static void draw(VertexConsumer buffer, PoseStack.Pose pose, TownBuildPayload build, float t, Vec3 camera, Clip clip, Area area) {
        if (t < 0.0F || t > building(build) + AFTERGLOW) {
            return;
        }
        boolean street = TownPlan.Kind.values()[Math.clamp(build.kind(), 0, TownPlan.Kind.values().length - 1)].isStreet();
        if (street) {
            return;
        }
        float x0 = (float) (build.minX() - camera.x);
        float x1 = (float) (build.maxX() + 1 - camera.x);
        float z0 = (float) (build.minZ() - camera.z);
        float z1 = (float) (build.maxZ() + 1 - camera.z);
        // the rising outline, climbing with the walls and the roof
        float rise = rise(build, t);
        float lift = Ease.clamp01((t - LIFT_FROM) / 6.0F) * (1.0F - Ease.clamp01((t - building(build)) / AFTERGLOW));
        if (lift > 0.0F) {
            float y = (float) (build.ground() + 1.0 + rise * build.height() - camera.y);
            outline(buffer, pose, x0, z0, x1, z1, y, 1.0F, 0.12F, Glow.withAlpha(ScarletPalette.SCARLET, 0.6F * lift), clip, camera);
            outline(buffer, pose, x0, z0, x1, z1, y, 1.0F, 0.04F, Glow.withAlpha(ScarletPalette.CORE, 0.45F * lift), clip, camera);
            if (area.whole) {
                Glow.planeDisc(buffer, pose, (x0 + x1) / 2, y, (z0 + z1) / 2, FLAT_U, FLAT_V, Math.max(x1 - x0, z1 - z0) * 0.55F,
                        Glow.withAlpha(ScarletPalette.SCARLET, 0.06F * lift), Glow.withAlpha(ScarletPalette.SCARLET, 0.0F), 24);
            }
        }
    }

    /**
     * How far up the build the outline has climbed: from the ground to the top over the build.
     */
    private static float rise(TownBuildPayload build, float t) {
        return Ease.inOutCubic(Ease.clamp01((t - LIFT_FROM) / Math.max(1.0F, building(build) - LIFT_FROM)));
    }

    /**
     * A rectangle's edge in light, drawn as far around as {@code amount} reaches, starting from its first corner.
     */
    public static void outline(VertexConsumer buffer, PoseStack.Pose pose, float x0, float z0, float x1, float z1, float y, float amount, float width,
                               int color) {
        outline(buffer, pose, x0, z0, x1, z1, y, amount, width, color, Clip.NONE, Vec3.ZERO);
    }

    /**
     * The same, only where it lies inside the wall.
     */
    static void outline(VertexConsumer buffer, PoseStack.Pose pose, float x0, float z0, float x1, float z1, float y, float amount, float width,
                        int color, Clip clip, Vec3 camera) {
        float w = x1 - x0;
        float d = z1 - z0;
        float perimeter = 2 * (w + d);
        float left = perimeter * amount;
        float[][] corners = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}, {x0, z0}};
        for (int i = 0; i < 4 && left > 0.0F; i++) {
            float[] a = corners[i];
            float[] b = corners[i + 1];
            float length = Math.abs(b[0] - a[0]) + Math.abs(b[1] - a[1]);
            float k = Math.min(1.0F, left / length);
            line(buffer, pose, a[0], a[1], a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, y, width, color, clip, camera);
            left -= length;
        }
    }

    /**
     * A line of light on a level, drawn in the runs of it that lie inside the wall, each run ending right at the wall.
     */
    private static void line(VertexConsumer buffer, PoseStack.Pose pose, float ax, float az, float bx, float bz, float y, float width, int color,
                             Clip clip, Vec3 camera) {
        if (clip.none()) {
            Glow.planeLine(buffer, pose, 0, y, 0, FLAT_U, FLAT_V, ax, az, bx, bz, width, color);
            return;
        }
        int steps = Math.max(1, (int) Math.ceil(Math.abs(bx - ax) + Math.abs(bz - az)));
        float runFrom = clip.inside(camera.x + ax, camera.z + az) ? 0.0F : -1.0F;
        float before = 0.0F;
        boolean wasInside = runFrom >= 0.0F;
        for (int i = 1; i <= steps; i++) {
            float t = (float) i / steps;
            boolean inside = clip.inside(camera.x + ax + (bx - ax) * t, camera.z + az + (bz - az) * t);
            if (inside != wasInside) {
                // close in on where the line crosses the wall
                float lo = before;
                float hi = t;
                for (int j = 0; j < 6; j++) {
                    float mid = (lo + hi) / 2;
                    boolean in = clip.inside(camera.x + ax + (bx - ax) * mid, camera.z + az + (bz - az) * mid);
                    if (in == wasInside) {
                        lo = mid;
                    } else {
                        hi = mid;
                    }
                }
                float crossing = wasInside ? lo : hi;
                if (wasInside) {
                    segment(buffer, pose, ax, az, bx, bz, runFrom, crossing, y, width, color);
                } else {
                    runFrom = crossing;
                }
                wasInside = inside;
            }
            before = t;
        }
        if (wasInside) {
            segment(buffer, pose, ax, az, bx, bz, runFrom, 1.0F, y, width, color);
        }
    }

    private static void segment(VertexConsumer buffer, PoseStack.Pose pose, float ax, float az, float bx, float bz, float from, float to, float y,
                                float width, int color) {
        if (to - from <= 1.0E-4F) {
            return;
        }
        Glow.planeLine(buffer, pose, 0, y, 0, FLAT_U, FLAT_V, ax + (bx - ax) * from, az + (bz - az) * from, ax + (bx - ax) * to, az + (bz - az) * to,
                width, color);
    }

    private static Vec3 edgePoint(TownBuildPayload build, float along, double y) {
        double w = build.maxX() + 1 - build.minX();
        double d = build.maxZ() + 1 - build.minZ();
        double s = along * 2 * (w + d);
        if (s < w) {
            return new Vec3(build.minX() + s, y, build.minZ());
        }
        s -= w;
        if (s < d) {
            return new Vec3(build.maxX() + 1, y, build.minZ() + s);
        }
        s -= d;
        if (s < w) {
            return new Vec3(build.maxX() + 1 - s, y, build.maxZ() + 1);
        }
        s -= w;
        return new Vec3(build.minX(), y, build.maxZ() + 1 - s);
    }

    private static Vec3 center(TownBuildPayload build) {
        return new Vec3((build.minX() + build.maxX() + 1) / 2.0, build.ground() + 1, (build.minZ() + build.maxZ() + 1) / 2.0);
    }

    /**
     * The wall the magic of a part of the town stays inside, as it is drawn: none while a Hex is still founding its
     * home, before it has a wall to speak of.
     */
    record Clip(Vec3 center, float radius) {

        private static final Clip NONE = new Clip(Vec3.ZERO, Float.MAX_VALUE);
        /** Farthest from a Hex a part of a town can lie. */
        private static final double TOWN_REACH = HexShape.reach(Hexes.MAX_RADIUS) + 32.0;

        static Clip of(Vec3 at, double now) {
            HexSnapshot nearest = null;
            double best = TOWN_REACH * TOWN_REACH;
            for (HexSnapshot hex : Hexes.clientHexes()) {
                double dx = hex.center().x - at.x;
                double dz = hex.center().z - at.z;
                double distance = dx * dx + dz * dz;
                if (distance < best) {
                    best = distance;
                    nearest = hex;
                }
            }
            if (nearest == null || nearest.phaseValue() == Hex.Phase.FOUNDING) {
                return NONE;
            }
            return new Clip(nearest.center(), HexClient.drawnRadius(nearest, now));
        }

        boolean none() {
            return this == NONE;
        }

        boolean inside(double x, double z) {
            return none() || HexShape.contains(center, radius, new Vec3(x, center.y, z));
        }

        boolean inside(Vec3 at) {
            return inside(at.x, at.z);
        }

        /**
         * The part of a lot inside the wall, as a box around it, or null if none of it is.
         */
        @Nullable Area over(TownBuildPayload build) {
            double x0 = build.minX();
            double z0 = build.minZ();
            double x1 = build.maxX() + 1.0;
            double z1 = build.maxZ() + 1.0;
            if (none() || inside(x0, z0) && inside(x1, z0) && inside(x0, z1) && inside(x1, z1)) {
                return new Area(x0, z0, x1, z1, true);
            }
            double minX = Double.MAX_VALUE;
            double minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxZ = -Double.MAX_VALUE;
            for (int x = build.minX(); x <= build.maxX(); x++) {
                for (int z = build.minZ(); z <= build.maxZ(); z++) {
                    if (inside(x + 0.5, z + 0.5)) {
                        minX = Math.min(minX, x);
                        minZ = Math.min(minZ, z);
                        maxX = Math.max(maxX, x + 1.0);
                        maxZ = Math.max(maxZ, z + 1.0);
                    }
                }
            }
            return minX > maxX ? null : new Area(minX, minZ, maxX, maxZ, false);
        }
    }

    /**
     * @param whole whether all of the lot is inside the wall
     */
    private record Area(double minX, double minZ, double maxX, double maxZ, boolean whole) {

        double centerX() {
            return (minX + maxX) / 2.0;
        }

        double centerZ() {
            return (minZ + maxZ) / 2.0;
        }

        float size() {
            return (float) Math.max(maxX - minX, maxZ - minZ);
        }

        /**
         * 1 seen from a little way off, fading to 0 as the camera comes within a few blocks of it or into it.
         */
        float closeness(Vec3 camera) {
            double dx = Math.max(0.0, Math.max(minX - camera.x, camera.x - maxX));
            double dz = Math.max(0.0, Math.max(minZ - camera.z, camera.z - maxZ));
            return Ease.clamp01((float) ((Math.sqrt(dx * dx + dz * dz) - 2.0) / 4.0));
        }
    }

    /**
     * A block of a caster's home on its way.
     *
     * @param id    its place in the count of all told of
     * @param told  when word of it came
     * @param lands when it is put down
     */
    public record Form(int id, BlockPos pos, long told, long lands) {
    }

    /**
     * A block of a home left behind on its way out.
     *
     * @param state what it was, taken when word of it came
     * @param box   its shape then
     * @param goes  when it goes
     */
    private record Unform(BlockPos pos, BlockState state, AABB box, long told, long goes) {
    }

    /**
     * A two-by-two of columns where the town just went back to what was there.
     */
    private static final class Cut {
        final int x;
        final int z;
        final int low;
        final int high;
        final long start;
        boolean shown;

        Cut(int x, int z, int low, int high, long start) {
            this.x = x;
            this.z = z;
            this.low = low;
            this.high = high;
            this.start = start;
        }
    }
}
