package com.yashjit.scarlet.network;

import com.yashjit.scarlet.magic.Magic;
import com.yashjit.scarlet.player.SuitUp;

public final class ScarletNetwork {

    /**
     * Bump whenever a payload's format changes, so mismatched clients and servers refuse to connect.
     */
    public static final String VERSION = "6";

    private ScarletNetwork() {
    }

    public static void registerPayloads(PayloadRegistry registry) {
        registry.serverbound(ToggleSuitPayload.TYPE, ToggleSuitPayload.STREAM_CODEC, (payload, player) -> SuitUp.requestToggle(player));
        registry.serverbound(CastPayload.TYPE, CastPayload.STREAM_CODEC, (payload, player) -> Magic.handleCast(player, payload.start(), payload.spell()));
        registry.serverbound(SelectSpellPayload.TYPE, SelectSpellPayload.STREAM_CODEC, (payload, player) -> Magic.select(player, payload.spell()));
        registry.clientbound(ChaosImpactPayload.TYPE, ChaosImpactPayload.STREAM_CODEC);
        registry.clientbound(MagicEventPayload.TYPE, MagicEventPayload.STREAM_CODEC);
        registry.clientbound(HexSyncPayload.TYPE, HexSyncPayload.STREAM_CODEC);
        registry.clientbound(TownBuildPayload.TYPE, TownBuildPayload.STREAM_CODEC);
        registry.clientbound(ResidentsPayload.TYPE, ResidentsPayload.STREAM_CODEC);
    }
}
