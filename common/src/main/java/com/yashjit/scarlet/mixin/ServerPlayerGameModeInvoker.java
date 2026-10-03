package com.yashjit.scarlet.mixin;

import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Sets a game mode along with the one before it, so a dreamwalker comes back playing exactly as they were.
 */
@Mixin(ServerPlayerGameMode.class)
public interface ServerPlayerGameModeInvoker {

    @Invoker("setGameModeForPlayer")
    void scarlet$setGameModeForPlayer(GameType gameMode, @Nullable GameType previousGameMode);
}
