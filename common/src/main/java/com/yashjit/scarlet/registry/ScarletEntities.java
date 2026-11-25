package com.yashjit.scarlet.registry;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.entity.ChaosBolt;
import com.yashjit.scarlet.entity.DreamBody;
import com.yashjit.scarlet.entity.ParkedCar;
import com.yashjit.scarlet.entity.Seat;
import com.yashjit.scarlet.platform.Services;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;

public final class ScarletEntities {
    public static final Supplier<EntityType<com.yashjit.scarlet.entity.WanderingWitch>> WANDERING_WITCH = register("wandering_witch",
            EntityType.Builder.<com.yashjit.scarlet.entity.WanderingWitch>of(com.yashjit.scarlet.entity.WanderingWitch::new, MobCategory.CREATURE).noLootTable().sized(0.6F,1.95F).eyeHeight(1.67F).clientTrackingRange(10));

    public static final Supplier<EntityType<ChaosBolt>> CHAOS_BOLT = register("chaos_bolt",
            EntityType.Builder.<ChaosBolt>of(ChaosBolt::new, MobCategory.MISC).noLootTable().sized(0.5F, 0.5F).clientTrackingRange(16)
                    .updateInterval(ChaosBolt.SYNC_INTERVAL));
    public static final Supplier<EntityType<ParkedCar>> PARKED_CAR = register("parked_car",
            EntityType.Builder.<ParkedCar>of(ParkedCar::new, MobCategory.MISC).noLootTable().sized(2.2F, 1.6F).clientTrackingRange(10)
                    .updateInterval(3));
    public static final Supplier<EntityType<Seat>> SEAT = register("seat",
            EntityType.Builder.<Seat>of(Seat::new, MobCategory.MISC).noLootTable().noSummon().sized(0.0F, 0.0F)
                    .clientTrackingRange(8).updateInterval(20));
    public static final Supplier<EntityType<DreamBody>> DREAM_BODY = register("dream_body",
            EntityType.Builder.<DreamBody>of(DreamBody::create, MobCategory.MISC).noLootTable().noSummon().sized(0.7F, 1.6F).eyeHeight(1.33F)
                    .clientTrackingRange(32).updateInterval(20));

    private ScarletEntities() {
    }

    public static void bootstrap() {
        Services.REGISTRY.registerAttributes(DREAM_BODY, LivingEntity::createLivingAttributes);
        Services.REGISTRY.registerAttributes(WANDERING_WITCH, com.yashjit.scarlet.entity.WanderingWitch::attributes);
    }

    private static <T extends Entity> Supplier<EntityType<T>> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Scarlet.id(name));
        return Services.REGISTRY.register(BuiltInRegistries.ENTITY_TYPE, name, () -> builder.build(key));
    }
}
