package com.yashjit.scarlet.hex;

import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.hex.town.HexTown;
import com.yashjit.scarlet.hex.town.HomeRemnant;
import com.yashjit.scarlet.hex.town.Restyle;
import com.yashjit.scarlet.hex.town.TownMemory;
import com.yashjit.scarlet.hex.town.TownPlan;
import com.yashjit.scarlet.magic.Magic;
import com.yashjit.scarlet.network.HexSyncPayload;
import com.yashjit.scarlet.platform.Services;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The rules of the Hex, shared by both sides.
 *
 * <ul>
 *     <li>One per caster. Cast it and it spreads out from you, then stays where you cast it, whether you stay or
 *     not.</li>
 *     <li>Hold the cast to grow it, sneak and hold to shrink it, faster the bigger it is. Shrinking stops at its
 *     smallest; shrink it again from there and it comes down.</li>
 *     <li>It falls if its caster loses the crown, after a few seconds of flickering in case the crown comes back.</li>
 *     <li>While it stands it holds back part of the caster's energy.</li>
 * </ul>
 */
public final class Hexes {

    public static final float CAST_RADIUS = 24.0F;
    public static final float MIN_RADIUS = 6.0F;
    public static final float MAX_RADIUS = 192.0F;
    /** Blocks per tick while the caster holds the cast, for a small Hex. */
    public static final float RESIZE_SPEED = 0.35F;
    /** Share of its radius a big Hex grows or shrinks by each tick, so it takes seconds whatever its size. */
    private static final float RESIZE_RATE = 0.012F;
    /** How much slower a Hex shrinks than it grows, so what its wall takes back is seen going. */
    public static final float SHRINK_SHARE = 0.6F;
    /** How close the caster must be to their wall to take hold of it and part it. */
    public static final double TEAR_REACH = 8.0;
    /** Blocks each edge of an opening moves per tick while its caster widens or closes it. */
    private static final float PART_SPEED = 0.2F;
    /** How near the corners of its wall the edges of an opening may come, in blocks. */
    public static final float PART_MARGIN = 2.0F;
    public static final float CAST_COST = 60.0F;
    /** Energy held back from the caster's bar while their Hex stands. */
    public static final float RESERVE = 40.0F;
    public static final int SPREAD_TICKS = 70;
    /** Longest the Hex waits for its caster's home to finish before bursting out anyway. */
    public static final int FOUNDING_LIMIT = 600;
    /**
     * Once their home stands, ticks the caster takes to come down onto its floor, and then until the Hex bursts out of
     * them where they stand, a moment after.
     */
    public static final int LAND_TICKS = 20;
    public static final int LANDING_TICKS = LAND_TICKS + 6;
    /** How near where their home stood a caster must cast over a town of theirs again for it to rise around them there. */
    public static final double HOME_RECALL = 16.0;
    /** How far off a caster can raise their home again inside their Hex, as far as they can aim. */
    public static final double HOME_PLACE_REACH = 160.0;
    /**
     * Fastest a caster is carried over to where their home stood, in blocks a tick, and how near it they begin easing
     * in, a share of the way left each tick, which takes them about {@link #DRIFT_SETTLE} ticks more to settle.
     */
    public static final double DRIFT_SPEED = 0.8;
    public static final double DRIFT_EASE = 2.3;
    private static final int DRIFT_SETTLE = 9;
    public static final int WARNING_TICKS = 100;
    /** Ticks between updates sent while a Hex keeps changing, such as while it is being resized. */
    public static final int SYNC_INTERVAL = 2;
    /**
     * Ticks the wall as players see it takes to glide over to each new size while it is resized: a tick longer than the
     * wait for the next, so it never stands still between them, and trailing where the wall truly is by as long.
     */
    public static final int GLIDE_TICKS = SYNC_INTERVAL + 1;

    private static final Map<ServerPlayer, Synced> SYNCED = new WeakHashMap<>();
    private static volatile ClientView client = ClientView.EMPTY;

    private Hexes() {
    }

    // ---------------------------------------------------------------- both sides

    /**
     * Whether the player has a Hex standing anywhere. On a client this is only known for yourself.
     */
    public static boolean ownsHex(Player player) {
        if (player.level() instanceof ServerLevel level) {
            return find(level.getServer(), player.getUUID()) != null;
        }
        return client.ownsHex();
    }

    /**
     * The Hexes in the dimension this client is in, as last sent.
     */
    public static List<HexSnapshot> clientHexes() {
        return client.hexes();
    }

    /**
     * The homes fallen Hexes have left standing in the dimension this client is in, as last sent.
     */
    public static List<RemnantSnapshot> clientRemnants() {
        return client.remnants();
    }

    public static void receive(HexSyncPayload payload) {
        client = new ClientView(payload.ownsHex(), List.copyOf(payload.hexes()), List.copyOf(payload.remnants()));
    }

    public static void clearClient() {
        client = ClientView.EMPTY;
    }

    // ---------------------------------------------------------------- server

    public static @Nullable Hex find(MinecraftServer server, UUID caster) {
        for (ServerLevel level : server.getAllLevels()) {
            Hex hex = HexData.of(level).byCaster(caster);
            if (hex != null) {
                return hex;
            }
        }
        return null;
    }

    /**
     * The Hex around a point, if any.
     */
    public static @Nullable Hex at(ServerLevel level, Vec3 point) {
        for (Hex hex : HexData.of(level).all()) {
            if (hex.contains(point)) {
                return hex;
            }
        }
        return null;
    }

    /**
     * Casts a Hex where the player stands. If they build, their home rises first, right in front of them, and the Hex
     * bursts out of it once it stands. Cast by a house, or what is left of one, and their home is made of it: they are
     * carried in to the middle of it as it is made over and finished around them.
     *
     * <p>Cast over ground a town of theirs stood on before, with the same build, and that town comes back just as it
     * was. Cast by where its home stood and they are carried over to it as it rises around them again; cast further off
     * and the town just comes back around them, with their home where it was.
     *
     * @return whether a Hex was cast; a caster with one standing anywhere cannot cast another
     */
    public static boolean cast(ServerPlayer player, long now) {
        ServerLevel level = player.level();
        if (find(level.getServer(), player.getUUID()) != null) {
            return false;
        }
        HexBuild build = Services.PLAYER_DATA.get(player).hexBuild();
        HexData data = HexData.of(level);
        Vec3 center = player.position();
        // what is left of their last home, or of anyone's the new Hex comes down on, goes before it rises again
        for (HomeRemnant remnant : List.copyOf(data.remnants())) {
            if (remnant.caster().equals(player.getUUID())
                    || HexShape.level(center, 1.0F, remnant.center()) <= CAST_RADIUS + remnant.reach() + 2.0F) {
                finish(level, data, remnant);
            }
        }
        TownMemory back = null;
        boolean founding = build != HexBuild.NOTHING;
        int carried = 0;
        Direction facing = player.getDirection();
        if (founding) {
            back = TownMemory.first(data.towns(player.getUUID(), build), center.x, center.z);
            if (back != null) {
                double away = back.home().subtract(center).horizontalDistance();
                founding = away <= HOME_RECALL;
                if (founding) {
                    center = back.home();
                    // their home waits for them to be carried over to where it stood: steadily, then easing in
                    carried = away > 0.5 ? Mth.ceil(Math.max(0.0, away - DRIFT_EASE) / DRIFT_SPEED) + DRIFT_SETTLE : 0;
                }
            } else {
                // a house standing near them, or the ruin of one, is what their home is made of, and they are carried
                // in to the middle of it as it is
                Restyle.HomeSite site = Restyle.site(level, center, facing, key -> data.isBuilt(BlockPos.of(key)));
                if (site != null) {
                    double away = site.center().subtract(center).horizontalDistance();
                    carried = away > 0.5 ? Mth.ceil(Math.max(0.0, away - DRIFT_EASE) / DRIFT_SPEED) + DRIFT_SETTLE : 0;
                    center = site.center();
                    facing = site.forward();
                }
            }
        }
        Hex hex = new Hex(player.getUUID(), player.getGameProfile().name(), center, CAST_RADIUS, Era.FIFTIES, Hex.DEFAULT_NAME,
                Hex.Phase.SPREADING, now, 0.0F);
        if (build != HexBuild.NOTHING) {
            List<TownMemory> remembered = data.towns(player.getUUID(), build, center, MAX_RADIUS);
            hex.town = back != null ? HexTown.recall(back, remembered, hex.era)
                    : HexTown.raise(build, center, facing, player.getUUID().getLeastSignificantBits() ^ now * 31L, remembered, hex.era);
            int raising = founding ? hex.town.foundHome(level, hex.center, hex.era, now, carried) : -1;
            if (raising > 0) {
                hex.enter(Hex.Phase.FOUNDING, now);
                // when it is meant to stand, the same moment every client counts to
                hex.homeStoodAt = now + raising;
            }
        }
        data.add(hex);
        play(level, hex.center, SoundEvents.BEACON_ACTIVATE, 3.0F, 0.5F);
        play(level, hex.center, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, 2.0F, 0.6F);
        if (hex.phase == Hex.Phase.SPREADING) {
            play(level, hex.center, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5F, 1.8F);
        }
        return true;
    }

    /**
     * How far out the wall stands at a moment: nowhere while the home is founded, easing out as it spreads, falling
     * inward as it collapses, and otherwise at the Hex's radius.
     */
    public static float wallRadius(Hex hex, double now) {
        return wallRadius(hex.phase, now - hex.phaseSince, hex.radius, hex.phaseRadius);
    }

    /**
     * The same for a Hex as a client knows it.
     */
    public static float wallRadius(HexSnapshot hex, double now) {
        return wallRadius(hex.phaseValue(), now - hex.phaseSince(), hex.radius(), hex.phaseRadius());
    }

    private static float wallRadius(Hex.Phase phase, double sincePhase, float radius, float phaseRadius) {
        float t = (float) sincePhase;
        return switch (phase) {
            case FOUNDING -> 0.0F;
            case SPREADING -> {
                float k = Math.clamp(t / SPREAD_TICKS, 0.0F, 1.0F);
                float out = 1.0F - (1.0F - k) * (1.0F - k) * (1.0F - k);
                yield radius * out;
            }
            case COLLAPSING -> phaseRadius * (1.0F - fallen(t / collapseTicks(phaseRadius)));
            default -> radius;
        };
    }

    /**
     * Ticks a Hex takes to fall from a size: longer the bigger it is, so it comes down slowly enough to watch the town go.
     */
    public static float collapseTicks(float radius) {
        return Math.clamp(120.0F + radius, 140.0F, 320.0F);
    }

    /**
     * How far in a falling Hex's wall has come, as a share of the way to its middle, a share {@code k} of the way through
     * its fall: easing in, then gathering speed, though never so much that the last of the town goes in a rush.
     */
    public static float fallen(float k) {
        float t = Math.clamp(k, 0.0F, 1.0F);
        return t * t;
    }

    /**
     * Blocks behind the front of a new era over which its look comes in: color blooming into a black and white world,
     * or draining out of one.
     */
    public static final float ERA_BAND = 20.0F;

    /**
     * Ticks a new era takes to spread over a Hex from its middle, out to the wall: longer the bigger the Hex.
     */
    public static float eraSweepTicks(float radius) {
        return Math.clamp(70.0F + radius * 0.9F, 90.0F, 240.0F);
    }

    /**
     * How far out from a Hex's middle a new era has spread, a while after it changed, measured as {@link HexShape#level}
     * is: easing out from the caster's home, quickening across the town and slowing as it comes to the wall, with its
     * band following behind. Everywhere once it is done.
     */
    public static float eraFront(float radius, double sinceChange) {
        float k = (float) (sinceChange / eraSweepTicks(radius));
        if (k >= 1.0F) {
            return Float.MAX_VALUE;
        }
        k = Math.max(0.0F, k);
        float eased = k * k * (3.0F - 2.0F * k);
        return (radius + ERA_BAND) * eased;
    }

    /**
     * The era showing at a point of a Hex as a client sees it: the new one where its front has passed, the one before it
     * further out.
     */
    public static Era eraAt(HexSnapshot hex, Vec3 point, double now) {
        Era era = hex.eraValue();
        Era before = hex.previousEraValue();
        if (before == era) {
            return era;
        }
        return HexShape.level(hex.center(), 1.0F, point) <= eraFront(hex.radius(), now - hex.eraSince()) ? era : before;
    }

    /**
     * The Hex a point is inside on this client, if any.
     */
    public static @Nullable HexSnapshot clientHexAt(Vec3 point, double now) {
        for (HexSnapshot hex : client.hexes()) {
            if (HexShape.contains(hex.center(), wallRadius(hex, now), point)) {
                return hex;
            }
        }
        return null;
    }

    /**
     * Moves a Hex to another era, beginning its next episode. Its town makes itself over to match, sweeping out from
     * the middle.
     */
    public static void setEra(ServerLevel level, Hex hex, Era era) {
        if (hex.era == era) {
            return;
        }
        hex.previousEra = hex.era;
        hex.era = era;
        hex.eraSince = level.getGameTime();
        hex.episode++;
        HexData.of(level).changed();
        play(level, hex.center, SoundEvents.BEACON_POWER_SELECT, 2.5F, 0.6F);
    }

    /**
     * Turns episodes mode on or off: while on, the era moves on by itself every morning.
     */
    public static void setEpisodes(ServerLevel level, Hex hex, boolean on) {
        hex.episodes = on;
        hex.episodeDay = day(level);
        HexData.of(level).changed();
    }

    public static void rename(ServerLevel level, Hex hex, String name) {
        hex.name = name;
        HexData.of(level).changed();
    }

    /**
     * Sets the time of day and weather inside a Hex, which everyone inside sees come over its sky.
     */
    public static void setSky(ServerLevel level, Hex hex, HexSky sky) {
        if (!hex.sky.equals(sky)) {
            hex.sky = sky;
            HexData.of(level).changed();
        }
    }

    /**
     * Raises the caster's home again where they choose inside their standing Hex: what stands of it now dissolves and
     * it goes up there instead. A Hex that builds nothing builds just their home from then on.
     */
    public static void raiseHome(ServerPlayer player, TownPlan.HomeLot lot) {
        ServerLevel level = player.level();
        HexData data = HexData.of(level);
        Hex hex = data.byCaster(player.getUUID());
        if (hex == null || hex.phase == Hex.Phase.COLLAPSING) {
            return;
        }
        Vec3 middle = new Vec3(lot.x() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lot.x(), lot.z()), lot.z() + 0.5);
        String refusal = null;
        if (hex.phase != Hex.Phase.STANDING) {
            refusal = "hex.scarlet.home.not_now";
        } else if (!TownPlan.fits(lot, hex.center, hex.radius)) {
            refusal = "hex.scarlet.home.outside";
        } else if (player.position().subtract(middle).horizontalDistance() > HOME_PLACE_REACH + 8.0) {
            refusal = "hex.scarlet.home.too_far";
        }
        HexTown town = hex.town;
        if (refusal == null) {
            if (town == null) {
                town = HexTown.raise(HexBuild.HOME, hex.center, lot.front().getOpposite(), player.getUUID().getLeastSignificantBits() ^ level.getGameTime() * 31L,
                        List.of(), hex.era);
            }
            refusal = switch (town.raiseHome(level, hex.center, lot)) {
                case RAISED -> null;
                case ALREADY -> "";
                case BUSY -> "hex.scarlet.home.busy";
                case NO_ROOM -> "hex.scarlet.home.no_room";
            };
        }
        if (refusal != null) {
            if (!refusal.isEmpty()) {
                player.sendOverlayMessage(Component.translatable(refusal).withColor(ScarletPalette.BRIGHT_SCARLET));
            }
            return;
        }
        hex.town = town;
        data.setDirty();
        play(level, middle.add(0.0, 1.0, 0.0), SoundEvents.BEACON_ACTIVATE, 2.0F, 0.8F);
        play(level, middle.add(0.0, 1.0, 0.0), SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 0.5F);
        play(level, player.position(), SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.8F, 0.7F);
    }

    /**
     * The next morning's episode: the next era on, or after the present, back to the 1950s for a new season.
     */
    private static void nextEpisode(ServerLevel level, Hex hex) {
        Era[] eras = Era.values();
        int next = hex.era.ordinal() + 1;
        if (next >= eras.length) {
            hex.season++;
            hex.episode = 0;
            next = 0;
        }
        setEra(level, hex, eras[next]);
    }

    private static long day(ServerLevel level) {
        return Math.floorDiv(level.getOverworldClockTime(), 24000L);
    }

    /**
     * Grows or shrinks the caster's Hex in their dimension, faster the bigger it is. Shrinking stops at its smallest;
     * shrinking it again from there brings it down, so it never falls by accident.
     *
     * @return whether it can go on resizing
     */
    public static boolean resize(ServerPlayer player, boolean grow, long now) {
        ServerLevel level = player.level();
        HexData data = HexData.of(level);
        Hex hex = data.byCaster(player.getUUID());
        if (hex == null || hex.phase == Hex.Phase.COLLAPSING || hex.phase == Hex.Phase.FOUNDING) {
            return false;
        }
        float step = Math.max(RESIZE_SPEED, hex.radius * RESIZE_RATE) * (grow ? 1.0F : SHRINK_SHARE);
        if (!grow && hex.radius <= MIN_RADIUS) {
            // a fresh press at the smallest, not the end of a long shrink
            if (now - Magic.state(player).channelStart() <= 1) {
                collapse(level, data, hex, now);
            }
            return false;
        }
        hex.radius = Math.clamp(hex.radius + (grow ? step : -step), MIN_RADIUS, MAX_RADIUS);
        data.changed();
        return grow ? hex.radius < MAX_RADIUS : hex.radius > MIN_RADIUS;
    }

    /**
     * Keeps a caster aloft while their home rises around them, or lets them go once it stands. Their own client floats
     * them; the server only lets them fly meanwhile, and spares them the fall.
     */
    private static void hold(@Nullable ServerPlayer caster, boolean aloft) {
        if (caster == null || caster.isCreative() || caster.isSpectator()) {
            return;
        }
        Abilities abilities = caster.getAbilities();
        caster.fallDistance = 0.0F;
        if (aloft && (!abilities.mayfly || !abilities.flying)) {
            abilities.mayfly = true;
            abilities.flying = true;
            caster.onUpdateAbilities();
        } else if (!aloft && abilities.mayfly && !Magic.state(caster).levitating()) {
            abilities.mayfly = false;
            abilities.flying = false;
            caster.onUpdateAbilities();
        }
    }

    /**
     * Sets a Hex's size outright; its wall glides out or in to it, and its town follows.
     */
    public static void setSize(ServerLevel level, Hex hex, float radius) {
        if (hex.phase == Hex.Phase.COLLAPSING) {
            return;
        }
        hex.radius = Math.clamp(radius, MIN_RADIUS, MAX_RADIUS);
        HexData.of(level).changed();
    }

    /**
     * Whether the player's Hex stands in the dimension they are in, so they can reach it to resize it.
     */
    public static boolean ownsHexHere(ServerPlayer player) {
        return HexData.of(player.level()).byCaster(player.getUUID()) != null;
    }

    /**
     * Whether a player is founding their Hex right now, carried into their home as it rises around them.
     */
    public static boolean isFounding(ServerPlayer player) {
        Hex hex = HexData.of(player.level()).byCaster(player.getUUID());
        return hex != null && hex.phase == Hex.Phase.FOUNDING;
    }

    /**
     * The caster takes hold of their Hex's wall, if they are looking at it from close enough to touch it: to part it
     * where they look, or, if it already stands parted, to widen or close the opening.
     *
     * @param widen false to close it
     * @return whether they took hold of it
     */
    public static boolean beginPart(ServerPlayer player, boolean widen) {
        ServerLevel level = player.level();
        HexData data = HexData.of(level);
        Hex hex = data.byCaster(player.getUUID());
        if (hex == null || hex.phase != Hex.Phase.STANDING) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 at = HexRipples.crossing(hex.center, hex.radius, eye, eye.add(player.getLookAngle().scale(TEAR_REACH)));
        if (at == null || hex.tearAt == null && !widen) {
            return false;
        }
        if (hex.tearAt == null) {
            hex.tearAt = at;
            hex.opening = 0.0F;
            play(level, at, SoundEvents.RESPAWN_ANCHOR_CHARGE, 2.0F, 0.6F);
            play(level, at, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.2F, 0.55F);
            play(level, at, SoundEvents.BEACON_DEACTIVATE, 1.5F, 1.4F);
        }
        hex.parting = true;
        data.changed();
        return true;
    }

    /**
     * Every tick the caster holds the opening, its edges move a little further apart, or closer together. Closed all
     * the way, it is gone.
     *
     * @return whether it can go on: false once it is closed, or as wide as the wall allows
     */
    public static boolean part(ServerPlayer player, boolean widen) {
        ServerLevel level = player.level();
        HexData data = HexData.of(level);
        Hex hex = data.byCaster(player.getUUID());
        if (hex == null || hex.tearAt == null || hex.phase != Hex.Phase.STANDING) {
            return false;
        }
        if (!widen && hex.opening <= 0.0F) {
            play(level, hex.tearAt, SoundEvents.BEACON_ACTIVATE, 1.5F, 1.2F);
            play(level, hex.tearAt, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5F, 0.5F);
            hex.tearAt = null;
            hex.parting = false;
            data.changed();
            return false;
        }
        float widest = widestOpening(hex);
        hex.opening = Math.clamp(hex.opening + (widen ? PART_SPEED : -PART_SPEED), 0.0F, widest);
        data.changed();
        return !widen || hex.opening < widest;
    }

    /**
     * The caster lets go of the opening; it stays as wide as they left it.
     */
    public static void endPart(ServerPlayer player) {
        HexData data = HexData.of(player.level());
        Hex hex = data.byCaster(player.getUUID());
        if (hex != null && hex.parting) {
            hex.parting = false;
            data.changed();
        }
    }

    /**
     * How far each edge of an opening can be pulled from where it was taken hold of: until the edge nearer a corner
     * stops a little short of it and the other catches up. The corners always stand, so a side of the Hex never falls
     * away entirely.
     */
    private static float widestOpening(Hex hex) {
        if (hex.tearAt == null) {
            return 0.0F;
        }
        Vec3 normal = HexShape.outward(hex.center, hex.tearAt);
        Vec3 along = new Vec3(-normal.z, 0.0, normal.x);
        double offset = hex.tearAt.subtract(hex.center).dot(along);
        return (float) Math.max(0.0, HexShape.halfSide(hex.radius) + Math.abs(offset) - PART_MARGIN);
    }

    /**
     * A blow to a caster shakes their Hex for a few seconds, more the harder it was.
     */
    public static void hurt(ServerPlayer player, float damage) {
        for (ServerLevel level : player.level().getServer().getAllLevels()) {
            HexData data = HexData.of(level);
            Hex hex = data.byCaster(player.getUUID());
            if (hex != null) {
                long now = level.getGameTime();
                hex.stress = HexUnrest.struck(HexUnrest.stress(hex.stress, hex.stressAt, now), damage);
                hex.stressAt = now;
                data.changed();
                return;
            }
        }
    }

    public static void tick(ServerLevel level) {
        HexData data = HexData.of(level);
        long now = level.getGameTime();
        if (now % 20 == 0 && (!data.pending().isEmpty() || !data.pendingKept().isEmpty() || !data.pendingPaint().isEmpty())) {
            putBackLeftovers(level, data);
        }
        Residents.tick(level, data, now);
        tickRemnants(level, data, now);
        HexDecor.tick(level, data, now);
        if (data.all().isEmpty()) {
            return;
        }
        List<Hex> fallen = new ArrayList<>();
        for (Hex hex : data.all()) {
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(hex.caster);
            // an offline caster still has their crown; the Hex waits for them
            boolean crowned = caster == null || caster.isAlive() && CrownItem.isWearingCrown(caster);
            if (caster != null && !caster.getGameProfile().name().equals(hex.casterName)) {
                hex.casterName = caster.getGameProfile().name();
            }
            switch (hex.phase) {
                case FOUNDING -> {
                    boolean standing = hex.town == null || hex.town.isHomeFinished(BlockPos.containing(hex.center));
                    if (standing && hex.homeStoodAt < 0) {
                        // picked up again after a reload
                        hex.homeStoodAt = now;
                    }
                    standing &= now >= hex.homeStoodAt;
                    if (!crowned) {
                        hex.enter(Hex.Phase.WARNING, now);
                        data.changed();
                        hold(caster, false);
                    } else if (standing && now - hex.homeStoodAt >= LANDING_TICKS || now - hex.phaseSince >= FOUNDING_LIMIT) {
                        // the home stands and its caster has come down in it: the Hex bursts out of them
                        hex.enter(Hex.Phase.SPREADING, now);
                        data.changed();
                        hold(caster, false);
                        play(level, hex.center, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8F, 1.6F);
                        play(level, hex.center, SoundEvents.BEACON_ACTIVATE, 3.0F, 0.7F);
                        play(level, hex.center, SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.45F);
                        play(level, hex.center, SoundEvents.WARDEN_SONIC_BOOM, 1.2F, 0.6F);
                    } else {
                        // floating in the middle of it as it rises, then down on its floor
                        hold(caster, !standing || now - hex.homeStoodAt < LAND_TICKS);
                        if (caster != null) {
                            // carried in through the walls of what stands there already; set after their own tick
                            // clears it, it holds while the moves they send before the next come in
                            caster.noPhysics = true;
                        }
                    }
                }
                case SPREADING, STANDING -> {
                    if (!crowned) {
                        hex.enter(Hex.Phase.WARNING, now);
                        data.changed();
                    } else if (hex.phase == Hex.Phase.SPREADING && now - hex.phaseSince >= SPREAD_TICKS) {
                        hex.enter(Hex.Phase.STANDING, now);
                        data.changed();
                    } else if (hex.episodes && now % 20 == 0) {
                        long day = day(level);
                        if (day != hex.episodeDay) {
                            // a clock turned back with /time only catches the count up
                            boolean morning = day > hex.episodeDay;
                            hex.episodeDay = day;
                            data.setDirty();
                            if (morning) {
                                nextEpisode(level, hex);
                            }
                        }
                    }
                }
                case WARNING -> {
                    if (crowned) {
                        hex.enter(Hex.Phase.STANDING, now);
                        data.changed();
                    } else if (now - hex.phaseSince >= WARNING_TICKS) {
                        collapse(level, data, hex, now);
                    }
                }
                case COLLAPSING -> {
                    if (now - hex.phaseSince >= collapseTicks(hex.phaseRadius)) {
                        fallen.add(hex);
                    }
                }
            }
            if (hex.tearAt != null) {
                if (hex.phase != Hex.Phase.STANDING) {
                    // a Hex that is falling, or flickering for want of its caster's crown, closes up
                    hex.tearAt = null;
                    hex.parting = false;
                    data.changed();
                } else {
                    if (hex.parting && caster == null) {
                        hex.parting = false;
                        data.changed();
                    }
                    // a Hex drawn in leaves less wall to part
                    float widest = widestOpening(hex);
                    if (hex.opening > widest) {
                        hex.opening = widest;
                        data.changed();
                    }
                }
            }
            // paint first, so a town block painted over is the town's again by the time its wall takes it down
            HexPaint.tick(level, data, hex, wallRadius(hex, now));
            if (hex.town != null && hex.phase == Hex.Phase.COLLAPSING) {
                // the wall falling in on the caster's home lets it go: it stands on, alone, after the Hex has gone
                HomeRemnant left = hex.town.leaveHome(level, hex.center, wallRadius(hex, now), hex.caster, now);
                if (left != null) {
                    data.addRemnant(left);
                }
            }
            if (hex.town != null) {
                if (hex.phase == Hex.Phase.STANDING) {
                    hex.town.slip(level, BlockPos.containing(hex.center), hex.unrest(now), now);
                }
                hex.town.tick(level, hex.center, wallRadius(hex, now), hex.radius, hex.era, eraFront(hex.radius, now - hex.eraSince),
                        hex.phase == Hex.Phase.COLLAPSING, hex.phase == Hex.Phase.FOUNDING ? hex.caster : null, now);
                if (hex.town.takeChanged()) {
                    data.setDirty();
                }
            }
        }
        for (Hex hex : fallen) {
            takeDownTown(level, data, hex);
            data.remove(hex);
        }
    }

    /**
     * Lets a Hex go: it falls as it would without its caster's crown, wall rushing in.
     */
    public static void release(ServerLevel level, Hex hex) {
        if (hex.phase != Hex.Phase.COLLAPSING) {
            collapse(level, HexData.of(level), hex, level.getGameTime());
        }
    }

    /**
     * Takes down every Hex in a dimension at once, without the fall, and any homes fallen ones have left standing.
     */
    public static void dispelAll(ServerLevel level) {
        HexData data = HexData.of(level);
        for (Hex hex : List.copyOf(data.all())) {
            takeDownTown(level, data, hex);
            data.remove(hex);
        }
        for (HomeRemnant remnant : List.copyOf(data.remnants())) {
            finish(level, data, remnant);
        }
    }

    /**
     * The homes fallen Hexes have left standing, glitching and then going a part at a time. One a Hex is cast over, or
     * spreads out over, goes back all at once, so a new town never builds over what is left of an old one.
     */
    private static void tickRemnants(ServerLevel level, HexData data, long now) {
        if (data.remnants().isEmpty()) {
            return;
        }
        for (HomeRemnant remnant : List.copyOf(data.remnants())) {
            boolean covered = false;
            for (Hex hex : data.all()) {
                if (hex.phase != Hex.Phase.COLLAPSING
                        && HexShape.level(hex.center, 1.0F, remnant.center()) <= wallRadius(hex, now) + remnant.reach() + 2.0F) {
                    covered = true;
                    break;
                }
            }
            if (covered) {
                finish(level, data, remnant);
            } else if (remnant.tick(level, now, data.pending()::put, data.pendingKept()::add)) {
                data.removeRemnant(remnant);
            }
        }
    }

    private static void finish(ServerLevel level, HexData data, HomeRemnant remnant) {
        remnant.finish(level, data.pending()::put, data.pendingKept()::add);
        data.removeRemnant(remnant);
    }

    /**
     * Puts back everything a Hex's town built. What lies in chunks that aren't loaded goes back when they are.
     */
    private static void takeDownTown(ServerLevel level, HexData data, Hex hex) {
        HexPaint.restoreAll(level, data, hex);
        if (hex.town != null) {
            TownMemory memory = hex.town.memory(hex.caster, hex.center);
            if (memory != null) {
                data.remember(memory);
            }
            hex.town.restoreAll(level, data.pending()::put, data.pendingKept()::add);
            data.setDirty();
        }
    }

    private static void putBackLeftovers(ServerLevel level, HexData data) {
        // paint first, so the town finds its own blocks under it
        HexPaint.putBackLeftovers(level, data);
        // the blocks first, so what hung on them has somewhere to hang
        var iterator = data.pending().long2ObjectEntrySet().fastIterator();
        int budget = 2048;
        while (iterator.hasNext() && budget-- > 0) {
            var entry = iterator.next();
            if (HexTown.putBackLeftover(level, entry.getLongKey(), entry.getValue())) {
                iterator.remove();
                data.setDirty();
            }
        }
        var kept = data.pendingKept().iterator();
        while (kept.hasNext()) {
            HexTown.Kept entry = kept.next();
            BlockPos at = BlockPos.containing(entry.at());
            if (level.hasChunkAt(at) && !hasPendingNear(data, at)) {
                HexTown.setDown(level, entry);
                kept.remove();
                data.setDirty();
            }
        }
    }

    /**
     * Whether blocks right around a spot are still waiting to be put back, so whatever hangs there has to wait for them.
     */
    private static boolean hasPendingNear(HexData data, BlockPos at) {
        if (data.pending().isEmpty()) {
            return false;
        }
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1))) {
            if (data.pending().containsKey(pos.asLong())) {
                return true;
            }
        }
        return false;
    }

    private static void collapse(ServerLevel level, HexData data, Hex hex, long now) {
        hex.enter(Hex.Phase.COLLAPSING, now);
        data.changed();
        play(level, hex.center, SoundEvents.BEACON_DEACTIVATE, 3.0F, 0.5F);
        play(level, hex.center, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 2.0F, 0.6F);
    }

    /**
     * Sends a player the Hexes in their dimension when they arrive there or anything about them changes.
     */
    public static void syncIfNeeded(ServerPlayer player) {
        ServerLevel level = player.level();
        HexData data = HexData.of(level);
        boolean owns = find(level.getServer(), player.getUUID()) != null;
        long now = level.getGameTime();
        Synced last = SYNCED.get(player);
        if (last != null && last.level() == level.dimension() && last.instance() == data.instance() && last.owns() == owns
                && (last.version() == data.version() || now - last.sentAt() < SYNC_INTERVAL)) {
            return;
        }
        Services.NETWORK.sendToPlayer(player, new HexSyncPayload(owns, data.snapshots(), data.remnantSnapshots()));
        SYNCED.put(player, new Synced(level.dimension(), data.instance(), data.version(), owns, now));
    }

    private static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private record Synced(ResourceKey<Level> level, long instance, int version, boolean owns, long sentAt) {
    }

    private record ClientView(boolean ownsHex, List<HexSnapshot> hexes, List<RemnantSnapshot> remnants) {
        static final ClientView EMPTY = new ClientView(false, List.of(), List.of());
    }
}
