package com.yashjit.scarlet.darkhold;

import com.yashjit.scarlet.ScarletPalette;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * The Darkhold, the Book of the Damned. Carried, it lends its dark spells to whoever wears a crown. Held up and read,
 * it opens: its pages turn one by one, and every page takes a little more of the reader.
 */
public class DarkholdItem extends Item {

    /** Ticks of reading to each page. */
    public static final int PAGE_TICKS = 60;

    /**
     * How much of the one looking at the tooltip the Darkhold has taken, 0 to 1, or less than 0 if no one is. Set by
     * the client, which is where tooltips are made.
     */
    public static DoubleSupplier viewerCorruption = () -> -1.0;

    public DarkholdItem(Properties properties) {
        super(properties);
    }

    public static Properties properties() {
        return new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC);
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withColor(ScarletPalette.SICKLY);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        // held open in both hands by a pose of the mod's own, without vanilla's sword-blocking twist in the first person
        return ItemUseAnimation.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
        int read = getUseDuration(stack, entity) - ticksRemaining;
        if (entity instanceof ServerPlayer player && read > 0 && read % PAGE_TICKS == 0) {
            Darkhold.readPage(player);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.scarlet.darkhold.lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        builder.accept(Component.translatable("item.scarlet.darkhold.carry").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.scarlet.darkhold.read").withStyle(ChatFormatting.GRAY));
        double taken = viewerCorruption.getAsDouble();
        if (taken >= 0.005) {
            builder.accept(Component.translatable("item.scarlet.darkhold.taken", Math.round(taken * 100)).withColor(ScarletPalette.SICKLY));
        }
    }
}
