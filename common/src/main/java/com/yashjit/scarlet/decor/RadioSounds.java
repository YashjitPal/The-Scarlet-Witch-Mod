package com.yashjit.scarlet.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where the client learns of radios playing near it, to play their music: each one that is on reports itself now and
 * then as the client animates the blocks around the player.
 */
public final class RadioSounds {

    private static Listener listener = (level, pos, state) -> {
    };

    private RadioSounds() {
    }

    public static void listen(Listener listener) {
        RadioSounds.listener = listener;
    }

    static void heard(Level level, BlockPos pos, BlockState state) {
        listener.heard(level, pos, state);
    }

    public interface Listener {
        void heard(Level level, BlockPos pos, BlockState state);
    }
}
