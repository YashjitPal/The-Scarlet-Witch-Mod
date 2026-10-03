package com.yashjit.scarlet.client.hex;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.client.fx.Glitch;
import com.yashjit.scarlet.client.fx.SlipFx;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.hex.HexSnapshot;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.network.GesturePayload;
import com.yashjit.scarlet.network.ResidentsPayload;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.ClientAsset;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Which mobs this client draws as a Hex's townspeople, and how each one looks: dressed for the era of the Hex it
 * lives in, always the same person for the same mob.
 *
 * <p>When one is rewritten, as the wall sweeps over it or it walks in, it glitches red into its new self: it flickers
 * between the two, more and more often the new one, while red bars tear across it and red static crawls over it.
 * Turning back is the same, the other way. While its Hex is unsteady, parted or its caster hurt, residents slip now and
 * then: into another era's clothes, or out of their disguise altogether, for a moment.
 */
public final class ResidentsClient {

    /** Townspeople per era; the last half have slim arms. */
    public static final int PEOPLE = 6;
    private static final float CHANGE_TICKS = 26.0F;

    private static IntOpenHashSet residents = new IntOpenHashSet();
    /** When each mob began changing between its two selves. */
    private static final Int2DoubleOpenHashMap CHANGED = new Int2DoubleOpenHashMap();
    /** The gesture each townsperson is making, and when they began it. */
    private static final Int2ObjectOpenHashMap<Gesturing> GESTURES = new Int2ObjectOpenHashMap<>();
    private static final Map<Integer, PlayerSkin> SKINS = new HashMap<>();
    private static @Nullable ClientLevel seenLevel;

    private ResidentsClient() {
    }

    public static void receive(ResidentsPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        forgetIfLeft(minecraft.level);
        ClientLevel level = minecraft.level;
        double now = level != null ? level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false) : 0.0;
        IntOpenHashSet next = new IntOpenHashSet(payload.ids());
        IntOpenHashSet changed = new IntOpenHashSet();
        for (int id : residents) {
            if (!next.contains(id)) {
                changed.add(id);
            }
        }
        for (int id : next) {
            if (!residents.contains(id)) {
                changed.add(id);
            }
        }
        residents = next;
        for (int id : changed) {
            CHANGED.put(id, now);
            if (level != null && level.getEntity(id) instanceof Entity entity) {
                // the crackle of a picture losing its signal
                level.playLocalSound(entity.getX(), entity.getY() + 1.0, entity.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.25F,
                        1.9F, false);
            }
        }
    }

    public static void gesture(GesturePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        forgetIfLeft(minecraft.level);
        if (minecraft.level != null) {
            GESTURES.put(payload.entityId(), new Gesturing(payload.kind(), minecraft.level.getGameTime()));
        }
    }

    /**
     * The gesture a townsperson is making, and how many ticks into it they are; or null.
     */
    public static @Nullable Gesturing gesture(Entity entity) {
        Gesturing gesturing = GESTURES.get(entity.getId());
        return gesturing != null && seenLevel != null && seenLevel.getGameTime() - gesturing.start() < gesturing.kind().ticks ? gesturing : null;
    }

    public static void tick(Minecraft minecraft) {
        forgetIfLeft(minecraft.level);
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused()) {
            return;
        }
        double now = level.getGameTime();
        CHANGED.int2DoubleEntrySet().removeIf(entry -> now - entry.getDoubleValue() > CHANGE_TICKS);
        GESTURES.values().removeIf(gesturing -> now - gesturing.start() > gesturing.kind().ticks);
        for (int id : CHANGED.keySet()) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                AABB box = entity.getBoundingBox();
                Glitch.crawl(new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.maxY, box.maxZ), 1.0F);
            }
        }
        if (Hexes.clientHexes().isEmpty()) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        for (int id : residents) {
            Entity entity = level.getEntity(id);
            if (entity == null || entity.distanceToSqr(camera) > 48 * 48) {
                continue;
            }
            Glitch.Slip slip = slip(entity, now);
            if (slip != slip(entity, now - 1.0)) {
                if (slip != Glitch.Slip.NONE) {
                    // the red magic that made them takes hold of them as they slip
                    SlipFx.body(entity, (long) now);
                } else {
                    AABB box = entity.getBoundingBox();
                    Glitch.puff(new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.maxY, box.maxZ));
                }
            }
        }
    }

    private static void forgetIfLeft(@Nullable ClientLevel level) {
        if (level != seenLevel) {
            seenLevel = level;
            residents = new IntOpenHashSet();
            CHANGED.clear();
            GESTURES.clear();
        }
    }

    /**
     * How far a mob is through changing between its two selves: 0 to 1, or -1 when it is not changing.
     */
    private static float change(Entity entity, double now) {
        double since = CHANGED.getOrDefault(entity.getId(), Double.NaN);
        if (Double.isNaN(since) || now - since < 0.0 || now - since > CHANGE_TICKS) {
            return -1.0F;
        }
        return (float) ((now - since) / CHANGE_TICKS);
    }

    /**
     * How a resident is slipping right now, while its Hex is unsteady: into another era's clothes, or out of its
     * disguise altogether.
     */
    private static Glitch.Slip slip(Entity entity, double now) {
        HexSnapshot hex = Hexes.clientHexAt(entity.position(), now);
        return Glitch.slip(now, entity.getId(), hex == null ? 0.0F : HexClient.unrest(hex, now));
    }

    /**
     * Whether to draw a mob as a townsperson right now. Mid-change it flicks between its two selves, settling on the
     * new one; in an unsteady Hex it flickers out of its disguise now and then.
     */
    public static boolean drawsAsResident(Entity entity) {
        boolean resident = residents.contains(entity.getId());
        if (seenLevel == null) {
            return resident;
        }
        double now = seenLevel.getGameTime() + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float change = change(entity, now);
        if (change >= 0.0F) {
            return Glitch.showsNew(Glitch.frame(now), entity.getId(), change) == resident;
        }
        return resident && slip(entity, now) != Glitch.Slip.GONE;
    }

    /**
     * The red glitch over everyone being rewritten.
     */
    public static void submit(SubmitNodeCollector collector, PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        double now = level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        List<Draw> draws = new ArrayList<>();
        for (int id : CHANGED.keySet()) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                float change = change(entity, now);
                if (change >= 0.0F) {
                    // strongest in the thick of it, easing in and out
                    float intensity = (float) Math.sin(Math.PI * Math.min(1.0F, change * 1.15F));
                    draws.add(draw(entity, partialTick, camera, intensity));
                }
            }
        }
        if (draws.isEmpty()) {
            return;
        }
        long frame = Glitch.frame(now);
        GlowPass.submit(collector, poseStack, (pose, buffer) -> {
            Glow.Billboard axes = Glow.billboard(pose);
            for (Draw draw : draws) {
                Glitch.draw(buffer, pose, axes, draw.center(), draw.width(), draw.height(), draw.intensity(), frame, draw.seed());
            }
        });
    }

    private static Draw draw(Entity entity, float partialTick, Vec3 camera, float intensity) {
        Vec3 center = entity.getPosition(partialTick).add(0.0, entity.getBbHeight() * 0.5, 0.0).subtract(camera);
        return new Draw(center.toVector3f(), entity.getBbWidth() * 1.3F, entity.getBbHeight() * 1.05F, intensity, entity.getId());
    }

    /**
     * Who a resident is: dressed for its Hex's era, and always the same person for the same mob. In an unsteady Hex
     * their clothes jump into other eras now and then.
     */
    public static PlayerSkin skin(Entity entity) {
        Era era = shownEra(entity);
        int person = Math.floorMod(entity.getUUID().hashCode(), PEOPLE);
        return SKINS.computeIfAbsent(era.ordinal() * PEOPLE + person, key -> PlayerSkin.insecure(
                new ClientAsset.ResourceTexture(Scarlet.id("entity/resident/" + name(era) + "_" + person)), null, null,
                person >= PEOPLE / 2 ? PlayerModelType.SLIM : PlayerModelType.WIDE));
    }

    /**
     * The era a resident is dressed for right now: its Hex's, or another for a moment while it slips.
     */
    private static Era shownEra(Entity entity) {
        Era era = eraAt(entity.position());
        if (seenLevel == null) {
            return era;
        }
        double now = seenLevel.getGameTime() + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return slip(entity, now) == Glitch.Slip.ERA ? Glitch.slipEra(now, entity.getId(), era) : era;
    }

    public static Era eraAt(Vec3 point) {
        double now = seenLevel != null ? seenLevel.getGameTime() : 0.0;
        HexSnapshot hex = Hexes.clientHexAt(point, now);
        return hex != null ? Hexes.eraAt(hex, point, now) : Era.FIFTIES;
    }

    /** The era's name in texture paths. */
    public static String name(Era era) {
        return switch (era) {
            case FIFTIES -> "fifties";
            case SIXTIES -> "sixties";
            case SEVENTIES -> "seventies";
            case EIGHTIES -> "eighties";
            case TWO_THOUSANDS -> "two_thousands";
            case PRESENT -> "present";
        };
    }

    private record Draw(Vector3f center, float width, float height, float intensity, int seed) {
    }

    public record Gesturing(GesturePayload.Kind kind, long start) {
    }
}
