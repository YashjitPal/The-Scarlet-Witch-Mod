package com.yashjit.scarlet.hex;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.network.ResidentsPayload;
import com.yashjit.scarlet.platform.Services;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The people of a Hex. Every hostile mob that comes inside is rewritten into a townsperson: the same mob underneath,
 * so nothing is ever lost or duplicated, but one that never attacks and never explodes, and that nothing attacks.
 *
 * <ul>
 *     <li>Hit one and the spell breaks for it: it turns back into what it really is and fights back, and stays
 *     itself until it has left the Hex.</li>
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
        for (Hex hex : data.all()) {
            float wall = Hexes.wallRadius(hex, now);
            if (wall < 1.0F) {
                continue;
            }
            double reach = HexShape.reach(wall);
            AABB box = new AABB(hex.center.x - reach, level.getMinY(), hex.center.z - reach, hex.center.x + reach, level.getMaxY() + 1,
                    hex.center.z + reach);
            for (Mob mob : level.getEntitiesOfClass(Mob.class, box, mob -> mob.isAlive() && mob.is(CAN_LIVE_HERE))) {
                if (!HexShape.contains(hex.center, wall, mob.position()) || isAwake(mob)) {
                    continue;
                }
                if (!isResident(mob)) {
                    rewrite(level, mob);
                }
                inside.add(mob.getId());
            }
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
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 0.7F, 0.6F);
    }

    /**
     * Hitting a resident breaks the spell for it: it turns back into what it really is, and turns on whoever hit it.
     */
    public static void struck(Mob mob, @Nullable Entity attacker) {
        if (!isResident(mob) || attacker == null || attacker == mob) {
            return;
        }
        mob.removeTag(RESIDENT);
        mob.addTag(AWAKE);
        if (attacker instanceof LivingEntity living && !(living instanceof Player player && (player.isCreative() || player.isSpectator()))) {
            mob.setTarget(living);
        }
        if (mob.level() instanceof ServerLevel level) {
            Found found = FOUND.get(level.dimension());
            if (found != null && found.ids.remove(mob.getId())) {
                found.version++;
            }
            Vec3 at = mob.position();
            level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 1.0F, 0.5F);
        }
    }

    /**
     * Whether a mob is kept from targeting another: residents mean no one harm, and no one means them any.
     */
    public static boolean keepsPeace(Mob mob, @Nullable LivingEntity target) {
        return target != null && (isResident(mob) || isResident(target));
    }

    /**
     * Tells a player which mobs around them are townspeople, whenever that changes or they arrive.
     */
    public static void syncIfNeeded(ServerPlayer player) {
        ServerLevel level = player.level();
        Found found = FOUND.computeIfAbsent(level.dimension(), key -> new Found());
        Sent last = SENT.get(player);
        if (last != null && last.level() == level.dimension() && last.version() == found.version && last.found() == found) {
            return;
        }
        Services.NETWORK.sendToPlayer(player, new ResidentsPayload(found.ids.toIntArray()));
        SENT.put(player, new Sent(level.dimension(), found, found.version));
    }

    private static final class Found {
        IntOpenHashSet ids = new IntOpenHashSet();
        int version;
    }

    private record Sent(ResourceKey<Level> level, Found found, int version) {
    }
}
