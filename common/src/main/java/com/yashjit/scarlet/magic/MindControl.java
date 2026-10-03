package com.yashjit.scarlet.magic;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.config.ScarletServerConfig;
import com.yashjit.scarlet.network.ControlPayload;
import com.yashjit.scarlet.network.PossessPayload;
import com.yashjit.scarlet.network.PuppetPayload;
import com.yashjit.scarlet.platform.Services;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Mind Control, server side: reaching into a creature's mind and steering it from inside.
 *
 * <p>Held on a creature, the cast sends scarlet tendrils into its head, and it stands entranced, turned to the caster.
 * A moment later the caster's view moves into it and they steer it themselves: walking, sprinting, jumping, flying if
 * it flies, and attacking the way it does, a skeleton loosing arrows, a creeper going off, a ghast or a blaze throwing
 * fire. Their own body stands channeling where they left it, and a blow to it snaps them back. Let go, and the creature
 * is itself again, but stays loyal for a while: it never turns on the caster, and goes after whoever they fight.
 *
 * <p>A player is held only for a few seconds, and can struggle free with their own keys. Their own game moves them,
 * playing the caster's controls. Servers can forbid holding players at all.
 */
public final class MindControl {

    public static final double RANGE = 20.0;
    /** How far what is held can wander from the caster's body before the hold snaps. */
    public static final double LEASH = 40.0;
    /** Ticks the tendrils take to reach into a mind before the caster's view moves into it. */
    public static final int SEIZE_TICKS = 14;
    public static final int LOYAL_TICKS = 1200;
    /** Presses of their own keys a held player needs to struggle free. */
    public static final int STRUGGLE_PRESSES = 12;
    public static final TagKey<EntityType<?>> RESISTS = TagKey.create(Registries.ENTITY_TYPE, Scarlet.id("resists_mind_control"));

    /** Cooldown after holding a player, rather than the spell's own, and after taking hold of nothing at all. */
    private static final int PLAYER_COOLDOWN = 600;
    private static final int MISSED_COOLDOWN = 10;
    private static final int MELEE_COOLDOWN = 10;
    private static final int RESYNC_TICKS = 20;
    /** How fast what is held walks, as the share of a player's walk it is pushed by, and sprinting on top of that. */
    private static final float WALK = 0.1F;
    private static final float SPRINT = 1.35F;
    /** How far a loyal creature looks for whoever its master is fighting. */
    private static final double LOYAL_REACH = 24.0;

    private static final Map<UUID, Link> LINKS = new HashMap<>();
    /** The same links, by the id of what each holds, for the creatures' own ticks. */
    private static final Int2ObjectMap<Link> HELD = new Int2ObjectOpenHashMap<>();
    private static final Map<UUID, Loyalty> LOYAL = new HashMap<>();

    private MindControl() {
    }

    static void start(ServerPlayer caster, long now) {
        Link link = new Link(caster.getUUID(), caster.level());
        LINKS.put(caster.getUUID(), link);
        reach(caster, link, now);
    }

    /**
     * Every tick of the channel: reaches for a mind under the crosshair until it has hold of one, then keeps hold of it.
     *
     * @return false once the hold has ended by itself and the channel should stop
     */
    static boolean hold(ServerPlayer caster, long now) {
        Link link = LINKS.get(caster.getUUID());
        if (link == null) {
            return false;
        }
        if (link.targetId == Link.NOTHING) {
            reach(caster, link, now);
            return true;
        }
        LivingEntity target = target(caster, link);
        if (target == null || target.distanceTo(caster) > LEASH || target instanceof Player held && (link.struggles >= STRUGGLE_PRESSES
                || now - link.since > SEIZE_TICKS + ScarletServerConfig.get().mindControlPlayerSeconds * 20L || held.isSpectator())) {
            return false;
        }
        if (!link.inside && now - link.since >= SEIZE_TICKS) {
            link.inside = true;
            link.input = new ControlPayload(0.0F, 0.0F, 0, target.getYHeadRot(), target.getXRot());
            announce(caster, link, PossessPayload.INSIDE);
            Vec3 head = target.getEyePosition();
            play(link.level, head, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.8F, 0.7F);
            play(link.level, head, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 0.55F);
        } else if ((now - link.since) % RESYNC_TICKS == 0) {
            // anyone who has just come into view learns whose mind is held
            announce(caster, link, link.inside ? PossessPayload.INSIDE : PossessPayload.SEIZING);
        }
        if (target instanceof Mob mob) {
            mob.setNoActionTime(0);
        } else if (target instanceof ServerPlayer held && link.inside) {
            Services.NETWORK.sendToPlayer(held, new PuppetPayload(link.input));
        }
        return true;
    }

    /**
     * Lets go of whatever mind is held: the creature becomes itself again, loyal to the caster for a while.
     *
     * @return the cooldown the spell should take, or -1 for its own
     */
    static int stop(ServerPlayer caster) {
        Link link = LINKS.remove(caster.getUUID());
        if (link == null) {
            return -1;
        }
        HELD.remove(link.targetId);
        if (link.targetId == Link.NOTHING) {
            return MISSED_COOLDOWN;
        }
        LivingEntity target = target(caster, link);
        announce(caster, link, PossessPayload.RELEASED);
        if (target == null) {
            return -1;
        }
        Vec3 head = target.getEyePosition();
        play(link.level, head, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.7F, 1.25F);
        play(link.level, head, SoundEvents.BEACON_DEACTIVATE, 0.5F, 1.7F);
        if (target instanceof Mob mob && link.inside && !(mob instanceof Creeper creeper && creeper.isIgnited())) {
            LOYAL.put(mob.getUUID(), new Loyalty(caster.getUUID(), link.level, mob.getId(), link.level.getGameTime() + LOYAL_TICKS));
            Services.NETWORK.sendToTrackingAndSelf(caster, new PossessPayload(caster.getId(), mob.getId(), PossessPayload.LOYAL));
        }
        return target instanceof Player ? PLAYER_COOLDOWN : -1;
    }

    /**
     * A dreamwalker's spirit arriving in a creature: inside it at once, with nothing reaching in first, and steering it
     * the way Mind Control does for as long as it stays.
     */
    public static void possess(ServerPlayer spirit, Mob mob) {
        dispossess(spirit);
        Link link = new Link(spirit.getUUID(), (ServerLevel) mob.level());
        link.targetId = mob.getId();
        link.since = mob.level().getGameTime();
        link.inside = true;
        link.dream = true;
        link.input = new ControlPayload(0.0F, 0.0F, 0, mob.getYHeadRot(), mob.getXRot());
        LINKS.put(spirit.getUUID(), link);
        HELD.put(mob.getId(), link);
        LOYAL.remove(mob.getUUID());
    }

    /**
     * The spirit leaving the creature it was in, which is itself again.
     */
    public static void dispossess(ServerPlayer spirit) {
        Link link = LINKS.get(spirit.getUUID());
        if (link != null && link.dream) {
            LINKS.remove(spirit.getUUID());
            HELD.remove(link.targetId);
        }
    }

    /**
     * What a caster has hold of, if anything: for showing whose controls are being steered.
     */
    public static @Nullable LivingEntity held(ServerPlayer caster) {
        Link link = LINKS.get(caster.getUUID());
        return link == null ? null : target(caster, link);
    }

    /**
     * The caster's controls, as they steer what they hold. An attack is carried out at once.
     */
    public static void handle(ServerPlayer caster, ControlPayload control) {
        Link link = LINKS.get(caster.getUUID());
        if (link == null || !link.inside || !Magic.state(caster).channeling(link.dream ? Spell.DREAMWALK : Spell.MIND_CONTROL)) {
            return;
        }
        float pitch = Mth.clamp(control.pitch(), -90.0F, 90.0F);
        link.input = new ControlPayload(Mth.clamp(control.forward(), -1.0F, 1.0F), Mth.clamp(control.strafe(), -1.0F, 1.0F),
                control.flags() & ~ControlPayload.ATTACK, Mth.wrapDegrees(control.yaw()), pitch);
        LivingEntity target = target(caster, link);
        if (target != null && control.has(ControlPayload.ATTACK)) {
            attack(caster, link, target);
        }
    }

    /**
     * A held player fighting it: enough of them and they are free.
     */
    public static void struggle(ServerPlayer held) {
        for (Link link : LINKS.values()) {
            if (link.targetId == held.getId() && link.level == held.level()) {
                link.struggles++;
                if (link.struggles % 3 == 0) {
                    play(link.level, held.getEyePosition(), SoundEvents.AMETHYST_BLOCK_HIT, 0.6F, 0.6F + link.struggles * 0.05F);
                }
            }
        }
    }

    /**
     * A blow to the caster's body snaps them back into it.
     */
    public static void hurt(ServerPlayer caster) {
        Link link = LINKS.get(caster.getUUID());
        if (link != null && link.inside && !link.dream) {
            Magic.stopChannel(caster, caster.level().getGameTime(), false);
        }
    }

    /**
     * Whether a creature's own mind is set aside this tick, entranced or steered by whoever holds it.
     */
    public static boolean steers(Mob mob) {
        return !HELD.isEmpty() && HELD.containsKey(mob.getId());
    }

    /**
     * In place of the creature's own thinking: standing entranced, turned to the caster, or moving as the caster steers.
     */
    public static void steer(Mob mob) {
        Link link = HELD.get(mob.getId());
        if (link == null) {
            return;
        }
        mob.getNavigation().stop();
        mob.setTarget(null);
        if (!link.inside) {
            mob.setXxa(0.0F);
            mob.setYya(0.0F);
            mob.setZza(0.0F);
            mob.setJumping(false);
            ServerPlayer caster = mob.level().getServer() == null ? null : mob.level().getServer().getPlayerList().getPlayer(link.caster);
            if (caster != null) {
                mob.getLookControl().setLookAt(caster, 30.0F, 30.0F);
                mob.getLookControl().tick();
            }
            return;
        }
        ControlPayload input = link.input;
        mob.setYRot(input.yaw());
        mob.setYHeadRot(input.yaw());
        mob.setYBodyRot(input.yaw());
        mob.setXRot(input.pitch());
        double speed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        float push = WALK * Mth.clamp((float) speed / 0.25F, 0.6F, 1.4F) * (input.has(ControlPayload.SPRINT) ? SPRINT : 1.0F);
        mob.setSpeed(push);
        mob.setZza(input.forward());
        mob.setXxa(input.strafe());
        if (mob.isNoGravity()) {
            // flying: up and down with jump and sneak, and climbing or diving along the look while moving
            float climb = (input.has(ControlPayload.JUMP) ? 1.0F : 0.0F) - (input.has(ControlPayload.SNEAK) ? 1.0F : 0.0F)
                    - Mth.sin(input.pitch() * Mth.DEG_TO_RAD) * input.forward();
            mob.setYya(Mth.clamp(climb, -1.0F, 1.0F));
            mob.setJumping(false);
        } else {
            mob.setYya(0.0F);
            mob.setJumping(input.has(ControlPayload.JUMP));
        }
    }

    /**
     * Whether a loyal creature refuses to turn on someone: its master, while its loyalty lasts.
     */
    public static boolean keepsFaith(Mob mob, @Nullable LivingEntity target) {
        if (target == null || LOYAL.isEmpty()) {
            return false;
        }
        Loyalty loyalty = LOYAL.get(mob.getUUID());
        return loyalty != null && target.getUUID().equals(loyalty.master()) && mob.level().getGameTime() < loyalty.until();
    }

    /**
     * Twice a second: loyal creatures take up their master's fights, loyalty runs out, and holds whose caster has gone
     * let go.
     */
    public static void sweep(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        LINKS.entrySet().removeIf(entry -> {
            ServerPlayer caster = server.getPlayerList().getPlayer(entry.getKey());
            if (caster != null && caster.level() == entry.getValue().level) {
                return false;
            }
            HELD.remove(entry.getValue().targetId);
            return true;
        });
        Iterator<Loyalty> iterator = LOYAL.values().iterator();
        while (iterator.hasNext()) {
            Loyalty loyalty = iterator.next();
            if (!(loyalty.level().getEntity(loyalty.mobId()) instanceof Mob mob) || !mob.isAlive() || loyalty.level().getGameTime() >= loyalty.until()) {
                iterator.remove();
                continue;
            }
            ServerPlayer master = server.getPlayerList().getPlayer(loyalty.master());
            if (master == null || master.level() != loyalty.level() || steers(mob) || !(mob instanceof Enemy)
                    && mob.getAttribute(Attributes.ATTACK_DAMAGE) == null) {
                continue;
            }
            LivingEntity foe = foeOf(master);
            if (foe != null && foe != mob && foe.distanceTo(mob) < LOYAL_REACH && mob.getTarget() != foe) {
                mob.setTarget(foe);
            }
        }
    }

    private static void reach(ServerPlayer caster, Link link, long now) {
        ServerLevel level = caster.level();
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(RANGE));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster, eye, limit, new AABB(eye, limit).inflate(1.0),
                candidate -> canHold(caster, candidate), eye.distanceToSqr(limit));
        if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) {
            return;
        }
        link.targetId = target.getId();
        link.since = now;
        HELD.put(target.getId(), link);
        LOYAL.remove(target.getUUID());
        announce(caster, link, PossessPayload.SEIZING);
        Vec3 head = target.getEyePosition();
        play(level, head, SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 0.7F, 1.2F);
        play(level, head, SoundEvents.BEACON_POWER_SELECT, 0.5F, 0.7F);
        Mastery.grant(caster, 3);
    }

    private static boolean canHold(ServerPlayer caster, Entity candidate) {
        if (!(candidate instanceof LivingEntity living) || !living.isAlive() || candidate == caster || candidate.isSpectator()
                || candidate.typeHolder().is(RESISTS) || HELD.containsKey(candidate.getId()) || Telekinesis.isHeld(candidate)
                || candidate.isPassengerOfSameVehicle(caster) || candidate.isVehicle()) {
            return false;
        }
        if (candidate instanceof Player other) {
            return ScarletServerConfig.get().mindControlPlayers && !other.isCreative() && caster.canHarmPlayer(other)
                    && !(other instanceof ServerPlayer && LINKS.containsKey(other.getUUID()));
        }
        return candidate instanceof Mob;
    }

    private static @Nullable LivingEntity target(ServerPlayer caster, Link link) {
        if (link.targetId == Link.NOTHING || caster.level() != link.level) {
            return null;
        }
        return link.level.getEntity(link.targetId) instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    /**
     * What is held strikes the way it does: a creeper goes off, a ghast or a blaze throws fire, a bow looses an arrow,
     * and anything else lashes out at what is in front of it.
     */
    private static void attack(ServerPlayer caster, Link link, LivingEntity attacker) {
        ServerLevel level = link.level;
        long now = level.getGameTime();
        if (now < link.readyAt) {
            return;
        }
        Vec3 look = Vec3.directionFromRotation(link.input.pitch(), link.input.yaw());
        if (attacker instanceof Creeper creeper) {
            if (!creeper.isIgnited()) {
                creeper.ignite();
            }
            link.readyAt = now + 40;
            return;
        }
        if (attacker instanceof Ghast ghast) {
            LargeFireball fireball = new LargeFireball(level, ghast, look, ghast.getExplosionPower());
            fireball.setPos(ghast.getX() + look.x * 4.0, ghast.getY(0.5) + 0.5, ghast.getZ() + look.z * 4.0);
            level.addFreshEntity(fireball);
            level.playSound(null, ghast.getX(), ghast.getY(), ghast.getZ(), SoundEvents.GHAST_SHOOT, SoundSource.HOSTILE, 10.0F, 1.0F);
            link.readyAt = now + 30;
            return;
        }
        if (attacker instanceof Blaze blaze) {
            SmallFireball fireball = new SmallFireball(level, blaze, look);
            fireball.setPos(fireball.getX(), blaze.getY(0.5) + 0.5, fireball.getZ());
            level.addFreshEntity(fireball);
            level.playSound(null, blaze.getX(), blaze.getY(), blaze.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0F, 1.0F);
            link.readyAt = now + 8;
            return;
        }
        ItemStack bow = attacker.getItemInHand(ProjectileUtil.getWeaponHoldingHand(attacker, Items.BOW));
        if (bow.getItem() instanceof BowItem && !(attacker instanceof Player)) {
            ItemStack arrowStack = new ItemStack(Items.ARROW);
            AbstractArrow arrow = ProjectileUtil.getMobArrow(attacker, arrowStack, 1.0F, bow);
            Projectile.spawnProjectileUsingShoot(arrow, level, arrowStack, look.x, look.y, look.z, 1.6F, 1.0F);
            level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 1.0F, 1.0F);
            link.readyAt = now + 20;
            return;
        }
        attacker.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        link.readyAt = now + MELEE_COOLDOWN;
        Entity struck = struck(caster, attacker, look);
        if (struck == null) {
            return;
        }
        if (attacker instanceof ServerPlayer held) {
            held.attack(struck);
        } else if (attacker instanceof Mob mob && mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            mob.doHurtTarget(level, struck);
        } else {
            // something with no blow of its own butts into it
            struck.push(look.x * 0.45, 0.2, look.z * 0.45);
            struck.syncVelocity = true;
        }
    }

    /**
     * Whatever is right in front of what is held, within its reach, other than the caster's own body.
     */
    private static @Nullable Entity struck(ServerPlayer caster, LivingEntity attacker, Vec3 look) {
        double reach = Math.max(3.0, attacker.getBbWidth() * 0.5 + 2.5);
        Vec3 eye = attacker.getEyePosition();
        Vec3 end = eye.add(look.scale(reach));
        BlockHitResult block = attacker.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, attacker));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(attacker, eye, limit, new AABB(eye, limit).inflate(1.0),
                candidate -> candidate.isPickable() && candidate.isAlive() && candidate != caster && candidate != attacker && !candidate.isSpectator(),
                eye.distanceToSqr(limit));
        return hit == null ? null : hit.getEntity();
    }

    /**
     * Whoever the master is fighting: what last hurt them, or what they last hurt, if it was lately.
     */
    private static @Nullable LivingEntity foeOf(ServerPlayer master) {
        LivingEntity attacker = master.getLastHurtByMob();
        if (attacker != null && attacker.isAlive() && master.tickCount - master.getLastHurtByMobTimestamp() < 200) {
            return attacker;
        }
        LivingEntity victim = master.getLastHurtMob();
        if (victim != null && victim.isAlive() && master.tickCount - master.getLastHurtMobTimestamp() < 200) {
            return victim;
        }
        return null;
    }

    private static void announce(ServerPlayer caster, Link link, int stage) {
        PossessPayload payload = new PossessPayload(caster.getId(), link.targetId, stage);
        Services.NETWORK.sendToTrackingAndSelf(caster, payload);
        if (link.level.getEntity(link.targetId) instanceof ServerPlayer held && held != caster) {
            Services.NETWORK.sendToPlayer(held, payload);
        }
    }

    private static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static final class Link {
        static final int NOTHING = -1;

        final UUID caster;
        final ServerLevel level;
        int targetId = NOTHING;
        long since;
        /** Whether the caster's view has moved in, so they steer it. */
        boolean inside;
        /** A dreamwalker's spirit, rather than a mind reached into with Mind Control. */
        boolean dream;
        ControlPayload input = ControlPayload.IDLE;
        long readyAt;
        int struggles;

        Link(UUID caster, ServerLevel level) {
            this.caster = caster;
            this.level = level;
        }
    }

    private record Loyalty(UUID master, ServerLevel level, int mobId, long until) {
    }
}
