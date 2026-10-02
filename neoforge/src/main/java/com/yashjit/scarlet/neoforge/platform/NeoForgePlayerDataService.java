package com.yashjit.scarlet.neoforge.platform;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.magic.MagicState;
import com.yashjit.scarlet.platform.PlayerDataService;
import com.yashjit.scarlet.player.ScarletPlayerData;
import java.util.function.Supplier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class NeoForgePlayerDataService implements PlayerDataService {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Scarlet.MOD_ID);

    private static final Supplier<AttachmentType<ScarletPlayerData>> PLAYER_DATA = ATTACHMENTS.register("player_data",
            () -> AttachmentType.builder(() -> ScarletPlayerData.DEFAULT)
                    .serialize(ScarletPlayerData.MAP_CODEC)
                    .sync(ScarletPlayerData.STREAM_CODEC)
                    .build());

    private static final Supplier<AttachmentType<MagicState>> MAGIC = ATTACHMENTS.register("magic",
            () -> AttachmentType.builder(() -> MagicState.DEFAULT)
                    .serialize(MagicState.MAP_CODEC)
                    .sync(MagicState.STREAM_CODEC)
                    .build());

    @Override
    public ScarletPlayerData get(Player player) {
        return player.getData(PLAYER_DATA);
    }

    @Override
    public void set(Player player, ScarletPlayerData data) {
        player.setData(PLAYER_DATA, data);
    }

    @Override
    public MagicState magic(Player player) {
        return player.getData(MAGIC);
    }

    @Override
    public void setMagic(Player player, MagicState state) {
        player.setData(MAGIC, state);
    }
}
