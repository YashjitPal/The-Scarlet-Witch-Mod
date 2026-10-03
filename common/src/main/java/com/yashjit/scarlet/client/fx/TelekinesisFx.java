package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.PoseBlends;
import com.yashjit.scarlet.client.darkhold.CorruptionClient;
import com.yashjit.scarlet.client.magic.Hands;
import com.yashjit.scarlet.client.magic.TelekinesisClient;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.client.render.PixelSprite;
import com.yashjit.scarlet.client.render.Pixels;
import com.yashjit.scarlet.network.MagicEventPayload;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Telekinesis as everyone sees it, as pixel art. Scarlet tendrils curl from both of the caster's palms into what they
 * hold, writhing as it moves, with beads of light racing along them into it. The held thing reddens through, wisps of
 * magic wind round it one way and the other, and it sheds sparks. A block torn out of the ground bursts out in sparks,
 * and anything thrown that slams into the world goes off in a burst of them.
 */
public final class TelekinesisFx {

    private static final int TENDRILS_PER_HAND = 3;

    private static final Int2ObjectMap<Hum> HUMS = new Int2ObjectOpenHashMap<>();

    private TelekinesisFx() {
    }

    public static void onEvent(MagicEventPayload payload) {
        int kind = payload.kind();
        if (kind != MagicEventPayload.TORN_OUT && kind != MagicEventPayload.SLAM) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        try (Glow.Darkening ignored = Glow.darkening(level == null ? 0.0F : CorruptionClient.darkness(level.getEntity(payload.entityId())))) {
            burst(payload, kind);
        }
    }

    private static void burst(MagicEventPayload payload, int kind) {
        RandomSource random = ScarletFx.random();
        Vec3 at = payload.position();
        float density = ScarletFx.density();
        int sparks = kind == MagicEventPayload.SLAM ? 34 : 22;
        for (int i = 0, n = Math.round(sparks * density); i < n; i++) {
            Vec3 out = randomUnit(random);
            ScarletFx.spark(at, out.scale(0.12 + random.nextDouble() * 0.2).add(0.0, 0.05, 0.0), 6 + random.nextInt(8), 0.028F,
                    random.nextFloat() < 0.35F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET, 0.012F, 0.85F);
        }
        for (int i = 0, n = Math.round(10 * density); i < n; i++) {
            ChaosDust.spawn(at.add(randomUnit(random).scale(0.5)), randomUnit(random).scale(0.06));
        }
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            HUMS.values().forEach(Hum::end);
            HUMS.clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        RandomSource random = ScarletFx.random();
        for (Int2IntMap.Entry entry : TelekinesisClient.held().int2IntEntrySet()) {
            Entity target = level.getEntity(entry.getIntValue());
            if (target == null) {
                continue;
            }
            if (!HUMS.containsKey(entry.getIntKey())) {
                Hum hum = new Hum(target);
                HUMS.put(entry.getIntKey(), hum);
                minecraft.getSoundManager().play(hum);
            }
            // sparks shed off it as it is carried
            AABB box = target.getBoundingBox();
            float darkness = CorruptionClient.darkness(level.getEntity(entry.getIntKey()));
            try (Glow.Darkening ignored = Glow.darkening(darkness)) {
                for (int i = 0, n = count(random, 2.2F * ScarletFx.density()); i < n; i++) {
                    Vec3 at = new Vec3(Mth.lerp(random.nextDouble(), box.minX, box.maxX), Mth.lerp(random.nextDouble(), box.minY, box.maxY),
                            Mth.lerp(random.nextDouble(), box.minZ, box.maxZ));
                    ScarletFx.spark(at, randomUnit(random).scale(0.02).add(0.0, 0.015, 0.0), 10 + random.nextInt(8), 0.022F,
                            random.nextFloat() < 0.3F ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET, ScarletPalette.SCARLET, -0.001F, 0.9F);
                }
                if (random.nextFloat() < 0.4F * ScarletFx.density()) {
                    ChaosDust.spawn(box.getCenter().add(randomUnit(random).scale(box.getSize() * 0.6)), randomUnit(random).scale(0.01));
                }
            }
            if (random.nextFloat() < 0.5F * darkness * ScarletFx.density()) {
                ScarletFx.smoke(box.getCenter().add(randomUnit(random).scale(box.getSize() * 0.5)), new Vec3(0.0, 0.01, 0.0), 22 + random.nextInt(12),
                        0.1F, 0.3F + 0.4F * darkness);
            }
        }
        HUMS.int2ObjectEntrySet().removeIf(entry -> {
            if (!TelekinesisClient.held().containsKey(entry.getIntKey())) {
                entry.getValue().end();
                return true;
            }
            return false;
        });
    }

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || TelekinesisClient.held().isEmpty()) {
            return;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + partialTick;
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        List<Draw> draws = new ArrayList<>();
        for (Int2IntMap.Entry entry : TelekinesisClient.held().int2IntEntrySet()) {
            if (!(level.getEntity(entry.getIntKey()) instanceof Player caster) || caster.isInvisible()) {
                continue;
            }
            Entity target = level.getEntity(entry.getIntValue());
            if (target == null) {
                continue;
            }
            float presence = PoseBlends.of(caster).hold;
            if (presence < 0.02F) {
                continue;
            }
            Vec3 center = target.getPosition(partialTick).add(0.0, target.getBbHeight() * 0.5, 0.0);
            float size = (float) Math.max(target.getBbWidth(), target.getBbHeight());
            Vector3f right = Hands.palm(caster, HumanoidArm.RIGHT).subtract(camera).toVector3f();
            Vector3f left = Hands.palm(caster, HumanoidArm.LEFT).subtract(camera).toVector3f();
            draws.add(new Draw(center.subtract(camera).toVector3f(), size, target.getBbWidth(), target.getBbHeight(), right, left, presence,
                    ScarletFx.isFirstPersonViewOf(caster), caster.getId() * 0.618F, CorruptionClient.darkness(caster)));
        }
        if (draws.isEmpty()) {
            return;
        }
        // what is held reddens through, in a blot of chunky pixels
        GlowPass.submitTint(collector, poseStack, (pose, buffer) -> {
            float before = Glow.darkness();
            for (Draw draw : draws) {
                Glow.darken(draw.darkness());
                tint(buffer, pose, draw);
            }
            Glow.darken(before);
        });
        GlowPass.submitPixels(collector, poseStack, (pose, buffer) -> {
            float time = (float) (now % 24000.0);
            float before = Glow.darkness();
            for (Draw draw : draws) {
                Glow.darken(draw.darkness());
                PixelSprite sprite = Wisps.farther(pose, draw.center(), new Vector3f(draw.rightPalm()).add(draw.leftPalm()).mul(0.5F));
                wrap(sprite, draw, time);
                for (int hand = 0; hand < 2; hand++) {
                    Vector3f palm = hand == 0 ? draw.rightPalm() : draw.leftPalm();
                    for (int i = 0; i < TENDRILS_PER_HAND; i++) {
                        tendril(sprite, draw, palm, hand * TENDRILS_PER_HAND + i, time);
                    }
                }
                sprite.draw(buffer);
            }
            Glow.darken(before);
        });
    }

    /**
     * The red that what is held shows through: a blot over it, its edge broken up in a dither.
     */
    private static void tint(VertexConsumer buffer, PoseStack.Pose pose, Draw draw) {
        Vector3f c = draw.center();
        PixelSprite sprite = PixelSprite.inWorld(pose, c.x, c.y, c.z);
        float rx = (draw.width() * 0.5F + 0.25F) / Pixels.SIZE;
        float ry = (draw.height() * 0.5F + 0.25F) / Pixels.SIZE;
        int color = GlowPass.tint(ScarletPalette.GLASS, 0.5F * draw.presence());
        int reachX = (int) Math.ceil(rx);
        int reachY = (int) Math.ceil(ry);
        for (int i = -reachX; i < reachX; i++) {
            for (int j = -reachY; j < reachY; j++) {
                float x = (i + 0.5F) / rx;
                float y = (j + 0.5F) / ry;
                float d = x * x + y * y;
                if (d > 1.0F || d > 0.6F && !Pixels.shows((1.0F - d) / 0.4F, i, j)) {
                    continue;
                }
                sprite.cell(i, j, color, 0);
            }
        }
        sprite.draw(buffer);
    }

    /**
     * Wisps of it wound round what is held, turning about it one way and the other, light running up them.
     */
    private static void wrap(PixelSprite sprite, Draw draw, float time) {
        Vector3f c = draw.center();
        float radius = draw.width() * 0.5F + 0.22F;
        float half = draw.height() * 0.5F + 0.12F;
        Wisps.helix(sprite, c, radius, radius, -half, half, 2, 1.4F, 0.11F, time, draw.presence(), draw.seed(), 0);
        Wisps.helix(sprite, c, radius * 1.12F, radius * 0.9F, -half * 0.8F, half * 0.9F, 1, 1.1F, -0.08F, time, draw.presence() * 0.8F,
                draw.seed() + 1.7F, 5);
    }

    /**
     * One tendril from a palm into the held thing: it leaves the hand thick and hot, curls along the way, and thins as
     * it sinks into its own spot on the target.
     */
    private static void tendril(PixelSprite sprite, Draw draw, Vector3f palm, int index, float time) {
        float seed = draw.seed() + index * 1.913F;
        Vector3f c = draw.center();
        // where on the target it takes hold
        Vector3f grip = new Vector3f(c).add(Mth.sin(seed * 3.1F) * draw.width() * 0.4F, Mth.sin(seed * 1.7F) * draw.height() * 0.35F,
                Mth.cos(seed * 2.3F) * draw.width() * 0.4F);
        float curl = Math.min(0.6F, grip.distance(palm) * 0.08F);
        Wisps.tendril(sprite, palm, grip, 1.0F, curl, seed, time, draw.presence() * (draw.own() ? 0.75F : 1.0F), 0.09F, index + 11);
    }

    private static int count(RandomSource random, float expected) {
        return (int) expected + (random.nextFloat() < expected % 1.0F ? 1 : 0);
    }

    private static Vec3 randomUnit(RandomSource random) {
        return new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
    }

    private record Draw(Vector3f center, float size, float width, float height, Vector3f rightPalm, Vector3f leftPalm, float presence, boolean own,
                        float seed, float darkness) {
    }

    /**
     * The low thrum of the hold, coming from what is held.
     */
    private static final class Hum extends AbstractTickableSoundInstance {

        private final Entity target;
        private boolean ending;

        Hum(Entity target) {
            super(SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.target = target;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001F;
            this.pitch = 0.8F;
            follow();
        }

        void end() {
            ending = true;
        }

        @Override
        public void tick() {
            if (target.isRemoved()) {
                stop();
                return;
            }
            volume = ending ? volume * 0.6F : Math.min(0.45F, volume + 0.08F);
            if (ending && volume < 0.01F) {
                stop();
                return;
            }
            pitch = 0.75F + (float) Math.min(0.5, target.getDeltaMovement().length() * 0.4);
            follow();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        private void follow() {
            x = target.getX();
            y = target.getY() + target.getBbHeight() * 0.5;
            z = target.getZ();
        }
    }
}
