package com.yashjit.scarlet.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.client.fx.ShieldFx;
import com.yashjit.scarlet.client.fx.ShockwaveFx;
import com.yashjit.scarlet.crown.CrownItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.HumanoidArm;
import org.jspecify.annotations.Nullable;

/**
 * Your own casting gestures in first person. Arms swing about their shoulders, so they stay attached to you.
 *
 * <ul>
 *     <li>A striking arm reaches toward the crosshair.</li>
 *     <li>Holding the shield raises both palms in front of you, straining against it.</li>
 *     <li>Gathering a Shockwave draws both hands in together; it bursts out of them flung wide.</li>
 *     <li>Reading the Darkhold holds both hands up either side of it as it floats before you.</li>
 *     <li>The empty off hand, which vanilla never shows, rises into view while you cast and sinks away when you
 *     stop.</li>
 * </ul>
 */
public final class FirstPersonGestures {

    private static final double OFF_HAND_LINGER = 30.0;

    // swing about the shoulder at full reach, in radians: x raises the hand toward the crosshair, y turns the palm
    // forward, negative z draws the hands in toward the middle; y and z mirror between the arms
    private static final float REACH_X = -0.55F;
    private static final float REACH_Y = 0.2F;
    private static final float REACH_Z = -0.12F;
    private static final float PULL_X = 0.3F;
    private static final float SHIELD_X = -0.5F;
    private static final float SHIELD_Y = 0.25F;
    private static final float SHIELD_Z = -0.24F;
    private static final float RECOIL_X = 0.3F;
    private static final float HOLD_X = -0.6F;
    private static final float HOLD_Y = 0.2F;
    private static final float HOLD_Z = -0.08F;
    private static final float TEAR_X = -0.6F;
    private static final float TEAR_IN = -0.2F;
    private static final float TEAR_OUT = 0.75F;
    private static final float GATHER_X = -0.45F;
    private static final float GATHER_Y = 0.15F;
    private static final float GATHER_Z = -0.4F;
    private static final float FLING_X = -0.3F;
    private static final float FLING_Z = 0.6F;
    private static final float BEAM_X = -0.72F;
    private static final float BEAM_Y = 0.12F;
    private static final float BEAM_Z = 0.06F;
    private static final float RAISE_X = -0.85F;
    private static final float RAISE_Y = 0.15F;
    private static final float RAISE_Z = 0.12F;
    private static final float READ_X = -0.3F;
    private static final float READ_Y = 0.1F;
    private static final float READ_Z = 0.22F;

    /**
     * Development only: holds both arms at full reach with this swing (x, y, z) instead of the real gesture.
     */
    public static float @Nullable [] debugSwing;

    private static float offHandPresence;
    private static long lastNanos;

    private FirstPersonGestures() {
    }

    /**
     * Applied just before vanilla positions an empty-handed arm: a slight push forward, toward the crosshair.
     */
    public static void transform(PoseStack poseStack, HumanoidArm arm) {
        float reach = Math.max(Math.min(extension(arm), 1.2F), Math.max(Math.max(shield(), Math.max(hold(), tear())), Math.max(gather(), fling())));
        reach = Math.max(reach, Math.max(beam(arm), Math.max(raise(), read())));
        if (reach <= 0.0F) {
            return;
        }
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        poseStack.translate(-side * 0.04F * reach, 0.0F, -0.08F * reach);
    }

    /**
     * Applied to the arm part after vanilla has posed it for first person and before it is drawn.
     */
    public static void poseArm(PlayerModel model, ModelPart arm) {
        boolean right = arm == model.rightArm;
        float side = right ? 1.0F : -1.0F;
        float[] debug = debugSwing;
        if (debug != null) {
            arm.xRot += debug[0];
            arm.yRot += side * debug[1];
            arm.zRot += side * debug[2];
            return;
        }
        float shield = shield();
        if (shield > 0.0F) {
            LocalPlayer player = Minecraft.getInstance().player;
            double now = now();
            float tremble = Mth.sin((float) now * 1.9F) * 0.012F * side;
            float recoil = player == null ? 0.0F : ShieldFx.recoil(player, now);
            arm.xRot += (SHIELD_X + tremble + RECOIL_X * recoil) * shield;
            arm.yRot += side * SHIELD_Y * shield;
            arm.zRot += side * SHIELD_Z * shield;
        }
        float hold = hold();
        if (hold > 0.0F) {
            float tremble = Mth.sin((float) now() * 2.2F) * 0.015F * side;
            arm.xRot += (HOLD_X + tremble + (right ? -0.06F : 0.06F)) * hold;
            arm.yRot += side * HOLD_Y * hold;
            arm.zRot += side * HOLD_Z * hold;
        }
        float tear = tear();
        if (tear > 0.0F) {
            LocalPlayer player = Minecraft.getInstance().player;
            float spread = player == null ? 0.0F : PoseBlends.of(player).spread;
            float tremble = Mth.sin((float) now() * 2.4F) * 0.02F * side;
            arm.xRot += (TEAR_X + tremble) * tear;
            arm.zRot += side * (TEAR_IN + TEAR_OUT * spread) * tear;
        }
        float gather = gather();
        if (gather > 0.0F) {
            float tremble = Mth.sin((float) now() * 2.6F) * 0.02F * side;
            arm.xRot += (GATHER_X + tremble) * gather;
            arm.yRot += side * GATHER_Y * gather;
            arm.zRot += side * GATHER_Z * gather;
        }
        float fling = fling();
        if (fling > 0.0F) {
            arm.xRot += FLING_X * fling;
            arm.zRot += side * FLING_Z * fling;
        }
        float beam = beam(right ? HumanoidArm.RIGHT : HumanoidArm.LEFT);
        if (beam > 0.0F) {
            float tremble = Mth.sin((float) now() * 2.3F) * 0.015F * side;
            arm.xRot += (BEAM_X + tremble) * beam;
            arm.yRot += side * BEAM_Y * beam;
            arm.zRot += side * BEAM_Z * beam;
        }
        float raise = raise();
        if (raise > 0.0F) {
            float tremble = Mth.sin((float) now() * 2.0F) * 0.015F * side;
            arm.xRot += (RAISE_X + tremble) * raise;
            arm.yRot += side * RAISE_Y * raise;
            arm.zRot += side * RAISE_Z * raise;
        }
        float read = read();
        if (read > 0.0F) {
            // weaving slowly, a beat apart, as they hold it up
            float weave = Mth.sin((float) now() * 0.09F + (right ? 0.0F : 1.7F)) * 0.04F;
            arm.xRot += (READ_X + weave) * read;
            arm.yRot += side * READ_Y * read;
            arm.zRot += side * READ_Z * read;
        }
        float extension = extension(right ? HumanoidArm.RIGHT : HumanoidArm.LEFT);
        if (extension == 0.0F) {
            return;
        }
        float reach = Math.clamp(extension, 0.0F, 1.2F);
        float pull = Math.clamp(-extension, 0.0F, 0.35F);
        arm.xRot += REACH_X * reach + PULL_X * pull;
        arm.yRot += side * REACH_Y * reach;
        arm.zRot += side * REACH_Z * reach;
    }

    /**
     * Runs after vanilla has drawn the hands.
     */
    public static void offHand(ArmDrawer drawer, HumanoidArm offArm, boolean offHandEmpty) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        long nanos = Util.getNanos();
        float seconds = lastNanos == 0 ? 0.0F : Math.min(0.1F, (nanos - lastNanos) / 1.0E9F);
        lastNanos = nanos;
        if (player == null) {
            return;
        }
        boolean wanted = debugSwing != null || shield() > 0.02F || hold() > 0.02F || tear() > 0.02F || gather() > 0.02F || fling() > 0.02F
                || raise() > 0.02F || read() > 0.02F
                || offHandEmpty && CrownItem.isWearingCrown(player) && CastGestures.sinceLastStrike(player, now()) < OFF_HAND_LINGER;
        offHandPresence = Ease.damp(offHandPresence, wanted ? 1.0F : 0.0F, wanted ? 10.0F : 4.0F, seconds);
        if (!offHandEmpty || offHandPresence < 0.01F) {
            return;
        }
        drawer.draw(1.0F - Ease.outCubic(offHandPresence), offArm);
    }

    private static float shield() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null || debugSwing != null ? 0.0F : PoseBlends.of(player).shield;
    }

    private static float gather() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null || debugSwing != null ? 0.0F : ShockwaveFx.gather(player, now());
    }

    /**
     * Reaching out to hold something, with Telekinesis or a mind with Mind Control.
     */
    private static float hold() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || debugSwing != null) {
            return 0.0F;
        }
        PoseBlends.Blend blend = PoseBlends.of(player);
        return Math.max(blend.hold, blend.control);
    }

    private static float tear() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null || debugSwing != null ? 0.0F : PoseBlends.of(player).tear;
    }

    /**
     * The main arm flung out at what its beam of magic paints.
     */
    private static float beam(HumanoidArm arm) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null || debugSwing != null || arm != player.getMainArm() ? 0.0F : PoseBlends.of(player).beam;
    }

    /**
     * Both arms raised to a home going up far off.
     */
    private static float raise() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null || debugSwing != null ? 0.0F : PoseBlends.of(player).raise;
    }

    /**
     * Both hands held up either side of the Darkhold floating before you as you read it.
     */
    private static float read() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null || debugSwing != null ? 0.0F : PoseBlends.of(player).read;
    }

    private static float fling() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null || debugSwing != null ? 0.0F : ShockwaveFx.fling(player, now());
    }

    private static float extension(HumanoidArm arm) {
        if (debugSwing != null) {
            return 1.0F;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? 0.0F : CastGestures.extension(player, arm, now());
    }

    private static double now() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        return player == null ? 0.0 : player.level().getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    @FunctionalInterface
    public interface ArmDrawer {
        /**
         * @param lowered vanilla's equip offset: 0 in view, 1 below the screen
         */
        void draw(float lowered, HumanoidArm arm);
    }
}
