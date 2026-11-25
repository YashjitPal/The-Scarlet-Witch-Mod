package com.yashjit.scarlet.entity;

import com.yashjit.scarlet.magic.AgathaMagic;
import com.yashjit.scarlet.magic.Spell;
import com.yashjit.scarlet.network.WitchEffect;
import com.yashjit.scarlet.platform.Services;
import com.yashjit.scarlet.registry.ScarletDamageTypes;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.*;

public final class WanderingWitch extends PathfinderMob {
    private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(WanderingWitch.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> GOLD = SynchedEntityData.defineId(WanderingWitch.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CASTING = SynchedEntityData.defineId(WanderingWitch.class, EntityDataSerializers.BOOLEAN);
    private UUID coven;
    private long departure;
    private int cooldown, windup, pattern, art;
    private Vec3 mark;
    public WanderingWitch(EntityType<? extends WanderingWitch> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 24).add(Attributes.MOVEMENT_SPEED, 0.28).add(Attributes.FOLLOW_RANGE, 28);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(TIER,1); data.define(GOLD,false); data.define(CASTING,false);
    }
    public int tier() { return entityData.get(TIER); }
    public boolean gold() { return entityData.get(GOLD); }
    public boolean casting() { return entityData.get(CASTING); }
    public Spell technique() { return tier()==3 ? Spell.RUNE_TRAP : tier()==2 ? Spell.ARC_BEAM : Spell.STARFIRE; }
    @Override protected void dropCustomDeathLoot(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, boolean playerKill) {
        super.dropCustomDeathLoot(level,source,playerKill);
        if (!playerKill || random.nextInt(3)!=0) return;
        var book = switch (tier()) {
            case 3 -> com.yashjit.scarlet.registry.ScarletItems.RUNE_GRIMOIRE;
            case 2 -> com.yashjit.scarlet.registry.ScarletItems.BEAM_GRIMOIRE;
            default -> com.yashjit.scarlet.registry.ScarletItems.ORB_GRIMOIRE;
        };
        spawnAtLocation(level,book.get());
    }
    public void join(UUID group, int tier, boolean gold, long until) {
        coven = group;
        entityData.set(TIER, Math.clamp(tier,1,3));
        entityData.set(GOLD,gold);
        departure = until;
    }
    public boolean companion(Entity entity) { return entity instanceof WanderingWitch w && coven!=null && coven.equals(w.coven); }
    @Override protected Component getTypeName() { return Component.translatable("entity.scarlet.wandering_witch." + tier()); }
    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (coven!=null) out.store("Coven", UUIDUtil.CODEC, coven);
        out.putInt("Tier",tier()); out.putBoolean("Gold",gold()); out.putLong("Departure",departure);
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        join(in.read("Coven",UUIDUtil.CODEC).orElse(null), in.getIntOr("Tier",1), in.getBooleanOr("Gold",false), in.getLongOr("Departure",0));
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0,new FloatGoal(this));
        goalSelector.addGoal(5,new WaterAvoidingRandomStrollGoal(this,0.8));
        goalSelector.addGoal(6,new LookAtPlayerGoal(this,Player.class,8));
        goalSelector.addGoal(7,new RandomLookAroundGoal(this));
        targetSelector.addGoal(1,new HurtByTargetGoal(this,WanderingWitch.class) {
            @Override protected void alertOther(Mob other, LivingEntity enemy) {
                if (companion(other)) super.alertOther(other, enemy);
            }
        }.setAlertOthers());
    }
    @Override public boolean removeWhenFarAway(double distance) { return departure == 0; }
    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (departure>0 && level.getGameTime()>=departure && !hasCustomName() && getTarget()==null) { discard(); return; }
        var target = getTarget();
        if (target==null || !target.isAlive() || !canAttack(target) || AgathaMagic.silenced(this)
                || ((AgathaMagic.InnateMagic)(Object)this).scarlet$remainingMagic()==0) {
            windup=0; entityData.set(CASTING,false);
            if (target==null && coven!=null && (tickCount+getId())%40==0) {
                var leader=level.getEntitiesOfClass(WanderingWitch.class,getBoundingBox().inflate(32),this::companion)
                        .stream().min(Comparator.comparing(WanderingWitch::getUUID)).orElse(this);
                if (distanceToSqr(leader)>36) getNavigation().moveTo(leader,1.0);
            }
            return;
        }
        getLookControl().setLookAt(target,35,30);
        if (distanceToSqr(target)>18*18 || !hasLineOfSight(target)) {
            entityData.set(CASTING,false); windup=0;
            if (tickCount%10==0) getNavigation().moveTo(target,1);
            return;
        }
        getNavigation().stop();
        if (windup>0) {
            if (--windup==0) { release(level,target); entityData.set(CASTING,false); cooldown=45+random.nextInt(30); }
        } else if (--cooldown<=0) {
            art = (tier()-1 + pattern++) % tier();
            windup = 14+art*6;
            mark = target.position();
            if (art==2) {
                var ground=level.clip(new ClipContext(mark.add(0,0.5,0),mark.add(0,-3,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
                if (ground.getType()==HitResult.Type.MISS) art=0;
                else mark=ground.getLocation().add(0,0.04,0);
            }
            if (art==2 && AgathaMagic.intercept(level,this,getEyePosition(),mark)!=null) { cooldown=55; windup=0; return; }
            entityData.set(CASTING,true);
            fx(art==2 ? WitchEffect.Kind.COVEN_RUNE : WitchEffect.Kind.COVEN_GATHER,mark,windup);
            playSound(SoundEvents.AMETHYST_BLOCK_CHIME,0.65F,gold()?1.6F:1.15F);
        }
    }
    private void release(ServerLevel level, LivingEntity target) {
        Vec3 start=getEyePosition(), end=target.getBoundingBox().getCenter();
        if (art==0) {
            Vec3 aim=end.subtract(start).normalize();
            ChaosBolt.fire(this, start.add(aim.scale(0.55)),aim);
        } else if (art==1) {
            var block=level.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            var hit=ProjectileUtil.getEntityHitResult(this,start,block.getLocation(), new AABB(start,end).inflate(1),
                    e -> e instanceof LivingEntity living && e!=this && living.isAlive() && !e.isSpectator() && !companion(e),start.distanceToSqr(end));
            Vec3 stop=hit==null ? block.getLocation() : hit.getLocation();
            Vec3 ward=AgathaMagic.intercept(level,this,start,stop);
            fx(WitchEffect.Kind.COVEN_BEAM,ward==null ? stop : ward,8);
            if (ward==null && hit!=null) hurtWithMagic(level,(LivingEntity)hit.getEntity(),4);
        } else if (AgathaMagic.intercept(level,this,start,mark)==null && target.position().distanceToSqr(mark)<=2.2*2.2) {
            if (hurtWithMagic(level,target,3)) target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,38,3),this);
            fx(WitchEffect.Kind.COVEN_BURST,mark.add(0,0.6,0),10);
        }
    }
    private boolean hurtWithMagic(ServerLevel level,LivingEntity victim,float damage) {
        return victim.hurtServer(level,ScarletDamageTypes.spell(level,ScarletDamageTypes.CHAOS_BOLT,this),damage);
    }
    public void fx(WitchEffect.Kind kind, Vec3 position, int ticks) {
        Services.NETWORK.sendToTrackingAndSelf(this,new WitchEffect(getId(),-1,kind,position,ticks));
    }
}
