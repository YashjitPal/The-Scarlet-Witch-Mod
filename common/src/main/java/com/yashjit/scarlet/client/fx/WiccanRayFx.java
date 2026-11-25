package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.magic.Hands;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.client.render.PixelSprite;
import com.yashjit.scarlet.client.render.Pixels;
import com.yashjit.scarlet.magic.Magic;
import com.yashjit.scarlet.magic.WiccanSpells;
import com.yashjit.scarlet.network.MagicEventPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** A short-lived star streak and a continuous braided current following the caster's palm. */
final class WiccanRayFx {
    private static final Pixels.Ramp BLUE = Pixels.Ramp.WICCAN;
    private static final List<Star> STARS = new ArrayList<>();
    private static final Map<Integer, Beam> BEAMS = new HashMap<>();
    private static ClientLevel world;

    private record Star(Vec3 start, Vec3 end, long born, boolean contact, float scale) {}
    private record Beam(Vec3 oldEnd, Vec3 end, long sample, long began, boolean contact, float scale) {
        Vec3 tip(double now) {
            return oldEnd.lerp(end, Math.clamp((now - sample) / 2.0, 0, 1));
        }
    }

    private WiccanRayFx() {}

    private static void world(ClientLevel level) {
        if (world != level) {
            STARS.clear();
            BEAMS.clear();
            world = level;
        }
    }

    static void receive(ClientLevel level, Player caster, int kind, Vec3 at) {
        world(level);
        long now = level.getGameTime();
        float scale = WiccanSpells.ascended(caster) ? 1.45F : 1.0F;
        if (kind == MagicEventPayload.STAR || kind == MagicEventPayload.STAR_MISS) {
            var arm = Magic.castArm(caster, Magic.state(caster).castCount());
            STARS.add(new Star(Hands.raisedPalm(caster, arm, 1), at, now, kind == MagicEventPayload.STAR, scale));
            return;
        }
        Beam previous = BEAMS.get(caster.getId());
        boolean continuous = previous != null && now - previous.sample <= 7;
        // Large aim changes are new contacts. Blending across a doorway would draw through the wall.
        Vec3 start = continuous && previous.end.distanceToSqr(at) < 9 ? previous.tip(now) : at;
        BEAMS.put(caster.getId(), new Beam(start, at, now, continuous ? previous.began : now, kind == MagicEventPayload.BEAM, scale));
    }

    static void submit(Minecraft mc, SubmitNodeCollector collector, PoseStack stack) {
        world(mc.level);
        if (world == null || STARS.isEmpty() && BEAMS.isEmpty()) return;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = world.getGameTime() + partial;
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        STARS.removeIf(s -> now - s.born > 10);
        for (Star star : STARS) {
            Vector3f start = star.start.subtract(camera).toVector3f();
            Vector3f end = star.end.subtract(camera).toVector3f();
            float age = (float) (now - star.born);
            GlowPass.submitPixels(collector, stack, (pose, buffer) -> star(buffer, pose, start, end, age, star));
        }
        BEAMS.entrySet().removeIf(entry -> now - entry.getValue().sample > 7 || !(world.getEntity(entry.getKey()) instanceof Player));
        BEAMS.forEach((id, beam) -> {
            Player p = (Player) world.getEntity(id);
            if (p == null || p.isInvisible()) return;
            Vector3f start = Hands.raisedPalm(p, p.getMainArm(), partial).subtract(camera).toVector3f();
            Vector3f end = beam.tip(now).subtract(camera).toVector3f();
            float life = (float) (now - beam.began);
            float fade = Mth.clamp((float) (7 - (now - beam.sample)) / 3, 0, 1);
            GlowPass.submitPixels(collector, stack, (pose, buffer) -> beam(buffer, pose, start, end, life, fade, beam));
        });
    }

    private static void star(VertexConsumer buffer, PoseStack.Pose pose, Vector3f from, Vector3f to, float age, Star star) {
        float distance = from.distance(to);
        if (distance < 0.02F || age < 0) return;
        float flight = Math.clamp(distance / 7, 1, 4);
        Vector3f head = new Vector3f(from).lerp(to, Math.min(1, age / flight));
        PixelSprite sprite = PixelSprite.inWorld(pose, head.x, head.y, head.z);
        float tail = Math.clamp((age - 2) / flight, 0, 1);
        if (tail < 1) {
            Vector3f back = new Vector3f(from).lerp(to, tail);
            sprite.line(back, head, BLUE.opaque(Pixels.SCARLET), 1, 3);
            sprite.line(new Vector3f(back).lerp(head, 0.43F), head, BLUE.opaque(Pixels.PINK), 2, 1);
        }
        float after = Math.max(0, age - flight);
        float strength = Math.max(0, 1 - after / (star.contact ? 6 : 2));
        if (strength > 0) {
            float size = strength * star.scale;
            Wisps.orb(sprite, 2.8F * size, size, (int) age, 87, BLUE);
            for (int i = 0; i < 8; i++) {
                float a = i * Mth.PI / 4 + 0.24F;
                float length = size * (i % 2 == 0 ? 0.48F : 0.22F);
                Vector3f tip = new Vector3f(head).fma(length * Mth.cos(a), sprite.right()).fma(length * Mth.sin(a), sprite.up());
                sprite.line(head, tip, BLUE.opaque(Pixels.PINK), 2, 1);
            }
            if (after > 0 && star.contact) {
                Vector3f[] axes = Glow.planeAxes(new Vector3f(from).sub(to).normalize());
                Wisps.ring(sprite, head, axes[0], axes[1], (1 - strength) * star.scale * 0.95F, 0, 1,
                        Glow.withAlpha(BLUE.color(Pixels.BRIGHT), strength), 1, 1);
            }
        }
        sprite.draw(buffer);
    }

    private static void beam(VertexConsumer buffer, PoseStack.Pose pose, Vector3f from, Vector3f end, float age, float fade, Beam beam) {
        float distance = from.distance(end);
        if (distance < 0.03F || fade <= 0) return;
        Vector3f[] axes = Glow.planeAxes(new Vector3f(end).sub(from).normalize());
        Vector3f to = new Vector3f(from).lerp(end, Ease.outCubic(Math.clamp((age + 0.5F) / 2, 0, 1)));
        PixelSprite sprite = Wisps.farther(pose, from, to);
        int steps = Math.clamp((int) (distance * 5), 6, 160);
        for (int strand = 0; strand < 2; strand++) {
            Vector3f previous = from;
            for (int i = 1; i <= steps; i++) {
                float t = (float) i / steps;
                float angle = distance * t * 1.8F - age * 0.85F + strand * Mth.PI;
                float radius = (0.035F + 0.12F * Mth.sin(t * Mth.PI)) * beam.scale;
                Vector3f point = new Vector3f(from).lerp(to, t).fma(Mth.cos(angle) * radius, axes[0]).fma(Mth.sin(angle) * radius, axes[1]);
                int step = Mth.cos(angle + t * 3) > 0.5F ? Pixels.PINK : strand == 0 ? Pixels.BRIGHT : Pixels.SCARLET;
                sprite.line(previous, point, Glow.withAlpha(BLUE.color(step), fade), 1, 1);
                previous = point;
            }
        }
        sprite.line(from, to, Glow.withAlpha(BLUE.color(Pixels.HOT), fade), 2, 1);
        if (beam.contact && age >= 1) {
            float pulse = (age % 5) / 5;
            Wisps.ring(sprite, to, axes[0], axes[1], (0.15F + pulse * 0.38F) * beam.scale, age * 0.1F, 1,
                    Glow.withAlpha(BLUE.color(Pixels.BRIGHT), fade * (1 - pulse)), 1, 1);
        }
        sprite.draw(buffer);
    }
}
