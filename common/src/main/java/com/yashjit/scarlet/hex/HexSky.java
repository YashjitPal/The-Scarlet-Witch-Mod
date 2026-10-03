package com.yashjit.scarlet.hex;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * The sky over a Hex, as its caster sets it: the time of day and the weather inside, whatever they are outside. Only
 * the view from inside follows it; the world itself keeps its own day and weather.
 */
public record HexSky(Time time, Weather weather) {

    public static final HexSky WORLD = new HexSky(Time.WORLD, Weather.WORLD);

    public static final Codec<HexSky> CODEC = RecordCodecBuilder.create(i -> i.group(
            Time.CODEC.optionalFieldOf("time", Time.WORLD).forGetter(HexSky::time),
            Weather.CODEC.optionalFieldOf("weather", Weather.WORLD).forGetter(HexSky::weather)
    ).apply(i, HexSky::new));

    public HexSky withTime(Time time) {
        return new HexSky(time, weather);
    }

    public HexSky withWeather(Weather weather) {
        return new HexSky(time, weather);
    }

    public enum Time implements StringRepresentable {
        /** The world's own time of day. */
        WORLD("world", -1),
        DAWN("dawn", 23300),
        NOON("noon", 6000),
        DUSK("dusk", 12400),
        NIGHT("night", 18000);

        public static final Codec<Time> CODEC = StringRepresentable.fromEnum(Time::values);
        private static final Time[] VALUES = values();

        private final String id;
        private final int dayTime;

        Time(String id, int dayTime) {
            this.id = id;
            this.dayTime = dayTime;
        }

        /**
         * Where in the day's 24000 ticks it stands, or -1 for the world's own.
         */
        public int dayTime() {
            return dayTime;
        }

        public Component displayName() {
            return Component.translatable("hex_sky.scarlet." + id);
        }

        @Override
        public String getSerializedName() {
            return id;
        }

        public static Time byIndex(int index) {
            return VALUES[Math.clamp(index, 0, VALUES.length - 1)];
        }
    }

    public enum Weather implements StringRepresentable {
        /** The world's own weather. */
        WORLD("world"),
        CLEAR("clear"),
        RAIN("rain"),
        STORM("storm");

        public static final Codec<Weather> CODEC = StringRepresentable.fromEnum(Weather::values);
        private static final Weather[] VALUES = values();

        private final String id;

        Weather(String id) {
            this.id = id;
        }

        public Component displayName() {
            return Component.translatable("hex_weather.scarlet." + id);
        }

        @Override
        public String getSerializedName() {
            return id;
        }

        public static Weather byIndex(int index) {
            return VALUES[Math.clamp(index, 0, VALUES.length - 1)];
        }
    }
}
