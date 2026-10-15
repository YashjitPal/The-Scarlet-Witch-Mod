package com.yashjit.scarlet.decor;

import com.yashjit.scarlet.hex.Hex;
import com.yashjit.scarlet.hex.HexData;
import com.yashjit.scarlet.hex.HexShape;
import com.yashjit.scarlet.hex.Hexes;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A clock on the wall, its hands turning with the time: the Hex's own hour, where its caster has set one, or else the
 * world's. Clicked, it tells the time.
 */
public class ClockBlock extends WallDecorBlock implements EntityBlock {

    public ClockBlock(BlockBehaviour.Properties properties, Function<BlockState, VoxelShape> north) {
        super(properties, north);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ClockBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            player.sendOverlayMessage(Component.translatable("decor.scarlet.clock", time(dayTime(server, Vec3.atCenterOf(pos)))));
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.4F, 1.9F);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.35F, 1.6F);
        }
        return InteractionResult.SUCCESS;
    }

    private static long dayTime(ServerLevel level, Vec3 at) {
        long now = level.getGameTime();
        for (Hex hex : HexData.of(level).all()) {
            int set = hex.sky().time().dayTime();
            if (set >= 0 && HexShape.contains(hex.center(), Hexes.wallRadius(hex, now), at)) {
                return set;
            }
        }
        return level.getDefaultClockTime();
    }

    /**
     * The time on a clock's face, from the ticks into a day, which starts at six in the morning.
     */
    static String time(long ticks) {
        long day = Math.floorMod(ticks, 24000L);
        int hours = (int) ((day / 1000 + 6) % 24);
        int minutes = (int) (day % 1000 * 60 / 1000);
        return String.format(Locale.ROOT, "%d:%02d %s", hours % 12 == 0 ? 12 : hours % 12, minutes, hours < 12 ? "AM" : "PM");
    }
}
