package com.yashjit.scarlet.registry;

import com.yashjit.scarlet.platform.Services;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;

public final class ScarletDataComponents {

    /**
     * Mastery experience stored on a crown. It travels with the crown, so a stolen crown keeps its power.
     */
    public static final Supplier<DataComponentType<Integer>> MASTERY = Services.REGISTRY.register(BuiltInRegistries.DATA_COMPONENT_TYPE, "mastery",
            () -> DataComponentType.<Integer>builder().persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    private ScarletDataComponents() {
    }

    public static void bootstrap() {
    }
}
