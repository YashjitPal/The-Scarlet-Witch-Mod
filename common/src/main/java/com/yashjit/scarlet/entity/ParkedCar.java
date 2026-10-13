package com.yashjit.scarlet.entity;

import com.yashjit.scarlet.decor.HexMade;
import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.hex.Hex;
import com.yashjit.scarlet.hex.HexData;
import com.yashjit.scarlet.hex.HexShape;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.registry.ScarletEntities;
import com.yashjit.scarlet.registry.ScarletItems;
import com.yashjit.scarlet.registry.ScarletSounds;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
    private static final float TOP_SPEED = 0.62F;
    private static final float REVERSE_SPEED = 0.2F;
    /** How fast it picks up speed from a standstill, easing off as it nears its top speed, and how hard it brakes. */
    private static final float ACCELERATION = 0.024F;
    private static final float BRAKING = 0.06F;
    /** How much of its speed it keeps each tick with nothing pressed. */
    private static final float ROLL_ON = 0.97F;
    /** Degrees a tick it turns with the wheel hard over, once it is going. */
    private static final float STEERING = 4.0F;
    /** How much of the way the wheel comes round to where its driver turns it each tick. */
    private static final float STEER_RATE = 0.3F;
    private static final double GRAVITY = 0.08;
    /** The tallest step its wheels climb, and so the tallest its nose or tail goes over rather than stopping at. */
    private static final double STEP = 1.05;
    /** How far its body tips at most, to a slope under it or out of a turn, in degrees. */
    private static final float MOST_TIP = 18.0F;
    private static final float MOST_LEAN = 3.0F;
    /** How soon its horn sounds again, and the lower of its two notes in each era. */
    private static final int HORN_EVERY = 6;
    private static final float[] HORN_PITCHES = {0.53F, 0.56F, 0.6F, 0.71F, 0.75F, 0.8F};

    /** Whether the town parked it, so it goes with the town. */
    private boolean town;
    /** Whose Hex made it out of thin air, if one did: it goes the moment it is outside that Hex. */
    private @Nullable UUID madeBy;
    /** Whether anyone has driven it, so it settles to the ground wherever it is left. */
    private boolean driven;
    /** Its speed along the way it faces, in blocks a tick, while it is driven here. */
    private float speed;
    /** How far it turned this tick, for whoever rides in it to turn with it. */
    private float turned;
    /** The wheel as its driver holds it, from hard left at 1 to hard right at -1. */
    private float wheel;
    private long hornedAt = Long.MIN_VALUE / 2;
    // how it looks, on the client: how far its wheels have turned, its steering, the ground under each axle over its
    // own feet, and how far it leans out of a turn
    private float spin;
    private float spinO;
    private float steer;
    private float steerO;
    private float frontRise;
    private float frontRiseO;
    private float rearRise;
    private float rearRiseO;
    private float roll;
    private float rollO;

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

    public void madeBy(@Nullable UUID caster) {
        madeBy = caster;
    }

    public CarShape shape() {
        return CarShape.of(era());
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

    /** How far its wheels have turned, in radians, backward as it rolls forward. */
    public float spin(float partialTick) {
        return Mth.lerp(partialTick, spinO, spin);
    }

    /** Its steering, from hard left at 1 to hard right at -1. */
    public float steer(float partialTick) {
        return Mth.lerp(partialTick, steerO, steer);
    }

    /** How high the ground stands under its front axle, over its own feet, in blocks. */
    public float frontRise(float partialTick) {
        return Mth.lerp(partialTick, frontRiseO, frontRise);
    }

    public float rearRise(float partialTick) {
        return Mth.lerp(partialTick, rearRiseO, rearRise);
    }

    /** How far it leans out of a turn, in degrees, its right side down for positive. */
    public float roll(float partialTick) {
        return Mth.lerp(partialTick, rollO, roll);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ERA, Era.PRESENT.ordinal());
        builder.define(PAINT, 0);
    }

    /** Seen driven by someone else, it glides between where it is told it is, the way a boat does. */
    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, 3);
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
                    bump();
                }
                if (horizontalCollision) {
                    speed *= 0.3F;
                }
            } else {
                speed = 0.0F;
                wheel = 0.0F;
                if (driven && !onGround()) {
                    setDeltaMovement(0.0, Math.max(-2.0, getDeltaMovement().y - GRAVITY), 0.0);
                    move(MoverType.SELF, getDeltaMovement());
                } else {
                    setDeltaMovement(Vec3.ZERO);
                }
            }
        }
        if (level().isClientSide()) {
            look();
        }
        if (!(level() instanceof ServerLevel level) || (tickCount + getId()) % LOOK_EVERY != 0) {
            return;
        }
        Hex hex = hexAround(level, position());
        // the town's own, never driven off, goes with the lot it was parked on when that is taken back
        boolean lotGone = town && !driven && hex != null && hex.town() != null && !hex.town().isBuilt(blockPosition().below().asLong());
        if (town && hex == null || lotGone || madeBy != null && !HexMade.inside(level, madeBy, position())) {
            vanish(level);
            return;
        }
        int era = (hex == null ? Era.PRESENT : hex.era()).ordinal();
        if (entityData.get(ERA) != era) {
            entityData.set(ERA, era);
        }
    }

    /**
     * One tick at the wheel: the pedals forward and back, and the wheel left and right, coming round to where its driver
     * turns it, which turns the car more the faster it goes, a little less hard at speed, and the other way backing up.
     * Its nose, or its tail backing up, stops at a wall it could not climb.
     */
    private void drive(Player driver) {
        float pedal = driver.zza;
        wheel += (Mth.clamp(driver.xxa, -1.0F, 1.0F) - wheel) * STEER_RATE;
        if (Math.abs(wheel) < 0.002F) {
            wheel = 0.0F;
        }
        if (pedal > 0.0F) {
            speed = speed < 0.0F ? Math.min(0.0F, speed + BRAKING) : Math.min(TOP_SPEED, speed + ACCELERATION * (1.0F - 0.75F * speed / TOP_SPEED));
        } else if (pedal < 0.0F) {
            speed = speed > 0.0F ? Math.max(0.0F, speed - BRAKING) : Math.max(-REVERSE_SPEED, speed - ACCELERATION * 0.6F);
        } else {
            speed *= ROLL_ON;
            if (Math.abs(speed) < 0.004F) {
                speed = 0.0F;
            }
        }
        if (speed != 0.0F && wheel != 0.0F) {
            float grip = Mth.clamp(Math.abs(speed) / 0.2F, 0.3F, 1.0F);
            float lock = 1.0F - 0.2F * Math.abs(speed) / TOP_SPEED;
            turned = -wheel * STEERING * grip * lock * Math.signum(speed);
            setYRot(getYRot() + turned);
        }
        Vec3 heading = Vec3.directionFromRotation(0.0F, getYRot());
        double fall = onGround() ? -GRAVITY * 0.5 : Math.max(-2.0, getDeltaMovement().y - GRAVITY);
        Vec3 motion = new Vec3(heading.x * speed, fall, heading.z * speed);
        if (speed != 0.0F && blockedAhead(heading, motion)) {
            if (Math.abs(speed) > 0.2F) {
                bump();
            }
            speed = 0.0F;
            motion = new Vec3(0.0, fall, 0.0);
        }
        setDeltaMovement(motion);
    }

    /**
     * Whether its end, its nose going forward or its tail backing up, would run into more than a step over the ground
     * under the axle behind it: a wall rather than a flight of stairs. Its box is only as long as it is wide, so its
     * ends need looking out for.
     */
    private boolean blockedAhead(Vec3 heading, Vec3 motion) {
        CarShape shape = shape();
        double direction = Math.signum(speed);
        // along it forward, the nose is at -length/2 and the axles at -frontAxle and -rearAxle, in sixteenths
        double end = shape.halfLength() * direction;
        double axle = (direction > 0 ? -shape.frontAxle() : -shape.rearAxle()) / 16.0;
        double atEnd = groundAt(getX() + motion.x + heading.x * end, getZ() + motion.z + heading.z * end);
        double atAxle = groundAt(getX() + motion.x + heading.x * axle, getZ() + motion.z + heading.z * axle);
        return atEnd - atAxle > STEP;
    }

    /**
     * How high the ground stands at a point, looking down from just over the car's roof to a step below its feet; just
     * over the roof itself if a wall stands that high there.
     */
    private double groundAt(double x, double z) {
        double top = getY() + shape().height() + 0.15;
        Vec3 from = new Vec3(x, top, z);
        Vec3 to = new Vec3(x, getY() - 1.6, z);
        BlockHitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS ? to.y : hit.getLocation().y;
    }

    private void bump() {
        level().playLocalSound(getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.NEUTRAL, 0.5F, 1.6F, false);
    }

    /**
     * Its driver sounding its horn: two notes at once a third apart, deeper the older the car, heard all round it.
     */
    public static void honk(ServerPlayer player) {
        if (!(player.getVehicle() instanceof ParkedCar car) || car.getControllingPassenger() != player) {
            return;
        }
        long now = car.level().getGameTime();
        if (now - car.hornedAt < HORN_EVERY) {
            return;
        }
        car.hornedAt = now;
        float pitch = HORN_PITCHES[car.era().ordinal()];
        car.level().playSound(null, car.getX(), car.getY() + 0.6, car.getZ(), ScarletSounds.CAR_HORN.get(), SoundSource.NEUTRAL, 1.4F, pitch);
        car.level().playSound(null, car.getX(), car.getY() + 0.6, car.getZ(), ScarletSounds.CAR_HORN.get(), SoundSource.NEUTRAL, 1.1F, pitch * 1.26F);
    }

    /**
     * How it looks, worked out on the client: its wheels turned as far as it has rolled; its steering as its driver turns
     * it, or as it is seen turning; its body tipped to the ground under its axles; and leaning out of its turns.
     */
    private void look() {
        CarShape shape = shape();
        spinO = spin;
        steerO = steer;
        frontRiseO = frontRise;
        rearRiseO = rearRise;
        rollO = roll;
        Vec3 heading = Vec3.directionFromRotation(0.0F, getYRot());
        double rolled = (getX() - xo) * heading.x + (getZ() - zo) * heading.z;
        spin -= (float) (rolled * 16.0 / shape.wheelRadius());
        if (Math.abs(spin) > Mth.TWO_PI) {
            float wrap = Math.signum(spin) * Mth.TWO_PI;
            spin -= wrap;
            spinO -= wrap;
        }
        float target;
        if (isLocalInstanceAuthoritative() && getControllingPassenger() instanceof Player) {
            target = wheel;
        } else if (Math.abs(rolled) > 0.01) {
            target = Mth.clamp(-Mth.wrapDegrees(getYRot() - yRotO) / STEERING, -1.0F, 1.0F) * (float) Math.signum(rolled);
        } else {
            target = steer;
        }
        steer += (target - steer) * 0.5F;
        // the ground under each axle, a step up or down at most, or level where it falls away further
        float front = rise(heading, -shape.frontAxle() / 16.0);
        float rear = rise(heading, -shape.rearAxle() / 16.0);
        float wheelbase = (shape.rearAxle() - shape.frontAxle()) / 16.0F;
        float most = (float) Math.tan(Math.toRadians(MOST_TIP)) * wheelbase;
        float tip = Mth.clamp(front - rear, -most, most);
        float middle = (front + rear) / 2;
        frontRise += (middle + tip / 2 - frontRise) * 0.45F;
        rearRise += (middle - tip / 2 - rearRise) * 0.45F;
        float pace = Mth.clamp((float) Math.abs(rolled) / TOP_SPEED, 0.0F, 1.0F);
        roll += (-steer * pace * MOST_LEAN - roll) * 0.2F;
    }

    /** How high the ground stands a way along the car, over its feet: a step up or down at most. */
    private float rise(Vec3 heading, double along) {
        double ground = groundAt(getX() + heading.x * along, getZ() + heading.z * along) - getY();
        return Math.abs(ground) > STEP + 0.05 ? 0.0F : (float) ground;
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
        return shape().seat(getPassengers().indexOf(passenger)).yRot(-getYRot() * Mth.DEG_TO_RAD);
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
        Vec3 seat = shape().seat(getPassengers().indexOf(passenger));
        double side = Math.signum(seat.x) * (shape().halfWidth() + 0.5);
        Vec3 out = position().add(new Vec3(side, 0.0, seat.z).yRot(-getYRot() * Mth.DEG_TO_RAD));
        if (level().noCollision(passenger, passenger.getDimensions(passenger.getPose()).makeBoundingBox(out))) {
            return out;
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public float maxUpStep() {
        return (float) STEP;
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
            ItemStack item = new ItemStack(ScarletItems.PARKED_CAR.get());
            if (madeBy != null) {
                HexMade.mark(item, madeBy);
            }
            spawnAtLocation(level, item);
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
        madeBy = input.read("made_by", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("paint", entityData.get(PAINT));
        output.putInt("era", entityData.get(ERA));
        output.putBoolean("town", town);
        output.putBoolean("driven", driven);
        output.storeNullable("made_by", UUIDUtil.CODEC, madeBy);
    }
}
