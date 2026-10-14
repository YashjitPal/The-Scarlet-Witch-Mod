package com.yashjit.scarlet.hex;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.network.ResidentsPayload;
import com.yashjit.scarlet.platform.Services;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The people of a Hex. Every hostile mob that comes inside is rewritten into a townsperson: the same mob underneath,
 * so nothing is ever lost or duplicated, but one that never attacks and never explodes, and that nothing attacks.
 *
 * <ul>
 *     <li>Hit one and the spell breaks for it: it turns back into what it really is and fights back, and stays
 *     itself until it has left the Hex. The woken go after players before anyone, and with no player about, after
 *     the townspeople, any of whom they hurt wakes too and joins them. They never turn on each other.</li>
 *     <li>One that walks out turns back at the wall; when the Hex falls, each turns back as the wall rushes past.</li>
 *     <li>Which mobs can live in a Hex is the {@code scarlet:hex_residents} entity type tag. Animals stay animals and
 *     bosses resist it.</li>
 * </ul>
 *
 * <p>Who is a resident is kept on the mob itself, as an entity tag, so it survives saving; clients are told which
 * mobs to draw as townspeople.
 */
public final class Residents {

    public static final String RESIDENT = "scarlet.resident";
    /** Broken free of the spell, until it leaves the Hex. */
    public static final String AWAKE = "scarlet.awake";
    public static final TagKey<EntityType<?>> CAN_LIVE_HERE = TagKey.create(Registries.ENTITY_TYPE, Scarlet.id("hex_residents"));

    private static final int SCAN_INTERVAL = 10;
    /** How far a woken mob looks for townspeople to go after, and how far off it keeps after one it is chasing. */
    private static final double HUNT_REACH = 16.0;
    private static final double CHASE_REACH = 24.0;
    /** How near a player a woken mob knows they are there without seeing them. */
    private static final double HEARS = 8.0;
    /**
     * How often anyone in a Hex might find someone new about the town, and how likely each time; how many townspeople
     * may be about them before none do; and how near counts as about them, and as too near to turn up unseen.
     */
    private static final int ARRIVAL_INTERVAL = 100;
    private static final float ARRIVAL_CHANCE = 0.35F;
    private static final int MOST_ABOUT = 6;
    private static final double ABOUT = 48.0;
    private static final double TOO_NEAR = 16.0;
    /** How likely a townsperson its caster can't see jerks its head each scan, and how far. */
    private static final float TWITCH_CHANCE = 0.12F;
    private static final float TWITCH = 28.0F;

    /** Each dimension's residents as last found, and a count of changes for knowing who needs telling. */
    private static final Map<ResourceKey<Level>, Found> FOUND = new HashMap<>();
    private static final Map<ServerPlayer, Sent> SENT = new WeakHashMap<>();

    private Residents() {
    }

    public static boolean isResident(Entity entity) {
        return entity.entityTags().contains(RESIDENT);
    }

    public static boolean isAwake(Entity entity) {
        return entity.entityTags().contains(AWAKE);
    }

    /**
     * Rewrites whoever has come inside a Hex and lets go of whoever has left.
     */
    static void tick(ServerLevel level, HexData data, long now) {
        if (now % SCAN_INTERVAL != 0) {
            return;
        }
        Found found = FOUND.computeIfAbsent(level.dimension(), key -> new Found());
        IntOpenHashSet inside = new IntOpenHashSet();
        IntOpenHashSet paused = new IntOpenHashSet();
        List<Mob> woken = new ArrayList<>();
        List<Hex> standing = new ArrayList<>();
        List<Float> walls = new ArrayList<>();
        List<Sight> sights = new ArrayList<>();
        for (Hex hex : data.all()) {
            float wall = Hexes.wallRadius(hex, now);
            if (wall >= 1.0F) {
                standing.add(hex);
                walls.add(wall);
                sights.add(Sight.of(level, hex));
            }
        }
        if (!standing.isEmpty()) {
            // a Hex can be as tall as the world and hundreds of blocks across: going through everything once costs
            // less than searching a box that size
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof Mob mob) || !mob.isAlive() || !mob.is(CAN_LIVE_HERE)) {
                    continue;
                }
                if (isAwake(mob)) {
                    woken.add(mob);
                    continue;
                }
                for (int i = 0; i < standing.size(); i++) {
                    if (HexShape.contains(standing.get(i).center, walls.get(i), mob.position())) {
                        if (!isResident(mob)) {
                            rewrite(level, mob);
                        }
                        // once a townsperson, they have their days to live, ones loaded back in from a save too
                        Sitcom.attach(mob);
                        inside.add(mob.getId());
                        if (!sights.get(i).sees(mob.position())) {
                            paused.add(mob.getId());
                            if (level.getRandom().nextFloat() < TWITCH_CHANCE) {
                                // stuck, the way a scene skips: a jerk of the head, and still again
                                mob.setYHeadRot(mob.getYRot() + (level.getRandom().nextBoolean() ? TWITCH : -TWITCH));
                            }
                        }
                        break;
                    }
                }
            }
        }
        if (!paused.equals(found.paused)) {
            found.paused = paused;
            found.version++;
        }
        // whoever was here last time and isn't now has crossed the wall, or the wall has crossed them
        for (int id : found.ids.toIntArray()) {
            if (!inside.contains(id) && level.getEntity(id) instanceof Mob mob && isResident(mob)) {
                turnBack(level, mob);
            }
        }
        if (!inside.equals(found.ids)) {
            found.ids = inside;
            found.version++;
        }
        for (Mob mob : woken) {
            hunt(level, mob);
        }
        if (now % ARRIVAL_INTERVAL == 0 && !standing.isEmpty()) {
            arrive(level, standing, walls, found);
        }
    }

    /**
     * Whether a mob is kept from turning up in a Hex the way monsters do, out of the dark, wherever it is dark enough,
     * which is on a roof as like as not: townspeople turn up in the town's own way, see {@link #arrive}.
     */
    public static boolean keptFromSpawning(Mob mob, LevelAccessor level, EntitySpawnReason reason) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.PATROL && reason != EntitySpawnReason.REINFORCEMENT
                || !mob.is(CAN_LIVE_HERE) || !(level instanceof ServerLevelAccessor accessor)) {
            return false;
        }
        ServerLevel server = accessor.getLevel();
        long now = server.getGameTime();
        for (Hex hex : HexData.of(server).all()) {
            if (HexShape.contains(hex.center, Hexes.wallRadius(hex, now), mob.position())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Now and then someone new about the town, near whoever is in a Hex but out of their sight: at home in one of its
     * houses, or out on its sidewalks, walks and lawns, a few about anyone at most. What they are underneath is what
     * the land would have brought out of the dark there, a husk in a desert, a stray in the snow; they are townspeople
     * from the moment they are there.
     */
    private static void arrive(ServerLevel level, List<Hex> standing, List<Float> walls, Found found) {
        if (level.getDifficulty() == Difficulty.PEACEFUL || !level.getGameRules().get(GameRules.SPAWN_MOBS)) {
            return;
        }
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || random.nextFloat() >= ARRIVAL_CHANCE) {
                continue;
            }
            Hex around = null;
            for (int i = 0; i < standing.size() && around == null; i++) {
                if (standing.get(i).town != null && HexShape.contains(standing.get(i).center, walls.get(i), player.position())) {
                    around = standing.get(i);
                }
            }
            if (around == null || level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(ABOUT), Residents::isResident).size() >= MOST_ABOUT) {
                continue;
            }
            BlockPos at = around.town.spawnSpot(level, player.position(), TOO_NEAR, ABOUT, random);
            if (at == null || seen(level, Vec3.atCenterOf(at).add(0.0, 0.5, 0.0))) {
                continue;
            }
            EntityType<?> type = arrivalAt(level, at, random);
            if (type == null || !(type.create(level, EntitySpawnReason.EVENT) instanceof Mob mob)) {
                continue;
            }
            mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
            if (!level.noCollision(mob)) {
                mob.discard();
                continue;
            }
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
            mob.addTag(RESIDENT);
            level.addFreshEntity(mob);
            Sitcom.attach(mob);
            if (found.ids.add(mob.getId())) {
                found.version++;
            }
        }
    }

    /** Whether anyone would see a spot, too near it or with nothing between them. */
    private static boolean seen(ServerLevel level, Vec3 spot) {
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            Vec3 eyes = player.getEyePosition();
            if (eyes.distanceToSqr(spot) < TOO_NEAR * TOO_NEAR || eyes.distanceToSqr(spot) < ABOUT * ABOUT * 4.0
                    && level.clip(new ClipContext(eyes, spot, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
    }

    /** What the land would bring out of the dark at a spot, of what can live in a Hex. */
    private static @Nullable EntityType<?> arrivalAt(ServerLevel level, BlockPos at, RandomSource random) {
        List<Weighted<MobSpawnSettings.SpawnerData>> kinds = new ArrayList<>();
        int total = 0;
        for (Weighted<MobSpawnSettings.SpawnerData> kind
                : level.getChunkSource().getGenerator().getMobsAt(level, level.structureManager(), MobCategory.MONSTER, at).unwrap()) {
            if (kind.value().type().builtInRegistryHolder().is(CAN_LIVE_HERE)) {
                kinds.add(kind);
                total += kind.weight();
            }
        }
        if (total <= 0) {
            return EntityTypes.ZOMBIE;
        }
        int pick = random.nextInt(total);
        for (Weighted<MobSpawnSettings.SpawnerData> kind : kinds) {
            pick -= kind.weight();
            if (pick < 0) {
                return kind.value().type();
            }
        }
        return EntityTypes.ZOMBIE;
    }

    /**
     * Picks who a mob woken from the spell goes after: a player before anyone, keeping one it already has, or else the
     * nearest it can see, or is near enough to know is there; and only with no player about, a townsperson, the one it
     * is chasing or the nearest it can see. One that has woken too is let go.
     */
    private static void hunt(ServerLevel level, Mob mob) {
        LivingEntity target = mob.getTarget();
        if (target instanceof Player player && huntable(mob, player)) {
            return;
        }
        double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        Player nearest = null;
        double best = range * range;
        for (ServerPlayer player : level.players()) {
            double distance = player.distanceToSqr(mob);
            if (distance < best && huntable(mob, player) && (distance <= HEARS * HEARS || mob.getSensing().hasLineOfSight(player))) {
                best = distance;
                nearest = player;
            }
        }
        if (nearest != null) {
            mob.setTarget(nearest);
            return;
        }
        if (target != null && target.isAlive() && isResident(target) && target.distanceToSqr(mob) <= CHASE_REACH * CHASE_REACH) {
            return;
        }
        Mob prey = null;
        best = HUNT_REACH * HUNT_REACH;
        for (Mob other : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(HUNT_REACH), Residents::isResident)) {
            double distance = other.distanceToSqr(mob);
            if (distance < best && other.isAlive() && mob.getSensing().hasLineOfSight(other)) {
                best = distance;
                prey = other;
            }
        }
        if (prey != null) {
            mob.setTarget(prey);
        } else if (target != null && !(target instanceof Player)) {
            mob.setTarget(null);
        }
    }

    private static boolean huntable(Mob mob, Player player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator() && player.level() == mob.level();
    }

    /**
     * Each resident checks now and then that it is still inside a Hex, so ones loaded back in after their Hex has gone,
     * or that slipped out between scans, turn back too. The awake forget once they are out.
     */
    public static void checkStillInside(ServerLevel level, Mob mob) {
        boolean resident = isResident(mob);
        if (!resident && !isAwake(mob)) {
            return;
        }
        long now = level.getGameTime();
        for (Hex hex : HexData.of(level).all()) {
            if (HexShape.contains(hex.center, Hexes.wallRadius(hex, now), mob.position())) {
                return;
            }
        }
        if (resident) {
            turnBack(level, mob);
        }
        mob.removeTag(AWAKE);
    }

    private static void rewrite(ServerLevel level, Mob mob) {
        mob.addTag(RESIDENT);
        mob.setTarget(null);
        mob.clearFire();
        if (mob instanceof NeutralMob neutral) {
            neutral.stopBeingAngry();
        }
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.6F, 1.4F);
    }

    private static void turnBack(ServerLevel level, Mob mob) {
        mob.removeTag(RESIDENT);
        Sitcom.detach(mob);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 0.7F, 0.6F);
    }

    /**
     * Hitting a resident breaks the spell for it: it turns back into what it really is, and turns on the player who hit
     * it. Hurt by anything else, one of the woken most likely, it joins them, and goes after a player rather than its
     * own kind; whatever hit it lets it go.
     */
    public static void struck(Mob mob, @Nullable Entity attacker) {
        if (!isResident(mob) || attacker == null || attacker == mob) {
            return;
        }
        mob.removeTag(RESIDENT);
        mob.addTag(AWAKE);
        Sitcom.detach(mob);
        mob.setTarget(null);
        if (attacker instanceof Player player) {
            if (!player.isCreative() && !player.isSpectator()) {
                mob.setTarget(player);
            }
        } else if (attacker instanceof Mob hunter && hunter.getTarget() == mob) {
            hunter.setTarget(null);
        }
        if (mob.level() instanceof ServerLevel level) {
            if (!(attacker instanceof Player)) {
                hunt(level, mob);
            }
            Found found = FOUND.get(level.dimension());
            if (found != null && found.ids.remove(mob.getId())) {
                found.version++;
            }
            Vec3 at = mob.position();
            level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 1.0F, 0.5F);
        }
    }

    /**
     * What a townsperson sounds like, in place of the monster underneath: no groans, hisses, rattles or clicks, just
     * footsteps like anyone's on whatever they walk on, a gulp like anyone's when they drink, and a person's cry when
     * they're hurt or killed. Splashes, swimming and falls sound as they would for a player. Hit one and its own voice
     * comes back with its true self.
     *
     * @return whether the sound was taken over, so isn't to be played as it was
     */
    public static boolean voice(Entity entity, SoundEvent sound, float volume, float pitch) {
        String path = sound.location().getPath();
        if (!path.startsWith("entity.") || path.startsWith("entity.player.") || path.startsWith("entity.generic.")) {
            return false;
        }
        if (path.endsWith(".step")) {
            BlockState below = entity.level().getBlockState(entity.blockPosition().below());
            SoundType type = below.getSoundType();
            entity.playSound(type.getStepSound(), type.getVolume() * 0.15F, type.getPitch());
        } else if (path.startsWith("entity.hostile.")) {
            // the monster versions of what a player sounds like
            BuiltInRegistries.SOUND_EVENT.get(Identifier.withDefaultNamespace("entity.player." + path.substring("entity.hostile.".length())))
                    .ifPresent(human -> entity.playSound(human.value(), volume, pitch));
        } else if (path.endsWith(".drink")) {
            entity.playSound(SoundEvents.GENERIC_DRINK.value(), volume, pitch);
        } else if (path.contains(".hurt")) {
            entity.playSound(SoundEvents.PLAYER_HURT, volume, pitch);
        } else if (path.endsWith(".death")) {
            entity.playSound(SoundEvents.PLAYER_DEATH, volume, pitch);
        }
        return true;
    }

    /**
     * Whether a mob is kept from targeting another: residents mean no one harm, and none but the woken mean them any;
     * the woken never turn on each other.
     */
    public static boolean keepsPeace(Mob mob, @Nullable LivingEntity target) {
        return target != null && (isResident(mob) || isResident(target) && !isAwake(mob) || isAwake(mob) && isAwake(target));
    }

    /**
     * Tells a player which mobs around them are townspeople, whenever that changes or they arrive.
     */
    public static void syncIfNeeded(ServerPlayer player) {
        ServerLevel level = player.level();
        Found found = FOUND.computeIfAbsent(level.dimension(), key -> new Found());
        boolean unwatched = unwatched(level, player);
        Sent last = SENT.get(player);
        if (last != null && last.level() == level.dimension() && last.version() == found.version && last.found() == found
                && last.unwatched() == unwatched) {
            return;
        }
        Services.NETWORK.sendToPlayer(player, new ResidentsPayload(found.ids.toIntArray(), found.paused.toIntArray(), unwatched));
        SENT.put(player, new Sent(level.dimension(), found, found.version, unwatched));
    }

    /**
     * Whether a townsperson stands out past where its Hex's caster can see: there the show has stopped, and it stands
     * stock still where it was.
     */
    public static boolean isPaused(Mob mob) {
        Found found = FOUND.get(mob.level().dimension());
        return found != null && found.paused.contains(mob.getId()) && isResident(mob);
    }

    /** Holds a paused townsperson just as it stands. */
    public static void hold(Mob mob) {
        mob.getNavigation().stop();
        mob.setXxa(0.0F);
        mob.setYya(0.0F);
        mob.setZza(0.0F);
        mob.setJumping(false);
    }

    /** Whether a player stands in someone else's Hex out past where its caster can see. */
    private static boolean unwatched(ServerLevel level, ServerPlayer player) {
        long now = level.getGameTime();
        for (Hex hex : HexData.of(level).all()) {
            if (!hex.caster.equals(player.getUUID()) && HexShape.contains(hex.center, Hexes.wallRadius(hex, now), player.position())) {
                return !Sight.of(level, hex).sees(player.position());
            }
        }
        return false;
    }

    /**
     * Where a Hex's caster can see: as far as the world is drawn for them, around where they stand; nowhere while they
     * are away from it. Out past that, the show has stopped.
     */
    private record Sight(@Nullable Vec3 from, double reach) {

        static Sight of(ServerLevel level, Hex hex) {
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(hex.caster);
            if (caster == null || caster.level() != level) {
                return new Sight(null, 0.0);
            }
            int chunks = Math.min(level.getServer().getPlayerList().getViewDistance(), Math.max(2, caster.requestedViewDistance()));
            return new Sight(caster.position(), chunks * 16.0);
        }

        boolean sees(Vec3 at) {
            if (from == null) {
                return false;
            }
            double dx = at.x - from.x;
            double dz = at.z - from.z;
            return dx * dx + dz * dz <= reach * reach;
        }
    }

    private static final class Found {
        IntOpenHashSet ids = new IntOpenHashSet();
        /** Those out past where their Hex's caster can see. */
        IntOpenHashSet paused = new IntOpenHashSet();
        int version;
    }

    private record Sent(ResourceKey<Level> level, Found found, int version, boolean unwatched) {
    }
}
