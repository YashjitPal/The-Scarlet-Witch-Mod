package com.yashjit.scarlet.decor;

import com.yashjit.scarlet.hex.Era;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A decoration that answers a click: the telephone rings, the toaster pops.
 */
public class ChimeDecorBlock extends DecorBlock {

    private final Kind kind;

    public ChimeDecorBlock(BlockBehaviour.Properties properties, Function<BlockState, VoxelShape> north, Kind kind) {
        super(properties, north);
        this.kind = kind;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        Era era = state.getValue(EraDecor.ERA);
        switch (kind) {
            case TELEPHONE -> {
                if (era == Era.PRESENT) {
                    // a notification's chime
                    level.playSound(player, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.7F, 1.6F);
                    level.playSound(player, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.5F, 2.0F);
                } else {
                    // the bell, brrring brrring, sharper with every decade
                    float pitch = 1.5F + era.ordinal() * 0.08F;
                    level.playSound(player, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.8F, pitch);
                    level.playSound(player, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.8F, pitch * 1.06F);
                }
            }
            case TOASTER -> {
                level.playSound(player, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, 1.6F);
                level.playSound(player, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 0.6F);
                if (level instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.45, pos.getZ() + 0.5, 4, 0.12, 0.02, 0.06, 0.01);
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    public enum Kind {
        TELEPHONE,
        TOASTER
    }
}
