package com.yashjit.scarlet.client;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.client.anim.CastGestures;
import com.yashjit.scarlet.client.anim.PoseBlends;
import com.yashjit.scarlet.client.costume.CostumeLayer;
import com.yashjit.scarlet.client.costume.CostumeModels;
import com.yashjit.scarlet.client.dev.Showcase;
import com.yashjit.scarlet.client.entity.ChaosBoltRenderer;
import com.yashjit.scarlet.client.fx.BoltFx;
import com.yashjit.scarlet.client.fx.CastFx;
import com.yashjit.scarlet.client.fx.LevitationFx;
import com.yashjit.scarlet.client.fx.ScarletFx;
import com.yashjit.scarlet.client.fx.ShieldFx;
import com.yashjit.scarlet.client.fx.TownFx;
import com.yashjit.scarlet.client.fx.TransformationFx;
import com.yashjit.scarlet.client.hex.HexClient;
import com.yashjit.scarlet.client.hex.HexScreen;
import com.yashjit.scarlet.client.hex.LaughTrack;
import com.yashjit.scarlet.client.hex.OutfitLayer;
import com.yashjit.scarlet.client.hex.Outfits;
import com.yashjit.scarlet.client.hex.ResidentsClient;
import com.yashjit.scarlet.client.hex.TitleCard;
import com.yashjit.scarlet.client.magic.CastInput;
import com.yashjit.scarlet.client.magic.Hands;
import com.yashjit.scarlet.client.magic.MagicHud;
import com.yashjit.scarlet.client.magic.MagicLayer;
import com.yashjit.scarlet.client.magic.SpellWheel;
import com.yashjit.scarlet.client.platform.ClientPlatform;
import com.yashjit.scarlet.client.platform.ClientServices;
import com.yashjit.scarlet.client.render.ScarletRenderTypes;
import com.yashjit.scarlet.config.ScarletClientConfig;
import com.yashjit.scarlet.network.ChaosImpactPayload;
import com.yashjit.scarlet.network.HexSyncPayload;
import com.yashjit.scarlet.network.MagicEventPayload;
import com.yashjit.scarlet.network.ResidentsPayload;
import com.yashjit.scarlet.network.ToggleSuitPayload;
import com.yashjit.scarlet.network.TownBuildPayload;
import com.yashjit.scarlet.platform.Services;
import com.yashjit.scarlet.registry.ScarletEntities;
import net.minecraft.client.Minecraft;
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
        platform.registerClientbound(ChaosImpactPayload.TYPE, BoltFx::impact);
        platform.registerClientbound(MagicEventPayload.TYPE, payload -> {
            ShieldFx.onEvent(payload);
            LevitationFx.onEvent(payload);
        });
        platform.registerClientbound(HexSyncPayload.TYPE, HexClient::receive);
        platform.registerClientbound(TownBuildPayload.TYPE, TownFx::onBuild);
        platform.registerClientbound(ResidentsPayload.TYPE, ResidentsClient::receive);
        platform.registerHud(Scarlet.id("magic"), MagicHud::render);
        platform.registerHud(Scarlet.id("title_card"), TitleCard::render);
        platform.liftStatusBars(MagicHud::lift);
        platform.onSubmitWorldGeometry(ScarletFx::submit);
        platform.onSubmitWorldGeometry(BoltFx::submit);
        platform.onSubmitWorldGeometry(ShieldFx::submit);
        platform.onSubmitWorldGeometry(LevitationFx::submit);
        platform.onSubmitWorldGeometry(TownFx::submit);
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
        HexClient.tick(minecraft);
        ResidentsClient.tick(minecraft);
        Outfits.tick(minecraft);
        TitleCard.tick(minecraft);
        LaughTrack.tick(minecraft);
        SpellWheel.tick(minecraft);
        CastInput.tick(minecraft);
        ScarletFx.tick(minecraft);
        TransformationFx.tick(minecraft);
        CastFx.tick(minecraft);
        BoltFx.tick(minecraft);
        ShieldFx.tick(minecraft);
        LevitationFx.tick(minecraft);
        TownFx.tick(minecraft);
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
