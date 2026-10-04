package com.yashjit.scarlet.magic;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.darkhold.Darkhold;
import com.yashjit.scarlet.darkhold.Dreamwalk;
import com.yashjit.scarlet.hex.HexEjection;
import com.yashjit.scarlet.hex.HexPaint;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.network.MagicEventPayload;
import com.yashjit.scarlet.platform.Services;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The rules of spellcasting, shared by both sides: the server enforces them, clients use them to predict and to draw
 * the HUD. Energy regenerates by formula from the last settled value, so it never needs syncing while it refills.
 */
public final class Magic {

    /**
     * Ticks without casting before regeneration speeds up.
     */
    public static final int FAST_REGEN_DELAY = 30;

    /**
     * Ticks from the cast to its effect, so the effect lands on the strike of the gesture rather than its wind-up.
     */
    public static final int RELEASE_DELAY = 1;

    /**
     * Energy needed to begin a channel or levitation; below it the spell would only flicker.
     */
    public static final float MIN_SUSTAIN = 8.0F;

    public static final float SHIELD_DISTANCE = 0.95F;
    public static final float SHIELD_RADIUS = 1.05F;

    private static final float SLOW_REGEN = 0.35F;
    private static final float FAST_REGEN = 1.1F;
    private static final int SHATTER_COOLDOWN = 60;
    private static final int TAKEOFF_GRACE = 10;
    private static final Identifier SHIELD_SLOW = Scarlet.id("chaos_shield_slow");

    private static final Map<UUID, List<PendingCast>> PENDING = new HashMap<>();
    /** Casters whose Hex channel took hold of the wall to part it, rather than resizing the Hex. */
    private static final Set<UUID> TEARING = new HashSet<>();
    /** Casters whose Hex channel took hold of someone inside it, to throw them out. */
    private static final Set<UUID> EJECTING = new HashSet<>();
    /** Casters whose Hex channel is restyling what they look at with the block in their off hand. */
    private static final Set<UUID> PAINTING = new HashSet<>();

    private Magic() {
    }

    public static MagicState state(Player player) {
        return Services.PLAYER_DATA.magic(player);
    }

    public static float maxEnergy(Player player) {
        float max = Mastery.maxEnergy(Math.max(1, Mastery.rank(player)));
        return Hexes.ownsHex(player) ? max - Hexes.RESERVE : max;
    }

    /**
     * Energy at a (possibly fractional) game time. Channels and levitation drain it; otherwise it refills.
     */
    public static float energy(MagicState state, double now, float max) {
        double elapsed = Math.max(0.0, now - state.energyAt());
        float drain = (state.channeling() ? Spell.byIndex(state.channel()).cost() : 0.0F) + (state.levitating() ? Spell.LEVITATION.cost() : 0.0F);
        if (drain > 0.0F) {
            return (float) Math.clamp(state.energy() - drain * elapsed, 0.0, max);
        }
        double fastFrom = Math.max(state.energyAt(), state.lastCastAt() + FAST_REGEN_DELAY);
        double slowTicks = Math.min(elapsed, Math.max(0.0, fastFrom - state.energyAt()));
        double fastTicks = elapsed - slowTicks;
        return (float) Math.min(max, state.energy() + SLOW_REGEN * slowTicks + FAST_REGEN * fastTicks);
    }

    public static float energy(Player player, double now) {
        return energy(state(player), now, maxEnergy(player));
    }

    /**
     * Takes energy for magic that is no spell of its own, such as winding back a Hex, down to none at most.
     */
    public static void spend(ServerPlayer player, float cost, long now) {
        MagicState state = state(player);
        Services.PLAYER_DATA.setMagic(player, state.withEnergy(Math.max(0.0F, energy(state, now, maxEnergy(player)) - cost), now));
    }

    /**
     * Which arm a cast uses: alternating, starting with the main arm.
     */
    public static HumanoidArm castArm(Player player, int castNumber) {
        return castNumber % 2 == 0 ? player.getMainArm() : player.getMainArm().getOpposite();
    }

    /**
     * Where the shield stands: in front of the chest, facing where the player looks.
     */
    public static Vec3 shieldCenter(Player player, float partialTick) {
        return player.getEyePosition(partialTick).add(player.getViewVector(partialTick).scale(SHIELD_DISTANCE)).add(0.0, -0.2, 0.0);
    }

    public static Refusal check(Player player, Spell spell, long now) {
        if (!CrownItem.isWearingCrown(player) || player.isSpectator() || !player.isAlive()) {
            return Refusal.NO_CROWN;
        }
        if (!player.getMainHandItem().isEmpty() && spell != Spell.LEVITATION) {
            return Refusal.HANDS_FULL;
        }
        if (Mastery.rank(player) < spell.rank()) {
            return Refusal.LOCKED;
        }
        if (!spell.available()) {
            return Refusal.UNAVAILABLE;
        }
        if (spell.darkhold() && !Darkhold.carries(player)) {
            return Refusal.DARKHOLD;
        }
        MagicState state = state(player);
        if (now < state.readyAt(spell)) {
            return Refusal.COOLDOWN;
        }
        if (spell == Spell.DREAMWALK && (!player.onGround() || player.isPassenger() || player.isSwimming() || player.isFallFlying()
                || state.levitating())) {
            return Refusal.FOOTING;
        }
        float energy = energy(state, now, maxEnergy(player));
        if (!shrinkingHex(player, spell) && (spell.input() == Spell.Input.TAP ? energy < spell.cost() : energy < MIN_SUSTAIN)) {
            return Refusal.ENERGY;
        }
        if (spell == Spell.HEX && !Hexes.ownsHex(player) && energy < Hexes.CAST_COST
                || spell == Spell.DREAMWALK && energy < Dreamwalk.START_ENERGY) {
            return Refusal.ENERGY;
        }
        return Refusal.NONE;
    }

    /**
     * Drawing your own Hex in takes nothing out of you, so it can always be done, even spent.
     */
    private static boolean shrinkingHex(Player player, Spell spell) {
        return spell == Spell.HEX && player.isShiftKeyDown() && Hexes.ownsHex(player) && HexPaint.ink(player.getOffhandItem()) == null;
    }

    // ---------------------------------------------------------------- server

    public static void handleCast(ServerPlayer player, boolean start, int spellIndex) {
        Spell spell = Spell.byIndex(spellIndex);
        long now = player.level().getGameTime();
        MagicState state = state(player);
        if (!start) {
            if (state.channeling(spell)) {
                stopChannel(player, now, false);
            }
            return;
        }
        if (spell == Spell.LEVITATION && state.levitating()) {
            stopLevitation(player, now, true);
            return;
        }
        if (check(player, spell, now) != Refusal.NONE) {
            return;
        }
        if (spell == Spell.HEX) {
            castHex(player, state, now);
            return;
        }
        switch (spell.input()) {
            case TAP -> cast(player, spell, now);
            case CHANNEL -> {
                if (!state.channeling()) {
                    startChannel(player, spell, now);
                }
            }
            case TOGGLE -> {
                if (spell == Spell.LEVITATION) {
                    startLevitation(player, now);
                }
            }
        }
    }

    /**
     * The first cast raises the Hex; once it stands, holding the cast resizes it.
     */
    private static void castHex(ServerPlayer player, MagicState state, long now) {
        if (!Hexes.ownsHex(player)) {
            float energy = energy(state, now, maxEnergy(player));
            if (Hexes.cast(player, now)) {
                Services.PLAYER_DATA.setMagic(player, state.withSpent(Spell.HEX, energy - Hexes.CAST_COST, now));
                Mastery.grant(player, 10);
            }
        } else if (Hexes.ownsHexHere(player) && !state.channeling()) {
            startChannel(player, Spell.HEX, now);
        }
    }

    public static void select(ServerPlayer player, int index) {
        MagicState state = state(player);
        Spell spell = Spell.byIndex(index);
        if (state.selected() != index && Spell.WHEEL.contains(spell) && (!spell.darkhold() || Darkhold.carries(player))) {
            Services.PLAYER_DATA.setMagic(player, state.withSelected(index));
        }
    }

    /**
     * Begins a spell that lasts until it is ended, for spells that decide for themselves when they begin.
     */
    public static void channel(ServerPlayer player, Spell spell, long now) {
        if (!state(player).channeling()) {
            startChannel(player, spell, now);
        }
    }

    private static void cast(ServerPlayer player, Spell spell, long now) {
        MagicState state = state(player);
        float energy = energy(state, now, maxEnergy(player)) - spell.cost();
        boolean offHand = castArm(player, state.castCount()) != player.getMainArm();
        Services.PLAYER_DATA.setMagic(player, spell.strikes() ? state.withCast(spell, energy, now) : state.withSpent(spell, energy, now));
        PENDING.computeIfAbsent(player.getUUID(), id -> new ArrayList<>()).add(new PendingCast(spell, offHand, now + spell.windUp()));
        SpellCasts.begin(player, spell);
        Mastery.grant(player, 1);
    }

    private static void startChannel(ServerPlayer player, Spell spell, long now) {
        MagicState state = state(player);
        Services.PLAYER_DATA.setMagic(player, state.withChannel(spell.ordinal(), energy(state, now, maxEnergy(player)), now));
        if (spell == Spell.CHAOS_SHIELD) {
            slowForShield(player, true);
            SpellCasts.shieldRaised(player);
        } else if (spell == Spell.TELEKINESIS) {
            Telekinesis.start(player, now);
        } else if (spell == Spell.MIND_CONTROL) {
            MindControl.start(player, now);
        } else if (spell == Spell.HEX && !player.isShiftKeyDown() && HexEjection.seize(player)) {
            EJECTING.add(player.getUUID());
        } else if (spell == Spell.HEX && Hexes.beginPart(player, !player.isShiftKeyDown())) {
            TEARING.add(player.getUUID());
        } else if (spell == Spell.HEX && HexPaint.canPaint(player)) {
            PAINTING.add(player.getUUID());
        }
    }

    /**
     * @param shattered the channel broke rather than being let go, which costs a longer cooldown
     */
    public static void stopChannel(ServerPlayer player, long now, boolean shattered) {
        MagicState state = state(player);
        if (!state.channeling()) {
            return;
        }
        Spell spell = Spell.byIndex(state.channel());
        MagicState stopped = state.withChannel(MagicState.NO_CHANNEL, energy(state, now, maxEnergy(player)), now);
        int cooldown = spell == Spell.MIND_CONTROL ? MindControl.stop(player) : spell == Spell.DREAMWALK ? Dreamwalk.stop(player) : -1;
        if (cooldown < 0) {
            cooldown = shattered ? SHATTER_COOLDOWN : spell.cooldown();
        }
        Services.PLAYER_DATA.setMagic(player, stopped.withCooldown(spell, now + cooldown));
        if (spell == Spell.TELEKINESIS) {
            Telekinesis.stop(player);
        } else if (spell == Spell.HEX && TEARING.remove(player.getUUID())) {
            Hexes.endPart(player);
        } else if (spell == Spell.HEX && EJECTING.remove(player.getUUID())) {
            HexEjection.fling(player);
        } else if (spell == Spell.HEX && PAINTING.remove(player.getUUID())) {
            HexPaint.stop(player);
        }
        if (spell == Spell.CHAOS_SHIELD) {
            slowForShield(player, false);
            if (shattered) {
                SpellCasts.shieldShattered(player);
                Services.NETWORK.sendToTrackingAndSelf(player, new MagicEventPayload(player.getId(), MagicEventPayload.SHIELD_SHATTER, shieldCenter(player, 1.0F)));
            } else {
                SpellCasts.shieldLowered(player);
            }
        }
        Mastery.grant(player, 2);
    }

    private static void startLevitation(ServerPlayer player, long now) {
        MagicState state = state(player);
        Services.PLAYER_DATA.setMagic(player, state.withLevitation(true, energy(state, now, maxEnergy(player)), now));
        // the client drops flight the moment it finds itself flying on the ground, so a grounded player is lifted first
        // and flies once airborne
        boolean grounded = player.onGround();
        Abilities abilities = player.getAbilities();
        abilities.mayfly = true;
        abilities.flying = !grounded;
        player.onUpdateAbilities();
        if (grounded) {
            player.setDeltaMovement(player.getDeltaMovement().add(0.0, 0.5, 0.0));
            player.syncVelocity = true;
        }
        SpellCasts.liftOff(player);
        Services.NETWORK.sendToTrackingAndSelf(player, new MagicEventPayload(player.getId(), MagicEventPayload.LIFT_OFF, player.position()));
    }

    /**
     * @param gently float down rather than drop, when stopping in mid-air
     */
    public static void stopLevitation(ServerPlayer player, long now, boolean gently) {
        MagicState state = state(player);
        if (!state.levitating()) {
            return;
        }
        Services.PLAYER_DATA.setMagic(player, state.withLevitation(false, energy(state, now, maxEnergy(player)), now)
                .withCooldown(Spell.LEVITATION, now + Spell.LEVITATION.cooldown()));
        Abilities abilities = player.getAbilities();
        if (!player.isCreative() && !player.isSpectator()) {
            abilities.mayfly = false;
            abilities.flying = false;
            player.onUpdateAbilities();
        }
        if (player.onGround()) {
            SpellCasts.touchDown(player);
            Services.NETWORK.sendToTrackingAndSelf(player, new MagicEventPayload(player.getId(), MagicEventPayload.TOUCH_DOWN, player.position()));
        } else {
            if (gently) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
            }
            SpellCasts.levitationReleased(player);
        }
        Mastery.grant(player, 2);
    }

    public static void tick(ServerPlayer player) {
        long now = player.level().getGameTime();
        runPending(player, now);
        Telekinesis.tick(player, now);
        MagicState state = state(player);
        if (player.tickCount % 20 == 0 && state.selectedSpell().darkhold() && !state.channeling() && !Darkhold.carries(player)) {
            // the book's spells go with the book
            state = state.withSelected(Spell.WHEEL.getFirst().ordinal());
            Services.PLAYER_DATA.setMagic(player, state);
        }
        if (state.channeling()) {
            boolean exhausted = energy(state, now, maxEnergy(player)) <= 0.001F && !shrinkingHex(player, Spell.byIndex(state.channel()));
            if (exhausted || !CrownItem.isWearingCrown(player) || !player.getMainHandItem().isEmpty() || !player.isAlive()) {
                stopChannel(player, now, exhausted);
            } else if (state.channeling(Spell.CHAOS_SHIELD)) {
                deflectProjectiles(player, now);
            } else if (state.channeling(Spell.TELEKINESIS) && !Telekinesis.hold(player, now)) {
                stopChannel(player, now, false);
            } else if (state.channeling(Spell.MIND_CONTROL) && !MindControl.hold(player, now)) {
                stopChannel(player, now, false);
            } else if (state.channeling(Spell.DREAMWALK) && !Dreamwalk.hold(player, now)) {
                stopChannel(player, now, false);
            } else if (state.channeling(Spell.HEX) && (EJECTING.contains(player.getUUID()) ? !HexEjection.hold(player)
                    : TEARING.contains(player.getUUID()) ? !Hexes.part(player, !player.isShiftKeyDown())
                    : PAINTING.contains(player.getUUID()) ? !HexPaint.paint(player, now)
                    : !Hexes.resize(player, !player.isShiftKeyDown(), now))) {
                stopChannel(player, now, false);
            }
            state = state(player);
        }
        if (state.levitating()) {
            Abilities abilities = player.getAbilities();
            boolean exhausted = energy(state, now, maxEnergy(player)) <= 0.001F;
            if (exhausted || !CrownItem.isWearingCrown(player) || !player.isAlive() || player.isPassenger() || player.isFallFlying()) {
                stopLevitation(player, now, true);
            } else if (now - state.levitatingSince() > TAKEOFF_GRACE && player.onGround() && !abilities.flying) {
                // touched down: landing ends levitation
                stopLevitation(player, now, false);
            } else if (!abilities.mayfly || !abilities.flying && !player.onGround()) {
                abilities.mayfly = true;
                abilities.flying = true;
                player.onUpdateAbilities();
            }
        }
    }

    private static void runPending(ServerPlayer player, long now) {
        List<PendingCast> pending = PENDING.get(player.getUUID());
        if (pending == null) {
            return;
        }
        Iterator<PendingCast> iterator = pending.iterator();
        while (iterator.hasNext()) {
            PendingCast cast = iterator.next();
            if (now >= cast.at()) {
                iterator.remove();
                if (player.isAlive() && CrownItem.isWearingCrown(player)) {
                    SpellCasts.perform(player, cast.spell(), cast.offHand());
                }
            }
        }
        if (pending.isEmpty()) {
            PENDING.remove(player.getUUID());
        }
    }

    // ---------------------------------------------------------------- the shield

    /**
     * Turns projectiles that are about to strike the shield back the way they came. They become the caster's.
     */
    private static void deflectProjectiles(ServerPlayer player, long now) {
        Vec3 look = player.getLookAngle();
        Vec3 center = shieldCenter(player, 1.0F);
        Vec3 eye = player.getEyePosition();
        for (Projectile projectile : player.level().getEntitiesOfClass(Projectile.class, new AABB(center, center).inflate(SHIELD_RADIUS + 3.0))) {
            if (projectile.getOwner() == player || !projectile.isAlive()) {
                continue;
            }
            Vec3 velocity = projectile.getDeltaMovement();
            if (velocity.dot(look) >= -0.01 || projectile.position().subtract(eye).dot(look) < 0.2) {
                continue;
            }
            // close to the shield now, or will be within this tick's travel
            Vec3 from = projectile.position();
            Vec3 to = from.add(velocity);
            if (distanceToSegment(center, from, to) > SHIELD_RADIUS + 0.25) {
                continue;
            }
            Vec3 reflected = velocity.subtract(look.scale(2.0 * velocity.dot(look))).scale(0.85);
            projectile.setDeltaMovement(reflected);
            projectile.setOwner(player);
            projectile.needsSync = true;
            SpellCasts.shieldStruck(player, from);
            Services.NETWORK.sendToTrackingAndSelf(player, new MagicEventPayload(player.getId(), MagicEventPayload.SHIELD_HIT, from));
            payForBlock(player, now, 3.0F);
            if (!state(player).channeling(Spell.CHAOS_SHIELD)) {
                return;
            }
        }
    }

    /**
     * Whether the shield stops a blow: anything from the front that a shield could block, as long as there is energy to
     * pay for it. Melee attackers are thrown back.
     */
    public static boolean shieldBlocks(ServerPlayer player, DamageSource source, float amount) {
        MagicState state = state(player);
        if (!state.channeling(Spell.CHAOS_SHIELD) || source.is(DamageTypeTags.BYPASSES_SHIELD)) {
            return false;
        }
        Vec3 from = source.getSourcePosition();
        if (from == null) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 direction = from.subtract(eye).normalize();
        if (direction.dot(player.getLookAngle()) < 0.45) {
            return false;
        }
        long now = player.level().getGameTime();
        Vec3 at = eye.add(direction.scale(SHIELD_DISTANCE));
        SpellCasts.shieldStruck(player, at);
        Services.NETWORK.sendToTrackingAndSelf(player, new MagicEventPayload(player.getId(), MagicEventPayload.SHIELD_HIT, at));
        payForBlock(player, now, 2.5F + amount * 1.6F);
        if (source.getDirectEntity() instanceof LivingEntity attacker && source.getDirectEntity() == source.getEntity()) {
            attacker.push(direction.x * 0.7, 0.18, direction.z * 0.7);
            attacker.syncVelocity = true;
        }
        return true;
    }

    /**
     * Takes energy for a blocked blow. With too little left the shield still stops it, but shatters doing so and takes
     * all that remains.
     */
    private static void payForBlock(ServerPlayer player, long now, float cost) {
        MagicState state = state(player);
        float energy = energy(state, now, maxEnergy(player));
        Services.PLAYER_DATA.setMagic(player, state.withEnergy(Math.max(0.0F, energy - cost), now));
        if (energy < cost) {
            stopChannel(player, now, true);
        }
    }

    private static void slowForShield(ServerPlayer player, boolean slowed) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        if (slowed) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(SHIELD_SLOW, -0.4, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else {
            speed.removeModifier(SHIELD_SLOW);
        }
    }

    private static double distanceToSegment(Vec3 point, Vec3 from, Vec3 to) {
        Vec3 segment = to.subtract(from);
        double length = segment.lengthSqr();
        double t = length < 1.0E-8 ? 0.0 : Math.clamp(point.subtract(from).dot(segment) / length, 0.0, 1.0);
        return point.distanceTo(from.add(segment.scale(t)));
    }

    private record PendingCast(Spell spell, boolean offHand, long at) {
    }

    public enum Refusal {
        NONE, NO_CROWN, HANDS_FULL, LOCKED, UNAVAILABLE, COOLDOWN, ENERGY,
        /** One of the Darkhold's spells, without the book. */
        DARKHOLD,
        /** Dreamwalking anywhere but sat on solid ground. */
        FOOTING
    }
}
