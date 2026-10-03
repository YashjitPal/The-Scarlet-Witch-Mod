package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.hex.town.TownPlan;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A caster raising their home again where they chose inside their Hex.
 *
 * @param x     the middle of its lot
 * @param z     the middle of its lot
 * @param front which way its front faces, as a 2D data value
 */
public record HomePayload(int x, int z, int front) implements CustomPacketPayload {

    public static final Type<HomePayload> TYPE = new Type<>(Scarlet.id("home"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HomePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HomePayload::x,
            ByteBufCodecs.VAR_INT, HomePayload::z,
            ByteBufCodecs.VAR_INT, HomePayload::front,
            HomePayload::new);

    public static HomePayload of(TownPlan.HomeLot lot) {
        return new HomePayload(lot.x(), lot.z(), lot.front().get2DDataValue());
    }

    public TownPlan.HomeLot lot() {
        return new TownPlan.HomeLot(x, z, Direction.from2DDataValue(front & 3));
    }

    @Override
    public Type<HomePayload> type() {
        return TYPE;
    }
}
