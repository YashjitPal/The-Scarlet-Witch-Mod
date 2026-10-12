package com.yashjit.scarlet.entity;

import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.hex.Hex;
import com.yashjit.scarlet.hex.HexData;
import com.yashjit.scarlet.hex.HexShape;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.registry.ScarletEntities;
import com.yashjit.scarlet.registry.ScarletItems;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A car: a different car every era, in that era's colors, the way the car outside Wanda's house changes with every
 * episode. Outside a Hex it is today's car. Parked, it is solid like a boat, and still; anyone can get in and drive it,
 * with someone beside them, forward and back, steering as they go, up a step or a flight of stairs but not a wall.
 *
 * <p>The town parks one in front of every garage it builds; that car is the Hex's, and goes as its wall passes it when
 * the Hex falls or is drawn in, or when it is driven out past the wall. One put down from its item stays, and gives its
 * item back when broken.
 */
public class ParkedCar extends Entity {

    private static final EntityDataAccessor<Integer> ERA = SynchedEntityData.defineId(ParkedCar.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PAINT = SynchedEntityData.defineId(ParkedCar.class, EntityDataSerializers.INT);
    /** Each era's car colors: pastels, then turquoise and red, harvest gold and avocado, red and black, silver, and today's whites and greys. */
    private static final int[][] PAINTS = {
            {0x9ED9C3, 0xF2A7B8, 0x9DC6E3, 0xF3E2A0, 0xC8303A},
            {0x3FB8AF, 0xB3282E, 0xEDEDE8, 0xE8C34A, 0x5E8FC8},
            {0xC9952E, 0x7C8A3A, 0x6B4423, 0xC1601F, 0xD8C9A6},
            {0xB82E2E, 0x2A2A2E, 0xB8BCC2, 0x2B3E6B, 0xEDEDE8},
            {0xBFC3C8, 0xD8C9A6, 0x28406B, 0x2E5A3E, 0x8A1F2A},
            {0xEEEEEC, 0x7D8086, 0x2A2B2F, 0x8E1F2A, 0x3D6FB6}
    };
    /** How often a car looks round for the Hex it is in, in ticks. */
    private static final int LOOK_EVERY = 20;
    /** Blocks a tick, flat out forward, and backing up. */
    private static final float TOP_SPEED = 0.55F;
    private static final float REVERSE_SPEED = 0.18F;
    private static final float ACCELERATION = 0.014F;
    private static final float BRAKING = 0.045F;
    /** How much of its speed it keeps each tick with nothing pressed. */
    private static final float ROLL_ON = 0.955F;
    /** Degrees a tick it turns at full lock, once it is going. */
    private static final float STEERING = 4.5F;
    private static final double GRAVITY = 0.08;
    /** Where the two front seats are: how far to each side of the middle, how far forward of it, and how high. */
    private static final double SEAT_ACROSS = 0.42;
    private static final double SEAT_FORWARD = 0.18;
    private static final double SEAT_HEIGHT = 0.05;

    /** Whether the town parked it, so it goes with the town. */
    private boolean town;
    /** Whether anyone has driven it, so it settles to the ground wherever it is left. */
    private boolean driven;
    /** Its speed along the way it faces, in blocks a tick, while it is driven here. */
    private float speed;
    /** How far it turned this tick, for whoever rides in it to turn with it. */
    private float turned;

    public ParkedCar(EntityType<? extends ParkedCar> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    /**
     * Parks a car, its front the way given.
     *
     * @param paint which of each era's colors it is painted, steadily
     * @param town  whether it belongs to the town of the Hex around it
     */
    public static ParkedCar park(ServerLevel level, Vec3 at, float yaw, int paint, boolean town) {
        ParkedCar car = new ParkedCar(ScarletEntities.PARKED_CAR.get(), level);
        car.setPos(at);
        car.setYRot(yaw);
        car.entityData.set(PAINT, paint);
        car.town = town;
        car.entityData.set(ERA, eraAround(level, at).ordinal());
        level.addFreshEntity(car);
        return car;
    }

    public Era era() {
        return Era.byIndex(entityData.get(ERA));
    }

    /** The color of its paint in the era it is in now. */
    public int color() {
        int[] paints = PAINTS[era().ordinal()];
        return paints[Math.floorMod(entityData.get(PAINT), paints.length)];
    }

    /** How fast it is going, in blocks a tick, as this side has it. */
    public float speed() {
        return speed;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ERA, Era.PRESENT.ordinal());
        builder.define(PAINT, 0);
    }

    @Override
    public void tick() {
        super.tick();
        turned = 0.0F;
        if (isLocalInstanceAuthoritative()) {
            if (getControllingPassenger() instanceof Player driver) {
                drive(driver);
                move(MoverType.SELF, getDeltaMovement());
                if (horizontalCollision && Math.abs(speed) > 0.2F) {
                    level().playLocalSound(getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.NEUTRAL, 0.5F, 1.6F, false);
                }
                if (horizontalCollision) {
                    speed *= 0.3F;
                }
            } else {
                speed = 0.0F;
                if (driven && !onGround()) {
                    setDeltaMovement(0.0, Math.max(-2.0, getDeltaMovement().y - GRAVITY), 0.0);
                    move(MoverType.SELF, getDeltaMovement());
                } else {
                    setDeltaMovement(Vec3.ZERO);
                }
            }
        }
        if (!(level() instanceof ServerLevel level) || (tickCount + getId()) % LOOK_EVERY != 0) {
            return;
        }
        Hex hex = hexAround(level, position());
        if (town && hex == null) {
            vanish(level);
            return;
        }
        int era = (hex == null ? Era.PRESENT : hex.era()).ordinal();
        if (entityData.get(ERA) != era) {
            entityData.set(ERA, era);
        }
    }

    /**
     * One tick at the wheel: the pedals forward and back, the wheel left and right, which turns it more the faster it
     * goes and the other way backing up.
     */
    private void drive(Player driver) {
        float pedal = driver.zza;
        float wheel = driver.xxa;
        if (pedal > 0.0F) {
            speed = speed < 0.0F ? Math.min(0.0F, speed + BRAKING) : Math.min(TOP_SPEED, speed + ACCELERATION);
        } else if (pedal < 0.0F) {
            speed = speed > 0.0F ? Math.max(0.0F, speed - BRAKING) : Math.max(-REVERSE_SPEED, speed - ACCELERATION * 0.6F);
        } else {
            speed *= ROLL_ON;
            if (Math.abs(speed) < 0.004F) {
                speed = 0.0F;
            }
        }
        if (speed != 0.0F && wheel != 0.0F) {
            float grip = Mth.clamp(Math.abs(speed) / 0.22F, 0.25F, 1.0F);
            turned = -wheel * STEERING * grip * Math.signum(speed);
            setYRot(getYRot() + turned);
        }
        Vec3 heading = Vec3.directionFromRotation(0.0F, getYRot());
        double fall = onGround() ? -GRAVITY * 0.5 : Math.max(-2.0, getDeltaMovement().y - GRAVITY);
        setDeltaMovement(heading.x * speed, fall, heading.z * speed);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player.isSecondaryUseActive() || !canAddPassenger(player) || player.isPassenger()) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide()) {
            if (!player.startRiding(this)) {
                return InteractionResult.PASS;
            }
            driven = true;
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 0.6F, 1.3F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < 2 && passenger instanceof Player;
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player driver ? driver : null;
    }

    /** The driver on the left, the other on the right, both in the front seats. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        double across = getPassengers().indexOf(passenger) <= 0 ? SEAT_ACROSS : -SEAT_ACROSS;
        return new Vec3(across, SEAT_HEIGHT, SEAT_FORWARD).yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        super.positionRider(passenger, move);
        // everyone in it turns as it does
        passenger.setYRot(passenger.getYRot() + turned);
        passenger.setYHeadRot(passenger.getYHeadRot() + turned);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        // out of their own door, or over the top if a wall is in the way
        double side = getPassengers().indexOf(passenger) <= 0 ? 1.5 : -1.5;
        Vec3 out = position().add(new Vec3(side, 0.0, SEAT_FORWARD).yRot(-getYRot() * Mth.DEG_TO_RAD));
        if (level().noCollision(passenger, passenger.getDimensions(passenger.getPose()).makeBoundingBox(out))) {
            return out;
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public float maxUpStep() {
        return 1.05F;
    }

    /** The town's car goes the way its town does: in a scatter of scarlet. */
    private void vanish(ServerLevel level) {
        level.sendParticles(new DustParticleOptions(0xE0143C, 1.4F), getX(), getY() + 0.7, getZ(), 30, 1.1, 0.5, 1.6, 0.0);
        discard();
    }

    private static @Nullable Hex hexAround(ServerLevel level, Vec3 at) {
        long now = level.getGameTime();
        for (Hex hex : HexData.of(level).all()) {
            if (HexShape.contains(hex.center(), Hexes.wallRadius(hex, now), at)) {
                return hex;
            }
        }
        return null;
    }

    private static Era eraAround(ServerLevel level, Vec3 at) {
        Hex hex = hexAround(level, at);
        return hex == null ? Era.PRESENT : hex.era();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (!(source.getEntity() instanceof Player player) || isRemoved() || hasPassenger(player)) {
            return false;
        }
        // the town's own car can only be taken away by someone in creative, and gives nothing
        if (town && !player.isCreative()) {
            return false;
        }
        if (!town && !player.isCreative()) {
            spawnAtLocation(level, ScarletItems.PARKED_CAR.get());
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 0.8F, 0.8F);
        discard();
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        entityData.set(PAINT, input.getIntOr("paint", 0));
        entityData.set(ERA, Math.clamp(input.getIntOr("era", Era.PRESENT.ordinal()), 0, Era.values().length - 1));
        town = input.getBooleanOr("town", false);
        driven = input.getBooleanOr("driven", false);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("paint", entityData.get(PAINT));
        output.putInt("era", entityData.get(ERA));
        output.putBoolean("town", town);
        output.putBoolean("driven", driven);
    }
}
