package com.yashjit.scarlet.client.magic;

import com.yashjit.scarlet.client.anim.CastGestures;
import com.yashjit.scarlet.client.anim.PoseBlends;
import com.yashjit.scarlet.client.costume.CostumeView;
import com.yashjit.scarlet.client.hex.Founding;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * How strongly magic shows on a player's arms: the costume's steady glow, flaring on every strike, burning while the
 * shield is held or a mind is, smoldering while levitating and pouring out while founding a Hex. Magic only appears
 * while it is being used, so a crown alone shows nothing until you cast.
 */
public final class MagicVisuals {

    private MagicVisuals() {
    }

    public static float armIntensity(Player player, HumanoidArm arm, float partialTick) {
        CostumeView view = CostumeView.of(player, partialTick);
        float costume = view != null ? view.magicIntensity() : 0.0F;
        double now = player.level().getGameTime() + partialTick;
        float reach = Math.clamp(CastGestures.extension(player, arm, now), 0.0F, 1.0F);
        float cast = 0.35F * reach + CastGestures.flare(player, arm, now);
        PoseBlends.Blend blend = PoseBlends.of(player);
        float held = Math.max(Math.max(0.85F * blend.shield, 0.5F * blend.levitate), Math.max(0.9F * Founding.lift(player, now), 0.8F * blend.control));
        return Math.min(1.0F, Math.max(costume, Math.max(cast, held)));
    }
}
