package com.yashjit.scarlet.client.dev;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.client.ScarletKeyMappings;
import com.yashjit.scarlet.client.anim.FirstPersonGestures;
import com.yashjit.scarlet.client.magic.SpellWheel;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.magic.Spell;
import com.yashjit.scarlet.network.SelectSpellPayload;
import com.yashjit.scarlet.network.ToggleSuitPayload;
import com.yashjit.scarlet.platform.Services;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.Lifecycle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Development-only automated scenes for reviewing visuals without manual play.
 *
 * <p>Enabled by {@code -Pshowcase=<scene>} on a {@code runClient} task. Loads a flat creative test world, plays the
 * scene's steps and saves screenshots to {@code runs/client/screenshots/scarlet_*.png}. Then it hands control to the
 * player, or quits if {@code -PshowcaseQuit} is also given.
 */
public final class Showcase {

    private static final String WORLD = "scarlet-showcase";
    private static final @Nullable String SCENE = System.getProperty("scarlet.showcase");
    private static final boolean QUIT = Boolean.getBoolean("scarlet.showcase.quit");
    private static final int SETTLE_TICKS = 60;

    private static State state = State.WAITING_FOR_MENU;
    private static int wait;
    private static int index;
    private static List<Step> steps = List.of();
    private static @Nullable ArmorStand camera;
    private static @Nullable Entity subject;
    private static @Nullable Boolean savedImprovedTransparency;
    private static @Nullable BackupConfirmScreen answeredPrompt;

    private Showcase() {
    }

    public static boolean enabled() {
        return SCENE != null && !SCENE.isBlank() && Services.PLATFORM.isDevelopmentEnvironment();
    }

    public static boolean isFreeCameraActive() {
        return camera != null && Minecraft.getInstance().getCameraEntity() == camera;
    }

    public static void tick(Minecraft minecraft) {
        switch (state) {
            case WAITING_FOR_MENU -> {
                if (minecraft.gui.overlay() == null && minecraft.gui.screen() != null) {
                    minecraft.options.pauseOnLostFocus = false;
                    steps = scene(SCENE);
                    openWorld(minecraft);
                    state = State.WAITING_FOR_WORLD;
                }
            }
            case WAITING_FOR_WORLD -> {
                if (minecraft.gui.screen() instanceof BackupConfirmScreen screen) {
                    skipBackupPrompt(screen);
                }
                if (minecraft.player != null && minecraft.level != null && minecraft.gui.screen() == null) {
                    reportUnstableRegistries(minecraft);
                    wait = SETTLE_TICKS;
                    state = State.PLAYING;
                }
            }
            case PLAYING -> play(minecraft);
            case DONE -> {
            }
        }
    }

    private static void play(Minecraft minecraft) {
        if (minecraft.gui.screen() instanceof PauseScreen) {
            minecraft.gui.setScreen(null);
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (index >= steps.size()) {
            state = State.DONE;
            if (QUIT) {
                Scarlet.LOG.info("Showcase '{}' finished, quitting", SCENE);
                minecraft.stop();
            } else {
                Scarlet.LOG.info("Showcase '{}' finished, handing control to the player", SCENE);
                handOver(minecraft);
            }
            return;
        }
        Step step = steps.get(index++);
        step.action().accept(minecraft);
        wait = step.waitAfter();
    }

    private static void handOver(Minecraft minecraft) {
        minecraft.setCameraEntity(minecraft.player);
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.options.pauseOnLostFocus = true;
        if (minecraft.gui.hud.isHidden()) {
            minecraft.gui.hud.toggle();
        }
    }

    /**
     * Answers vanilla's experimental-settings warning with "I know what I'm doing", so the scene can go on.
     */
    private static void skipBackupPrompt(BackupConfirmScreen screen) {
        // the screen stays up for a few ticks while loading resumes, and a second press would load the world twice
        if (answeredPrompt == screen) {
            return;
        }
        answeredPrompt = screen;
        String skip = Component.translatable("selectWorld.backupJoinSkipButton").getString();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && button.getMessage().getString().equals(skip)) {
                Scarlet.LOG.warn("Showcase: the world was flagged as using experimental settings; loading it anyway");
                button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                return;
            }
        }
    }

    /**
     * Logs whatever makes a world count as experimental, which players would be warned about on every load.
     */
    private static void reportUnstableRegistries(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        Scarlet.LOG.info("Showcase: world settings lifecycle {}", server.getWorldData().worldGenSettingsLifecycle());
        server.registryAccess().registries().forEach(entry -> reportUnstable(entry.value()));
    }

    private static <T> void reportUnstable(Registry<T> registry) {
        if (registry.registryLifecycle() == Lifecycle.stable()) {
            return;
        }
        Scarlet.LOG.warn("Showcase: registry {} is {}", registry.key().identifier(), registry.registryLifecycle());
        for (ResourceKey<T> key : registry.registryKeySet()) {
            registry.registrationInfo(key).filter(info -> info.lifecycle() != Lifecycle.stable()).ifPresent(info ->
                    Scarlet.LOG.warn("Showcase:   {} is {} (pack {})", key.identifier(), info.lifecycle(), info.knownPackInfo()));
        }
    }

    private static void openWorld(Minecraft minecraft) {
        if (minecraft.getLevelSource().levelExists(WORLD)) {
            minecraft.createWorldOpenFlows().openWorld(WORLD, () -> minecraft.gui.setScreen(new TitleScreen()));
            return;
        }
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, WorldOptions.testWorldWithRandomSeed(),
                WorldPresets::createTestWorldDimensions, new TitleScreen());
    }

    private static List<Step> scene(String name) {
        Scene s = new Scene();
        s.command("time set noon").command("weather clear").camera(CameraType.THIRD_PERSON_FRONT).hideHud(true);
        switch (name) {
            case "crown" -> s
                    .command("item replace entity @a armor.head with scarlet:witch_tiara").face(0, 0)
                    .look(3.4, 0, 8, 1.0).shot("witch_full", 30)
                    .look(1.25, 0, 6, 1.75).shot("witch_close_front", 6)
                    .look(1.35, 40, 12, 1.75).shot("witch_close_three_quarter", 6)
                    .look(1.35, 90, 8, 1.75).shot("witch_close_side", 6)
                    .look(1.45, 160, 18, 1.75).shot("witch_close_back", 6)
                    .command("item replace entity @a armor.head with scarlet:warlock_crown")
                    .look(1.25, 0, 6, 1.75).shot("warlock_close_front", 10)
                    .look(1.35, 40, 12, 1.75).shot("warlock_close_three_quarter", 6)
                    .look(1.35, 90, 8, 1.75).shot("warlock_close_side", 6)
                    .command("time set midnight").look(1.35, 30, 10, 1.75).shot("warlock_night", 20)
                    .command("item replace entity @a armor.head with scarlet:witch_tiara").shot("witch_night", 10)
                    .command("time set noon")
                    .command("item replace entity @a weapon.mainhand with scarlet:witch_tiara")
                    .look(2.2, 25, 10, 1.1).shot("held", 10)
                    .suitUp().look(3.4, 0, 8, 1.0).shot("suit_up", 6)
                    .playerCamera().inventory().shot("inventory", 15);
            case "tiara" -> s
                    .command("execute as @a at @s run tp @s ~ ~ ~ 180 0")
                    .command("execute at @a run summon minecraft:mannequin ~ ~ ~-3 {Rotation:[0f,0f]}")
                    .command("item replace entity @e[type=minecraft:mannequin] armor.head with scarlet:witch_tiara")
                    .subject()
                    .look(3.0, 0, 5, 1.0).shot("tiara_full", 20)
                    .look(1.1, 0, 0, 1.72).shot("tiara_front", 6)
                    .look(1.2, 35, 8, 1.75).shot("tiara_three_quarter", 6)
                    .look(1.2, 80, 5, 1.75).shot("tiara_side", 6)
                    .look(1.3, 25, 35, 1.8).shot("tiara_above", 6)
                    .command("item replace entity @e[type=minecraft:mannequin] armor.head with scarlet:warlock_crown")
                    .look(1.1, 0, 0, 1.72).shot("warlock_front", 10)
                    .look(1.2, 35, 8, 1.75).shot("warlock_three_quarter", 6)
                    .look(1.2, 80, 5, 1.75).shot("warlock_side", 6);
            case "suit" -> s
                    .command("item replace entity @a armor.head with scarlet:witch_tiara")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .ensureUnsuited().face(0, 0)
                    .look(3.2, 25, 6, 1.0).shot("suit_before", 10)
                    .suitUp()
                    .shot("suit_a", 4).shot("suit_b", 4).shot("suit_c", 4).shot("suit_d", 4).shot("suit_e", 4).shot("suit_done", 14)
                    .look(1.8, 20, 4, 1.25).shot("suit_close", 6)
                    .look(2.8, 160, 10, 1.1).shot("suit_back", 6)
                    .look(2.8, 90, 4, 1.0).shot("suit_side", 6)
                    .command("item replace entity @a armor.head with scarlet:warlock_crown")
                    .look(3.2, 25, 6, 1.0).shot("warlock_suit", 10)
                    .look(2.8, 160, 10, 1.1).shot("warlock_back", 6)
                    .command("time set midnight").look(2.4, 30, 8, 1.1).shot("suit_night", 20)
                    .command("time set noon")
                    .playerCamera().hideHud(false).shot("first_person", 12).hideHud(true)
                    .look(3.2, 25, 6, 1.0).suitUp().shot("dismiss_a", 8).shot("dismiss_b", 8).shot("dismiss_done", 20);
            case "flare" -> s
                    .command("item replace entity @a armor.head with scarlet:witch_tiara")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .ensureUnsuited().face(0, 0)
                    .transparency(false)
                    .flare("flare_classic_front", 0).flare("flare_classic_left", 120).flare("flare_classic_right", 240)
                    .transparency(true)
                    .flare("flare_oit_front", 0).flare("flare_oit_left", 120)
                    .restoreTransparency();
            case "cast" -> s
                    .land()
                    .command("kill @e[type=minecraft:mannequin]")
                    .command("kill @e[type=minecraft:pig]")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .ensureUnsuited().face(0, 0)
                    .command("execute at @a run fill ~-3 ~ ~13 ~3 ~4 ~13 minecraft:polished_blackstone_bricks")
                    .command("execute at @a run summon minecraft:pig ~1 ~ ~8 {NoAI:1b,Rotation:[90f,0f]}")
                    .look(4.6, 62, 8, 1.3)
                    .hold(true).shot("cast_a", 2).shot("cast_b", 1).shot("cast_c", 1).shot("cast_d", 2).shot("cast_e", 3).hold(false)
                    .shot("cast_after", 10)
                    .look(2.3, 28, 2, 1.45).hold(true).shot("cast_close_a", 2).shot("cast_close_b", 1).shot("cast_close_c", 2).hold(false)
                    .look(3.4, -150, 10, 1.2).hold(true).shot("cast_back", 5).hold(false)
                    .suitUp().then(minecraft -> {
                    }, 40)
                    .look(4.6, 62, 8, 1.3).hold(true).shot("cast_suited_a", 3).shot("cast_suited_b", 2).hold(false)
                    .command("time set midnight").look(4.2, 48, 6, 1.3).hold(true).shot("cast_night", 6).hold(false).command("time set noon")
                    .playerCamera().hideHud(false).then(minecraft -> {
                    }, 30)
                    .hold(true).shot("first_person_a", 1).shot("first_person_b", 1).shot("first_person_c", 2).shot("first_person_d", 3).hold(false)
                    .shot("first_person_after", 14)
                    .wheel(true).wheelCursor(0, -40).shot("wheel_open", 6).wheelCursor(40, 34).shot("wheel_hover", 5).wheel(false)
                    .shot("wheel_closed", 6)
                    .hideHud(true);
            case "bolt" -> s
                    .land()
                    .command("kill @e[type=minecraft:mannequin]")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .ensureUnsuited().face(0, 0)
                    .look(5.0, 90, 4, 1.3)
                    .hold(true).shot("bolt_a", 3).shot("bolt_b", 2).shot("bolt_c", 2).hold(false).shot("bolt_after", 6)
                    .command("gamemode survival @a").playerCamera().hideHud(false).then(minecraft -> {
                    }, 10)
                    .hold(true).shot("hud_casting", 8).hold(false).shot("hud_after", 30)
                    .command("gamemode creative @a").hideHud(true);
            case "gesture" -> s
                    .command("item replace entity @a armor.head with scarlet:witch_tiara")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .land().ensureUnsuited().playerCamera().hideHud(false)
                    .swing(0, 0, 0).face(0, 0).shot("gesture_rest", 20)
                    .swing(-0.6F, 0, 0).face(0, 0).shot("gesture_x_neg", 6)
                    .swing(0.6F, 0, 0).face(0, 0).shot("gesture_x_pos", 6)
                    .swing(0, 0.6F, 0).face(0, 0).shot("gesture_y_pos", 6)
                    .swing(0, -0.6F, 0).face(0, 0).shot("gesture_y_neg", 6)
                    .swing(0, 0, 0.6F).face(0, 0).shot("gesture_z_pos", 6)
                    .swing(0, 0, -0.6F).face(0, 0).shot("gesture_z_neg", 6)
                    .then(minecraft -> FirstPersonGestures.debugSwing = null, 2).hideHud(true);
            case "shield" -> s
                    .land()
                    .command("kill @e[type=minecraft:arrow]")
                    .command("kill @e[type=minecraft:mannequin]")
                    .command("kill @e[type=minecraft:pig]")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .command("effect give @a minecraft:resistance 120 4 true")
                    .ensureUnsuited().face(0, 0).select(Spell.CHAOS_SHIELD)
                    .look(3.4, 35, 6, 1.3)
                    .hold(true).shot("shield_raise", 1).shot("shield_open", 2).shot("shield_up", 12)
                    .transparency(true).shot("shield_oit", 4).transparency(false).shot("shield_classic", 4)
                    .look(3.0, 100, 4, 1.4).shot("shield_side", 4)
                    .look(2.6, 200, 6, 1.5).shot("shield_behind", 4)
                    .look(3.4, 35, 6, 1.3)
                    .command("execute at @a run summon minecraft:arrow ~0.3 ~1.4 ~7 {Motion:[0.0d,0.0d,-1.6d]}")
                    .shot("shield_deflect_a", 2).shot("shield_deflect_b", 1).shot("shield_deflect_c", 4)
                    .command("gamemode survival @a")
                    .command("execute at @a run damage @p 6 minecraft:mob_attack at ~ ~1.5 ~3")
                    .shot("shield_block_a", 0).shot("shield_block_b", 3)
                    .look(3.0, 160, 8, 1.3).shot("shield_back", 4)
                    .playerCamera().hideHud(false).shot("shield_first_person", 8)
                    .command("execute at @a run damage @p 6 minecraft:mob_attack at ~ ~1.5 ~3")
                    .shot("shield_first_person_hit", 0).hideHud(true)
                    .look(3.4, 35, 6, 1.3)
                    .command("execute at @a run damage @p 20 minecraft:mob_attack at ~ ~1.5 ~3")
                    .command("execute at @a run damage @p 20 minecraft:mob_attack at ~ ~1.5 ~3")
                    .command("execute at @a run damage @p 20 minecraft:mob_attack at ~ ~1.5 ~3")
                    .shot("shield_shatter_a", 0).shot("shield_shatter_b", 3).shot("shield_shatter_c", 5)
                    .hold(false)
                    .command("gamemode creative @a")
                    .command("effect give @a minecraft:instant_health 1 5 true")
                    .then(minecraft -> {
                    }, 70)
                    .suitUp().then(minecraft -> {
                    }, 40)
                    .look(3.4, 35, 6, 1.3).hold(true).shot("shield_suited", 12)
                    .command("time set midnight").shot("shield_night", 14).hold(false).command("time set noon")
                    .shot("shield_lowered", 8).restoreTransparency();
            case "levitate" -> s
                    .land()
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .command("gamemode survival @a")
                    .command("effect give @a minecraft:resistance 120 4 true")
                    .ensureUnsuited().face(0, 0).select(Spell.LEVITATION)
                    .look(4.2, 40, 6, 1.2)
                    .tap().shot("lift_a", 0).shot("lift_b", 3).shot("lift_c", 4)
                    .look(4.2, 40, 4, 1.4).shot("hover_a", 14).shot("hover_b", 10)
                    .look(3.2, 100, 2, 1.4).shot("hover_side", 6)
                    .walk(true).then(minecraft -> {
                    }, 10)
                    .look(4.6, 50, 4, 1.3).shot("lean", 0).walk(false)
                    .then(minecraft -> {
                    }, 14)
                    .playerCamera().hideHud(false).face(0, 55).shot("levitate_first_person", 8).face(0, 0).hideHud(true)
                    .tap().look(4.2, 40, 6, 1.0).shot("descend", 10)
                    .then(minecraft -> {
                    }, 50)
                    .look(4.2, 40, 6, 1.0).shot("landed", 2)
                    .jumpTap().jumpTap().shot("double_tap_a", 3).shot("double_tap_b", 8)
                    .suitUp().then(minecraft -> {
                    }, 40)
                    .look(4.2, 40, 4, 1.4).shot("hover_suited", 8)
                    .command("time set midnight").shot("hover_night", 14).command("time set noon")
                    .sneak(true).then(minecraft -> {
                    }, 30)
                    .sneak(false).look(4.2, 40, 6, 1.0).shot("touch_down_a", 0).shot("touch_down_b", 4)
                    .command("gamemode creative @a");
            case "hex" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=minecraft:pig]")
                    .command("kill @e[type=minecraft:mannequin]")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .command("execute at @a run summon minecraft:pig ~3 ~ ~5 {NoAI:1b}")
                    .command("execute at @a run fill ~-4 ~ ~8 ~-1 ~3 ~8 minecraft:red_wool")
                    .command("execute at @a run fill ~1 ~ ~8 ~4 ~3 ~8 minecraft:lime_wool")
                    .command("execute at @a run fill ~-1 ~ ~-9 ~1 ~2 ~-9 minecraft:blue_wool")
                    .ensureUnsuited().face(0, 0).select(Spell.HEX)
                    .look(44, 25, 14, 1.0)
                    .tap().shot("hex_spread_a", 3).shot("hex_spread_b", 12).shot("hex_spread_c", 25)
                    .shot("hex_outside", 40)
                    .look(40, 70, 3, 2.0).shot("hex_outside_low", 4)
                    .look(75, 40, 58, 1.0).shot("hex_above", 4)
                    .look(5, 30, 10, 1.2).shot("hex_inside_third", 4)
                    .playerCamera().hideHud(false).shot("hex_inside", 8).hideHud(true)
                    .hold(true).then(minecraft -> {
                    }, 40)
                    .hold(false).look(52, 25, 14, 1.0).shot("hex_grown", 20)
                    .command("item replace entity @a armor.head with minecraft:air")
                    .shot("hex_warning_a", 20).shot("hex_warning_b", 30)
                    .shot("hex_collapse_a", 55).shot("hex_collapse_b", 15).shot("hex_collapse_c", 15).shot("hex_gone", 30)
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]");
            case "play" -> s
                    .command("item replace entity @a armor.head with scarlet:witch_tiara")
                    .command("give @a scarlet:warlock_crown");
            default -> Scarlet.LOG.warn("Unknown showcase scene '{}'", name);
        }
        return s.steps;
    }

    private enum State {
        WAITING_FOR_MENU, WAITING_FOR_WORLD, PLAYING, DONE
    }

    private record Step(Consumer<Minecraft> action, int waitAfter) {
    }

    private static final class Scene {
        final List<Step> steps = new ArrayList<>();

        Scene then(Consumer<Minecraft> action, int waitAfter) {
            steps.add(new Step(action, waitAfter));
            return this;
        }

        Scene command(String command) {
            return then(minecraft -> {
                IntegratedServer server = minecraft.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
                }
            }, 2);
        }

        Scene camera(CameraType type) {
            return then(minecraft -> minecraft.options.setCameraType(type), 0);
        }

        Scene hideHud(boolean hidden) {
            return then(minecraft -> {
                if (minecraft.gui.hud.isHidden() != hidden) {
                    minecraft.gui.hud.toggle();
                }
            }, 0);
        }

        Scene face(float yaw, float pitch) {
            return then(minecraft -> {
                var player = minecraft.player;
                if (player != null) {
                    player.setYRot(yaw);
                    player.setXRot(pitch);
                    player.setYHeadRot(yaw);
                    player.setYBodyRot(yaw);
                }
            }, 4);
        }

        /**
         * Moves a free camera onto an orbit around the player. {@code yawOffset} is measured from straight in front of
         * the player, {@code elevation} in degrees above the target, {@code targetHeight} in blocks above their feet.
         */
        Scene look(double distance, float yawOffset, float elevation, double targetHeight) {
            return then(minecraft -> {
                Entity focus = subject != null && !subject.isRemoved() ? subject : minecraft.player;
                if (focus == null || minecraft.level == null) {
                    return;
                }
                if (camera == null || camera.level() != minecraft.level) {
                    camera = new ArmorStand(minecraft.level, 0, 0, 0);
                    camera.setInvisible(true);
                }
                Vec3 target = focus.position().add(0, targetHeight, 0);
                double yaw = Math.toRadians(focus.getYRot() + yawOffset);
                double pitch = Math.toRadians(elevation);
                Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
                Vec3 eye = target.add(forward.scale(distance * Math.cos(pitch))).add(0, distance * Math.sin(pitch), 0);
                Vec3 toTarget = target.subtract(eye);
                float lookYaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
                float lookPitch = (float) Math.toDegrees(-Math.atan2(toTarget.y, Math.hypot(toTarget.x, toTarget.z)));
                camera.snapTo(eye.x, eye.y - camera.getEyeHeight(), eye.z, lookYaw, lookPitch);
                camera.setYHeadRot(lookYaw);
                camera.yHeadRotO = lookYaw;
                camera.setOldPosAndRot();
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                minecraft.setCameraEntity(camera);
            }, 3);
        }

        /**
         * Makes the nearest mannequin the focus of later camera moves. The local player is never drawn from a detached
         * camera, so close-ups of worn items use a mannequin.
         */
        Scene subject() {
            then(minecraft -> {
            }, 10);
            return then(minecraft -> {
                if (minecraft.level == null || minecraft.player == null) {
                    return;
                }
                double best = Double.MAX_VALUE;
                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    if (entity instanceof Mannequin && entity.distanceToSqr(minecraft.player) < best) {
                        best = entity.distanceToSqr(minecraft.player);
                        subject = entity;
                    }
                }
            }, 0);
        }

        Scene playerCamera() {
            return then(minecraft -> {
                minecraft.setCameraEntity(minecraft.player);
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            }, 2);
        }

        Scene suitUp() {
            return then(minecraft -> Services.NETWORK.sendToServer(ToggleSuitPayload.INSTANCE), 0);
        }

        /**
         * Dismisses the costume left over from an earlier run and waits for the transformation to finish.
         */
        Scene ensureUnsuited() {
            return then(minecraft -> {
                if (minecraft.player != null && Services.PLAYER_DATA.get(minecraft.player).suited()) {
                    Services.NETWORK.sendToServer(ToggleSuitPayload.INSTANCE);
                }
            }, 40);
        }

        /**
         * Suits up under a camera looking up past the head into the sky, catching the crown flare against the clouds,
         * then suits down again.
         */
        Scene flare(String name, float yawOffset) {
            return look(2.4, yawOffset, -14, 1.7).suitUp().shot(name, 27).shot(name + "_after", 10)
                    .suitUp().then(minecraft -> {
                    }, 40);
        }

        /**
         * Presses or releases the use button, exactly as holding right click would.
         */
        Scene hold(boolean down) {
            return then(minecraft -> minecraft.options.keyUse.setDown(down), 0);
        }

        /**
         * Stops creative flight left over from an earlier run, so the player stands on the ground.
         */
        /**
         * Takes down any Hex an earlier run left standing in the world.
         */
        Scene dispelHexes() {
            return then(minecraft -> {
                IntegratedServer server = minecraft.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> server.getAllLevels().forEach(Hexes::dispelAll));
                }
            }, 4);
        }

        Scene land() {
            return then(minecraft -> {
                if (minecraft.player != null && minecraft.player.getAbilities().flying) {
                    minecraft.player.getAbilities().flying = false;
                    minecraft.player.onUpdateAbilities();
                }
            }, 20);
        }

        /**
         * Holds both first-person arms at full reach with a fixed swing, for calibrating the gesture.
         */
        Scene swing(float x, float y, float z) {
            return then(minecraft -> FirstPersonGestures.debugSwing = new float[] {x, y, z}, 0);
        }

        /**
         * One short press of the use button.
         */
        Scene tap() {
            return hold(true).then(minecraft -> {
            }, 3).hold(false).then(minecraft -> {
            }, 2);
        }

        Scene jumpTap() {
            return then(minecraft -> minecraft.options.keyJump.setDown(true), 1)
                    .then(minecraft -> minecraft.options.keyJump.setDown(false), 2);
        }

        Scene walk(boolean down) {
            return then(minecraft -> minecraft.options.keyUp.setDown(down), 0);
        }

        Scene sneak(boolean down) {
            return then(minecraft -> minecraft.options.keyShift.setDown(down), 0);
        }

        Scene select(Spell spell) {
            return then(minecraft -> Services.NETWORK.sendToServer(new SelectSpellPayload(spell.ordinal())), 4);
        }

        Scene wheel(boolean down) {
            return then(minecraft -> ScarletKeyMappings.SPELL_WHEEL.setDown(down), 2);
        }

        /**
         * Moves the wheel's cursor to an offset from its center, in GUI pixels.
         */
        Scene wheelCursor(float x, float y) {
            return then(minecraft -> SpellWheel.debugCursor(x, y), 0);
        }

        Scene transparency(boolean improved) {
            return then(minecraft -> {
                if (savedImprovedTransparency == null) {
                    savedImprovedTransparency = minecraft.options.improvedTransparency().get();
                }
                minecraft.options.improvedTransparency().set(improved);
            }, 10);
        }

        Scene restoreTransparency() {
            return then(minecraft -> {
                if (savedImprovedTransparency != null) {
                    minecraft.options.improvedTransparency().set(savedImprovedTransparency);
                    savedImprovedTransparency = null;
                }
            }, 2);
        }

        Scene inventory() {
            return then(minecraft -> {
                if (minecraft.player != null) {
                    minecraft.gui.setScreen(new InventoryScreen(minecraft.player));
                }
            }, 0);
        }

        Scene shot(String name, int delay) {
            then(minecraft -> {
            }, delay);
            return then(minecraft -> Screenshot.grab(minecraft.gameDirectory, "scarlet_" + name + ".png",
                    minecraft.gameRenderer.mainRenderTarget(), 1,
                    message -> Scarlet.LOG.info("Showcase: {}", message.getString())), 2);
        }
    }
}
