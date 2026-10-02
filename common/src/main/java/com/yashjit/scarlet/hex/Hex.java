package com.yashjit.scarlet.hex;

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
            HexTown.CODEC.optionalFieldOf("town").forGetter(hex -> Optional.ofNullable(hex.town))
    ).apply(i, (caster, casterName, center, radius, era, name, phase, phaseSince, phaseRadius, eraSince, town) -> {
        Hex hex = new Hex(caster, casterName, center, radius, era, name, phase, phaseSince, phaseRadius);
        hex.eraSince = eraSince;
        hex.town = town.orElse(null);
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
    /** When the era last changed, for the channel-change flicker. */
    long eraSince;
    @Nullable HexTown town;

    Hex(UUID caster, String casterName, Vec3 center, float radius, Era era, String name, Phase phase, long phaseSince, float phaseRadius) {
        this.caster = caster;
        this.casterName = casterName;
        this.center = center;
        this.radius = radius;
        this.era = era;
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
        return new HexSnapshot(caster, casterName, center, radius, era.ordinal(), name, phase.ordinal(), phaseSince, phaseRadius, eraSince);
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
