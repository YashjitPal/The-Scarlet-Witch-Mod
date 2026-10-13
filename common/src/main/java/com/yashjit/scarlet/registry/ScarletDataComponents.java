package com.yashjit.scarlet.registry;

import com.mojang.serialization.Codec;
import com.yashjit.scarlet.platform.Services;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.UUIDUtil;
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

    /**
     * Whose Hex made something out of thin air: it lasts only inside that Hex.
     */
    public static final Supplier<DataComponentType<UUID>> HEX_MADE = Services.REGISTRY.register(BuiltInRegistries.DATA_COMPONENT_TYPE, "hex_made",
            () -> DataComponentType.<UUID>builder().persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC).build());

    /**
     * When something a Hex made began to glitch out, carried outside it.
     */
    public static final Supplier<DataComponentType<Long>> HEX_FADING = Services.REGISTRY.register(BuiltInRegistries.DATA_COMPONENT_TYPE, "hex_fading",
            () -> DataComponentType.<Long>builder().persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG).build());

    private ScarletDataComponents() {
    }

    public static void bootstrap() {
    }
}
