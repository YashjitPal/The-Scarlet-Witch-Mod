package com.yashjit.scarlet.client.hex;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.hex.HexShape;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * Draws the Hex over the finished world: the staticky wall, and the era as seen through it, or all around you from
 * inside.
 *
 * <p>The shader works out where each pixel's surface is in the world from the depth buffer, so walls are cut
 * exactly by whatever stands in front of them. Vanilla wipes the world's depth to draw your hand unless a screen
 * effect needs it, so while a Hex is near we ask for it to be kept, and your hand is told apart by its own depth.
 */
public final class HexScreen {

    public static final int MAX_HEXES = 4;

    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Scarlet.id("post/hex"))
            .withBindGroupLayout(BindGroupLayout.builder()
                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("HandDepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("HexView", UniformType.UNIFORM_BUFFER)
                    .build())
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withLocation(Scarlet.id("pipeline/hex"))
            .build();

    // mat4, then the shapes and styles of each Hex, then the camera and frame parameters
    private static final int UNIFORM_SIZE = 64 + MAX_HEXES * 16 * 2 + 16 * 2;
    private static final float ENTER_RATE = 5.0F;
    private static final float LEAVE_RATE = 7.0F;

    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW_ROTATION = new Matrix4f();
    private static Vec3 cameraPos = Vec3.ZERO;
    private static @Nullable TextureTarget copy;
    private static @Nullable MappableRingBuffer uniforms;
    private static boolean broken;
    private static boolean wanted;
    private static float inside;
    private static Era insideEra = Era.PRESENT;
    private static long lastNanos;

    private HexScreen() {
    }

    /**
     * Records the camera the world is about to be drawn with.
     */
    public static void captureView(Matrix4f projection, CameraRenderState camera) {
        PROJECTION.set(projection);
        VIEW_ROTATION.set(camera.viewRotationMatrix);
        cameraPos = camera.pos;
    }

    /**
     * Whether a Hex is near enough that the world's depth must outlive the hand being drawn.
     */
    public static boolean wanted() {
        return wanted && !broken;
    }

    public static void warmUp() {
        if (!broken && RenderSystem.getCompiledPipelineNullable(PIPELINE) == null) {
            broken = true;
            Scarlet.LOG.error("The Hex screen effect failed to compile, so Hexes will not be drawn");
        }
    }

    public static void render(RenderTarget main, RenderTarget hand) {
        Minecraft minecraft = Minecraft.getInstance();
        long nanos = System.nanoTime();
        float seconds = lastNanos == 0 ? 0.0F : Math.min(0.1F, (nanos - lastNanos) / 1.0E9F);
        lastNanos = nanos;
        if (broken || minecraft.level == null) {
            wanted = false;
            return;
        }
        double now = minecraft.level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double reach = minecraft.options.getEffectiveRenderDistance() * 16.0;
        List<HexClient.Shown> hexes = HexClient.shown(cameraPos, now, reach, MAX_HEXES);
        HexClient.Shown around = null;
        for (HexClient.Shown hex : hexes) {
            if (HexShape.contains(hex.center(), hex.radius(), cameraPos)) {
                around = hex;
                break;
            }
        }
        if (around != null) {
            insideEra = around.era();
        }
        inside = Ease.damp(inside, around != null ? 1.0F : 0.0F, around != null ? ENTER_RATE : LEAVE_RATE, seconds);
        if (around == null && inside < 0.002F) {
            inside = 0.0F;
        }
        wanted = !hexes.isEmpty();
        if (hexes.isEmpty() && inside == 0.0F) {
            return;
        }
        RenderPipeline compiled = PIPELINE;
        if (RenderSystem.getCompiledPipelineNullable(compiled) == null) {
            broken = true;
            Scarlet.LOG.error("The Hex screen effect failed to compile, so Hexes will not be drawn");
            return;
        }
        int width = main.width;
        int height = main.height;
        if (copy == null) {
            copy = new TextureTarget("Scarlet hex", width, height, GpuFormat.RGBA8_UNORM, null);
        } else if (copy.width != width || copy.height != height) {
            copy.resize(width, height);
        }
        if (uniforms == null) {
            uniforms = new MappableRingBuffer(() -> "Scarlet hex view", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UNIFORM_SIZE);
        }
        writeUniforms(uniforms, hexes, around, width, height, (float) (now / 20.0 % 3600.0));

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(main.getColorTexture(), copy.getColorTexture(), 0, 0, 0, 0, 0, width, height);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        try (RenderPass pass = encoder.createRenderPass(() -> "Scarlet hex", main.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(compiled));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("HexView", uniforms.currentBuffer());
            pass.setUniform("InSampler", copy.getColorTextureView(), linear);
            pass.setUniform("DepthSampler", main.getDepthTextureView(), nearest);
            pass.setUniform("HandDepthSampler", hand.getDepthTextureView(), nearest);
            pass.draw(3, 1, 0, 0);
        }
        uniforms.rotate();
    }

    private static void writeUniforms(MappableRingBuffer buffer, List<HexClient.Shown> hexes, HexClient.@Nullable Shown around, int width,
                                      int height, float time) {
        Matrix4f inverse = new Matrix4f(PROJECTION).mul(VIEW_ROTATION).invert();
        int aroundIndex = around == null ? -1 : hexes.indexOf(around);
        float tv = inside * (insideEra.ordinal() <= Era.TWO_THOUSANDS.ordinal() ? 1.0F : 0.0F);
        try (GpuBufferSlice.MappedView view = buffer.currentBuffer().map(false, true)) {
            Std140Builder builder = Std140Builder.intoBuffer(view.data());
            builder.putMat4f(inverse);
            for (int i = 0; i < MAX_HEXES; i++) {
                if (i < hexes.size()) {
                    HexClient.Shown hex = hexes.get(i);
                    Vec3 center = hex.center().subtract(cameraPos);
                    builder.putVec4((float) center.x, (float) center.y, (float) center.z, hex.radius());
                } else {
                    builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
                }
            }
            for (int i = 0; i < MAX_HEXES; i++) {
                if (i < hexes.size()) {
                    HexClient.Shown hex = hexes.get(i);
                    builder.putVec4(hex.era().ordinal(), hex.flare(), hex.warning(), 1.0F);
                } else {
                    builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
                }
            }
            builder.putVec4(aroundIndex + 1, inside, insideEra.ordinal(), hexes.size());
            builder.putVec4(time, width, height, tv);
        }
    }
}
