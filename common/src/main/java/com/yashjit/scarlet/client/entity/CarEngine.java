package com.yashjit.scarlet.client.entity;

import com.yashjit.scarlet.entity.ParkedCar;
import com.yashjit.scarlet.network.CarDrivePayload;
import com.yashjit.scarlet.network.CarHornPayload;
import com.yashjit.scarlet.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * A car as whoever sits in it hears it: the rumble of its engine, low while it idles and rising as it picks up speed,
 * and the road under its tires, louder the faster it goes. Its driver sounds its horn with the jump key.
 */
public final class CarEngine {

    private static @Nullable Loop engine;
    private static @Nullable Loop road;
    private static boolean jumpWasDown;

    private CarEngine() {
    }

    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        ParkedCar car = player != null && player.getVehicle() instanceof ParkedCar riding ? riding : null;
        if (car != null && (engine == null || engine.isStopped())) {
            engine = new Loop(car, player, SoundEvents.MINECART_INSIDE, false);
            minecraft.getSoundManager().play(engine);
        }
        if (car != null && (road == null || road.isStopped())) {
            road = new Loop(car, player, SoundEvents.MINECART_RIDING, true);
            minecraft.getSoundManager().play(road);
        }
        boolean driving = car != null && car.getControllingPassenger() == player;
        if (driving) {
            // this game moves the car; the server only sees where it gets to
            Services.NETWORK.sendToServer(new CarDrivePayload(car.speed(), car.takeCrash()));
        }
        boolean jump = driving && minecraft.gui.screen() == null && minecraft.options.keyJump.isDown();
        if (jump && !jumpWasDown) {
            Services.NETWORK.sendToServer(new CarHornPayload());
        }
        jumpWasDown = jump;
    }

    private static final class Loop extends AbstractTickableSoundInstance {

        private final ParkedCar car;
        private final LocalPlayer rider;
        private final boolean tires;

        Loop(ParkedCar car, LocalPlayer rider, SoundEvent sound, boolean tires) {
            super(sound, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.car = car;
            this.rider = rider;
            this.tires = tires;
            this.looping = true;
            this.delay = 0;
            this.volume = tires ? 0.0F : 0.05F;
            this.pitch = 0.5F;
            this.x = car.getX();
            this.y = car.getY();
            this.z = car.getZ();
        }

        @Override
        public void tick() {
            if (car.isRemoved() || rider.getVehicle() != car) {
                stop();
                return;
            }
            x = car.getX();
            y = car.getY();
            z = car.getZ();
            float pace = Math.min(1.0F, Math.abs(car.speed()) / 0.62F);
            if (tires) {
                volume = car.isInWater() ? 0.0F : 0.32F * pace * pace;
                pitch = 0.7F + 0.45F * pace;
            } else if (!car.runs()) {
                // flooded or wrecked: the engine is dead
                volume = 0.0F;
            } else {
                // a battered engine runs rough
                float rough = (1.0F - car.condition()) * 0.12F * Mth.sin(car.tickCount * 1.3F);
                volume = 0.08F + 0.34F * pace;
                pitch = 0.45F + 0.55F * pace + rough;
            }
        }
    }
}
