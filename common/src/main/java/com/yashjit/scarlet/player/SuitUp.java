package com.yashjit.scarlet.player;

import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.platform.Services;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Server-side rules for manifesting and dismissing the costume. Clients only ask; the server decides.
 */
public final class SuitUp {

    /**
     * Length of the transformation, in ticks. A toggle is ignored while one is still playing.
     */
    public static final int TRANSFORM_TICKS = 30;

    private SuitUp() {
    }

    public static void requestToggle(ServerPlayer player) {
        if (!CrownItem.isWearingCrown(player)) {
            player.sendOverlayMessage(Component.translatable("message.scarlet.suit_up.need_crown").withColor(ScarletPalette.BRIGHT_SCARLET));
            return;
        }
        ScarletPlayerData data = Services.PLAYER_DATA.get(player);
        if (player.level().getGameTime() - data.suitChangedAt() < TRANSFORM_TICKS) {
            return;
        }
        setSuited(player, !data.suited());
    }

    public static void setSuited(ServerPlayer player, boolean suited) {
        ScarletPlayerData data = Services.PLAYER_DATA.get(player);
        if (data.suited() == suited) {
            return;
        }
        Services.PLAYER_DATA.set(player, data.withSuited(suited, player.level().getGameTime()));
        playTransformation(player, suited);
    }

    // Visuals are drawn by every client from the synced state; the server only plays the sound.
    private static void playTransformation(ServerPlayer player, boolean suited) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                suited ? SoundEvents.ILLUSIONER_CAST_SPELL : SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.8F, suited ? 1.2F : 0.7F);
    }
}
