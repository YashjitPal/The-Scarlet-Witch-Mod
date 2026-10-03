package com.yashjit.scarlet.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Parks a car where it is used, nose away from whoever parks it: in the look of the era around it, in a color of its
 * own.
 */
public class ParkedCarItem extends Item {

    public ParkedCarItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos().above();
        Vec3 at = Vec3.atBottomCenterOf(pos);
        if (!level.noCollision(new AABB(at.x - 1.0, at.y, at.z - 1.0, at.x + 1.0, at.y + 1.4, at.z + 1.0))) {
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            Player player = context.getPlayer();
            float yaw = player != null ? player.getYRot() : 0.0F;
            ParkedCar.park(server, at, yaw, server.getRandom().nextInt(5), false);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 0.7F, 1.1F);
            if (player == null || !player.isCreative()) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
