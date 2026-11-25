package com.yashjit.scarlet.client.costume;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.config.ScarletClientConfig;
import com.yashjit.scarlet.platform.Services;
import com.yashjit.scarlet.player.ScarletPlayerData;
import com.yashjit.scarlet.player.Speed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

public final class SpeedLook {
    private static final Identifier WIDE = Scarlet.id("textures/entity/costume/speed.png");
    private static final Identifier SLIM = Scarlet.id("textures/entity/costume/speed_slim.png");

    public interface Pose {
        void scarlet$setSpeed(float goggles, float reach, float stride, boolean dressed);
        float scarlet$goggles();
        float scarlet$goggleReach();
        float scarlet$stride();
        boolean scarlet$speedSuit();
    }

    private SpeedLook() {}

    public static Identifier texture(boolean slim) {
        return slim ? SLIM : WIDE;
    }

    public static boolean dressed(Player player) {
        return Speed.wearsGoggles(player) && Services.PLAYER_DATA.get(player).speedSuit() && !player.isSpectator();
    }

    private static float phase(Player player, ScarletPlayerData data, float partialTick) {
        if (ScarletClientConfig.get().instantTransformations) return 1;
        return Ease.clamp01((player.level().getGameTime() - data.gogglesChangedAt() + partialTick) / Speed.CHANGE_TICKS);
    }

    public static void extract(Avatar avatar, AvatarRenderState state, float partialTick) {
        Pose pose = (Pose) state;
        if (!(avatar instanceof Player player) || !Speed.wearsGoggles(player) || player.isSpectator()) {
            pose.scarlet$setSpeed(0, 0, 0, false);
            return;
        }
        ScarletPlayerData data = Services.PLAYER_DATA.get(player);
        float phase = phase(player, data, partialTick);
        float slide = Ease.inOutCubic((phase - 0.32F) / 0.38F);
        float stride = data.gogglesDown() && player.isSprinting() && !player.isPassenger() && !player.isInWater()
                && !player.isFallFlying() && !player.getAbilities().flying ? Math.min(1, state.walkAnimationSpeed) : 0;
        pose.scarlet$setSpeed(data.gogglesDown() ? slide : 1 - slide, Ease.pulse(phase, 0.04F, 0.98F), stride, data.speedSuit());
    }

    public static float firstPersonReach() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || !Speed.wearsGoggles(player)) return 0;
        float phase = phase(player, Services.PLAYER_DATA.get(player), mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        return Ease.pulse(phase, 0.04F, 0.98F);
    }

    public static void pose(PlayerModel model, AvatarRenderState state) {
        Pose pose = (Pose) state;
        float stride = pose.scarlet$stride();
        if (stride > 0) {
            float cycle = Mth.sin(state.walkAnimationPos * 1.35F);
            model.rightLeg.xRot = Mth.lerp(stride, model.rightLeg.xRot, cycle * 1.12F);
            model.leftLeg.xRot = Mth.lerp(stride, model.leftLeg.xRot, -cycle * 1.12F);
            model.rightArm.xRot = Mth.lerp(stride, model.rightArm.xRot, -0.28F - cycle * 0.94F);
            model.leftArm.xRot = Mth.lerp(stride, model.leftArm.xRot, -0.28F + cycle * 0.94F);
            model.head.xRot -= stride * 0.17F;
        }
        float reach = pose.scarlet$goggleReach();
        if (reach > 0) {
            model.rightArm.xRot = Mth.lerp(reach, model.rightArm.xRot, -2.75F + model.head.xRot);
            model.rightArm.zRot = Mth.lerp(reach, model.rightArm.zRot, 0.19F);
        }
    }
}
