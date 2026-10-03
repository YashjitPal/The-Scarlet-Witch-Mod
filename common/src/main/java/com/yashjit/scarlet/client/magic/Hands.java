package com.yashjit.scarlet.client.magic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.client.fx.ScarletFx;
import com.yashjit.scarlet.magic.SpellCasts;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Where each player's palms were when they were last drawn, so effects can leave the real hands of the animated
 * model. Falls back to an estimate from the view while a player is not drawn, such as yourself in first person.
 */
public final class Hands {

    /** How long after your hand was last drawn in first person it is still taken to show there. */
    private static final long FIRST_PERSON_NANOS = 100_000_000L;

    private static final Int2ObjectMap<Recorded> RECORDED = new Int2ObjectOpenHashMap<>();
    /** Where your hands last showed in first person, right then left: left, up and forward of the camera. */
    private static final @Nullable Vector3f[] FIRST_PERSON = new Vector3f[2];
    private static final long[] FIRST_PERSON_AT = new long[2];

    private Hands() {
    }

    static void record(Entity entity, Vec3 right, Vec3 left) {
        RECORDED.put(entity.getId(), new Recorded(right, left, entity.level().getGameTime()));
    }

    /**
     * Where a dreamwalker's body last showed its palms, if it was drawn lately.
     */
    public static @Nullable Vec3 drawnPalm(Entity entity, HumanoidArm arm) {
        Recorded recorded = RECORDED.get(entity.getId());
        if (recorded == null || entity.level().getGameTime() - recorded.gameTime() > 2) {
            return null;
        }
        return arm == HumanoidArm.RIGHT ? recorded.right() : recorded.left();
    }

    public static Vec3 palm(Player player, HumanoidArm arm) {
        Recorded recorded = RECORDED.get(player.getId());
        if (recorded != null && !ScarletFx.isFirstPersonViewOf(player) && player.level().getGameTime() - recorded.gameTime() <= 2) {
            return arm == HumanoidArm.RIGHT ? recorded.right() : recorded.left();
        }
        return SpellCasts.handPosition(player, arm != player.getMainArm());
    }

    /**
     * Called as your arm is drawn in first person, in the pose it is drawn in: from the camera along the world's axes.
     */
    public static void recordFirstPerson(boolean right, boolean slim, PoseStack poseStack, ModelPart arm) {
        poseStack.pushPose();
        arm.translateAndRotate(poseStack);
        float axisX = (right ? -1.0F : 1.0F) * (slim ? 0.5F : 1.0F) / 16.0F;
        Vector3f palm = poseStack.last().pose().transformPosition(axisX, MagicLayer.PALM_Y, 0.0F, new Vector3f());
        poseStack.popPose();
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        int i = right ? 0 : 1;
        FIRST_PERSON[i] = new Vector3f(palm.dot(camera.leftVector()), palm.dot(camera.upVector()), palm.dot(camera.forwardVector()));
        FIRST_PERSON_AT[i] = System.nanoTime();
    }

    /**
     * The same for a hand flung up and out, casting a beam. In first person, where that hand shows as it is drawn, or
     * if it is not, an estimate up toward the crosshair and off to its side.
     */
    public static Vec3 raisedPalm(Player player, HumanoidArm arm, float partialTick) {
        if (!ScarletFx.isFirstPersonViewOf(player)) {
            return palm(player, arm);
        }
        int i = arm == HumanoidArm.RIGHT ? 0 : 1;
        Vector3f shown = FIRST_PERSON[i];
        if (shown != null && System.nanoTime() - FIRST_PERSON_AT[i] < FIRST_PERSON_NANOS) {
            // hands are drawn in a view of their own, so it lands where the hand shows at its depth in the world's
            Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
            float widen = (float) (Math.tan(Math.toRadians(camera.getFov()) / 2.0) / Math.tan(Math.toRadians(Camera.BASE_HUD_FOV) / 2.0));
            Vector3fc left = camera.leftVector();
            Vector3fc up = camera.upVector();
            Vector3fc forward = camera.forwardVector();
            float l = shown.x * widen;
            float u = shown.y * widen;
            return camera.position().add(left.x() * l + up.x() * u + forward.x() * shown.z, left.y() * l + up.y() * u + forward.y() * shown.z,
                    left.z() * l + up.z() * u + forward.z() * shown.z);
        }
        double side = arm == HumanoidArm.RIGHT ? 1.0 : -1.0;
        Vec3 look = player.getViewVector(partialTick);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0E-4) {
            double yaw = Math.toRadians(player.getViewYRot(partialTick));
            right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        }
        right = right.normalize();
        Vec3 up = right.cross(look).normalize();
        return player.getEyePosition(partialTick).add(right.scale(0.3 * side)).add(up.scale(-0.17)).add(look.scale(0.62));
    }

    public static void prune(ClientLevel level) {
        RECORDED.int2ObjectEntrySet().removeIf(entry -> level.getEntity(entry.getIntKey()) == null);
    }

    private record Recorded(Vec3 right, Vec3 left, long gameTime) {
    }
}
