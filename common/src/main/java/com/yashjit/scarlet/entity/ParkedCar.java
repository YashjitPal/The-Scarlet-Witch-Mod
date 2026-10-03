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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A car parked in a driveway: a different car every era, in that era's colors, the way the car outside Wanda's house
 * changes with every episode. Outside a Hex it is today's car. Solid like a boat, and still.
 *
 * <p>The town parks one in front of every garage it builds; that car is the Hex's, and goes as its wall passes it when
 * the Hex falls or is drawn in. One put down from its item stays, and gives its item back when broken.
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

    /** Whether the town parked it, so it goes with the town. */
    private boolean town;

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

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ERA, Era.PRESENT.ordinal());
        builder.define(PAINT, 0);
    }

    @Override
    public void tick() {
        super.tick();
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
        if (!(source.getEntity() instanceof Player player) || isRemoved()) {
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
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("paint", entityData.get(PAINT));
        output.putInt("era", entityData.get(ERA));
        output.putBoolean("town", town);
    }
}
