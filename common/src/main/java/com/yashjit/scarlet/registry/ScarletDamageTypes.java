package com.yashjit.scarlet.registry;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Damage types are data: their definitions live in {@code data/scarlet/damage_type}.
 */
public final class ScarletDamageTypes {

    public static final ResourceKey<DamageType> CHAOS_BOLT = ResourceKey.create(Registries.DAMAGE_TYPE, Scarlet.id("chaos_bolt"));
    public static final ResourceKey<DamageType> SHOCKWAVE = ResourceKey.create(Registries.DAMAGE_TYPE, Scarlet.id("shockwave"));
    public static final ResourceKey<DamageType> TELEKINESIS = ResourceKey.create(Registries.DAMAGE_TYPE, Scarlet.id("telekinesis"));

    private ScarletDamageTypes() {
    }

    public static DamageSource chaosBolt(Level level, Entity bolt, @Nullable Entity caster) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(CHAOS_BOLT), bolt, caster);
    }

    /**
     * Damage dealt by a spell straight from its caster, with nothing flying between them.
     */
    public static DamageSource spell(Level level, ResourceKey<DamageType> type, Entity caster) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type), caster);
    }
}
