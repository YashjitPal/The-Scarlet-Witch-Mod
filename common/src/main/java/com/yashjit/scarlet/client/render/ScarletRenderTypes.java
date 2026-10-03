package com.yashjit.scarlet.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.crown.CrownStyle;
import com.yashjit.scarlet.mixin.client.RenderTypeInvoker;
import java.util.Optional;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Render types for costumes and magic, built only from vanilla shaders so they work on both graphics backends, with
 * and without improved transparency.
 */
public final class ScarletRenderTypes {

    private static final Identifier REVEAL = Scarlet.id("textures/entity/costume/reveal.png");
    private static final Identifier REVEAL_SLIM = Scarlet.id("textures/entity/costume/reveal_slim.png");
    private static final Identifier CAPE_REVEAL = Scarlet.id("textures/entity/costume/cape_reveal.png");

    /**
     * Vanilla lightning's shaders, drawn from both sides: magic laid out in a plane, like a shield, is seen from behind
     * as often as from the front.
     */
    private static final RenderPipeline.Snippet LIGHTNING_SNIPPET = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .buildSnippet();

    /**
     * Vanilla lightning, but depth tested without writing depth: a glow's faint rim covers far more of the screen than
     * its light does, and must not hide the water, clouds and weather drawn after it.
     */
    public static final RenderPipeline GLOW_PIPELINE = RenderPipeline.builder(LIGHTNING_SNIPPET)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withLocation(Scarlet.id("pipeline/glow"))
            .build();

    /**
     * Vanilla lightning's depth bounds stage, except that the glow writes only its closest depth. Vanilla also widens
     * each pixel's depth range with it and, where it is bright enough, marks it opaque: a soft halo then smears the
     * clouds and water behind it, and a hot core punches a hole through them.
     */
    private static final RenderPipeline GLOW_DEPTH_BOUNDS = RenderPipeline.builder(LIGHTNING_SNIPPET, RenderPipelines.OIT_DEPTH_BOUNDS_SNIPPET)
            .withShaderDefine("OIT_ADDITIVE")
            .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.MAX), GpuFormat.RGBA32_FLOAT, ColorTargetState.WRITE_BLUE))
            .withLocation(Scarlet.id("pipeline/oit_depth_bounds_glow"))
            .build();

    private static final OitPipelineSet GLOW_OIT = OitPipelineSet.builder("scarlet_glow",
            RenderPipeline.builder(LIGHTNING_SNIPPET).withShaderDefine("OIT_ADDITIVE")).build();

    private static final RenderType GLOW = RenderTypeInvoker.scarlet$create("scarlet_glow",
            RenderSetup.builder(GLOW_PIPELINE)
                    .setOitPipelines(new OitPipelineSet(GLOW_DEPTH_BOUNDS, GLOW_OIT.transmittancePipeline(), GLOW_OIT.accumulatePipeline()))
                    .sortOnUpload()
                    .createRenderSetup());

    /**
     * Colored glass made of light, depth tested without writing depth. A glow can only brighten what is behind it, which
     * washes out to pink against a bright sky; a tint underneath filters it the way red glass does, so magic keeps its
     * scarlet in daylight. Colors are premultiplied: what is behind is multiplied by {@code 1 - a + a * rgb}.
     */
    public static final RenderPipeline TINT_PIPELINE = RenderPipeline.builder(LIGHTNING_SNIPPET)
            .withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.DST_COLOR, BlendFactor.ONE_MINUS_SRC_ALPHA,
                    BlendFactor.ZERO, BlendFactor.ONE)))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withLocation(Scarlet.id("pipeline/tint"))
            .build();

    private static final OitPipelineSet TINT_OIT = OitPipelineSet.builder("scarlet_tint", RenderPipeline.builder(LIGHTNING_SNIPPET)).build();

    private static final RenderType TINT = RenderTypeInvoker.scarlet$create("scarlet_tint",
            RenderSetup.builder(TINT_PIPELINE)
                    .setOitPipelines(TINT_OIT)
                    .sortOnUpload()
                    .createRenderSetup());

    /**
     * Magic as pixel art: hard-edged squares of flat color, blended over what is behind them as they are, depth tested
     * without writing depth. Unlike light, they can be as dark as crimson over snow.
     */
    public static final RenderPipeline PIXEL_PIPELINE = RenderPipeline.builder(LIGHTNING_SNIPPET)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withLocation(Scarlet.id("pipeline/pixel"))
            .build();

    private static final OitPipelineSet PIXEL_OIT = OitPipelineSet.builder("scarlet_pixel", RenderPipeline.builder(LIGHTNING_SNIPPET)).build();

    private static final RenderType PIXEL = RenderTypeInvoker.scarlet$create("scarlet_pixel",
            RenderSetup.builder(PIXEL_PIPELINE)
                    .setOitPipelines(PIXEL_OIT)
                    .sortOnUpload()
                    .createRenderSetup());

    private ScarletRenderTypes() {
    }

    /**
     * Compiles our pipelines ahead of time. Vanilla compiles any pipeline it has not seen on first use, which would
     * stall the first frame a glow appears in.
     */
    public static void warmUp() {
        RenderSystem.getCompiledPipeline(GLOW_PIPELINE);
        RenderSystem.getCompiledPipeline(GLOW_DEPTH_BOUNDS);
        RenderSystem.getCompiledPipeline(GLOW_OIT.transmittancePipeline());
        RenderSystem.getCompiledPipeline(GLOW_OIT.accumulatePipeline());
        RenderSystem.getCompiledPipeline(TINT_PIPELINE);
        RenderSystem.getCompiledPipeline(TINT_OIT.depthBoundsPipeline());
        RenderSystem.getCompiledPipeline(TINT_OIT.transmittancePipeline());
        RenderSystem.getCompiledPipeline(TINT_OIT.accumulatePipeline());
        RenderSystem.getCompiledPipeline(PIXEL_PIPELINE);
        RenderSystem.getCompiledPipeline(PIXEL_OIT.depthBoundsPipeline());
        RenderSystem.getCompiledPipeline(PIXEL_OIT.transmittancePipeline());
        RenderSystem.getCompiledPipeline(PIXEL_OIT.accumulatePipeline());
    }

    /**
     * The costume over the skin. The tint's alpha is the transformation progress: texels whose reveal mask is above it
     * are discarded, so the costume rises from the feet as progress grows.
     */
    public static RenderType costume(CrownStyle style, boolean slim) {
        String name = style.getSerializedName() + (slim ? "_slim" : "");
        return RenderTypes.entityCutoutDissolve(Scarlet.id("textures/entity/costume/" + name + ".png"), slim ? REVEAL_SLIM : REVEAL);
    }

    public static RenderType cape(CrownStyle style) {
        return RenderTypes.entityCutoutDissolve(Scarlet.id("textures/entity/costume/" + style.getSerializedName() + "_cape.png"), CAPE_REVEAL);
    }

    /**
     * Additive, emissive and untextured: geometry color is added to the scene, which reads as light. Glows placed in
     * the world go through {@link GlowPass} rather than using this directly.
     */
    public static RenderType glow() {
        return GLOW;
    }

    /**
     * Untextured, for tinting what lies behind magic. Placed in the world through {@link GlowPass#submitTint}, with
     * colors from {@link GlowPass#tint}.
     */
    public static RenderType tint() {
        return TINT;
    }

    /**
     * Untextured flat color. Placed in the world through {@link GlowPass#submitPixels}, with {@link Pixels}.
     */
    public static RenderType pixel() {
        return PIXEL;
    }
}
