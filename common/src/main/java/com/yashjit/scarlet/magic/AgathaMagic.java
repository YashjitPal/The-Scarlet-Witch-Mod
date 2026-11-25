package com.yashjit.scarlet.magic;

import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.network.WitchEffect;
import com.yashjit.scarlet.network.WitchEffect.Kind;
import com.yashjit.scarlet.platform.Services;
import com.yashjit.scarlet.player.AgathaForm;
import com.yashjit.scarlet.registry.ScarletDamageTypes;
import com.yashjit.scarlet.registry.ScarletDataComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

public final class AgathaMagic {
    public interface InnateMagic {
        int scarlet$remainingMagic();
        void scarlet$setRemainingMagic(int value);
    }
    private static final List<Spell> COMBAT = List.of(Spell.VIOLET_BLAST, Spell.SIPHON, Spell.BINDING, Spell.TELEKINESIS,
            Spell.WITCH_RUNE, Spell.MALEDICTION, Spell.DOUBLE, Spell.NIGHTMARE, Spell.FAMILIAR);
    private static final List<Spell> CRAFT = List.of(Spell.WITCH_SIGHT, Spell.AEGIS, Spell.TRANSMUTE, Spell.GLAMOUR, Spell.MIND_CONTROL, Spell.DREAMBIND);
    // These are actual castable techniques. The crown-bound Hex and Demiurge transformations stay with their artifacts.
    private static final List<Spell> THEFT = List.of(Spell.STARFIRE, Spell.ARC_BEAM, Spell.SHOCKWAVE, Spell.WEAVE,
            Spell.RED_MIST, Spell.CHAOS_SHIELD, Spell.TELEKINESIS, Spell.MIND_CONTROL, Spell.TRANSMUTE, Spell.DREAMBIND, Spell.AEGIS, Spell.GLAMOUR, Spell.RUNE_TRAP);
    private record Drain(LivingEntity target, ItemStack brooch, long start, int duration, int knowledge) {}
    private record Field(ServerLevel level, Vec3 center, long until) {}
    public static final double RADIUS = 5;

    /** First point on the side or cap of the ward touched by the finite segment. */
    static double entry(Vec3 center, Vec3 from, Vec3 to) {
        Vec3 a = from.subtract(center), v = to.subtract(from);
        double radial = a.x * a.x + a.z * a.z - RADIUS * RADIUS;
        if (radial <= 0 && a.y >= -0.5 && a.y <= 4) return Double.POSITIVE_INFINITY;
        double lo = 0, hi = 1, speed = v.x * v.x + v.z * v.z;
        if (speed < 1e-12) {
            if (radial > 0) return Double.POSITIVE_INFINITY;
        } else {
            double dot = a.x * v.x + a.z * v.z, det = dot * dot - speed * radial;
            if (det <= 0) return Double.POSITIVE_INFINITY;
            double root = Math.sqrt(det);
            lo = Math.max(lo, (-dot - root) / speed);
            hi = Math.min(hi, (-dot + root) / speed);
        }
        if (Math.abs(v.y) < 1e-8) {
            if (a.y < -0.5 || a.y > 4) return Double.POSITIVE_INFINITY;
        } else {
            double bottom = (-0.5 - a.y) / v.y, top = (4 - a.y) / v.y;
            lo = Math.max(lo, Math.min(bottom, top));
            hi = Math.min(hi, Math.max(bottom, top));
        }
        return lo <= hi ? lo : Double.POSITIVE_INFINITY;
    }

    public static Vec3 intercept(ServerLevel level, Entity attacker, Vec3 from, Vec3 to) {
        double first = Double.POSITIVE_INFINITY;
        ServerPlayer defender = null;
        for (var pair : FIELDS.entrySet()) {
            Field f = pair.getValue();
            ServerPlayer p = pair.getKey();
            if (f.level != level || f.until <= level.getGameTime() || p.level() != level || !p.isAlive()
                    || !AgathaForm.worn(p) || p == attacker || attacker != null && p.isAlliedTo(attacker)) continue;
            Vec3 offset = p.position().subtract(f.center);
            if (offset.horizontalDistanceSqr() > RADIUS * RADIUS || offset.y < -0.5 || offset.y > 4) continue;
            double t = entry(f.center, from, to);
            if (t < first) { first = t; defender = p; }
        }
        if (defender == null) return null;
        Vec3 at = from.lerp(to, first);
        effect(defender, null, Kind.RIM, at, 14);
        level.playSound(null, BlockPos.containing(at), SoundEvents.CANDLE_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 0.8F);
        return at;
    }
    private record Summon(LivingEntity spirit, long until) {}
    private static final Map<ServerPlayer, Drain> DRAINS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Field> FIELDS = new WeakHashMap<>();
    private static final Map<LivingEntity, Long> SILENCE = new WeakHashMap<>();
    private static final Map<LivingEntity, Long> CURSES = new WeakHashMap<>();
    private static final Map<LivingEntity, Vec3> BOUND = new WeakHashMap<>();
    private static final Map<ServerPlayer, Summon> FAMILIARS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Summon> DOUBLES = new WeakHashMap<>();
    public static final String CONJURED = "scarlet_conjured";
    private AgathaMagic() {}

    public static List<Spell> wheel(Player p, boolean craft) {
        if (!craft) return COMBAT;
        var spells = new ArrayList<>(CRAFT);
        for (Spell spell : THEFT) if ((stolen(p, spell) || GrimoireLeaf.learned(p, spell)) && !spells.contains(spell) && !COMBAT.contains(spell)) spells.add(spell);
        if (dark(p) && com.yashjit.scarlet.darkhold.Darkhold.carries(p)) spells.add(Spell.DREAMWALK);
        return spells;
    }

    static boolean permits(Player p, Spell s) {
        return COMBAT.contains(s) || CRAFT.contains(s) || s == Spell.LEVITATION || stolen(p, s) || s == Spell.DREAMWALK && dark(p);
    }

    public static boolean stolen(Player p, Spell s) {
        return THEFT.contains(s) && AgathaForm.worn(p) && (p.getOffhandItem().getOrDefault(ScarletDataComponents.STOLEN_SPELLS.get(), 0) & 1 << s.ordinal()) != 0;
    }

    public static int charge(Player p) { return p.getOffhandItem().getOrDefault(ScarletDataComponents.SIPHON_CHARGE.get(), 0); }
    private static boolean dark(Player p) { return Services.PLAYER_DATA.corruption(p).opened(); }
    public static boolean silenced(LivingEntity p) { return SILENCE.getOrDefault(p, 0L) > p.level().getGameTime(); }

    private static boolean canAffect(ServerPlayer p, LivingEntity t) {
        return t != p && t.isAlive() && !t.isSpectator() && !p.isAlliedTo(t)
                && (!(t instanceof Player other) || !other.isCreative() && p.canHarmPlayer(other))
                && (!(t instanceof TamableAnimal pet) || !pet.isOwnedBy(p)) && !t.entityTags().contains(CONJURED);
    }

    private static boolean witch(LivingEntity t) {
        return t instanceof com.yashjit.scarlet.entity.WanderingWitch || t instanceof Player p && Magic.canCast(p) || t.getType() == EntityTypes.WITCH
                || t.getType() == EntityTypes.EVOKER || t.getType() == EntityTypes.ILLUSIONER || t.getType() == EntityTypes.VEX;
    }

    private static HitResult aim(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(range));
        var block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        var hit = ProjectileUtil.getEntityHitResult(p, eye, block.getLocation(), new AABB(eye, block.getLocation()).inflate(1),
                e -> e instanceof LivingEntity t && canAffect(p, t), eye.distanceToSqr(block.getLocation()));
        return hit == null ? block : hit;
    }

    private static void effect(ServerPlayer p, Entity t, Kind kind, Vec3 at, int ticks) {
        Services.NETWORK.sendToTrackingAndSelf(p, new WitchEffect(p.getId(), t == null ? -1 : t.getId(), kind, at, ticks));
    }

    private static void tell(ServerPlayer p, String name) {
        p.sendOverlayMessage(Component.translatable("agatha.scarlet." + name).withColor(0xD7ACF0));
    }

    private static ItemStack focus(LivingEntity t) {
        if (t instanceof Player p && AgathaForm.worn(p)) return p.getOffhandItem();
        return CrownItem.isWearingCrown(t) ? t.getItemBySlot(EquipmentSlot.HEAD) : ItemStack.EMPTY;
    }

    private static int knowledge(LivingEntity t) {
        int bits = 0;
        if (t instanceof Player p) {
            for (Spell s : THEFT) if (WiccanSpells.permits(p, s) && WiccanSpells.unlocked(p, s)) bits |= 1 << s.ordinal();
        } else if (t instanceof com.yashjit.scarlet.entity.WanderingWitch member) bits = 1 << member.technique().ordinal();
        else if (witch(t)) bits = 1 << (t.getType() == EntityTypes.EVOKER ? Spell.WEAVE : Spell.GLAMOUR).ordinal();
        return bits;
    }

    // Called only for accepted hits, with actual health/absorption lost after armor and hurt cooldowns.
    public static void struck(LivingEntity target, DamageSource source, float loss) {
        if (loss <= 0 || !(source.getEntity() instanceof LivingEntity attacker) || target == attacker) return;
        if (target instanceof ServerPlayer p && AgathaForm.worn(p)) {
            p.getOffhandItem().set(ScarletDataComponents.SIPHON_CHARGE.get(), Math.min(100, charge(p) + Math.max(5, Math.round(loss * 4))));
            effect(p, p, Kind.SEAL, p.getBoundingBox().getCenter(), 8);
        }
        if (attacker instanceof ServerPlayer p && AgathaForm.worn(p) && dark(p) && !Magic.state(p).channeling(Spell.SIPHON)) {
            p.heal(Math.min(2, loss * 0.35F));
            Magic.spend(p, -Math.min(4, loss), p.level().getGameTime());
            effect(p, target, Kind.DARK, target.getBoundingBox().getCenter(), 10);
        }
    }

    static boolean beginDrain(ServerPlayer p, long now) {
        HitResult hit = aim(p, 16);
        if (!(hit instanceof EntityHitResult e) || !(e.getEntity() instanceof LivingEntity t) || !(witch(t) || t instanceof Player)) {
            tell(p, "need_caster"); return false;
        }
        if (intercept(p.level(), p, p.getEyePosition(), hit.getLocation()) != null) return false;
        int reserve = charge(p);
        int duration = 14 + reserve * 106 / 100 + (dark(p) ? 20 : 0);
        DRAINS.put(p, new Drain(t, p.getOffhandItem(), now, duration, knowledge(t)));
        p.getOffhandItem().set(ScarletDataComponents.SIPHON_CHARGE.get(), 0);
        effect(p, t, Kind.SIPHON, t.getBoundingBox().getCenter(), duration);
        p.level().playSound(null, p.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.8F, 0.78F);
        return true;
    }

    static boolean drain(ServerPlayer p, long now) {
        Drain d = DRAINS.get(p);
        if (d == null || d.brooch != p.getOffhandItem() || now - d.start >= d.duration) return false;
        LivingEntity t = d.target;
        if (t.level() != p.level() || !canAffect(p, t) || p.distanceToSqr(t) > 18 * 18 || !p.hasLineOfSight(t)) return false;
        if (intercept(p.level(), p, p.getEyePosition(), t.getBoundingBox().getCenter()) != null) return false;
        if ((now - d.start) % 10 != 0) return true;
        ItemStack victim = focus(t);
        int xp = Mastery.xp(victim);
        InnateMagic innate = t instanceof InnateMagic pool && witch(t) && victim.isEmpty() ? pool : null;
        if (innate != null) xp = innate.scarlet$remainingMagic();
        float before = t.getHealth();
        if (!t.hurtServer(p.level(), ScarletDamageTypes.spell(p.level(), ScarletDamageTypes.CHAOS_BOLT, p),
                (xp > 0 ? 2 : 4) * strength(p))) return true;
        p.heal(Math.min(4, Math.max(0, before - t.getHealth())));
        int taken = Math.min(xp, 36);
        if (taken > 0) {
            if (innate != null) innate.scarlet$setRemainingMagic(xp - taken);
            else victim.set(ScarletDataComponents.MASTERY.get(), xp - taken);
        }
        if (!p.isCreative()) {
            int oldRank = Mastery.rank(p);
            d.brooch.set(ScarletDataComponents.MASTERY.get(), (int)Math.min(Integer.MAX_VALUE, (long)Mastery.xp(d.brooch) + taken));
            int rank = Mastery.rank(p);
            if (oldRank < 9 && rank >= 9) d.brooch.set(ScarletDataComponents.AGATHA_FORM.get(), AgathaForm.FINALE);
        }
        if (xp <= taken) {
            interrupt(t, now, 12);
            effect(p, t, Kind.ASH, t.getBoundingBox().getCenter(), 36);
        }
        if (t instanceof ServerPlayer other) Magic.spend(other, 8, now);
        if (!t.isAlive() && d.knowledge != 0) {
            int known = d.brooch.getOrDefault(ScarletDataComponents.STOLEN_SPELLS.get(), 0);
            d.brooch.set(ScarletDataComponents.STOLEN_SPELLS.get(), known | d.knowledge);
            effect(p, t, Kind.THEFT, t.getEyePosition(), 26);
            tell(p, "theft");
        }
        return t.isAlive();
    }

    static void endDrain(ServerPlayer p) {
        Drain d = DRAINS.remove(p);
        if (d != null) effect(p, d.target, Kind.RELEASE, d.target.position(), 1);
    }

    private static float strength(ServerPlayer p) {
        Field field = FIELDS.get(p);
        boolean inside = field != null && field.level == p.level() && field.until > p.level().getGameTime() && p.position().distanceToSqr(field.center) <= 25;
        return (dark(p) ? 1.35F : 1) * (inside ? 1.25F : 1);
    }

    private static void bind(ServerPlayer p, LivingEntity t, int ticks) {
        t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 8, false, false), p);
        t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1, false, false), p);
        BOUND.put(t, t.position());
        effect(p, t, Kind.CHAINS, t.getBoundingBox().getCenter(), ticks);
    }

    public static void holdBound(LivingEntity t) {
        Vec3 anchor = BOUND.get(t);
        if (anchor == null) return;
        var slow = t.getEffect(MobEffects.SLOWNESS);
        if (!t.isAlive() || slow == null || slow.getAmplifier() < 8 || t.position().distanceToSqr(anchor) > 4) { BOUND.remove(t); return; }
        if (t.position().distanceToSqr(anchor) > 0.01) t.teleportTo(anchor.x, anchor.y, anchor.z);
        t.setDeltaMovement(Vec3.ZERO);
    }

    private static void interrupt(LivingEntity t, long now, int ticks) {
        SILENCE.put(t, now + ticks);
        if (t instanceof ServerPlayer other && Magic.state(other).channeling()) Magic.stopChannel(other, now, true);
        if (t.isUsingItem()) t.stopUsingItem();
    }

    static boolean cast(ServerPlayer p, Spell spell, long now) {
        HitResult hit = aim(p, spell == Spell.VIOLET_BLAST ? 28 : 18);
        LivingEntity target = hit instanceof EntityHitResult e ? (LivingEntity)e.getEntity() : null;
        Vec3 at = hit.getLocation();
        int duration = Math.round(70 * strength(p));
        if (spell == Spell.VIOLET_BLAST || spell == Spell.BINDING || spell == Spell.NIGHTMARE || spell == Spell.MALEDICTION) {
            Vec3 rim = intercept(p.level(), p, p.getEyePosition(), at);
            if (rim != null) {
                if (spell == Spell.VIOLET_BLAST) effect(p, null, Kind.MISS, rim, 13);
                return true;
            }
        }
        switch (spell) {
            case VIOLET_BLAST -> {
                if (target != null && target.hurtServer(p.level(), ScarletDamageTypes.spell(p.level(), ScarletDamageTypes.CHAOS_BOLT, p),
                        (5 + Mastery.rank(p) * 0.28F) * strength(p))) interrupt(target, now, 8);
                effect(p, target, hit.getType() == HitResult.Type.MISS ? Kind.MISS : Kind.BLAST, at, 13);
                p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_CLUSTER_HIT, SoundSource.PLAYERS, 0.85F, 0.78F);
                return true;
            }
            case WITCH_RUNE -> {
                if (hit.getType() == HitResult.Type.MISS) { tell(p, "need_ground"); return false; }
                Vec3 ground = Vec3.atBottomCenterOf(BlockPos.containing(at.add(0, 0.05, 0))).add(0, 0.06, 0);
                FIELDS.put(p, new Field(p.level(), ground, now + duration * 3));
                effect(p, null, Kind.RUNE, ground, duration * 3);
            }
            case BINDING, NIGHTMARE, MALEDICTION -> {
                if (target == null) { tell(p, "need_target"); return false; }
                if (spell == Spell.BINDING) bind(p, target, duration);
                if (spell == Spell.NIGHTMARE) {
                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 0, false, false), p);
                    target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, duration, 0, false, false), p);
                    if (target instanceof Mob mob) {
                        mob.setTarget(null);
                        Vec3 away = target.position().add(target.position().subtract(p.position()).normalize().scale(7));
                        mob.getNavigation().moveTo(away.x, away.y, away.z, 1.4);
                    }
                    effect(p, target, Kind.NIGHTMARE, at, duration);
                }
                if (spell == Spell.MALEDICTION) {
                    target.addEffect(new MobEffectInstance(MobEffects.WITHER, duration, 0, false, false), p);
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 1, false, false), p);
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 1, false, false), p);
                    CURSES.put(target, now + duration);
                    effect(p, target, Kind.CURSE, at, duration);
                }
            }
            case WITCH_SIGHT -> {
                int count = 0;
                for (var e : p.level().getEntities(p, p.getBoundingBox().inflate(32 * strength(p)), e ->
                        e instanceof LivingEntity l && (witch(l) || l.typeHolder().is(net.minecraft.tags.EntityTypeTags.UNDEAD))
                        || e instanceof ItemEntity item && item.getItem().isEnchanted())) {
                    if (count >= 20) break;
                    Services.NETWORK.sendToPlayer(p, new WitchEffect(p.getId(), e.getId(), Kind.SIGHT, e.getBoundingBox().getCenter(), 70));
                    count++;
                }
                p.sendOverlayMessage(Component.translatable("agatha.scarlet.sense", count).withColor(0xDEC0FA));
                effect(p, p, Kind.SEAL, p.getEyePosition(), 16);
            }
            case DOUBLE, FAMILIAR -> { if (!summon(p, spell, target, now)) return false; }
            default -> { return false; }
        }
        p.level().playSound(null, BlockPos.containing(at.distanceToSqr(p.position()) > 100 ? p.position() : at),
                spell == Spell.BINDING ? SoundEvents.CHAIN_PLACE : SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.65F, 0.85F);
        return true;
    }

    private static boolean summon(ServerPlayer p, Spell spell, LivingEntity target, long now) {
        boolean familiar = spell == Spell.FAMILIAR;
        LivingEntity spirit = familiar ? EntityTypes.WOLF.create(p.level(), EntitySpawnReason.MOB_SUMMONED)
                : EntityTypes.MANNEQUIN.create(p.level(), EntitySpawnReason.MOB_SUMMONED);
        if (spirit == null) return false;
        Vec3 side = Vec3.directionFromRotation(0, p.getYRot() + 65);
        for (int sign : new int[]{1, -1, 0}) {
            spirit.snapTo(p.position().add(side.scale(sign * 1.25)), p.getYRot(), 0);
            if (p.level().noCollision(spirit)) break;
        }
        if (!p.level().noCollision(spirit)) return false;
        spirit.addTag(CONJURED);
        if (spirit instanceof Wolf wolf) {
            wolf.tame(p);
            wolf.setComponent(DataComponents.WOLF_COLLAR, DyeColor.PURPLE);
            wolf.setOrderedToSit(false);
            wolf.setTarget(target);
        } else {
            spirit.setComponent(DataComponents.PROFILE, ResolvableProfile.createResolved(p.getGameProfile()));
            spirit.setYHeadRot(p.getYHeadRot());
        }
        if (!p.level().addFreshEntity(spirit)) return false;
        var summons = familiar ? FAMILIARS : DOUBLES;
        int life = (int)((familiar ? 240 : 100) * strength(p));
        Summon previous = summons.put(p, new Summon(spirit, now + life));
        if (previous != null) previous.spirit.discard();
        if (!familiar) {
            p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, life, 0, false, false));
            for (Mob mob : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(14), m -> m.getTarget() == p)) mob.setTarget(spirit);
        }
        effect(p, spirit, familiar ? Kind.FAMILIAR : Kind.DOUBLE, spirit.position(), life);
        return true;
    }

    private static void expire(ServerPlayer p, long now, Map<ServerPlayer, Summon> summons) {
        Summon s = summons.get(p);
        if (s == null) return;
        if (now >= s.until || !AgathaForm.worn(p) || !p.isAlive() || s.spirit.isRemoved() || s.spirit.level() != p.level()) {
            summons.remove(p);
            s.spirit.discard();
        }
    }

    public static void disconnect(ServerPlayer p) {
        for (var summons : List.of(DOUBLES, FAMILIARS)) {
            Summon s = summons.remove(p);
            if (s != null) s.spirit.discard();
        }
        DRAINS.remove(p);
        FIELDS.remove(p);
    }

    static void tick(ServerPlayer p, long now) {
        expire(p, now, DOUBLES);
        expire(p, now, FAMILIARS);
        if (CURSES.getOrDefault(p, 0L) > now && now % 20 == 0) interrupt(p, now, 6);
        Field f = FIELDS.get(p);
        if (f == null) return;
        if (f.level != p.level() || !AgathaForm.worn(p) || !p.isAlive() || now >= f.until) { FIELDS.remove(p); return; }
        if (now % 12 != 0) return;
        if (p.position().distanceToSqr(f.center) <= 25) p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 16, 0, false, false));
        for (LivingEntity t : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(f.center, f.center).inflate(5, 2, 5),
                e -> canAffect(p, e) && e.position().distanceToSqr(f.center) <= 25 && (e instanceof Player || e instanceof net.minecraft.world.entity.monster.Enemy))) {
            interrupt(t, now, 15);
            bind(p, t, 16);
        }
    }
}
