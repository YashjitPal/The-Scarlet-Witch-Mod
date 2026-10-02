package com.yashjit.scarlet.fabric.client;

import com.yashjit.scarlet.client.platform.ClientPlatform;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

public final class FabricClientPlatform implements ClientPlatform {

    @Override
    public void registerLayerDefinition(ModelLayerLocation location, Supplier<LayerDefinition> definition) {
        ModelLayerRegistry.registerModelLayer(location, definition::get);
    }

    @Override
    public void registerAvatarLayer(AvatarLayerFactory factory) {
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) {
                helper.register(factory.create(avatar, context.getModelSet()));
            }
        });
    }

    @Override
    public void onSubmitWorldGeometry(WorldGeometryListener listener) {
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> listener.submit(context.submitNodeCollector(), context.poseStack()));
    }

    @Override
    public <T extends CustomPacketPayload> void registerClientbound(CustomPacketPayload.Type<T> type, Consumer<T> handler) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.accept(payload));
    }

    @Override
    public <E extends Entity> void registerEntityRenderer(Supplier<EntityType<E>> type, EntityRendererProvider<E> provider) {
        EntityRendererRegistry.register(type.get(), provider);
    }

    @Override
    public void registerHud(Identifier id, HudLayer layer) {
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, id, layer::render);
    }

    @Override
    public void liftStatusBars(StatusBarLift lift) {
        lift(lift, StatusBar.VITALS, VanillaHudElements.HEALTH_BAR, VanillaHudElements.ARMOR_BAR, VanillaHudElements.FOOD_BAR,
                VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.AIR_BAR);
        lift(lift, StatusBar.LEVEL, VanillaHudElements.EXPERIENCE_LEVEL);
        lift(lift, StatusBar.MESSAGES, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.OVERLAY_MESSAGE);
    }

    private static void lift(StatusBarLift lift, StatusBar bar, Identifier... elements) {
        for (Identifier id : elements) {
            HudElementRegistry.replaceElement(id, element -> (graphics, deltaTracker) ->
                    ClientPlatform.lifted(graphics, lift.pixels(bar), () -> element.extractRenderState(graphics, deltaTracker)));
        }
    }
}
