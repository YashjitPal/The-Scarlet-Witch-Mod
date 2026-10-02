package com.yashjit.scarlet.neoforge.client;

import com.yashjit.scarlet.client.platform.ClientPlatform;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.PlayerModelType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class NeoForgeClientPlatform implements ClientPlatform {

    private record Definition(ModelLayerLocation location, Supplier<LayerDefinition> supplier) {
    }

    private record Receiver<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, Consumer<T> handler) {
        void register(RegisterClientPayloadHandlersEvent event) {
            event.register(type, (payload, context) -> handler.accept(payload));
        }
    }

    private record Renderer<E extends Entity>(Supplier<EntityType<E>> type, EntityRendererProvider<E> provider) {
        void register(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(type.get(), provider);
        }
    }

    private record Hud(Identifier id, HudLayer layer) {
    }

    private static final List<Definition> DEFINITIONS = new ArrayList<>();
    private static final List<AvatarLayerFactory> LAYERS = new ArrayList<>();
    private static final List<WorldGeometryListener> WORLD = new ArrayList<>();
    private static final List<Receiver<?>> RECEIVERS = new ArrayList<>();
    private static final List<Renderer<?>> RENDERERS = new ArrayList<>();
    private static final List<Hud> HUDS = new ArrayList<>();
    private static final List<StatusBarLift> LIFTS = new ArrayList<>();

    @Override
    public void registerLayerDefinition(ModelLayerLocation location, Supplier<LayerDefinition> definition) {
        DEFINITIONS.add(new Definition(location, definition));
    }

    @Override
    public void registerAvatarLayer(AvatarLayerFactory factory) {
        LAYERS.add(factory);
    }

    @Override
    public void onSubmitWorldGeometry(WorldGeometryListener listener) {
        WORLD.add(listener);
    }

    @Override
    public <T extends CustomPacketPayload> void registerClientbound(CustomPacketPayload.Type<T> type, Consumer<T> handler) {
        RECEIVERS.add(new Receiver<>(type, handler));
    }

    @Override
    public <E extends Entity> void registerEntityRenderer(Supplier<EntityType<E>> type, EntityRendererProvider<E> provider) {
        RENDERERS.add(new Renderer<>(type, provider));
    }

    @Override
    public void registerHud(Identifier id, HudLayer layer) {
        HUDS.add(new Hud(id, layer));
    }

    @Override
    public void liftStatusBars(StatusBarLift lift) {
        LIFTS.add(lift);
    }

    public static void attach(IEventBus modBus) {
        modBus.addListener(NeoForgeClientPlatform::registerLayerDefinitions);
        modBus.addListener(NeoForgeClientPlatform::addLayers);
        modBus.addListener(NeoForgeClientPlatform::registerRenderers);
        modBus.addListener(NeoForgeClientPlatform::registerReceivers);
        modBus.addListener(NeoForgeClientPlatform::registerHuds);
        NeoForge.EVENT_BUS.addListener(NeoForgeClientPlatform::submitWorld);
    }

    private static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (Definition definition : DEFINITIONS) {
            event.registerLayerDefinition(definition.location(), definition.supplier());
        }
    }

    private static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerModelType type : event.getSkins()) {
            AvatarRenderer<AbstractClientPlayer> player = event.getPlayerRenderer(type);
            AvatarRenderer<ClientMannequin> mannequin = event.getMannequinRenderer(type);
            for (AvatarLayerFactory factory : LAYERS) {
                if (player != null) {
                    player.addLayer(factory.create(player, event.getEntityModels()));
                }
                if (mannequin != null) {
                    mannequin.addLayer(factory.create(mannequin, event.getEntityModels()));
                }
            }
        }
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        RENDERERS.forEach(renderer -> renderer.register(event));
    }

    private static void registerReceivers(RegisterClientPayloadHandlersEvent event) {
        RECEIVERS.forEach(receiver -> receiver.register(event));
    }

    private static void registerHuds(RegisterGuiLayersEvent event) {
        for (Hud hud : HUDS) {
            event.registerAbove(VanillaGuiLayers.CROSSHAIR, hud.id(), hud.layer()::render);
        }
        for (StatusBarLift lift : LIFTS) {
            lift(event, lift, StatusBar.VITALS, VanillaGuiLayers.PLAYER_HEALTH, VanillaGuiLayers.ARMOR_LEVEL, VanillaGuiLayers.FOOD_LEVEL,
                    VanillaGuiLayers.VEHICLE_HEALTH, VanillaGuiLayers.AIR_LEVEL);
            lift(event, lift, StatusBar.LEVEL, VanillaGuiLayers.EXPERIENCE_LEVEL);
            lift(event, lift, StatusBar.MESSAGES, VanillaGuiLayers.SELECTED_ITEM_NAME, VanillaGuiLayers.OVERLAY_MESSAGE);
        }
    }

    private static void lift(RegisterGuiLayersEvent event, StatusBarLift lift, StatusBar bar, Identifier... layers) {
        for (Identifier id : layers) {
            event.wrapLayer(id, layer -> (graphics, deltaTracker) ->
                    ClientPlatform.lifted(graphics, lift.pixels(bar), () -> layer.render(graphics, deltaTracker)));
        }
    }

    private static void submitWorld(SubmitCustomGeometryEvent event) {
        for (WorldGeometryListener listener : WORLD) {
            listener.submit(event.getSubmitNodeCollector(), event.getPoseStack());
        }
    }
}
