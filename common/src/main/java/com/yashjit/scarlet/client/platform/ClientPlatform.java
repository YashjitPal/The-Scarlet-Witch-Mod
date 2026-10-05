package com.yashjit.scarlet.client.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Client-only loader hooks. Implementations queue registrations until their loader's events fire.
 */
public interface ClientPlatform {

    void registerLayerDefinition(ModelLayerLocation location, Supplier<LayerDefinition> definition);

    /**
     * Adds a render layer to every player and mannequin renderer, for both skin models.
     */
    void registerAvatarLayer(AvatarLayerFactory factory);

    /**
     * Called every frame while the world is being submitted. The pose stack is camera-relative.
     */
    void onSubmitWorldGeometry(WorldGeometryListener listener);

    /**
     * Handles a payload declared with {@code PayloadRegistry#clientbound}, on the client thread.
     */
    <T extends CustomPacketPayload> void registerClientbound(CustomPacketPayload.Type<T> type, Consumer<T> handler);

    <E extends Entity> void registerEntityRenderer(Supplier<EntityType<E>> type, EntityRendererProvider<E> provider);

    /**
     * Draws above the crosshair.
     */
    void registerHud(Identifier id, HudLayer layer);

    /**
     * Moves vanilla's status bars up by however many GUI pixels {@code lift} asks for, to make room beneath them.
     */
    void liftStatusBars(StatusBarLift lift);

    /**
     * Leaves vanilla's own parts of the HUD undrawn whenever {@code when} holds, the mod's still drawn: for the
     * showcase's films, in development only.
     */
    void hideVanillaHud(BooleanSupplier when);

    /**
     * Draws {@code draw} raised by {@code pixels}.
     */
    static void lifted(GuiGraphicsExtractor graphics, float pixels, Runnable draw) {
        if (pixels == 0.0F) {
            draw.run();
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0F, -pixels);
        draw.run();
        graphics.pose().popMatrix();
    }

    enum StatusBar {
        /** Hearts, armor, food, air and mount health. */
        VITALS,
        /** The experience level number. */
        LEVEL,
        /** The held item's name and action bar messages. */
        MESSAGES
    }

    @FunctionalInterface
    interface StatusBarLift {
        float pixels(StatusBar bar);
    }

    @FunctionalInterface
    interface AvatarLayerFactory {
        RenderLayer<AvatarRenderState, PlayerModel> create(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet models);
    }

    @FunctionalInterface
    interface WorldGeometryListener {
        void submit(SubmitNodeCollector collector, PoseStack poseStack);
    }

    @FunctionalInterface
    interface HudLayer {
        void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker);
    }
}
