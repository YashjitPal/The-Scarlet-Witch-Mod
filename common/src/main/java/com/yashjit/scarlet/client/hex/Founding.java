package com.yashjit.scarlet.client.hex;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.anim.PoseBlends;
import com.yashjit.scarlet.client.fx.MagicBeam;
import com.yashjit.scarlet.client.fx.ScarletFx;
import com.yashjit.scarlet.client.fx.TownFx;
import com.yashjit.scarlet.client.magic.Hands;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.config.ScarletClientConfig;
import com.yashjit.scarlet.hex.Hex;
import com.yashjit.scarlet.hex.HexShape;
import com.yashjit.scarlet.hex.HexSnapshot;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.network.TownBuildPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Founding a Hex, the way Wanda founded Westview. The caster is lifted off the ground and floats in the middle of the
 * empty lot, chest thrown up to the sky and head back, arms hanging open, wisps of scarlet curling around them. Their
 * magic reaches out of their palms and their heart in writhing tendrils, each one to where a block of the house is
 * about to land, and the house writes itself around them a block at a time, slowly, a stage at a time. Once it stands
 * they come down on its floor, and the Hex bursts out of them: a flash, a boom, and a scarlet blast racing out over the
 * land with the wall.
 *
 * <p>For the caster themselves the view leaves their eyes while the house rises and circles slowly around it, gliding
 * back into them as it is finished, so they see the Hex burst out of them first-hand. Sneaking leaves the circling
 * early; it can be turned off altogether.
 */
public final class Founding {

    /** How high the caster floats over their home's floor, and the floor over the land it was cast on. */
    private static final double HOVER = 1.0;
    private static final double FLOOR = 1.0;
    /** How hard a caster come down in their home is held down onto its floor, a tick's fall. */
    private static final double WALK_PRESS = 0.08;
    /** How near the view starts out, on the caster themselves, before it draws back to take in the whole house. */
    private static final float CLOSE_RADIUS = 5.5F;
    private static final float CLOSE_HEIGHT = 2.0F;
    private static final float DRAW_BACK_FROM = 40.0F;
    private static final float DRAW_BACK_TICKS = 120.0F;
    private static final float RISE_TICKS = 34.0F;
    private static final float ORBIT_RADIUS = 23.0F;
    private static final float ORBIT_HEIGHT = 10.0F;
    /** How high over the land the circling view looks, at the middle of the rising house. */
    private static final float ORBIT_LOOK = 6.0F;
    private static final float ORBIT_TICKS = 380.0F;
    private static final float GLIDE_IN = 36.0F;
    private static final float GLIDE_OUT = 20.0F;
    private static final float FLASH_TICKS = 18.0F;
    private static final float SHAKE_TICKS = 28.0F;
    private static final float SHOCK_TICKS = 12.0F;
    private static final Vector3f FLAT_U = new Vector3f(1, 0, 0);
    private static final Vector3f FLAT_V = new Vector3f(0, 0, 1);
    /** Farthest from a founding caster's home that a block of it is still theirs to reach for. */
    private static final double HOME_REACH = 24.0;
    private static final int MAX_TENDRILS = 10;
    /** Fewest ticks a tendril takes to reach its block, however soon it lands. */
    private static final float MIN_REACH_TICKS = 3.0F;
    /** Ticks a tendril takes to let go once its block is down, its tail running out after it. */
    private static final float LET_GO_TICKS = 7.0F;
    private static final int TENDRIL_POINTS = 20;

    /** The burst each Hex last made, by its caster, so each bursts once. */
    private static final Map<UUID, Long> BURSTS = new HashMap<>();
    /** When each founding caster's home stands finished. */
    private static final Map<UUID, Double> HOMES = new HashMap<>();
    /** Magic reaching out of founding casters for the blocks of their homes. */
    private static final List<Tendril> TENDRILS = new ArrayList<>();
    /** Where the magic out of the hands of casters raising their homes far off lands, by caster. */
    private static final Map<UUID, Raising> RAISINGS = new HashMap<>();
    /** The last block of a home already reached for, in the count of all told of. */
    private static int formsSeen;
    private static int sources;

    private static boolean filming;
    private static double filmFrom;
    private static double filmBackAt = -1.0;
    private static float filmYaw;
    private static Vec3 filmCenter = Vec3.ZERO;
    private static @Nullable CameraType cameraBefore;
    /** Whether the filming hid the HUD, to show it again after. */
    private static boolean hidGui;
    /** The founding the caster chose to watch from their own eyes, by when it began. */
    private static long skipped = Long.MIN_VALUE;
    private static boolean hinted;
    /** The founding the local caster is in, by when it began, and which way is out of their home's front door. */
    private static long foundedAt = Long.MIN_VALUE;
    private static float outYaw;
    private static double blastAt = -1.0E9;
    private static Vec3 blastFrom = Vec3.ZERO;

    private Founding() {
    }

    /**
     * The Hex a player is founding right now, if they are.
     */
    static @Nullable HexSnapshot founding(Player player) {
        for (HexSnapshot hex : Hexes.clientHexes()) {
            if (hex.caster().equals(player.getUUID()) && hex.phaseValue() == Hex.Phase.FOUNDING) {
                return hex;
            }
        }
        return null;
    }

    /**
     * When a Hex's home will stand finished, as far as this client has heard; until then it is still rising.
     */
    private static double homeDone(HexSnapshot hex) {
        TownBuildPayload home = TownFx.home(hex.center());
        if (home != null && home.start() >= hex.phaseSince()) {
            // remembered, as word of the build is let go of soon after it is done
            HOMES.put(hex.caster(), (double) (home.start() + home.duration()));
        }
        Double done = HOMES.get(hex.caster());
        return done != null && hex.phaseValue() == Hex.Phase.FOUNDING ? done : Double.MAX_VALUE;
    }

    private static float rise(HexSnapshot hex, double now) {
        return Ease.inOutCubic(Ease.clamp01((float) (now - hex.phaseSince()) / RISE_TICKS));
    }

    private static float land(HexSnapshot hex, double now) {
        return Ease.inOutCubic(Ease.clamp01((float) ((now - homeDone(hex)) / Hexes.LAND_TICKS)));
    }

    /**
     * How far a caster is into floating over their home: rising to 1, holding there while it builds, and back to 0 as
     * they come down on its floor.
     */
    public static float lift(Player player, double now) {
        HexSnapshot hex = founding(player);
        return hex == null ? 0.0F : rise(hex, now) * (1.0F - land(hex, now));
    }

    // ---------------------------------------------------------------- the tick

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            stopFilming(minecraft);
            BURSTS.clear();
            HOMES.clear();
            TENDRILS.clear();
            RAISINGS.clear();
            return;
        }
        double now = level.getGameTime();
        if (!minecraft.isPaused()) {
            List<TownFx.Form> fresh = TownFx.formsAfter(formsSeen);
            formsSeen = TownFx.formsTold();
            TENDRILS.removeIf(tendril -> now > tendril.lands() + LET_GO_TICKS || level.getPlayerByUUID(tendril.caster()) == null);
            RAISINGS.entrySet().removeIf(entry -> {
                Player caster = level.getPlayerByUUID(entry.getKey());
                return caster == null || raising(caster, now) == null && PoseBlends.of(caster).raise < 0.01F;
            });
            for (Player caster : level.players()) {
                HexSnapshot hex = founding(caster);
                if (hex != null) {
                    pour(level, caster, hex, now, fresh);
                    continue;
                }
                TownBuildPayload raising = raising(caster, now);
                if (raising != null) {
                    aim(caster, raising, now, fresh);
                }
            }
            for (HexSnapshot hex : Hexes.clientHexes()) {
                if (hex.phaseValue() == Hex.Phase.SPREADING && now - hex.phaseSince() < 3.0
                        && !Long.valueOf(hex.phaseSince()).equals(BURSTS.get(hex.caster()))) {
                    BURSTS.put(hex.caster(), hex.phaseSince());
                    burst(minecraft, level, hex, now);
                }
            }
        }
        HexSnapshot own = founding(player);
        if (own == null) {
            stopFilming(minecraft);
            return;
        }
        if (foundedAt != own.phaseSince()) {
            // their home's front faces back the way they were looking as they cast
            foundedAt = own.phaseSince();
            outYaw = player.getYRot() + 180.0F;
        }
        TownBuildPayload home = TownFx.home(own.center());
        if (home != null && home.start() >= own.phaseSince()) {
            // or wherever it faced when it first stood, if it is rising again
            outYaw = Direction.from2DDataValue(home.front()).toYRot();
        }
        hover(player, own, now);
        film(minecraft, player, own, now);
    }

    /**
     * Floats the caster: up off the land, held over the middle of the rising house with a slow bob, then down onto its
     * floor once it stands, turning to look out of its front, where the Hex bursts out of them as they stand.
     */
    private static void hover(LocalPlayer player, HexSnapshot hex, double now) {
        float rise = rise(hex, now);
        float land = land(hex, now);
        double bob = Mth.sin((float) now * 0.09F) * 0.07 * rise * (1.0F - land);
        Vec3 center = hex.center();
        // coming down onto the middle of the block they float over
        Vec3 middle = new Vec3(Mth.floor(center.x) + 0.5, center.y, Mth.floor(center.z) + 0.5);
        Vec3 over = center.lerp(middle, land);
        Vec3 goal = new Vec3(over.x, center.y + rise * (FLOOR + HOVER) - land * HOVER + bob, over.z);
        // carried across to where their home stood before, cast a little way off it, no faster than flying
        Vec3 step = goal.subtract(player.position()).scale(0.35);
        double across = step.horizontalDistance();
        if (across > Hexes.DRIFT_SPEED) {
            step = new Vec3(step.x * Hexes.DRIFT_SPEED / across, step.y, step.z * Hexes.DRIFT_SPEED / across);
        }
        player.setDeltaMovement(Vec3.ZERO);
        if (land > 0.0F) {
            // down on their floor they walk rather than glide, as the server puts back any move into a block, even a
            // rug; pressed down onto it once there, so they step up over a rug's edge rather than stop at it
            player.move(MoverType.SELF, land < 1.0F ? step : new Vec3(step.x, Math.min(step.y, -WALK_PRESS), step.z));
        } else {
            Vec3 next = player.position().add(step);
            player.setPos(next.x, next.y, next.z);
        }
        player.fallDistance = 0.0F;
        if (land > 0.0F) {
            player.setYRot(player.getYRot() + Mth.wrapDegrees(outYaw - player.getYRot()) * 0.2F);
            player.setXRot(Mth.lerp(0.2F, player.getXRot(), 2.0F));
        }
    }

    /**
     * The home a caster is raising again somewhere new inside their standing Hex, while it goes up and they are near
     * enough to reach it.
     */
    public static @Nullable TownBuildPayload raising(Player caster, double now) {
        for (HexSnapshot hex : Hexes.clientHexes()) {
            if (hex.caster().equals(caster.getUUID()) && hex.phaseValue() == Hex.Phase.STANDING) {
                TownBuildPayload home = TownFx.homeRising(hex, now);
                return home != null && TownFx.middle(home).distanceTo(caster.position()) <= Hexes.HOME_PLACE_REACH + 16.0 ? home : null;
            }
        }
        return null;
    }

    /**
     * Turns the beams out of a caster's raised hands onto the blocks of the home they are raising far off, each hand
     * jumping to the next block as it is told of, by turns.
     */
    private static void aim(Player caster, TownBuildPayload rising, double now, List<TownFx.Form> fresh) {
        Raising raising = RAISINGS.computeIfAbsent(caster.getUUID(), id -> new Raising());
        Vec3 home = TownFx.middle(rising);
        int taken = 0;
        for (TownFx.Form form : fresh) {
            if (taken >= 2) {
                break;
            }
            Vec3 at = Vec3.atCenterOf(form.pos());
            if (at.distanceToSqr(home.x, at.y, home.z) > HOME_REACH * HOME_REACH) {
                continue;
            }
            int hand = raising.next++ % 2;
            raising.was[hand] = raising.landing(hand, now, home);
            raising.at[hand] = at;
            raising.since[hand] = now;
            taken++;
        }
    }

    /**
     * Where the beams out of a caster's raised hands land, right hand first: on the block each last jumped to, and where
     * it jumped from, for it to sweep across rather than snap.
     */
    private static final class Raising {
        private static final double SWEEP_TICKS = 2.0;
        final @Nullable Vec3[] at = new Vec3[2];
        final @Nullable Vec3[] was = new Vec3[2];
        final double[] since = new double[2];
        int next;

        Vec3 landing(int hand, double now, Vec3 home) {
            Vec3 to = at[hand];
            if (to == null) {
                // nothing reached for yet: the middle of the lot, a little over the ground
                return home.add(0.0, 1.5 + hand * 0.5, 0.0);
            }
            Vec3 from = was[hand];
            float k = (float) Math.clamp((now - since[hand]) / SWEEP_TICKS, 0.0, 1.0);
            return from == null ? to : from.lerp(to, Ease.outCubic(k));
        }
    }

    /**
     * Magic pouring out of a caster into the house rising around them: a tendril reaching out for each block of it as
     * it is about to land, by turns from either palm and now and then from their heart, and the low hum of it.
     */
    private static void pour(ClientLevel level, Player caster, HexSnapshot hex, double now, List<TownFx.Form> fresh) {
        float lift = lift(caster, now);
        if (lift <= 0.01F) {
            return;
        }
        RandomSource random = ScarletFx.random();
        Vec3 home = hex.center();
        // the blocks only just told of, latest first; a whole course of a wall can land at once, and one tendril a tick
        // is plenty to keep the magic flowing without tangling into a knot
        int reaching = 0;
        for (TownFx.Form form : fresh) {
            if (reaching >= 1 || TENDRILS.size() >= MAX_TENDRILS) {
                break;
            }
            BlockPos pos = form.pos();
            double dx = pos.getX() + 0.5 - home.x;
            double dz = pos.getZ() + 0.5 - home.z;
            if (dx * dx + dz * dz > HOME_REACH * HOME_REACH || random.nextFloat() > 0.65F) {
                continue;
            }
            reaching++;
            int source = sources++ % 5 == 4 ? CHEST : sources % 2;
            TENDRILS.add(new Tendril(caster.getUUID(), source, pos, now, Math.max(form.lands(), now + MIN_REACH_TICKS), random.nextInt(1000)));
        }
        if (random.nextFloat() < 0.07F * lift) {
            Vec3 chest = caster.position().add(0.0, caster.getBbHeight() * 0.7, 0.0);
            level.playLocalSound(chest.x, chest.y, chest.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6F, 0.55F + random.nextFloat() * 0.3F,
                    false);
        }
    }

    private static final int CHEST = 2;

    /**
     * A beam out of a raised hand, relative to the camera.
     */
    private record Beam(Vector3f from, Vector3f to, float presence, float seed) {
    }

    /**
     * Magic reaching out of a caster for a block of their home.
     *
     * @param source which hand it leaves, by {@link HumanoidArm} order, or {@link #CHEST} for their heart
     * @param start  when it sets out
     * @param lands  when its block lands, by when it has reached it
     */
    private record Tendril(UUID caster, int source, BlockPos target, double start, double lands, int seed) {
    }

    /**
     * The Hex bursting out of its caster: a ring of sparks flung out over the land, and for everyone close enough to
     * be caught in it, a flash and a shudder.
     */
    private static void burst(Minecraft minecraft, ClientLevel level, HexSnapshot hex, double now) {
        Player caster = level.getPlayerByUUID(hex.caster());
        Vec3 from = caster != null ? caster.position().add(0.0, 1.1, 0.0) : hex.center().add(0.0, 1.1, 0.0);
        RandomSource random = ScarletFx.random();
        for (int i = 0, n = Math.round(110 * ScarletFx.density()); i < n; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 0.5 + random.nextDouble() * 0.9;
            Vec3 velocity = new Vec3(Math.cos(angle) * speed, 0.05 + random.nextDouble() * 0.25, Math.sin(angle) * speed);
            int color = random.nextFloat() < 0.35F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET;
            ScarletFx.spark(from, velocity, 18 + random.nextInt(22), 0.06F + random.nextFloat() * 0.05F, color, ScarletPalette.SCARLET, -0.004F, 0.93F);
        }
        LocalPlayer player = minecraft.player;
        if (player != null && HexShape.level(hex.center(), 1.0F, player.position()) < hex.radius() + 16.0) {
            blastAt = now;
            blastFrom = from;
        }
    }

    // ---------------------------------------------------------------- the view

    private static void film(Minecraft minecraft, LocalPlayer player, HexSnapshot hex, double now) {
        boolean wanted = ScarletClientConfig.get().cinematicFounding && skipped != hex.phaseSince();
        if (!filming) {
            if (!wanted || now - hex.phaseSince() > RISE_TICKS || now >= homeDone(hex)) {
                return;
            }
            filming = true;
            filmFrom = now;
            filmBackAt = -1.0;
            filmYaw = player.getYRot() + 180.0F;
            filmCenter = hex.center();
            cameraBefore = minecraft.options.getCameraType();
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            // the screen to itself, as in the show
            hidGui = !minecraft.gui.hud.isHidden();
            if (hidGui) {
                minecraft.gui.hud.toggle();
            }
            if (!hinted) {
                hinted = true;
                player.sendSystemMessage(Component.translatable("hex.scarlet.cinematic.skip", minecraft.options.keyShift.getTranslatedKeyMessage()));
            }
        }
        if (filmBackAt < 0.0 && (minecraft.options.keyShift.isDown() || !wanted)) {
            skipped = hex.phaseSince();
            filmBackAt = now;
        }
        // the house stands: back to the caster's eyes, in time to see the Hex burst out of them
        if (filmBackAt < 0.0 && now >= homeDone(hex)) {
            filmBackAt = now;
        }
        if (filmBackAt >= 0.0 && now - filmBackAt >= GLIDE_OUT) {
            stopFilming(minecraft);
        }
    }

    private static void stopFilming(Minecraft minecraft) {
        if (!filming) {
            return;
        }
        filming = false;
        if (cameraBefore != null) {
            minecraft.options.setCameraType(cameraBefore);
        }
        if (hidGui && minecraft.gui.hud.isHidden()) {
            minecraft.gui.hud.toggle();
        }
        hidGui = false;
        cameraBefore = null;
    }

    /**
     * Where the view is while the home rises: circling slowly around the house, looking in at it, after gliding out of
     * the caster's eyes, and gliding back into them at the end.
     */
    public static @Nullable Shot shot(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!filming || player == null || minecraft.level == null) {
            return null;
        }
        double now = minecraft.level.getGameTime() + partialTick;
        double angle = Math.toRadians(filmYaw) + (now - filmFrom) / ORBIT_TICKS * Math.PI * 2.0;
        // close on the caster at first, floating with the magic pouring out of them, then drawing back to the house
        float back = Ease.inOutCubic(Ease.clamp01((float) ((now - filmFrom - DRAW_BACK_FROM) / DRAW_BACK_TICKS)));
        float radius = Mth.lerp(back, CLOSE_RADIUS, ORBIT_RADIUS);
        float height = Mth.lerp(back, CLOSE_HEIGHT, ORBIT_HEIGHT);
        Vec3 look = filmCenter.add(0.0, Mth.lerp(back, (float) (FLOOR + HOVER + 1.2), ORBIT_LOOK), 0.0);
        Vec3 orbit = new Vec3(filmCenter.x - Math.sin(angle) * radius, filmCenter.y + height, filmCenter.z + Math.cos(angle) * radius);
        Vec3 toLook = look.subtract(orbit);
        float orbitYaw = (float) Math.toDegrees(Math.atan2(-toLook.x, toLook.z));
        float orbitPitch = (float) Math.toDegrees(-Math.atan2(toLook.y, Math.hypot(toLook.x, toLook.z)));
        float away = Ease.inOutCubic(Ease.clamp01((float) ((now - filmFrom) / GLIDE_IN)));
        if (filmBackAt >= 0.0) {
            away *= 1.0F - Ease.inOutCubic(Ease.clamp01((float) ((now - filmBackAt) / GLIDE_OUT)));
        }
        Vec3 eye = player.getEyePosition(partialTick);
        float eyeYaw = player.getViewYRot(partialTick);
        float eyePitch = player.getViewXRot(partialTick);
        return new Shot(eye.lerp(orbit, away), eyeYaw + Mth.wrapDegrees(orbitYaw - eyeYaw) * away, Mth.lerp(away, eyePitch, orbitPitch));
    }

    /**
     * How far the view shudders as a Hex bursts out around you, in degrees of yaw and pitch.
     */
    public static float[] shake(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return new float[] {0.0F, 0.0F};
        }
        float t = (float) (minecraft.level.getGameTime() + partialTick - blastAt) / SHAKE_TICKS;
        if (t < 0.0F || t >= 1.0F) {
            return new float[] {0.0F, 0.0F};
        }
        float strength = (1.0F - t) * (1.0F - t) * (ScarletClientConfig.get().reduceCameraShake ? 0.25F : 1.6F);
        float time = (float) (minecraft.level.getGameTime() + partialTick);
        return new float[] {Mth.sin(time * 2.9F) * strength, Mth.sin(time * 3.7F + 1.3F) * strength * 0.7F};
    }

    /**
     * @param position where the view is
     */
    public record Shot(Vec3 position, float yaw, float pitch) {
    }

    // ---------------------------------------------------------------- drawing

    /**
     * The glow at a founding caster's chest, and the blast of a Hex bursting out over the land: a shock of light on the
     * ground around its caster, and a burning scarlet line racing out with its wall.
     */
    public static void submit(SubmitNodeCollector collector, PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        double now = level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        // beams out of the raised hands of casters raising their homes far off
        List<Beam> beams = new ArrayList<>();
        for (Map.Entry<UUID, Raising> entry : RAISINGS.entrySet()) {
            Player caster = level.getPlayerByUUID(entry.getKey());
            TownBuildPayload rising = caster == null ? null : raising(caster, now);
            float presence = caster == null ? 0.0F : PoseBlends.of(caster).raise;
            if (caster == null || caster.isInvisible() || presence < 0.02F) {
                continue;
            }
            Raising raising = entry.getValue();
            Vec3 home = rising != null ? TownFx.middle(rising) : raising.at[0] != null ? raising.at[0] : caster.position();
            for (HumanoidArm arm : HumanoidArm.values()) {
                int hand = arm == HumanoidArm.RIGHT ? 0 : 1;
                Vec3 to = rising != null ? raising.landing(hand, now, home) : raising.at[hand] != null ? raising.at[hand] : home;
                beams.add(new Beam(Hands.raisedPalm(caster, arm, partialTick).subtract(camera).toVector3f(), to.subtract(camera).toVector3f(),
                        presence, caster.getId() * 0.618F + hand * 0.37F));
            }
        }
        boolean any = !TENDRILS.isEmpty() || !beams.isEmpty();
        for (HexSnapshot hex : Hexes.clientHexes()) {
            Hex.Phase phase = hex.phaseValue();
            if (phase == Hex.Phase.FOUNDING || phase == Hex.Phase.SPREADING && now - hex.phaseSince() < Hexes.SPREAD_TICKS) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }
        if (!beams.isEmpty()) {
            GlowPass.submitPixels(collector, poseStack, (pose, buffer) -> {
                for (Beam beam : beams) {
                    MagicBeam.draw(buffer, pose, beam.from(), beam.to(), now, beam.seed(), beam.presence());
                }
            });
        }
        List<Tendril> tendrils = List.copyOf(TENDRILS);
        GlowPass.submit(collector, poseStack, (pose, buffer) -> {
            Glow.Billboard axes = Glow.billboard(pose);
            for (Tendril tendril : tendrils) {
                Player caster = level.getPlayerByUUID(tendril.caster());
                if (caster != null) {
                    tendril(buffer, pose, axes, camera, caster, tendril, now, partialTick);
                }
            }
            for (HexSnapshot hex : Hexes.clientHexes()) {
                Player caster = level.getPlayerByUUID(hex.caster());
                if (hex.phaseValue() == Hex.Phase.FOUNDING && caster != null) {
                    float lift = lift(caster, now);
                    if (lift > 0.01F) {
                        Vec3 at = caster.getPosition(partialTick);
                        Vec3 chest = at.add(0.0, caster.getBbHeight() * 0.7, 0.0).subtract(camera);
                        float pulse = 0.8F + 0.2F * Mth.sin((float) now * 0.3F);
                        // her heart, where it all pours out of, glowing softly
                        Glow.disc(buffer, pose, axes, (float) chest.x, (float) chest.y, (float) chest.z, 0.8F * pulse * lift,
                                Glow.withAlpha(ScarletPalette.SCARLET, 0.16F * lift));
                        Glow.disc(buffer, pose, axes, (float) chest.x, (float) chest.y, (float) chest.z, 0.16F * pulse * lift,
                                Glow.withAlpha(ScarletPalette.CORE, 0.75F * lift));
                        aura(buffer, pose, axes, at.subtract(camera), caster.getBbHeight(), lift, now, caster.getId());
                    }
                }
                float since = (float) (now - hex.phaseSince());
                if (hex.phaseValue() != Hex.Phase.SPREADING || since >= Hexes.SPREAD_TICKS) {
                    continue;
                }
                Vec3 center = hex.center();
                float y = (float) (center.y + 0.08 - camera.y);
                float cx = (float) (center.x - camera.x);
                float cz = (float) (center.z - camera.z);
                // the shock of it, a flash of light on the ground around the caster
                if (since < SHOCK_TICKS) {
                    float k = since / SHOCK_TICKS;
                    Glow.planeDisc(buffer, pose, cx, y, cz, FLAT_U, FLAT_V, 3.0F + 16.0F * Ease.outCubic(k),
                            Glow.withAlpha(0xFFF4F4, 0.55F * (1.0F - k)), Glow.withAlpha(ScarletPalette.SCARLET, 0.0F), 40);
                }
                // and the line of it racing out with the wall, burning
                float wall = HexClient.drawnRadius(hex, now);
                float fade = 1.0F - Ease.inCubic(since / Hexes.SPREAD_TICKS);
                float corner = wall * HexShape.CORNER;
                for (int side = 0; side < 6; side++) {
                    double a0 = Math.toRadians(60.0 * side);
                    double a1 = Math.toRadians(60.0 * (side + 1));
                    float ax = (float) Math.cos(a0) * corner;
                    float az = (float) Math.sin(a0) * corner;
                    float bx = (float) Math.cos(a1) * corner;
                    float bz = (float) Math.sin(a1) * corner;
                    Glow.planeLine(buffer, pose, cx, y, cz, FLAT_U, FLAT_V, ax, az, bx, bz, 2.6F, Glow.withAlpha(ScarletPalette.SCARLET, 0.55F * fade));
                    Glow.planeLine(buffer, pose, cx, y + 0.01F, cz, FLAT_U, FLAT_V, ax, az, bx, bz, 0.7F, Glow.withAlpha(ScarletPalette.CORE, 0.8F * fade));
                }
            }
        });
    }

    /**
     * A tendril of magic reaching out of a caster for a block of their home: out of their palm and away from them,
     * arcing up and over, and down onto where the block lands, writhing all the way, with wisps twisting around it and
     * light running down it. Its tip reaches the block as it lands; then its tail lets go of the caster and runs out
     * after it.
     */
    private static void tendril(VertexConsumer buffer, PoseStack.Pose pose, Glow.Billboard axes, Vec3 camera, Player caster, Tendril tendril,
                                double now, float partialTick) {
        float head = Ease.outCubic((float) ((now - tendril.start()) / Math.max(1.0, tendril.lands() - tendril.start())));
        float tail = Ease.inOutCubic((float) ((now - tendril.lands()) / LET_GO_TICKS));
        if (head <= 0.01F || tail >= 0.99F) {
            return;
        }
        Vec3 chest = caster.getPosition(partialTick).add(0.0, caster.getBbHeight() * 0.7, 0.0);
        Vec3 from;
        Vec3 outward;
        if (tendril.source() == CHEST) {
            from = chest;
            outward = new Vec3(0.0, 1.0, 0.0);
        } else {
            from = Hands.palm(caster, HumanoidArm.values()[tendril.source()]);
            Vec3 away = from.subtract(chest);
            Vec3 flat = new Vec3(away.x, 0.0, away.z);
            outward = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : flat.normalize().add(0.0, 0.6, 0.0).normalize();
        }
        Vec3 to = Vec3.atCenterOf(tendril.target());
        Vec3 span = to.subtract(from);
        double length = Math.max(span.length(), 1.0E-3);
        Vec3 out = from.add(outward.scale(1.2 + length * 0.18));
        Vec3 over = to.add(0.0, 1.0 + length * 0.22, 0.0);
        Vec3 direction = span.scale(1.0 / length);
        Vec3 side = direction.cross(new Vec3(0.0, 1.0, 0.0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
        Vec3 up = side.cross(direction).normalize();
        float time = (float) now;
        float seed = tendril.seed();
        int n = TENDRIL_POINTS;
        Vector3f[] points = new Vector3f[n];
        float[] along = new float[n];
        float[] taper = new float[n];
        for (int i = 0; i < n; i++) {
            float k = i / (float) (n - 1);
            float s = Mth.lerp(k, tail, head);
            // writhing, most in the middle of its reach and still at both ends
            float bend = Mth.sin((float) Math.PI * s);
            float sway = (Mth.sin(s * 9.0F + time * 0.33F + seed) * 0.32F + Mth.sin(s * 23.0F - time * 0.61F + seed * 1.7F) * 0.09F) * bend;
            float heave = Mth.cos(s * 7.0F - time * 0.27F + seed * 0.6F) * 0.22F * bend;
            Vec3 p = bezier(from, out, over, to, s).add(side.scale(sway)).add(up.scale(heave));
            points[i] = new Vector3f((float) (p.x - camera.x), (float) (p.y - camera.y), (float) (p.z - camera.z));
            along[i] = s;
            // drawn out to a point at its tip, and at its tail once it has let go
            taper[i] = Ease.clamp01((1.0F - k) * 4.0F) * Ease.clamp01(k * 5.0F + (tail <= 0.0F ? 0.35F : 0.0F));
        }
        float fade = (1.0F - tail) * Ease.clamp01(head * 3.0F);
        float[] widths = new float[n];
        int[] colors = new int[n];
        // the haze of it
        for (int i = 0; i < n; i++) {
            widths[i] = 0.5F * taper[i];
            colors[i] = Glow.withAlpha(ScarletPalette.SCARLET, 0.18F * fade * taper[i]);
        }
        Glow.ribbon(buffer, pose, axes, points, widths, colors);
        // its body
        for (int i = 0; i < n; i++) {
            widths[i] = 0.15F * taper[i];
            colors[i] = Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.65F * fade * taper[i]);
        }
        Glow.ribbon(buffer, pose, axes, points, widths, colors);
        // the light running down it, out of the caster toward the block
        for (int i = 0; i < n; i++) {
            float run = (float) Math.pow(0.5 + 0.5 * Mth.sin(along[i] * 16.0F - time * 1.1F + seed), 4);
            widths[i] = (0.035F + 0.035F * run) * taper[i];
            colors[i] = Glow.withAlpha(Glow.mix(ScarletPalette.BRIGHT_SCARLET, ScarletPalette.CORE, 0.4F + 0.6F * run), (0.55F + 0.45F * run) * fade * taper[i]);
        }
        Glow.ribbon(buffer, pose, axes, points, widths, colors);
        // and wisps twisting around it
        Vector3f[] wisp = new Vector3f[n];
        for (int w = 0; w < 2; w++) {
            for (int i = 0; i < n; i++) {
                float s = along[i];
                float angle = s * 15.0F + time * 0.45F + w * (float) Math.PI + seed;
                float radius = 0.03F + 0.16F * Mth.sin((float) Math.PI * s);
                float c = Mth.cos(angle) * radius;
                float d = Mth.sin(angle) * radius;
                wisp[i] = new Vector3f(points[i]).add((float) (side.x * c + up.x * d), (float) (side.y * c + up.y * d), (float) (side.z * c + up.z * d));
                widths[i] = 0.028F * taper[i];
                colors[i] = Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.45F * fade * taper[i]);
            }
            Glow.ribbon(buffer, pose, axes, wisp, widths, colors);
        }
    }

    private static Vec3 bezier(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float s) {
        float u = 1.0F - s;
        return a.scale(u * u * u).add(b.scale(3.0F * u * u * s)).add(c.scale(3.0F * u * s * s)).add(d.scale(s * s * s));
    }

    /**
     * Wisps of scarlet curling slowly up around a founding caster, from their feet to over their head, light running up
     * them.
     */
    private static void aura(VertexConsumer buffer, PoseStack.Pose pose, Glow.Billboard axes, Vec3 feet, float height, float lift, double now, int seed) {
        float time = (float) now;
        int n = 24;
        Vector3f[] points = new Vector3f[n];
        float[] widths = new float[n];
        int[] colors = new int[n];
        for (int w = 0; w < 2; w++) {
            for (int i = 0; i < n; i++) {
                float s = i / (float) (n - 1);
                float angle = s * 4.2F + time * 0.07F + w * (float) Math.PI + seed;
                float radius = 0.55F + 0.3F * Mth.sin(s * 3.0F + time * 0.05F + w);
                points[i] = new Vector3f((float) feet.x + Mth.cos(angle) * radius, (float) feet.y - 0.2F + s * (height + 0.9F),
                        (float) feet.z + Mth.sin(angle) * radius);
                float body = Mth.sin((float) Math.PI * s);
                float run = (float) Math.pow(0.5 + 0.5 * Mth.sin(s * 10.0F - time * 0.4F + w * 2.0F), 3);
                widths[i] = (0.05F + 0.05F * run) * body;
                colors[i] = Glow.withAlpha(Glow.mix(ScarletPalette.SCARLET, ScarletPalette.BRIGHT_SCARLET, run), (0.22F + 0.33F * run) * body * lift);
            }
            Glow.ribbon(buffer, pose, axes, points, widths, colors);
        }
    }

    /**
     * The flash of a Hex bursting out around you, white-hot and then scarlet, fading.
     */
    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float t = (float) (minecraft.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false) - blastAt) / FLASH_TICKS;
        if (t < 0.0F || t >= 1.0F) {
            return;
        }
        float strength = ScarletClientConfig.get().reduceFlashing ? 0.25F : 0.75F;
        float fade = (1.0F - t) * (1.0F - t) * strength;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float white = Ease.clamp01(1.0F - t * 4.0F);
        graphics.fill(0, 0, width, height, ARGB.color(fade * (1.0F - white), ScarletPalette.SCARLET));
        graphics.fill(0, 0, width, height, ARGB.color(fade * white, 0xFFF0F0));
    }
}
