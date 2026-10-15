package com.yashjit.scarlet.decor;

import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.hex.Hexes;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A telephone. Inside a Hex it rings by itself now and then, with someone near enough to hear it: a bell, a trill or a
 * ringtone, by its era, until it is answered or the caller gives up. Answered, someone is on the line: a neighbor, a
 * salesman, a wrong number, or a voice from outside the Hex breaking through the static. Picked up when it isn't
 * ringing, you dial out, and no one answers.
 */
public class TelephoneBlock extends DecorBlock {

    public static final BooleanProperty RINGING = BooleanProperty.create("ringing");

    /** One chance in this, each time a telephone is looked at at random, that someone calls. */
    private static final int CALLS_ONE_IN = 8;
    /** Whoever might answer it must be this near. */
    private static final double HEARD_FROM = 16.0;
    /** Ticks between one ring and the next, and one chance in this after each that the caller gives up. */
    private static final int RING_EVERY = 40;
    private static final int GIVES_UP_ONE_IN = 7;
    /** How many different callers there are, each with their own line. */
    private static final int CALLERS = 8;

    public TelephoneBlock(BlockBehaviour.Properties properties, Function<BlockState, VoxelShape> north) {
        super(properties, north);
        registerDefaultState(defaultBlockState().setValue(RINGING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RINGING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(RINGING, false);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return !state.getValue(RINGING);
    }

    /** Someone calling, now and then, a telephone in a Hex with someone near to hear it. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(CALLS_ONE_IN) != 0 || Hexes.at(level, Vec3.atCenterOf(pos)) == null
                || level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, HEARD_FROM, false) == null) {
            return;
        }
        level.setBlock(pos, state.setValue(RINGING, true), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, 1);
    }

    /** One ring, and the next after it, until the caller gives up. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(RINGING)) {
            return;
        }
        ring(level, pos, state.getValue(EraDecor.ERA));
        if (random.nextInt(GIVES_UP_ONE_IN) == 0) {
            level.setBlock(pos, state.setValue(RINGING, false), Block.UPDATE_ALL);
        } else {
            level.scheduleTick(pos, this, RING_EVERY);
        }
    }

    /** A ring: a bell in the old days, sharper with every decade, then a trill, and today a ringtone. */
    private static void ring(Level level, BlockPos pos, Era era) {
        if (era == Era.PRESENT) {
            float[] tune = {1.19F, 1.5F, 1.78F, 1.5F};
            for (int k = 0; k < tune.length; k++) {
                play(level, pos, SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), 0.7F, tune[k]);
            }
        } else if (era.ordinal() >= Era.EIGHTIES.ordinal()) {
            play(level, pos, SoundEvents.NOTE_BLOCK_BIT.value(), 0.55F, 1.6F);
            play(level, pos, SoundEvents.NOTE_BLOCK_BIT.value(), 0.55F, 1.8F);
        } else {
            float pitch = 1.5F + era.ordinal() * 0.08F;
            play(level, pos, SoundEvents.NOTE_BLOCK_BELL.value(), 0.9F, pitch);
            play(level, pos, SoundEvents.NOTE_BLOCK_BELL.value(), 0.9F, pitch * 1.06F);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        Era era = state.getValue(EraDecor.ERA);
        // the receiver lifted
        level.playSound(player, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.45F, 1.8F);
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        if (state.getValue(RINGING)) {
            server.setBlock(pos, state.setValue(RINGING, false), Block.UPDATE_ALL);
            int caller = server.getRandom().nextInt(CALLERS);
            // a voice on the line, small and far off
            for (int k = 0; k < 3; k++) {
                server.playSound(null, pos, SoundEvents.VILLAGER_AMBIENT, SoundSource.BLOCKS, 0.25F, 1.5F + server.getRandom().nextFloat() * 0.4F);
            }
            player.sendOverlayMessage(Component.translatable("decor.scarlet.telephone.caller." + caller));
        } else {
            // dialing out: the clicks of a dial in the old days, the tones of buttons later
            SoundEvent dial = era.ordinal() <= Era.SEVENTIES.ordinal() ? SoundEvents.NOTE_BLOCK_HAT.value() : SoundEvents.NOTE_BLOCK_PLING.value();
            for (int k = 0; k < 4; k++) {
                server.playSound(null, pos, dial, SoundSource.BLOCKS, 0.35F, 1.4F + server.getRandom().nextFloat() * 0.5F);
            }
            player.sendOverlayMessage(Component.translatable("decor.scarlet.telephone.dial"));
        }
        return InteractionResult.SUCCESS;
    }

    /** Notes over a ringing telephone, for anyone who can't hear it. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(RINGING) && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, random.nextInt(24) / 24.0, 0.0, 0.0);
        }
    }

    private static void play(Level level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
    }
}
