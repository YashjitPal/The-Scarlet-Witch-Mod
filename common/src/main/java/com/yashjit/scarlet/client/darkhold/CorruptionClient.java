package com.yashjit.scarlet.client.darkhold;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.anim.Ease;
import com.yashjit.scarlet.client.fx.ScarletFx;
import com.yashjit.scarlet.client.magic.Hands;
import com.yashjit.scarlet.client.magic.MagicVisuals;
import com.yashjit.scarlet.config.ScarletClientConfig;
import com.yashjit.scarlet.darkhold.Darkhold;
import com.yashjit.scarlet.platform.Services;
import com.yashjit.scarlet.registry.ScarletItems;
import com.yashjit.scarlet.registry.ScarletSounds;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Darkhold's corruption as it is seen and heard.
 *
 * <ul>
 *     <li>A corrupted caster's magic darkens toward black-crimson for everyone, from a tenth of the way in, and black
 *     smoke rises off it.</li>
 *     <li>Dark veins crawl in at the edges of your sight, further the deeper it runs, beating with your heart; they
 *     flare while you read the book and as the dark closes in.</li>
 *     <li>It whispers to you from all around, now and then when you merely carry the book, every few seconds once it
 *     has all of you, and in many voices near the end.</li>
 *     <li>Reading it, the pages give off sickly sparks and black smoke.</li>
 * </ul>
 */
public final class CorruptionClient {

    private static final Identifier VEINS = Scarlet.id("hud/darkhold_veins");

    private static int whisperIn = 200;
    private static float veinsShown;
    private static float reading;
    private static long lastNanos;

    private CorruptionClient() {
    }

    /**
     * How far the Darkhold has taken someone right now, 0 to 1.
     */
    public static float corruption(@Nullable Entity entity) {
        if (!(entity instanceof Player player)) {
            return 0.0F;
        }
        Minecraft minecraft = Minecraft.getInstance();
        double now = player.level().getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return Services.PLAYER_DATA.corruption(player).at(now);
    }

    /**
     * How far a caster's magic is darkened: not at all until a tenth of the way in, fully at nine tenths.
     */
    public static float darkness(@Nullable Entity caster) {
        return darkness(corruption(caster));
    }

    /**
     * The same, for a corruption from 0 to 1.
     */
    public static float darkness(float corruption) {
        if (corruption <= 0.1F) {
            return 0.0F;
        }
        float t = Ease.clamp01((corruption - 0.1F) / 0.8F);
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * Whether someone is reading the Darkhold right now.
     */
    public static boolean reading(Player player) {
        return player.isUsingItem() && player.getUseItem().is(ScarletItems.DARKHOLD.get());
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        LocalPlayer local = minecraft.player;
        if (level == null || local == null || minecraft.isPaused()) {
            return;
        }
        for (Player player : level.players()) {
            if (player.distanceToSqr(local) > 48 * 48) {
                continue;
            }
            if (reading(player)) {
                pages(player, level.getRandom());
            }
            smolder(player, level.getRandom());
        }
        whisper(minecraft, local);
    }

    /**
     * Black smoke curling off a corrupted caster's hands while their magic burns.
     */
    private static void smolder(Player player, RandomSource random) {
        float darkness = darkness(player);
        if (darkness < 0.2F) {
            return;
        }
        for (HumanoidArm arm : HumanoidArm.values()) {
            float intensity = MagicVisuals.armIntensity(player, arm, 1.0F);
            if (intensity > 0.15F && random.nextFloat() < darkness * intensity * 0.6F * ScarletFx.density()) {
                Vec3 palm = Hands.palm(player, arm).add((random.nextDouble() - 0.5) * 0.12, (random.nextDouble() - 0.5) * 0.12,
                        (random.nextDouble() - 0.5) * 0.12);
                ScarletFx.smoke(palm, new Vec3((random.nextDouble() - 0.5) * 0.01, 0.01 + random.nextDouble() * 0.015, (random.nextDouble() - 0.5) * 0.01),
                        20 + random.nextInt(14), 0.06F + 0.04F * darkness, 0.35F + 0.4F * darkness);
            }
        }
    }

    /**
     * Sickly sparks and black smoke rising off the open pages.
     */
    private static void pages(Player player, RandomSource random) {
        Vec3 look = player.getLookAngle();
        Vec3 book = ScarletFx.isFirstPersonViewOf(player)
                ? player.getEyePosition().add(look.scale(0.55)).add(0.0, -0.32, 0.0)
                : player.position().add(0.0, player.getBbHeight() * 0.62, 0.0).add(new Vec3(look.x, 0.0, look.z).normalize().scale(0.45));
        float density = ScarletFx.density();
        if (random.nextFloat() < 0.8F * density) {
            Vec3 at = book.add((random.nextDouble() - 0.5) * 0.3, random.nextDouble() * 0.05, (random.nextDouble() - 0.5) * 0.3);
            Vec3 rise = new Vec3((random.nextDouble() - 0.5) * 0.01, 0.015 + random.nextDouble() * 0.025, (random.nextDouble() - 0.5) * 0.01);
            ScarletFx.spark(at, rise, 18 + random.nextInt(14), 0.014F + random.nextFloat() * 0.01F,
                    random.nextFloat() < 0.3F ? 0xFFB3BE : ScarletPalette.SICKLY, ScarletPalette.ABYSS, -0.0006F, 0.97F);
        }
        if (random.nextFloat() < 0.35F * density) {
            Vec3 at = book.add((random.nextDouble() - 0.5) * 0.25, 0.02, (random.nextDouble() - 0.5) * 0.25);
            ScarletFx.smoke(at, new Vec3(0.0, 0.012 + random.nextDouble() * 0.01, 0.0), 26 + random.nextInt(16), 0.07F, 0.55F);
        }
    }

    /**
     * The book speaking from all around whoever it has a hold on: a minute or so apart when it barely has them, or they
     * only carry it, every few seconds once it has all of them.
     */
    private static void whisper(Minecraft minecraft, LocalPlayer player) {
        float corruption = corruption(player);
        boolean carrying = Darkhold.carries(player);
        float hold = Math.max(corruption, carrying ? 0.12F : 0.0F);
        if (hold < 0.12F || player.isSpectator() && !DreamwalkClient.away()) {
            whisperIn = Math.max(whisperIn, 100);
            return;
        }
        if (--whisperIn > 0) {
            return;
        }
        RandomSource random = player.getRandom();
        whisperIn = Math.round(1300.0F * (1.0F - hold) * (1.0F - hold)) + 70 + random.nextInt(110);
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = 1.4 + random.nextDouble() * 1.8;
        Vec3 at = player.getEyePosition().add(Math.cos(angle) * distance, (random.nextDouble() - 0.3) * 0.8, Math.sin(angle) * distance);
        boolean voices = corruption > 0.85F && random.nextFloat() < 0.3F;
        minecraft.getSoundManager().play(new SimpleSoundInstance(voices ? ScarletSounds.DARKHOLD_VOICES.get() : ScarletSounds.DARKHOLD_WHISPER.get(),
                SoundSource.AMBIENT, 0.25F + 0.5F * hold, 0.88F + random.nextFloat() * 0.2F, RandomSource.create(random.nextLong()), at.x, at.y, at.z));
    }

    /**
     * Dark veins at the edges of the view.
     */
    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        long nanos = System.nanoTime();
        float seconds = lastNanos == 0 ? 0.0F : Math.min(0.1F, (nanos - lastNanos) / 1.0E9F);
        lastNanos = nanos;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            veinsShown = 0.0F;
            return;
        }
        float corruption = corruption(player);
        veinsShown = Ease.damp(veinsShown, Ease.clamp01((corruption - 0.15F) / 0.75F), 2.0F, seconds);
        reading = Ease.damp(reading, reading(player) ? 1.0F : 0.0F, reading(player) ? 1.5F : 3.0F, seconds);
        float closing = player.hasEffect(MobEffects.DARKNESS) && corruption >= 0.7F ? 1.0F : 0.0F;
        float strength = Math.max(veinsShown, reading * 0.55F);
        if (strength < 0.01F) {
            return;
        }
        boolean calm = ScarletClientConfig.get().reduceScreenEffects;
        double now = minecraft.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);
        // a slow heartbeat, quickening the deeper it runs
        float beat = calm ? 0.0F : heartbeat((float) now * (0.8F + 1.2F * corruption));
        float alpha = Math.min(1.0F, strength * (0.62F + 0.3F * beat) + 0.25F * reading + 0.25F * closing) * (calm ? 0.55F : 1.0F);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        // the veins reach further in the deeper it runs: drawn larger than the view, only their ends show at first
        float reach = Ease.clamp01(strength + 0.15F * reading + 0.1F * closing);
        float margin = (1.0F - reach) * 0.3F;
        int x = Math.round(-width * margin);
        int y = Math.round(-height * margin);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, VEINS, x, y, width - 2 * x, height - 2 * y, ARGB.white(alpha));
    }

    private static float heartbeat(float time) {
        float t = (time / 26.0F) % 1.0F;
        float first = Math.max(0.0F, 1.0F - Math.abs(t - 0.08F) / 0.07F);
        float second = Math.max(0.0F, 1.0F - Math.abs(t - 0.24F) / 0.07F) * 0.7F;
        return Mth.clamp(Math.max(first, second), 0.0F, 1.0F);
    }
}
