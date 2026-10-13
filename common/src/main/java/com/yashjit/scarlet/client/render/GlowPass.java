package com.yashjit.scarlet.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jspecify.annotations.Nullable;

/**
 * Magic that lives in the world: its pixels, glows, and the tints beneath them.
 *
 * <p>With improved transparency they are ordinary translucent geometry, composited in depth order with water and
 * clouds. Without it, vanilla draws water, clouds and weather after every translucent entity and blends them over
 * whatever is already there, which would dim magic standing in front of them. So in that mode it is held back and
 * drawn last, after the weather, depth tested against the scene but never writing depth: tints first, then the pixels,
 * farthest first, then the glows over them.
 */
public final class GlowPass {

    private static final ByteBufferBuilder BYTES = new ByteBufferBuilder(1 << 18);
    private static final ByteBufferBuilder INDEX_BYTES = new ByteBufferBuilder(1 << 16);
    private static final Layer TINTS = new Layer("Scarlet tints", ScarletRenderTypes.TINT_PIPELINE, false);
    private static final Layer PIXELS = new Layer("Scarlet pixels", ScarletRenderTypes.PIXEL_PIPELINE, true);
    private static final Layer GLOWS = new Layer("Scarlet glows", ScarletRenderTypes.GLOW_PIPELINE, false);

    private GlowPass() {
    }

    /**
     * Glows to draw, darkened as {@link Glow#darkness()} is now: a corrupted caster's magic keeps its darkness though it
     * is drawn later.
     */
    public static void submit(SubmitNodeCollector collector, PoseStack poseStack, SubmitNodeCollector.CustomGeometryRenderer renderer) {
        submit(collector, poseStack, renderer, ScarletRenderTypes.glow(), GLOWS);
    }

    /**
     * Pixels to draw, darkened as {@link Glow#darkness()} is now. Drawn with {@link Pixels}.
     */
    public static void submitPixels(SubmitNodeCollector collector, PoseStack poseStack, SubmitNodeCollector.CustomGeometryRenderer renderer) {
        submit(collector, poseStack, renderer, ScarletRenderTypes.pixel(), PIXELS);
    }

    public static void submitTint(SubmitNodeCollector collector, PoseStack poseStack, SubmitNodeCollector.CustomGeometryRenderer renderer) {
        submit(collector, poseStack, renderer, ScarletRenderTypes.tint(), TINTS);
    }

    private static void submit(SubmitNodeCollector collector, PoseStack poseStack, SubmitNodeCollector.CustomGeometryRenderer renderer,
                               RenderType type, Layer layer) {
        float darkness = Glow.darkness();
        if (Minecraft.getInstance().gameRenderer.useImprovedTransparency()) {
            boolean tint = layer == TINTS;
            collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
                try (Glow.Darkening ignored = Glow.drawing(darkness, tint)) {
                    renderer.render(pose, buffer);
                }
            });
        } else {
            layer.deferred.add(new Deferred(poseStack.last().copy(), renderer, darkness));
        }
    }

    /**
     * A tint's vertex color: glass of color {@code rgb} at {@code density}, 0 clear to 1 the full filter.
     *
     * <p>Without improved transparency a tint filters what is behind it, so the color is premultiplied for
     * {@link ScarletRenderTypes#TINT_PIPELINE}. The transparency pass can only blend; there the glass is drawn darker
     * and thinner, which is the closest blending comes to filtering.
     */
    public static int tint(int rgb, float density) {
        float a = Math.clamp(density, 0.0F, 1.0F);
        if (Minecraft.getInstance().gameRenderer.useImprovedTransparency()) {
            return Glow.withAlpha(Glow.mix(rgb, 0x000000, 0.35F), a * 0.5F);
        }
        int r = Math.round(((rgb >> 16) & 0xFF) * a);
        int g = Math.round(((rgb >> 8) & 0xFF) * a);
        int b = Math.round((rgb & 0xFF) * a);
        return Glow.withAlpha((r << 16) | (g << 8) | b, a);
    }

    /**
     * Runs before the main render pass opens, the only time vertex and index buffers may be written.
     */
    public static void prepare() {
        int tints = TINTS.prepare();
        PIXELS.prepare();
        int glows = GLOWS.prepare();
        if (tints > 0 || glows > 0) {
            RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).requestIndexCount(Math.max(tints, glows));
        }
    }

    /**
     * Runs last in the classic transparency pass, after translucent terrain, clouds, weather and the world border.
     */
    public static void draw(RenderPass renderPass) {
        TINTS.draw(renderPass);
        PIXELS.draw(renderPass);
        GLOWS.draw(renderPass);
    }

    private static final class Layer {
        final List<Deferred> deferred = new ArrayList<>();
        final String name;
        final RenderPipeline pipeline;
        /** Whether its quads are drawn farthest first, as anything blended over what is behind it must be. */
        final boolean sorted;
        @Nullable GpuBuffer vertexBuffer;
        @Nullable GpuBuffer indexBuffer;
        IndexType indexType = IndexType.INT;
        int indexCount;

        Layer(String name, RenderPipeline pipeline, boolean sorted) {
            this.name = name;
            this.pipeline = pipeline;
            this.sorted = sorted;
        }

        int prepare() {
            indexCount = 0;
            if (deferred.isEmpty()) {
                return 0;
            }
            BufferBuilder builder = new BufferBuilder(BYTES, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (Deferred entry : deferred) {
                try (Glow.Darkening ignored = Glow.drawing(entry.darkness(), this == TINTS)) {
                    entry.renderer().render(entry.pose(), builder);
                }
            }
            deferred.clear();
            try (MeshData mesh = builder.build()) {
                if (mesh == null) {
                    return 0;
                }
                if (sorted) {
                    // the camera is at the origin of the space the quads are drawn in
                    mesh.sortQuads(INDEX_BYTES, VertexSorting.DISTANCE_TO_ORIGIN);
                    ByteBuffer indices = mesh.indexBuffer();
                    if (indices == null) {
                        return 0;
                    }
                    indexType = mesh.drawState().indexType();
                    uploadIndices(indices);
                }
                upload(mesh.vertexBuffer());
                indexCount = mesh.drawState().indexCount();
            }
            return indexCount;
        }

        void draw(RenderPass renderPass) {
            if (indexCount == 0 || vertexBuffer == null || sorted && indexBuffer == null) {
                return;
            }
            renderPass.pushDebugGroup(() -> name);
            renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy()));
            if (sorted) {
                renderPass.setIndexBuffer(indexBuffer, indexType);
            } else {
                RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
                renderPass.setIndexBuffer(indices.getBuffer(), indices.type());
            }
            renderPass.setVertexBuffer(0, vertexBuffer.slice());
            renderPass.drawIndexed(indexCount, 1, 0, 0, 0);
            renderPass.popDebugGroup();
            indexCount = 0;
        }

        private void upload(ByteBuffer vertices) {
            GpuDevice device = RenderSystem.getDevice();
            if (vertexBuffer == null || vertexBuffer.size() < vertices.remaining()) {
                if (vertexBuffer != null) {
                    vertexBuffer.close();
                }
                vertexBuffer = device.createBuffer(() -> name + " vertices", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                        vertices.remaining() * 2L);
            }
            device.createCommandEncoder().writeToBuffer(vertexBuffer.slice(), vertices);
        }

        private void uploadIndices(ByteBuffer indices) {
            GpuDevice device = RenderSystem.getDevice();
            if (indexBuffer == null || indexBuffer.size() < indices.remaining()) {
                if (indexBuffer != null) {
                    indexBuffer.close();
                }
                indexBuffer = device.createBuffer(() -> name + " indices", GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST,
                        indices.remaining() * 2L);
            }
            device.createCommandEncoder().writeToBuffer(indexBuffer.slice(), indices);
        }
    }

    private record Deferred(PoseStack.Pose pose, SubmitNodeCollector.CustomGeometryRenderer renderer, float darkness) {
    }
}
