package com.yashjit.scarlet.entity;

import com.yashjit.scarlet.decor.SeatBlock;
import com.yashjit.scarlet.registry.ScarletEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
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
 *
 * <p>It has to be saved, as nothing can ride what isn't, but one loaded back in stands whoever was on it up again.
 */
public class Seat extends Entity {

    /** How high a person's hips are over their soles: how far below the cushion their feet go, for the hips to rest on it. */
    private static final double HIP_HEIGHT = 0.75;
    /** How far back from the middle of the seat they sit, against its back. */
    private static final double SIT_BACK = 0.1;
    /** How far round from the way the seat faces whoever sits on it can turn to look, in degrees. */
    private static final float MOST_TURN = 100.0F;

    private boolean loaded;

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
        if (level() instanceof ServerLevel && (loaded || !isVehicle() || !(level().getBlockState(blockPosition()).getBlock() instanceof SeatBlock))) {
            ejectPassengers();
            discard();
        }
    }

    /** Sat down facing the way the seat does, on whichever side is told of it, a player's own game too. */
    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        float yaw = getYRot();
        passenger.setYRot(yaw);
        passenger.yRotO = yaw;
        passenger.setYHeadRot(yaw);
        if (passenger instanceof LivingEntity living) {
            living.setYBodyRot(yaw);
            living.yBodyRotO = yaw;
        }
    }

    /** Their body faces the way the seat does, and their head turns only so far from it. */
    @Override
    public void onPassengerTurned(Entity passenger) {
        face(passenger);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        super.positionRider(passenger, move);
        face(passenger);
    }

    private void face(Entity passenger) {
        float yaw = getYRot();
        passenger.setYBodyRot(yaw);
        float turned = Mth.wrapDegrees(passenger.getYRot() - yaw);
        float allowed = Mth.clamp(turned, -MOST_TURN, MOST_TURN);
        if (allowed != turned) {
            passenger.yRotO += allowed - turned;
            passenger.setYRot(passenger.getYRot() + allowed - turned);
            passenger.setYHeadRot(passenger.getYRot());
        }
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        // townspeople are drawn as people, whatever mob they really are, so they sit the way a player does
        Direction facing = Direction.fromYRot(getYRot());
        return passenger.getVehicleAttachmentPoint(this).add(-facing.getStepX() * SIT_BACK, -HIP_HEIGHT, -facing.getStepZ() * SIT_BACK);
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
        loaded = true;
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
