package com.yashjit.scarlet.registry;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.crown.CrownStyle;
import com.yashjit.scarlet.darkhold.DarkholdItem;
import com.yashjit.scarlet.entity.ParkedCarItem;
import com.yashjit.scarlet.platform.Services;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ScarletItems {

    public static final Supplier<CrownItem> WITCH_TIARA = crown("witch_tiara", CrownStyle.WITCH);
    public static final Supplier<CrownItem> WARLOCK_CROWN = crown("warlock_crown", CrownStyle.WARLOCK);
    public static final Supplier<CrownItem> WICCAN_CROWN = crown("wiccan_crown", CrownStyle.WICCAN);
    public static final Supplier<Item> AGATHA_BROOCH = register("agatha_brooch", Item::new,
            () -> new Item.Properties().stacksTo(1).component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.OFFHAND)
                    .setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER).build()));
    public static final Supplier<Item> MIRROR_LEAF = leaf("mirror_leaf", com.yashjit.scarlet.magic.Spell.GLAMOUR);
    public static final Supplier<Item> STONE_LEAF = leaf("stone_leaf", com.yashjit.scarlet.magic.Spell.TRANSMUTE);
    public static final Supplier<Item> DREAM_LEAF = leaf("dream_leaf", com.yashjit.scarlet.magic.Spell.DREAMBIND);
    public static final Supplier<Item> ORB_GRIMOIRE = leaf("orb_grimoire", com.yashjit.scarlet.magic.Spell.STARFIRE);
    public static final Supplier<Item> BEAM_GRIMOIRE = leaf("beam_grimoire", com.yashjit.scarlet.magic.Spell.ARC_BEAM);
    public static final Supplier<Item> RUNE_GRIMOIRE = leaf("rune_grimoire", com.yashjit.scarlet.magic.Spell.RUNE_TRAP);

    private static Supplier<Item> leaf(String name, com.yashjit.scarlet.magic.Spell spell) {
        return register(name, p -> new com.yashjit.scarlet.magic.GrimoireLeaf(p, spell), () -> new Item.Properties().stacksTo(16));
    }
    public static final Supplier<Item> SPEED_GOGGLES = register("speed_goggles", Item::new,
            () -> new Item.Properties().stacksTo(1).component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD)
                    .setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER).build()));
    public static final Supplier<Item> SPEED_SUIT = register("speed_suit", Item::new, () -> new Item.Properties().stacksTo(1));
    public static final Supplier<ParkedCarItem> PARKED_CAR = register("parked_car", ParkedCarItem::new, () -> new Item.Properties().stacksTo(1));
    public static final Supplier<DarkholdItem> DARKHOLD = register("darkhold", DarkholdItem::new, DarkholdItem::properties);
    /** A slice of toast, out of a toaster: a little more filling than the bread it was. */
    public static final Supplier<Item> TOAST = register("toast", Item::new,
            () -> new Item.Properties().food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.7F).build()));

    private ScarletItems() {
    }

    public static void bootstrap() {
    }

    public static CrownItem crown(CrownStyle style) {
        return switch (style) {
            case WITCH -> WITCH_TIARA.get();
            case WARLOCK -> WARLOCK_CROWN.get();
            case WICCAN -> WICCAN_CROWN.get();
        };
    }

    private static Supplier<CrownItem> crown(String name, CrownStyle style) {
        return register(name, properties -> new CrownItem(style, properties), CrownItem::properties);
    }

    /**
     * The item that puts a block down, named after it.
     *
     * @param tall whether the block stands two high, like a door, and needs the space over it too
     */
    static Supplier<Item> blockItem(String name, Supplier<? extends Block> block, boolean tall) {
        return register(name, properties -> tall ? new DoubleHighBlockItem(block.get(), properties) : new BlockItem(block.get(), properties),
                () -> new Item.Properties().useBlockDescriptionPrefix());
    }

    private static <T extends Item> Supplier<T> register(String name, Function<Item.Properties, T> factory, Supplier<Item.Properties> properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Scarlet.id(name));
        return Services.REGISTRY.register(BuiltInRegistries.ITEM, name, () -> factory.apply(properties.get().setId(key)));
    }
}
