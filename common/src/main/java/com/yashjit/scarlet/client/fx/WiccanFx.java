package com.yashjit.scarlet.client.fx;

import com.yashjit.scarlet.client.magic.Hands;
import com.yashjit.scarlet.client.render.Pixels;
import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.network.MagicEventPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** World-aligned glyphs: an unbroken circle, five intersecting chords and ticks marking the points. */
public final class WiccanFx {
    private record Seal(Vec3 center, double radius, long until) {}
    private static final java.util.Map<String, Seal> SEALS = new java.util.HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel seen;
    private WiccanFx() {}

    public static boolean receive(MagicEventPayload e) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(e.entityId()) instanceof Player p)) return false;
        int kind = e.kind();
        if (kind < MagicEventPayload.STAR && !CrownItem.isWiccan(p)) return false;
        Vec3 at = e.position();
        switch (kind) {
            case MagicEventPayload.STAR, MagicEventPayload.STAR_MISS, MagicEventPayload.BEAM, MagicEventPayload.BEAM_MISS ->
                    WiccanRayFx.receive(mc.level, p, kind, at);
            case MagicEventPayload.PENTACLE, MagicEventPayload.DOMAIN -> {
                double r = kind == MagicEventPayload.DOMAIN ? 7 : 2.1;
                Vec3 center = at.add(0, 0.09, 0);
                if (seen != mc.level) { SEALS.clear(); seen = mc.level; }
                SEALS.put(e.entityId() + ":" + kind, new Seal(center, r, mc.level.getGameTime() + 18));
                for (int i = 0; i < 5; i++) {
                    double a = i * Math.PI * 2 / 5 - Math.PI / 2;
                    line(point(center, r * 0.94, a + 0.09), point(center, r * 1.05, a + 0.09), 14);
                }
            }
            case MagicEventPayload.HALO -> {
                for (var arm : HumanoidArm.values()) ring(Hands.palm(p, arm), 0.27, 0.25, 10);
                if (!ScarletFx.isFirstPersonViewOf(p)) {
                    Vec3 look = p.getLookAngle(), brow = p.getEyePosition().add(look.scale(0.275));
                    Vec3 side = look.cross(new Vec3(0,1,0)).normalize().scale(0.11);
                    spark(brow.add(side), Vec3.ZERO, 4, Pixels.PINK);
                    spark(brow.subtract(side), Vec3.ZERO, 4, Pixels.PINK);
                }
            }
            case MagicEventPayload.GATE -> {
                for (int i = 0; i < 44; i++) {
                    double a = i * Math.PI / 22;
                    spark(at.add(Math.cos(a)*0.8, 1.1 + Math.sin(a), 0), new Vec3(0,0.012,0), 16, i % 4 == 0 ? Pixels.HOT : Pixels.BRIGHT);
                }
            }
            case MagicEventPayload.MIRROR -> {
                for (int i = -1; i <= 1; i++) {
                    Vec3 echo = at.add(i * 0.85, 0, 0);
                    ring(echo.add(0,1.4,0), 0.28, 0.1, 20);
                    line(echo, echo.add(0,1.7,0), 24);
                }
            }
            case MagicEventPayload.SENSE -> {
                Vec3 start = p.getEyePosition().add(p.getLookAngle().scale(0.6));
                line(start, start.add(at.subtract(start).normalize().scale(Math.min(3, at.distanceTo(start)))), 16);
                burst(at, 6, 0.018);
            }
            case MagicEventPayload.WARD, MagicEventPayload.WEAVE, MagicEventPayload.TRANSMUTED, MagicEventPayload.UNWOVEN,
                 MagicEventPayload.REWRITTEN, MagicEventPayload.FATE, MagicEventPayload.DEFLECT -> WiccanConstructFx.cast(mc.level, p, kind, at);
            default -> { return false; }
        }
        return true;
    }

    public static void submit(net.minecraft.client.renderer.SubmitNodeCollector collector, com.mojang.blaze3d.vertex.PoseStack pose) {
        var mc = Minecraft.getInstance();
        WiccanRayFx.submit(mc, collector, pose);
        WiccanConstructFx.submit(mc, collector, pose);
        if (mc.level == null || mc.level != seen) { SEALS.clear(); seen = mc.level; return; }
        long now = mc.level.getGameTime();
        SEALS.values().removeIf(s -> s.until <= now);
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        for (Seal seal : SEALS.values()) {
            Vec3 at = seal.center.subtract(camera);
            if (at.lengthSqr() > 96 * 96) continue;
            float alpha = Math.min(1, (seal.until - now) / 6F);
            com.yashjit.scarlet.client.render.GlowPass.submitPixels(collector, pose, (matrix, buffer) -> {
                for (int pass = 0; pass < 2; pass++) {
                    int color = com.yashjit.scarlet.client.render.Glow.withAlpha(pass == 0 ? 0x2488F2 : 0xBDF8FF, alpha * (pass == 0 ? 0.48F : 0.95F));
                    double width = (pass == 0 ? 0.038 : 0.012) * Math.sqrt(seal.radius);
                    Vec3 origin = at.add(0,0.002*pass,0);
                    for (int i = 0; i < 72; i++) stripe(buffer, matrix, point(origin,seal.radius,i*Math.PI/36), point(origin,seal.radius,(i+1)*Math.PI/36),width,color);
                    for (int i = 0; i < 5; i++) {
                        double a = i*Math.PI*2/5-Math.PI/2;
                        stripe(buffer,matrix,point(origin,seal.radius,a), point(origin,seal.radius,a+Math.PI*4/5),width,color);
                    }
                }
            });
        }
    }

    private static void stripe(com.mojang.blaze3d.vertex.VertexConsumer b, com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                               Vec3 from, Vec3 to, double half, int color) {
        Vec3 dir = to.subtract(from).normalize();
        float dx=(float)(-dir.z*half), dz=(float)(dir.x*half);
        b.addVertex(pose,(float)from.x-dx,(float)from.y,(float)from.z-dz).setColor(color);
        b.addVertex(pose,(float)to.x-dx,(float)to.y,(float)to.z-dz).setColor(color);
        b.addVertex(pose,(float)to.x+dx,(float)to.y,(float)to.z+dz).setColor(color);
        b.addVertex(pose,(float)from.x+dx,(float)from.y,(float)from.z+dz).setColor(color);
    }

    private static Vec3 point(Vec3 at, double r, double a) { return at.add(r*Math.cos(a),0,r*Math.sin(a)); }

    private static void ring(Vec3 at, double r, double rise, int life) {
        int n = Math.clamp((int)(r * 32 * ScarletFx.density()), 12, 160);
        for (int i = 0; i < n; i++) {
            double a = 2*Math.PI*i/n;
            spark(point(at, r, a).add(0,rise*Math.sin(a),0), Vec3.ZERO, life, i%5 == 0 ? Pixels.PINK : Pixels.BRIGHT);
        }
    }

    private static void line(Vec3 a, Vec3 b, int life) {
        int n = Math.clamp((int)(a.distanceTo(b)*9*ScarletFx.density()), 2, 100);
        for (int i = 0; i <= n; i++) spark(a.lerp(b, (double)i/n), Vec3.ZERO, life, i%5 == 0 ? Pixels.HOT : Pixels.BRIGHT);
    }

    private static void burst(Vec3 at, int count, double speed) {
        var random = ScarletFx.random();
        for (int i=0; i<count*ScarletFx.density(); i++) spark(at, new Vec3(random.nextGaussian(),random.nextGaussian(),random.nextGaussian()).scale(speed),
                10+random.nextInt(8), i%3 == 0 ? Pixels.PINK : Pixels.BRIGHT);
    }

    private static void spark(Vec3 p, Vec3 v, int life, int step) {
        ScarletFx.spark(p, v, life, 0.026F, Pixels.Ramp.WICCAN, step, 0, 0.99F);
    }
}
