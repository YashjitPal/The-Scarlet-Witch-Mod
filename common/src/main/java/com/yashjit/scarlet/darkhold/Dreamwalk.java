package com.yashjit.scarlet.darkhold;

import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.config.ScarletServerConfig;
import com.yashjit.scarlet.entity.DreamBody;
import com.yashjit.scarlet.magic.Magic;
import com.yashjit.scarlet.magic.Mastery;
import com.yashjit.scarlet.magic.MindControl;
import com.yashjit.scarlet.magic.Spell;
import com.yashjit.scarlet.magic.Telekinesis;
import com.yashjit.scarlet.mixin.ServerPlayerGameModeInvoker;
import com.yashjit.scarlet.network.DreamOptionsPayload;
import com.yashjit.scarlet.network.DreamPayload;
import com.yashjit.scarlet.network.DreamStatePayload;
import com.yashjit.scarlet.network.MagicEventPayload;
import com.yashjit.scarlet.platform.Services;
import com.yashjit.scarlet.registry.ScarletSounds;
import com.yashjit.scarlet.registry.ScarletTickets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Dreamwalking, the Darkhold's spell, server side. The body sits down cross-legged and rises a little into the air, and
 * the spirit leaves it for a creature somewhere else, in this dimension or another, to look out through it and move as
 * it.
 *
 * <ul>
 *     <li>Where it goes: near where the dreamwalker last stood in that dimension, else by their bed, the world's spawn,
 *     or where a portal from the spawn would come out; into the creature nearest there.</li>
 *     <li>While away: the body stays behind where anything can get at it, and a blow to it snaps the spirit back in time
 *     to defend it. Being away drains energy, and the Darkhold takes more of them the longer it lasts.</li>
 *     <li>It ends when they wake themselves, when the creature dies or goes, when their energy runs out, and when they
 *     leave the game. Whatever ends it, they come back to their body; if the server went down first, they come back
 *     to it the moment they return.</li>
 * </ul>
 *
 * <p>The spirit is the dreamwalker themselves, gone along as a spectator looking out through the creature, so it is the
 * creature's surroundings their game loads and shows. A spectator is seen by no one, touches nothing and can't be hurt,
 * and while it looks out through something the game carries it along.
 */
public final class Dreamwalk {

    /** Ticks the body takes to sit down and rise before the spirit leaves it. */
    public static final int RISE_TICKS = 40;
    /** Energy it takes to begin. */
    public static final float START_ENERGY = 30.0F;
    /** How far around where it arrives the spirit looks for a creature to go into. */
    private static final double SEARCH_RADIUS = 48.0;
    /** How much nearer in height than across the ground counts for, choosing the nearest. */
    private static final double HEIGHT_WEIGHT = 0.25;
    /** How long after the rise the spirit waits for the creatures there to load before giving up. */
    private static final int SEARCH_TICKS = 60;
    private static final float DEPART_CORRUPTION = 0.02F;
    /** Corruption each second away. */
    private static final float AWAY_CORRUPTION = 0.001F;
    private static final int ANCHOR_TICKET_RADIUS = 3;
    private static final int BODY_TICKET_RADIUS = 3;
    private static final int CREATURE_TICKET_RADIUS = 2;
    private static final int REMEMBER_EVERY = 100;
    private static final int RESYNC_TICKS = 20;
    /** How far off hostile creatures notice a body sat alone. */
    private static final double NOTICE_RANGE = 16.0;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Spirits being carried off or brought back right now, who have not set foot where they are going. */
    private static final Set<UUID> TRAVELLING = new HashSet<>();

    private Dreamwalk() {
    }

    /**
     * Whether a player is only a spirit being carried into another dimension or back, so they haven't been there.
     */
    public static boolean travelling(ServerPlayer player) {
        return TRAVELLING.contains(player.getUUID());
    }

    public static void handle(DreamPayload payload, ServerPlayer player) {
        switch (payload.action()) {
            case DreamPayload.ASK -> offer(player);
            case DreamPayload.GO -> payload.dimension().ifPresent(dimension -> begin(player, dimension));
            case DreamPayload.WAKE -> end(player);
            default -> {
            }
        }
    }

    /**
     * Ends the spell, however far it had got.
     */
    private static void end(ServerPlayer player) {
        if (Magic.state(player).channeling(Spell.DREAMWALK)) {
            Magic.stopChannel(player, player.level().getGameTime(), false);
        } else {
            stop(player);
        }
    }

    /**
     * Whether a player's spirit is away from their body, in a creature somewhere.
     */
    public static boolean isAway(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && session.away;
    }

    /**
     * The body a player's spirit has left, if it is away.
     */
    public static @Nullable DreamBody bodyOf(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null ? null : session.body;
    }

    /**
     * Tells the dreamwalker where their spirit could go: every dimension, and where in each it would arrive.
     */
    private static void offer(ServerPlayer player) {
        List<DreamOptionsPayload.Destination> destinations = new ArrayList<>();
        for (ServerLevel level : player.level().getServer().getAllLevels()) {
            destinations.add(new DreamOptionsPayload.Destination(level.dimension(), arrival(player, level).kind()));
        }
        Services.NETWORK.sendToPlayer(player, new DreamOptionsPayload(destinations));
    }

    /**
     * The body sits down and begins to rise, and the ground where the spirit will arrive starts to load.
     */
    private static void begin(ServerPlayer player, ResourceKey<Level> dimension) {
        long now = player.level().getGameTime();
        ServerLevel destination = player.level().getServer().getLevel(dimension);
        if (SESSIONS.containsKey(player.getUUID()) || destination == null || Magic.state(player).channeling()
                || Magic.check(player, Spell.DREAMWALK, now) != Magic.Refusal.NONE) {
            return;
        }
        if (!ScarletServerConfig.get().dreamwalking) {
            player.sendOverlayMessage(Component.translatable("dreamwalk.scarlet.forbidden").withColor(ScarletPalette.SICKLY));
            return;
        }
        SESSIONS.put(player.getUUID(), new Session(destination, arrival(player, destination).pos(), now));
        remember(player);
        Magic.channel(player, Spell.DREAMWALK, now);
        play(player.level(), player.position(), ScarletSounds.DARKHOLD_WHISPER.get(), 0.8F, 0.8F);
        play(player.level(), player.position(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, 0.6F, 0.7F);
        play(player.level(), player.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5F, 0.5F);
    }

    /**
     * Every tick of the spell: rising, then away.
     *
     * @return false once it should end
     */
    public static boolean hold(ServerPlayer player, long now) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !Darkhold.carries(player)) {
            return false;
        }
        return session.away ? stayAway(player, session, now) : rise(player, session, now);
    }

    private static boolean rise(ServerPlayer player, Session session, long now) {
        if (player.isPassenger() || player.isSleeping()) {
            return false;
        }
        keepLoaded(session.destination, session.anchor, ANCHOR_TICKET_RADIUS);
        long since = now - session.since;
        if (since < RISE_TICKS) {
            return true;
        }
        Mob mob = nearestCreature(session.destination, session.anchor);
        if (mob != null) {
            depart(player, session, mob, now);
            return true;
        }
        if (since > RISE_TICKS + SEARCH_TICKS) {
            player.sendOverlayMessage(Component.translatable("dreamwalk.scarlet.nowhere").withColor(ScarletPalette.SICKLY));
            return false;
        }
        return true;
    }

    /**
     * The spirit leaves: the body is left sitting where the dreamwalker sat, and they go as a spectator into the creature.
     */
    private static void depart(ServerPlayer player, Session session, Mob mob, long now) {
        ServerLevel from = player.level();
        Vec3 at = player.position();
        DreamwalkState state = Services.PLAYER_DATA.dreamwalk(player);
        DreamwalkState.Away away = new DreamwalkState.Away(from.dimension(), at, player.getYRot(), player.getXRot(), player.gameMode(),
                Optional.ofNullable(player.gameMode.getPreviousGameModeForPlayer()));
        Services.PLAYER_DATA.setDreamwalk(player, state.stoodAt(from.dimension(), player.blockPosition()).withAway(Optional.of(away)));
        session.body = DreamBody.leave(player);
        session.away = true;
        session.awaySince = now;
        Darkhold.corrupt(player, DEPART_CORRUPTION);
        Services.NETWORK.sendToTrackingAndSelf(player, new MagicEventPayload(player.getId(), MagicEventPayload.DREAM_DEPART, at));
        play(from, at, SoundEvents.SOUL_ESCAPE.value(), 1.0F, 0.6F);
        play(from, at, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.7F, 0.6F);
        player.setGameMode(GameType.SPECTATOR);
        TRAVELLING.add(player.getUUID());
        try {
            player.setCamera(mob);
        } finally {
            TRAVELLING.remove(player.getUUID());
        }
        session.creatureId = mob.getId();
        session.creatureLevel = (ServerLevel) mob.level();
        MindControl.possess(player, mob);
        Services.NETWORK.sendToPlayer(player, new DreamStatePayload(DreamStatePayload.AWAY, mob.getId(), corruption(player)));
        announce(player, mob, DreamStatePayload.POSSESSED);
        Vec3 head = mob.getEyePosition();
        play(session.creatureLevel, head, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.8F, 0.75F);
        play(session.creatureLevel, head, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 0.45F);
        Mastery.grant(player, 5);
    }

    private static boolean stayAway(ServerPlayer player, Session session, long now) {
        Mob mob = session.creature();
        DreamBody body = session.body;
        if (mob == null || body == null || body.isRemoved() || player.getCamera() != mob) {
            return false;
        }
        mob.setNoActionTime(0);
        keepLoaded((ServerLevel) body.level(), body.blockPosition(), BODY_TICKET_RADIUS);
        keepLoaded(session.creatureLevel, mob.blockPosition(), CREATURE_TICKET_RADIUS);
        long away = now - session.awaySince;
        if (away > 0 && away % 20 == 0) {
            Darkhold.corrupt(player, AWAY_CORRUPTION);
            rouse(body);
        }
        if (away > 0 && away % RESYNC_TICKS == 0) {
            // anyone who has just come into view learns whose spirit is in it, and the dreamwalker's game where to look
            announce(player, mob, DreamStatePayload.POSSESSED);
            Services.NETWORK.sendToPlayer(player, new DreamStatePayload(DreamStatePayload.AWAY, mob.getId(), corruption(player)));
        }
        return true;
    }

    /**
     * Called as the spell ends, however it ends. Whoever was away comes back to their body.
     *
     * @return the cooldown the spell should take: always its own, since clients tell from it when the body got up
     */
    public static int stop(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null || !session.away) {
            return Spell.DREAMWALK.cooldown();
        }
        Mob mob = session.creature();
        MindControl.dispossess(player);
        if (mob != null) {
            announce(player, mob, DreamStatePayload.RELEASED);
            Vec3 head = mob.getEyePosition();
            play(session.creatureLevel, head, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.7F, 1.1F);
        }
        Services.NETWORK.sendToPlayer(player, new DreamStatePayload(DreamStatePayload.WOKE, -1, 0.0F));
        DreamBody body = session.body;
        returnTo(player, body);
        if (body != null && !body.isRemoved()) {
            Services.NETWORK.sendToTrackingAndSelf(body, new MagicEventPayload(body.getId(), MagicEventPayload.DREAM_WAKE, body.position()));
            ServerLevel level = (ServerLevel) body.level();
            play(level, body.position(), SoundEvents.BREEZE_INHALE, 0.8F, 0.6F);
            play(level, body.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 0.7F);
            body.discard();
        }
        return Spell.DREAMWALK.cooldown();
    }

    /**
     * Puts the spirit back where its body sits, or where it was left if it has gone, playing as it was before.
     */
    private static void returnTo(ServerPlayer player, @Nullable DreamBody body) {
        DreamwalkState state = Services.PLAYER_DATA.dreamwalk(player);
        DreamwalkState.Away away = state.away().orElse(null);
        Services.PLAYER_DATA.setDreamwalk(player, state.withAway(Optional.empty()));
        if (away == null) {
            return;
        }
        player.setCamera(player);
        if (player.isAlive()) {
            MinecraftServer server = player.level().getServer();
            boolean bodyStands = body != null && !body.isRemoved();
            ServerLevel level = bodyStands ? (ServerLevel) body.level() : server.getLevel(away.dimension());
            if (level == null) {
                level = server.overworld();
            }
            Vec3 at = bodyStands ? body.position() : away.position();
            TRAVELLING.add(player.getUUID());
            try {
                player.teleportTo(level, at.x, at.y, at.z, Set.of(), bodyStands ? body.getYRot() : away.yRot(), away.xRot(), true);
            } finally {
                TRAVELLING.remove(player.getUUID());
            }
        }
        player.setGameMode(away.gameMode());
        ((ServerPlayerGameModeInvoker) player.gameMode).scarlet$setGameModeForPlayer(away.gameMode(), away.previousGameMode().orElse(null));
        player.resetFallDistance();
    }

    /**
     * A blow while still sitting down to it breaks the meditation.
     */
    public static void hurt(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && !session.away) {
            end(player);
        }
    }

    /**
     * A blow to the body snaps the spirit back into it, in time to defend itself.
     */
    public static void bodyStruck(DreamBody body, DamageSource source, float amount) {
        UUID owner = body.owner();
        ServerPlayer player = owner == null ? null : ((ServerLevel) body.level()).getServer().getPlayerList().getPlayer(owner);
        if (player == null || bodyOf(player) != body) {
            body.discard();
            return;
        }
        end(player);
    }

    /**
     * Whatever takes a player out of the game brings their spirit home first, so they are saved in their body.
     */
    public static void onLeave(ServerPlayer player) {
        if (SESSIONS.containsKey(player.getUUID())) {
            end(player);
        }
    }

    /**
     * Every tick of every player: bringing back one whose spirit was away when the server went down, once their game has
     * loaded, and remembering where the rest stand.
     */
    public static void tick(ServerPlayer player) {
        if (SESSIONS.containsKey(player.getUUID())) {
            return;
        }
        if (Services.PLAYER_DATA.dreamwalk(player).away().isPresent()) {
            if (player.connection.hasClientLoaded()) {
                returnTo(player, null);
            }
        } else if (player.tickCount % REMEMBER_EVERY == 0) {
            remember(player);
        }
    }

    private static void remember(ServerPlayer player) {
        if (player.onGround() && !player.isSpectator()) {
            DreamwalkState state = Services.PLAYER_DATA.dreamwalk(player);
            DreamwalkState stood = state.stoodAt(player.level().dimension(), player.blockPosition());
            if (stood != state) {
                Services.PLAYER_DATA.setDreamwalk(player, stood);
            }
        }
    }

    /**
     * Where in a dimension the spirit would arrive: where they last stood there, by their bed, by the world's spawn, or
     * failing all those where a portal from the spawn would come out (or the End's landing).
     */
    private static Arrival arrival(ServerPlayer player, ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        if (dimension != player.level().dimension()) {
            BlockPos stood = Services.PLAYER_DATA.dreamwalk(player).lastStood().get(dimension);
            if (stood != null) {
                return new Arrival(stood, DreamOptionsPayload.LAST_STOOD);
            }
        }
        ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
        if (respawn != null && respawn.respawnData().dimension() == dimension) {
            return new Arrival(respawn.respawnData().pos(), DreamOptionsPayload.RESPAWN);
        }
        LevelData.RespawnData spawn = level.getServer().getRespawnData();
        if (spawn.dimension() == dimension) {
            return new Arrival(spawn.pos(), DreamOptionsPayload.WORLD_SPAWN);
        }
        if (dimension == Level.END) {
            return new Arrival(ServerLevel.END_SPAWN_POINT, DreamOptionsPayload.UNKNOWN);
        }
        ServerLevel spawnLevel = level.getServer().getLevel(spawn.dimension());
        double scale = spawnLevel == null ? 1.0 : DimensionType.getTeleportationScale(spawnLevel.dimensionType(), level.dimensionType());
        BlockPos at = level.getWorldBorder().clampToBounds(spawn.pos().getX() * scale, spawn.pos().getY(), spawn.pos().getZ() * scale);
        return new Arrival(at, DreamOptionsPayload.UNKNOWN);
    }

    /**
     * The creature nearest where the spirit arrives that it can go into: not one already held, carried or ridden, nor
     * one too great to be taken, like a boss. Nearest across the ground, at any height, since a place never visited is
     * only known across the ground.
     */
    private static @Nullable Mob nearestCreature(ServerLevel level, BlockPos anchor) {
        Vec3 center = Vec3.atCenterOf(anchor);
        AABB around = new AABB(center.x - SEARCH_RADIUS, level.getMinY(), center.z - SEARCH_RADIUS,
                center.x + SEARCH_RADIUS, level.getMaxY() + 1, center.z + SEARCH_RADIUS);
        Mob best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, around, Dreamwalk::canPossess)) {
            double dx = mob.getX() - center.x;
            double dy = (mob.getY() - center.y) * HEIGHT_WEIGHT;
            double dz = mob.getZ() - center.z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = mob;
            }
        }
        return best;
    }

    private static boolean canPossess(Mob mob) {
        return mob.isAlive() && !mob.typeHolder().is(MindControl.RESISTS) && !MindControl.steers(mob) && !Telekinesis.isHeld(mob)
                && !mob.isVehicle() && !mob.isPassenger() && mob.level().getWorldBorder().isWithinBounds(mob.getBoundingBox());
    }

    /**
     * Hostile creatures that catch sight of a body sat alone go for it.
     */
    private static void rouse(DreamBody body) {
        ServerLevel level = (ServerLevel) body.level();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, body.getBoundingBox().inflate(NOTICE_RANGE),
                mob -> mob instanceof Enemy && mob.isAlive() && mob.getTarget() == null && !MindControl.steers(mob))) {
            if (mob.canAttack(body) && mob.hasLineOfSight(body)) {
                mob.setTarget(body);
            }
        }
    }

    private static void keepLoaded(ServerLevel level, BlockPos at, int radius) {
        level.getChunkSource().addTicketWithRadius(ScarletTickets.DREAMWALK.get(), ChunkPos.containing(at), radius);
    }

    private static void announce(ServerPlayer player, Mob mob, int stage) {
        DreamStatePayload payload = new DreamStatePayload(stage, mob.getId(), corruption(player));
        Services.NETWORK.sendToTrackingAndSelf(mob, payload);
    }

    private static float corruption(ServerPlayer player) {
        return Darkhold.corruption(player, player.level().getGameTime());
    }

    private static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private record Arrival(BlockPos pos, int kind) {
    }

    private static final class Session {
        final ServerLevel destination;
        /** Where in the destination the spirit arrives. */
        final BlockPos anchor;
        final long since;
        boolean away;
        long awaySince;
        @Nullable DreamBody body;
        int creatureId = -1;
        @Nullable ServerLevel creatureLevel;

        Session(ServerLevel destination, BlockPos anchor, long since) {
            this.destination = destination;
            this.anchor = anchor;
            this.since = since;
        }

        @Nullable Mob creature() {
            return creatureLevel != null && creatureLevel.getEntity(creatureId) instanceof Mob mob && mob.isAlive() ? mob : null;
        }
    }
}
