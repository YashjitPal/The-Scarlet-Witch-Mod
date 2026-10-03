package com.yashjit.scarlet.hex;

import com.yashjit.scarlet.decor.SeatBlock;
import com.yashjit.scarlet.decor.SwitchedDecorBlock;
import com.yashjit.scarlet.entity.Seat;
import com.yashjit.scarlet.mixin.MobGoalsAccessor;
import com.yashjit.scarlet.network.GesturePayload;
import com.yashjit.scarlet.platform.Services;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What a Hex's townspeople do with their days, like extras in a sitcom: they wander about, sit down on a couch or an
 * armchair (in front of the television if there is one, which they switch on), stop to chat with one another, and
 * wave to anyone who comes by.
 *
 * <p>A goal of its own on each resident, over the mob's own wandering, so it takes the lead only while it has
 * something for them to do. Villagers keep the village life they already have.
 */
public final class Sitcom {

    /** Each resident's routine, while it is one. */
    private static final Map<Mob, Routine> ROUTINES = new WeakHashMap<>();

    /** How far a townsperson looks for somewhere to sit, across and up or down. */
    private static final int SEAT_REACH = 10;
    private static final int SEAT_REACH_UP = 3;
    /** How far in front of a seat a television can stand and be watched from it. */
    private static final int TV_REACH = 6;
    private static final double CHAT_REACH = 10.0;
    private static final double WAVE_REACH = 6.0;
    /** How long a townsperson waits before waving to the same person again. */
    private static final int WAVE_AGAIN = 1200;

    private Sitcom() {
    }

    /** Gives a townsperson their routine, if they haven't one, and the manners to open doors and close them after. */
    static void attach(Mob mob) {
        if (mob instanceof Villager || ROUTINES.containsKey(mob)) {
            return;
        }
        Routine routine = new Routine(mob);
        ROUTINES.put(mob, routine);
        GoalSelector goals = ((MobGoalsAccessor) mob).scarlet$goalSelector();
        goals.addGoal(0, routine);
        if (GoalUtils.hasGroundPathNavigation(mob)) {
            routine.couldOpenDoors = mob.getNavigation().getNodeEvaluator().canOpenDoors();
            mob.getNavigation().setCanOpenDoors(true);
            routine.doors = new OpenDoorGoal(mob, true);
            goals.addGoal(1, routine.doors);
        }
    }

    /** Takes it away again, as the spell breaks or they leave the Hex. */
    static void detach(Mob mob) {
        Routine routine = ROUTINES.remove(mob);
        if (routine != null) {
            routine.stop();
            GoalSelector goals = ((MobGoalsAccessor) mob).scarlet$goalSelector();
            goals.removeGoal(routine);
            if (routine.doors != null) {
                goals.removeGoal(routine.doors);
                mob.getNavigation().setCanOpenDoors(routine.couldOpenDoors);
            }
        }
    }

    /** What a townsperson is up to, in a word. */
    public static String doing(Mob mob) {
        Routine routine = ROUTINES.get(mob);
        return routine == null ? "no routine" : routine.act.name().toLowerCase(Locale.ROOT);
    }

    private static void gesture(Mob mob, GesturePayload.Kind kind) {
        Services.NETWORK.sendToTrackingAndSelf(mob, new GesturePayload(mob.getId(), kind));
    }

    /** Whether another townsperson has a seat already, or is on their way to it. */
    private static boolean spokenFor(Level level, BlockPos pos, Mob asking) {
        for (Routine routine : ROUTINES.values()) {
            if (routine.mob != asking && routine.act == Act.SIT && pos.equals(routine.seat) && routine.mob.isAlive()
                    && routine.mob.level() == level) {
                return true;
            }
        }
        return Seat.isTaken(level, pos);
    }

    private enum Act {
        NONE,
        SIT,
        CHAT,
        WAVE
    }

    static final class Routine extends Goal {
        private final Mob mob;
        private Act act = Act.NONE;
        private int timer;
        private long nextThought;
        private @Nullable BlockPos seat;
        /** Where they walk to, to sit down from. */
        private @Nullable BlockPos approach;
        private @Nullable BlockPos tv;
        private @Nullable Mob partner;
        /** Who they are waving to, and for how many ticks more: over whatever else they are doing. */
        private @Nullable Player greeted;
        private int waving;
        private int tries;
        private final Map<UUID, Long> wavedAt = new HashMap<>();
        /** The door manners it was given, and whether it had them before. */
        private @Nullable OpenDoorGoal doors;
        private boolean couldOpenDoors;
        /** A door they opened on their way, to close behind them. */
        private @Nullable BlockPos opened;
        private long closeAt;

        Routine(Mob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (!Residents.isResident(mob) || !(mob.level() instanceof ServerLevel level) || mob.isInWater()) {
                return false;
            }
            if (act != Act.NONE) {
                // asked over for a chat by someone else
                return true;
            }
            Player player = passerby(level);
            if (player != null) {
                // with nothing else to do, they stop to wave
                greet(player);
                act = Act.WAVE;
                timer = GesturePayload.Kind.WAVE.ticks;
                return true;
            }
            long now = level.getGameTime();
            if (now < nextThought) {
                return false;
            }
            nextThought = now + 100 + mob.getRandom().nextInt(140);
            float roll = mob.getRandom().nextFloat();
            if (roll < 0.45F && findSeat(level)) {
                act = Act.SIT;
                timer = 600 + mob.getRandom().nextInt(1000);
                tries = 0;
                return true;
            }
            if (roll < 0.8F && findPartner(level)) {
                return true;
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            if (act == Act.NONE || timer <= 0 || !Residents.isResident(mob)) {
                return false;
            }
            return switch (act) {
                case SIT -> seat != null && mob.level().getBlockState(seat).getBlock() instanceof SeatBlock;
                case CHAT -> partner != null && partner.isAlive() && ROUTINES.get(partner) instanceof Routine other && other.partner == mob;
                case WAVE -> waving > 0;
                case NONE -> false;
            };
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            if (act == Act.WAVE) {
                mob.getNavigation().stop();
            } else if (act == Act.SIT) {
                walkToSeat();
            }
        }

        @Override
        public void tick() {
            timer--;
            if (mob.level() instanceof ServerLevel level) {
                manners(level);
            }
            if (waving == 0 && mob.tickCount % 10 == 0 && mob.level() instanceof ServerLevel level) {
                Player player = passerby(level);
                if (player != null) {
                    greet(player);
                }
            }
            switch (act) {
                case SIT -> sit();
                case CHAT -> chat();
                default -> {
                }
            }
            // last, so they turn to whoever they wave to over the television or the chat
            if (waving > 0) {
                wave();
            }
        }

        @Override
        public void stop() {
            if (act == Act.SIT && mob.getVehicle() instanceof Seat) {
                mob.stopRiding();
            }
            act = Act.NONE;
            seat = null;
            approach = null;
            tv = null;
            partner = null;
            greeted = null;
            waving = 0;
            timer = 0;
        }

        /** Someone come by they haven't waved to in a while, near enough and in sight. */
        private @Nullable Player passerby(ServerLevel level) {
            Player player = level.getNearestPlayer(mob, WAVE_REACH);
            if (player == null || player.isSpectator() || !mob.hasLineOfSight(player)) {
                return null;
            }
            long now = level.getGameTime();
            Long last = wavedAt.get(player.getUUID());
            if (last != null && now - last < WAVE_AGAIN) {
                return null;
            }
            wavedAt.put(player.getUUID(), now);
            return player;
        }

        private void greet(Player player) {
            greeted = player;
            waving = GesturePayload.Kind.WAVE.ticks;
            gesture(mob, GesturePayload.Kind.WAVE);
        }

        /**
         * Opens a door they walk up against on their way somewhere, and closes it behind them once they are through.
         */
        private void manners(ServerLevel level) {
            if (opened != null && level.getGameTime() > closeAt && mob.blockPosition().distManhattan(opened) > 1) {
                BlockState door = level.getBlockState(opened);
                if (door.getBlock() instanceof DoorBlock block && door.getValue(DoorBlock.OPEN)) {
                    block.setOpen(mob, level, door, opened, false);
                }
                opened = null;
            }
            if (!mob.horizontalCollision || mob.getNavigation().isDone()) {
                return;
            }
            Direction ahead = Direction.fromYRot(mob.getYRot());
            for (BlockPos pos : new BlockPos[] {mob.blockPosition(), mob.blockPosition().relative(ahead)}) {
                BlockState door = level.getBlockState(pos);
                if (DoorBlock.isWoodenDoor(door) && !door.getValue(DoorBlock.OPEN)) {
                    ((DoorBlock) door.getBlock()).setOpen(mob, level, door, pos, true);
                    opened = pos;
                    closeAt = level.getGameTime() + 30;
                    return;
                }
            }
        }

        /** Turns to whoever is passing while they wave. */
        private void wave() {
            waving--;
            if (greeted == null || !greeted.isAlive() || greeted.level() != mob.level()) {
                waving = 0;
                return;
            }
            mob.getLookControl().setLookAt(greeted, 30.0F, 30.0F);
        }

        /** Walks over to the seat, sits down facing the way it does, and watches the television if there is one. */
        private void sit() {
            if (seat == null || !(mob.level() instanceof ServerLevel level)) {
                return;
            }
            if (mob.getVehicle() instanceof Seat) {
                if (tv != null) {
                    mob.getLookControl().setLookAt(Vec3.atCenterOf(tv));
                    BlockState set = level.getBlockState(tv);
                    if (timer % 200 == 100 && set.getBlock() instanceof SwitchedDecorBlock screen && screen.kind() == SwitchedDecorBlock.Kind.TELEVISION
                            && !set.getValue(SwitchedDecorBlock.LIT)) {
                        // reaching for the remote
                        level.setBlock(tv, set.setValue(SwitchedDecorBlock.LIT, true), Block.UPDATE_ALL);
                        level.playSound(null, tv, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4F, 1.1F);
                    }
                }
                return;
            }
            double distance = mob.position().distanceToSqr(Vec3.atBottomCenterOf(seat));
            if (distance < 2.6) {
                BlockState state = level.getBlockState(seat);
                if (!(state.getBlock() instanceof SeatBlock block)
                        || !Seat.sit(level, seat, block.seatHeight(state), state.getValue(HorizontalDirectionalBlock.FACING), mob)) {
                    timer = 0;
                }
                return;
            }
            if (mob.getNavigation().isDone()) {
                if (++tries > 6) {
                    // can't get there: give up and do something else
                    timer = 0;
                    return;
                }
                walkToSeat();
            }
        }

        private void walkToSeat() {
            if (approach != null) {
                mob.getNavigation().moveTo(mob.getNavigation().createPath(approach, 0), 1.0);
            }
        }

        /** Walks up to the other, then stands talking: turned to them, hands going now and then. */
        private void chat() {
            if (partner == null || !(mob.level() instanceof ServerLevel level)) {
                return;
            }
            mob.getLookControl().setLookAt(partner, 30.0F, 30.0F);
            double distance = mob.distanceToSqr(partner);
            if (distance > 2.5 * 2.5) {
                if (timer % 10 == 0) {
                    mob.getNavigation().moveTo(partner, 0.9);
                }
                return;
            }
            mob.getNavigation().stop();
            // they take turns: whoever began does the talking on even beats
            if (timer % 30 == (mob.getId() < partner.getId() ? 0 : 15)) {
                gesture(mob, mob.getRandom().nextBoolean() ? GesturePayload.Kind.TALK_RIGHT : GesturePayload.Kind.TALK_LEFT);
                level.playSound(null, mob.getX(), mob.getEyeY(), mob.getZ(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.18F,
                        1.25F + mob.getRandom().nextFloat() * 0.4F);
            }
        }

        /**
         * Somewhere to sit near by that no one has and that can be walked to, best of all one facing a television.
         */
        private boolean findSeat(ServerLevel level) {
            BlockPos at = mob.blockPosition();
            record Candidate(BlockPos pos, BlockPos approach, @Nullable BlockPos tv, double score) {
            }
            List<Candidate> candidates = new ArrayList<>();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int dy = -SEAT_REACH_UP; dy <= SEAT_REACH_UP; dy++) {
                for (int dz = -SEAT_REACH; dz <= SEAT_REACH; dz++) {
                    for (int dx = -SEAT_REACH; dx <= SEAT_REACH; dx++) {
                        pos.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                        BlockState state = level.getBlockState(pos);
                        if (!(state.getBlock() instanceof SeatBlock) || spokenFor(level, pos, mob)) {
                            continue;
                        }
                        BlockPos seatPos = pos.immutable();
                        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
                        BlockPos screen = tvFacing(level, seatPos, facing);
                        candidates.add(new Candidate(seatPos, approach(level, seatPos, facing), screen,
                                Math.sqrt(dx * dx + dy * dy + dz * dz) - (screen != null ? 12.0 : 0.0)));
                    }
                }
            }
            candidates.sort(Comparator.comparingDouble(Candidate::score));
            // working out a path is the dear part: only for the few best
            for (int k = 0; k < Math.min(3, candidates.size()); k++) {
                Candidate candidate = candidates.get(k);
                Path path = mob.getNavigation().createPath(candidate.approach(), 0);
                if (path != null && path.canReach()) {
                    seat = candidate.pos();
                    approach = candidate.approach();
                    tv = candidate.tv();
                    return true;
                }
            }
            return false;
        }

        /**
         * The floor in front of a seat, where your feet go as you sit down, or the seat itself if a table stands
         * there.
         */
        private static BlockPos approach(ServerLevel level, BlockPos seat, Direction facing) {
            BlockPos front = seat.relative(facing);
            for (BlockPos spot : new BlockPos[] {front, front.relative(facing.getClockWise()), front.relative(facing.getCounterClockWise())}) {
                if (level.getBlockState(spot).getCollisionShape(level, spot).max(Direction.Axis.Y) <= 0.125
                        && level.getBlockState(spot.above()).getCollisionShape(level, spot.above()).isEmpty()) {
                    return spot;
                }
            }
            return seat;
        }

        /** A television standing in front of a seat, within sight of it. */
        private static @Nullable BlockPos tvFacing(ServerLevel level, BlockPos seat, Direction facing) {
            for (int d = 1; d <= TV_REACH; d++) {
                for (int side = -1; side <= 1; side++) {
                    BlockPos pos = seat.relative(facing, d).relative(facing.getClockWise(), side);
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockState state = level.getBlockState(pos.above(dy));
                        if (state.getBlock() instanceof SwitchedDecorBlock screen && screen.kind() == SwitchedDecorBlock.Kind.TELEVISION) {
                            return pos.above(dy);
                        }
                    }
                }
            }
            return null;
        }

        /** Someone near by with nothing better to do, to have a chat with. */
        private boolean findPartner(ServerLevel level) {
            for (Mob other : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(CHAT_REACH), Residents::isResident)) {
                if (other == mob || other.isPassenger() || !(ROUTINES.get(other) instanceof Routine routine) || routine.act != Act.NONE) {
                    continue;
                }
                int length = 160 + mob.getRandom().nextInt(140);
                act = Act.CHAT;
                partner = other;
                timer = length;
                routine.act = Act.CHAT;
                routine.partner = mob;
                routine.timer = length;
                return true;
            }
            return false;
        }
    }
}
