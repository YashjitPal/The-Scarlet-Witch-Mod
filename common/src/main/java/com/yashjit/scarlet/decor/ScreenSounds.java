package com.yashjit.scarlet.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where the client learns of televisions on near it, to play their shows: each one that is on reports itself now and
 * then as the client animates the blocks around the player.
 */
public final class ScreenSounds {

    private static RadioSounds.Listener listener = (level, pos, state) -> {
    };

    private ScreenSounds() {
    }

    public static void listen(RadioSounds.Listener listener) {
        ScreenSounds.listener = listener;
    }

    static void watched(Level level, BlockPos pos, BlockState state) {
        listener.heard(level, pos, state);
    }
}
