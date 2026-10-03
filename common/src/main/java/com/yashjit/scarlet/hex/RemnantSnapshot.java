package com.yashjit.scarlet.hex;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * A caster's home left standing after their Hex fell in around it, as clients see it. It holds on for a while, glitching
 * through the eras ever more wildly, then goes a part at a time, the way it went up but backward.
 *
 * @param id           which of the homes left behind it is, to match the blocks clients are sent of it
 * @param center       the middle of the Hex it was left by
 * @param min          the lowest corner of the box around it
 * @param max          the highest
 * @param start        game time the wall reached it
 * @param ground       the level it was built on
 * @param unbuildTicks ticks it takes to go once it starts to
 */
public record RemnantSnapshot(int id, UUID caster, Vec3 center, BlockPos min, BlockPos max, long start, int ground, int unbuildTicks) {

    /** Ticks it holds on, glitching, before it starts to go. */
    public static final int GLITCH_TICKS = 200;

    public static final StreamCodec<ByteBuf, RemnantSnapshot> STREAM_CODEC = StreamCodec.of(
            (buf, remnant) -> {
                ByteBufCodecs.VAR_INT.encode(buf, remnant.id);
                UUIDUtil.STREAM_CODEC.encode(buf, remnant.caster);
                Vec3.STREAM_CODEC.encode(buf, remnant.center);
                BlockPos.STREAM_CODEC.encode(buf, remnant.min);
                BlockPos.STREAM_CODEC.encode(buf, remnant.max);
                ByteBufCodecs.VAR_LONG.encode(buf, remnant.start);
                ByteBufCodecs.VAR_INT.encode(buf, remnant.ground);
                ByteBufCodecs.VAR_INT.encode(buf, remnant.unbuildTicks);
            },
            buf -> new RemnantSnapshot(ByteBufCodecs.VAR_INT.decode(buf), UUIDUtil.STREAM_CODEC.decode(buf), Vec3.STREAM_CODEC.decode(buf),
                    BlockPos.STREAM_CODEC.decode(buf), BlockPos.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));

    /**
     * How wildly it glitches at a moment, 0 to 1: building up while it holds on, at its wildest as it starts to go, and
     * easing off as less and less of it is left.
     */
    public float glitch(double now) {
        double since = now - start;
        if (since < 0.0) {
            return 0.0F;
        }
        if (since < GLITCH_TICKS) {
            float k = (float) (since / GLITCH_TICKS);
            return 0.3F + 0.7F * k * k * (3.0F - 2.0F * k);
        }
        float k = (float) Math.min(1.0, (since - GLITCH_TICKS) / Math.max(1, unbuildTicks));
        return 1.0F - 0.85F * k;
    }

    /**
     * Whether it has started to go.
     */
    public boolean going(double now) {
        return now - start >= GLITCH_TICKS;
    }

    /**
     * Whether a point lies in the box around it.
     */
    public boolean contains(Vec3 point, double margin) {
        return point.x >= min.getX() - margin && point.x <= max.getX() + 1 + margin && point.y >= min.getY() - margin
                && point.y <= max.getY() + 1 + margin && point.z >= min.getZ() - margin && point.z <= max.getZ() + 1 + margin;
    }

    /**
     * The middle of the box around it.
     */
    public Vec3 middle() {
        return new Vec3((min.getX() + max.getX() + 1) / 2.0, (min.getY() + max.getY() + 1) / 2.0, (min.getZ() + max.getZ() + 1) / 2.0);
    }
}
