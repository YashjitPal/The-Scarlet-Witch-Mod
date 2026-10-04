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
import com.yashjit.scarlet.client.render.GlowPass;
import com.yashjit.scarlet.config.ScarletClientConfig;
import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.hex.Hex;
import com.yashjit.scarlet.hex.HexShape;
import com.yashjit.scarlet.hex.HexSnapshot;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.hex.RemnantSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * Draws the Hex over the finished world: the glitching wall from outside, and the era all around you from inside. The
 * homes fallen Hexes leave standing slip through the eras wherever they are seen from.
 *
 * <p>The shader works out where each pixel's surface is in the world from the depth buffer, so walls are cut
 * exactly by whatever stands in front of them. Vanilla wipes the world's depth to draw your hand unless a screen
 * effect needs it, so while a Hex is near we ask for it to be kept, and your hand is told apart by its own depth.
 */
public final class HexScreen {

    public static final int MAX_HEXES = 4;
    public static final int MAX_RIPPLES = 8;
    public static final int MAX_REMNANTS = 2;

    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Scarlet.id("post/hex"))
            .withBindGroupLayout(BindGroupLayout.builder()
                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("HandDepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("MagicSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("HexView", UniformType.UNIFORM_BUFFER)
                    .build())
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withLocation(Scarlet.id("pipeline/hex"))
            .build();

    // mat4, then the shapes and styles of each Hex, the camera and frame parameters, the ripples and how hard each
    // struck, where each Hex is parted, how far a new era has spread over the one around the camera, the view coming
    // through a wall, the boxes and slips of the homes fallen Hexes have left standing, and where the camera is in the
    // world's grid of blocks
    private static final int UNIFORM_SIZE = 64 + MAX_HEXES * 16 * 2 + 16 * 2 + MAX_RIPPLES * 16 + MAX_RIPPLES / 4 * 16 + MAX_HEXES * 16 + 16 + 16
            + MAX_REMNANTS * 16 * 3 + 16 + 16;
    /** The camera's place in the world's grid of blocks is passed modulo this, which keeps it precise as a float. */
    private static final double GRID_WRAP = 256.0;
    private static final float ENTER_RATE = 5.0F;
    private static final float LEAVE_RATE = 7.0F;
    /** Seconds the view takes to come through a wall, as the shader has it. */
    private static final float CROSS_SECONDS = 1.6F;
    /** Coming back through before the last crossing is done picks it up no further back than this far in. */
    private static final float CROSS_AGAIN = 0.3F;

    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW_ROTATION = new Matrix4f();
    private static Vec3 cameraPos = Vec3.ZERO;
    private static final Vector4f NO_MAGIC = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);
    private static @Nullable TextureTarget copy;
    private static @Nullable TextureTarget magic;
    private static @Nullable MappableRingBuffer uniforms;
    private static boolean broken;
    private static boolean wanted;
    private static float inside;
    private static Era insideEra = Era.PRESENT;
    private static long lastNanos;
    /** Whether the view was inside a Hex last frame, in which world, and when and which way it last came through a wall. */
    private static boolean wasInside;
    private static @Nullable Object seenLevel;
    private static double crossedAt = -1.0E9;
    private static boolean crossedIn;

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
        try {
            draw(main, hand);
        } finally {
            GlowPass.endFrame();
        }
    }

    private static void draw(RenderTarget main, RenderTarget hand) {
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
        float crossing = cross(minecraft, around != null, now);
        boolean crossingNow = crossing < CROSS_SECONDS;
        inside = Ease.damp(inside, around != null ? 1.0F : 0.0F, around != null ? ENTER_RATE : LEAVE_RATE, seconds);
        if (around == null && inside < 0.002F) {
            inside = 0.0F;
        }
        List<RemnantSnapshot> remnants = remnantsNear(now, reach);
        wanted = !hexes.isEmpty() || !remnants.isEmpty();
        if (hexes.isEmpty() && inside == 0.0F && !crossingNow && remnants.isEmpty()) {
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
        copy = sized(copy, "Scarlet hex", width, height);
        magic = sized(magic, "Scarlet magic", width, height);
        if (uniforms == null) {
            uniforms = new MappableRingBuffer(() -> "Scarlet hex view", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UNIFORM_SIZE);
        }
        writeUniforms(uniforms, hexes, around, width, height, now, crossingNow ? crossing : -1.0F, remnants);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(main.getColorTexture(), copy.getColorTexture(), 0, 0, 0, 0, 0, width, height);
        encoder.clearColorTexture(magic.getColorTexture(), NO_MAGIC);
        if (inside > 0.0F || crossingNow || !remnants.isEmpty()) {
            // the magic's own light, so it can stay scarlet while the era drains the world around it
            GlowPass.drawMagic(magic.getColorTextureView(), main.getDepthTextureView(), PROJECTION, VIEW_ROTATION);
        }
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        try (RenderPass pass = encoder.createRenderPass(() -> "Scarlet hex", main.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(compiled));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("HexView", uniforms.currentBuffer());
            pass.setUniform("InSampler", copy.getColorTextureView(), linear);
            pass.setUniform("DepthSampler", main.getDepthTextureView(), nearest);
            pass.setUniform("HandDepthSampler", hand.getDepthTextureView(), nearest);
            pass.setUniform("MagicSampler", magic.getColorTextureView(), nearest);
            pass.draw(3, 1, 0, 0);
        }
        uniforms.rotate();
    }

    /**
     * Notices the view coming through a wall, walking or flying through it or the wall sweeping over it as the Hex
     * grows, shrinks or falls, and starts the picture coming through with it, with the sound of it: the hum of the field
     * and the crackle of the snow, and the picture humming into tune. Arriving in a world, or in another one, is not
     * coming through, and nor is a caster's own Hex spreading out from them as they cast it: the era just comes over
     * them with it.
     *
     * @return seconds since the view last came through
     */
    private static float cross(Minecraft minecraft, boolean isInside, double now) {
        if (minecraft.level != seenLevel) {
            seenLevel = minecraft.level;
            wasInside = isInside;
            crossedAt = -1.0E9;
        } else if (isInside != wasInside) {
            wasInside = isInside;
            if (isInside && castingAround(minecraft, now)) {
                return (float) ((now - crossedAt) / 20.0);
            }
            double since = (now - crossedAt) / 20.0;
            crossedAt = since < CROSS_SECONDS ? now - Math.min(since, CROSS_AGAIN * CROSS_SECONDS) * 20.0 : now;
            crossedIn = isInside;
            float volume = ScarletClientConfig.get().reduceFlashing ? 0.5F : 0.8F;
            SoundManager sounds = minecraft.getSoundManager();
            sounds.play(SimpleSoundInstance.forUI(SoundEvents.ILLUSIONER_MIRROR_MOVE, isInside ? 0.7F : 0.85F, volume));
            sounds.playDelayed(SimpleSoundInstance.forUI(SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1.7F, volume * 0.7F), 6);
            sounds.playDelayed(SimpleSoundInstance.forUI(SoundEvents.BEACON_POWER_SELECT, isInside ? 1.6F : 1.3F, volume * 0.6F), 14);
        }
        return (float) ((now - crossedAt) / 20.0);
    }

    /**
     * Whether the view is in the caster's own Hex while it is still being cast, rising or spreading out from them.
     */
    private static boolean castingAround(Minecraft minecraft, double now) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return false;
        }
        for (HexSnapshot hex : Hexes.clientHexes()) {
            Hex.Phase phase = hex.phaseValue();
            if (hex.caster().equals(player.getUUID()) && (phase == Hex.Phase.FOUNDING || phase == Hex.Phase.SPREADING)
                    && HexShape.contains(hex.center(), HexClient.drawnRadius(hex, now) + 1.0F, cameraPos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The homes fallen Hexes have left standing near enough to be seen, nearest first.
     */
    private static List<RemnantSnapshot> remnantsNear(double now, double reach) {
        List<RemnantSnapshot> near = new ArrayList<>();
        for (RemnantSnapshot remnant : Hexes.clientRemnants()) {
            if (remnant.glitch(now) > 0.0F && remnant.middle().distanceToSqr(cameraPos) < reach * reach) {
                near.add(remnant);
            }
        }
        near.sort(Comparator.comparingDouble(remnant -> remnant.middle().distanceToSqr(cameraPos)));
        return near.size() > MAX_REMNANTS ? near.subList(0, MAX_REMNANTS) : near;
    }

    private static TextureTarget sized(@Nullable TextureTarget target, String name, int width, int height) {
        if (target == null) {
            return new TextureTarget(name, width, height, GpuFormat.RGBA8_UNORM, null);
        }
        if (target.width != width || target.height != height) {
            target.resize(width, height);
        }
        return target;
    }

    /**
     * @param crossing seconds since the view came through a wall, below 0 once it is all the way through
     */
    private static void writeUniforms(MappableRingBuffer buffer, List<HexClient.Shown> hexes, HexClient.@Nullable Shown around, int width,
                                      int height, double now, float crossing, List<RemnantSnapshot> remnants) {
        float time = (float) (now / 20.0 % 3600.0);
        List<HexClient.Ripple> ripples = HexClient.ripples(now);
        Matrix4f inverse = new Matrix4f(PROJECTION).mul(VIEW_ROTATION).invert();
        int aroundIndex = around == null ? -1 : hexes.indexOf(around);
        float tv = insideEra.ordinal() <= Era.TWO_THOUSANDS.ordinal() ? 1.0F : 0.0F;
        ScarletClientConfig config = ScarletClientConfig.get();
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
                    builder.putVec4(hex.era().ordinal(), hex.flare(), hex.warning(), hex.channel());
                } else {
                    builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
                }
            }
            builder.putVec4(aroundIndex + 1, inside, insideEra.ordinal(), hexes.size());
            builder.putVec4(time, width, height, tv);
            float[] strengths = new float[MAX_RIPPLES];
            for (int i = 0; i < MAX_RIPPLES; i++) {
                if (i < ripples.size()) {
                    HexClient.Ripple ripple = ripples.get(ripples.size() - 1 - i);
                    Vec3 at = ripple.at().subtract(cameraPos);
                    builder.putVec4((float) at.x, (float) at.y, (float) at.z, (float) (now - ripple.start()));
                    strengths[i] = ripple.strength();
                } else {
                    builder.putVec4(0.0F, 0.0F, 0.0F, -1.0F);
                }
            }
            for (int i = 0; i < MAX_RIPPLES; i += 4) {
                builder.putVec4(strengths[i], strengths[i + 1], strengths[i + 2], strengths[i + 3]);
            }
            for (int i = 0; i < MAX_HEXES; i++) {
                HexClient.Shown hex = i < hexes.size() ? hexes.get(i) : null;
                if (hex != null && hex.tearAt() != null && hex.opening() >= 0.0F) {
                    Vec3 tear = hex.tearAt().subtract(cameraPos);
                    builder.putVec4((float) tear.x, (float) tear.y, (float) tear.z, hex.opening());
                } else {
                    builder.putVec4(0.0F, 0.0F, 0.0F, -1.0F);
                }
            }
            if (around != null && around.changingEra()) {
                builder.putVec4(around.previousEra().ordinal(), around.eraFront(), Hexes.ERA_BAND, 1.0F);
            } else {
                builder.putVec4(0.0F, 0.0F, Hexes.ERA_BAND, 0.0F);
            }
            builder.putVec4(crossing, crossedIn ? 1.0F : 0.0F, config.reduceFlashing ? 0.45F : 1.0F, config.reduceCameraShake ? 0.0F : 1.0F);
            // a little over the box around each home, so its own outermost faces are never left out
            for (int i = 0; i < MAX_REMNANTS; i++) {
                if (i < remnants.size()) {
                    RemnantSnapshot remnant = remnants.get(i);
                    Vec3 low = Vec3.atLowerCornerOf(remnant.min()).subtract(cameraPos);
                    builder.putVec4((float) low.x - 0.1F, (float) low.y - 0.1F, (float) low.z - 0.1F,
                            remnant.glitch(now) * (config.reduceFlashing ? 0.5F : 1.0F));
                } else {
                    builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
                }
            }
            for (int i = 0; i < MAX_REMNANTS; i++) {
                if (i < remnants.size()) {
                    RemnantSnapshot remnant = remnants.get(i);
                    Vec3 high = Vec3.atLowerCornerOf(remnant.max()).add(1.0, 1.0, 1.0).subtract(cameraPos);
                    builder.putVec4((float) high.x + 0.1F, (float) high.y + 0.1F, (float) high.z + 0.1F, remnant.id() % 97 * 1.7F);
                } else {
                    builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
                }
            }
            for (int i = 0; i < MAX_REMNANTS; i++) {
                Remnants.Phase phase = i < remnants.size() ? Remnants.phase(remnants.get(i), now) : null;
                if (phase != null) {
                    // as it was built, it is seen as it is, as the present is
                    Era before = phase.before() != null ? phase.before() : Era.PRESENT;
                    builder.putVec4(before.ordinal(), phase.after().ordinal(), phase.sweep(), phase.start());
                } else {
                    builder.putVec4(Era.PRESENT.ordinal(), Era.PRESENT.ordinal(), 0.0F, 0.0F);
                }
            }
            builder.putVec4(wrapped(cameraPos.x), wrapped(cameraPos.y), wrapped(cameraPos.z), 0.0F);
            float partial = (float) (now - Math.floor(now));
            builder.putVec4(RewindClient.amount(partial), RewindClient.speed(now), RewindClient.seconds(now), 0.0F);
        }
    }

    private static float wrapped(double coordinate) {
        double block = Math.floor(coordinate);
        return (float) (Math.floorMod((long) block, (long) GRID_WRAP) + (coordinate - block));
    }
}
