package com.yashjit.scarlet.client;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.client.anim.CastGestures;
import com.yashjit.scarlet.client.anim.PoseBlends;
import com.yashjit.scarlet.client.config.SettingsScreen;
import com.yashjit.scarlet.client.costume.CostumeLayer;
import com.yashjit.scarlet.client.costume.CostumeModels;
import com.yashjit.scarlet.client.darkhold.CorruptionClient;
import com.yashjit.scarlet.client.darkhold.DreamwalkClient;
import com.yashjit.scarlet.client.dev.Showcase;
import com.yashjit.scarlet.client.entity.ChaosBoltRenderer;
import com.yashjit.scarlet.client.entity.ClientDreamBody;
import com.yashjit.scarlet.client.fx.BoltFx;
import com.yashjit.scarlet.client.fx.CastFx;
import com.yashjit.scarlet.client.fx.DreamFx;
import com.yashjit.scarlet.client.fx.LevitationFx;
import com.yashjit.scarlet.client.fx.MindControlFx;
import com.yashjit.scarlet.client.fx.MistFx;
import com.yashjit.scarlet.client.fx.PaintFx;
import com.yashjit.scarlet.client.fx.RewriteFx;
import com.yashjit.scarlet.client.fx.RuneFx;
import com.yashjit.scarlet.client.fx.ScarletFx;
import com.yashjit.scarlet.client.fx.ShieldFx;
import com.yashjit.scarlet.client.fx.ShockwaveFx;
import com.yashjit.scarlet.client.fx.TearFx;
import com.yashjit.scarlet.client.fx.TelekinesisFx;
import com.yashjit.scarlet.client.fx.SlipFx;
import com.yashjit.scarlet.client.fx.TownFx;
import com.yashjit.scarlet.client.fx.TransformationFx;
import com.yashjit.scarlet.client.hex.Ejections;
import com.yashjit.scarlet.client.hex.Founding;
import com.yashjit.scarlet.client.hex.HexClient;
import com.yashjit.scarlet.client.hex.HexScreen;
import com.yashjit.scarlet.client.hex.HexSkyClient;
import com.yashjit.scarlet.client.hex.HomePlacement;
import com.yashjit.scarlet.client.hex.ShowrunnerScreen;
import com.yashjit.scarlet.client.hex.LaughTrack;
import com.yashjit.scarlet.client.hex.OutfitLayer;
import com.yashjit.scarlet.client.hex.Outfits;
import com.yashjit.scarlet.client.hex.ResidentsClient;
import com.yashjit.scarlet.client.hex.RewindClient;
import com.yashjit.scarlet.client.hex.TitleCard;
import com.yashjit.scarlet.client.hex.Remnants;
import com.yashjit.scarlet.client.hex.TownSlips;
import com.yashjit.scarlet.client.magic.CastInput;
import com.yashjit.scarlet.client.magic.Hands;
import com.yashjit.scarlet.client.magic.MagicHud;
import com.yashjit.scarlet.client.magic.MagicLayer;
import com.yashjit.scarlet.client.magic.MindControlClient;
import com.yashjit.scarlet.client.magic.SpellWheel;
import com.yashjit.scarlet.client.magic.TelekinesisClient;
import com.yashjit.scarlet.client.platform.ClientPlatform;
import com.yashjit.scarlet.client.platform.ClientServices;
import com.yashjit.scarlet.client.render.ScarletRenderTypes;
import com.yashjit.scarlet.config.ScarletClientConfig;
import com.yashjit.scarlet.darkhold.DarkholdItem;
import com.yashjit.scarlet.network.ChaosImpactPayload;
import com.yashjit.scarlet.network.DreamOptionsPayload;
import com.yashjit.scarlet.network.DreamStatePayload;
import com.yashjit.scarlet.network.EjectPayload;
import com.yashjit.scarlet.network.GesturePayload;
import com.yashjit.scarlet.network.HexRipplePayload;
import com.yashjit.scarlet.network.HexSyncPayload;
import com.yashjit.scarlet.network.HoldPayload;
import com.yashjit.scarlet.network.MagicEventPayload;
import com.yashjit.scarlet.network.PaintPayload;
import com.yashjit.scarlet.network.PossessPayload;
import com.yashjit.scarlet.network.PuppetPayload;
import com.yashjit.scarlet.network.ResidentsPayload;
import com.yashjit.scarlet.network.RewritePayload;
import com.yashjit.scarlet.network.RunePayload;
import com.yashjit.scarlet.network.ToggleSuitPayload;
import com.yashjit.scarlet.network.TownBuildPayload;
import com.yashjit.scarlet.network.TownCutPayload;
import com.yashjit.scarlet.network.RemnantBlocksPayload;
import com.yashjit.scarlet.network.TownFormPayload;
import com.yashjit.scarlet.network.TownUnformPayload;
import com.yashjit.scarlet.network.TownGlitchPayload;
import com.yashjit.scarlet.platform.Services;
import com.yashjit.scarlet.registry.ScarletEntities;
import com.yashjit.scarlet.client.hex.RadioMusic;
import com.yashjit.scarlet.client.hex.EraAudio;
import com.yashjit.scarlet.client.entity.ParkedCarRenderer;
import com.yashjit.scarlet.decor.RadioSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jspecify.annotations.Nullable;

/**
 * Client-side entry point. Only reachable from each loader's client initializer.
 */
public final class ScarletClient {

    private static @Nullable ClientLevel warmedFor;

    private ScarletClient() {
    }

    public static void init() {
        ScarletClientConfig.load();
        ClientPlatform platform = ClientServices.PLATFORM;
        CostumeModels.register(platform);
        platform.registerAvatarLayer(OutfitLayer::new);
        platform.registerAvatarLayer(CostumeLayer::new);
        platform.registerAvatarLayer(MagicLayer::new);
        platform.registerEntityRenderer(ScarletEntities.CHAOS_BOLT, ChaosBoltRenderer::new);
        platform.registerEntityRenderer(ScarletEntities.SEAT, NoopRenderer::new);
        platform.registerEntityRenderer(ScarletEntities.PARKED_CAR, ParkedCarRenderer::new);
        platform.registerEntityRenderer(ScarletEntities.DREAM_BODY, ClientDreamBody::renderer);
        ClientDreamBody.registerOverride();
        RadioSounds.listen(RadioMusic::heard);
        DarkholdItem.viewerCorruption = () -> CorruptionClient.corruption(Minecraft.getInstance().player);
        platform.registerClientbound(ChaosImpactPayload.TYPE, BoltFx::impact);
        platform.registerClientbound(MagicEventPayload.TYPE, payload -> {
            ShieldFx.onEvent(payload);
            LevitationFx.onEvent(payload);
            ShockwaveFx.onEvent(payload);
            MistFx.onEvent(payload);
            TelekinesisFx.onEvent(payload);
            RuneFx.onEvent(payload);
            DreamFx.onEvent(payload);
        });
        platform.registerClientbound(DreamOptionsPayload.TYPE, DreamwalkClient::receive);
        platform.registerClientbound(DreamStatePayload.TYPE, DreamwalkClient::receive);
        platform.registerClientbound(RunePayload.TYPE, RuneFx::receive);
        platform.registerClientbound(EjectPayload.TYPE, Ejections::receive);
        platform.registerClientbound(RewritePayload.TYPE, RewriteFx::receive);
        platform.registerClientbound(PaintPayload.TYPE, PaintFx::receive);
        platform.registerClientbound(HoldPayload.TYPE, TelekinesisClient::receive);
        platform.registerClientbound(PossessPayload.TYPE, MindControlClient::receive);
        platform.registerClientbound(PuppetPayload.TYPE, MindControlClient::receive);
        platform.registerClientbound(HexSyncPayload.TYPE, HexClient::receive);
        platform.registerClientbound(HexRipplePayload.TYPE, HexClient::ripple);
        platform.registerClientbound(TownGlitchPayload.TYPE, TownSlips::receive);
        platform.registerClientbound(TownBuildPayload.TYPE, TownFx::onBuild);
        platform.registerClientbound(TownCutPayload.TYPE, TownFx::onCut);
        platform.registerClientbound(TownFormPayload.TYPE, TownFx::onForm);
        platform.registerClientbound(TownUnformPayload.TYPE, TownFx::onUnform);
        platform.registerClientbound(RemnantBlocksPayload.TYPE, Remnants::receive);
        platform.registerClientbound(ResidentsPayload.TYPE, ResidentsClient::receive);
        platform.registerClientbound(GesturePayload.TYPE, ResidentsClient::gesture);
        platform.registerHud(Scarlet.id("corruption"), CorruptionClient::render);
        platform.registerHud(Scarlet.id("mind_control"), MindControlFx::render);
        platform.registerHud(Scarlet.id("dreamwalk"), DreamFx::render);
        platform.registerHud(Scarlet.id("magic"), MagicHud::render);
        platform.registerHud(Scarlet.id("title_card"), TitleCard::render);
        platform.registerHud(Scarlet.id("rewind"), RewindClient::render);
        platform.registerHud(Scarlet.id("mist"), MistFx::render);
        platform.registerHud(Scarlet.id("hex_burst"), Founding::render);
        platform.liftStatusBars(MagicHud::lift);
        platform.onSubmitWorldGeometry(ScarletFx::submit);
        platform.onSubmitWorldGeometry(BoltFx::submit);
        platform.onSubmitWorldGeometry(ShieldFx::submit);
        platform.onSubmitWorldGeometry(LevitationFx::submit);
        platform.onSubmitWorldGeometry(ShockwaveFx::submit);
        platform.onSubmitWorldGeometry(MistFx::submit);
        platform.onSubmitWorldGeometry(TelekinesisFx::submit);
        platform.onSubmitWorldGeometry(MindControlFx::submit);
        platform.onSubmitWorldGeometry(DreamFx::submit);
        platform.onSubmitWorldGeometry(RuneFx::submit);
        platform.onSubmitWorldGeometry(Ejections::submit);
        platform.onSubmitWorldGeometry(RewriteFx::submit);
        platform.onSubmitWorldGeometry(PaintFx::submit);
        platform.onSubmitWorldGeometry(TownFx::submit);
        platform.onSubmitWorldGeometry(HomePlacement::submit);
        platform.onSubmitWorldGeometry(Founding::submit);
        platform.onSubmitWorldGeometry(SlipFx::submit);
        platform.onSubmitWorldGeometry(Remnants::submit);
        platform.onSubmitWorldGeometry(ResidentsClient::submit);
        platform.onSubmitWorldGeometry(Outfits::submit);
    }

    public static void onClientTick(Minecraft minecraft) {
        if (minecraft.level != warmedFor) {
            warmedFor = minecraft.level;
            if (minecraft.level != null) {
                ScarletRenderTypes.warmUp();
                HexScreen.warmUp();
            }
        }
        while (ScarletKeyMappings.SUIT_UP.consumeClick()) {
            if (minecraft.player != null) {
                Services.NETWORK.sendToServer(ToggleSuitPayload.INSTANCE);
            }
        }
        while (ScarletKeyMappings.SHOWRUNNER.consumeClick()) {
            ShowrunnerScreen.open(minecraft);
        }
        while (ScarletKeyMappings.SETTINGS.consumeClick()) {
            if (minecraft.gui.screen() == null) {
                SettingsScreen.open(minecraft);
            }
        }
        HexClient.tick(minecraft);
        RewindClient.tick(minecraft);
        HexSkyClient.tick(minecraft);
        HomePlacement.tick(minecraft);
        Founding.tick(minecraft);
        ResidentsClient.tick(minecraft);
        Outfits.tick(minecraft);
        TitleCard.tick(minecraft);
        LaughTrack.tick(minecraft);
        RadioMusic.tick(minecraft);
        EraAudio.tick(minecraft);
        SpellWheel.tick(minecraft);
        CastInput.tick(minecraft);
        ScarletFx.tick(minecraft);
        CorruptionClient.tick(minecraft);
        TransformationFx.tick(minecraft);
        CastFx.tick(minecraft);
        BoltFx.tick(minecraft);
        ShieldFx.tick(minecraft);
        LevitationFx.tick(minecraft);
        ShockwaveFx.tick(minecraft);
        MistFx.tick(minecraft);
        TelekinesisClient.tick(minecraft);
        TelekinesisFx.tick(minecraft);
        DreamwalkClient.tick(minecraft);
        MindControlClient.tick(minecraft);
        DreamFx.tick(minecraft);
        RuneFx.tick(minecraft);
        Ejections.tick(minecraft);
        RewriteFx.tick(minecraft);
        PaintFx.tick(minecraft);
        TownFx.tick(minecraft);
        TearFx.tick(minecraft);
        Remnants.tick(minecraft);
        TownSlips.tick(minecraft);
        SlipFx.tick(minecraft);
        if (minecraft.level != null && minecraft.level.getGameTime() % 100 == 0) {
            CastGestures.prune(minecraft.level);
            PoseBlends.prune(minecraft.level);
            Hands.prune(minecraft.level);
            CastFx.prune(minecraft.level);
            ShieldFx.prune(minecraft.level);
        }
        if (Showcase.enabled()) {
            Showcase.tick(minecraft);
        }
    }
}
