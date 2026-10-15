package com.yashjit.scarlet.entity;

import com.yashjit.scarlet.decor.HexMade;
import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.hex.Hex;
import com.yashjit.scarlet.hex.HexData;
import com.yashjit.scarlet.hex.HexShape;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.registry.ScarletDamageTypes;
import com.yashjit.scarlet.registry.ScarletDataComponents;
import com.yashjit.scarlet.registry.ScarletEntities;
import com.yashjit.scarlet.registry.ScarletItems;
import com.yashjit.scarlet.registry.ScarletSounds;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.item.Items;
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
 * <p>It takes a knock like anything made of steel: blows, magic, blasts, fire and lava, crashing into a wall and coming
 * down hard all dent it. It rocks with each, its paint scuffs and darkens, its windows craze, its lamps fail and its
 * hood begins to smoke, until it is wrecked: it goes up in flames, throwing out whoever is in it, and burns out to a
 * blackened shell that goes nowhere again. Whatever it runs into it knocks aside, and hurts the faster it goes.
 *
 * <p>It drives through shallow water slowly, splashing. In water deeper than its doors its engine floods and dies, and
 * it floats a moment before it goes down; brought out again, it dries out and starts in the end.
 *
 * <p>The town parks one in front of every garage it builds; that car is the Hex's, and goes as its wall passes it when
 * the Hex falls or is drawn in, or when it is driven out past the wall. The Hex mends its own, and one it made out of
 * thin air, dents and wrecks alike, and brings back one of the town's that sank. One put down from its item stays, and
 * is its owner's to mend with iron, to pack up again whole with a sneaking click, or to break up for scrap once it is a
 * wreck.
 */
public class ParkedCar extends Entity {

    private static final EntityDataAccessor<Integer> ERA = SynchedEntityData.defineId(ParkedCar.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PAINT = SynchedEntityData.defineId(ParkedCar.class, EntityDataSerializers.INT);
    /** What is left of it, out of {@link #MAX_HEALTH}; at nothing or less, a wreck. */
    private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(ParkedCar.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> WRECKED = SynchedEntityData.defineId(ParkedCar.class, EntityDataSerializers.BOOLEAN);
    /** The game tick water flooded its engine, or -1 while it runs. */
    private static final EntityDataAccessor<Integer> FLOODED_AT = SynchedEntityData.defineId(ParkedCar.class, EntityDataSerializers.INT);
    /** Ticks left of rocking from the last knock, and which way it rocks. */
    private static final EntityDataAccessor<Integer> SHAKE = SynchedEntityData.defineId(ParkedCar.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SHAKE_SIDE = SynchedEntityData.defineId(ParkedCar.class, EntityDataSerializers.INT);
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
    /** How much of its speed it keeps each tick with nothing pressed, and with no one at the wheel at all. */
    private static final float ROLL_ON = 0.97F;
    private static final float COASTS = 0.94F;
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

    /** How much a car takes before it is wrecked, and before a wreck of your own comes apart for scrap. */
    public static final float MAX_HEALTH = 40.0F;
    /** How much of a blow, a shot or a spell its steel takes, against the whole of a blast, a fire, a crash or a fall. */
    private static final float STEEL = 0.5F;
    private static final float SCRAP_AT = -20.0F;
    /** How much an iron ingot hammers back. */
    private static final float REPAIR = 10.0F;
    /** Ticks a wreck burns before it only smolders. */
    private static final int BURNS_FOR = 240;
    /** Ticks with nothing more done to it before the Hex mends its own dents, and before it mends its own wreck or brings back its own sunk car. */
    private static final int MENDS_AFTER = 600;
    private static final int MENDS_WRECK_AFTER = 400;
    /** How deep water stands over its feet before it wades, slower the deeper, and before it floods its engine. */
    private static final double WADES = 0.02;
    private static final double DEEP_WADE = 0.35;
    private static final double FLOODS = 0.8;
    /** Fastest it wades through shallow water and through deeper, as shares of its top speed, and how fast the water drags it down to that. */
    private static final float SHALLOW_PACE = 0.6F;
    private static final float DEEP_PACE = 0.35F;
    private static final float WATER_DRAG = 0.05F;
    /** Ticks it floats once its engine floods, how deep it floats, how fast it goes down after, and how long out of deep water before it starts again. */
    private static final int FLOATS_FOR = 70;
    private static final double FLOAT_DEPTH = 1.0;
    private static final double SINKS = 0.035;
    private static final int DRIES_IN = 100;
    /** Slowest it shoves anyone aside, and hits them hard enough to hurt, in blocks a tick, and how hard at full speed. */
    private static final float SHOVES_FROM = 0.04F;
    private static final float HURTS_FROM = 0.14F;
    private static final float HIT_DAMAGE = 20.0F;
    /** Ticks before it can hit the same one again. */
    private static final int HIT_AGAIN = 10;
    /** Slowest it crashes into a wall hard enough to dent it, how badly, and to hurt whoever is in it. */
    private static final float CRASHES_FROM = 0.25F;
    private static final float CRASH_DAMAGE = 30.0F;
    private static final float HURTS_RIDERS_FROM = 0.42F;
    /** How far it falls before landing dents it, and hurts whoever is in it. */
    private static final double FALL_DENTS = 3.0;
    private static final double FALL_HURTS_RIDERS = 5.0;

    /** Whether the town parked it, so it goes with the town, and where, for the Hex to bring it back to if it sinks. */
    private boolean town;
    private @Nullable Vec3 home;
    private float homeYaw;
    /** Whose Hex made it out of thin air, if one did: it goes the moment it is outside that Hex. */
    private @Nullable UUID madeBy;
    /** Whether anyone has driven it, so it settles to the ground wherever it is left. */
    private boolean driven;
    /** Its speed along the way it faces, in blocks a tick, as whoever moves it has it: told the server by whoever drives it. */
    private float speed;
    /** How far it turned this tick, for whoever rides in it to turn with it. */
    private float turned;
    /** The wheel as its driver holds it, from hard left at 1 to hard right at -1. */
    private float wheel;
    private long hornedAt = Long.MIN_VALUE / 2;
    // on the server: when it was last hurt, wrecked and crashed, how long it has been out of deep water since it flooded,
    // and when it last hit each thing it ran into
    private long hurtAt = Long.MIN_VALUE / 2;
    private long wreckedAt = Long.MIN_VALUE / 2;
    private long crashedAt = Long.MIN_VALUE / 2;
    private int dryFor;
    private final Int2LongOpenHashMap hitAt = new Int2LongOpenHashMap();
    /** On its driver's side: how fast it was going when it last ran into a wall, still to be told the server. */
    private float crash;
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
        if (town) {
            car.home = at;
            car.homeYaw = yaw;
        }
        car.entityData.set(ERA, eraAround(level, at).ordinal());
        level.addFreshEntity(car);
        return car;
    }

    public void madeBy(@Nullable UUID caster) {
        madeBy = caster;
    }

    /** Batters it as badly as it was when it was packed up. */
    public void damaged(float damage) {
        entityData.set(HEALTH, Math.max(1.0F, MAX_HEALTH - damage));
    }

    public Era era() {
        return Era.byIndex(entityData.get(ERA));
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

    /** How much of it is left, from 1 whole to 0 a wreck. */
    public float condition() {
        return Math.clamp(entityData.get(HEALTH) / MAX_HEALTH, 0.0F, 1.0F);
    }

    public boolean wrecked() {
        return entityData.get(WRECKED);
    }

    /** Whether water has flooded its engine, so it goes nowhere until it dries out. */
    public boolean flooded() {
        return entityData.get(FLOODED_AT) >= 0;
    }

    /** Whether it can be driven: neither a wreck nor flooded. */
    public boolean runs() {
        return !wrecked() && !flooded();
    }

    private int floodedFor() {
        return (int) level().getGameTime() - entityData.get(FLOODED_AT);
    }

    /** How far it rocks from its last knock, in degrees. */
    public float shake(float partialTick) {
        float left = entityData.get(SHAKE) - partialTick;
        return left <= 0.0F ? 0.0F : Mth.sin(left * 1.7F) * left * 0.45F * entityData.get(SHAKE_SIDE);
    }

    /** On its driver's side: how hard it last ran into a wall, once, to tell the server. */
    public float takeCrash() {
        float impact = crash;
        crash = 0.0F;
        return impact;
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
        builder.define(HEALTH, MAX_HEALTH);
        builder.define(WRECKED, false);
        builder.define(FLOODED_AT, -1);
        builder.define(SHAKE, 0);
        builder.define(SHAKE_SIDE, 1);
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
        if (level() instanceof ServerLevel level) {
            serverTick(level);
            if (isRemoved()) {
                return;
            }
        }
        if (isLocalInstanceAuthoritative()) {
            advance();
        }
        if (level().isClientSide()) {
            look();
        }
    }

    /**
     * One tick of it moving, on whichever side moves it: its driver's, while someone drives it, else the server's.
     */
    private void advance() {
        double water = getFluidHeight(FluidTags.WATER);
        double lava = getFluidHeight(FluidTags.LAVA);
        Vec3 heading = Vec3.directionFromRotation(0.0F, getYRot());
        if (flooded() || lava > 0.0 || wrecked() && water > 0.0) {
            sink(heading, water, lava);
        } else if (getControllingPassenger() instanceof Player driver && !wrecked()) {
            drive(driver, water);
            move(MoverType.SELF, getDeltaMovement());
            if (horizontalCollision) {
                hitWall(Math.abs(speed));
                speed *= 0.3F;
            }
        } else {
            roll(heading, water);
        }
    }

    /**
     * One tick at the wheel: the pedals forward and back, and the wheel left and right, coming round to where its driver
     * turns it, which turns the car more the faster it goes, a little less hard at speed, and the other way backing up.
     * Water drags it down to a wade. Its nose, or its tail backing up, stops at a wall it could not climb.
     */
    private void drive(Player driver, double water) {
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
        float top = TOP_SPEED * (water > DEEP_WADE ? DEEP_PACE : water > WADES ? SHALLOW_PACE : 1.0F);
        if (Math.abs(speed) > top) {
            speed = Math.signum(speed) * Math.max(top, Math.abs(speed) - WATER_DRAG);
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
            hitWall(Math.abs(speed));
            speed = 0.0F;
            motion = new Vec3(0.0, fall, 0.0);
        }
        setDeltaMovement(motion);
    }

    /**
     * With no one at the wheel it rolls on to a stop, as it was left going, and settles to the ground.
     */
    private void roll(Vec3 heading, double water) {
        wheel = 0.0F;
        if (Math.abs(speed) > 0.004F) {
            speed *= water > WADES ? 0.8F : COASTS;
            double fall = onGround() ? -GRAVITY * 0.5 : Math.max(-2.0, getDeltaMovement().y - GRAVITY);
            Vec3 motion = new Vec3(heading.x * speed, fall, heading.z * speed);
            if (blockedAhead(heading, motion)) {
                hitWall(Math.abs(speed));
                speed = 0.0F;
                motion = new Vec3(0.0, fall, 0.0);
            }
            setDeltaMovement(motion);
            move(MoverType.SELF, motion);
            if (horizontalCollision) {
                hitWall(Math.abs(speed));
                speed = 0.0F;
            }
            return;
        }
        speed = 0.0F;
        if (driven && !onGround()) {
            setDeltaMovement(0.0, Math.max(-2.0, getDeltaMovement().y - GRAVITY), 0.0);
            move(MoverType.SELF, getDeltaMovement());
        } else {
            setDeltaMovement(Vec3.ZERO);
        }
    }

    /**
     * Its engine dead in the water, or in lava: the air still in it holds it up a moment, low in the water, then it goes
     * down, slowly, rolling to a stop as it does.
     */
    private void sink(Vec3 heading, double water, double lava) {
        wheel = 0.0F;
        speed *= 0.86F;
        if (Math.abs(speed) < 0.004F) {
            speed = 0.0F;
        }
        double fluid = Math.max(water, lava);
        double rise = getDeltaMovement().y;
        if (fluid <= 0.0) {
            rise = onGround() ? 0.0 : Math.max(-2.0, rise - GRAVITY);
        } else if (lava <= 0.0 && !wrecked() && floodedFor() < FLOATS_FOR) {
            rise = (rise + (fluid - FLOAT_DEPTH) * 0.05) * 0.75;
        } else {
            rise = Math.max(rise * 0.9 - 0.006, -SINKS);
        }
        Vec3 motion = new Vec3(heading.x * speed, rise, heading.z * speed);
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        if (horizontalCollision) {
            speed = 0.0F;
        }
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

    /**
     * Its nose, or its tail, running into a wall: a bang, and on whichever side moves it, the dent it leaves, which its
     * driver's own game tells the server of.
     */
    private void hitWall(float impact) {
        if (impact > 0.2F) {
            level().playLocalSound(getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.NEUTRAL, 0.5F, 1.6F, false);
        }
        if (level() instanceof ServerLevel server) {
            crash(server, impact);
        } else {
            crash = Math.max(crash, impact);
        }
    }

    /**
     * Its driver's own game telling the server how fast it goes, and how hard it just ran into a wall, if it did.
     */
    public static void reported(ServerPlayer player, float speed, float crash) {
        if (!(player.getVehicle() instanceof ParkedCar car) || car.getControllingPassenger() != player || !Float.isFinite(speed) || !Float.isFinite(crash)) {
            return;
        }
        car.speed = Mth.clamp(speed, -REVERSE_SPEED, TOP_SPEED);
        if (crash > 0.0F) {
            car.crash(player.level(), Math.min(crash, TOP_SPEED));
        }
    }

    /** Running into a wall hard enough dents it, and harder still jolts whoever is in it. */
    private void crash(ServerLevel level, float impact) {
        long now = level.getGameTime();
        if (impact <= CRASHES_FROM || now - crashedAt < 10) {
            return;
        }
        crashedAt = now;
        level.playSound(null, getX(), getY() + 0.5, getZ(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.NEUTRAL, 1.0F, 0.75F);
        if (impact > HURTS_RIDERS_FROM) {
            for (Entity rider : getPassengers()) {
                rider.hurtServer(level, level.damageSources().flyIntoWall(), (impact - HURTS_RIDERS_FROM) * 10.0F);
            }
        }
        hurtServer(level, level.damageSources().flyIntoWall(), (impact - CRASHES_FROM) * CRASH_DAMAGE);
    }

    /**
     * Whatever its nose, or its tail backing up, comes into it knocks on ahead of it and off to the side, and hurts the
     * faster it goes; a body slows it, the heavier the more.
     */
    private void runOver(ServerLevel level) {
        LivingEntity driver = getControllingPassenger();
        Vec3 heading = Vec3.directionFromRotation(0.0F, getYRot());
        Vec3 across = new Vec3(-heading.z, 0.0, heading.x);
        CarShape shape = shape();
        double halfLength = shape.halfLength();
        double halfWidth = shape.halfWidth();
        double top = getY() + shape.height();
        float impact = Math.abs(speed);
        double ahead = Math.signum(speed);
        long now = level.getGameTime();
        for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(halfLength + 1.0, 0.0, halfLength + 1.0),
                living -> living.isAlive() && !living.isSpectator() && !living.isPassenger() && !living.isVehicle())) {
            Vec3 offset = hit.position().subtract(position());
            double along = (offset.x * heading.x + offset.z * heading.z) * ahead;
            double side = offset.x * across.x + offset.z * across.z;
            double reach = hit.getBbWidth() / 2.0;
            if (along <= 0.0 || along - reach > halfLength + impact + 0.1 || Math.abs(side) > halfWidth + reach
                    || hit.getY() > top || hit.getBoundingBox().maxY < getY() + 0.1) {
                continue;
            }
            if (now - hitAt.getOrDefault(hit.getId(), Long.MIN_VALUE / 2) < HIT_AGAIN) {
                continue;
            }
            hitAt.put(hit.getId(), now);
            if (impact >= HURTS_FROM) {
                hit.hurtServer(level, ScarletDamageTypes.car(level, this, driver), (impact - HURTS_FROM * 0.5F) * HIT_DAMAGE);
            }
            Vec3 thrown = heading.scale(ahead * (0.25 + impact * 1.4)).add(across.scale(Math.signum(side) * (0.1 + impact * 0.5)))
                    .add(0.0, 0.12 + impact * 0.55, 0.0);
            hit.setDeltaMovement(hit.getDeltaMovement().scale(0.2).add(thrown));
            hit.syncVelocity = true;
            level.playSound(null, hit.getX(), hit.getY() + 0.5, hit.getZ(), impact >= HURTS_FROM ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_WEAK,
                    SoundSource.NEUTRAL, 0.9F, 0.75F);
            float mass = hit.getBbWidth() * hit.getBbWidth() * hit.getBbHeight();
            speed *= Mth.clamp(1.0F - mass * 0.15F, 0.25F, 0.9F);
            if (mass > 3.0F && impact >= HURTS_FROM) {
                // something as big as a car itself: it gives as good as it gets
                hurtServer(level, level.damageSources().flyIntoWall(), impact * 12.0F);
            }
        }
        if (now % 40 == 0) {
            hitAt.values().removeIf(at -> now - at > HIT_AGAIN);
        }
    }

    private void serverTick(ServerLevel level) {
        long now = level.getGameTime();
        int shake = entityData.get(SHAKE);
        if (shake > 0) {
            entityData.set(SHAKE, shake - 1);
        }
        if (wrecked() && isVehicle()) {
            ejectPassengers();
        }
        flooding(level);
        if (Math.abs(speed) > SHOVES_FROM && !wrecked()) {
            runOver(level);
        }
        if ((tickCount + getId()) % LOOK_EVERY != 0) {
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
        if (hex != null && (town || madeBy != null)) {
            mendIfDue(level, now);
        }
    }

    /**
     * Water deeper than its doors floods its engine and kills it; out of it a while, it dries out and starts again.
     */
    private void flooding(ServerLevel level) {
        double water = getFluidHeight(FluidTags.WATER);
        if (!flooded()) {
            if (water > FLOODS) {
                entityData.set(FLOODED_AT, (int) level.getGameTime());
                dryFor = 0;
                level.playSound(null, getX(), getY() + 0.6, getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.9F, 0.55F);
                level.playSound(null, getX(), getY() + 0.6, getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.NEUTRAL, 1.0F, 0.7F);
                tellRiders("car.scarlet.flooded");
            }
        } else if (water < DEEP_WADE) {
            if (++dryFor >= DRIES_IN) {
                entityData.set(FLOODED_AT, -1);
                level.playSound(null, getX(), getY() + 0.6, getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.35F, 0.5F);
                tellRiders("car.scarlet.dried");
            }
        } else {
            dryFor = 0;
        }
    }

    private void tellRiders(String key) {
        for (Entity rider : getPassengers()) {
            if (rider instanceof ServerPlayer player) {
                player.sendOverlayMessage(Component.translatable(key));
            }
        }
    }

    /**
     * The Hex mends its own: its dents once nothing more is done to it for a while, its wreck once it has burned out, and
     * one of the town's that sank it brings back to where the town parked it.
     */
    private void mendIfDue(ServerLevel level, long now) {
        boolean sunk = flooded() && getFluidHeight(FluidTags.WATER) > FLOODS;
        boolean due;
        if (wrecked()) {
            due = now - wreckedAt > MENDS_WRECK_AFTER && now - hurtAt > LOOK_EVERY;
        } else if (sunk) {
            due = floodedFor() > MENDS_WRECK_AFTER;
        } else {
            due = entityData.get(HEALTH) < MAX_HEALTH && now - hurtAt > MENDS_AFTER;
        }
        if (!due) {
            return;
        }
        if (sunk && home == null) {
            // one it made, gone to the bottom: it goes back to where it came from
            vanish(level);
            return;
        }
        glitch(level);
        if (sunk) {
            setPos(home);
            setYRot(homeYaw);
            driven = false;
        }
        entityData.set(HEALTH, MAX_HEALTH);
        entityData.set(WRECKED, false);
        entityData.set(FLOODED_AT, -1);
        clearFire();
        speed = 0.0F;
        setDeltaMovement(Vec3.ZERO);
        glitch(level);
        level.playSound(null, getX(), getY() + 0.6, getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.2F);
    }

    /** A flicker of the Hex's red where it mends its car. */
    private void glitch(ServerLevel level) {
        level.sendParticles(new DustParticleOptions(0xE0143C, 1.2F), getX(), getY() + 0.7, getZ(), 40, 1.0, 0.5, 1.5, 0.0);
        level.playSound(null, getX(), getY() + 0.6, getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.9F, 0.6F);
        level.playSound(null, getX(), getY() + 0.6, getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8F, 0.8F);
    }

    /**
     * Its driver sounding its horn: two notes at once a third apart, deeper the older the car, heard all round it.
     */
    public static void honk(ServerPlayer player) {
        if (!(player.getVehicle() instanceof ParkedCar car) || car.getControllingPassenger() != player || !car.runs()) {
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
     * it, or as it is seen turning; its body tipped to the ground under its axles; leaning out of its turns; and smoking,
     * burning, splashing or bubbling as it is.
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
        fumes(heading, shape, rolled);
    }

    /**
     * Smoke from under its hood once it is battered, black and thick once it is near wrecked; fire and smoke from a
     * wreck, then only a smolder; spray thrown up from its wheels wading; and bubbles from one gone under.
     */
    private void fumes(Vec3 heading, CarShape shape, double rolled) {
        RandomSource random = this.random;
        Vec3 hood = position().add(heading.scale(-shape.frontAxle() / 16.0)).add(0.0, shape.hood() / 16.0, 0.0);
        float condition = condition();
        if (wrecked()) {
            boolean burning = isOnFire();
            if (random.nextFloat() < (burning ? 0.9F : 0.25F)) {
                Vec3 at = position().add((random.nextDouble() - 0.5) * shape.halfWidth() * 1.6, shape.belt() / 16.0, 0.0)
                        .add(heading.scale((random.nextDouble() - 0.5) * shape.halfLength() * 1.6));
                level().addParticle(burning ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, at.x, at.y, at.z, 0.0, 0.04, 0.0);
            }
            for (int k = 0; burning && k < 3; k++) {
                // flames licking up out of it, all along it, thickest out of its middle
                double across = (random.nextDouble() - 0.5) * shape.halfWidth() * 1.6;
                double along = random.nextGaussian() * shape.halfLength() * 0.45;
                Vec3 at = position().add(-heading.z * across, shape.sill() / 16.0 + random.nextDouble() * shape.belt() / 16.0, heading.x * across)
                        .add(heading.scale(along));
                level().addParticle(ParticleTypes.FLAME, at.x, at.y, at.z, random.nextGaussian() * 0.01, 0.03 + random.nextDouble() * 0.03,
                        random.nextGaussian() * 0.01);
            }
        } else if (condition < 0.5F && random.nextFloat() < (0.5F - condition) * 1.6F) {
            level().addParticle(condition < 0.25F ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, hood.x + (random.nextDouble() - 0.5) * 0.4,
                    hood.y, hood.z + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.03, 0.0);
            if (condition < 0.25F && random.nextFloat() < 0.08F) {
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, hood.x, hood.y, hood.z, random.nextGaussian() * 0.1, 0.1, random.nextGaussian() * 0.1);
            }
        }
        double water = getFluidHeight(FluidTags.WATER);
        if (water > WADES && water < FLOODS && Math.abs(rolled) > 0.03) {
            for (int k = 0; k < 4; k++) {
                boolean front = k < 2;
                double along = -(front ? shape.frontAxle() : shape.rearAxle()) / 16.0;
                double side = (k % 2 == 0 ? 1 : -1) * shape.track() / 16.0;
                Vec3 at = position().add(heading.scale(along)).add(-heading.z * side, water, heading.x * side);
                level().addParticle(ParticleTypes.SPLASH, at.x, at.y, at.z, 0.0, 0.0, 0.0);
            }
        }
        if (flooded() && water > FLOODS && random.nextFloat() < 0.5F) {
            level().addParticle(ParticleTypes.BUBBLE, getX() + (random.nextDouble() - 0.5) * 1.6, getY() + 0.4 + random.nextDouble() * 0.8,
                    getZ() + (random.nextDouble() - 0.5) * 1.6, 0.0, 0.1, 0.0);
        }
    }

    /** How high the ground stands a way along the car, over its feet: a step up or down at most. */
    private float rise(Vec3 heading, double along) {
        double ground = groundAt(getX() + heading.x * along, getZ() + heading.z * along) - getY();
        return Math.abs(ground) > STEP + 0.05 ? 0.0F : (float) ground;
    }

    /**
     * A click gets in, unless it is a wreck; an iron ingot hammers out its dents; and a sneaking click with an empty
     * hand packs one of your own up again, dents and all.
     */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.IRON_INGOT) && !wrecked() && entityData.get(HEALTH) < MAX_HEALTH) {
            if (level() instanceof ServerLevel server) {
                repair(server, player, held);
            }
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            if (hand != InteractionHand.MAIN_HAND || !held.isEmpty() || isVehicle()) {
                return InteractionResult.PASS;
            }
            if (level() instanceof ServerLevel server) {
                packUp(server, player);
            }
            return InteractionResult.SUCCESS;
        }
        if (wrecked()) {
            if (!level().isClientSide()) {
                player.sendOverlayMessage(Component.translatable("car.scarlet.wreck"));
            }
            return InteractionResult.SUCCESS;
        }
        if (!canAddPassenger(player) || player.isPassenger()) {
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

    private void repair(ServerLevel level, Player player, ItemStack ingot) {
        float health = Math.min(MAX_HEALTH, entityData.get(HEALTH) + REPAIR);
        entityData.set(HEALTH, health);
        ingot.consume(1, player);
        level.playSound(null, getX(), getY() + 0.5, getZ(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.6F, 1.25F);
        level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.8, getZ(), 12, 0.8, 0.3, 0.8, 0.1);
        player.sendOverlayMessage(Component.translatable("car.scarlet.repaired", Math.round(health / MAX_HEALTH * 100.0F)));
    }

    private void packUp(ServerLevel level, Player player) {
        if (town) {
            player.sendOverlayMessage(Component.translatable("car.scarlet.town"));
            return;
        }
        if (wrecked()) {
            player.sendOverlayMessage(Component.translatable("car.scarlet.wreck"));
            return;
        }
        ItemStack item = new ItemStack(ScarletItems.PARKED_CAR.get());
        if (madeBy != null) {
            HexMade.mark(item, madeBy);
        }
        float damage = MAX_HEALTH - entityData.get(HEALTH);
        if (damage > 0.01F) {
            item.set(ScarletDataComponents.CAR_DAMAGE.get(), damage);
        }
        if (!player.getInventory().add(item)) {
            spawnAtLocation(level, item);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 0.8F, 0.8F);
        discard();
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < 2 && passenger instanceof Player && !wrecked();
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

    /**
     * A knock of any kind dents it, but for someone in creative taking it away with their own hands. A wreck of the
     * Hex's own waits for the Hex to mend it, and a burnt-out shell burns no more.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        Entity by = source.getEntity();
        if (isRemoved() || isInvulnerableToBase(source) || by != null && hasPassenger(by)) {
            return false;
        }
        if (source.getDirectEntity() instanceof Player player && by == player && player.isCreative()) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 0.8F, 0.8F);
            discard();
            return true;
        }
        boolean fire = source.is(DamageTypeTags.IS_FIRE);
        if (wrecked() && (fire || town || madeBy != null)) {
            return false;
        }
        if (source.getEntity() != null && !source.is(DamageTypeTags.IS_EXPLOSION) && !fire) {
            damage *= STEEL;
        }
        float health = entityData.get(HEALTH) - damage;
        entityData.set(HEALTH, health);
        hurtAt = level.getGameTime();
        if (!fire) {
            entityData.set(SHAKE, 10);
            entityData.set(SHAKE_SIDE, -entityData.get(SHAKE_SIDE));
            dent(level, source, damage);
        }
        if (!wrecked() && health <= 0.0F) {
            wreck(level);
        } else if (wrecked() && health <= SCRAP_AT) {
            scrap(level);
        }
        return true;
    }

    /** A clang, louder the harder the knock, and sparks off its body where it took it. */
    private void dent(ServerLevel level, DamageSource source, float damage) {
        Vec3 from = source.getSourcePosition();
        Vec3 middle = position().add(0.0, shape().height() * 0.5, 0.0);
        Vec3 at = from == null ? middle : middle.add(from.subtract(middle).multiply(1.0, 0.0, 1.0).normalize().scale(shape().halfWidth()));
        float hard = Math.min(1.0F, damage / 8.0F);
        level.playSound(null, at.x, at.y, at.z, hard > 0.4F ? SoundEvents.ZOMBIE_ATTACK_IRON_DOOR : SoundEvents.CHAIN_HIT, SoundSource.NEUTRAL,
                0.5F + 0.5F * hard, 0.9F + random.nextFloat() * 0.3F);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 4 + Math.round(10 * hard), 0.25, 0.25, 0.25, 0.15);
    }

    /** Gone up in flames: whoever is in it is thrown out, and it burns out to a shell. */
    private void wreck(ServerLevel level) {
        entityData.set(WRECKED, true);
        entityData.set(HEALTH, 0.0F);
        wreckedAt = level.getGameTime();
        speed = 0.0F;
        tellRiders("car.scarlet.wrecked");
        ejectPassengers();
        if (!isInWater()) {
            setRemainingFireTicks(BURNS_FOR);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.8, getZ(), 2, 0.6, 0.2, 0.6, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0, getZ(), 30, 0.9, 0.4, 0.9, 0.04);
        level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.8, getZ(), 24, 0.8, 0.3, 0.8, 0.05);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 0.9F, 1.3F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 1.0F, 0.7F);
    }

    /** A wreck of your own beaten apart: what is left of it is scrap. */
    private void scrap(ServerLevel level) {
        spawnAtLocation(level, new ItemStack(Items.IRON_NUGGET, 5 + random.nextInt(6)));
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_DESTROY, SoundSource.NEUTRAL, 0.8F, 0.8F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.6, getZ(), 16, 0.8, 0.3, 0.8, 0.02);
        discard();
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
        if (fallDistance <= FALL_DENTS || !(level() instanceof ServerLevel level)) {
            return false;
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.NEUTRAL, 1.0F, 0.6F);
        if (fallDistance > FALL_HURTS_RIDERS) {
            for (Entity rider : getPassengers()) {
                rider.hurtServer(level, source, (float) (fallDistance - FALL_HURTS_RIDERS));
            }
        }
        hurtServer(level, source, (float) (fallDistance - FALL_DENTS) * 2.5F);
        return true;
    }

    /** Its fire is its own flames all along it, not the sheet of flame drawn over a burning creature's box. */
    @Override
    public boolean displayFireAnimation() {
        return false;
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
        entityData.set(HEALTH, input.getFloatOr("health", MAX_HEALTH));
        entityData.set(WRECKED, input.getBooleanOr("wrecked", false));
        entityData.set(FLOODED_AT, input.getBooleanOr("flooded", false) ? (int) level().getGameTime() - FLOATS_FOR : -1);
        town = input.getBooleanOr("town", false);
        driven = input.getBooleanOr("driven", false);
        madeBy = input.read("made_by", UUIDUtil.CODEC).orElse(null);
        home = input.read("home", Vec3.CODEC).orElse(null);
        homeYaw = input.getFloatOr("home_yaw", 0.0F);
        // a wreck loaded back in is long burned out; one of the Hex's is mended soon after
        wreckedAt = wrecked() ? level().getGameTime() - MENDS_WRECK_AFTER : Long.MIN_VALUE / 2;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("paint", entityData.get(PAINT));
        output.putInt("era", entityData.get(ERA));
        output.putFloat("health", entityData.get(HEALTH));
        output.putBoolean("wrecked", wrecked());
        output.putBoolean("flooded", flooded());
        output.putBoolean("town", town);
        output.putBoolean("driven", driven);
        output.storeNullable("made_by", UUIDUtil.CODEC, madeBy);
        output.storeNullable("home", Vec3.CODEC, home);
        output.putFloat("home_yaw", homeYaw);
    }
}
