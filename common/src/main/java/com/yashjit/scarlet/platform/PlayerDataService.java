package com.yashjit.scarlet.platform;

import com.yashjit.scarlet.darkhold.Corruption;
import com.yashjit.scarlet.magic.MagicState;
import com.yashjit.scarlet.player.ScarletPlayerData;
import net.minecraft.world.entity.player.Player;

/**
 * Per-player state, saved with the player and synced to every client that can see them.
 */
public interface PlayerDataService {

    ScarletPlayerData get(Player player);

    /**
     * Server only. Values are immutable, so syncing happens on every change.
     */
    void set(Player player, ScarletPlayerData data);

    MagicState magic(Player player);

    /**
     * Server only, like {@link #set}.
     */
    void setMagic(Player player, MagicState state);

    /**
     * Kept through death: dying is no cure for it.
     */
    Corruption corruption(Player player);

    /**
     * Server only, like {@link #set}.
     */
    void setCorruption(Player player, Corruption corruption);
}
