package com.yashjit.scarlet.network;

import com.yashjit.scarlet.hex.Showrunner;
import com.yashjit.scarlet.magic.Magic;
import com.yashjit.scarlet.magic.MindControl;
import com.yashjit.scarlet.magic.Telekinesis;
import com.yashjit.scarlet.player.SuitUp;

public final class ScarletNetwork {

    /**
     * Bump whenever a payload's format changes, so mismatched clients and servers refuse to connect.
     */
    public static final String VERSION = "18";

    private ScarletNetwork() {
    }

    public static void registerPayloads(PayloadRegistry registry) {
        registry.serverbound(ToggleSuitPayload.TYPE, ToggleSuitPayload.STREAM_CODEC, (payload, player) -> SuitUp.requestToggle(player));
        registry.serverbound(CastPayload.TYPE, CastPayload.STREAM_CODEC, (payload, player) -> Magic.handleCast(player, payload.start(), payload.spell()));
        registry.serverbound(SelectSpellPayload.TYPE, SelectSpellPayload.STREAM_CODEC, (payload, player) -> Magic.select(player, payload.spell()));
        registry.serverbound(TelekinesisPayload.TYPE, TelekinesisPayload.STREAM_CODEC, (payload, player) -> Telekinesis.handle(player, payload));
        registry.serverbound(ControlPayload.TYPE, ControlPayload.STREAM_CODEC, (payload, player) -> MindControl.handle(player, payload));
        registry.serverbound(StrugglePayload.TYPE, StrugglePayload.STREAM_CODEC, (payload, player) -> MindControl.struggle(player));
        registry.serverbound(ShowrunnerPayload.TYPE, ShowrunnerPayload.STREAM_CODEC, (payload, player) -> Showrunner.handle(player, payload));
        registry.serverbound(HomePayload.TYPE, HomePayload.STREAM_CODEC, (payload, player) -> Showrunner.raiseHome(player, payload));
        registry.clientbound(PossessPayload.TYPE, PossessPayload.STREAM_CODEC);
        registry.clientbound(PuppetPayload.TYPE, PuppetPayload.STREAM_CODEC);
        registry.clientbound(RunePayload.TYPE, RunePayload.STREAM_CODEC);
        registry.clientbound(EjectPayload.TYPE, EjectPayload.STREAM_CODEC);
        registry.clientbound(RewritePayload.TYPE, RewritePayload.STREAM_CODEC);
        registry.clientbound(PaintPayload.TYPE, PaintPayload.STREAM_CODEC);
        registry.clientbound(ChaosImpactPayload.TYPE, ChaosImpactPayload.STREAM_CODEC);
        registry.clientbound(MagicEventPayload.TYPE, MagicEventPayload.STREAM_CODEC);
        registry.clientbound(HoldPayload.TYPE, HoldPayload.STREAM_CODEC);
        registry.clientbound(HexRipplePayload.TYPE, HexRipplePayload.STREAM_CODEC);
        registry.clientbound(TownGlitchPayload.TYPE, TownGlitchPayload.STREAM_CODEC);
        registry.clientbound(HexSyncPayload.TYPE, HexSyncPayload.STREAM_CODEC);
        registry.clientbound(TownBuildPayload.TYPE, TownBuildPayload.STREAM_CODEC);
        registry.clientbound(TownCutPayload.TYPE, TownCutPayload.STREAM_CODEC);
        registry.clientbound(TownFormPayload.TYPE, TownFormPayload.STREAM_CODEC);
        registry.clientbound(TownUnformPayload.TYPE, TownUnformPayload.STREAM_CODEC);
        registry.clientbound(RemnantBlocksPayload.TYPE, RemnantBlocksPayload.STREAM_CODEC);
        registry.clientbound(ResidentsPayload.TYPE, ResidentsPayload.STREAM_CODEC);
    }
}
