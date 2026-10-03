package com.yashjit.scarlet.client.hex;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.fx.TownFx;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.hex.Hex;
import com.yashjit.scarlet.hex.HexSnapshot;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.hex.town.Terrain;
import com.yashjit.scarlet.hex.town.TownPlan;
import com.yashjit.scarlet.network.HomePayload;
import com.yashjit.scarlet.platform.Services;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Raising your home again somewhere new inside your Hex, from the Showrunner's remote. A scarlet outline of its lot
 * lies on the land wherever you look, its front toward you, with the shape of the house it will be standing faintly
 * over it. Scrolling turns it, using raises it there, and attacking thinks better of it. Where it can't stand, the
 * outline shows dim and broken, and says why.
 */
public final class HomePlacement {

    /** How tall the shape of the house over the lot stands. */
    private static final float HOUSE_HEIGHT = 14.0F;
    /** Ticks the outline takes to trace itself around the lot, and the house to rise over it, when placing begins. */
    private static final float TRACE_TICKS = 8.0F;
    private static final float RISE_TICKS = 12.0F;
    /** How far the land under a lot may stand over or under its middle height, as the town levels it. */
    private static final int MAX_CUT = 8;
    private static final int MAX_FILL = 6;
    /** Water deeper than this is a lake, a river or the sea. */
    private static final int SHALLOW = 2;
    /** How far down through what stands on the land the ground is looked for. */
    private static final int SCAN_DEPTH = 48;
    private static final Vector3f FLAT_U = new Vector3f(1, 0, 0);
    private static final Vector3f FLAT_V = new Vector3f(0, 0, 1);

    private static boolean active;
    private static double begunAt;
    /** The way its front was turned by scrolling, or null to face the caster. */
    private static @Nullable Direction turned;
    /** The click that ended placing, still held, which goes to nothing else until it is let go. */
    private static boolean latched;
    private static @Nullable Aim aim;
    /** The land under the lot last looked at, kept until the lot moves. */
    private static TownPlan.@Nullable HomeLot surveyed;
    private static @Nullable Survey survey;

    private HomePlacement() {
    }

    public static boolean active() {
        return active;
    }

    /**
     * Starts placing, for a caster whose Hex stands.
     */
    public static void begin(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || own(player) == null) {
            return;
        }
        active = true;
        begunAt = minecraft.level.getGameTime();
        turned = null;
        aim = null;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 0.9F));
    }

    public static void stop() {
        active = false;
        aim = null;
    }

    public static void tick(Minecraft minecraft) {
        if (latched && !minecraft.options.keyUse.isDown() && !minecraft.options.keyAttack.isDown()) {
            latched = false;
        }
        if (!active) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !player.isAlive() || !CrownItem.isWearingCrown(player) || own(player) == null) {
            stop();
        }
    }

    /**
     * @return whether the scroll went to turning the house
     */
    public static boolean scroll(double amount) {
        if (!active || amount == 0.0) {
            return false;
        }
        Direction front = aim != null ? aim.lot().front() : turned != null ? turned : Direction.NORTH;
        turned = amount < 0.0 ? front.getClockWise() : front.getCounterClockWise();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.7F, 0.2F));
        return true;
    }

    /**
     * Raises the home where the outline lies, if it can stand there.
     *
     * @return whether the click went to placing, rather than to anything else
     */
    public static boolean use(Minecraft minecraft) {
        if (latched) {
            return true;
        }
        if (!active) {
            return false;
        }
        Aim at = aim;
        if (at == null || at.problem() != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_HIT, 0.5F, 0.6F));
            return true;
        }
        Services.NETWORK.sendToServer(HomePayload.of(at.lot()));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2F, 0.3F));
        stop();
        latched = true;
        return true;
    }

    /**
     * Puts placing away.
     *
     * @return whether the click went to placing, rather than to swinging at anything
     */
    public static boolean attack(Minecraft minecraft) {
        if (latched) {
            return true;
        }
        if (!active) {
            return false;
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.8F, 0.2F));
        stop();
        latched = true;
        return true;
    }

    /**
     * Whether a click held down is placing's, so nothing is broken or used by it.
     */
    public static boolean holdsClick() {
        return active || latched;
    }

    /**
     * What to do now, while placing: or why the home can't stand where the outline lies.
     */
    public static @Nullable Component hint(Minecraft minecraft) {
        if (!active) {
            return null;
        }
        Component cancel = minecraft.options.keyAttack.getTranslatedKeyMessage();
        Aim at = aim;
        if (at == null) {
            return Component.translatable("hud.scarlet.home.aim", cancel);
        }
        if (at.problem() != null) {
            return Component.translatable(at.problem().key, cancel);
        }
        return Component.translatable("hud.scarlet.home.place", minecraft.options.keyUse.getTranslatedKeyMessage(), cancel);
    }

    private static @Nullable HexSnapshot own(LocalPlayer player) {
        for (HexSnapshot hex : Hexes.clientHexes()) {
            if (hex.caster().equals(player.getUUID()) && hex.phaseValue() == Hex.Phase.STANDING) {
                return hex;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- where it goes

    /**
     * The lot where the caster looks, facing back at them unless they have turned it, and whether it can stand there.
     */
    private static @Nullable Aim aim(ClientLevel level, LocalPlayer player, HexSnapshot hex, float partialTick) {
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 end = eye.add(player.getViewVector(partialTick).scale(Hexes.HOME_PLACE_REACH));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos at = hit.getBlockPos();
        Direction front = turned;
        if (front == null) {
            double dx = player.getX() - (at.getX() + 0.5);
            double dz = player.getZ() - (at.getZ() + 0.5);
            front = Direction.fromYRot(Math.toDegrees(Math.atan2(-dx, dz)));
        }
        TownPlan.HomeLot lot = new TownPlan.HomeLot(at.getX(), at.getZ(), front);
        if (!lot.equals(surveyed) || survey == null) {
            surveyed = lot;
            survey = survey(level, lot);
        }
        Problem problem = null;
        if (!TownPlan.fits(lot, hex.center(), hex.radius())) {
            problem = Problem.OUTSIDE;
        } else if (survey.water()) {
            problem = Problem.WATER;
        } else if (survey.steep()) {
            problem = Problem.STEEP;
        }
        return new Aim(lot, survey.ground(), problem);
    }

    /**
     * The lie of the land under a lot, as the town will see it: the middle height of the ground under whatever stands
     * on it, and whether any of it is under deep water or too far over or under that to level.
     */
    private static Survey survey(ClientLevel level, TownPlan.HomeLot lot) {
        int[] grounds = new int[(lot.maxX() - lot.minX() + 1) * (lot.maxZ() - lot.minZ() + 1)];
        int count = 0;
        boolean water = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = lot.minX(); x <= lot.maxX(); x++) {
            for (int z = lot.minZ(); z <= lot.maxZ(); z++) {
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                int ground = top;
                int deep = 0;
                BlockState below = level.getBlockState(pos.set(x, top, z));
                for (int y = top; y > Math.max(level.getMinY(), top - SCAN_DEPTH); y--) {
                    BlockState state = below;
                    below = level.getBlockState(pos.set(x, y - 1, z));
                    if (state.isAir()) {
                        continue;
                    }
                    if (Terrain.isGround(state) && Terrain.isGround(below)) {
                        ground = y;
                        break;
                    }
                    if (Terrain.isWater(state)) {
                        deep++;
                    }
                }
                water |= deep > SHALLOW;
                grounds[count++] = ground;
            }
        }
        int[] sorted = Arrays.copyOf(grounds, count);
        Arrays.sort(sorted);
        int middle = sorted[count / 2];
        boolean steep = sorted[count - 1] - middle > MAX_CUT || middle - sorted[0] > MAX_FILL;
        return new Survey(middle, water, steep);
    }

    // ---------------------------------------------------------------- drawing

    /**
     * The outline where the home will go: its lot traced in scarlet on the land, an arrow out of its front where the
     * porch will be, and the shape of the house over it with a line of light climbing it. Dim, flickering and crossed
     * out where it can't stand.
     */
    public static void submit(SubmitNodeCollector collector, PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (!active || level == null || player == null) {
            return;
        }
        HexSnapshot hex = own(player);
        if (hex == null) {
            return;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + partialTick;
        Aim at = aim(level, player, hex, partialTick);
        aim = at;
        if (at == null) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        TownPlan.HomeLot lot = at.lot();
        boolean good = at.problem() == null;
        float traced = Ease.outCubic(Ease.clamp01((float) ((now - begunAt) / TRACE_TICKS)));
        float risen = Ease.outCubic(Ease.clamp01((float) ((now - begunAt - TRACE_TICKS * 0.5) / RISE_TICKS)));
        float flicker = good ? 1.0F : 0.5F + 0.5F * Math.abs(Mth.sin((float) now * 1.7F) * Mth.sin((float) now * 0.61F + 1.0F));
        int halo = good ? ScarletPalette.BRIGHT_SCARLET : ScarletPalette.CRIMSON;
        int core = good ? ScarletPalette.CORE : ScarletPalette.SCARLET;
        float x0 = (float) (lot.minX() - camera.x);
        float x1 = (float) (lot.maxX() + 1 - camera.x);
        float z0 = (float) (lot.minZ() - camera.z);
        float z1 = (float) (lot.maxZ() + 1 - camera.z);
        float y = (float) (at.ground() + 1.04 - camera.y);
        float top = y + HOUSE_HEIGHT * risen;
        float cx = (x0 + x1) / 2.0F;
        float cz = (z0 + z1) / 2.0F;
        Direction front = lot.front();
        Direction side = front.getClockWise();
        float reach = (front.getAxis() == Direction.Axis.Z ? z1 - z0 : x1 - x0) / 2.0F;
        float[][] corners = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
        float fx = cx + front.getStepX() * reach;
        float fz = cz + front.getStepZ() * reach;
        float tipX = fx + front.getStepX() * 1.8F;
        float tipZ = fz + front.getStepZ() * 1.8F;
        float[][] wings = new float[2][];
        for (int wing = 0; wing < 2; wing++) {
            float sign = wing == 0 ? -1.0F : 1.0F;
            wings[wing] = new float[] {fx + front.getStepX() * 0.5F + side.getStepX() * 1.3F * sign, fz + front.getStepZ() * 0.5F + side.getStepZ() * 1.3F * sign};
        }
        // scarlet glass under the light, so it reads as red over bright sand or snow as much as over grass at dusk
        GlowPass.submitTint(collector, poseStack, (pose, buffer) -> {
            int glass = GlowPass.tint(good ? ScarletPalette.GLASS : ScarletPalette.CRIMSON, (good ? 0.7F : 0.45F) * traced * flicker);
            for (int i = 0; i < 4; i++) {
                float[] a = corners[i];
                float[] b = corners[(i + 1) % 4];
                Glow.tintPlaneLine(buffer, pose, 0, y, 0, FLAT_U, FLAT_V, a[0], a[1], b[0], b[1], 0.45F, glass);
            }
            for (float[] wing : wings) {
                Glow.tintPlaneLine(buffer, pose, 0, y, 0, FLAT_U, FLAT_V, wing[0], wing[1], tipX, tipZ, 0.4F, glass);
            }
            if (risen <= 0.01F) {
                return;
            }
            Glow.Billboard axes = Glow.billboard(pose);
            for (float[] corner : corners) {
                Vector3f[] points = {new Vector3f(corner[0], y, corner[1]), new Vector3f(corner[0], top, corner[1])};
                Glow.tintRibbon(buffer, pose, axes, points, new float[] {0.32F, 0.2F},
                        new int[] {GlowPass.tint(ScarletPalette.GLASS, 0.55F * flicker), GlowPass.tint(ScarletPalette.GLASS, 0.1F * flicker)});
            }
        });
        GlowPass.submit(collector, poseStack, (pose, buffer) -> {
            // the lot, traced around on the land
            TownFx.outline(buffer, pose, x0, z0, x1, z1, y, traced, 0.18F, Glow.withAlpha(halo, 0.8F * flicker));
            TownFx.outline(buffer, pose, x0, z0, x1, z1, y, traced, 0.055F, Glow.withAlpha(core, 0.85F * flicker));
            // out of its front, where the porch and the street will be
            for (float[] wing : wings) {
                Glow.planeLine(buffer, pose, 0, y, 0, FLAT_U, FLAT_V, wing[0], wing[1], tipX, tipZ, 0.16F, Glow.withAlpha(halo, 0.8F * traced * flicker));
                Glow.planeLine(buffer, pose, 0, y + 0.01F, 0, FLAT_U, FLAT_V, wing[0], wing[1], tipX, tipZ, 0.05F,
                        Glow.withAlpha(core, 0.85F * traced * flicker));
            }
            if (!good) {
                Glow.planeLine(buffer, pose, 0, y, 0, FLAT_U, FLAT_V, x0, z0, x1, z1, 0.12F, Glow.withAlpha(halo, 0.6F * flicker));
                Glow.planeLine(buffer, pose, 0, y, 0, FLAT_U, FLAT_V, x0, z1, x1, z0, 0.12F, Glow.withAlpha(halo, 0.6F * flicker));
            }
            if (risen <= 0.01F) {
                return;
            }
            // the shape of the house over it: its corners standing up out of the land, and its eaves
            Glow.Billboard axes = Glow.billboard(pose);
            for (float[] corner : corners) {
                Vector3f[] points = {new Vector3f(corner[0], y, corner[1]), new Vector3f(corner[0], (y + top) / 2.0F, corner[1]),
                        new Vector3f(corner[0], top, corner[1])};
                float[] widths = {0.13F, 0.1F, 0.07F};
                int[] colors = {Glow.withAlpha(halo, 0.7F * flicker), Glow.withAlpha(halo, 0.4F * flicker), Glow.withAlpha(halo, 0.15F * flicker)};
                Glow.ribbon(buffer, pose, axes, points, widths, colors);
            }
            TownFx.outline(buffer, pose, x0, z0, x1, z1, top, 1.0F, 0.08F, Glow.withAlpha(halo, 0.25F * risen * flicker));
            // and a line of light climbing it, over and over
            float climb = (float) (now / 36.0 % 1.0);
            float fade = Mth.sin(climb * (float) Math.PI);
            float line = Mth.lerp(climb, y, top);
            TownFx.outline(buffer, pose, x0, z0, x1, z1, line, 1.0F, 0.1F, Glow.withAlpha(halo, 0.35F * fade * risen * flicker));
            TownFx.outline(buffer, pose, x0, z0, x1, z1, line, 1.0F, 0.03F, Glow.withAlpha(core, 0.4F * fade * risen * flicker));
        });
    }

    /**
     * @param ground the top of the land under it, at its middle height
     * @param problem why it can't stand there, if it can't
     */
    private record Aim(TownPlan.HomeLot lot, int ground, @Nullable Problem problem) {
    }

    private record Survey(int ground, boolean water, boolean steep) {
    }

    private enum Problem {
        OUTSIDE("hud.scarlet.home.outside"), WATER("hud.scarlet.home.water"), STEEP("hud.scarlet.home.steep");

        private final String key;

        Problem(String key) {
            this.key = key;
        }
    }
}
