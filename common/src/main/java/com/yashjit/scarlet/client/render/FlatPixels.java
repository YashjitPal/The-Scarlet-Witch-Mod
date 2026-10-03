package com.yashjit.scarlet.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Magic lying flat, pixel by pixel on the world's own grid of sixteenths of a block, so its pixels line up with the
 * texels of the blocks it lies on, the way a sigil chalked on the ground would. Each pixel is painted by a function of
 * where it lies, as a shader would paint it.
 */
public final class FlatPixels {

    private static final Vector3f X = new Vector3f(1.0F, 0.0F, 0.0F);
    private static final Vector3f Z = new Vector3f(0.0F, 0.0F, 1.0F);

    private FlatPixels() {
    }

    /**
     * What color a pixel lying flat is, or 0 to leave it out.
     */
    @FunctionalInterface
    public interface Paint {
        /**
         * @param i     which pixel of the world's grid it is across x
         * @param k     and across z
         * @param r     how far its middle is from the middle of what is being drawn, in blocks
         * @param theta which way from that middle it lies, in radians, 0 toward +x and a quarter turn toward +z
         */
        int at(int i, int k, float r, float theta);
    }

    /**
     * Paints every pixel lying flat at height {@code y} whose middle is between {@code inner} and {@code outer} blocks
     * from x, z. Positions are in the world; the pose is the camera's, where the world is drawn less the camera's
     * position.
     */
    public static void ring(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, double x, double y, double z, float inner, float outer, Paint paint) {
        if (outer <= 0.0F || outer <= inner) {
            return;
        }
        float half = Pixels.SIZE * 0.5F;
        float height = (float) (y - camera.y);
        int k0 = Mth.floor((z - outer) * 16.0);
        int k1 = Mth.floor((z + outer) * 16.0);
        for (int k = k0; k <= k1; k++) {
            double cz = (k + 0.5) / 16.0 - z;
            if (Math.abs(cz) > outer) {
                continue;
            }
            double span = Math.sqrt(outer * outer - cz * cz);
            double hole = Math.abs(cz) < inner ? Math.sqrt(inner * inner - cz * cz) : -1.0;
            int i0 = Mth.floor((x - span) * 16.0);
            int i1 = Mth.floor((x + span) * 16.0);
            for (int i = i0; i <= i1; i++) {
                double cx = (i + 0.5) / 16.0 - x;
                if (hole > 0.0 && Math.abs(cx) < hole) {
                    // skip over the hole in the middle in one go
                    i = Math.max(i, Mth.floor((x + hole) * 16.0) - 1);
                    continue;
                }
                float r = (float) Math.sqrt(cx * cx + cz * cz);
                if (r > outer || r < inner) {
                    continue;
                }
                int argb = paint.at(i, k, r, (float) Math.atan2(cz, cx));
                if ((argb >>> 24) == 0) {
                    continue;
                }
                Pixels.square(buffer, pose, X, Z, (float) ((i + 0.5) / 16.0 - camera.x), height, (float) ((k + 0.5) / 16.0 - camera.z), half, argb);
            }
        }
    }

    /**
     * Lines drawn flat on the world's grid of sixteenths a pixel wide, as a sigil chalked on the ground would be: each
     * pixel colored once, and where lines cross, the one drawn with the higher priority shows. Positions are in the
     * world.
     */
    public static final class Sketch {

        private final Long2IntOpenHashMap colors = new Long2IntOpenHashMap();
        private final Long2IntOpenHashMap priorities = new Long2IntOpenHashMap();

        public void dot(double x, double z, int argb, int priority) {
            put(Mth.floor(x * 16.0), Mth.floor(z * 16.0), argb, priority);
        }

        public void line(double x0, double z0, double x1, double z1, int argb, int priority) {
            double dx = (x1 - x0) * 16.0;
            double dz = (z1 - z0) * 16.0;
            int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dz))));
            for (int s = 0; s <= steps; s++) {
                double t = s / (double) steps;
                put(Mth.floor(x0 * 16.0 + dx * t), Mth.floor(z0 * 16.0 + dz * t), argb, priority);
            }
        }

        /**
         * As much of a circle as {@code reach} goes round, 0 none to 1 all of it, from {@code from} radians.
         */
        public void arc(double x, double z, double radius, float from, float reach, int argb, int priority) {
            if (reach <= 0.0F) {
                return;
            }
            int steps = Math.max(4, (int) Math.ceil(radius * 16.0 * Mth.TWO_PI * reach * 1.5));
            for (int s = 0; s <= steps; s++) {
                double a = from + reach * Mth.TWO_PI * s / steps;
                put(Mth.floor((x + Math.cos(a) * radius) * 16.0), Mth.floor((z + Math.sin(a) * radius) * 16.0), argb, priority);
            }
        }

        private void put(int i, int k, int argb, int priority) {
            if ((argb >>> 24) == 0) {
                return;
            }
            long key = ((long) i << 32) ^ (k & 0xFFFFFFFFL);
            if (colors.containsKey(key) && priorities.get(key) > priority) {
                return;
            }
            colors.put(key, argb);
            priorities.put(key, priority);
        }

        /**
         * Draws it lying at height {@code y}, thinned out in a dither as {@code shown} falls below 1.
         */
        public void draw(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, double y, float shown) {
            float half = Pixels.SIZE * 0.5F;
            float height = (float) (y - camera.y);
            for (Long2IntMap.Entry entry : colors.long2IntEntrySet()) {
                long key = entry.getLongKey();
                int i = (int) (key >> 32);
                int k = (int) key;
                if (shown < 1.0F && !Pixels.shows(shown, i, k)) {
                    continue;
                }
                Pixels.square(buffer, pose, X, Z, (float) ((i + 0.5) / 16.0 - camera.x), height, (float) ((k + 0.5) / 16.0 - camera.z), half,
                        entry.getIntValue());
            }
        }
    }
}
