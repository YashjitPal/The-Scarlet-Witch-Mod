package com.yashjit.scarlet.network;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.hex.HexSnapshot;
import com.yashjit.scarlet.hex.RemnantSnapshot;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Every Hex in the receiver's dimension, and the homes fallen ones have left standing for now.
 *
 * @param ownsHex whether the receiver has a Hex standing anywhere, which holds back part of their energy
 */
public record HexSyncPayload(boolean ownsHex, List<HexSnapshot> hexes, List<RemnantSnapshot> remnants) implements CustomPacketPayload {

    public static final Type<HexSyncPayload> TYPE = new Type<>(Scarlet.id("hex_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HexSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, HexSyncPayload::ownsHex,
            HexSnapshot.STREAM_CODEC.apply(ByteBufCodecs.list()), HexSyncPayload::hexes,
            RemnantSnapshot.STREAM_CODEC.apply(ByteBufCodecs.list()), HexSyncPayload::remnants,
            HexSyncPayload::new);

    @Override
    public Type<HexSyncPayload> type() {
        return TYPE;
    }
}
