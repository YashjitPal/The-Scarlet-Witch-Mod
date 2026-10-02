package com.yashjit.scarlet.hex;

import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.hex.town.HexTown;
import com.yashjit.scarlet.network.HexSyncPayload;
import com.yashjit.scarlet.platform.Services;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The rules of the Hex, shared by both sides.
 *
 * <ul>
 *     <li>One per caster. Cast it and it spreads out from you, then stays where you cast it, whether you stay or
 *     not.</li>
 *     <li>Hold the cast to grow it, sneak and hold to shrink it. Shrink it past its smallest and it comes down.</li>
 *     <li>It falls if its caster loses the crown, after a few seconds of flickering in case the crown comes back.</li>
 *     <li>While it stands it holds back part of the caster's energy.</li>
 * </ul>
 */
public final class Hexes {

    public static final float CAST_RADIUS = 24.0F;
    public static final float MIN_RADIUS = 6.0F;
    public static final float MAX_RADIUS = 64.0F;
    /** Blocks per tick while the caster holds the cast. */
    public static final float RESIZE_SPEED = 0.35F;
    public static final float CAST_COST = 60.0F;
    /** Energy held back from the caster's bar while their Hex stands. */
    public static final float RESERVE = 40.0F;
    public static final int SPREAD_TICKS = 70;
    /** Longest the Hex waits for its caster's home to finish before bursting out anyway. */
    public static final int FOUNDING_LIMIT = 200;
    public static final int WARNING_TICKS = 100;
    public static final int COLLAPSE_TICKS = 50;
    /** Ticks between updates sent while a Hex keeps changing, such as while it is being resized. */
    private static final int SYNC_INTERVAL = 2;

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

    public static void receive(HexSyncPayload payload) {
        client = new ClientView(payload.ownsHex(), List.copyOf(payload.hexes()));
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
     * bursts out of it once it stands.
     *
     * @return whether a Hex was cast; a caster with one standing anywhere cannot cast another
     */
    public static boolean cast(ServerPlayer player, long now) {
        ServerLevel level = player.level();
        if (find(level.getServer(), player.getUUID()) != null) {
            return false;
        }
        HexBuild build = Services.PLAYER_DATA.get(player).hexBuild();
        Hex hex = new Hex(player.getUUID(), player.getGameProfile().name(), player.position(), CAST_RADIUS, Era.FIFTIES, Hex.DEFAULT_NAME,
                Hex.Phase.SPREADING, now, 0.0F);
        if (build != HexBuild.NOTHING) {
            hex.town = new HexTown(build, player.getDirection(), player.getUUID().getLeastSignificantBits() ^ now * 31L, hex.era);
            if (hex.town.foundHome(level, hex.center, hex.era, now) > 0) {
                hex.enter(Hex.Phase.FOUNDING, now);
            }
        }
        HexData.of(level).add(hex);
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
            case COLLAPSING -> {
                float k = Math.clamp(t / COLLAPSE_TICKS, 0.0F, 1.0F);
                yield phaseRadius * (1.0F - k * k * k);
            }
            default -> radius;
        };
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
     * Grows or shrinks the caster's Hex in their dimension. Shrinking it past its smallest brings it down.
     *
     * @return whether it can go on resizing
     */
    public static boolean resize(ServerPlayer player, float delta, long now) {
        ServerLevel level = player.level();
        HexData data = HexData.of(level);
        Hex hex = data.byCaster(player.getUUID());
        if (hex == null || hex.phase == Hex.Phase.COLLAPSING || hex.phase == Hex.Phase.FOUNDING) {
            return false;
        }
        float next = hex.radius + delta;
        if (next < MIN_RADIUS) {
            collapse(level, data, hex, now);
            return false;
        }
        hex.radius = Math.min(next, MAX_RADIUS);
        data.changed();
        return hex.radius < MAX_RADIUS || delta < 0.0F;
    }

    /**
     * Whether the player's Hex stands in the dimension they are in, so they can reach it to resize it.
     */
    public static boolean ownsHexHere(ServerPlayer player) {
        return HexData.of(player.level()).byCaster(player.getUUID()) != null;
    }

    public static void tick(ServerLevel level) {
        HexData data = HexData.of(level);
        long now = level.getGameTime();
        if (now % 20 == 0 && !data.pending().isEmpty()) {
            putBackLeftovers(level, data);
        }
        Residents.tick(level, data, now);
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
                    if (!crowned) {
                        hex.enter(Hex.Phase.WARNING, now);
                        data.changed();
                    } else if (hex.town == null || hex.town.isHomeFinished(BlockPos.containing(hex.center)) || now - hex.phaseSince >= FOUNDING_LIMIT) {
                        // the home stands: the Hex bursts out of it
                        hex.enter(Hex.Phase.SPREADING, now);
                        data.changed();
                        play(level, hex.center, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 1.8F);
                        play(level, hex.center, SoundEvents.BEACON_ACTIVATE, 3.0F, 0.7F);
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
                    if (now - hex.phaseSince >= COLLAPSE_TICKS) {
                        fallen.add(hex);
                    }
                }
            }
            if (hex.town != null) {
                hex.town.tick(level, hex.center, wallRadius(hex, now), hex.radius, hex.era, hex.phase == Hex.Phase.COLLAPSING, now);
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
     * Takes down every Hex in a dimension at once, without the fall.
     */
    public static void dispelAll(ServerLevel level) {
        HexData data = HexData.of(level);
        for (Hex hex : List.copyOf(data.all())) {
            takeDownTown(level, data, hex);
            data.remove(hex);
        }
    }

    /**
     * Puts back everything a Hex's town built. What lies in chunks that aren't loaded goes back when they are.
     */
    private static void takeDownTown(ServerLevel level, HexData data, Hex hex) {
        if (hex.town != null) {
            hex.town.restoreAll(level, data.pending()::put);
            data.setDirty();
        }
    }

    private static void putBackLeftovers(ServerLevel level, HexData data) {
        var iterator = data.pending().long2ObjectEntrySet().fastIterator();
        int budget = 2048;
        while (iterator.hasNext() && budget-- > 0) {
            var entry = iterator.next();
            if (HexTown.putBackLeftover(level, entry.getLongKey(), entry.getValue())) {
                iterator.remove();
                data.setDirty();
            }
        }
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
        Services.NETWORK.sendToPlayer(player, new HexSyncPayload(owns, data.snapshots()));
        SYNCED.put(player, new Synced(level.dimension(), data.instance(), data.version(), owns, now));
    }

    private static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private record Synced(ResourceKey<Level> level, long instance, int version, boolean owns, long sentAt) {
    }

    private record ClientView(boolean ownsHex, List<HexSnapshot> hexes) {
        static final ClientView EMPTY = new ClientView(false, List.of());
    }
}
