package com.yashjit.scarlet.client.anim;

import com.yashjit.scarlet.client.fx.PaintFx;
import com.yashjit.scarlet.client.hex.Ejections;
import com.yashjit.scarlet.client.hex.Founding;
import com.yashjit.scarlet.client.hex.HexClient;
import com.yashjit.scarlet.hex.HexSnapshot;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.magic.Magic;
import com.yashjit.scarlet.magic.MagicState;
import com.yashjit.scarlet.magic.Spell;
import com.yashjit.scarlet.registry.ScarletItems;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;

/**
 * How far each player has eased into their held poses: raising the shield, reaching out with Telekinesis, levitating,
 * and leaning into flight. Every
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
        boolean holding = state.channeling(Spell.TELEKINESIS) || Ejections.holds(player);
        boolean painting = PaintFx.painting(player);
        boolean raising = Founding.raising(player, player.level().getGameTime()) != null;
        boolean controlling = state.channeling(Spell.MIND_CONTROL);
        boolean levitating = state.levitating();
        boolean reading = player.isUsingItem() && player.getUseItem().is(ScarletItems.DARKHOLD.get());
        blend.read = Ease.damp(blend.read, reading ? 1.0F : 0.0F, reading ? 9.0F : 7.0F, seconds);
        blend.shield = Ease.damp(blend.shield, shielding ? 1.0F : 0.0F, shielding ? 14.0F : 9.0F, seconds);
        blend.hold = Ease.damp(blend.hold, holding ? 1.0F : 0.0F, holding ? 12.0F : 7.0F, seconds);
        blend.beam = Ease.damp(blend.beam, painting ? 1.0F : 0.0F, painting ? 14.0F : 7.0F, seconds);
        blend.raise = Ease.damp(blend.raise, raising ? 1.0F : 0.0F, raising ? 6.0F : 4.0F, seconds);
        blend.control = Ease.damp(blend.control, controlling ? 1.0F : 0.0F, controlling ? 10.0F : 6.0F, seconds);
        HexSnapshot parted = null;
        for (HexSnapshot hex : Hexes.clientHexes()) {
            if (hex.parting() && hex.caster().equals(player.getUUID())) {
                parted = hex;
            }
        }
        blend.tear = Ease.damp(blend.tear, parted != null ? 1.0F : 0.0F, parted != null ? 10.0F : 6.0F, seconds);
        blend.spread = Ease.damp(blend.spread, parted != null ? Math.min(1.0F, Math.max(0.0F, HexClient.opening(parted)) / 8.0F) : 0.0F, 8.0F, seconds);
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
        /** Reaching out to hold something with Telekinesis. */
        public float hold;
        /** An arm flung out at what a beam of magic from it paints. */
        public float beam;
        /** Both arms raised to a home going up far off. */
        public float raise;
        /** Working the strings of a mind held with Mind Control. */
        public float control;
        /** Gripping the wall of one's own Hex to part it. */
        public float tear;
        /** How far apart the hands have pulled the opening. */
        public float spread;
        public float levitate;
        public float lean;
        /** Reading the Darkhold. */
        public float read;
        long lastNanos;
    }
}
