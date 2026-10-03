package com.yashjit.scarlet.client.fx;

import com.yashjit.scarlet.client.anim.CastGestures;
import com.yashjit.scarlet.client.darkhold.CorruptionClient;
import com.yashjit.scarlet.client.magic.Hands;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.magic.Magic;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Effects that leave the hands of anyone wearing a crown: a flash and spray of sparks at the instant each strike lets
 * go, and a trail of scarlet dust from both hands while flying, lighter while levitating.
 */
public final class CastFx {

    private static final Int2ObjectMap<double[]> RELEASED = new Int2ObjectOpenHashMap<>();

    private CastFx() {
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.isPaused()) {
            return;
        }
        double now = minecraft.level.getGameTime();
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        for (Player player : minecraft.level.players()) {
            if (player.isInvisible() || player.distanceToSqr(camera) > 80 * 80 || !CrownItem.isWearingCrown(player)) {
                continue;
            }
            boolean ownFirstPerson = ScarletFx.isFirstPersonViewOf(player);
            double[] released = RELEASED.computeIfAbsent(player.getId(), id -> new double[] {-1.0E9, -1.0E9});
            float darkness = CorruptionClient.darkness(player);
            try (Glow.Darkening ignored = Glow.darkening(darkness)) {
                for (HumanoidArm arm : HumanoidArm.values()) {
                    double release = CastGestures.releaseTime(player, arm);
                    if (release > released[arm.ordinal()] && now >= release) {
                        released[arm.ordinal()] = release;
                        if (now - release < 3.0) {
                            BoltFx.release(Hands.palm(player, arm), player.getLookAngle(), ownFirstPerson, darkness);
                        }
                    }
                }
                if (Magic.state(player).levitating()) {
                    handDust(player, 0.6F, ownFirstPerson);
                } else if (flying(player, minecraft)) {
                    trail(player, ownFirstPerson);
                }
            }
        }
    }

    public static void prune(ClientLevel level) {
        RELEASED.int2ObjectEntrySet().removeIf(entry -> level.getEntity(entry.getIntKey()) == null);
    }

    /**
     * Dust from both palms, a few specks a tick. Used while flying and while suiting up.
     */
    public static void handDust(Player player, float perHand, boolean ownFirstPerson) {
        RandomSource random = ScarletFx.random();
        Vec3 look = player.getLookAngle();
        for (HumanoidArm arm : HumanoidArm.values()) {
            Vec3 palm = Hands.palm(player, arm);
            if (ownFirstPerson) {
                // keep it out of your own view: just behind and below your hands
                palm = palm.subtract(look.scale(0.3)).add(0, -0.15, 0);
            }
            float budget = perHand * ScarletFx.density();
            int count = (int) budget + (random.nextFloat() < budget % 1 ? 1 : 0);
            for (int i = 0; i < count; i++) {
                Vec3 jitter = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(0.05);
                ChaosDust.spawn(palm.add(jitter), jitter.scale(0.15));
            }
        }
    }

    private static boolean flying(Player player, Minecraft minecraft) {
        return player.isFallFlying() || (player == minecraft.player && player.getAbilities().flying);
    }

    private static void trail(Player player, boolean ownFirstPerson) {
        handDust(player, 2.5F, ownFirstPerson);
    }
}
