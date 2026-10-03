package com.yashjit.scarlet.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * Magic that lives in the world: glows, and the tints beneath them.
 *
 * <p>With improved transparency they are ordinary translucent geometry, composited in depth order with water and
 * clouds. Without it, vanilla draws water, clouds and weather after every translucent entity and blends them over
 * whatever is already there, which would dim magic standing in front of them. So in that mode it is held back and
 * drawn last, after the weather, depth tested against the scene but never writing depth: tints first, then the glows
 * over them. Either way the glows can be drawn again on their own, for a Hex to keep them in color.
 */
public final class GlowPass {

    private static final ByteBufferBuilder BYTES = new ByteBufferBuilder(1 << 18);
    private static final Layer TINTS = new Layer("Scarlet tints", ScarletRenderTypes.TINT_PIPELINE);
    private static final Layer GLOWS = new Layer("Scarlet glows", ScarletRenderTypes.GLOW_PIPELINE);
    /** With improved transparency the glows are drawn by vanilla; a copy is kept to draw again on their own. */
    private static final Layer MIRROR = new Layer("Scarlet magic", ScarletRenderTypes.GLOW_PIPELINE);
    private static final ProjectionMatrixBuffer MAGIC_PROJECTION = new ProjectionMatrixBuffer("Scarlet magic projection");
    /** How many glow indices the classic pass drew this frame, still uploaded to draw again. */
    private static int drawnGlows;

    private GlowPass() {
    }

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack, SubmitNodeCollector.CustomGeometryRenderer renderer) {
        submit(collector, poseStack, renderer, ScarletRenderTypes.glow(), GLOWS);
        if (Minecraft.getInstance().gameRenderer.useImprovedTransparency()) {
            MIRROR.deferred.add(new Deferred(poseStack.last().copy(), renderer));
        }
    }

    /**
     * Draws this frame's glows again, alone, depth tested against the finished world: the magic's own light, kept
     * apart so a Hex can drain the world around it to black and white while the magic stays scarlet.
     *
     * @param color where to draw them, already cleared
     */
    public static void drawMagic(GpuTextureView color, GpuTextureView depth, Matrix4f projection, Matrix4f view) {
        Layer layer = GLOWS;
        int count = drawnGlows;
        if (Minecraft.getInstance().gameRenderer.useImprovedTransparency()) {
            layer = MIRROR;
            count = MIRROR.prepare();
        }
        if (count == 0 || layer.vertexBuffer == null) {
            return;
        }
        // buffers can only be written before the pass opens
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        indices.requestIndexCount(count);
        GpuBufferSlice projectionSlice = MAGIC_PROJECTION.getBuffer(projection);
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(view);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Scarlet magic", color, Optional.empty(),
                depth, OptionalDouble.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(ScarletRenderTypes.GLOW_PIPELINE));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("Projection", projectionSlice);
            pass.setUniform("DynamicTransforms", transform);
            pass.setIndexBuffer(indices.getBuffer(), indices.type());
            pass.setVertexBuffer(0, layer.vertexBuffer.slice());
            pass.drawIndexed(count, 1, 0, 0, 0);
        }
    }

    public static void submitTint(SubmitNodeCollector collector, PoseStack poseStack, SubmitNodeCollector.CustomGeometryRenderer renderer) {
        submit(collector, poseStack, renderer, ScarletRenderTypes.tint(), TINTS);
    }

    private static void submit(SubmitNodeCollector collector, PoseStack poseStack, SubmitNodeCollector.CustomGeometryRenderer renderer,
                               RenderType type, Layer layer) {
        if (Minecraft.getInstance().gameRenderer.useImprovedTransparency()) {
            collector.submitCustomGeometry(poseStack, type, renderer);
        } else {
            layer.deferred.add(new Deferred(poseStack.last().copy(), renderer));
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
     * Lets go of this frame's copy of the glows, whether or not it was drawn again.
     */
    public static void endFrame() {
        MIRROR.deferred.clear();
    }

    /**
     * Runs before the main render pass opens, the only time vertex and index buffers may be written.
     */
    public static void prepare() {
        drawnGlows = 0;
        int tints = TINTS.prepare();
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
        drawnGlows = GLOWS.indexCount;
        GLOWS.draw(renderPass);
    }

    private static final class Layer {
        final List<Deferred> deferred = new ArrayList<>();
        final String name;
        final RenderPipeline pipeline;
        @Nullable GpuBuffer vertexBuffer;
        int indexCount;

        Layer(String name, RenderPipeline pipeline) {
            this.name = name;
            this.pipeline = pipeline;
        }

        int prepare() {
            indexCount = 0;
            if (deferred.isEmpty()) {
                return 0;
            }
            BufferBuilder builder = new BufferBuilder(BYTES, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (Deferred entry : deferred) {
                entry.renderer().render(entry.pose(), builder);
            }
            deferred.clear();
            try (MeshData mesh = builder.build()) {
                if (mesh == null) {
                    return 0;
                }
                upload(mesh.vertexBuffer());
                indexCount = mesh.drawState().indexCount();
            }
            return indexCount;
        }

        void draw(RenderPass renderPass) {
            if (indexCount == 0 || vertexBuffer == null) {
                return;
            }
            RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            renderPass.pushDebugGroup(() -> name);
            renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy()));
            renderPass.setIndexBuffer(indices.getBuffer(), indices.type());
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
    }

    private record Deferred(PoseStack.Pose pose, SubmitNodeCollector.CustomGeometryRenderer renderer) {
    }
}
