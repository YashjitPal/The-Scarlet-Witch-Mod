package com.yashjit.scarlet.client.anim;

import com.yashjit.scarlet.magic.Magic;
import com.yashjit.scarlet.magic.MagicState;
import com.yashjit.scarlet.magic.Spell;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;

/**
 * How far each player has eased into their held poses: raising the shield, levitating, and leaning into flight. Every
 * value approaches its target at its own rate, so poses settle in and out instead of snapping.
 */
public final class PoseBlends {

    /** Degrees of forward lean at full flying speed. */
    public static final float MAX_LEAN = 22.0F;

    private static final Int2ObjectMap<Blend> BLENDS = new Int2ObjectOpenHashMap<>();

    private PoseBlends() {
    }

    public static Blend of(Player player) {
        Blend blend = BLENDS.computeIfAbsent(player.getId(), id -> new Blend());
        long nanos = System.nanoTime();
        float seconds = blend.lastNanos == 0 ? 0.0F : Math.min(0.1F, (nanos - blend.lastNanos) / 1.0E9F);
        blend.lastNanos = nanos;
        MagicState state = Magic.state(player);
        boolean shielding = state.channeling(Spell.CHAOS_SHIELD);
        boolean levitating = state.levitating();
        blend.shield = Ease.damp(blend.shield, shielding ? 1.0F : 0.0F, shielding ? 14.0F : 9.0F, seconds);
        blend.levitate = Ease.damp(blend.levitate, levitating ? 1.0F : 0.0F, levitating ? 5.0F : 6.0F, seconds);
        double yaw = Math.toRadians(player.yBodyRot);
        double forward = -(player.getX() - player.xo) * Math.sin(yaw) + (player.getZ() - player.zo) * Math.cos(yaw);
        float lean = levitating ? (float) Math.clamp(forward / 0.6, -0.4, 1.0) * MAX_LEAN : 0.0F;
        blend.lean = Ease.damp(blend.lean, lean, 4.0F, seconds);
        return blend;
    }

    public static void prune(ClientLevel level) {
        BLENDS.int2ObjectEntrySet().removeIf(entry -> level.getEntity(entry.getIntKey()) == null);
    }

    public static final class Blend {
        public float shield;
        public float levitate;
        public float lean;
        long lastNanos;
    }
}
