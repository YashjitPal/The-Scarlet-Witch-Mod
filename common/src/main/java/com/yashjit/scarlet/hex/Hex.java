package com.yashjit.scarlet.hex;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yashjit.scarlet.hex.town.HexTown;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One caster's Hex: a hexagon of rewritten reality around the point it was cast from, shaped as
 * {@link HexShape} describes. It stays there whether or not the caster does. Server only; clients see
 * {@link HexSnapshot}s.
 */
public final class Hex {

    public static final String DEFAULT_NAME = "Westview";
    /** The longest name a Hex can be given, to fit its title card. */
    public static final int MAX_NAME_LENGTH = 24;

    public static final Codec<Hex> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("caster").forGetter(hex -> hex.caster),
            Codec.STRING.optionalFieldOf("caster_name", "").forGetter(hex -> hex.casterName),
            Vec3.CODEC.fieldOf("center").forGetter(hex -> hex.center),
            Codec.FLOAT.fieldOf("radius").forGetter(hex -> hex.radius),
            Era.CODEC.optionalFieldOf("era", Era.FIFTIES).forGetter(hex -> hex.era),
            Codec.STRING.optionalFieldOf("name", DEFAULT_NAME).forGetter(hex -> hex.name),
            Phase.CODEC.optionalFieldOf("phase", Phase.STANDING).forGetter(hex -> hex.phase),
            Codec.LONG.optionalFieldOf("phase_since", 0L).forGetter(hex -> hex.phaseSince),
            Codec.FLOAT.optionalFieldOf("phase_radius", 0.0F).forGetter(hex -> hex.phaseRadius),
            Codec.LONG.optionalFieldOf("era_since", 0L).forGetter(hex -> hex.eraSince),
            Codec.INT.optionalFieldOf("episode", 1).forGetter(hex -> hex.episode),
            Codec.INT.optionalFieldOf("season", 1).forGetter(hex -> hex.season),
            Codec.BOOL.optionalFieldOf("episodes", false).forGetter(hex -> hex.episodes),
            Codec.LONG.optionalFieldOf("episode_day", 0L).forGetter(hex -> hex.episodeDay),
            HexTown.CODEC.optionalFieldOf("town").forGetter(hex -> Optional.ofNullable(hex.town)),
            Codec.mapPair(HexSky.CODEC.optionalFieldOf("sky", HexSky.WORLD), HexPaint.CODEC.optionalFieldOf("paint"))
                    .forGetter(hex -> Pair.of(hex.sky, hex.paint.size() > 0 ? Optional.of(hex.paint) : Optional.empty()))
    ).apply(i, (caster, casterName, center, radius, era, name, phase, phaseSince, phaseRadius, eraSince, episode, season, episodes,
                episodeDay, town, look) -> {
        Hex hex = new Hex(caster, casterName, center, radius, era, name, phase, phaseSince, phaseRadius, look.getSecond().orElseGet(HexPaint::new));
        hex.eraSince = eraSince;
        hex.episode = episode;
        hex.season = season;
        hex.episodes = episodes;
        hex.episodeDay = episodeDay;
        hex.town = town.orElse(null);
        hex.sky = look.getFirst();
        return hex;
    }));

    final UUID caster;
    String casterName;
    final Vec3 center;
    float radius;
    Era era;
    String name;
    Phase phase;
    long phaseSince;
    /** The radius when the current phase began, which the collapse shrinks from. */
    float phaseRadius;
    /** When the era last changed, as it spreads out over the Hex from its middle. */
    long eraSince;
    /** The era before, still showing wherever the new one hasn't spread to yet. Not saved: a reload finishes a change. */
    Era previousEra;
    /** When the caster's home stood finished while founding, for them to come down in it; -1 until then. */
    long homeStoodAt = -1;
    /** Which episode is on: the first when cast, then the next with every change of era. */
    int episode = 1;
    /** Which season is on. Running past the present back to the 1950s begins the next. */
    int season = 1;
    /** Whether the era moves on by itself every morning, one episode a day. */
    boolean episodes;
    /** The day the current episode began, in episodes mode. */
    long episodeDay;
    @Nullable HexTown town;
    /**
     * Where its caster took hold of its wall to part it, while it stands parted. Not saved: an opening closes on a
     * reload.
     */
    @Nullable Vec3 tearAt;
    /** How far each edge of the opening stands from where it was taken hold of, in blocks. */
    float opening;
    /** Whether its caster has hold of the opening right now, widening or closing it. */
    boolean parting;
    /** How shaken it was by the last blow to its caster, at {@link #stressAt}. */
    float stress;
    long stressAt;
    /** The time of day and weather its caster has set inside it. */
    HexSky sky = HexSky.WORLD;
    /** The blocks its caster has restyled. */
    final HexPaint paint;
    /** The last ten seconds inside it, kept while it stands for its caster to rewind. Not saved. */
    @Nullable HexTape tape;
    /** Whether its caster is winding it back right now, and since when. */
    boolean rewinding;
    long rewindSince;
    /** When it can be wound back again, resting after the last time. */
    long rewindReadyAt;

    Hex(UUID caster, String casterName, Vec3 center, float radius, Era era, String name, Phase phase, long phaseSince, float phaseRadius) {
        this(caster, casterName, center, radius, era, name, phase, phaseSince, phaseRadius, new HexPaint());
    }

    private Hex(UUID caster, String casterName, Vec3 center, float radius, Era era, String name, Phase phase, long phaseSince, float phaseRadius,
                HexPaint paint) {
        this.paint = paint;
        this.caster = caster;
        this.casterName = casterName;
        this.center = center;
        this.radius = radius;
        this.era = era;
        this.previousEra = era;
        this.name = name;
        this.phase = phase;
        this.phaseSince = phaseSince;
        this.phaseRadius = phaseRadius;
    }

    public UUID caster() {
        return caster;
    }

    public Vec3 center() {
        return center;
    }

    public float radius() {
        return radius;
    }

    public Era era() {
        return era;
    }

    public String name() {
        return name;
    }

    public Phase phase() {
        return phase;
    }

    public int episode() {
        return episode;
    }

    public int season() {
        return season;
    }

    public boolean episodes() {
        return episodes;
    }

    public HexSky sky() {
        return sky;
    }

    public @Nullable HexTown town() {
        return town;
    }

    public boolean contains(Vec3 point) {
        return HexShape.contains(center, radius, point);
    }

    void enter(Phase next, long now) {
        phase = next;
        phaseSince = now;
        phaseRadius = radius;
    }

    HexSnapshot snapshot() {
        return new HexSnapshot(caster, casterName, center, radius, era.ordinal(), name, phase.ordinal(), phaseSince, phaseRadius, eraSince,
                previousEra.ordinal(), episode, season, tearAt, opening, parting, stress, stressAt, sky, episodes, rewinding, rewindSince);
    }

    public float unrest(double now) {
        return HexUnrest.unrest(tearAt == null ? -1.0F : opening, HexUnrest.stress(stress, stressAt, now));
    }

    public enum Phase implements StringRepresentable {
        /** The caster's home building itself, before the Hex bursts out of it. */
        FOUNDING("founding"),
        /** Rushing out from where it was cast. */
        SPREADING("spreading"),
        STANDING("standing"),
        /** The caster has lost the crown: it flickers, and falls unless the crown comes back. */
        WARNING("warning"),
        /** The wall rushing inward, changing everything back as it passes. */
        COLLAPSING("collapsing");

        public static final Codec<Phase> CODEC = StringRepresentable.fromEnum(Phase::values);
        private static final Phase[] VALUES = values();

        private final String id;

        Phase(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }

        public static Phase byIndex(int index) {
            return VALUES[Math.clamp(index, 0, VALUES.length - 1)];
        }
    }
}
