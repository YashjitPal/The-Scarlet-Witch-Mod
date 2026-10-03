package com.yashjit.scarlet.entity;

import com.yashjit.scarlet.decor.SeatBlock;
import com.yashjit.scarlet.registry.ScarletEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Where someone sits on a couch or a chair: an invisible place to ride, at the height of the cushion, that goes the
 * moment they get up or the seat is gone.
 */
public class Seat extends Entity {

    public Seat(EntityType<? extends Seat> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * Sits someone down on the seat of a block, facing the way it does.
     *
     * @return whether they sat; not if someone already sits there
     */
    public static boolean sit(ServerLevel level, BlockPos pos, double height, Direction facing, LivingEntity sitter) {
        if (sitter.isPassenger() || isTaken(level, pos)) {
            return false;
        }
        Seat seat = new Seat(ScarletEntities.SEAT.get(), level);
        seat.setPos(pos.getX() + 0.5, pos.getY() + height, pos.getZ() + 0.5);
        seat.setYRot(facing.toYRot());
        seat.setYHeadRot(facing.toYRot());
        level.addFreshEntity(seat);
        if (!sitter.startRiding(seat)) {
            seat.discard();
            return false;
        }
        sitter.setYRot(facing.toYRot());
        sitter.setYHeadRot(facing.toYRot());
        sitter.setYBodyRot(facing.toYRot());
        return true;
    }

    /** Whether someone already sits on the seat of a block. */
    public static boolean isTaken(Level level, BlockPos pos) {
        return !level.getEntitiesOfClass(Seat.class, new AABB(pos), Entity::isVehicle).isEmpty();
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel && (!isVehicle() || !(level().getBlockState(blockPosition()).getBlock() instanceof SeatBlock))) {
            ejectPassengers();
            discard();
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        // up and out in front of the seat, or back where they were if that is blocked
        Direction facing = Direction.fromYRot(getYRot());
        BlockPos front = blockPosition().relative(facing);
        Vec3 out = Vec3.atBottomCenterOf(front);
        if (level().noCollision(passenger, passenger.getDimensions(passenger.getPose()).makeBoundingBox(out))) {
            return out;
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
