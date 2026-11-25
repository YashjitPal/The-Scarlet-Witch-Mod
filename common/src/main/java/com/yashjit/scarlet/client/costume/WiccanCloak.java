package com.yashjit.scarlet.client.costume;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yashjit.scarlet.client.render.ScarletRenderTypes;
import com.yashjit.scarlet.crown.CrownStyle;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;

/** A lined sheet pinned at the shoulders. Vanilla supplies the cape swing and the sheet flexes below it. */
final class WiccanCloak {
    private static final int COLUMNS = 8, ROWS = 8;

    private WiccanCloak() {}

    static void submit(PlayerCapeModel model, AvatarRenderState state, PoseStack stack, SubmitNodeCollector collector,
                       boolean divine, int light, int overlay, int tint) {
        model.setupAnim(state);
        stack.pushPose();
        model.root().translateAndRotate(stack);
        model.body.translateAndRotate(stack);
        model.body.getChild("cape").translateAndRotate(stack);
        stack.scale(1 / 16f, 1 / 16f, 1 / 16f);
        float time = state.ageInTicks * 0.12f + (state.id & 31);
        float motion = Mth.clamp(Math.abs(state.capeLean) / 60f, 0f, 1f);
        var type = divine ? ScarletRenderTypes.demiurgeCape() : ScarletRenderTypes.cape(CrownStyle.WICCAN);
        collector.submitCustomGeometry(stack, type, (pose, buffer) -> {
            Corner[] previous = new Corner[COLUMNS + 1], current = new Corner[COLUMNS + 1];
            for (int row = 0; row <= ROWS; row++) {
                float t = row / (float) ROWS;
                for (int col = 0; col <= COLUMNS; col++) {
                    float u = col / (float) COLUMNS;
                    current[col] = corner(u, t, divine, time, motion);
                    if (row == 0 || col == 0) continue;
                    Corner a = previous[col - 1], b = previous[col], c = current[col], d = current[col - 1];
                    // Face-local normal from the cross product of the top and left edges.
                    float ex = b.x - a.x, ey = b.y - a.y, ez = b.z - a.z;
                    float fx = d.x - a.x, fy = d.y - a.y, fz = d.z - a.z;
                    float nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx;
                    float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
                    nx /= length;
                    ny /= length;
                    nz /= length;
                    for (int face = 0; face < 2; face++) {
                        boolean lining = face == 1;
                        float sign = lining ? 1 : -1;
                        a.emit(buffer, pose, lining, nx * sign, ny * sign, nz * sign, light, overlay, tint);
                        (lining ? b : d).emit(buffer, pose, lining, nx * sign, ny * sign, nz * sign, light, overlay, tint);
                        c.emit(buffer, pose, lining, nx * sign, ny * sign, nz * sign, light, overlay, tint);
                        (lining ? d : b).emit(buffer, pose, lining, nx * sign, ny * sign, nz * sign, light, overlay, tint);
                    }
                }
                Corner[] swap = previous;
                previous = current;
                current = swap;
            }
        });
        stack.popPose();
    }

    private static Corner corner(float u, float t, boolean divine, float time, float motion) {
        float side = u * 2 - 1;
        float bow = Mth.sin(t * (float) Math.PI);
        float width = divine ? 6.0f + 5.0f * bow + 2.7f * t : 4.65f + 2.5f * t;
        float wave = Mth.sin(time - 5 * t + side * 1.6f);
        float x = side * width + t * t * wave * (0.75f + motion);
        float y = 22 * t - (divine ? 2.1f : 0.55f) * side * side * bow;
        float z = -0.8f - t * (1.8f + motion * 1.3f + wave * (0.85f + motion))
                - bow * (divine ? 2.8f : 1.2f) - (0.18f + t * 0.8f) * Mth.cos(side * 9.3f - t * 1.4f);
        return new Corner(x, y, z, u, t);
    }

    private record Corner(float x, float y, float z, float u, float t) {
        void emit(VertexConsumer buffer, PoseStack.Pose pose, boolean lining, float nx, float ny, float nz,
                  int light, int overlay, int tint) {
            float atlasU = lining ? 22 - 10 * u : 1 + 10 * u;
            buffer.addVertex(pose, x, y, z + (lining ? 0.08f : 0)).setColor(tint)
                    .setUv(atlasU / 64, (1 + t * 22) / 64).setOverlay(overlay).setLight(light)
                    .setNormal(pose, nx, ny, nz);
        }
    }
}
