package com.yashjit.scarlet.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.client.fx.ShieldFx;
import com.yashjit.scarlet.crown.CrownItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.jspecify.annotations.Nullable;

/**
 * Your own casting gestures in first person. Arms swing about their shoulders, so they stay attached to you.
 *
 * <ul>
 *     <li>A striking arm reaches toward the crosshair.</li>
 *     <li>Holding the shield raises both palms in front of you, straining against it.</li>
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
        float reach = Math.max(Math.min(extension(arm), 1.2F), shield());
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
        long nanos = System.nanoTime();
        float seconds = lastNanos == 0 ? 0.0F : Math.min(0.1F, (nanos - lastNanos) / 1.0E9F);
        lastNanos = nanos;
        if (player == null) {
            return;
        }
        boolean wanted = debugSwing != null || shield() > 0.02F
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
