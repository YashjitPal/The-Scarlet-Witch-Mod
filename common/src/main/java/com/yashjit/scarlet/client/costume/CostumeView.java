package com.yashjit.scarlet.client.costume;

import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.config.ScarletClientConfig;
import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.crown.CrownStyle;
import com.yashjit.scarlet.entity.DreamBody;
import com.yashjit.scarlet.platform.Services;
import com.yashjit.scarlet.player.ScarletPlayerData;
import com.yashjit.scarlet.player.SuitUp;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * How a player's costume looks on this frame, derived from synced state so every client animates the same way.
 *
 * @param progress how much of the costume is manifested, 0 to 1, eased
 * @param phase    how far through the current transformation, 0 to 1 (1 once settled)
 * @param suited   whether the costume is manifesting (true) or being dismissed (false)
 */
public record CostumeView(CrownStyle style, float progress, float phase, boolean suited) {

    private static final Int2ObjectMap<CrownStyle> LAST_STYLE = new Int2ObjectOpenHashMap<>();

    public static @Nullable CostumeView of(Player player, float partialTick) {
        ScarletPlayerData data = Services.PLAYER_DATA.get(player);
        CrownStyle worn = CrownItem.wornStyle(player);
        if (worn != null) {
            LAST_STYLE.put(player.getId(), worn);
        }
        double elapsed = player.level().getGameTime() + partialTick - data.suitChangedAt();
        float phase = ScarletClientConfig.get().instantTransformations ? 1.0F : (float) Math.clamp(elapsed / SuitUp.TRANSFORM_TICKS, 0.0, 1.0);
        float progress = data.suited() ? Ease.inOutCubic(phase) : 1.0F - Ease.inOutCubic(phase);
        if (progress <= 0.001F) {
            return null;
        }
        // While dismissing after the crown was taken off, keep showing the costume it was.
        CrownStyle style = worn != null ? worn : LAST_STYLE.getOrDefault(player.getId(), CrownStyle.WITCH);
        return new CostumeView(style, progress, phase, data.suited());
    }

    /**
     * A dreamwalker's body wears the costume its owner had on, settled.
     */
    public static @Nullable CostumeView of(DreamBody body) {
        CrownStyle worn = CrownItem.wornStyle(body);
        return body.suited() && worn != null ? new CostumeView(worn, 1.0F, 1.0F, true) : null;
    }

    public boolean transforming() {
        return phase < 1.0F;
    }

    /**
     * How strongly magic wreathes the arms: a steady glow while suited, flaring through the transformation.
     */
    public float magicIntensity() {
        float settled = suited ? 0.55F : 0.0F;
        float flare = Ease.pulse(phase, 0.0F, 1.0F) * 0.6F;
        return Math.min(1.0F, progress * settled + flare);
    }
}
