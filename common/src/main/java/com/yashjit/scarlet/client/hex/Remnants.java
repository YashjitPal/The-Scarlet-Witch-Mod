package com.yashjit.scarlet.client.hex;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.client.fx.Glitch;
import com.yashjit.scarlet.client.fx.ScarletFx;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.hex.RemnantSnapshot;
import com.yashjit.scarlet.hex.town.EraStyle;
import com.yashjit.scarlet.hex.town.Role;
import com.yashjit.scarlet.network.RemnantBlocksPayload;
import com.yashjit.scarlet.network.TownGlitchPayload;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The homes fallen Hexes have left standing, as this client shows them. Each holds on a while after its Hex has gone,
 * stepping back through the eras one at a time, the newest first, down to black and white, like the show rewinding to
 * its first episode: each era sweeps round the house and up it in turn, its blocks turning as the sweep reaches them
 * and the picture of it turning with them texel by texel, and holds a moment before the next. A set changing channels
 * is heard as each begins, and red static crawls over it all the while. Then it goes a block at a time, still in black
 * and white. Its caster's clothes turn with it.
 *
 * <p>None of it really changes until it goes: the eras it steps through are only shown here, worked out from what it
 * was built of.
 */
public final class Remnants {

    /** How near the camera a home must be for this client to show it stepping through the eras. */
    private static final double SHOWN_FROM = 128.0;
    /** How near their home its caster must be for their clothes to turn with it. */
    private static final double WORN_NEAR = 96.0;
    /** How long what a home is built of is kept without word of the home itself, which may come a moment later. */
    private static final int UNCLAIMED_TICKS = 100;
    /** Ticks each era takes to sweep round a home, before it holds a moment for the next. */
    private static final int SWEEP_TICKS = 26;
    /** Ticks between showing each block its era again, before the last showing of it lets go. */
    private static final int REFRESH_TICKS = 12;
    private static final int EVERY_TICK = (1 << TownGlitchPayload.TICKS) - 1;
    /** Of the way round a sweep comes to a block, how much is its height on the house, so it spirals up it. */
    private static final float UP = 0.18F;
    private static final double TAU = Math.PI * 2.0;

    private static final Int2ObjectMap<Built> BUILT = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<Shown> SHOWN = new Int2ObjectOpenHashMap<>();
    private static @Nullable ClientLevel seenLevel;

    private Remnants() {
    }

    public static void receive(RemnantBlocksPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        forgetIfLeft(minecraft.level);
        if (minecraft.level == null || payload.positions().length != payload.roles().length || payload.roles().length != payload.paints().length) {
            return;
        }
        BUILT.put(payload.id(), new Built(Era.byIndex(payload.era()), payload.positions(), payload.roles(), payload.paints(),
                minecraft.level.getGameTime()));
        SHOWN.remove(payload.id());
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        forgetIfLeft(level);
        if (level == null || minecraft.isPaused()) {
            return;
        }
        long now = level.getGameTime();
        List<RemnantSnapshot> remnants = Hexes.clientRemnants();
        BUILT.int2ObjectEntrySet().removeIf(entry -> remnants.stream().noneMatch(remnant -> remnant.id() == entry.getIntKey())
                && now - entry.getValue().receivedAt() > UNCLAIMED_TICKS);
        SHOWN.keySet().removeIf(id -> !BUILT.containsKey(id));
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        RandomSource random = ScarletFx.random();
        for (RemnantSnapshot remnant : remnants) {
            Built built = BUILT.get(remnant.id());
            float glitch = remnant.glitch(now);
            if (built == null || glitch <= 0.0F || remnant.middle().distanceToSqr(camera) > SHOWN_FROM * SHOWN_FROM) {
                continue;
            }
            Vec3 min = Vec3.atLowerCornerOf(remnant.min());
            Vec3 max = Vec3.atLowerCornerOf(remnant.max()).add(1.0, 1.0, 1.0);
            // red static crawling over it, steady
            Glitch.crawl(min, max, 0.4F + 0.6F * glitch);
            Phase phase = phase(remnant, now);
            if (phase != null) {
                show(level, remnant, built, phase, now, random);
            }
        }
    }

    /**
     * Shows each block of a home the era the sweeps have brought it to: those the sweep has just reached, and every
     * now and then all of them again, before the last showing of them lets go.
     */
    private static void show(ClientLevel level, RemnantSnapshot remnant, Built built, Phase phase, long now, RandomSource random) {
        Shown shown = SHOWN.computeIfAbsent(remnant.id(), id -> new Shown(built.positions().length));
        boolean refresh = now - shown.refreshedAt >= REFRESH_TICKS;
        LongArrayList positions = new LongArrayList();
        List<BlockState> states = new ArrayList<>();
        LongArrayList turned = new LongArrayList();
        for (int i = 0; i < built.positions().length; i++) {
            BlockPos pos = BlockPos.of(built.positions()[i]);
            Era era = phase.sweep() >= reaches(remnant, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, phase.start()) ? phase.after()
                    : phase.before();
            boolean changed = era != shown.eras[i];
            shown.eras[i] = era;
            if (era == null || !changed && !refresh) {
                continue;
            }
            BlockState current = level.getBlockState(pos);
            Role role = Role.byIndex(built.roles()[i]);
            int paint = built.paints()[i];
            // only what still stands of it, as it was built
            if (current.isAir() || !EraStyle.isStyleOf(role, paint, current)) {
                continue;
            }
            positions.add(pos.asLong());
            states.add(EraStyle.state(role, era, paint, current));
            if (changed) {
                turned.add(pos.asLong());
            }
        }
        if (refresh) {
            shown.refreshedAt = now;
        }
        if (!positions.isEmpty()) {
            TownSlips.slip(level, positions.toLongArray(), states.toArray(BlockState[]::new), EVERY_TICK, false, true);
        }
        // the static of a changed channel where the sweep turns it
        for (int i = 0; i < Math.min(2, turned.size()); i++) {
            Vec3 at = Vec3.atCenterOf(BlockPos.of(turned.getLong(random.nextInt(turned.size()))));
            Glitch.puff(at.subtract(0.6, 0.6, 0.6), at.add(0.6, 0.6, 0.6));
        }
        if (phase.index() != shown.phase && phase.sweep() < 1.0F) {
            shown.phase = phase.index();
            Vec3 middle = remnant.middle();
            level.playLocalSound(middle.x, middle.y, middle.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.45F,
                    0.6F + 0.15F * phase.after().ordinal(), false);
        }
    }

    /**
     * Where a home is in stepping back through the eras right now, or null if it isn't yet.
     */
    public static @Nullable Phase phase(RemnantSnapshot remnant, double now) {
        double since = now - remnant.start();
        if (since < 0.0) {
            return null;
        }
        Built built = BUILT.get(remnant.id());
        List<Era> eras = eras(built == null ? Era.PRESENT : built.era());
        double each = RemnantSnapshot.GLITCH_TICKS / (double) eras.size();
        int index = (int) Math.min(eras.size() - 1, Math.floor(since / each));
        float sweep = (float) Math.clamp((since - index * each) / SWEEP_TICKS, 0.0, 1.0);
        // each sweep sets off from somewhere new round it
        float start = (float) ((index * 2.4 + remnant.id() * 0.9) % TAU);
        return new Phase(index, index == 0 ? null : eras.get(index - 1), eras.get(index), sweep, start);
    }

    /**
     * The eras a home steps back through: every one but the era it was built in, the newest first, down to black and
     * white.
     */
    private static List<Era> eras(Era built) {
        List<Era> eras = new ArrayList<>();
        for (int i = Era.values().length - 1; i >= 0; i--) {
            if (Era.values()[i] != built) {
                eras.add(Era.values()[i]);
            }
        }
        return eras;
    }

    /**
     * How far round a sweep has come when it reaches a point of a home, 0 to 1: round the house from where it set off,
     * and a little later the higher up, so it spirals up it. The picture of the home works this out the same way.
     */
    private static float reaches(RemnantSnapshot remnant, double x, double y, double z, float start) {
        Vec3 middle = remnant.middle();
        double around = ((Math.atan2(z - middle.z, x - middle.x) - start) / TAU) % 1.0;
        if (around < 0.0) {
            around += 1.0;
        }
        double height = remnant.max().getY() + 1 - remnant.min().getY();
        double up = Math.clamp((y - remnant.min().getY()) / height, 0.0, 1.0);
        return (float) (around * (1.0 - UP) + up * UP);
    }

    /**
     * The red magic that still has hold of each home: red bars tearing across it, harder while an era sweeps round it,
     * fading as the camera comes up close to it, where it would only be a flat red slab across the view.
     */
    public static void submit(SubmitNodeCollector collector, PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || BUILT.isEmpty()) {
            return;
        }
        double now = level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        List<Draw> draws = new ArrayList<>();
        for (RemnantSnapshot remnant : Hexes.clientRemnants()) {
            float glitch = remnant.glitch(now);
            Phase phase = phase(remnant, now);
            if (glitch <= 0.0F || phase == null || !BUILT.containsKey(remnant.id()) || remnant.middle().distanceToSqr(camera) > SHOWN_FROM * SHOWN_FROM) {
                continue;
            }
            Vec3 min = Vec3.atLowerCornerOf(remnant.min());
            Vec3 max = Vec3.atLowerCornerOf(remnant.max()).add(1.0, 1.0, 1.0);
            double dx = Math.max(0.0, Math.max(min.x - camera.x, camera.x - max.x));
            double dz = Math.max(0.0, Math.max(min.z - camera.z, camera.z - max.z));
            float closeness = Math.clamp((float) ((Math.sqrt(dx * dx + dz * dz) - 2.0) / 4.0), 0.0F, 1.0F);
            float sweeping = phase.sweep() > 0.0F && phase.sweep() < 1.0F ? 0.22F : 0.08F;
            float intensity = sweeping * (0.4F + 0.6F * glitch) * closeness;
            if (intensity > 0.02F) {
                Vec3 middle = remnant.middle().subtract(camera);
                draws.add(new Draw(middle.toVector3f(), (float) Math.max(max.x - min.x, max.z - min.z) * 1.05F, (float) (max.y - min.y), intensity,
                        remnant.id() * 131));
            }
        }
        if (draws.isEmpty()) {
            return;
        }
        long frame = Glitch.frame(now);
        GlowPass.submit(collector, poseStack, (pose, buffer) -> {
            Glow.Billboard axes = Glow.billboard(pose);
            for (Draw draw : draws) {
                Glitch.draw(buffer, pose, axes, draw.center(), draw.width(), draw.height(), draw.intensity(), frame, draw.seed());
            }
        });
    }

    /**
     * How strongly someone's clothes are caught up with the home a fallen Hex left by them, as hard as it glitches;
     * otherwise 0.
     */
    public static float worn(Player player, double now) {
        RemnantSnapshot remnant = wornBy(player);
        return remnant == null ? 0.0F : remnant.glitch(now);
    }

    /**
     * Where the home someone's clothes turn with is in stepping back through the eras, or null.
     */
    public static @Nullable Phase wornPhase(Player player, double now) {
        RemnantSnapshot remnant = wornBy(player);
        return remnant == null || remnant.glitch(now) <= 0.0F ? null : phase(remnant, now);
    }

    private static @Nullable RemnantSnapshot wornBy(Player player) {
        for (RemnantSnapshot remnant : Hexes.clientRemnants()) {
            if (remnant.caster().equals(player.getUUID()) && remnant.middle().distanceToSqr(player.position()) < WORN_NEAR * WORN_NEAR) {
                return remnant;
            }
        }
        return null;
    }

    private static void forgetIfLeft(@Nullable ClientLevel level) {
        if (level != seenLevel) {
            seenLevel = level;
            BUILT.clear();
            SHOWN.clear();
        }
    }

    /**
     * Where a home is in stepping back through the eras.
     *
     * @param index  which of its eras it is in, counting from the first
     * @param before the era it is leaving, or null for as it was built
     * @param after  the era sweeping round it
     * @param sweep  how far round it the sweep has come, 0 to 1
     * @param start  where round it the sweep set off from, in radians
     */
    public record Phase(int index, @Nullable Era before, Era after, float sweep, float start) {
    }

    /**
     * What a home is built of.
     *
     * @param era the era it was built in
     */
    private record Built(Era era, long[] positions, byte[] roles, byte[] paints, long receivedAt) {
    }

    /**
     * What this client is showing each block of a home: the era it shows, null for as it was built, by the block's place
     * in what the home is built of.
     */
    private static final class Shown {
        final @Nullable Era[] eras;
        long refreshedAt = Long.MIN_VALUE / 4;
        int phase = -1;

        Shown(int blocks) {
            this.eras = new Era[blocks];
        }
    }

    private record Draw(Vector3f center, float width, float height, float intensity, int seed) {
    }
}
