package com.yashjit.scarlet.client.entity;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.entity.DreamBody;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A dreamwalker's body as clients have it: in its owner's own skin, looked up the way a mannequin's is.
 */
public final class ClientDreamBody extends DreamBody implements ClientAvatarEntity {

    private final ClientAvatarState avatarState = new ClientAvatarState();
    private final PlayerSkinRenderCache skinRenderCache;
    private @Nullable CompletableFuture<Optional<PlayerSkin>> skinLookup;
    private PlayerSkin skin = DefaultPlayerSkin.getDefaultSkin();

    private ClientDreamBody(EntityType<? extends DreamBody> type, Level level, PlayerSkinRenderCache skinRenderCache) {
        super(type, level);
        this.skinRenderCache = skinRenderCache;
    }

    /**
     * Bodies a client is told of become this kind.
     */
    public static void registerOverride() {
        DreamBody.constructor = (type, level) -> level instanceof ClientLevel
                ? new ClientDreamBody(type, level, Minecraft.getInstance().playerSkinRenderCache())
                : new DreamBody(type, level);
    }

    /**
     * Its renderer only gathers what to draw: a body is drawn as a player is, in the skin's own build, slim or wide.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static EntityRenderer<DreamBody, ?> renderer(EntityRendererProvider.Context context) {
        return (EntityRenderer) new AvatarRenderer<ClientDreamBody>(context, false);
    }

    @Override
    public void tick() {
        super.tick();
        avatarState.tick(position(), getDeltaMovement());
        if (skinLookup != null && skinLookup.isDone()) {
            try {
                skinLookup.get().ifPresent(found -> skin = found);
            } catch (Exception e) {
                Scarlet.LOG.error("Could not look up a dreamwalker's skin", e);
            }
            skinLookup = null;
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (accessor.equals(PROFILE)) {
            if (skinLookup != null) {
                skinLookup.cancel(false);
            }
            skinLookup = skinRenderCache.lookup(getProfile()).thenApply(info -> info.map(PlayerSkinRenderCache.RenderInfo::playerSkin));
        }
    }

    @Override
    public ClientAvatarState avatarState() {
        return avatarState;
    }

    @Override
    public PlayerSkin getSkin() {
        return skin;
    }

    @Override
    public Parrot.@Nullable Variant getParrotVariantOnShoulder(boolean left) {
        return null;
    }

    @Override
    public boolean showExtraEars() {
        return false;
    }
}
