package com.yashjit.scarlet.client.anim;

import com.yashjit.scarlet.darkhold.Dreamwalk;
import com.yashjit.scarlet.entity.DreamBody;
import com.yashjit.scarlet.magic.Magic;
import com.yashjit.scarlet.magic.MagicState;
import com.yashjit.scarlet.magic.Spell;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Sitting down cross-legged to dreamwalk and rising a little into the air, and getting up again: how far into it a
 * player or a left body is, worked out from synced state so every client sees the same. A body is always all the way
 * in. A player sits over the first half of the rise and lifts off over the rest, and gets up again quickly, the same way
 * backward, from however far they had got.
 */
public final class Meditation {

    /** How far the body drops to sit on the ground with its legs crossed before it, in blocks. */
    public static final float SIT_DROP = 0.586F;
    /** How far it then rises above the ground. */
    public static final float HOVER = 0.3F;
    /** Standing eye height, from which the pose lowers the eyes. */
    private static final float STANDING_EYES = 1.62F;
    private static final float STAND_TICKS = 10.0F;
    private static final float BOB = 0.05F;

    private Meditation() {
    }

    /**
     * How far down into the pose, 0 to 1.
     */
    public static float sit(Entity entity, double now) {
        return Ease.inOutCubic(Ease.clamp01(rise(entity, now) / 0.45F));
    }

    /**
     * How far up off the ground, 0 to 1.
     */
    public static float lift(Entity entity, double now) {
        return Ease.inOutCubic(Ease.clamp01((rise(entity, now) - 0.4F) / 0.6F));
    }

    /**
     * How far the pose lowers or raises the whole body from where it stands, in blocks, with the slow bob of hovering.
     */
    public static float offset(Entity entity, double now) {
        float rise = rise(entity, now);
        float sit = Ease.inOutCubic(Ease.clamp01(rise / 0.45F));
        float lift = Ease.inOutCubic(Ease.clamp01((rise - 0.4F) / 0.6F));
        return -SIT_DROP * sit + (HOVER + Mth.sin((float) now * 0.06F + entity.getId()) * BOB) * lift;
    }

    /**
     * Where the eyes are above the feet in the pose.
     */
    public static float eyes(Entity entity, double now) {
        return STANDING_EYES + offset(entity, now);
    }

    /**
     * How far through the rise, 0 to 1: forward while the spell gathers, backward once it is over.
     */
    public static float rise(Entity entity, double now) {
        if (entity instanceof DreamBody) {
            return 1.0F;
        }
        if (!(entity instanceof Player player)) {
            return 0.0F;
        }
        MagicState state = Magic.state(player);
        if (state.channeling(Spell.DREAMWALK)) {
            return progress(state, now);
        }
        double ended = state.readyAt(Spell.DREAMWALK) - Spell.DREAMWALK.cooldown();
        double since = now - ended;
        if (since < 0.0 || since >= STAND_TICKS) {
            return 0.0F;
        }
        return progress(state, ended) * (1.0F - Ease.inOutCubic((float) (since / STAND_TICKS)));
    }

    private static float progress(MagicState state, double now) {
        return (float) Math.clamp((now - state.channelStart()) / Dreamwalk.RISE_TICKS, 0.0, 1.0);
    }
}
