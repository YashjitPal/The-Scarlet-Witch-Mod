package com.yashjit.scarlet.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.decor.ClockBlockEntity;
import com.yashjit.scarlet.decor.DecorBlock;
import com.yashjit.scarlet.decor.EraDecor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A wall clock's hands, turning with the time: the hour of the sky over the clock, which is a Hex's own where its caster
 * has set one, racing round with it when the hour is changed; and spinning wildly where there is no day to tell.
 */
public final class ClockRenderer implements BlockEntityRenderer<ClockBlockEntity, ClockRenderer.State> {

    private static final RenderType HANDS = RenderTypes.entitySolid(Scarlet.id("textures/block/decor/wall_clock/hands.png"));
    private static final int DAY = 24000;
    /** How far from its pivot each hand reaches back the other way, as a share of its length. */
    private static final float TAIL = 0.22F;
    /** How the dial of each era lies, in pixels on the clock facing north, from the 1950s. */
    private static final Dial[] DIALS = {
            // the atomic sunburst's little cream face
            new Dial(8.0F, 8.0F, 14.75F, 1.3F, 2.1F, 0.45F, 0x1C1A19),
            // the ball clock's long hands, out over its spokes
            new Dial(8.0F, 8.0F, 14.75F, 3.4F, 5.0F, 0.4F, 0x1C1A19),
            // the pendulum clock's face, up over its glass
            new Dial(8.0F, 11.0F, 14.0F, 1.5F, 2.5F, 0.45F, 0x1C1A19),
            new Dial(8.0F, 8.0F, 15.0F, 2.2F, 3.3F, 0.55F, 0x2E9C9A),
            new Dial(8.0F, 8.0F, 15.0F, 2.2F, 3.3F, 0.55F, 0x1F1F22),
            new Dial(8.0F, 8.0F, 15.0F, 3.0F, 4.6F, 0.6F, 0x1A1A1C)
    };
    private static final int PIN = 0xC8303A;

    public ClockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ClockBlockEntity clock, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(clock, state, partialTick, camera, breaking);
        BlockState block = clock.getBlockState();
        state.facing = block.getValue(DecorBlock.FACING).toYRot();
        state.dial = DIALS[Math.min(block.getValue(EraDecor.ERA).ordinal(), DIALS.length - 1)];
        Level level = clock.getLevel();
        if (level == null) {
            state.hour = 305.0F;
            state.minute = 60.0F;
        } else if (level.dimensionType().defaultClock().isEmpty()) {
            // no day here to tell: the hands spin, each its own way
            float t = level.getGameTime() + partialTick + (clock.getBlockPos().asLong() & 0xFF);
            state.minute = t * 23.0F % 360.0F;
            state.hour = 360.0F - t * 9.0F % 360.0F;
        } else {
            // the day starts at six in the morning
            long day = Math.floorMod(level.getDefaultClockTime(), DAY);
            state.minute = day % 1000 * 0.36F;
            state.hour = (float) ((day / 1000.0 + 6.0) % 12.0) * 30.0F;
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Dial dial = state.dial;
        poseStack.pushPose();
        // turned with the clock about the middle of its block, then measured in pixels, as its model is
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.rotateDegrees(Axis.YP, 180.0F - state.facing);
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        poseStack.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
        int light = state.lightCoords;
        collector.submitCustomGeometry(poseStack, HANDS, (pose, buffer) -> {
            hand(buffer, pose, dial, state.hour, dial.hour(), dial.width() * 1.25F, dial.front() - 0.03F, dial.color(), light);
            hand(buffer, pose, dial, state.minute, dial.minute(), dial.width(), dial.front() - 0.06F, dial.color(), light);
            float pin = dial.width() * 0.75F;
            quad(buffer, pose, dial.x() + pin, dial.y() - pin, dial.x() - pin, dial.y() - pin, dial.x() - pin, dial.y() + pin,
                    dial.x() + pin, dial.y() + pin, dial.front() - 0.09F, PIN, light);
        });
        poseStack.popPose();
    }

    /**
     * A hand at {@code degrees} round from twelve, clockwise as you face the clock: broad at its pivot, narrowing to its
     * tip, with a little tail back past the pivot.
     */
    private static void hand(VertexConsumer buffer, PoseStack.Pose pose, Dial dial, float degrees, float length, float width, float z, int color,
                             int light) {
        float a = degrees * Mth.DEG_TO_RAD;
        // facing the clock from the north, its right is to the west
        float alongX = -Mth.sin(a);
        float alongY = Mth.cos(a);
        float acrossX = alongY;
        float acrossY = -alongX;
        float base = width * 0.5F;
        float tip = width * 0.3F;
        float tailX = dial.x() - alongX * length * TAIL;
        float tailY = dial.y() - alongY * length * TAIL;
        float tipX = dial.x() + alongX * length;
        float tipY = dial.y() + alongY * length;
        quad(buffer, pose, tailX - acrossX * base, tailY - acrossY * base, tailX + acrossX * base, tailY + acrossY * base,
                tipX + acrossX * tip, tipY + acrossY * tip, tipX - acrossX * tip, tipY - acrossY * tip, z, color, light);
    }

    /** A flat quad on the clock's face, drawn from both sides so it shows whichever way round its corners go. */
    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float x2, float y2,
                             float x3, float y3, float z, int color, int light) {
        vertex(buffer, pose, x0, y0, z, 0.0F, 0.0F, color, light);
        vertex(buffer, pose, x1, y1, z, 1.0F, 0.0F, color, light);
        vertex(buffer, pose, x2, y2, z, 1.0F, 1.0F, color, light);
        vertex(buffer, pose, x3, y3, z, 0.0F, 1.0F, color, light);
        vertex(buffer, pose, x3, y3, z, 0.0F, 1.0F, color, light);
        vertex(buffer, pose, x2, y2, z, 1.0F, 1.0F, color, light);
        vertex(buffer, pose, x1, y1, z, 1.0F, 0.0F, color, light);
        vertex(buffer, pose, x0, y0, z, 0.0F, 0.0F, color, light);
    }

    /** A corner of a hand, lit as though it faced up, so a hand shows its own colour whichever way the clock faces. */
    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int color, int light) {
        buffer.addVertex(pose, x, y, z)
                .setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /**
     * Where a dial's hands turn and how they look: their pivot across and up, the face they lie on, how long the hour and
     * minute hands are and how wide, and their colour.
     */
    private record Dial(float x, float y, float front, float hour, float minute, float width, int color) {
    }

    public static final class State extends BlockEntityRenderState {
        Dial dial = DIALS[DIALS.length - 1];
        float facing;
        float hour;
        float minute;
    }
}
