package com.yashjit.scarlet.client.hex;

import com.yashjit.scarlet.hex.HexShape;
import com.yashjit.scarlet.hex.HexSky;
import com.yashjit.scarlet.hex.HexSnapshot;
import com.yashjit.scarlet.hex.Hexes;
import net.minecraft.client.ClientClockManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The sky as seen from inside a Hex whose caster has set it: their time of day and their weather, whatever the world
 * outside is doing. Only the view follows it, and only from inside.
 *
 * <p>When the sky changes, the day runs to its new hour like a time-lapse, the sun racing across and the shadows
 * swinging with it, and rain or a storm rolls in or clears over a few seconds. Step out, and the world's own sky runs
 * back the same way.
 */
public final class HexSkyClient {

    private static final int DAY = 24000;
    /** How many ticks of the day run by each tick while the sky races to a new hour. */
    private static final double TIME_LAPSE = 240.0;
    /** How far the weather comes in or clears each tick. */
    private static final float WEATHER_STEP = 0.025F;

    /** The client's overworld clock, which the day's sky follows. */
    private static ClientClockManager.@Nullable ClientClockInstance overworld;
    /** The hour of the day being shown, 0 to 24000, or not a number while the world's own is. */
    private static double shownHour = Double.NaN;
    /** The rain and thunder being shown, or below 0 while the world's own are. */
    private static float shownRain = -1.0F;
    private static float shownThunder = -1.0F;
    private static int flashIn;
    private static @Nullable ClientLevel seenLevel;
    /** Set while reading the world's own sky, past what is shown. */
    private static boolean readingReal;

    private HexSkyClient() {
    }

    /**
     * Notes which of the client's clocks is the overworld's, as each is looked up.
     */
    public static void recordClock(boolean isOverworld, ClientClockManager.ClientClockInstance instance) {
        if (isOverworld) {
            overworld = instance;
        }
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level != seenLevel) {
            seenLevel = level;
            overworld = null;
            shownHour = Double.NaN;
            shownRain = -1.0F;
            shownThunder = -1.0F;
        }
        if (level == null || minecraft.isPaused()) {
            return;
        }
        HexSky sky = skyAround(minecraft);
        readingReal = true;
        long real = overworld == null ? 0L : overworld.totalTicks();
        float realRain = level.getRainLevel(1.0F);
        float realThunder = level.getThunderLevel(1.0F);
        readingReal = false;
        int realHour = Math.floorMod(real, DAY);
        int targetHour = sky.time().dayTime() < 0 ? realHour : sky.time().dayTime();
        if (Double.isNaN(shownHour)) {
            if (targetHour != realHour) {
                shownHour = realHour;
            }
        }
        if (!Double.isNaN(shownHour)) {
            double ahead = Math.floorMod((long) Math.round(targetHour - shownHour), DAY);
            // a little behind runs back; anything else runs forward through the day
            if (ahead > DAY - 1000) {
                ahead -= DAY;
            }
            if (Math.abs(ahead) <= TIME_LAPSE) {
                shownHour = targetHour;
                if (sky.time() == HexSky.Time.WORLD) {
                    shownHour = Double.NaN;
                }
            } else {
                shownHour = Math.floorMod((long) Math.round(shownHour + Math.signum(ahead) * TIME_LAPSE), DAY);
            }
        }
        float targetRain = switch (sky.weather()) {
            case WORLD -> realRain;
            case CLEAR -> 0.0F;
            case RAIN, STORM -> 1.0F;
        };
        float targetThunder = switch (sky.weather()) {
            case WORLD -> realThunder;
            case CLEAR, RAIN -> 0.0F;
            case STORM -> 1.0F;
        };
        if (shownRain < 0.0F && (Math.abs(targetRain - realRain) > 0.001F || Math.abs(targetThunder - realThunder) > 0.001F)) {
            shownRain = realRain;
            shownThunder = realThunder;
        }
        if (shownRain >= 0.0F) {
            shownRain = approach(shownRain, targetRain);
            shownThunder = approach(shownThunder, targetThunder);
            if (sky.weather() == HexSky.Weather.WORLD && shownRain == realRain && shownThunder == realThunder) {
                shownRain = -1.0F;
                shownThunder = -1.0F;
            }
        }
        storm(minecraft, level);
    }

    /**
     * The overworld clock's ticks, as the sky should read them: the world's own day count, at the hour being shown.
     */
    public static long clockTicks(ClientClockManager.ClientClockInstance instance, long real) {
        if (readingReal || instance != overworld || Double.isNaN(shownHour)) {
            return real;
        }
        return real - Math.floorMod(real, DAY) + (long) shownHour;
    }

    public static float rainLevel(float real) {
        return readingReal || shownRain < 0.0F ? real : shownRain;
    }

    public static float thunderLevel(float real) {
        return readingReal || shownThunder < 0.0F ? real : Math.min(shownThunder, rainLevel(real));
    }

    /**
     * Lightning far off in a storm the caster has called up: the sky flashing now and then, and thunder rolling in
     * after it.
     */
    private static void storm(Minecraft minecraft, ClientLevel level) {
        if (shownThunder < 0.5F) {
            flashIn = 0;
            return;
        }
        RandomSource random = level.getRandom();
        if (flashIn <= 0) {
            flashIn = 80 + random.nextInt(200);
            return;
        }
        if (--flashIn == 0) {
            level.setSkyFlashTime(2);
            Vec3 camera = minecraft.gameRenderer.mainCamera().position();
            double angle = random.nextDouble() * Math.PI * 2.0;
            level.playLocalSound(camera.x + Math.cos(angle) * 60.0, camera.y + 20.0, camera.z + Math.sin(angle) * 60.0, SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.WEATHER, 3.0F, 0.75F + random.nextFloat() * 0.2F, false);
        }
    }

    private static float approach(float value, float target) {
        if (Math.abs(target - value) <= WEATHER_STEP) {
            return target;
        }
        return value + Math.signum(target - value) * WEATHER_STEP;
    }

    /**
     * The sky set over the Hex the view is inside, if any.
     */
    private static HexSky skyAround(Minecraft minecraft) {
        if (minecraft.level == null) {
            return HexSky.WORLD;
        }
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        double now = minecraft.level.getGameTime();
        for (HexSnapshot hex : Hexes.clientHexes()) {
            if (HexShape.contains(hex.center(), HexClient.drawnRadius(hex, now), camera)) {
                return hex.sky();
            }
        }
        return HexSky.WORLD;
    }
}
