package com.yashjit.scarlet.magic;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.platform.Services;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

/** A solid, itemless construct. Scheduled ticks survive chunk unload and world saves. */
public final class WeaveBlock extends Block {
    public static final Supplier<Block> BLOCK = Services.REGISTRY.register(BuiltInRegistries.BLOCK, "starlight_weave",
            () -> new WeaveBlock(Properties.of().strength(0.35F).noLootTable().noOcclusion().lightLevel(s -> 11)
                    .pushReaction(PushReaction.POPPED).sound(SoundType.AMETHYST)
                    .setId(ResourceKey.create(Registries.BLOCK, Scarlet.id("starlight_weave")))));

    private WeaveBlock(Properties properties) { super(properties); }
    public static void bootstrap() {}

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.removeBlock(pos, false);
    }
}
