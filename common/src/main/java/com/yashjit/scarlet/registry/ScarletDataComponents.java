package com.yashjit.scarlet.registry;

import com.mojang.serialization.Codec;
import com.yashjit.scarlet.platform.Services;
import com.yashjit.scarlet.player.AgathaForm;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;

public final class ScarletDataComponents {
    public static final Supplier<DataComponentType<Boolean>> AGATHA_HAIR = Services.REGISTRY.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, "agatha_hair", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
    public static final Supplier<DataComponentType<Integer>> SIPHON_CHARGE = Services.REGISTRY.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, "siphon_charge", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.intRange(0, 100)).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final Supplier<DataComponentType<Integer>> STOLEN_SPELLS = Services.REGISTRY.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, "stolen_spells", () -> DataComponentType.<Integer>builder()
                    .persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final Supplier<DataComponentType<AgathaForm>> AGATHA_FORM = Services.REGISTRY.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, "agatha_form", () -> DataComponentType.<AgathaForm>builder()
                    .persistent(net.minecraft.util.StringRepresentable.fromEnum(AgathaForm::values)).build());

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

    /**
     * How battered a car packed up into its item was, to be just as battered when it is parked again.
     */
    public static final Supplier<DataComponentType<Float>> CAR_DAMAGE = Services.REGISTRY.register(BuiltInRegistries.DATA_COMPONENT_TYPE, "car_damage",
            () -> DataComponentType.<Float>builder().persistent(Codec.floatRange(0.0F, Float.MAX_VALUE)).networkSynchronized(ByteBufCodecs.FLOAT).build());

    private ScarletDataComponents() {
    }

    public static final Supplier<DataComponentType<Integer>> GRIMOIRE = Services.REGISTRY.register(BuiltInRegistries.DATA_COMPONENT_TYPE, "grimoire",
            () -> DataComponentType.<Integer>builder().persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    public static final Supplier<DataComponentType<GlobalPos>> WAYPOINT = Services.REGISTRY.register(BuiltInRegistries.DATA_COMPONENT_TYPE, "wiccan_waypoint",
            () -> DataComponentType.<GlobalPos>builder().persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC).build());

    public static void bootstrap() {
    }
}
