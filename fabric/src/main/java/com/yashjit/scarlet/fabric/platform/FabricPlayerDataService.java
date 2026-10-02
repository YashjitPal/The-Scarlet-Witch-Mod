package com.yashjit.scarlet.fabric.platform;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.magic.MagicState;
import com.yashjit.scarlet.platform.PlayerDataService;
import com.yashjit.scarlet.player.ScarletPlayerData;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.player.Player;

public final class FabricPlayerDataService implements PlayerDataService {

    private static final AttachmentType<ScarletPlayerData> PLAYER_DATA = AttachmentRegistry.create(Scarlet.id("player_data"),
            builder -> builder
                    .persistent(ScarletPlayerData.CODEC)
                    .initializer(() -> ScarletPlayerData.DEFAULT)
                    .syncWith(ScarletPlayerData.STREAM_CODEC, AttachmentSyncPredicate.all()));

    private static final AttachmentType<MagicState> MAGIC = AttachmentRegistry.create(Scarlet.id("magic"),
            builder -> builder
                    .persistent(MagicState.CODEC)
                    .initializer(() -> MagicState.DEFAULT)
                    .syncWith(MagicState.STREAM_CODEC, AttachmentSyncPredicate.all()));

    /**
     * Attachment types must exist on both sides before any world loads.
     */
    public static void bootstrap() {
    }

    @Override
    public ScarletPlayerData get(Player player) {
        return player.getAttachedOrElse(PLAYER_DATA, ScarletPlayerData.DEFAULT);
    }

    @Override
    public void set(Player player, ScarletPlayerData data) {
        player.setAttached(PLAYER_DATA, data);
    }

    @Override
    public MagicState magic(Player player) {
        return player.getAttachedOrElse(MAGIC, MagicState.DEFAULT);
    }

    @Override
    public void setMagic(Player player, MagicState state) {
        player.setAttached(MAGIC, state);
    }
}
