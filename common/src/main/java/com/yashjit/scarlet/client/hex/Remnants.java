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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The homes fallen Hexes have left standing, as this client shows them. Each holds on a while after its Hex has gone,
 * glitching through the eras ever more wildly: all of it jumps into another era for a moment, or its parts into
 * different ones at once, one era's walls under another's roof, or bits of it blink out of existence, while red static
 * crawls over it and the picture of it loses its signal and finds it again. Once it starts to go, a block at a time, it
 * glitches less and less, until the last of it has gone. Its caster's clothes slip through the eras with it.
 *
 * <p>None of it really changes until it goes: the eras it slips into are only shown here, worked out from what it was
 * built of.
 */
public final class Remnants {

    /** How near the camera a home must be for this client to glitch it. */
    private static final double SHOWN_FROM = 128.0;
    /** How near their home its caster must be for their clothes to slip through the eras with it. */
    private static final double WORN_NEAR = 96.0;
    /** How long what a home is built of is kept without word of the home itself, which may come a moment later. */
    private static final int UNCLAIMED_TICKS = 100;
    /** Blocks across each patch of a home that slips into an era of its own. */
    private static final int PATCH_SHIFT = 2;

    /** Showing itself as it is. */
    public static final int STILL = 0;
    /** All of it in another era. */
    public static final int WHOLE = 1;
    /** Each patch of it in an era of its own. */
    public static final int MIXED = 2;
    /** Bits of it gone. */
    public static final int GONE = 3;

    private static final Int2ObjectMap<Built> BUILT = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<Flick> FLICKS = new Int2ObjectOpenHashMap<>();
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
        FLICKS.keySet().removeIf(id -> !BUILT.containsKey(id));
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
            // red static crawling all over it, thicker the harder it glitches
            Glitch.crawl(min, max, 0.6F + 1.6F * glitch);
            Flick flick = FLICKS.get(remnant.id());
            if (flick == null || now >= flick.next()) {
                FLICKS.put(remnant.id(), flick(level, remnant, built, glitch, now, random));
            }
        }
    }

    /**
     * Its next slip: what kind, into which era, and when the one after it comes, sooner the harder it glitches.
     */
    private static Flick flick(ClientLevel level, RemnantSnapshot remnant, Built built, float glitch, long now, RandomSource random) {
        int kind = random.nextFloat() < 0.4F ? WHOLE : glitch > 0.55F && random.nextFloat() < 0.3F ? GONE : MIXED;
        Era era = pickEra(random, built.era());
        int seed = random.nextInt();
        int pattern = pattern(random, kind);
        int last = 31 - Integer.numberOfLeadingZeros(pattern);
        long next = now + last + 1 + Math.round(14.0F * (1.0F - glitch)) + random.nextInt(3);
        LongArrayList positions = new LongArrayList();
        List<BlockState> shown = new ArrayList<>();
        for (int i = 0; i < built.positions().length; i++) {
            long key = built.positions()[i];
            BlockPos pos = BlockPos.of(key);
            BlockState current = level.getBlockState(pos);
            Role role = Role.byIndex(built.roles()[i]);
            int paint = built.paints()[i];
            // only what still stands of it, as it was built
            if (current.isAir() || !EraStyle.isStyleOf(role, paint, current)) {
                continue;
            }
            BlockState other = switch (kind) {
                case WHOLE -> EraStyle.state(role, era, paint, current);
                case GONE -> walkable(role) || patch(pos, remnant.ground(), seed) < 0.55F ? current : Blocks.AIR.defaultBlockState();
                default -> {
                    float pick = patch(pos, remnant.ground(), seed);
                    yield pick < 0.2F ? current : EraStyle.state(role, Era.byIndex((int) ((pick - 0.2F) / 0.8F * Era.values().length)), paint, current);
                }
            };
            if (other != current) {
                positions.add(key);
                shown.add(other);
            }
        }
        if (!positions.isEmpty()) {
            TownSlips.slip(level, positions.toLongArray(), shown.toArray(BlockState[]::new), pattern, false, true);
            Vec3 middle = remnant.middle();
            float volume = 0.25F + 0.35F * glitch;
            switch (kind) {
                // a set changing channels, the picture jumping into another show
                case WHOLE -> level.playLocalSound(middle.x, middle.y, middle.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, volume,
                        0.7F + random.nextFloat() * 0.9F, false);
                // the signal breaking up into bits of every channel at once
                case MIXED -> level.playLocalSound(middle.x, middle.y, middle.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, volume,
                        1.5F + random.nextFloat() * 0.5F, false);
                // and lost altogether for a moment
                default -> level.playLocalSound(middle.x, middle.y, middle.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.BLOCKS, volume,
                        1.4F + random.nextFloat() * 0.5F, false);
            }
            for (int i = 0; i < 2 + Math.round(4 * glitch); i++) {
                Vec3 at = Vec3.atCenterOf(BlockPos.of(positions.getLong(random.nextInt(positions.size()))));
                Glitch.puff(at.subtract(0.6, 0.6, 0.6), at.add(0.6, 0.6, 0.6));
            }
        }
        return new Flick(kind, era, seed, pattern, now, next);
    }

    /**
     * Black and white more often than its share: the eras a sitcom remembers best.
     */
    private static Era pickEra(RandomSource random, Era built) {
        Era era;
        do {
            era = random.nextFloat() < 0.45F ? (random.nextBoolean() ? Era.FIFTIES : Era.SIXTIES) : Era.byIndex(random.nextInt(Era.values().length));
        } while (era == built);
        return era;
    }

    /**
     * Which ticks of a slip it shows its other self on, a bit each: a few frames all at once in another era, a stutter
     * in and out of existence.
     */
    private static int pattern(RandomSource random, int kind) {
        int bits = 0;
        int tick = 0;
        int flicks = kind == GONE ? 2 + random.nextInt(2) : 1 + (random.nextFloat() < 0.3F ? 1 : 0);
        for (int flick = 0; flick < flicks && tick < TownGlitchPayload.TICKS; flick++) {
            int on = kind == GONE ? 1 + random.nextInt(2) : kind == WHOLE ? 2 + random.nextInt(4) : 3 + random.nextInt(4);
            for (int k = 0; k < on && tick < TownGlitchPayload.TICKS; k++, tick++) {
                bits |= 1 << tick;
            }
            tick += 1 + random.nextInt(2);
        }
        return bits;
    }

    /**
     * A steady number from 0 to 1 for the patch a block lies in, for one slip.
     */
    private static float patch(BlockPos pos, int ground, int seed) {
        long cell = (long) (pos.getX() >> PATCH_SHIFT) * 73856093L ^ (long) (pos.getY() - ground >> PATCH_SHIFT) * 19349663L
                ^ (long) (pos.getZ() >> PATCH_SHIFT) * 83492791L;
        return Glitch.hash(cell, seed, 13);
    }

    /**
     * What someone could be standing on, which never blinks out from under them.
     */
    private static boolean walkable(Role role) {
        return role == Role.FLOOR || role == Role.FOUNDATION || role == Role.PORCH || role == Role.STEP || role == Role.WALKWAY
                || role == Role.LAWN || role == Role.FILL;
    }

    /**
     * The red magic that still has hold of each home: red bars tearing across it, hard while it shows another self and
     * faint between, fading as the camera comes up close to it, where it would only be a flat red slab across the view.
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
            if (glitch <= 0.0F || !BUILT.containsKey(remnant.id()) || remnant.middle().distanceToSqr(camera) > SHOWN_FROM * SHOWN_FROM) {
                continue;
            }
            Vec3 min = Vec3.atLowerCornerOf(remnant.min());
            Vec3 max = Vec3.atLowerCornerOf(remnant.max()).add(1.0, 1.0, 1.0);
            double dx = Math.max(0.0, Math.max(min.x - camera.x, camera.x - max.x));
            double dz = Math.max(0.0, Math.max(min.z - camera.z, camera.z - max.z));
            float closeness = Math.clamp((float) ((Math.sqrt(dx * dx + dz * dz) - 2.0) / 4.0), 0.0F, 1.0F);
            float hold = style(remnant, now).kind() != STILL ? 0.75F : 0.18F;
            float intensity = hold * (0.4F + 0.6F * glitch) * closeness;
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
     * How a home is slipping right now, for the picture of it to slip with it.
     *
     * @return the kind of slip ({@link #STILL} while it shows itself), the era it slipped into and the slip's seed
     */
    public static Style style(RemnantSnapshot remnant, double now) {
        Flick flick = FLICKS.get(remnant.id());
        if (flick == null) {
            return Style.NONE;
        }
        int tick = (int) (now - flick.start());
        boolean on = tick >= 0 && tick < TownGlitchPayload.TICKS && (flick.pattern() >> tick & 1) != 0;
        return on ? new Style(flick.kind(), flick.era(), flick.seed()) : Style.NONE;
    }

    /**
     * How wildly someone's clothes slip through the eras: while their home is left glitching near them after their Hex
     * fell, as hard as it glitches; otherwise 0.
     */
    public static float worn(Player player, double now) {
        for (RemnantSnapshot remnant : Hexes.clientRemnants()) {
            if (remnant.caster().equals(player.getUUID()) && remnant.middle().distanceToSqr(player.position()) < WORN_NEAR * WORN_NEAR) {
                return remnant.glitch(now);
            }
        }
        return 0.0F;
    }

    private static void forgetIfLeft(@Nullable ClientLevel level) {
        if (level != seenLevel) {
            seenLevel = level;
            BUILT.clear();
            FLICKS.clear();
        }
    }

    /**
     * What a home is built of.
     *
     * @param era the era it was built in
     */
    private record Built(Era era, long[] positions, byte[] roles, byte[] paints, long receivedAt) {
    }

    /**
     * @param start when it began
     * @param next  when the one after it comes
     */
    private record Flick(int kind, Era era, int seed, int pattern, long start, long next) {
    }

    private record Draw(Vector3f center, float width, float height, float intensity, int seed) {
    }

    /**
     * @param kind {@link #STILL}, {@link #WHOLE}, {@link #MIXED} or {@link #GONE}
     */
    public record Style(int kind, Era era, int seed) {
        static final Style NONE = new Style(STILL, Era.PRESENT, 0);
    }
}
