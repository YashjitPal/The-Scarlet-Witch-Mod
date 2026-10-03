package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Something came into a Hex and was rewritten to fit, at {@code at}, while moving at {@code motion}.
 *
 * @param kind    one of the constants below
 * @param variant for flowers and bubbles their color, for fireworks the shape of the burst
 * @param era     the era of the Hex it came into, as an ordinal
 */
public record RewritePayload(Vec3 at, Vec3 motion, int kind, int variant, int era) implements CustomPacketPayload {

    /** An arrow, become a flower. */
    public static final int FLOWER = 0;
    /** A fireball or a skull, become a firework. */
    public static final int FIREWORK = 1;
    /** A thrown potion or spit, become soap bubbles. */
    public static final int BUBBLES = 2;
    /** Lit TNT, become a cake. */
    public static final int CAKE = 3;

    public static final Type<RewritePayload> TYPE = new Type<>(Scarlet.id("rewrite"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RewritePayload> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, RewritePayload::at,
            Vec3.STREAM_CODEC, RewritePayload::motion,
            ByteBufCodecs.VAR_INT, RewritePayload::kind,
            ByteBufCodecs.INT, RewritePayload::variant,
            ByteBufCodecs.VAR_INT, RewritePayload::era,
            RewritePayload::new);

    @Override
    public Type<RewritePayload> type() {
        return TYPE;
    }
}
