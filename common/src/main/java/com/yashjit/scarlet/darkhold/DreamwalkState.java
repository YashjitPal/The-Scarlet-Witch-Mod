package com.yashjit.scarlet.darkhold;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * What dreamwalking keeps about a player, saved with them and never synced: where they last stood in each dimension,
 * for the spirit to go back to, and while they are away, where their body was left and how they were playing, so that
 * whatever happens, even the server going down, they come back to it.
 */
public record DreamwalkState(Map<ResourceKey<Level>, BlockPos> lastStood, Optional<Away> away) {

    public static final DreamwalkState NONE = new DreamwalkState(Map.of(), Optional.empty());

    public static final MapCodec<DreamwalkState> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(Level.RESOURCE_KEY_CODEC, BlockPos.CODEC).optionalFieldOf("last_stood", Map.of()).forGetter(DreamwalkState::lastStood),
            Away.CODEC.optionalFieldOf("away").forGetter(DreamwalkState::away)
    ).apply(instance, DreamwalkState::new));

    public static final Codec<DreamwalkState> CODEC = MAP_CODEC.codec();

    public DreamwalkState stoodAt(ResourceKey<Level> dimension, BlockPos pos) {
        if (pos.equals(lastStood.get(dimension))) {
            return this;
        }
        Map<ResourceKey<Level>, BlockPos> stood = new HashMap<>(lastStood);
        stood.put(dimension, pos.immutable());
        return new DreamwalkState(Map.copyOf(stood), away);
    }

    public DreamwalkState withAway(Optional<Away> away) {
        return new DreamwalkState(lastStood, away);
    }

    /**
     * Where the body was left, and the game mode to give back.
     */
    public record Away(ResourceKey<Level> dimension, Vec3 position, float yRot, float xRot, GameType gameMode, Optional<GameType> previousGameMode) {

        public static final Codec<Away> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Away::dimension),
                Vec3.CODEC.fieldOf("position").forGetter(Away::position),
                Codec.FLOAT.optionalFieldOf("y_rot", 0.0F).forGetter(Away::yRot),
                Codec.FLOAT.optionalFieldOf("x_rot", 0.0F).forGetter(Away::xRot),
                GameType.CODEC.fieldOf("game_mode").forGetter(Away::gameMode),
                GameType.CODEC.optionalFieldOf("previous_game_mode").forGetter(Away::previousGameMode)
        ).apply(instance, Away::new));
    }
}
