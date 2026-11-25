package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.render.Glow;
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.client.render.PixelSprite;
import com.yashjit.scarlet.client.render.Pixels;
import com.yashjit.scarlet.magic.WeaveBlock;
import com.yashjit.scarlet.magic.WiccanSpells;
import com.yashjit.scarlet.network.MagicEventPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** World-locked construction lines on blocks, and protective spells that follow their wearer. */
final class WiccanConstructFx {
    private record Cast(int caster, int kind, Vec3 at, long began, int life, boolean divine) {}
    private static final List<Cast> CASTS = new ArrayList<>();
    private static final Pixels.Ramp BLUE = Pixels.Ramp.WICCAN;
    private static ClientLevel world;
    private static final Vector3f EAST = new Vector3f(1, 0, 0), SOUTH = new Vector3f(0, 0, 1);

    private WiccanConstructFx() {}

    private static void world(ClientLevel level) {
        if (world != level) {
            CASTS.clear();
            world = level;
        }
    }

    static void cast(ClientLevel level, Player caster, int kind, Vec3 at) {
        world(level);
        boolean divine = WiccanSpells.ascended(caster);
        int life = switch (kind) {
            case MagicEventPayload.WARD -> divine ? 240 : 120;
            case MagicEventPayload.FATE -> 120;
            case MagicEventPayload.REWRITTEN -> 36;
            case MagicEventPayload.DEFLECT -> 10;
            default -> 20;
        };
        if (kind == MagicEventPayload.WARD || kind == MagicEventPayload.FATE)
            CASTS.removeIf(c -> c.caster == caster.getId() && c.kind == kind);
        CASTS.add(new Cast(caster.getId(), kind, at, level.getGameTime(), life, divine));
    }

    static void submit(Minecraft mc, SubmitNodeCollector collector, PoseStack stack) {
        world(mc.level);
        if (world == null || CASTS.isEmpty()) return;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = world.getGameTime() + partial;
        CASTS.removeIf(c -> now - c.began > c.life);
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        for (Cast cast : CASTS) {
            float age = (float) (now - cast.began);
            boolean attached = cast.kind == MagicEventPayload.WARD || cast.kind == MagicEventPayload.FATE;
            Vec3 at = cast.at;
            boolean firstPerson = false;
            if (attached) {
                if (!(world.getEntity(cast.caster) instanceof Player p) || !p.isAlive()) continue;
                if (age > 5 && !p.hasEffect(cast.kind == MagicEventPayload.FATE ? MobEffects.LUCK : MobEffects.ABSORPTION)) continue;
                at = p.getPosition(partial).add(0, p.getBbHeight() * 0.5, 0);
                firstPerson = ScarletFx.isFirstPersonViewOf(p);
            }
            if (at.distanceToSqr(camera) > 80 * 80) continue;
            Vector3f center = at.subtract(camera).toVector3f();
            float fade = Math.clamp((cast.life - age) / 6, 0, 1);
            boolean own = firstPerson;
            if (cast.kind == MagicEventPayload.WEAVE) {
                BlockPos origin = BlockPos.containing(cast.at);
                int radius = cast.divine ? 3 : 1;
                List<BlockPos> tiles = new ArrayList<>();
                for (int z = -radius; z <= radius; z++) for (int x = -radius; x <= radius; x++) {
                    BlockPos tile = origin.offset(x, 0, z);
                    if (world.getBlockState(tile).is(WeaveBlock.BLOCK.get())) tiles.add(tile);
                }
                GlowPass.submitPixels(collector, stack, (pose, buffer) -> stitch(pose, buffer, center, origin, tiles, age, fade));
            } else {
                GlowPass.submitPixels(collector, stack, (pose, buffer) -> {
                    PixelSprite sprite = PixelSprite.inWorld(pose, center.x, center.y, center.z);
                    switch (cast.kind) {
                        case MagicEventPayload.WARD -> ward(sprite, center, age, fade, own);
                        case MagicEventPayload.FATE -> fate(sprite, center, age, fade, own);
                        case MagicEventPayload.DEFLECT -> Wisps.burst(sprite, 0.2F + age * 0.09F, 0.10F, fade, (int) age, BLUE);
                        case MagicEventPayload.REWRITTEN -> {
                            float radius = 5.8F * Ease.outCubic(age / cast.life);
                            Wisps.ring(sprite, center, EAST, SOUTH, radius, age * 0.04F, 1, tint(Pixels.BRIGHT, fade), 2, 2);
                            for (int i = 0; i < 8; i++) {
                                float a = i * Mth.TWO_PI / 8;
                                Vector3f root = new Vector3f(center).add(radius * Mth.cos(a), 0, radius * Mth.sin(a));
                                sprite.line(root, new Vector3f(root).add(0, fade * 0.8F, 0), tint(Pixels.PINK, fade), 1, 1);
                            }
                        }
                        default -> transmute(sprite, center, age / cast.life, fade, cast.kind == MagicEventPayload.UNWOVEN);
                    }
                    sprite.draw(buffer);
                });
            }
        }
    }

    private static void stitch(PoseStack.Pose pose, VertexConsumer buffer, Vector3f center, BlockPos origin,
                               List<BlockPos> tiles, float age, float fade) {
        PixelSprite sprite = PixelSprite.inWorld(pose, center.x, center.y + 0.51F, center.z);
        for (BlockPos tile : tiles) {
            int x = tile.getX() - origin.getX(), z = tile.getZ() - origin.getZ();
            float growth = Math.clamp((age - 1.3F * (Math.abs(x) + Math.abs(z))) / 6, 0, 1);
            if (growth <= 0) continue;
            Vector3f c = new Vector3f(center).add(x, 0.52F, z);
            float r = 0.46F * Ease.outCubic(growth);
            square(sprite, c, r, tint(Pixels.BRIGHT, fade));
            Vector3f left = new Vector3f(c).add(-r, 0, 0), right = new Vector3f(c).add(r, 0, 0);
            Vector3f far = new Vector3f(c).add(0, 0, r), near = new Vector3f(c).add(0, 0, -r);
            int thread = tint(Pixels.PINK, fade);
            sprite.line(left, far, thread, 2, 1);
            sprite.line(far, right, thread, 2, 1);
            sprite.line(right, near, thread, 2, 1);
            sprite.line(near, left, thread, 2, 1);
        }
        sprite.draw(buffer);
    }

    private static void transmute(PixelSprite sprite, Vector3f c, float t, float fade, boolean unwind) {
        float extent = unwind ? 0.52F * (1 - Ease.inOutCubic(t)) : 0.514F;
        float y = extent * (2 * Ease.inOutCubic(t) - 1);
        square(sprite, new Vector3f(c).add(0, y, 0), extent, tint(Pixels.HOT, fade));
        square(sprite, new Vector3f(c).add(0, -y, 0), extent, tint(Pixels.BRIGHT, fade));
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
            Vector3f low = new Vector3f(c).add(x * extent, -extent, z * extent);
            sprite.line(low, new Vector3f(low).add(0, extent + y, 0), tint(Pixels.BRIGHT, fade), 1, 1);
        }
    }

    private static void square(PixelSprite sprite, Vector3f c, float r, int color) {
        Vector3f a = new Vector3f(c).add(-r, 0, -r), b = new Vector3f(c).add(r, 0, -r);
        Vector3f d = new Vector3f(c).add(-r, 0, r), e = new Vector3f(c).add(r, 0, r);
        sprite.line(a, b, color, 1, 1);
        sprite.line(b, e, color, 1, 1);
        sprite.line(e, d, color, 1, 1);
        sprite.line(d, a, color, 1, 1);
    }

    private static void ward(PixelSprite sprite, Vector3f c, float age, float fade, boolean own) {
        float grow = Ease.outCubic(Math.clamp(age / 7, 0, 1));
        float turn = age * 0.025F;
        for (int i = 0; i < 8; i++) {
            float angle = turn + i * Mth.PI / 4;
            Vector3f radial = new Vector3f(Mth.cos(angle), 0, Mth.sin(angle));
            Vector3f across = new Vector3f(-radial.z, 0, radial.x);
            Vector3f hub = new Vector3f(c).fma(0.88F * grow, radial);
            if (own) hub.y -= 0.40F;
            float pulse = 0.5F + 0.5F * Mth.cos(age * 0.13F - angle * 2);
            int edge = tint(Pixels.SCARLET, fade * (0.26F + 0.45F * pulse));
            Vector3f top = new Vector3f(hub).add(0, grow * 0.55F, 0);
            Vector3f tip = new Vector3f(hub).add(0, -grow * 0.72F, 0);
            for (int side : new int[]{-1, 1}) {
                Vector3f shoulder = new Vector3f(hub).fma(side * 0.32F * grow, across).add(0, grow * 0.30F, 0);
                Vector3f hip = new Vector3f(shoulder).add(0, -grow * 0.52F, 0);
                sprite.line(top, shoulder, edge, 1, 1);
                sprite.line(shoulder, hip, edge, 1, 1);
                sprite.line(hip, tip, edge, 1, 1);
                if (pulse > 0.85F) sprite.line(shoulder, hub, tint(Pixels.PINK, fade * pulse), 2, 1);
            }
        }
    }

    private static void fate(PixelSprite sprite, Vector3f c, float age, float fade, boolean own) {
        if (own) c = new Vector3f(c).add(0, -0.5F, 0);
        for (int i = 0; i < 3; i++) {
            float a = age * 0.12F + i * Mth.TWO_PI / 3;
            Vector3f last = null;
            for (int k = 0; k <= 12; k++) {
                float phase = a - k * 0.055F;
                Vector3f next = new Vector3f(c).add(0.94F * Mth.cos(phase), 0.38F * Mth.sin(2 * phase), 0.94F * Mth.sin(phase));
                if (last != null) sprite.line(last, next, tint(k < 4 ? Pixels.HOT : Pixels.BRIGHT, fade * (1 - k / 15F)), 1, k < 3 ? 2 : 1);
                last = next;
            }
        }
    }

    private static int tint(int step, float alpha) {
        return Glow.withAlpha(BLUE.color(step), alpha);
    }
}
