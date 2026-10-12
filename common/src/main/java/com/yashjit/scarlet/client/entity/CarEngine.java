package com.yashjit.scarlet.client.entity;

import com.yashjit.scarlet.entity.ParkedCar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

/**
 * The rumble of a car's engine, heard by whoever sits in it: low while it idles, rising as it picks up speed.
 */
public final class CarEngine {

    private static @Nullable Rumble rumble;

    private CarEngine() {
    }

    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player != null && player.getVehicle() instanceof ParkedCar car && (rumble == null || rumble.isStopped())) {
            rumble = new Rumble(car, player);
            minecraft.getSoundManager().play(rumble);
        }
    }

    private static final class Rumble extends AbstractTickableSoundInstance {

        private final ParkedCar car;
        private final LocalPlayer rider;

        Rumble(ParkedCar car, LocalPlayer rider) {
            super(SoundEvents.MINECART_INSIDE, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.car = car;
            this.rider = rider;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.05F;
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
            float pace = Math.min(1.0F, Math.abs(car.speed()) / 0.55F);
            volume = 0.06F + 0.4F * pace;
            pitch = 0.5F + 0.45F * pace;
        }
    }
}
