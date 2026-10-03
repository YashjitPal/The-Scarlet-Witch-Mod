package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.render.Glow;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A block of a caster's home coming into being as the Hex writes it. While the magic reaches for it, an outline of
 * scarlet light flickers where it will stand, a few red pixels already crawling over where its faces will be. Then it
 * snaps in: its edges flare white-hot a little larger than it and close in on it, a scanline runs down its sides, red
 * pixels swarm over its faces and thin out, and the faces glow hot for a moment before they settle. All of it on a grid
 * of an eighth of a block, as blocky as Minecraft's own textures.
 */
public final class BlockGlitch {

    /** Ticks a block takes to settle once it lands. */
    public static final float SETTLE = 16.0F;
    /** Ticks what is left of a block that has gone takes to fade. */
    public static final float GONE = 7.0F;
    private static final float FLASH = 5.0F;
    private static final float SCAN = 8.0F;
    /** Pixels across a face. */
    private static final int GRID = 8;
    /** How far off a face its glitch is drawn, so the face never hides it. */
    private static final float LIFT = 0.006F;
    private static final AABB FULL = new AABB(0, 0, 0, 1, 1, 1);

    private BlockGlitch() {
    }

    /**
     * @param since ticks since the block landed, below 0 while the magic is still on its way to it
     * @param lead  ticks from when it was first told of to when it lands
     */
    public static void draw(VertexConsumer buffer, PoseStack.Pose pose, ClientLevel level, BlockPos pos, Vec3 camera, float since, float lead,
                            long frame, int seed) {
        if (since > SETTLE) {
            return;
        }
        float x = (float) (pos.getX() - camera.x);
        float y = (float) (pos.getY() - camera.y);
        float z = (float) (pos.getZ() - camera.z);
        if (since < 0.0F) {
            // on its way: where it will stand, flickering brighter as it nears
            float near = 1.0F - Ease.clamp01(-since / Math.max(1.0F, lead));
            float flicker = Glitch.hash(frame, seed, 1) < 0.3F ? 0.3F : 1.0F;
            float grow = 0.04F;
            edges(buffer, pose, x - grow, y - grow, z - grow, x + 1 + grow, y + 1 + grow, z + 1 + grow, 0.045F,
                    Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, (0.12F + 0.5F * near) * flicker));
            for (Direction face : Direction.values()) {
                if (facesCamera(face, x, y, z, x + 1, y + 1, z + 1)) {
                    pixels(buffer, pose, face, x, y, z, x + 1, y + 1, z + 1, Math.round(1 + 3 * near), 0.7F * near, frame, seed + face.ordinal() * 7);
                }
            }
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        VoxelShape shape = state.getShape(level, pos);
        AABB box = shape.isEmpty() ? FULL : shape.bounds();
        float x0 = x + (float) box.minX;
        float y0 = y + (float) box.minY;
        float z0 = z + (float) box.minZ;
        float x1 = x + (float) box.maxX;
        float y1 = y + (float) box.maxY;
        float z1 = z + (float) box.maxZ;
        float k = since / SETTLE;
        // the edges flare as it snaps in, a little larger than it, closing in on it
        float flash = 1.0F - Ease.outQuad(since / FLASH);
        if (flash > 0.0F) {
            float grow = 0.12F * flash * flash;
            edges(buffer, pose, x0 - grow, y0 - grow, z0 - grow, x1 + grow, y1 + grow, z1 + grow, 0.22F,
                    Glow.withAlpha(ScarletPalette.SCARLET, 0.45F * flash));
            edges(buffer, pose, x0 - grow, y0 - grow, z0 - grow, x1 + grow, y1 + grow, z1 + grow, 0.06F,
                    Glow.withAlpha(ScarletPalette.CORE, 0.95F * flash));
        }
        float scan = Ease.clamp01(since / SCAN);
        float heat = (1.0F - k) * (1.0F - k);
        for (Direction face : Direction.values()) {
            if (!facesCamera(face, x0, y0, z0, x1, y1, z1) || isCovered(level, pos, face)) {
                continue;
            }
            // the face glowing hot and cooling
            rect(buffer, pose, face, x0, y0, z0, x1, y1, z1, 0.0F, 0.0F, 1.0F, 1.0F, Glow.withAlpha(ScarletPalette.SCARLET, 0.2F * heat));
            // the scanline running down its sides as the Hex writes it, with a fading trail above
            if (face.getAxis().isHorizontal() && scan < 1.0F) {
                float line = 1.0F - scan;
                float fade = 1.0F - scan * 0.5F;
                rect(buffer, pose, face, x0, y0, z0, x1, y1, z1, 0.0F, Math.max(0.0F, line - 0.04F), 1.0F, Math.min(1.0F, line + 0.04F),
                        Glow.withAlpha(ScarletPalette.CORE, 0.85F * fade));
                rect(buffer, pose, face, x0, y0, z0, x1, y1, z1, 0.0F, line, 1.0F, Math.min(1.0F, line + 0.3F),
                        Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.22F * fade));
            }
            // the red pixels swarming over it and thinning out
            pixels(buffer, pose, face, x0, y0, z0, x1, y1, z1, Math.round(2 + 14 * heat), 0.95F * (1.0F - k * 0.6F), frame, seed + face.ordinal() * 7);
        }
    }

    /**
     * A block of a home its Hex left behind going, the way it came but backward: red pixels swarm over its faces,
     * thicker as its moment nears, its faces heat up and a scanline climbs its sides, as the Hex unwrites it. Then it is
     * gone, and its edges fly apart white-hot where it stood and fade.
     *
     * @param since ticks since it went, below 0 while it is still going
     * @param lead  ticks from when it was first told of to when it goes
     * @param box   its shape, taken while it still stood
     */
    public static void leave(VertexConsumer buffer, PoseStack.Pose pose, ClientLevel level, BlockPos pos, Vec3 camera, float since, float lead,
                             AABB box, long frame, int seed) {
        if (since > GONE) {
            return;
        }
        float x = (float) (pos.getX() - camera.x);
        float y = (float) (pos.getY() - camera.y);
        float z = (float) (pos.getZ() - camera.z);
        float x0 = x + (float) box.minX;
        float y0 = y + (float) box.minY;
        float z0 = z + (float) box.minZ;
        float x1 = x + (float) box.maxX;
        float y1 = y + (float) box.maxY;
        float z1 = z + (float) box.maxZ;
        if (since < 0.0F) {
            float near = 1.0F - Ease.clamp01(-since / Math.max(1.0F, lead));
            float flicker = Glitch.hash(frame, seed, 2) < 0.25F ? 0.35F : 1.0F;
            edges(buffer, pose, x0, y0, z0, x1, y1, z1, 0.05F, Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, (0.15F + 0.6F * near) * flicker));
            for (Direction face : Direction.values()) {
                if (!facesCamera(face, x0, y0, z0, x1, y1, z1) || isCovered(level, pos, face)) {
                    continue;
                }
                rect(buffer, pose, face, x0, y0, z0, x1, y1, z1, 0.0F, 0.0F, 1.0F, 1.0F, Glow.withAlpha(ScarletPalette.SCARLET, 0.28F * near * near));
                if (face.getAxis().isHorizontal()) {
                    // climbing up its sides as the Hex unwrites it, the reverse of the line that wrote it
                    float line = near;
                    rect(buffer, pose, face, x0, y0, z0, x1, y1, z1, 0.0F, Math.max(0.0F, line - 0.04F), 1.0F, Math.min(1.0F, line + 0.04F),
                            Glow.withAlpha(ScarletPalette.CORE, 0.85F * near));
                    rect(buffer, pose, face, x0, y0, z0, x1, y1, z1, 0.0F, Math.max(0.0F, line - 0.3F), 1.0F, line,
                            Glow.withAlpha(ScarletPalette.BRIGHT_SCARLET, 0.22F * near));
                }
                pixels(buffer, pose, face, x0, y0, z0, x1, y1, z1, Math.round(2 + 16 * near), 0.95F, frame, seed + face.ordinal() * 7);
            }
            return;
        }
        // gone: its edges flying apart white-hot where it stood, and a few last red pixels
        float k = since / GONE;
        float grow = 0.3F * Ease.outQuad(k);
        float fade = 1.0F - k;
        edges(buffer, pose, x0 - grow, y0 - grow, z0 - grow, x1 + grow, y1 + grow, z1 + grow, 0.2F, Glow.withAlpha(ScarletPalette.SCARLET, 0.5F * fade));
        edges(buffer, pose, x0 - grow, y0 - grow, z0 - grow, x1 + grow, y1 + grow, z1 + grow, 0.06F,
                Glow.withAlpha(ScarletPalette.CORE, 0.9F * fade * fade));
        for (Direction face : Direction.values()) {
            if (facesCamera(face, x0, y0, z0, x1, y1, z1)) {
                pixels(buffer, pose, face, x0, y0, z0, x1, y1, z1, Math.round(8 * fade), 0.9F * fade, frame, seed + face.ordinal() * 7);
            }
        }
    }

    /**
     * Red pixels at chance places on a face, different every frame of the glitch.
     */
    private static void pixels(VertexConsumer buffer, PoseStack.Pose pose, Direction face, float x0, float y0, float z0, float x1, float y1, float z1,
                               int count, float alpha, long frame, int seed) {
        float cell = 1.0F / GRID;
        for (int i = 0; i < count; i++) {
            float u = (int) (Glitch.hash(frame, seed, 500 + i) * GRID) * cell;
            float v = (int) (Glitch.hash(frame, seed, 600 + i) * GRID) * cell;
            // now and then two pixels side by side, a torn bit of a line
            float wide = Glitch.hash(frame, seed, 700 + i) > 0.75F ? 2.0F : 1.0F;
            float pick = Glitch.hash(frame, seed, 800 + i);
            int color = pick > 0.75F ? ScarletPalette.CORE : pick > 0.3F ? ScarletPalette.BRIGHT_SCARLET : ScarletPalette.SCARLET;
            rect(buffer, pose, face, x0, y0, z0, x1, y1, z1, u, v, Math.min(1.0F, u + cell * wide), v + cell, Glow.withAlpha(color, alpha));
        }
    }

    private static boolean facesCamera(Direction face, float x0, float y0, float z0, float x1, float y1, float z1) {
        // the camera is at the origin
        return switch (face) {
            case EAST -> x1 < 0.0F;
            case WEST -> x0 > 0.0F;
            case UP -> y1 < 0.0F;
            case DOWN -> y0 > 0.0F;
            case SOUTH -> z1 < 0.0F;
            case NORTH -> z0 > 0.0F;
        };
    }

    private static boolean isCovered(ClientLevel level, BlockPos pos, Direction face) {
        return level.getBlockState(pos.relative(face)).isSolidRender();
    }

    /**
     * Part of one face of a box, from (u0, v0) to (u1, v1) across it, lifted just off it. Up the sides v runs from the
     * bottom to the top.
     */
    private static void rect(VertexConsumer buffer, PoseStack.Pose pose, Direction face, float x0, float y0, float z0, float x1, float y1, float z1,
                             float u0, float v0, float u1, float v1, int argb) {
        switch (face.getAxis()) {
            case X -> {
                float px = face == Direction.EAST ? x1 + LIFT : x0 - LIFT;
                float za = z0 + (z1 - z0) * u0;
                float zb = z0 + (z1 - z0) * u1;
                float ya = y0 + (y1 - y0) * v0;
                float yb = y0 + (y1 - y0) * v1;
                quad(buffer, pose, px, ya, za, px, ya, zb, px, yb, zb, px, yb, za, argb);
            }
            case Z -> {
                float pz = face == Direction.SOUTH ? z1 + LIFT : z0 - LIFT;
                float xa = x0 + (x1 - x0) * u0;
                float xb = x0 + (x1 - x0) * u1;
                float ya = y0 + (y1 - y0) * v0;
                float yb = y0 + (y1 - y0) * v1;
                quad(buffer, pose, xa, ya, pz, xb, ya, pz, xb, yb, pz, xa, yb, pz, argb);
            }
            case Y -> {
                float py = face == Direction.UP ? y1 + LIFT : y0 - LIFT;
                float xa = x0 + (x1 - x0) * u0;
                float xb = x0 + (x1 - x0) * u1;
                float za = z0 + (z1 - z0) * v0;
                float zb = z0 + (z1 - z0) * v1;
                quad(buffer, pose, xa, py, za, xb, py, za, xb, py, zb, xa, py, zb, argb);
            }
        }
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy,
                             float cz, float dx, float dy, float dz, int argb) {
        buffer.addVertex(pose, ax, ay, az).setColor(argb);
        buffer.addVertex(pose, bx, by, bz).setColor(argb);
        buffer.addVertex(pose, cx, cy, cz).setColor(argb);
        buffer.addVertex(pose, dx, dy, dz).setColor(argb);
    }

    /**
     * The twelve edges of a box in soft lines of light.
     */
    static void edges(VertexConsumer buffer, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, float width, int argb) {
        // along x
        edge(buffer, pose, x0, y0, z0, x1, y0, z0, width, argb);
        edge(buffer, pose, x0, y1, z0, x1, y1, z0, width, argb);
        edge(buffer, pose, x0, y0, z1, x1, y0, z1, width, argb);
        edge(buffer, pose, x0, y1, z1, x1, y1, z1, width, argb);
        // up
        edge(buffer, pose, x0, y0, z0, x0, y1, z0, width, argb);
        edge(buffer, pose, x1, y0, z0, x1, y1, z0, width, argb);
        edge(buffer, pose, x0, y0, z1, x0, y1, z1, width, argb);
        edge(buffer, pose, x1, y0, z1, x1, y1, z1, width, argb);
        // along z
        edge(buffer, pose, x0, y0, z0, x0, y0, z1, width, argb);
        edge(buffer, pose, x1, y0, z0, x1, y0, z1, width, argb);
        edge(buffer, pose, x0, y1, z0, x0, y1, z1, width, argb);
        edge(buffer, pose, x1, y1, z0, x1, y1, z1, width, argb);
    }

    /**
     * A soft line between two points, turned to face the camera at the origin: bright down its middle, fading out to
     * both sides.
     */
    static void edge(VertexConsumer buffer, PoseStack.Pose pose, float ax, float ay, float az, float bx, float by, float bz, float width, int argb) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        // the middle of the line, toward the camera
        float mx = -(ax + bx) * 0.5F;
        float my = -(ay + by) * 0.5F;
        float mz = -(az + bz) * 0.5F;
        float sx = dy * mz - dz * my;
        float sy = dz * mx - dx * mz;
        float sz = dx * my - dy * mx;
        float length = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (length < 1.0E-6F) {
            return;
        }
        float scale = width * 0.5F / length;
        sx *= scale;
        sy *= scale;
        sz *= scale;
        int clear = argb & 0x00FFFFFF;
        for (float sign : new float[] {1.0F, -1.0F}) {
            buffer.addVertex(pose, ax, ay, az).setColor(argb);
            buffer.addVertex(pose, ax + sx * sign, ay + sy * sign, az + sz * sign).setColor(clear);
            buffer.addVertex(pose, bx + sx * sign, by + sy * sign, bz + sz * sign).setColor(clear);
            buffer.addVertex(pose, bx, by, bz).setColor(argb);
        }
    }
}
