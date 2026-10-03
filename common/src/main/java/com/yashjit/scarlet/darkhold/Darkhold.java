package com.yashjit.scarlet.darkhold;

import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.config.ScarletServerConfig;
import com.yashjit.scarlet.platform.Services;
import com.yashjit.scarlet.registry.ScarletItems;
import com.yashjit.scarlet.registry.ScarletSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * What the Darkhold does to whoever uses it. Reading it, and casting its spells, corrupts: the corruption darkens your
 * magic, crawls in at the edges of your sight and whispers to you, and once it runs deep it takes its toll. You hunger
 * faster from half way, the whispers won't let you sleep past three fifths, and past seven tenths the dark closes in on
 * you now and then. It all fades again, slowly, a minute after you last used the book.
 */
public final class Darkhold {

    /** Corruption each page of reading costs. */
    public static final float PAGE = 0.04F;

    private static final float HUNGRY = 0.5F;
    private static final float RESTLESS = 0.6F;
    private static final float DREAD = 0.7F;
    /** Ticks asleep before the whispers wake you. */
    private static final int RESTLESS_TICKS = 40;

    private Darkhold() {
    }

    /**
     * Whether someone has the Darkhold about them, anywhere in their inventory.
     */
    public static boolean carries(Player player) {
        return player.getInventory().contains(stack -> stack.is(ScarletItems.DARKHOLD.get()));
    }

    public static float corruption(Player player, double now) {
        return Services.PLAYER_DATA.corruption(player).at(now);
    }

    /**
     * Corrupts someone a little more.
     */
    public static void corrupt(ServerPlayer player, float amount) {
        Corruption corruption = Services.PLAYER_DATA.corruption(player);
        Services.PLAYER_DATA.setCorruption(player, corruption.raised(amount, player.level().getGameTime()));
    }

    public static void set(ServerPlayer player, float level) {
        Corruption corruption = Services.PLAYER_DATA.corruption(player);
        Services.PLAYER_DATA.setCorruption(player, corruption.withLevel(level, player.level().getGameTime()));
    }

    /**
     * Another page read: it turns with a whisper, and takes its share. The first time, the book says what it offers.
     */
    static void readPage(ServerPlayer player) {
        Corruption corruption = Services.PLAYER_DATA.corruption(player);
        if (!corruption.opened()) {
            player.sendSystemMessage(Component.translatable("darkhold.scarlet.opened").withColor(ScarletPalette.SICKLY));
            corruption = corruption.withOpened();
        }
        Services.PLAYER_DATA.setCorruption(player, corruption.raised(PAGE, player.level().getGameTime()));
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), ScarletSounds.DARKHOLD_PAGE.get(), SoundSource.PLAYERS, 0.9F,
                0.9F + player.getRandom().nextFloat() * 0.2F);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), ScarletSounds.DARKHOLD_WHISPER.get(), SoundSource.PLAYERS, 0.5F,
                0.85F + player.getRandom().nextFloat() * 0.2F);
    }

    /**
     * The toll a deep corruption takes, each tick.
     */
    public static void tick(ServerPlayer player) {
        if (!ScarletServerConfig.get().corruptionSideEffects || player.isSpectator()) {
            return;
        }
        float corruption = corruption(player, player.level().getGameTime());
        if (corruption < HUNGRY) {
            return;
        }
        player.causeFoodExhaustion(0.012F * (corruption - HUNGRY));
        if (corruption >= RESTLESS && player.isSleeping() && player.getSleepTimer() > RESTLESS_TICKS) {
            player.stopSleepInBed(true, true);
            player.sendOverlayMessage(Component.translatable("darkhold.scarlet.restless").withColor(ScarletPalette.SICKLY));
            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), ScarletSounds.DARKHOLD_VOICES.get(), SoundSource.PLAYERS,
                    0.8F, 0.9F);
        }
        // from once in a minute and a half on average to once in half a minute as it runs deepest
        float chance = 1.0F / (1800.0F - 1200.0F * Math.min(1.0F, (corruption - DREAD) / (1.0F - DREAD)));
        if (corruption >= DREAD && !player.hasEffect(MobEffects.DARKNESS) && player.getRandom().nextFloat() < chance) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100 + player.getRandom().nextInt(60), 0, false, false, true));
            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), ScarletSounds.DARKHOLD_DREAD.get(), SoundSource.PLAYERS,
                    0.9F, 0.8F);
        }
    }
}
