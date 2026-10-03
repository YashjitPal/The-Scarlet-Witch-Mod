package com.yashjit.scarlet.client.fx;

import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.costume.CostumeView;
import com.yashjit.scarlet.client.darkhold.CorruptionClient;
import com.yashjit.scarlet.client.render.Glow;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Particles for suiting up and down, emitted per client tick for every nearby player.
 *
 * <ul>
 *     <li>Suit up: threads spiral up the body, riding the costume's reveal front, then the crown flares.</li>
 *     <li>Suit down: the costume burns away into embers that drift upward.</li>
 *     <li>Suited: a few sparks slip off the fingertips.</li>
 * </ul>
 */
public final class TransformationFx {

    private static final float CROWN_FLARE_PHASE = 0.86F;
    private static final Int2FloatMap LAST_PHASE = new Int2FloatOpenHashMap();

    private TransformationFx() {
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.isPaused()) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        for (Player player : minecraft.level.players()) {
            if (player.isInvisible() || player.distanceToSqr(camera) > 80 * 80) {
                continue;
            }
            CostumeView view = CostumeView.of(player, 0.0F);
            if (view == null) {
                LAST_PHASE.remove(player.getId());
                continue;
            }
            float last = LAST_PHASE.getOrDefault(player.getId(), 1.0F);
            try (Glow.Darkening ignored = Glow.darkening(CorruptionClient.darkness(player))) {
                if (view.transforming()) {
                    if (view.suited()) {
                        rise(player, view);
                        boolean ownFirstPerson = player == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
                        CastFx.handDust(player, 1.2F, ownFirstPerson);
                    } else {
                        burnAway(player, view);
                    }
                } else if (view.suited()) {
                    fingertips(player);
                }
                if (view.suited() && last < CROWN_FLARE_PHASE && view.phase() >= CROWN_FLARE_PHASE) {
                    crownFlare(player);
                }
            }
            LAST_PHASE.put(player.getId(), view.phase());
        }
    }

    /**
     * Height of the costume's reveal front above the feet, matching the reveal mask's thresholds.
     */
    private static double revealHeight(Player player, float progress) {
        return Math.clamp((progress - 0.04) / 0.86, 0.0, 1.0) * player.getBbHeight() * 0.86;
    }

    private static void rise(Player player, CostumeView view) {
        RandomSource random = ScarletFx.random();
        double front = player.getY() + revealHeight(player, view.progress());
        int count = Math.round(8 * ScarletFx.density());
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = 0.4 + random.nextDouble() * 0.16;
            Vec3 position = new Vec3(player.getX() + Math.cos(angle) * radius, front + random.nextGaussian() * 0.04, player.getZ() + Math.sin(angle) * radius);
            Vec3 velocity = new Vec3(-Math.sin(angle) * 0.07, 0.025 + random.nextDouble() * 0.03, Math.cos(angle) * 0.07);
            int core = random.nextFloat() < 0.15F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET;
            ScarletFx.spark(position, velocity, 9 + random.nextInt(7), 0.028F + random.nextFloat() * 0.022F, core, ScarletPalette.SCARLET, 0.0F, 0.86F);
        }
        // two threads winding in from further out
        for (int strand = 0; strand < 2; strand++) {
            double angle = view.phase() * Math.PI * 7 + strand * Math.PI;
            Vec3 position = new Vec3(player.getX() + Math.cos(angle) * 0.75, front - 0.1, player.getZ() + Math.sin(angle) * 0.75);
            Vec3 velocity = new Vec3(-Math.cos(angle) * 0.045, 0.05, -Math.sin(angle) * 0.045);
            ScarletFx.spark(position, velocity, 12, 0.05F, ScarletPalette.CORE, ScarletPalette.BRIGHT_SCARLET, 0.0F, 0.9F);
        }
    }

    private static void burnAway(Player player, CostumeView view) {
        RandomSource random = ScarletFx.random();
        double front = player.getY() + revealHeight(player, view.progress());
        int count = Math.round(6 * ScarletFx.density());
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = 0.32 + random.nextDouble() * 0.14;
            Vec3 position = new Vec3(player.getX() + Math.cos(angle) * radius, front + random.nextGaussian() * 0.06, player.getZ() + Math.sin(angle) * radius);
            Vec3 velocity = new Vec3(Math.cos(angle) * 0.012, 0.012 + random.nextDouble() * 0.02, Math.sin(angle) * 0.012);
            int core = random.nextFloat() < 0.5F ? ScarletPalette.SCARLET : ScarletPalette.CRIMSON;
            ScarletFx.spark(position, velocity, 18 + random.nextInt(14), 0.03F + random.nextFloat() * 0.025F, core, ScarletPalette.WINE, -0.0015F, 0.93F);
        }
    }

    private static void fingertips(Player player) {
        RandomSource random = ScarletFx.random();
        if (random.nextFloat() > 0.45F * ScarletFx.density()) {
            return;
        }
        boolean right = random.nextBoolean();
        float yaw = (float) Math.toRadians(player.yBodyRot);
        double side = right ? 1.0 : -1.0;
        Vec3 hand = new Vec3(player.getX() - Math.cos(yaw) * 0.37 * side, player.getY() + 0.68, player.getZ() - Math.sin(yaw) * 0.37 * side);
        Vec3 velocity = new Vec3(random.nextGaussian() * 0.008, -0.004 - random.nextDouble() * 0.01, random.nextGaussian() * 0.008);
        ScarletFx.spark(hand, velocity, 14 + random.nextInt(10), 0.025F, ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET, 0.0005F, 0.94F);
    }

    private static void crownFlare(Player player) {
        RandomSource random = ScarletFx.random();
        Vec3 crown = new Vec3(player.getX(), player.getEyeY() + 0.28, player.getZ());
        ScarletFx.spark(crown, Vec3.ZERO, 7, 0.32F, ScarletPalette.CORE, ScarletPalette.BRIGHT_SCARLET, 0.0F, 1.0F);
        int count = Math.round(30 * ScarletFx.density());
        for (int i = 0; i < count; i++) {
            Vec3 direction = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.6 + 0.25, random.nextGaussian()).normalize();
            Vec3 velocity = direction.scale(0.08 + random.nextDouble() * 0.08);
            int core = random.nextFloat() < 0.4F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET;
            ScarletFx.spark(crown, velocity, 10 + random.nextInt(10), 0.03F + random.nextFloat() * 0.03F, core, ScarletPalette.SCARLET, 0.002F, 0.88F);
        }
    }
}
