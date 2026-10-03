package com.yashjit.scarlet.client.magic;

import com.yashjit.scarlet.client.ScarletKeyMappings;
import com.yashjit.scarlet.client.anim.CastGestures;
import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.magic.Magic;
import com.yashjit.scarlet.magic.MagicState;
import com.yashjit.scarlet.magic.Mastery;
import com.yashjit.scarlet.magic.Spell;
import com.yashjit.scarlet.network.CastPayload;
import com.yashjit.scarlet.platform.Services;
import com.yashjit.scarlet.player.ScarletPlayerData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Right click with an empty main hand casts the chosen spell. Vanilla gets the click first, so doors, chests and
 * villagers still work; only a click that would otherwise do nothing becomes magic.
 *
 * <ul>
 *     <li>Tapped spells fire again and again while the button is held.</li>
 *     <li>Channeled spells last while the button is held.</li>
 *     <li>Toggled spells flip once per press.</li>
 * </ul>
 *
 * <p>Levitation is not on the wheel: a double tap of jump, the way creative flight works, or its own key, lifts you
 * off or sets you down at any time, so you can fly and cast together.
 */
public final class CastInput {

    private static final int REFUSAL_FEEDBACK_TICKS = 10;
    private static final int DOUBLE_TAP_TICKS = 7;

    private static int predictedCount = Integer.MIN_VALUE;
    private static long localReadyAt = Long.MIN_VALUE;
    private static long lastRefusalAt = ScarletPlayerData.NEVER;
    private static @Nullable Spell holding;
    private static boolean pressHandled;
    private static boolean jumpWasDown;
    private static long lastJumpPress = ScarletPlayerData.NEVER;

    private CastInput() {
    }

    /**
     * Whether a click goes to the spell before whatever is under the crosshair gets it. While a channel is held the
     * click is the spell's alone; and Mind Control and Telekinesis are for taking hold of creatures, so with an empty
     * hand they reach for one rather than trade with it, ride it or feed it.
     */
    public static boolean claimsClick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || !CrownItem.isWearingCrown(player) || !player.getMainHandItem().isEmpty()) {
            return false;
        }
        if (holding != null) {
            return true;
        }
        Spell spell = Magic.state(player).selectedSpell();
        return (spell == Spell.MIND_CONTROL || spell == Spell.TELEKINESIS) && minecraft.hitResult instanceof EntityHitResult;
    }

    /**
     * @return whether the click was taken by magic
     */
    public static boolean tryCast(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || !CrownItem.isWearingCrown(player)) {
            return false;
        }
        MagicState state = Magic.state(player);
        Spell spell = state.selectedSpell();
        long now = player.level().getGameTime();
        if (spell.input() == Spell.Input.CHANNEL && holding != null) {
            return true;
        }
        if (spell.input() == Spell.Input.TOGGLE) {
            if (!pressHandled) {
                pressHandled = true;
                toggle(player, spell, state, now);
            }
            return true;
        }
        Magic.Refusal refusal = now < localReadyAt ? Magic.Refusal.COOLDOWN : Magic.check(player, spell, now);
        switch (refusal) {
            case NONE -> {
                if (spell.input() == Spell.Input.CHANNEL) {
                    holding = spell;
                } else {
                    int castNumber = Math.max(state.castCount(), predictedCount + 1);
                    predictedCount = castNumber;
                    localReadyAt = now + spell.cooldown();
                    float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                    CastGestures.predict(player, Magic.castArm(player, castNumber), now + partialTick);
                }
                Services.NETWORK.sendToServer(new CastPayload(true, spell.ordinal()));
                return true;
            }
            case COOLDOWN -> {
                return true;
            }
            case ENERGY, LOCKED, UNAVAILABLE -> {
                refuse(player, refusal, spell, now);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            holding = null;
            return;
        }
        boolean useDown = minecraft.options.keyUse.isDown() && minecraft.gui.screen() == null;
        if (!useDown) {
            pressHandled = false;
        }
        if (holding != null && (!useDown || !CrownItem.isWearingCrown(player))) {
            Services.NETWORK.sendToServer(new CastPayload(false, holding.ordinal()));
            holding = null;
        }
        long now = player.level().getGameTime();
        boolean jumpDown = minecraft.options.keyJump.isDown() && minecraft.gui.screen() == null;
        if (jumpDown && !jumpWasDown) {
            if (now - lastJumpPress <= DOUBLE_TAP_TICKS && canLevitate(player)) {
                MagicState state = Magic.state(player);
                // in creative the same double tap has just turned vanilla flight on or off; levitation follows it
                boolean wanted = player.getAbilities().instabuild ? player.getAbilities().flying : !state.levitating();
                if (wanted != state.levitating()) {
                    toggle(player, Spell.LEVITATION, state, now);
                }
                lastJumpPress = ScarletPlayerData.NEVER;
            } else {
                lastJumpPress = now;
            }
        }
        jumpWasDown = jumpDown;
        while (ScarletKeyMappings.LEVITATE.consumeClick()) {
            if (canLevitate(player)) {
                toggle(player, Spell.LEVITATION, Magic.state(player), now);
            }
        }
    }

    /**
     * Levitation is the crown's own gift rather than a spell on the wheel: whoever wears one and has the mastery for
     * it can rise or land at any time, whatever spell they have chosen.
     */
    private static boolean canLevitate(LocalPlayer player) {
        return CrownItem.isWearingCrown(player) && !player.isSpectator() && Mastery.rank(player) >= Spell.LEVITATION.rank();
    }

    private static void toggle(LocalPlayer player, Spell spell, MagicState state, long now) {
        if (!state.levitating() || spell != Spell.LEVITATION) {
            Magic.Refusal refusal = Magic.check(player, spell, now);
            if (refusal != Magic.Refusal.NONE) {
                if (refusal != Magic.Refusal.COOLDOWN) {
                    refuse(player, refusal, spell, now);
                }
                return;
            }
        }
        Services.NETWORK.sendToServer(new CastPayload(true, spell.ordinal()));
    }

    private static void refuse(LocalPlayer player, Magic.Refusal refusal, Spell spell, long now) {
        if (now - lastRefusalAt >= REFUSAL_FEEDBACK_TICKS) {
            lastRefusalAt = now;
            MagicHud.onRefused(refusal, spell);
            player.playSound(SoundEvents.AMETHYST_BLOCK_HIT, 0.5F, refusal == Magic.Refusal.ENERGY ? 0.55F : 0.8F);
        }
    }
}
