package com.yashjit.scarlet.crown;

import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.magic.Mastery;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.core.component.DataComponents;
import org.jspecify.annotations.Nullable;

/**
 * The source of all power: whoever wears a crown is the Scarlet Witch or Warlock.
 *
 * <p>Worn in the helmet slot with diamond helmet protection. It has no durability, so the mastery stored on it can
 * never be lost to wear, and it survives fire and lava. Its equippable has no equipment asset, which makes vanilla
 * draw the item's own 3D model on the head instead of an armor layer.
 */
public class CrownItem extends Item {

    private final CrownStyle style;

    public CrownItem(CrownStyle style, Properties properties) {
        super(properties);
        this.style = style;
    }

    public static Properties properties() {
        return new Properties()
                .stacksTo(1)
                .fireResistant()
                .attributes(ArmorMaterials.DIAMOND.createAttributes(ArmorType.HELMET))
                .enchantable(ArmorMaterials.DIAMOND.enchantmentValue())
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD)
                        .setEquipSound(SoundEvents.ARMOR_EQUIP_GOLD)
                        .build());
    }

    public CrownStyle style() {
        return style;
    }

    public static boolean isWearingCrown(LivingEntity entity) {
        return wornStyle(entity) != null;
    }

    public static @Nullable CrownStyle wornStyle(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof CrownItem crown ? crown.style() : null;
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withColor(style.nameColor());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        int xp = Mastery.xp(stack);
        int rank = Mastery.rankForXp(xp);
        builder.accept(Component.translatable("item.scarlet.crown.mastery", Mastery.numeral(rank)).withColor(ScarletPalette.BRIGHT_SCARLET));
        builder.accept((rank >= Mastery.MAX_RANK
                ? Component.translatable("item.scarlet.crown.mastery.max")
                : Component.translatable("item.scarlet.crown.mastery.progress", Math.round(Mastery.progress(xp) * 100)))
                .withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.scarlet.crown.lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
