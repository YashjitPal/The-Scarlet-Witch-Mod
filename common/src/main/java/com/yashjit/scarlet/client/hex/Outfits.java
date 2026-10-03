package com.yashjit.scarlet.client.hex;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.client.costume.CostumeView;
import com.yashjit.scarlet.client.fx.Glitch;
import com.yashjit.scarlet.client.fx.SlipFx;
import com.yashjit.scarlet.client.fx.ScarletFx;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.hex.HexSnapshot;
import com.yashjit.scarlet.hex.Hexes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Era clothes for everyone inside a Hex, like Wanda and Vision each episode. Each person always gets the same outfit
 * for an era, picked from its wardrobe by their UUID; the caster's own costume takes priority.
 *
 * <p>Stepping in or out, the change glitches red: the two sets of clothes flicker back and forth, more and more often
 * the new one, while red bars tear across them and red static crawls over them. In an unsteady Hex an outfit jumps
 * into another era's now and then, or slips off for a moment. And while the home a caster's fallen Hex left behind
 * steps back through the eras, their clothes turn with it.
 */
public final class Outfits {

    /** Outfits in each era's wardrobe. */
    public static final int WARDROBE = 4;
    private static final float CHANGE_TICKS = 20.0F;
    /** How far round their home each era's sweep comes before a caster's clothes turn with it. */
    private static final float TURN_AT = 0.5F;

    private static final Map<UUID, Crossing> CROSSINGS = new HashMap<>();
    private static @Nullable ClientLevel seenLevel;

    private Outfits() {
    }

    /**
     * The outfit a player is wearing at this moment, or null for their own clothes.
     */
    public static @Nullable Identifier outfit(Player player, boolean slim, float partialTick) {
        if (player.isSpectator() || CostumeView.of(player, partialTick) != null || seenLevel == null) {
            return null;
        }
        double now = seenLevel.getGameTime() + partialTick;
        Remnants.Phase phase = Remnants.wornPhase(player, now);
        if (phase != null) {
            return turning(player, slim, now, phase);
        }
        Crossing crossing = CROSSINGS.get(player.getUUID());
        if (crossing == null) {
            return null;
        }
        boolean wearing = crossing.era != null;
        float change = change(crossing, now);
        Glitch.Slip slip = Glitch.Slip.NONE;
        if (change >= 0.0F) {
            // mid-change, flicking between both
            wearing = Glitch.showsNew(Glitch.frame(now), player.getId(), change) == wearing;
        } else if (wearing) {
            slip = slip(player, now);
            wearing = slip != Glitch.Slip.GONE;
        }
        Era era = crossing.era != null ? crossing.era : crossing.previous;
        if (!wearing || era == null) {
            return null;
        }
        if (slip == Glitch.Slip.ERA) {
            era = Glitch.slipEra(now, player.getId(), era);
        }
        return texture(player, era, slim);
    }

    /**
     * Clothes turning through the eras with the home a fallen Hex left by them, each era's as its sweep comes halfway
     * round the house, flickering between the two for a moment as they do; their own until the first.
     */
    private static @Nullable Identifier turning(Player player, boolean slim, double now, Remnants.Phase phase) {
        float past = phase.sweep() - TURN_AT;
        Era era = past >= 0.0F ? phase.after() : phase.before();
        if (Math.abs(past) < 0.08F && Glitch.hash(Glitch.frame(now), player.getId(), 41) < 0.5F) {
            era = past >= 0.0F ? phase.before() : phase.after();
        }
        return era == null ? null : texture(player, era, slim);
    }

    /**
     * Whether someone's clothes are turning to a new era with the home a fallen Hex left by them, between this tick and
     * the last.
     */
    private static boolean turnedWith(Player player, long now) {
        Remnants.Phase phase = Remnants.wornPhase(player, now);
        Remnants.Phase before = Remnants.wornPhase(player, now - 1);
        return phase != null && before != null && phase.sweep() >= TURN_AT && (before.sweep() < TURN_AT || before.index() != phase.index());
    }

    private static Identifier texture(Player player, Era era, boolean slim) {
        int pick = Math.floorMod(player.getUUID().hashCode(), WARDROBE);
        return Scarlet.id("textures/entity/outfit/" + ResidentsClient.name(era) + "_" + pick + (slim ? "_slim" : "") + ".png");
    }

    /**
     * Whether a player's armor is hidden under an outfit.
     */
    public static boolean wearing(Player player, float partialTick) {
        return outfit(player, false, partialTick) != null;
    }

    private static float change(Crossing crossing, double now) {
        double age = now - crossing.changedAt;
        return age < 0.0 || age > CHANGE_TICKS ? -1.0F : (float) (age / CHANGE_TICKS);
    }

    /**
     * How someone's outfit is slipping right now in an unsteady Hex: into another era's, or off altogether.
     */
    private static Glitch.Slip slip(Player player, double now) {
        HexSnapshot hex = Hexes.clientHexAt(player.position(), now);
        return Glitch.slip(now, player.getId() * 7 + 1, hex == null ? 0.0F : hex.unrest(now));
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level != seenLevel) {
            seenLevel = level;
            CROSSINGS.clear();
        }
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        for (Player player : level.players()) {
            HexSnapshot hex = Hexes.clientHexAt(player.position(), now);
            // clothes change as a new era's front reaches whoever wears them
            Era era = hex != null ? Hexes.eraAt(hex, player.position(), now) : null;
            Crossing crossing = CROSSINGS.get(player.getUUID());
            if (crossing == null) {
                CROSSINGS.put(player.getUUID(), new Crossing(era, null, Long.MIN_VALUE / 4));
                continue;
            }
            float worn = Remnants.worn(player, now);
            if (worn > 0.0F && !minecraft.isPaused() && !ScarletFx.isFirstPersonViewOf(player)) {
                // turning through the eras with the home left by them
                AABB box = player.getBoundingBox();
                Glitch.crawl(new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.maxY, box.maxZ), 0.3F + 0.4F * worn);
                if (turnedWith(player, now)) {
                    SlipFx.body(player, now);
                }
            }
            if (crossing.era != era) {
                CROSSINGS.put(player.getUUID(), new Crossing(era, crossing.era, now));
            } else if (!minecraft.isPaused() && !ScarletFx.isFirstPersonViewOf(player)) {
                AABB box = player.getBoundingBox();
                if (change(crossing, now) >= 0.0F) {
                    Glitch.crawl(new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.maxY, box.maxZ), 0.8F);
                } else if (era != null && slip(player, now) != slip(player, now - 1.0)) {
                    if (slip(player, now) != Glitch.Slip.NONE) {
                        SlipFx.body(player, (long) now);
                    } else {
                        Glitch.puff(new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.maxY, box.maxZ));
                    }
                }
            }
        }
        CROSSINGS.keySet().removeIf(id -> level.getPlayerByUUID(id) == null);
    }

    /**
     * The red glitch over everyone changing clothes at the wall.
     */
    public static void submit(SubmitNodeCollector collector, PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || CROSSINGS.isEmpty()) {
            return;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + partialTick;
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        List<Draw> draws = new ArrayList<>();
        for (Player player : level.players()) {
            Crossing crossing = CROSSINGS.get(player.getUUID());
            if (crossing == null || player.isInvisible() || ScarletFx.isFirstPersonViewOf(player)) {
                continue;
            }
            float change = change(crossing, now);
            float intensity = change >= 0.0F ? (float) Math.sin(Math.PI * Math.min(1.0F, change * 1.15F)) : 0.0F;
            // turning with the home left by them, the red over them faint, and flaring as their clothes turn
            Remnants.Phase phase = Remnants.wornPhase(player, now);
            if (phase != null) {
                float turn = (phase.sweep() - TURN_AT) / 0.07F;
                intensity = Math.max(intensity, Remnants.worn(player, now) * (0.15F + 0.6F * (float) Math.exp(-turn * turn)));
            }
            if (intensity > 0.02F) {
                Vec3 center = player.getPosition(partialTick).add(0.0, player.getBbHeight() * 0.5, 0.0).subtract(camera);
                draws.add(new Draw(center.toVector3f(), player.getBbWidth() * 1.3F, player.getBbHeight() * 1.05F, intensity, player.getId()));
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

    private record Draw(Vector3f center, float width, float height, float intensity, int seed) {
    }

    /**
     * @param era      the era of the Hex they're in, or null outside
     * @param previous the era they were in before, for the change on the way out
     */
    private record Crossing(@Nullable Era era, @Nullable Era previous, long changedAt) {
    }
}
