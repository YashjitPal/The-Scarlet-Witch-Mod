package com.yashjit.scarlet.player;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.platform.Services;
import com.yashjit.scarlet.registry.ScarletItems;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;

public final class Speed {
    public static final int CHANGE_TICKS = 12;
    public static final Identifier STRIDE = Scarlet.id("speed_stride");
    private static final double TOP_SPEED_BONUS = 2.5;
    private static final AttributeModifier JUMP = new AttributeModifier(Scarlet.id("speed_jump"), 0.19, AttributeModifier.Operation.ADD_VALUE);
    private static final AttributeModifier LANDING = new AttributeModifier(Scarlet.id("speed_landing"), 2.0, AttributeModifier.Operation.ADD_VALUE);
    private static final AttributeModifier VIBRATION = new AttributeModifier(Scarlet.id("speed_vibration"), 11, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

    private Speed() {}

    public static boolean wearsGoggles(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(ScarletItems.SPEED_GOGGLES.get());
    }

    private static boolean carriesSuit(ServerPlayer player) {
        return player.getInventory().contains(stack -> stack.is(ScarletItems.SPEED_SUIT.get()));
    }

    static void toggle(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return;
        ScarletPlayerData data = Services.PLAYER_DATA.get(player);
        long now = player.level().getGameTime();
        if (now - data.gogglesChangedAt() < CHANGE_TICKS) return;
        boolean down = !data.gogglesDown();
        boolean dress = down && carriesSuit(player);
        Services.PLAYER_DATA.set(player, data.withGoggles(down, dress, now));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                dress || data.speedSuit() ? SoundEvents.BUNDLE_REMOVE_ONE : SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                SoundSource.PLAYERS, 0.65F, 1.65F);
        scuff(player, 15);
    }

    public static void tick(ServerPlayer player) {
        ScarletPlayerData data = Services.PLAYER_DATA.get(player);
        boolean equipped = wearsGoggles(player) && player.isAlive() && !player.isSpectator();
        if (!equipped && data.gogglesChangedAt() != ScarletPlayerData.NEVER) {
            Services.PLAYER_DATA.set(player, data.withGoggles(false, false, ScarletPlayerData.NEVER));
        } else if (data.speedSuit() && !carriesSuit(player)) {
            Services.PLAYER_DATA.set(player, data.withGoggles(true, false, data.gogglesChangedAt()));
        }
        boolean ready = equipped && data.gogglesDown() && !player.isPassenger() && !player.getAbilities().flying;
        AttributeInstance movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) {
            AttributeModifier stride = movement.getModifier(STRIDE);
            if (ready && player.isSprinting() && !player.isInWater() && !player.isFallFlying()) {
                double bonus = stride == null ? 0.25 : stride.amount();
                if (stride == null || bonus < TOP_SPEED_BONUS) {
                    double next = stride == null ? bonus : Math.min(TOP_SPEED_BONUS, bonus + 0.25);
                    movement.addOrUpdateTransientModifier(new AttributeModifier(STRIDE, next, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
            } else if (stride != null) {
                movement.removeModifier(STRIDE);
            }
        }
        modify(player, Attributes.JUMP_STRENGTH, JUMP, ready);
        modify(player, Attributes.SAFE_FALL_DISTANCE, LANDING, ready);
        modify(player, Attributes.BLOCK_BREAK_SPEED, VIBRATION, ready && player.getMainHandItem().isEmpty());
        long elapsed = player.level().getGameTime() - data.gogglesChangedAt();
        if (equipped && (elapsed == 2 || elapsed == 5)) scuff(player, 6);
    }

    private static void modify(ServerPlayer player, Holder<Attribute> key, AttributeModifier modifier, boolean active) {
        AttributeInstance attribute = player.getAttribute(key);
        if (attribute == null) return;
        if (active) attribute.addOrUpdateTransientModifier(modifier);
        else if (attribute.hasModifier(modifier.id())) attribute.removeModifier(modifier.id());
    }

    private static void scuff(ServerPlayer player, int count) {
        if (!player.onGround()) return;
        BlockState ground = player.getBlockStateOn();
        if (ground.isAir()) return;
        player.level().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), player.getX(), player.getY() + 0.09,
                player.getZ(), count, 0.32, 0.09, 0.32, 0.08);
        if (count == 6) player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ground.getSoundType().getStepSound(),
                SoundSource.PLAYERS, 0.65F, 1.35F);
    }
}
