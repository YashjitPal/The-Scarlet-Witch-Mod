package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.PoseBlends;
import com.yashjit.scarlet.client.darkhold.CorruptionClient;
import com.yashjit.scarlet.client.magic.Hands;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.hex.HexPaint;
import com.yashjit.scarlet.network.PaintPayload;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Restyling as everyone sees it. While a caster paints, a crackling beam of scarlet magic flies from their outstretched
 * hand to where they look and sprays sparks off the surface it lands on. Each block it paints is written in the way the
 * Hex writes its town: its edges flare, a scanline runs down it and red pixels thin out over its faces. Blocks changing
 * back as the wall comes in over them snap back the same way.
 */
public final class PaintFx {

    /** Ticks a caster is still shown painting after word of it last came. */
    private static final int SHOWN_FOR = 6;
    private static final int MAX_WRITTEN = 1500;

    /** When word last came of each caster painting. */
    private static final Int2LongOpenHashMap PAINTING = new Int2LongOpenHashMap();
    private static final List<Written> WRITTEN = new ArrayList<>();
    private static @Nullable ClientLevel seenLevel;

    private PaintFx() {
    }

    public static void receive(PaintPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        if (payload.casterId() >= 0) {
            PAINTING.put(payload.casterId(), now);
        }
        RandomSource random = ScarletFx.random();
        int[] blocks = payload.blocks();
        int sparks = Math.round(30 * ScarletFx.density());
        for (int i = 0; i + 2 < blocks.length; i += 3) {
            BlockPos pos = new BlockPos(blocks[i], blocks[i + 1], blocks[i + 2]);
            WRITTEN.add(new Written(pos, now, random.nextInt()));
            if (sparks-- > 0) {
                Vec3 at = Vec3.atCenterOf(pos).add(randomUnit(random).scale(0.6));
                ScarletFx.spark(at, randomUnit(random).scale(0.04), 6 + random.nextInt(6), 0.03F,
                        random.nextFloat() < 0.3F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET, -0.002F, 0.85F);
            }
        }
        if (WRITTEN.size() > MAX_WRITTEN) {
            WRITTEN.subList(0, WRITTEN.size() - MAX_WRITTEN).clear();
        }
    }

    /**
     * Whether a player is painting right now, as far as this client has been told.
     */
    public static boolean painting(Player player) {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && PAINTING.containsKey(player.getId()) && level.getGameTime() - PAINTING.get(player.getId()) <= SHOWN_FOR;
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level != seenLevel) {
            seenLevel = level;
            PAINTING.clear();
            WRITTEN.clear();
        }
        if (level == null || minecraft.isPaused()) {
            return;
        }
        long now = level.getGameTime();
        WRITTEN.removeIf(written -> now - written.at() > BlockGlitch.SETTLE + 1);
        PAINTING.int2LongEntrySet().removeIf(entry -> now - entry.getLongValue() > SHOWN_FOR + 40);
        RandomSource random = ScarletFx.random();
        for (Player player : level.players()) {
            if (!painting(player) || player.isInvisible()) {
                continue;
            }
            BlockHitResult hit = brush(player, 1.0F);
            if (hit == null) {
                continue;
            }
            // sparks thrown off the surface where the stream lands
            Vec3 normal = hit.getDirection().getUnitVec3();
            try (Glow.Darkening ignored = Glow.darkening(CorruptionClient.darkness(player))) {
                for (int i = 0, n = Math.round(3 * ScarletFx.density()); i < n; i++) {
                    Vec3 out = normal.scale(0.05 + random.nextDouble() * 0.05).add(randomUnit(random).scale(0.05));
                    ScarletFx.spark(hit.getLocation().add(normal.scale(0.05)), out, 5 + random.nextInt(6), 0.026F + random.nextFloat() * 0.02F,
                            random.nextFloat() < 0.35F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET, 0.004F, 0.86F);
                }
                if (random.nextFloat() < 0.3F * ScarletFx.density()) {
                    ChaosDust.spawn(hit.getLocation().add(normal.scale(0.2)), normal.scale(0.02).add(randomUnit(random).scale(0.02)));
                }
            }
        }
    }

    /**
     * Where a caster's brush is: the surface they look at, as far as they can paint.
     */
    private static @Nullable BlockHitResult brush(Player player, float partialTick) {
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 end = eye.add(player.getViewVector(partialTick).scale(HexPaint.REACH));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || WRITTEN.isEmpty() && PAINTING.isEmpty()) {
            return;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + partialTick;
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        List<Stream> streams = new ArrayList<>();
        for (Player player : level.players()) {
            float presence = PoseBlends.of(player).beam;
            if (!PAINTING.containsKey(player.getId()) || presence < 0.02F || player.isInvisible()) {
                continue;
            }
            BlockHitResult hit = brush(player, partialTick);
            if (hit == null) {
                continue;
            }
            Vec3 palm = Hands.raisedPalm(player, player.getMainArm(), partialTick);
            streams.add(new Stream(palm.subtract(camera).toVector3f(), hit.getLocation().subtract(camera).toVector3f(), presence,
                    player.getId() * 0.618F, CorruptionClient.darkness(player)));
        }
        List<Written> written = List.copyOf(WRITTEN);
        if (!streams.isEmpty()) {
            GlowPass.submitTint(collector, poseStack, (pose, buffer) -> {
                Glow.Billboard axes = Glow.billboard(pose);
                float before = Glow.darkness();
                for (Stream stream : streams) {
                    Glow.darken(stream.darkness());
                    MagicBeam.tint(buffer, pose, axes, stream.from(), stream.to(), stream.presence());
                }
                Glow.darken(before);
            });
        }
        GlowPass.submit(collector, poseStack, (pose, buffer) -> {
            long frame = Glitch.frame(now);
            for (Written block : written) {
                BlockGlitch.draw(buffer, pose, level, block.pos(), camera, (float) (now - block.at()), 0.0F, frame, block.seed());
            }
            if (streams.isEmpty()) {
                return;
            }
            Glow.Billboard axes = Glow.billboard(pose);
            float before = Glow.darkness();
            for (Stream stream : streams) {
                Glow.darken(stream.darkness());
                MagicBeam.draw(buffer, pose, axes, stream.from(), stream.to(), now, stream.seed(), stream.presence());
            }
            Glow.darken(before);
        });
    }

    private static Vec3 randomUnit(RandomSource random) {
        return new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
    }

    private record Written(BlockPos pos, long at, int seed) {
    }

    /**
     * The beam from a caster's hand to their brush, relative to the camera.
     */
    private record Stream(Vector3f from, Vector3f to, float presence, float seed, float darkness) {
    }
}
