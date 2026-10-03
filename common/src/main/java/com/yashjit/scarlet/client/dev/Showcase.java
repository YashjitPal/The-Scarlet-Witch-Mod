package com.yashjit.scarlet.client.dev;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.client.ScarletKeyMappings;
import com.yashjit.scarlet.client.anim.FirstPersonGestures;
import com.yashjit.scarlet.client.config.SettingsScreen;
import com.yashjit.scarlet.client.darkhold.DreamwalkClient;
import com.yashjit.scarlet.client.hex.HomePlacement;
import com.yashjit.scarlet.client.hex.ShowrunnerScreen;
import com.yashjit.scarlet.client.magic.MindControlClient;
import com.yashjit.scarlet.darkhold.Dreamwalk;
import com.yashjit.scarlet.entity.DreamBody;
import com.yashjit.scarlet.hex.HexSky;
import com.yashjit.scarlet.network.DreamPayload;
import com.yashjit.scarlet.network.ShowrunnerPayload;
import com.yashjit.scarlet.client.magic.SpellWheel;
import com.yashjit.scarlet.client.magic.TelekinesisClient;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.hex.Residents;
import com.yashjit.scarlet.hex.Sitcom;
import com.yashjit.scarlet.magic.Magic;
import com.yashjit.scarlet.magic.Spell;
import com.yashjit.scarlet.network.CastPayload;
import com.yashjit.scarlet.network.SelectSpellPayload;
import com.yashjit.scarlet.network.ToggleSuitPayload;
import com.yashjit.scarlet.platform.Services;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.Lifecycle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
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
    /** A server to play the scene on instead of the test world, given by {@code -PshowcaseServer=<address>}. */
    private static final @Nullable String SERVER = System.getProperty("scarlet.showcase.server");
    private static final int SETTLE_TICKS = 60;
    /** Half a block on into the Hex, for whoever is stepped in through its wall a little at a time. */
    private static final String STEP_IN = "execute as @a at @s run tp @s ~ ~ ~-0.5";
    private static final String PIG_IN = "execute as @e[tag=scarlet_walker] at @s run tp @s ~ ~ ~-0.5";

    private static State state = State.WAITING_FOR_MENU;
    private static int wait;
    private static int index;
    private static List<Step> steps = List.of();
    private static @Nullable ArmorStand camera;
    private static @Nullable Entity subject;
    /** Where the player stood when a scene marked it, for views and aims placed from there. */
    private static Vec3 anchor = Vec3.ZERO;
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

    /**
     * While a scene plays, the real keyboard and mouse are ignored, so typing elsewhere while its window has focus
     * cannot walk the player off or open screens mid-scene. The scene presses its own keys directly.
     */
    public static boolean ignoresPlayerInput() {
        return enabled() && state != State.DONE;
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
        if (SERVER != null && !SERVER.isBlank()) {
            ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(SERVER), new ServerData("Showcase", SERVER, ServerData.Type.OTHER),
                    false, null);
            return;
        }
        if (minecraft.getLevelSource().levelExists(WORLD)) {
            minecraft.createWorldOpenFlows().openWorld(WORLD, () -> minecraft.gui.setScreen(new TitleScreen()));
            return;
        }
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, WorldOptions.testWorldWithRandomSeed(),
                WorldPresets::createTestWorldDimensions, new TitleScreen());
    }

    /**
     * Every era decoration in one era, set down in a row six blocks in front of the player and facing them: the
     * kitchen to the left, the living room in the middle, the wall hangings to the right against the wall behind.
     */
    private static List<String> decorRow(String era) {
        String e = "era=" + era + ",facing=north";
        List<String> c = new ArrayList<>();
        c.add("execute at @a run setblock ~-12 ~ ~6 scarlet:refrigerator[" + e + ",half=lower]");
        c.add("execute at @a run setblock ~-12 ~1 ~6 scarlet:refrigerator[" + e + ",half=upper]");
        c.add("execute at @a run setblock ~-10 ~ ~6 scarlet:stove[" + e + ",lit=true]");
        c.add("execute at @a run setblock ~-8 ~ ~6 minecraft:smooth_quartz");
        c.add("execute at @a run setblock ~-8 ~1 ~6 scarlet:toaster[" + e + "]");
        c.add("execute at @a run setblock ~-6 ~ ~6 scarlet:television[" + e + ",lit=true]");
        c.add("execute at @a run setblock ~-4 ~ ~6 scarlet:couch[" + e + ",part=left]");
        c.add("execute at @a run setblock ~-3 ~ ~6 scarlet:couch[" + e + ",part=middle]");
        c.add("execute at @a run setblock ~-2 ~ ~6 scarlet:couch[" + e + ",part=right]");
        c.add("execute at @a run setblock ~0 ~ ~6 scarlet:armchair[" + e + "]");
        c.add("execute at @a run setblock ~2 ~ ~6 scarlet:lamp[" + e + ",lit=true]");
        c.add("execute at @a run setblock ~4 ~ ~6 scarlet:radio[" + e + ",lit=true]");
        c.add("execute at @a run setblock ~6 ~ ~6 minecraft:oak_slab[type=top]");
        c.add("execute at @a run setblock ~6 ~1 ~6 scarlet:telephone[" + e + "]");
        c.add("execute at @a run setblock ~8 ~1 ~6 scarlet:wall_clock[" + e + "]");
        c.add("execute at @a run setblock ~10 ~1 ~6 scarlet:picture_frame[" + e + "]");
        c.add("execute at @a run setblock ~12 ~1 ~6 scarlet:poster[" + e + "]");
        return c;
    }

    /**
     * The steps of a scene, or of several one after another, given as {@code a,b,c}.
     */
    private static List<Step> scene(String name) {
        if (name.contains(",")) {
            List<Step> steps = new ArrayList<>();
            for (String part : name.split(",")) {
                steps.addAll(scene(part.strip()));
            }
            return steps;
        }
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
                    .ensureUnsuited().face(0, 0)
                    .look(4.2, 40, 6, 1.2)
                    .jumpTap().jumpTap().shot("lift_a", 0).shot("lift_b", 3).shot("lift_c", 4)
                    .look(4.2, 40, 4, 1.4).shot("hover_a", 14).shot("hover_b", 10)
                    .look(3.2, 100, 2, 1.4).shot("hover_side", 6)
                    .walk(true).then(minecraft -> {
                    }, 10)
                    .look(4.6, 50, 4, 1.3).shot("lean", 0).walk(false)
                    .then(minecraft -> {
                    }, 14)
                    .playerCamera().hideHud(false).face(0, 55).shot("levitate_first_person", 8).face(0, 0).hideHud(true)
                    .jumpTap().jumpTap().look(4.2, 40, 6, 1.0).shot("descend", 10)
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
                    .command("kill @e[type=!minecraft:player]")
                    .command("execute as @a run scarlet hex build nothing")
                    .command("time set noon")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    // cast from the middle of a block, with a line of gold under where the south wall will stand
                    .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5")
                    .command("execute at @a run fill ~-6 ~-1 ~24 ~6 ~-1 ~24 minecraft:gold_block")
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
                    .look(95, 0, 4, 14.0).shot("hex_side_flat", 4)
                    .look(95, 90, 4, 14.0).shot("hex_side_corner", 4)
                    .look(160, 60, 10, 40.0).shot("hex_far", 4)
                    .look(14, 0, -60, 22.0).shot("hex_sky_inside", 4)
                    .look(5, 30, 10, 1.2).shot("hex_inside_third", 4)
                    .playerCamera().hideHud(false).shot("hex_inside", 8).hideHud(true)
                    // walking up to the wall from outside, view bobbing and all: its foot should stay on the gold line
                    .command("execute as @a at @s run tp @s ~ ~ ~31 180 30")
                    .then(minecraft -> {
                    }, 10)
                    .shot("hex_walk_still", 2)
                    .walk(true).shot("hex_walk_a", 4).shot("hex_walk_b", 3).shot("hex_walk_c", 3).shot("hex_walk_d", 3).walk(false)
                    .then(minecraft -> {
                    }, 10)
                    .command("execute as @a at @s run tp @s ~ ~ ~-28 0 0")
                    .hold(true).then(minecraft -> {
                    }, 40)
                    .hold(false).look(52, 25, 14, 1.0).shot("hex_grown", 20)
                    .command("item replace entity @a armor.head with minecraft:air")
                    .shot("hex_warning_a", 20).shot("hex_warning_b", 30)
                    .shot("hex_collapse_a", 55).shot("hex_collapse_b", 15).shot("hex_collapse_c", 15).shot("hex_gone", 30)
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]");
            // the chaos blast up close: in flight side on, three-quarters on and head on, as its caster sees it, bursting on a
            // wall and on a creature, the burn it leaves, and at night
            case "chaos" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .command("time set noon")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                    .command("execute at @a run fill ~-7 ~ ~1 ~7 ~7 ~24 minecraft:air")
                    .command("execute at @a run fill ~-5 ~ ~16 ~5 ~5 ~16 minecraft:smooth_stone")
                    .ensureUnsuited().face(0, 0).select(Spell.CHAOS_BOLT).anchor()
                    .view(-5.0, 1.6, 4.5, 0.0, 1.4, 4.5)
                    .hold(true).shot("chaos_side_a", 2).shot("chaos_side_b", 1).shot("chaos_side_c", 1).hold(false).then(minecraft -> {
                    }, 30)
                    .view(-2.2, 2.3, -2.6, 0.0, 1.3, 7.0)
                    .hold(true).shot("chaos_three_quarter_a", 3).shot("chaos_three_quarter_b", 2).hold(false).then(minecraft -> {
                    }, 30)
                    // over the shoulder, as you usually see yourself: it leaves from the hand, never from behind
                    .playerCamera().camera(CameraType.THIRD_PERSON_BACK).hideHud(true)
                    .hold(true).shot("chaos_behind_a", 1).shot("chaos_behind_b", 1).shot("chaos_behind_c", 1).shot("chaos_behind_d", 1).hold(false)
                    .then(minecraft -> {
                    }, 30)
                    .view(0.6, 1.5, 10.0, 0.0, 1.4, 0.0)
                    .hold(true).shot("chaos_head_on_a", 3).shot("chaos_head_on_b", 1).shot("chaos_head_on_c", 1).hold(false).then(minecraft -> {
                    }, 30)
                    // bursting on the wall and burning it
                    .view(-3.2, 2.2, 12.0, 0.0, 1.4, 16.0)
                    .hold(true).then(minecraft -> {
                    }, 1).hold(false)
                    .shot("chaos_wall_a", 9).shot("chaos_wall_b", 1).shot("chaos_wall_c", 2).shot("chaos_wall_d", 3).shot("chaos_wall_e", 5)
                    .shot("chaos_wall_burn", 15).then(minecraft -> {
                    }, 30)
                    // and on a creature
                    .command("execute at @a run summon minecraft:pig ~ ~ ~7 {NoAI:1b,Invulnerable:1b}")
                    .view(-3.0, 1.8, 4.5, 0.0, 0.8, 7.0)
                    .hold(true).then(minecraft -> {
                    }, 1).hold(false)
                    .shot("chaos_creature_a", 4).shot("chaos_creature_b", 1).shot("chaos_creature_c", 2).shot("chaos_creature_d", 3)
                    .command("kill @e[type=minecraft:pig]")
                    // as its caster sees it
                    .playerCamera().hideHud(true)
                    .hold(true).shot("chaos_cast_a", 2).shot("chaos_cast_b", 1).shot("chaos_cast_c", 2).hold(false).then(minecraft -> {
                    }, 30)
                    // at night
                    .command("time set midnight")
                    .view(-5.0, 1.6, 4.5, 0.0, 1.4, 4.5)
                    .hold(true).shot("chaos_night_a", 2).shot("chaos_night_b", 2).hold(false).then(minecraft -> {
                    }, 30)
                    .command("time set noon")
                    .command("execute at @a run fill ~-5 ~ ~16 ~5 ~5 ~16 minecraft:air");
            // the Hex's wall: spreading, standing seen from far off, up close and edge-on, struck by a bolt, seen faintly from
            // inside, and flaring with static as the era changes
            case "wall" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .command("execute as @a run scarlet hex build nothing")
                    .command("time set noon")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                    .ensureUnsuited().face(0, 0).select(Spell.HEX)
                    .look(44, 25, 14, 1.0)
                    .tap().shot("wall_spreading", 8).shot("wall_spreading_b", 14)
                    .shot("wall_outside", 60)
                    .look(80, 10, 8, 12.0).shot("wall_far", 4)
                    // out past the south wall, looking back at it from six blocks off, then from two, then along it
                    .command("execute as @a at @s run tp @s ~ ~ ~30 180 0")
                    .playerCamera().hideHud(true).shot("wall_close", 10)
                    .command("execute as @a at @s run tp @s ~ ~ ~-3.5")
                    .shot("wall_near", 8)
                    .face(250, 0).shot("wall_grazing", 6)
                    // a bolt through it
                    .face(180, 0).select(Spell.CHAOS_BOLT).tap().shot("wall_struck", 2).shot("wall_struck_after", 10)
                    // from inside, faint
                    .command("execute as @a at @s run tp @s ~ ~ ~-8 0 0")
                    .then(minecraft -> {
                    }, 40).shot("wall_inside", 4)
                    // back outside as the era changes: the whole wall flares with static
                    .command("execute as @a at @s run tp @s ~ ~ ~12 180 0")
                    .then(minecraft -> {
                    }, 40)
                    .remote(ShowrunnerPayload.ERA, 2).shot("wall_era_a", 1).shot("wall_era_b", 2)
                    .select(Spell.HEX)
                    .dispelHexes()
                    .command("execute as @a at @s run tp @s ~ ~ ~-30.5 0 0");
            case "mind" -> s
                    .land()
                    .dispelHexes()
                    .command("difficulty easy")
                    .command("kill @e[type=!minecraft:player]")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                    .command("execute at @a run fill ~-14 ~ ~-14 ~14 ~8 ~14 minecraft:air")
                    .ensureUnsuited().face(0, 0).select(Spell.MIND_CONTROL)
                    .look(7.5, 40, 12, 1.3)
                    // helmeted, so the sun doesn't set them alight, and summoned just before so they are still in front
                    .command("execute at @a run summon minecraft:zombie ~ ~ ~6 {PersistenceRequired:1b,Rotation:[180f,0f],"
                            + "equipment:{head:{id:\"minecraft:leather_helmet\",count:1}}}")
                    .command("execute at @a run summon minecraft:pig ~ ~ ~10 {PersistenceRequired:1b,NoAI:1b}")
                    // taking hold: tendrils into its head, a crown of light, its eyes kindling
                    .hold(true).shot("mind_reach", 4).shot("mind_seize", 6)
                    // the view dives in, looking back at your own body
                    .hideHud(false).then(minecraft -> {
                    }, 5).shot("mind_dive", 0).shot("mind_inside", 22)
                    .turnHeld(180.0F).shot("mind_turned", 4)
                    .walk(true).shot("mind_walk", 12).walk(false)
                    .attack().shot("mind_attack", 2).shot("mind_attack_after", 8)
                    // seen from outside while it is held: the thread from brow to brow
                    .look(9.0, 120, 14, 1.2).hideHud(true).shot("mind_held_outside", 6)
                    .hold(false).shot("mind_released", 2)
                    .look(4.5, 160, 10, 1.6).shot("mind_loyal", 20)
                    // a skeleton held looses its own arrows, once the spell has rested
                    .command("kill @e[type=!minecraft:player]").then(minecraft -> {
                    }, 180)
                    .playerCamera().face(0, 0)
                    .command("execute at @a run summon minecraft:skeleton ~ ~ ~5 {PersistenceRequired:1b,Rotation:[180f,0f],"
                            + "equipment:{head:{id:\"minecraft:iron_helmet\",count:1},mainhand:{id:\"minecraft:bow\",count:1}}}")
                    .hold(true).then(minecraft -> {
                    }, 22).turnHeld(180.0F).then(minecraft -> {
                    }, 4)
                    .attack().then(minecraft -> {
                    }, 2).look(6.0, 100, 10, 1.2).shot("mind_skeleton_shot", 3)
                    .hold(false).then(minecraft -> {
                    }, 10)
                    .command("kill @e[type=!minecraft:player]")
                    .command("difficulty peaceful");
            case "rune" -> s
                    .land()
                    .dispelHexes()
                    .command("difficulty easy")
                    .command("kill @e[type=!minecraft:player]")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                    .command("execute at @a run fill ~-14 ~ ~-14 ~14 ~8 ~14 minecraft:air")
                    .ensureUnsuited().face(0, 0).select(Spell.RUNE_TRAP)
                    // written onto the ground ahead, ring by ring and rune by rune
                    .face(0, 35).look(9.0, 35, 34, 0.0)
                    .tap().shot("rune_writing_a", 1).shot("rune_writing_b", 3).shot("rune_waiting", 14)
                    .look(4.0, 70, 22, 0.0).shot("rune_waiting_near", 2)
                    // something walks onto it, and it springs
                    .command("execute at @a run summon minecraft:zombie ~ ~ ~3 {PersistenceRequired:1b,"
                            + "equipment:{head:{id:\"minecraft:leather_helmet\",count:1}}}")
                    .look(7.0, 40, 16, 0.6).shot("rune_sprung", 2).shot("rune_binding", 8).shot("rune_held", 30)
                    .shot("rune_gone", 70)
                    // cast at a creature, it springs beneath it at once
                    .command("kill @e[type=!minecraft:player]")
                    // the player's own camera, so their new aim reaches the server
                    .playerCamera().face(0, 8).then(minecraft -> {
                    }, 90)
                    .command("execute at @a run summon minecraft:skeleton ~ ~ ~7 {PersistenceRequired:1b,Rotation:[180f,0f],"
                            + "equipment:{head:{id:\"minecraft:iron_helmet\",count:1}}}")
                    .look(8.0, 140, 18, 0.8).tap().shot("rune_snap_a", 4).shot("rune_snap_b", 6)
                    .command("kill @e[type=!minecraft:player]")
                    .command("difficulty peaceful");
            case "eject" -> s
                    .land()
                    .dispelHexes()
                    .command("difficulty easy")
                    .command("kill @e[type=!minecraft:player]")
                    .command("execute as @a run scarlet hex build nothing")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                    .command("execute at @a run fill ~-30 ~ ~-30 ~30 ~10 ~30 minecraft:air")
                    .ensureUnsuited().face(0, 0).select(Spell.HEX)
                    .tap().then(minecraft -> {
                    }, 90)
                    .command("execute at @a run summon minecraft:zombie ~ ~ ~6 {PersistenceRequired:1b,Rotation:[180f,0f],"
                            + "equipment:{head:{id:\"minecraft:leather_helmet\",count:1}}}")
                    // held up in scarlet light, then flung out through the wall
                    .look(9.0, 60, 12, 1.4)
                    .hold(true).shot("eject_lift", 4).shot("eject_held", 14)
                    .hold(false).shot("eject_flung", 3)
                    // the same again watched from outside the wall, once the view has settled out there
                    .command("execute at @a run summon minecraft:zombie ~ ~ ~6 {PersistenceRequired:1b,Rotation:[180f,0f],"
                            + "equipment:{head:{id:\"minecraft:leather_helmet\",count:1}}}")
                    .look(44.0, 75, 16, 4.0).then(minecraft -> {
                    }, 60)
                    .hold(true).shot("eject_outside_held", 14)
                    .hold(false).shot("eject_arc_a", 3).shot("eject_arc_b", 3).shot("eject_crossing", 2).shot("eject_after", 3).shot("eject_landed", 16)
                    .command("execute as @a run scarlet hex dispel").then(minecraft -> {
                    }, 90)
                    .command("kill @e[type=!minecraft:player]")
                    .command("difficulty peaceful");
            case "paint" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .command("execute as @a run scarlet hex build home")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .command("item replace entity @a weapon.offhand with minecraft:air")
                    .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                    .command("execute at @a run fill ~-30 ~ ~-30 ~30 ~12 ~30 minecraft:air")
                    .ensureUnsuited().face(0, 0).select(Spell.HEX).playerCamera()
                    .tap().then(minecraft -> {
                    }, 420)
                    .command("execute as @a run scarlet hex era present").then(minecraft -> {
                    }, 120)
                    // back out from the house, facing it, with bricks in the off hand
                    .command("execute as @a at @s run tp @s ^ ^ ^7 ~180 -8")
                    .command("item replace entity @a weapon.offhand with minecraft:bricks").then(minecraft -> {
                    }, 10)
                    .look(6.0, 150, 12, 1.6).shot("paint_before", 4)
                    .hold(true).turn(-22, 0, 4).shot("paint_stroke_a", 0).turn(44, -8, 14).shot("paint_stroke_b", 0)
                    .look(5.0, 60, 10, 1.4).shot("paint_stroke_side", 2)
                    .look(6.0, 150, 12, 1.6).turn(-44, -8, 14).shot("paint_stroke_c", 0).hold(false).shot("paint_wall", 18)
                    // through their own eyes
                    .playerCamera().hideHud(false).hold(true).shot("paint_first_person", 8).turn(0, 40, 8).shot("paint_first_person_low", 2)
                    .turn(0, -40, 8).hold(false).hideHud(true).then(minecraft -> {
                    }, 10)
                    // the ground at their feet, in moss
                    .command("item replace entity @a weapon.offhand with minecraft:moss_block")
                    .turn(22, 55, 6).look(5.0, 120, 22, 1.0).hold(true).shot("paint_feet_a", 8).look(4.5, 70, 8, 1.0).shot("paint_feet_side", 2)
                    .look(5.0, 120, 22, 1.0).turn(-30, 0, 10).turn(30, 6, 10).hold(false)
                    .look(5.0, 120, 40, 0.0).shot("paint_ground", 16)
                    // the Hex falls: its paint goes back as the wall comes in over it, and the town with it
                    .look(34.0, 170, 22, 2.0).then(minecraft -> {
                    }, 10)
                    .command("execute as @a run scarlet hex dispel").shot("paint_fall_a", 40).shot("paint_fall_b", 30).shot("paint_after", 80)
                    .command("item replace entity @a weapon.offhand with minecraft:air");
            case "home" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .command("execute as @a run scarlet hex build home")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .command("item replace entity @a weapon.offhand with minecraft:air")
                    .command("time set noon")
                    .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                    .command("execute at @a run fill ~-44 ~ ~-44 ~44 ~14 ~44 minecraft:air")
                    .ensureUnsuited().face(0, 0).select(Spell.HEX).playerCamera().anchor()
                    .tap().then(minecraft -> {
                    }, 420)
                    .command("execute as @a run scarlet hex era present")
                    .command("execute as @a at @s run scarlet hex size 40").then(minecraft -> {
                    }, 80)
                    // up over the Hex, looking out at open land to raise the home again on, from the remote
                    .fly().place(4, 12, -22).then(minecraft -> {
                    }, 10)
                    .aimAt(20, -0.5, -4).hideHud(false)
                    .then(HomePlacement::begin, 2).shot("home_aim", 12)
                    .then(minecraft -> HomePlacement.scroll(-1.0), 2).shot("home_turned", 6)
                    .then(minecraft -> HomePlacement.scroll(1.0), 2)
                    .aimAt(70, -0.5, -4).shot("home_outside", 6)
                    .aimAt(20, -0.5, -4).shot("home_aim_back", 6)
                    .then(HomePlacement::use, 2).hideHud(true)
                    // the old home dissolves, and the new one goes up where it was raised
                    .view(-2, 18, -30, 10, 2, -2).shot("home_raise_a", 4).shot("home_raise_b", 14).shot("home_raise_c", 30)
                    .shot("home_raise_d", 60).shot("home_raise_e", 80).shot("home_raise_f", 100).shot("home_raise_g", 120)
                    .view(20, 7, -24, 20, 3, -4).shot("home_raised", 20)
                    // the Hex falls and takes it down, remembering where it stood
                    .view(-2, 18, -30, 10, 2, -2).command("execute as @a run scarlet hex dispel").shot("home_fall_a", 40).shot("home_fall_b", 60)
                    .shot("home_after", 560)
                    // cast again by where it stood: it rises there around its caster
                    .place(20, 1, 6).land().face(180, 0).playerCamera().then(minecraft -> {
                    }, 20)
                    .tap().view(4, 14, -26, 20, 3, -4).shot("home_recall_a", 30).shot("home_recall_b", 120).shot("home_recall_c", 200)
                    .playerCamera();
            case "hometown" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .command("execute as @a run scarlet hex build town")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .command("item replace entity @a weapon.offhand with minecraft:air")
                    .command("time set noon")
                    .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                    .command("execute at @a run fill ~-48 ~ ~-48 ~48 ~16 ~48 minecraft:air")
                    .ensureUnsuited().face(0, 0).select(Spell.HEX).playerCamera().anchor()
                    .tap().then(minecraft -> {
                    }, 420)
                    .command("execute as @a run scarlet hex era present")
                    .command("execute as @a at @s run scarlet hex size 36").then(minecraft -> {
                    }, 420)
                    .fly().view(0, 46, -30, 0, 0, 2).shot("hometown_before", 10)
                    // over the houses and the main street off to one side of the home
                    .place(-10, 12, -26).then(minecraft -> {
                    }, 10)
                    .aimAt(16, -0.5, -10).hideHud(false)
                    .then(HomePlacement::begin, 2).shot("hometown_aim", 14)
                    .then(HomePlacement::use, 2).hideHud(true)
                    .view(0, 46, -30, 0, 0, 2).shot("hometown_way_a", 8).shot("hometown_way_b", 16).shot("hometown_rise_a", 40)
                    .shot("hometown_rise_b", 120).shot("hometown_rise_c", 160)
                    // and the lot it stood on, a house like any other once the home has gone up
                    .shot("hometown_settled", 200).view(16, 8, -26, 16, 3, -8).shot("hometown_home", 4)
                    .command("execute as @a run scarlet hex dispel").view(0, 46, -30, 0, 0, 2).shot("hometown_fall", 60).shot("hometown_after", 640)
                    .playerCamera();
            case "homes" -> {
                s.land()
                        .dispelHexes()
                        .command("kill @e[type=!minecraft:player]")
                        .elsewhere()
                        .command("execute as @a run scarlet hex build home")
                        .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                        .command("item replace entity @a weapon.mainhand with minecraft:air")
                        .command("item replace entity @a weapon.offhand with minecraft:air")
                        .command("time set noon")
                        .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                        .command("execute at @a run fill ~-30 ~ ~-30 ~30 ~16 ~30 minecraft:air")
                        .ensureUnsuited().face(0, 0).select(Spell.HEX).playerCamera().anchor();
                // the same open ground cast on again and again, its town forgotten each time: a home of its own every time
                for (int cast = 0; cast < 5; cast++) {
                    s.command("execute as @a run scarlet hex forget")
                            .place(0, 0.1, 0).land().face(0, 0).playerCamera()
                            .tap().then(minecraft -> {
                            }, 430)
                            .command("execute as @a run scarlet hex era present").then(minecraft -> {
                            }, 150)
                            .view(-12, 8, -19, 0, 6, 1).shot("homes_" + cast, 6)
                            .dispelHexes();
                }
                s.playerCamera();
            }
            // every era decoration in a row against a wall, era by era: the kitchen, the living room, what hangs on the wall
            case "decor" -> {
                s.land()
                        .dispelHexes()
                        .command("kill @e[type=!minecraft:player]")
                        .elsewhere()
                        .command("time set noon")
                        .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                        .command("execute at @a run fill ~-16 ~ ~-10 ~16 ~8 ~10 minecraft:air")
                        .command("execute at @a run fill ~-14 ~-1 ~-4 ~14 ~-1 ~8 minecraft:oak_planks")
                        .command("execute at @a run fill ~-14 ~ ~7 ~14 ~4 ~7 minecraft:smooth_quartz")
                        .anchor();
                for (String era : new String[] {"1950s", "1960s", "1970s", "1980s", "2000s", "present"}) {
                    for (String command : decorRow(era)) {
                        s.command(command);
                    }
                    s.view(0, 5, -8, 0, 1, 6).shot("decor_" + era, 20)
                            .view(-8.5, 2.6, 1.8, -8.5, 1, 6).shot("decor_" + era + "_kitchen", 6)
                            .view(-1.5, 2.4, 1.8, -1.5, 0.8, 6).shot("decor_" + era + "_living", 6)
                            .view(8, 2.6, 1.8, 8, 1.2, 6).shot("decor_" + era + "_wall", 6);
                }
                s.command("time set midnight").view(-2, 3, -2, -2, 1, 6).shot("decor_night", 30).command("time set noon").playerCamera();
            }
            // cars parked inside a Hex that builds nothing, the Hex taken through every era: a new car each time
            case "cars" -> {
                s.land()
                        .dispelHexes()
                        .command("kill @e[type=!minecraft:player]")
                        .elsewhere()
                        .command("execute as @a run scarlet hex build nothing")
                        .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                        .command("item replace entity @a weapon.mainhand with minecraft:air")
                        .command("item replace entity @a weapon.offhand with minecraft:air")
                        .command("time set noon")
                        .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                        .command("execute at @a run fill ~-16 ~ ~-6 ~16 ~6 ~16 minecraft:air")
                        .command("execute at @a run fill ~-16 ~-1 ~-6 ~16 ~-1 ~16 minecraft:smooth_stone")
                        .ensureUnsuited().face(0, 0).select(Spell.HEX).playerCamera().anchor()
                        .tap().then(minecraft -> {
                        }, 120)
                        .command("execute at @a run summon scarlet:parked_car ~-7 ~ ~9 {Rotation:[90f,0f],paint:0}")
                        .command("execute at @a run summon scarlet:parked_car ~0 ~ ~9 {Rotation:[90f,0f],paint:1}")
                        .command("execute at @a run summon scarlet:parked_car ~7 ~ ~9 {Rotation:[90f,0f],paint:2}");
                for (String era : new String[] {"1950s", "1960s", "1970s", "1980s", "2000s", "present"}) {
                    s.command("execute as @a run scarlet hex era " + era).then(minecraft -> {
                            }, 140)
                            .view(0, 4, 0, 0, 0.8, 9).shot("cars_" + era, 4)
                            .view(-5, 2.2, 4.5, 0, 0.8, 9).shot("cars_" + era + "_near", 4);
                }
                s.view(4, 2.5, 13.5, 0, 0.8, 9).shot("cars_back", 4).playerCamera().inventory().shot("cars_inventory", 15);
            }
            // townspeople going about their days in a home: sitting down to the television, chatting, waving to the caster
            case "routines" -> {
                s.land()
                        .dispelHexes()
                        .command("kill @e[type=!minecraft:player]")
                        .elsewhere()
                        .command("difficulty easy")
                        .command("gamerule spawn_mobs false")
                        .command("execute as @a run scarlet hex build home")
                        .command("execute as @a run scarlet hex forget")
                        .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                        .command("item replace entity @a weapon.mainhand with minecraft:air")
                        .command("item replace entity @a weapon.offhand with minecraft:air")
                        .command("time set noon")
                        .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                        .command("execute at @a run fill ~-30 ~ ~-30 ~30 ~16 ~30 minecraft:air")
                        .ensureUnsuited().face(0, 0).select(Spell.HEX).playerCamera().anchor()
                        .tap().then(minecraft -> {
                        }, 430)
                        .command("execute as @a run scarlet hex era 1970s").then(minecraft -> {
                        }, 120)
                        // four townspeople in the living room and two out on the lawn, let fall onto the floor
                        .place(0, 2.5, 0)
                        .command("execute at @a run summon minecraft:zombie ~1 ~ ~1 {PersistenceRequired:1b}")
                        .command("execute at @a run summon minecraft:skeleton ~-1 ~ ~2 {PersistenceRequired:1b}")
                        .command("execute at @a run summon minecraft:zombie ~2 ~ ~-1 {PersistenceRequired:1b}")
                        .command("execute at @a run summon minecraft:creeper ~-2 ~ ~-1 {PersistenceRequired:1b}")
                        .command("execute at @a run summon minecraft:zombie ~3 ~ ~-9 {PersistenceRequired:1b}")
                        .command("execute at @a run summon minecraft:husk ~-3 ~ ~-9 {PersistenceRequired:1b}")
                        .place(0, 0.1, -12).face(0, 0)
                        .view(0, 2.3, -2.6, 0, 1.2, 3.5).shot("routines_a", 300).residents("a")
                        .view(2.6, 1.9, -2.4, -0.5, 0.9, 2.5).shot("routines_a_side", 4)
                        .view(0, 4, -16, 0, 1.5, -8).shot("routines_lawn_a", 4)
                        .view(0, 2.3, -2.6, 0, 1.2, 3.5).shot("routines_b", 300).residents("b")
                        .view(2.6, 1.9, -2.4, -0.5, 0.9, 2.5).shot("routines_b_side", 4)
                        .view(0, 4, -16, 0, 1.5, -8).shot("routines_lawn_b", 4)
                        .place(1.5, 0.1, -6).face(180, 0)
                        .view(0, 2.3, -2.6, 0, 1.2, 3.5).shot("routines_c", 300).residents("c")
                        // someone new out on the sidewalk, too far from any seat to go in, and the caster walking up
                        .place(0, 0.1, -40).face(0, 0)
                        .command("execute at @a run summon minecraft:zombie ~ ~ ~22 {PersistenceRequired:1b}").then(minecraft -> {
                        }, 30)
                        .place(0, 0.1, -21.5).face(0, 0)
                        .view(-2.2, 2.0, -21, 0, 1.5, -18).shot("routines_wave", 14).shot("routines_wave_b", 9).residents("wave")
                        .command("difficulty peaceful").playerCamera();
            }
            // the Darkhold held, read and taking hold: the tome in the hand and opening as it is read, the veins at the
            // edges of the view as the corruption deepens, and a corrupted caster's magic beside a clean one's
            // reading the Darkhold in the first person, the open book before the view
            case "darkread" -> s
                    .land()
                    .command("item replace entity @a weapon.mainhand with scarlet:darkhold")
                    .command("scarlet corruption @p set 0")
                    .face(0, 0).playerCamera().hideHud(false)
                    .hold(true).shot("darkread_a", 20).shot("darkread_b", 20).hold(false)
                    .command("scarlet corruption @p set 0");
            case "darkhold" -> s
                    .land()
                    .command("kill @e[type=minecraft:mannequin]")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with scarlet:darkhold")
                    .command("item replace entity @a weapon.offhand with minecraft:air")
                    .command("scarlet corruption @p set 0")
                    .command("time set noon")
                    .ensureUnsuited().face(0, 0)
                    .playerCamera().hideHud(false).shot("darkhold_held", 12)
                    .inventory().shot("darkhold_inventory", 10).then(minecraft -> minecraft.gui.setScreen(null), 4)
                    // a page is read every three seconds
                    .hold(true).shot("darkhold_reading", 24).shot("darkhold_reading_page", 50).hold(false)
                    .hideHud(true)
                    .look(2.6, 40, 6, 1.2).shot("darkhold_held_front", 6).look(2.6, 120, 6, 1.2).shot("darkhold_held_side", 4)
                    .look(2.6, 30, 10, 1.4).hold(true).shot("darkhold_reading_front", 24)
                    .look(2.4, 90, 8, 1.3).shot("darkhold_reading_side", 6).hold(false)
                    .playerCamera().hideHud(false)
                    .command("scarlet corruption @p set 45").then(minecraft -> {
                    }, 60).shot("darkhold_veins_45", 2)
                    .command("scarlet corruption @p set 90").then(minecraft -> {
                    }, 80).shot("darkhold_veins_90", 2)
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .select(Spell.CHAOS_BOLT).hideHud(true)
                    .look(5.0, 90, 4, 1.3).hold(true).shot("darkhold_bolt_a", 3).shot("darkhold_bolt_b", 2).hold(false).shot("darkhold_bolt_after", 6)
                    .select(Spell.CHAOS_SHIELD).look(3.4, 35, 6, 1.3).hold(true).shot("darkhold_shield", 14).hold(false)
                    .command("scarlet corruption @p set 0").then(minecraft -> {
                    }, 10)
                    .hold(true).shot("darkhold_shield_clean", 14).hold(false)
                    .select(Spell.CHAOS_BOLT);
            // the tome itself: carried, then read, rising out of the hand to float open before the reader and turning
            // its pages, in the first person and from all around, at noon and at midnight, and sinking back into the hand
            case "tome" -> s
                    .land()
                    .command("kill @e[type=minecraft:mannequin]")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with scarlet:darkhold")
                    .command("item replace entity @a weapon.offhand with minecraft:air")
                    .command("scarlet corruption @p set 0")
                    .command("time set noon")
                    .ensureUnsuited().face(0, 0)
                    .then(minecraft -> minecraft.gui.hud.getChat().clearMessages(false), 1)
                    .playerCamera().hideHud(false).shot("tome_carried", 12)
                    .face(30, 25).shot("tome_carried_down", 4).face(0, 0)
                    .inventory().shot("tome_inventory", 10).then(minecraft -> minecraft.gui.setScreen(null), 4)
                    // hiding the HUD hides your hands, and the book is drawn among them
                    .hold(true).shot("tome_rising", 3).shot("tome_opening", 5).shot("tome_open", 24).shot("tome_turning", 25).hold(false)
                    .shot("tome_closing", 3).shot("tome_back", 14)
                    .hideHud(true)
                    .look(2.6, 30, 10, 1.4).hold(true).shot("tome_front", 30)
                    .look(2.4, 90, 8, 1.3).shot("tome_side", 6)
                    .look(2.6, 155, 14, 1.6).shot("tome_behind", 6)
                    .look(1.5, 15, 28, 1.8).shot("tome_close", 6).hold(false)
                    .command("time set midnight").then(minecraft -> {
                    }, 10)
                    .look(2.6, 30, 10, 1.4).hold(true).shot("tome_night", 30)
                    .playerCamera().hideHud(false).shot("tome_night_view", 6).hold(false)
                    .command("time set noon");
            // dreamwalking: choosing where to go, sitting down and rising, the spirit going into a cow by where the player
            // would wake and looking back at the body it left, the body up close, clean and corrupted, and a blow to it
            // snapping the spirit back; then into a piglin in the Nether, and waking with the use key
            case "dream" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .command("difficulty peaceful")
                    .command("time set noon")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .command("item replace entity @a weapon.offhand with minecraft:air")
                    .command("item replace entity @a hotbar.8 with scarlet:darkhold")
                    .command("scarlet corruption @p set 0")
                    .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                    .command("execute at @a run fill ~-12 ~ ~-4 ~12 ~6 ~30 minecraft:air")
                    .command("execute at @a run spawnpoint @a ~ ~ ~20")
                    .command("execute at @a run summon minecraft:cow ~ ~ ~20 {PersistenceRequired:1b,Rotation:[180f,0f]}")
                    .ensureUnsuited().face(0, 0).playerCamera().select(Spell.DREAMWALK).hideHud(false)
                    // casting it: the dimensions to choose from
                    .tap().shot("dream_picker", 12)
                    .then(minecraft -> {
                        if (minecraft.gui.screen() != null) {
                            minecraft.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_1, 0, 0));
                        }
                    }, 0)
                    // sitting down and rising as the dark closes in, then seen from outside
                    .shot("dream_rise_a", 10).shot("dream_rise_b", 12)
                    .look(3.2, 35, 8, 0.9).hideHud(true).shot("dream_rise_outside", 2)
                    // the spirit leaves, and the view opens out of the dark into the cow's
                    .dreamLog("departing").then(minecraft -> {
                    }, 24).dreamLog("away").dreamCamera().hideHud(false).shot("dream_inside_open", 1).shot("dream_inside", 18)
                    .subjectBody().aimHeldAtSubject().shot("dream_looking_back", 6)
                    .walk(true).shot("dream_walk", 30).walk(false).dreamLog("walked")
                    // the cow seen from outside, its eyes burning with the spirit in it
                    .subjectHeld().look(3.4, 30, 4, 1.0).hideHud(true).shot("dream_possessed", 4)
                    // the body it left, up close, clean and then corrupted
                    .subjectBody().look(2.6, 25, 6, 0.8).hideHud(true).shot("dream_body_front", 6)
                    .look(3.0, 115, 16, 0.8).shot("dream_body_side", 4)
                    .look(2.2, 180, 4, 0.9).shot("dream_body_back", 4)
                    .command("scarlet corruption @p set 75").then(minecraft -> {
                    }, 44).look(2.6, 25, 6, 0.8).shot("dream_body_dark", 4)
                    .command("scarlet corruption @p set 0")
                    .dreamCamera().hideHud(false).then(minecraft -> {
                    }, 30).shot("dream_inside_hud", 2)
                    // a blow to the body snaps the spirit back into it
                    .command("damage @e[type=scarlet:dream_body,limit=1] 1")
                    .shot("dream_woke_a", 1).shot("dream_woke_b", 6).dreamLog("woke")
                    .look(3.0, 30, 8, 1.0).hideHud(true).shot("dream_standing", 2)
                    .playerCamera()
                    // a room in the Nether with a piglin in it, where the spirit remembers standing
                    .command("execute in minecraft:the_nether run forceload add 0 0").then(minecraft -> {
                    }, 60)
                    .command("execute in minecraft:the_nether run fill -4 89 -4 4 89 4 minecraft:netherrack")
                    .command("execute in minecraft:the_nether run fill -4 90 -4 4 94 4 minecraft:air")
                    .command("difficulty easy")
                    .command("execute in minecraft:the_nether run summon minecraft:piglin 0 90 3 "
                            + "{PersistenceRequired:1b,IsImmuneToZombification:1b,Rotation:[180f,0f]}")
                    .rememberStanding(Level.NETHER, new BlockPos(0, 90, 0))
                    .then(minecraft -> {
                    }, 160)
                    .face(0, 0).hideHud(false)
                    .then(minecraft -> Services.NETWORK.sendToServer(DreamPayload.go(Level.NETHER)), 0)
                    .then(minecraft -> {
                    }, 110).dreamLog("in the Nether").dreamCamera().shot("dream_nether", 12)
                    .turnHeld(150.0F).shot("dream_nether_turned", 8)
                    // waking with the use key
                    .tap().shot("dream_nether_woke_a", 1).shot("dream_nether_woke_b", 10).dreamLog("home")
                    .command("difficulty peaceful")
                    .command("execute in minecraft:the_nether run forceload remove 0 0")
                    .command("kill @e[type=!minecraft:player]")
                    .command("item replace entity @a hotbar.8 with minecraft:air")
                    .select(Spell.CHAOS_BOLT);
            // the mod's settings: the tiara in the corner of the game's options, the panel it opens, and the keys picking
            // and changing a setting and stepping the quality, each put back as it was
            case "settings" -> s
                    .then(minecraft -> minecraft.gui.setScreen(new OptionsScreen(null, minecraft.options)), 12)
                    .shot("settings_options", 2)
                    .then(SettingsScreen::open, 14)
                    .shot("settings_open", 2)
                    .key(InputConstants.KEY_DOWN).shot("settings_quality", 4)
                    .key(InputConstants.KEY_LEFT).shot("settings_quality_medium", 6)
                    .key(InputConstants.KEY_RIGHT)
                    .key(InputConstants.KEY_DOWN).key(InputConstants.KEY_RETURN).shot("settings_flashing_on", 8)
                    .key(InputConstants.KEY_RETURN)
                    .key(InputConstants.KEY_DOWN).key(InputConstants.KEY_DOWN).key(InputConstants.KEY_DOWN).key(InputConstants.KEY_DOWN)
                    .shot("settings_hex", 6)
                    .then(minecraft -> minecraft.gui.setScreen(null), 4);
            // on a dedicated server (-PshowcaseServer): suited up, a bolt cast, then dreamwalking into a pig nearby,
            // looking back at the body left behind and seeing it close, and a blow to it snapping the spirit back
            case "online" -> s
                    .command("gamemode creative")
                    .command("difficulty peaceful")
                    .command("time set noon")
                    .command("kill @e[type=!minecraft:player]")
                    .command("item replace entity @s armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @s weapon.mainhand with minecraft:air")
                    .command("item replace entity @s hotbar.8 with scarlet:darkhold")
                    .command("execute at @s run spawnpoint @s ~ ~ ~14")
                    .command("execute at @s run summon minecraft:pig ~ ~ ~14 {PersistenceRequired:1b,Rotation:[180f,0f]}")
                    .land().ensureUnsuited().face(0, 0).playerCamera().hideHud(false)
                    .suitUp().shot("online_suited", 40)
                    .select(Spell.CHAOS_BOLT).tap().shot("online_bolt", 1)
                    .select(Spell.DREAMWALK).then(minecraft -> {
                    }, 30)
                    .then(minecraft -> Services.NETWORK.sendToServer(DreamPayload.go(Level.OVERWORLD)), 80)
                    .dreamCamera().shot("online_inside", 10)
                    .subjectBody().aimHeldAtSubject().shot("online_looking_back", 6)
                    .look(2.6, 25, 6, 0.8).hideHud(true).shot("online_body", 6)
                    .dreamCamera().hideHud(false)
                    .command("damage @e[type=scarlet:dream_body,limit=1] 1")
                    .shot("online_woke", 10)
                    .suitUp().then(minecraft -> {
                    }, 40)
                    .command("item replace entity @s hotbar.8 with minecraft:air")
                    .select(Spell.CHAOS_BOLT);
            // leaving the game with the spirit away: quitting, or the game crashing straight after a save
            case "dreamquit", "dreamcrash" -> s
                    .land()
                    .command("kill @e[type=!minecraft:player]")
                    .command("difficulty peaceful")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .command("item replace entity @a hotbar.8 with scarlet:darkhold")
                    .command("execute at @a run spawnpoint @a ~ ~ ~12")
                    .command("execute at @a run summon minecraft:pig ~ ~ ~12 {PersistenceRequired:1b}")
                    .dreamLog("before")
                    .then(minecraft -> Services.NETWORK.sendToServer(DreamPayload.go(Level.OVERWORLD)), 70)
                    .dreamLog("away, leaving")
                    .then(minecraft -> {
                        IntegratedServer server = minecraft.getSingleplayerServer();
                        if (server != null && "dreamcrash".equals(SCENE)) {
                            server.execute(() -> {
                                server.saveEverything(false, true, true);
                                Scarlet.LOG.info("Showcase: saved, crashing with the spirit away");
                                Runtime.getRuntime().halt(0);
                            });
                        }
                    }, 40);
            // where the player is on coming back, after leaving or crashing with the spirit away
            case "dreamcheck" -> s.then(minecraft -> {
            }, 40).dreamLog("on coming back");
            // a home raised on open ground and looked round inside, in three eras: the living room, the kitchen, upstairs
            case "furnished" -> {
                s.land()
                        .dispelHexes()
                        .command("kill @e[type=!minecraft:player]")
                        .elsewhere()
                        .command("execute as @a run scarlet hex build home")
                        .command("execute as @a run scarlet hex forget")
                        .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                        .command("item replace entity @a weapon.mainhand with minecraft:air")
                        .command("item replace entity @a weapon.offhand with minecraft:air")
                        .command("time set noon")
                        .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                        .command("execute at @a run fill ~-30 ~ ~-30 ~30 ~16 ~30 minecraft:air")
                        .ensureUnsuited().face(0, 0).select(Spell.HEX).playerCamera().anchor()
                        .tap().then(minecraft -> {
                        }, 430);
                for (String era : new String[] {"1950s", "1970s", "present"}) {
                    s.command("execute as @a run scarlet hex era " + era).then(minecraft -> {
                            }, 160)
                            .view(0, 2.3, -2.6, 0, 1.2, 3.5).shot("furnished_" + era + "_couch", 6)
                            .view(0.5, 2.0, 2.4, -1.5, 0.9, -2.4).shot("furnished_" + era + "_tv", 4)
                            .view(2.5, 2.0, 2.4, -1.5, 0.9, -2.4).shot("furnished_" + era + "_tv_b", 4)
                            .view(0, 2.4, -1, -4.5, 1.2, 2.5).shot("furnished_" + era + "_kitchen_a", 4)
                            .view(0, 2.4, -1, 4.5, 1.2, 2.5).shot("furnished_" + era + "_kitchen_b", 4)
                            .view(0, 7.3, -1.5, 0, 6.3, 3.5).shot("furnished_" + era + "_upstairs", 4);
                }
                s.playerCamera();
            }
            // the founding from the caster's eyes alone, without the circling view: down onto the floor, where the Hex
            // bursts out of them as they stand
            case "stepout" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .elsewhere()
                    .command("execute as @a run scarlet hex build home")
                    .command("execute as @a run scarlet hex forget")
                    .command("time set noon")
                    .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                    .command("execute at @a run fill ~-24 ~ ~-24 ~24 ~16 ~24 minecraft:air")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .then(minecraft -> com.yashjit.scarlet.config.ScarletClientConfig.get().cinematicFounding = false, 0)
                    .ensureUnsuited().face(0, 0).select(Spell.HEX).playerCamera().anchor()
                    .tap()
                    .shot("step_rising", 290).shot("step_down", 40).shot("step_burst", 12).shot("step_spread", 30)
                    .then(minecraft -> com.yashjit.scarlet.config.ScarletClientConfig.get().cinematicFounding = true, 0)
                    .view(-7, 4, -13, 0, 2, -4).shot("step_after", 4)
                    .playerCamera();
            case "ruin" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .elsewhere()
                    .command("execute as @a run scarlet hex build home")
                    .command("execute as @a run scarlet hex forget")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .command("item replace entity @a weapon.offhand with minecraft:air")
                    .command("time set noon")
                    .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                    .command("execute at @a run fill ~-30 ~ ~-30 ~30 ~16 ~30 minecraft:air")
                    // a ruined cottage just ahead: its floor, crumbling walls fallen in here and there, no roof, weeds
                    // growing up inside it
                    .command("execute at @a run fill ~-4 ~-1 ~3 ~4 ~-1 ~11 minecraft:cobblestone")
                    .command("execute at @a run fill ~-4 ~ ~3 ~4 ~2 ~3 minecraft:mossy_cobblestone")
                    .command("execute at @a run fill ~-4 ~ ~11 ~4 ~3 ~11 minecraft:cobblestone")
                    .command("execute at @a run fill ~-4 ~ ~4 ~-4 ~3 ~10 minecraft:cobblestone")
                    .command("execute at @a run fill ~4 ~ ~4 ~4 ~1 ~10 minecraft:mossy_cobblestone")
                    .command("execute at @a run fill ~-2 ~1 ~3 ~1 ~2 ~3 minecraft:air")
                    .command("execute at @a run fill ~4 ~ ~6 ~4 ~1 ~8 minecraft:air")
                    .command("execute at @a run fill ~-4 ~2 ~7 ~-4 ~3 ~10 minecraft:air")
                    .command("execute at @a run fill ~1 ~2 ~11 ~4 ~3 ~11 minecraft:air")
                    .command("execute at @a run fill ~-3 ~ ~4 ~3 ~ ~10 minecraft:short_grass replace minecraft:air")
                    .ensureUnsuited().face(0, 0).select(Spell.HEX).playerCamera().anchor()
                    .view(-13, 8, -9, 0, 2, 7).shot("ruin_before", 10)
                    // cast through the caster's own view, which the server follows only while it is theirs: carried in
                    // through its fallen front wall, made over and raised around them, and out onto its stoop
                    .playerCamera()
                    .tap().shot("ruin_a", 40).shot("ruin_b", 70).shot("ruin_c", 70).shot("ruin_d", 70).shot("ruin_e", 70)
                    .shot("ruin_land", 25).shot("ruin_out", 25)
                    .view(-13, 9, -9, 0, 5, 7).shot("ruin_front", 40)
                    .view(13, 11, 26, 0, 5, 7).shot("ruin_back", 4)
                    .view(-2.5, 2.4, 9, 2, 2.2, 6).shot("ruin_inside", 4)
                    .view(-2.5, 7.4, 9, 2, 7.2, 6).shot("ruin_upstairs", 4)
                    .playerCamera();
            case "village" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .elsewhere()
                    .command("execute as @a run scarlet hex build home")
                    .command("execute as @a run scarlet hex forget")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .command("item replace entity @a weapon.offhand with minecraft:air")
                    .command("time set noon")
                    .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                    .command("execute at @a run fill ~-30 ~ ~-30 ~30 ~16 ~30 minecraft:air")
                    // a village house standing just ahead, as a village would have it
                    .command("execute at @a run place template minecraft:village/plains/houses/plains_medium_house_1 ~-4 ~-1 ~4")
                    .command("execute at @a run fill ~-8 ~-2 ~ ~12 ~12 ~20 minecraft:air replace minecraft:jigsaw")
                    .ensureUnsuited().face(0, 0).select(Spell.HEX).playerCamera().anchor()
                    .view(-14, 9, -8, 0, 3, 8).shot("village_before", 10)
                    .playerCamera()
                    .tap().shot("village_a", 60).shot("village_b", 120).shot("village_c", 150).shot("village_land", 25).shot("village_out", 25)
                    .view(-14, 9, -8, 0, 3, 8).shot("village_after", 40)
                    .view(14, 10, 26, 0, 4, 8).shot("village_back", 4)
                    .playerCamera();
            case "suburb" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .elsewhere()
                    .command("execute as @a run scarlet hex build town")
                    .command("execute as @a run scarlet hex forget")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .command("item replace entity @a weapon.offhand with minecraft:air")
                    .command("time set noon")
                    .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                    .command("execute at @a run fill ~-48 ~ ~-48 ~48 ~16 ~48 minecraft:air")
                    .ensureUnsuited().face(0, 0).select(Spell.HEX).playerCamera().anchor()
                    .tap().then(minecraft -> {
                    }, 420)
                    .command("execute as @a run scarlet hex era sixties")
                    .command("execute as @a at @s run scarlet hex size 56").then(minecraft -> {
                    }, 560)
                    .fly().view(-6, 42, -40, 0, 0, -6).shot("suburb_over", 10)
                    .view(-34, 2.6, -14, 10, 3.5, -14).shot("suburb_street", 6)
                    .view(36, 9, -44, 4, 4, -24).shot("suburb_across", 6)
                    .view(-11, 6, -21, 0, 6, 0).shot("suburb_home", 6)
                    .playerCamera();
            case "finale" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .command("execute as @a run scarlet hex build town")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .command("item replace entity @a weapon.offhand with minecraft:air")
                    .command("time set noon")
                    .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                    .command("execute at @a run fill ~-48 ~ ~-48 ~48 ~16 ~48 minecraft:air")
                    .ensureUnsuited().face(0, 0).select(Spell.HEX).playerCamera().anchor()
                    .tap().then(minecraft -> {
                    }, 420)
                    // in color, so its slips into black and white show
                    .command("execute as @a run scarlet hex era 1970s")
                    .command("execute as @a at @s run scarlet hex size 32").then(minecraft -> {
                    }, 300)
                    // out on the street in front of it, back to it
                    .place(0, 1, -15).land().face(180, 0)
                    // the Hex falls in on the town, the caster's home last of all
                    .view(14, 9, -22, 0, 4, -1).shot("finale_before", 4)
                    .command("execute as @a run scarlet hex dispel")
                    .shot("finale_fall_a", 50).shot("finale_fall_b", 50)
                    // the wall reaches the home and lets it go: it holds on, glitching through the eras, even once the Hex has gone
                    .shot("finale_reached", 22).shot("finale_glitch_a", 20).shot("finale_glitch_b", 3).shot("finale_glitch_c", 3)
                    .view(7, 5, -15, 0, 5, -2).shot("finale_glitch_d", 24).shot("finale_glitch_e", 3).shot("finale_glitch_f", 3)
                    .view(14, 9, -22, 0, 4, -1).shot("finale_glitch_g", 36).shot("finale_glitch_h", 3).shot("finale_glitch_i", 3)
                    // its caster's clothes slip through the eras with it
                    .look(3.6, 15, 8, 1.1).shot("finale_outfit_a", 4).shot("finale_outfit_b", 3).shot("finale_outfit_c", 3)
                    // then it goes the way it went up, backward, and the land comes back as it was
                    .view(14, 9, -22, 0, 4, -1).shot("finale_going_a", 45).shot("finale_going_b", 50).shot("finale_going_c", 60)
                    .shot("finale_going_d", 60).shot("finale_going_e", 60).shot("finale_going_f", 50)
                    .shot("finale_gone", 60)
                    .playerCamera();
            // a blast inside the Hex mending itself: TNT goes off by a little stone hut with a chest in it, and the crater
            // closes back in from its edge over ten seconds, nothing dropped and the chest still full
            case "mend" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .command("execute as @a run scarlet hex build nothing")
                    .command("time set noon")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                    .command("execute at @a run fill ~-16 ~ ~-16 ~16 ~10 ~16 minecraft:air")
                    .command("execute at @a run fill ~-3 ~ ~7 ~3 ~3 ~12 minecraft:stone_bricks hollow")
                    .command("execute at @a run fill ~ ~ ~7 ~ ~1 ~7 minecraft:air")
                    .command("execute at @a run setblock ~1 ~ ~10 minecraft:chest[facing=north]{Items:[{Slot:0b,id:\"minecraft:diamond\",count:5}]}")
                    .command("execute at @a run setblock ~-2 ~1 ~6 minecraft:wall_torch[facing=north]")
                    .command("execute at @a run setblock ~2 ~ ~6 minecraft:poppy")
                    .ensureUnsuited().face(0, 0).select(Spell.HEX).playerCamera().anchor()
                    .tap().then(minecraft -> {
                    }, 140)
                    .view(-9, 5, 2, 0, 1, 9)
                    .shot("mend_before", 4)
                    .command("execute at @a run summon minecraft:tnt ~2.5 ~ ~5.5 {fuse:30}")
                    .shot("mend_lit", 20).shot("mend_blast", 14)
                    .shot("mend_a", 30).shot("mend_b", 40).shot("mend_c", 40).shot("mend_d", 40).shot("mend_e", 40).shot("mend_done", 40)
                    .command("execute at @a run data get block ~1 ~ ~10 Items")
                    .playerCamera().hideHud(false)
                    .shot("mend_chest", 10);
            // the home a fallen Hex leaves, seen whole from across the street: it steps back through the eras one at a
            // time, each sweeping round it and up it, then goes a block at a time
            case "fallen" -> {
                s.land()
                        .dispelHexes()
                        .command("kill @e[type=!minecraft:player]")
                        .command("execute as @a run scarlet hex build home")
                        .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                        .command("item replace entity @a weapon.mainhand with minecraft:air")
                        .command("time set noon")
                        .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                        .command("execute at @a run fill ~-40 ~ ~-40 ~40 ~16 ~40 minecraft:air")
                        .ensureUnsuited().face(0, 0).select(Spell.HEX).playerCamera().anchor()
                        .tap().then(minecraft -> {
                        }, 420)
                        .command("execute as @a run scarlet hex era 1970s").then(minecraft -> {
                        }, 200)
                        // out across the street from its front, looking back at it
                        .place(0, 1, -22).land()
                        .view(-16, 9, -26, 0, 4, 0)
                        .command("execute as @a run scarlet hex dispel")
                        .shot("fallen_wall", 60).then(minecraft -> {
                        }, 80);
                for (char frame = 'a'; frame <= 'z'; frame++) {
                    s.shot("fallen_" + frame, 8);
                }
                s.shot("fallen_going", 100).shot("fallen_gone", 300).playerCamera();
            }
            case "crossings" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .command("execute as @a run scarlet hex build nothing")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                    .command("execute at @a run fill ~-32 ~ ~-32 ~32 ~12 ~32 minecraft:air")
                    // arrows stuck in the ground where the Hex is about to spread
                    .command("execute at @a run summon minecraft:arrow ~3 ~1.5 ~9 {Motion:[0.0,-1.0,0.0],pickup:1b}")
                    .command("execute at @a run summon minecraft:arrow ~-4 ~1.5 ~13 {Motion:[0.0,-1.0,0.0],pickup:1b}")
                    .command("execute at @a run summon minecraft:arrow ~1 ~1.5 ~17 {Motion:[0.0,-1.0,0.0],pickup:1b}")
                    .ensureUnsuited().face(0, 0).select(Spell.HEX)
                    .look(7.0, 180, 32, 1.0)
                    .tap().shot("rewrite_swept_a", 22).shot("rewrite_swept_b", 10).then(minecraft -> {
                    }, 40)
                    .command("execute as @a run scarlet hex era 1970s").then(minecraft -> {
                    }, 110)
                    // up by the wall, watched from behind the caster and off to one side
                    .command("execute as @a at @s run tp @s ~ ~ ~16")
                    .look(5.0, 215, 9, 1.6).then(minecraft -> {
                    }, 20)
                    .command("execute at @a run summon minecraft:arrow ~ ~2 ~12 {Motion:[0.0,0.05,-2.0],pickup:1b}")
                    .shot("rewrite_arrow", 1).shot("rewrite_petals", 6).shot("rewrite_flower", 30)
                    .command("execute at @a run summon minecraft:fireball ~ ~3 ~14 {Motion:[0.0,0.0,-1.0],acceleration_power:0.0d,ExplosionPower:1b}")
                    .shot("rewrite_fireball", 4).shot("rewrite_firework", 12).shot("rewrite_firework_b", 14)
                    .command("execute at @a run summon minecraft:splash_potion ~ ~3 ~11 {Motion:[0.0,0.1,-0.9],"
                            + "Item:{id:\"minecraft:splash_potion\",count:1,components:{\"minecraft:potion_contents\":{potion:\"minecraft:swiftness\"}}}}")
                    .shot("rewrite_potion", 4).shot("rewrite_bubbles", 14).shot("rewrite_bubbles_b", 30)
                    .command("execute at @a run summon minecraft:tnt ~ ~0.5 ~9 {fuse:80s,Motion:[0.0,0.25,-0.45]}")
                    .shot("rewrite_tnt", 4).shot("rewrite_cake", 12).shot("rewrite_confetti", 26)
                    // what they left on the ground, inside the wall
                    .command("execute as @a at @s run tp @s ~ ~ ~4")
                    .look(4.0, 200, 40, 0.0).shot("rewrite_left", 30)
                    .command("execute as @a run scarlet hex dispel").then(minecraft -> {
                    }, 90)
                    .command("kill @e[type=!minecraft:player]");
            case "sky" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .command("execute as @a run scarlet hex build home")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                    .command("execute at @a run fill ~-30 ~ ~-30 ~30 ~12 ~30 minecraft:air")
                    .ensureUnsuited().face(0, 0).select(Spell.HEX).playerCamera()
                    .tap().then(minecraft -> {
                    }, 420)
                    .command("execute as @a run scarlet hex era present").then(minecraft -> {
                    }, 120)
                    // the remote, beside the view
                    .hideHud(false).then(ShowrunnerScreen::open, 12).shot("sky_remote", 2)
                    .then(minecraft -> minecraft.gui.setScreen(null), 2).hideHud(true)
                    .look(22.0, 160, 18, 2.0).shot("sky_noon", 4)
                    // night falls over the Hex as the day races on to it
                    .remote(ShowrunnerPayload.TIME, HexSky.Time.NIGHT.ordinal()).shot("sky_lapse", 24).shot("sky_night", 70)
                    .remote(ShowrunnerPayload.WEATHER, HexSky.Weather.STORM.ordinal()).shot("sky_storm", 100)
                    .remote(ShowrunnerPayload.WEATHER, HexSky.Weather.CLEAR.ordinal()).remote(ShowrunnerPayload.TIME, HexSky.Time.DUSK.ordinal())
                    .shot("sky_dusk", 120)
                    // from outside the wall, the world's own noon
                    .look(70.0, 160, 14, 2.0).shot("sky_outside", 70)
                    .remote(ShowrunnerPayload.TIME, HexSky.Time.WORLD.ordinal())
                    .command("execute as @a run scarlet hex dispel").then(minecraft -> {
                    }, 100);
            case "town" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=minecraft:pig]")
                    .command("kill @e[type=minecraft:mannequin]")
                    .command("execute as @a run scarlet hex build town")
                    .command("time set noon")
                    // open grassland, with a player's hut standing in one of the lots for the town to leave alone
                    .command("execute at @a run fill ~-24 ~ ~-24 ~24 ~12 ~24 minecraft:air")
                    .command("execute at @a run fill ~-64 ~-1 ~-64 ~64 ~-1 ~64 minecraft:grass_block")
                    .command("execute at @a run fill ~-14 ~ ~3 ~-10 ~3 ~7 minecraft:cobblestone hollow")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .ensureUnsuited().face(0, 0).select(Spell.HEX)
                    .look(17, 180, 24, 2.0)
                    .tap().shot("town_found_a", 12).shot("town_found_b", 22).shot("town_found_c", 24).shot("town_found_d", 24)
                    .shot("town_found_e", 24).shot("town_burst", 16)
                    .look(80, 180, 55, 0.0).shot("town_spread_a", 16).shot("town_spread_b", 30).shot("town_spread_c", 60)
                    .look(9, 200, 12, 1.6).shot("town_home", 40)
                    .hold(true).then(minecraft -> {
                    }, 50)
                    .hold(false)
                    .look(115, 180, 58, 0.0).shot("town_grown_a", 60).shot("town_grown_b", 90)
                    .look(22, 120, 16, 1.5).shot("town_street", 4)
                    .command("execute as @a at @s run scarlet hex era 1970s").shot("town_70s_sweep", 14).shot("town_70s_street", 50)
                    .look(115, 180, 58, 0.0).shot("town_70s", 4)
                    .look(22, 120, 16, 1.5)
                    .command("execute as @a at @s run scarlet hex era 1980s").shot("town_80s_street", 60)
                    .command("execute as @a at @s run scarlet hex era present").shot("town_present_street", 60)
                    .command("execute as @a at @s run scarlet hex era 1950s").shot("town_50s_street", 60)
                    .look(115, 180, 58, 0.0)
                    .sneak(true).hold(true).shot("town_shrinking_a", 8).shot("town_shrinking_b", 8)
                    .look(30, 0, 28, 1.0).shot("town_shrinking_edge_a", 6).shot("town_shrinking_edge_b", 4).shot("town_shrinking_edge_c", 4)
                    .shot("town_shrinking_edge_d", 4)
                    .hold(false).sneak(false).shot("town_shrink_stop", 6)
                    .look(115, 180, 58, 0.0).shot("town_shrunk", 60)
                    .command("execute as @a at @s run scarlet hex dispel").shot("town_collapse_a", 18).shot("town_collapse_b", 16)
                    .shot("town_gone", 50);
            case "founding" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .elsewhere()
                    .command("execute as @a run scarlet hex build town")
                    .command("execute as @a run scarlet hex forget")
                    .command("time set noon")
                    .command("execute at @a run fill ~-24 ~ ~-24 ~24 ~12 ~24 minecraft:air")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .playerCamera()
                    .ensureUnsuited().face(0, 0).select(Spell.HEX)
                    .tap()
                    .shot("found_a", 14).shot("found_b", 26).shot("found_c", 30).shot("found_d", 60).shot("found_e", 60).shot("found_f", 60)
                    .shot("found_g", 40)
                    .shot("found_land", 12).shot("found_landed", 10)
                    .shot("found_burst_a", 3).shot("found_burst_b", 5).shot("found_burst_c", 12).shot("found_burst_d", 26)
                    .shot("found_after", 60);
            case "anchor" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .command("execute as @a run scarlet hex build town")
                    .command("time set noon")
                    .command("execute at @a run fill ~-24 ~ ~-24 ~24 ~12 ~24 minecraft:air")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    // watched from where it stands, not circling: the caster's tendrils and each block landing, up close
                    .then(minecraft -> com.yashjit.scarlet.config.ScarletClientConfig.get().cinematicFounding = false, 0)
                    .ensureUnsuited().face(0, 0).select(Spell.HEX)
                    .tap()
                    .look(7, 150, 12, 2.2).shot("anchor_a", 40).shot("anchor_b", 1)
                    .look(9, 180, 10, 2.4).shot("anchor_c", 50).shot("anchor_d", 1).shot("anchor_e", 1)
                    .look(7, 205, 6, 2.8).shot("anchor_f", 30).shot("anchor_g", 1)
                    .look(12, 165, 24, 3.5).shot("anchor_h", 40).shot("anchor_i", 1)
                    .look(4.5, 60, 4, 2.2).shot("anchor_j", 30).shot("anchor_k", 1)
                    .look(10, 190, 15, 2.0).shot("anchor_l", 40)
                    .shot("anchor_m", 40).shot("anchor_done", 40)
                    .then(minecraft -> com.yashjit.scarlet.config.ScarletClientConfig.get().cinematicFounding = true, 0);
            case "memory" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .command("execute as @a run scarlet hex forget")
                    .command("execute as @a run scarlet hex build town")
                    .command("time set noon")
                    .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                    .command("execute at @a run summon minecraft:marker ~ ~ ~ {Tags:[\"scarlet_origin\"]}")
                    .command("execute at @a run fill ~-24 ~ ~-24 ~24 ~12 ~24 minecraft:air")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .then(minecraft -> com.yashjit.scarlet.config.ScarletClientConfig.get().cinematicFounding = false, 0)
                    .ensureUnsuited().face(0, 0).select(Spell.HEX)
                    // the first town, seen from the same spot high over it each time, once the view has come through the
                    // wall; each Hex grown by holding the cast, as in play
                    .tap().then(minecraft -> {
                    }, 460)
                    .hold(true).then(minecraft -> {
                    }, 58).hold(false).then(minecraft -> {
                    }, 300)
                    .command("execute at @e[tag=scarlet_origin] run tp @a ~ ~ ~ 0 0")
                    .look(150, 180, 55, 0).shot("memory_first", 45)
                    .look(46, 150, 32, 0).shot("memory_first_near", 45)
                    .command("execute as @a run scarlet hex dispel").then(minecraft -> {
                    }, 160)
                    .look(150, 180, 55, 0).shot("memory_gone", 4)
                    // cast again a few blocks off where the home stood, facing another way: carried over to it, and it
                    // rises around them once they are there
                    .command("execute at @e[tag=scarlet_origin] run tp @a ~3 ~ ~2 90 0")
                    .look(14, 200, 18, 1.5)
                    .tap().shot("memory_again_drift", 6).shot("memory_again_over", 10).shot("memory_again_home", 40).then(minecraft -> {
                    }, 400)
                    .hold(true).then(minecraft -> {
                    }, 58).hold(false).then(minecraft -> {
                    }, 300)
                    .command("execute at @e[tag=scarlet_origin] run tp @a ~ ~ ~ 0 0")
                    .look(150, 180, 55, 0).shot("memory_again", 45)
                    .look(46, 150, 32, 0).shot("memory_again_near", 45)
                    .command("execute as @a run scarlet hex dispel").then(minecraft -> {
                    }, 160)
                    // cast far across the old town from its home: the town comes back around the caster
                    .command("execute at @e[tag=scarlet_origin] run tp @a ~36 ~ ~ 180 0")
                    .tap().then(minecraft -> {
                    }, 100)
                    .hold(true).then(minecraft -> {
                    }, 42).hold(false).then(minecraft -> {
                    }, 340)
                    // with no founding, nothing kept the middle of the home clear, so the player waits over the rooftops
                    .fly().command("execute at @e[tag=scarlet_origin] run tp @a ~ ~24 ~ 0 0")
                    .look(150, 180, 55, -24).shot("memory_shifted", 45)
                    .land()
                    .command("execute as @a run scarlet hex dispel").then(minecraft -> {
                    }, 160)
                    // a new town beside the old one, its Hex grown over the edge of it: the old town stays where it stood
                    .command("execute at @e[tag=scarlet_origin] run tp @a ~120 ~ ~ 90 0")
                    .tap().then(minecraft -> {
                    }, 460)
                    .hold(true).then(minecraft -> {
                    }, 70).hold(false).then(minecraft -> {
                    }, 340)
                    .command("execute at @e[tag=scarlet_origin] run tp @a ~84 ~ ~ 0 0")
                    .look(150, 180, 55, 0).shot("memory_overlap", 45)
                    .look(60, 120, 40, 0).shot("memory_overlap_near", 10)
                    .then(minecraft -> com.yashjit.scarlet.config.ScarletClientConfig.get().cinematicFounding = true, 0);
            case "crossing" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .command("execute as @a run scarlet hex build nothing")
                    .command("time set noon")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5")
                    .command("execute at @a run summon minecraft:marker ~ ~ ~ {Tags:[\"scarlet_origin\"]}")
                    .command("execute at @a run fill ~-24 ~ ~-40 ~24 ~12 ~40 minecraft:air")
                    .command("execute at @a run fill ~-4 ~ ~6 ~-1 ~3 ~6 minecraft:red_wool")
                    .command("execute at @a run fill ~1 ~ ~6 ~4 ~3 ~6 minecraft:lime_wool")
                    .command("execute at @a run fill ~-2 ~ ~36 ~2 ~2 ~36 minecraft:blue_wool")
                    .ensureUnsuited().face(0, 0).select(Spell.HEX)
                    // through the caster's own eyes as they cast it: the era just comes over them, nothing comes through
                    .playerCamera().hideHud(true)
                    .tap().shot("cast_view_a", 0).shot("cast_view_b", 2).shot("cast_view_c", 2).shot("cast_view_d", 3).shot("cast_view_e", 6)
                    .then(minecraft -> {
                    }, 120)
                    // walking in, looking in
                    .command("execute at @e[tag=scarlet_origin] run tp @a ~ ~ ~27 180 4")
                    .then(minecraft -> {
                    }, 20)
                    .shot("cross_in_before", 0)
                    .walk(true).shot("cross_in_a", 10).shot("cross_in_b", 1).shot("cross_in_c", 1).shot("cross_in_d", 1).shot("cross_in_e", 1)
                    .shot("cross_in_f", 1).shot("cross_in_g", 1).shot("cross_in_h", 1).shot("cross_in_i", 1).shot("cross_in_j", 1)
                    .shot("cross_in_k", 1).shot("cross_in_l", 1).shot("cross_in_m", 1).walk(false).shot("cross_in_after", 10)
                    // walking out, looking out
                    .command("execute at @e[tag=scarlet_origin] run tp @a ~ ~ ~21 0 4")
                    .then(minecraft -> {
                    }, 30)
                    .walk(true).shot("cross_out_a", 10).shot("cross_out_b", 1).shot("cross_out_c", 1).shot("cross_out_d", 1).shot("cross_out_e", 1)
                    .shot("cross_out_f", 1).shot("cross_out_g", 1).shot("cross_out_h", 1).shot("cross_out_i", 1).shot("cross_out_j", 1)
                    .shot("cross_out_k", 1).shot("cross_out_l", 1).walk(false).shot("cross_out_after", 10)
                    // standing still outside as the wall sweeps out over you, then back in past you
                    .command("execute at @e[tag=scarlet_origin] run tp @a ~ ~ ~28 180 4")
                    .then(minecraft -> {
                    }, 30)
                    .command("execute as @a at @s run scarlet hex size 36")
                    .shot("cross_sweep_a", 2).shot("cross_sweep_b", 1).shot("cross_sweep_c", 1).shot("cross_sweep_d", 1).shot("cross_sweep_e", 1)
                    .shot("cross_sweep_f", 1).shot("cross_sweep_g", 1).shot("cross_sweep_h", 1).shot("cross_sweep_i", 1).shot("cross_sweep_j", 1)
                    .shot("cross_sweep_after", 20)
                    .command("execute as @a at @s run scarlet hex size 24")
                    .shot("cross_shrink_a", 2).shot("cross_shrink_b", 1).shot("cross_shrink_c", 1).shot("cross_shrink_d", 1).shot("cross_shrink_e", 1)
                    .shot("cross_shrink_f", 1).shot("cross_shrink_g", 1).shot("cross_shrink_h", 1).shot("cross_shrink_i", 1).shot("cross_shrink_j", 1)
                    .shot("cross_shrink_after", 20)
                    // walking in, seen from outside: the wall flares red where they pass, the red soaking out around them
                    // as rings run out across it
                    .command("execute at @e[tag=scarlet_origin] run tp @a ~ ~ ~27 180 0")
                    .then(minecraft -> {
                    }, 20)
                    .anchor().view(6.0, 2.5, 3.5, 0.0, 1.5, -3.0)
                    // stepped in half a block at a time, as the free camera keeps the keys from the player
                    .command(STEP_IN).command(STEP_IN).command(STEP_IN).command(STEP_IN).command(STEP_IN).command(STEP_IN)
                    .command(STEP_IN).command(STEP_IN)
                    .shot("cross_seen_a", 0).shot("cross_seen_b", 4).shot("cross_seen_c", 5).shot("cross_seen_d", 8)
                    .shot("cross_seen_after", 20)
                    // and a creature wandering in beside them
                    .command("execute at @e[tag=scarlet_origin] run summon minecraft:pig ~3 ~ ~27 {Tags:[\"scarlet_walker\"],NoAI:1b,Rotation:[180f,0f]}")
                    .then(minecraft -> {
                    }, 10)
                    .command(PIG_IN).command(PIG_IN).command(PIG_IN).command(PIG_IN).command(PIG_IN).command(PIG_IN).command(PIG_IN)
                    .command(PIG_IN)
                    .shot("cross_pig_a", 0).shot("cross_pig_b", 5).shot("cross_pig_c", 8)
                    .playerCamera();
            case "bigtown" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .command("execute as @a run scarlet hex build town")
                    .command("time set noon")
                    .command("execute at @a run fill ~-24 ~ ~-24 ~24 ~12 ~24 minecraft:air")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .ensureUnsuited().face(0, 0).select(Spell.HEX)
                    .tap().then(minecraft -> {
                    }, 360)
                    .command("execute as @a at @s run scarlet hex size 96").then(minecraft -> {
                    }, 900)
                    .command("execute as @a at @s run scarlet hex era present").then(minecraft -> {
                    }, 220)
                    .look(70, 180, 60, 0.0).shot("big_top_a", 4)
                    .look(70, 0, 60, 0.0).shot("big_top_b", 4)
                    .look(30, 180, 30, 1.0).shot("big_main_a", 4)
                    .look(30, 90, 30, 1.0).shot("big_main_b", 4)
                    .look(30, 270, 30, 1.0).shot("big_main_c", 4)
                    .look(55, 45, 35, 1.0).shot("big_far_a", 4)
                    .look(55, 135, 35, 1.0).shot("big_far_b", 4)
                    .look(55, 225, 35, 1.0).shot("big_far_c", 4)
                    .look(55, 315, 35, 1.0).shot("big_far_d", 4);
            case "orchard" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .command("execute as @a run scarlet hex build orchard")
                    .command("time set noon")
                    .command("execute at @a run fill ~-24 ~ ~-24 ~24 ~12 ~24 minecraft:air")
                    // trees standing where the orchard goes, for it to turn into fruit trees
                    .command("execute at @a run place feature minecraft:oak ~12 ~ ~16")
                    .command("execute at @a run place feature minecraft:birch ~-14 ~ ~18")
                    .command("execute at @a run place feature minecraft:oak ~20 ~ ~-14")
                    .command("execute at @a run place feature minecraft:spruce ~-20 ~ ~-16")
                    .command("execute at @a run place feature minecraft:oak ~-8 ~ ~30")
                    .command("execute at @a run place feature minecraft:birch ~28 ~ ~6")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .ensureUnsuited().face(0, 0).select(Spell.HEX)
                    .look(30, 160, 35, 1.0).shot("orchard_before", 4)
                    .tap().then(minecraft -> {
                    }, 360)
                    .command("execute as @a at @s run scarlet hex size 48").then(minecraft -> {
                    }, 520)
                    .command("execute as @a at @s run scarlet hex era 1970s").then(minecraft -> {
                    }, 180)
                    .look(40, 180, 55, 0.0).shot("orchard_top", 4)
                    .look(18, 200, 25, 1.0).shot("orchard_home", 4)
                    .look(30, 20, 30, 1.0).shot("orchard_a", 4)
                    .look(30, 110, 30, 1.0).shot("orchard_b", 4)
                    .look(30, 290, 30, 1.0).shot("orchard_c", 4)
                    .look(30, 160, 35, 1.0).shot("orchard_after", 4);
            case "colorize" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .command("execute as @a run scarlet hex build town")
                    .command("time set noon")
                    .command("execute at @a run fill ~-24 ~ ~-24 ~24 ~12 ~24 minecraft:air")
                    .command("execute at @a run fill ~-64 ~-1 ~-64 ~64 ~-1 ~64 minecraft:grass_block")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .ensureUnsuited().face(0, 0).select(Spell.HEX)
                    .tap().then(minecraft -> {
                    }, 420)
                    .command("execute as @a at @s run scarlet hex era 1960s").then(minecraft -> {
                    }, 160)
                    .command("execute as @a at @s run tp @s ~ ~ ~ 180 8")
                    .look(4, 0, 12, 1.6).shot("color_before", 4)
                    .command("execute as @a at @s run scarlet hex era 1970s")
                    .shot("color_a", 12).shot("color_b", 14).shot("color_c", 14).shot("color_d", 14).shot("color_e", 14).shot("color_f", 20)
                    .shot("color_done", 40)
                    .look(70, 180, 50, 0.0)
                    .command("execute as @a at @s run scarlet hex era 1950s").then(minecraft -> {
                    }, 160)
                    .command("execute as @a at @s run scarlet hex era 1970s")
                    .shot("color_top_a", 24).shot("color_top_b", 24).shot("color_top_c", 24).shot("color_top_d", 40);
            case "residents" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .command("execute as @a run scarlet hex build home")
                    .command("time set noon")
                    .command("difficulty normal")
                    .command("execute at @a run fill ~-24 ~ ~-24 ~24 ~12 ~24 minecraft:air")
                    .command("execute at @a run fill ~-64 ~-1 ~-64 ~64 ~-1 ~64 minecraft:grass_block")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a armor.chest with minecraft:diamond_chestplate")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .ensureUnsuited().face(0, 0).select(Spell.HEX)
                    .look(14, 180, 20, 1.5)
                    .tap().then(minecraft -> {
                    }, 200)
                    .command("execute at @a run summon minecraft:zombie ~3 ~ ~-4")
                    .command("execute at @a run summon minecraft:skeleton ~-3 ~ ~-4")
                    .command("execute at @a run summon minecraft:creeper ~0 ~ ~-6")
                    .command("execute at @a run summon minecraft:spider ~5 ~ ~-1")
                    .command("execute at @a run summon minecraft:witch ~-5 ~ ~-1")
                    .command("execute at @a run summon minecraft:zombie ~2 ~ ~-36 {NoAI:1b}")
                    .shot("residents_rewritten", 30)
                    .look(7, 180, 12, 1.4).shot("residents_close", 20)
                    .look(4, 0, 8, 1.2).shot("residents_outfit", 4)
                    .look(16, 180, 20, 1.5)
                    .command("execute as @a at @s run scarlet hex era 1970s").shot("residents_70s", 50)
                    .command("execute as @a at @s run scarlet hex era 1980s").shot("residents_80s", 50)
                    .command("execute as @a at @s run scarlet hex era 1950s")
                    .command("execute at @a run damage @e[type=minecraft:skeleton,limit=1,sort=nearest] 1 minecraft:player_attack by @p")
                    .shot("residents_struck", 8).shot("residents_struck_b", 20)
                    .look(12, 160, 18, 1.5)
                    .command("execute as @a at @s run tp @s ~ ~ ~-40").shot("residents_left_flicker", 4).shot("residents_left", 20)
                    .command("execute as @a at @s run tp @s ~ ~ ~40")
                    .look(40, 180, 30, 0.0)
                    .command("execute as @a at @s run scarlet hex dispel").shot("residents_fall_a", 25).shot("residents_fall_b", 40)
                    .command("kill @e[type=!minecraft:player]")
                    .command("difficulty peaceful");
            case "episodes" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .command("execute as @a run scarlet hex build nothing")
                    .command("time set noon")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .command("execute at @a run fill ~-1 ~ ~6 ~1 ~2 ~6 minecraft:blue_wool")
                    .ensureUnsuited().face(0, 0).select(Spell.HEX)
                    .playerCamera().hideHud(false)
                    .tap().shot("card_50s_a", 22).shot("card_50s_b", 30).shot("card_50s_c", 40)
                    .command("execute as @a at @s run scarlet hex era 1960s").shot("card_60s_a", 40).shot("card_60s_b", 30)
                    .command("execute as @a at @s run scarlet hex era 1970s").shot("card_70s_a", 40).shot("card_70s_b", 30)
                    .command("execute as @a at @s run scarlet hex era 1980s").shot("card_80s_a", 40).shot("card_80s_b", 30)
                    .command("execute as @a at @s run scarlet hex era 2000s").shot("card_2000s_a", 40).shot("card_2000s_b", 30)
                    .command("execute as @a at @s run scarlet hex era present").shot("card_present_a", 40).shot("card_present_b", 30)
                    .then(minecraft -> {
                    }, 80)
                    .command("execute as @a at @s run scarlet hex name Scarlet Falls")
                    .command("execute as @a at @s run scarlet hex episodes on")
                    .command("time add 24000").shot("card_season_a", 60).shot("card_season_b", 30)
                    .hideHud(true)
                    .command("execute as @a at @s run scarlet hex dispel").then(minecraft -> {
                    }, 80);
            case "spells" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .command("time set noon")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .command("effect give @a minecraft:resistance 300 4 true")
                    .command("execute as @a at @s run tp @s ~ ~ ~ 0 0")
                    .ensureUnsuited().face(0, 0)
                    // the wheel, with Levitation gone from it
                    .playerCamera().hideHud(false).wheel(true).wheelCursor(30, -30).shot("wheel", 6).wheel(false).hideHud(true)
                    // Shockwave, throwing back a ring of pigs
                    .command("execute at @a run summon minecraft:pig ~3 ~ ~2")
                    .command("execute at @a run summon minecraft:pig ~-3 ~ ~2")
                    .command("execute at @a run summon minecraft:pig ~0 ~ ~4")
                    .command("execute at @a run summon minecraft:pig ~2 ~ ~-3")
                    .select(Spell.SHOCKWAVE).look(9.0, 40, 20, 1.0)
                    .tap().shot("shockwave_gather_a", 1).shot("shockwave_gather_b", 3).shot("shockwave_blast", 3).shot("shockwave_wave_a", 2)
                    .shot("shockwave_wave_b", 4).shot("shockwave_after", 10)
                    .then(minecraft -> {
                    }, 90)
                    .playerCamera().face(0, 25).hideHud(false).tap().shot("shockwave_first_person_gather", 3).shot("shockwave_first_person", 6)
                    .hideHud(true)
                    // Red Mist, seen from the side as the caster crosses twelve blocks
                    .command("kill @e[type=minecraft:pig]")
                    .face(90, 0).select(Spell.RED_MIST).look(13.0, -90, 14, 1.0)
                    .tap().shot("mist_out_a", 1).shot("mist_out_b", 2).shot("mist_in_a", 3).shot("mist_in_b", 3).shot("mist_after", 10)
                    .playerCamera().hideHud(false).face(90, 0).then(minecraft -> {
                    }, 60)
                    .tap().shot("mist_first_person_a", 6).shot("mist_first_person_b", 3).hideHud(true)
                    // Telekinesis: a pig lifted, raised and thrown; then a block torn out of the ground
                    .command("execute as @a at @s run tp @s ~ ~ ~ 0 10")
                    .command("execute at @a run summon minecraft:pig ^ ^ ^5")
                    .face(0, 10).select(Spell.TELEKINESIS).look(6.5, 60, 12, 1.3)
                    .hold(true).shot("hold_grab", 6).shot("hold_carry", 10).face(0, -25).shot("hold_lift", 12)
                    .playerCamera().hideHud(false).shot("hold_first_person", 4).hideHud(true).look(6.5, 60, 12, 1.3)
                    .then(minecraft -> TelekinesisClient.attack(), 0).shot("throw_a", 2).shot("throw_b", 4).shot("throw_c", 8).hold(false)
                    .then(minecraft -> {
                    }, 30)
                    .face(0, 55).hold(true).shot("hold_block", 12).face(0, 0).shot("hold_block_up", 12).hold(false).shot("drop_block", 24)
                    // Levitation without the wheel: a double tap of jump, then firing while in the air
                    .command("gamemode survival @a")
                    .face(0, 0).jumpTap().jumpTap().look(5.0, 40, 8, 1.2).shot("levitate_double_tap", 14)
                    .select(Spell.CHAOS_BOLT).tap().shot("levitate_and_bolt", 2)
                    .jumpTap().jumpTap().then(minecraft -> {
                    }, 50)
                    .command("gamemode creative @a");
            case "hexfx" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .command("execute as @a run scarlet hex build town")
                    .command("time set noon")
                    .command("difficulty easy")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                    .ensureUnsuited().face(0, 0).select(Spell.HEX)
                    // the home rises, the Hex bursts out of it and the town builds itself around
                    .tap().then(minecraft -> {
                    }, 320)
                    // townspeople glitching in: husks summoned inside are rewritten within half a second
                    .command("execute at @a run summon minecraft:husk ~1.5 ~ ~-4")
                    .command("execute at @a run summon minecraft:husk ~-1.5 ~ ~-4")
                    .playerCamera().face(180, 12).shot("resident_glitch_a", 6).shot("resident_glitch_b", 4).shot("resident_glitch_c", 4)
                    .shot("resident_after", 24)
                    // chaos blasts bursting on the north wall, fired at it from outside where nothing of the town is in
                    // the way, as the soldiers did
                    .command("execute as @a at @s run tp @s ~ ~ ~-43.5 0 0")
                    // nothing that wandered up meanwhile may stand in the way
                    .command("kill @e[type=minecraft:slime]")
                    .command("execute at @a run kill @e[type=!minecraft:player,distance=..14]")
                    .face(0, -4).select(Spell.CHAOS_BOLT).look(7.0, 155, 8, 1.6)
                    .tap().shot("ripple_a", 3).shot("ripple_b", 4).shot("ripple_c", 6).shot("ripple_d", 12)
                    .tap().tap().tap().shot("ripple_many", 4)
                    .look(10.0, 60, 6, 3.0).tap().shot("ripple_side", 5)
                    // parting it, close up to the wall, from inside
                    .command("execute as @a at @s run tp @s ~ ~ ~24.5 180 0")
                    .face(180, 0).select(Spell.HEX).look(7.0, 150, 14, 1.5)
                    .then(minecraft -> {
                    }, 30)
                    // widened for a little over a second, then let go: it stays as wide as it was left
                    .hold(true).shot("part_seam", 3).shot("part_a", 10).shot("part_b", 12)
                    .hold(false).shot("part_left", 8)
                    .look(12.0, 0, 12, 2.0).shot("part_outside", 4)
                    .look(18.0, 180, 25, 2.0).shot("part_wide", 4)
                    .playerCamera().hideHud(false).shot("part_first_person", 4).face(0, 10).shot("part_slips_a", 6).shot("part_slips_b", 6)
                    .face(180, 0).shot("part_hint", 4).hideHud(true)
                    // sneaking, it closes again
                    .look(7.0, 150, 14, 1.5).sneak(true).hold(true).shot("part_closing", 12)
                    .then(minecraft -> {
                    }, 30)
                    .hold(false).sneak(false).shot("part_closed", 10)
                    // a blow to the caster: what the Hex made slips, here and there
                    .command("execute as @a at @s run tp @s ~ ~ ~12 0 0")
                    .command("gamemode survival @a")
                    .face(0, 0).look(8.0, 180, 30, 2.0)
                    .command("damage @p 8 minecraft:mob_attack")
                    .shot("hurt_a", 1).shot("hurt_b", 1).shot("hurt_c", 1).shot("hurt_d", 1).shot("hurt_e", 1).shot("hurt_f", 1)
                    .shot("hurt_g", 1).shot("hurt_h", 1).shot("hurt_settled", 80)
                    .command("gamemode creative @a")
                    .command("difficulty peaceful")
                    .command("effect give @a minecraft:instant_health 1 5 true");
            case "rewrite" -> s
                    .land()
                    .dispelHexes()
                    .command("kill @e[type=!minecraft:player]")
                    .command("execute as @a run scarlet hex build town")
                    .command("time set noon")
                    .command("difficulty peaceful")
                    .command("execute as @a at @s align xz run tp @s ~0.5 ~ ~0.5 0 0")
                    // open grass with everything the Hex should rewrite on it: a cottage with a full chest, a framed sword
                    // and an armor stand by it, trees, a pile of wool, a pond and a mound
                    .command("execute at @a run fill ~-44 ~ ~-44 ~44 ~24 ~44 minecraft:air")
                    .command("execute at @a run fill ~-48 ~-1 ~-48 ~48 ~-1 ~48 minecraft:grass_block")
                    .command("execute at @a run fill ~-48 ~-4 ~-48 ~48 ~-2 ~48 minecraft:dirt")
                    .command("execute at @a run fill ~-16 ~ ~-20 ~-10 ~3 ~-14 minecraft:cobblestone hollow")
                    .command("execute at @a run fill ~-17 ~4 ~-21 ~-9 ~4 ~-13 minecraft:oak_planks")
                    .command("execute at @a run fill ~-13 ~ ~-14 ~-13 ~1 ~-14 minecraft:air")
                    .command("execute at @a run setblock ~-13 ~ ~-17 minecraft:chest[facing=south]{Items:[{Slot:0b,id:\"minecraft:diamond\",count:12},"
                            + "{Slot:1b,id:\"minecraft:golden_apple\",count:3}]}")
                    .command("execute at @a run summon minecraft:item_frame ~-12 ~1.5 ~-13.47 {Facing:3b,Item:{id:\"minecraft:diamond_sword\",count:1}}")
                    .command("execute at @a run summon minecraft:armor_stand ~6 ~ ~-17")
                    .command("execute at @a run place feature minecraft:oak ~10 ~ ~10")
                    .command("execute at @a run place feature minecraft:birch ~-12 ~ ~10")
                    .command("execute at @a run place feature minecraft:fancy_oak ~17 ~ ~-7")
                    .command("execute at @a run fill ~3 ~ ~14 ~6 ~2 ~17 minecraft:magenta_wool")
                    .command("execute at @a run fill ~-6 ~-2 ~15 ~-2 ~-1 ~19 minecraft:water")
                    .command("execute at @a run fill ~14 ~ ~12 ~19 ~2 ~17 minecraft:dirt")
                    .command("execute at @a run fill ~14 ~3 ~12 ~19 ~3 ~17 minecraft:grass_block")
                    // a village house on the lot beside the home, with a path to the street, and a wheat farm on the other
                    .command("execute at @a run fill ~-14 ~ ~4 ~-8 ~ ~10 minecraft:cobblestone")
                    .command("execute at @a run fill ~-14 ~1 ~4 ~-8 ~3 ~10 minecraft:oak_planks hollow")
                    .command("execute at @a run fill ~-14 ~1 ~4 ~-14 ~3 ~4 minecraft:oak_log")
                    .command("execute at @a run fill ~-8 ~1 ~4 ~-8 ~3 ~4 minecraft:oak_log")
                    .command("execute at @a run fill ~-14 ~1 ~10 ~-14 ~3 ~10 minecraft:oak_log")
                    .command("execute at @a run fill ~-8 ~1 ~10 ~-8 ~3 ~10 minecraft:oak_log")
                    .command("execute at @a run fill ~-13 ~2 ~4 ~-12 ~2 ~4 minecraft:glass_pane")
                    .command("execute at @a run fill ~-10 ~2 ~4 ~-9 ~2 ~4 minecraft:glass_pane")
                    .command("execute at @a run fill ~-14 ~2 ~6 ~-14 ~2 ~8 minecraft:glass_pane")
                    .command("execute at @a run setblock ~-11 ~1 ~4 minecraft:oak_door[facing=north,half=lower]")
                    .command("execute at @a run setblock ~-11 ~2 ~4 minecraft:oak_door[facing=north,half=upper]")
                    .command("execute at @a run fill ~-15 ~4 ~3 ~-7 ~4 ~11 minecraft:oak_slab")
                    .command("execute at @a run setblock ~-12 ~1 ~9 minecraft:chest[facing=north]{Items:[{Slot:0b,id:\"minecraft:emerald\",count:7}]}")
                    .command("execute at @a run setblock ~-9 ~1 ~9 minecraft:crafting_table")
                    .command("execute at @a run setblock ~-10 ~1 ~7 minecraft:lectern")
                    .command("execute at @a run fill ~-11 ~-1 ~0 ~-11 ~-1 ~3 minecraft:dirt_path")
                    .command("execute at @a run fill ~8 ~-1 ~4 ~12 ~-1 ~8 minecraft:farmland")
                    .command("execute at @a run fill ~8 ~ ~4 ~12 ~ ~8 minecraft:wheat[age=7]")
                    .command("execute at @a run setblock ~10 ~ ~6 minecraft:air")
                    .command("execute at @a run setblock ~10 ~-1 ~6 minecraft:water")
                    .command("item replace entity @a armor.head with scarlet:witch_tiara[scarlet:mastery=6400]")
                    .command("item replace entity @a weapon.mainhand with minecraft:air")
                    .ensureUnsuited().face(0, 0).select(Spell.HEX)
                    .look(58, 160, 58, 0.0).shot("rw_before", 6)
                    .look(26, 200, 26, 0.0).shot("rw_before_close", 4)
                    .look(58, 160, 58, 0.0)
                    .tap().shot("rw_found", 40).shot("rw_spread_a", 60).shot("rw_spread_b", 30).shot("rw_spread_c", 30).shot("rw_spread_d", 30)
                    .look(26, 200, 26, 0.0).shot("rw_dissolve_a", 6).shot("rw_dissolve_b", 6)
                    .look(58, 160, 58, 0.0).shot("rw_town", 60).shot("rw_edges_a", 60).shot("rw_edges_b", 80)
                    .look(26, 200, 26, 0.0).shot("rw_town_close", 4)
                    .look(14, 300, 22, 1.0).shot("rw_village_house", 4)
                    .look(14, 60, 22, 1.0).shot("rw_farm", 4)
                    .command("execute at @a if block ~-13 ~ ~-17 minecraft:chest")
                    .command("execute if entity @e[type=minecraft:item_frame]")
                    // the Hex falls, and everything comes back exactly as it was
                    .look(58, 160, 58, 0.0)
                    .command("execute as @a at @s run scarlet hex dispel").shot("rw_collapse", 20).shot("rw_restored", 80)
                    .look(26, 200, 26, 0.0).shot("rw_restored_close", 4)
                    .look(14, 300, 22, 1.0).shot("rw_village_restored", 4)
                    .command("execute at @a run data get block ~-12 ~1 ~9 Items")
                    .command("execute at @a if block ~-11 ~1 ~4 minecraft:oak_door")
                    .command("execute at @a if block ~10 ~ ~5 minecraft:wheat")
                    .command("execute at @a run data get block ~-13 ~ ~-17 Items")
                    .command("execute if entity @e[type=minecraft:item_frame]")
                    .command("execute if entity @e[type=minecraft:armor_stand]")
                    .command("execute at @a if block ~3 ~ ~14 minecraft:magenta_wool")
                    .command("execute at @a if block ~-4 ~-1 ~17 minecraft:water")
                    .command("execute at @a if block ~16 ~3 ~14 minecraft:grass_block")
                    .then(minecraft -> {
                    }, 10);
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

        /**
         * Runs a command on the test world as the server, or on a server played on, as the player (who must be an
         * operator there).
         */
        Scene command(String command) {
            return then(minecraft -> {
                IntegratedServer server = minecraft.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
                } else if (minecraft.player != null) {
                    minecraft.player.connection.sendCommand(command);
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
         * Turns the player's view by the given degrees, evenly over a number of ticks, as a steady sweep of the mouse would.
         */
        Scene turn(float yaw, float pitch, int ticks) {
            for (int i = 0; i < ticks; i++) {
                then(minecraft -> {
                    var player = minecraft.player;
                    if (player != null) {
                        player.setYRot(player.getYRot() + yaw / ticks);
                        player.setXRot(Math.clamp(player.getXRot() + pitch / ticks, -90.0F, 90.0F));
                        player.setYHeadRot(player.getYRot());
                    }
                }, 1);
            }
            return this;
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

        /**
         * Looks out through the creature your spirit is in again, after a free camera looked elsewhere.
         */
        Scene dreamCamera() {
            return then(minecraft -> {
                Entity inside = MindControlClient.insideOf();
                if (inside != null) {
                    minecraft.setCameraEntity(inside);
                    minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                }
            }, 2);
        }

        /**
         * Makes the dreamwalker's body nearest the view the focus of later camera moves.
         */
        Scene subjectBody() {
            return then(minecraft -> {
                Entity viewer = minecraft.getCameraEntity();
                if (minecraft.level == null || viewer == null) {
                    return;
                }
                double best = Double.MAX_VALUE;
                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    if (entity instanceof DreamBody && entity.distanceToSqr(viewer) < best) {
                        best = entity.distanceToSqr(viewer);
                        subject = entity;
                    }
                }
            }, 0);
        }

        /**
         * Makes the creature your spirit or held mind is in the focus of later camera moves.
         */
        Scene subjectHeld() {
            return then(minecraft -> {
                if (MindControlClient.insideOf() != null) {
                    subject = MindControlClient.insideOf();
                }
            }, 0);
        }

        /**
         * Turns the head of what you look out through toward the current subject.
         */
        Scene aimHeldAtSubject() {
            return then(minecraft -> {
                Entity inside = MindControlClient.insideOf();
                if (inside == null || subject == null) {
                    return;
                }
                Vec3 to = subject.position().add(0.0, 0.9, 0.0).subtract(inside.getEyePosition());
                float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
                float pitch = (float) Math.toDegrees(-Math.atan2(to.y, Math.hypot(to.x, to.z)));
                MindControlClient.turn(Mth.wrapDegrees(yaw - MindControlClient.yaw()) / 0.15, (pitch - MindControlClient.pitch()) / 0.15);
            }, 4);
        }

        /**
         * Writes down in the log where the player's spirit is, on both sides, to read against the shots.
         */
        Scene dreamLog(String label) {
            return then(minecraft -> {
                IntegratedServer server = minecraft.getSingleplayerServer();
                if (server == null || minecraft.player == null) {
                    return;
                }
                java.util.UUID id = minecraft.player.getUUID();
                boolean clientAway = DreamwalkClient.away();
                Entity inside = MindControlClient.insideOf();
                String insideName = inside == null ? "nothing" : inside.getType().getDescriptionId();
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    if (player != null) {
                        Entity camera = player.getCamera();
                        int bodies = player.level().getEntities(EntityTypeTest.forClass(DreamBody.class), body -> true).size();
                        Scarlet.LOG.info("Showcase [{}]: away {} (client: away {}, inside {}), in {} as {} at {}, looking out of {} at {}; "
                                        + "{} bodies here, remembered away: {}", label, Dreamwalk.isAway(player), clientAway, insideName,
                                player.level().dimension().identifier(), player.gameMode(), player.blockPosition(), camera.getType().getDescriptionId(),
                                camera.blockPosition(), bodies, Services.PLAYER_DATA.dreamwalk(player).away().isPresent());
                    }
                });
            }, 0);
        }

        /**
         * Has the player's spirit remember standing somewhere, as if they had been there.
         */
        Scene rememberStanding(ResourceKey<Level> dimension, BlockPos pos) {
            return then(minecraft -> {
                IntegratedServer server = minecraft.getSingleplayerServer();
                if (server == null || minecraft.player == null) {
                    return;
                }
                java.util.UUID id = minecraft.player.getUUID();
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    if (player != null) {
                        Services.PLAYER_DATA.setDreamwalk(player, Services.PLAYER_DATA.dreamwalk(player).stoodAt(dimension, pos));
                    }
                });
            }, 2);
        }

        /**
         * Marks where the player stands now, for later views and aims to be placed from.
         */
        Scene anchor() {
            return then(minecraft -> {
                if (minecraft.player != null) {
                    anchor = minecraft.player.position();
                }
            }, 0);
        }

        /**
         * Moves a free camera to a spot measured from the anchor, looking at another.
         */
        Scene view(double x, double y, double z, double targetX, double targetY, double targetZ) {
            return then(minecraft -> {
                if (minecraft.level == null) {
                    return;
                }
                if (camera == null || camera.level() != minecraft.level) {
                    camera = new ArmorStand(minecraft.level, 0, 0, 0);
                    camera.setInvisible(true);
                }
                Vec3 eye = anchor.add(x, y, z);
                Vec3 toTarget = anchor.add(targetX, targetY, targetZ).subtract(eye);
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
         * Puts the player at a spot measured from the anchor, still facing the way they were.
         */
        Scene place(double x, double y, double z) {
            return then(minecraft -> {
                IntegratedServer server = minecraft.getSingleplayerServer();
                if (server != null) {
                    Vec3 at = anchor.add(x, y, z);
                    String command = String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f", at.x, at.y, at.z);
                    server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
                }
            }, 2);
        }

        /**
         * Turns the player to look at a spot measured from the anchor.
         */
        Scene aimAt(double x, double y, double z) {
            return then(minecraft -> {
                var player = minecraft.player;
                if (player == null) {
                    return;
                }
                Vec3 to = anchor.add(x, y, z).subtract(player.getEyePosition());
                float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
                float pitch = (float) Math.toDegrees(-Math.atan2(to.y, Math.hypot(to.x, to.z)));
                player.setYRot(yaw);
                player.setXRot(pitch);
                player.setYHeadRot(yaw);
                player.setYBodyRot(yaw);
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

        /**
         * Writes down in the log where each townsperson near the player is, what they ride and what they are doing,
         * measured from the anchor, to read against the shots.
         */
        Scene residents(String label) {
            return then(minecraft -> {
                IntegratedServer server = minecraft.getSingleplayerServer();
                if (server == null || minecraft.player == null) {
                    return;
                }
                java.util.UUID id = minecraft.player.getUUID();
                Vec3 from = anchor;
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(id);
                    if (player == null) {
                        return;
                    }
                    for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(40), Residents::isResident)) {
                        Vec3 at = mob.position().subtract(from);
                        Entity vehicle = mob.getVehicle();
                        String riding = vehicle == null ? "" : String.format(java.util.Locale.ROOT, " riding %s at y %.2f on %s",
                                vehicle.getType().getDescriptionId(), vehicle.getY() - from.y,
                                player.level().getBlockState(vehicle.blockPosition()).getBlock().getDescriptionId());
                        Scarlet.LOG.info("Showcase [{}]: {} at {} {} {} {}{}{}", label, mob.getType().getDescriptionId(),
                                String.format(java.util.Locale.ROOT, "%.2f", at.x), String.format(java.util.Locale.ROOT, "%.2f", at.y),
                                String.format(java.util.Locale.ROOT, "%.2f", at.z), Sitcom.doing(mob), riding,
                                mob.isBaby() ? " (baby)" : "");
                    }
                });
            }, 0);
        }

        /**
         * Takes the player well away from wherever earlier runs built, to ground nothing has touched, and sets them down
         * on it: a Hex cast by what another scene left standing would make its home of it.
         */
        Scene elsewhere() {
            return command("execute as @a at @s run tp @s ~ ~ ~400").then(minecraft -> {
            }, 60).land();
        }

        /**
         * Stops levitation and creative flight left over from an earlier run, so the player stands on the ground.
         */
        Scene land() {
            return then(minecraft -> {
                if (minecraft.player != null && Magic.state(minecraft.player).levitating()) {
                    Services.NETWORK.sendToServer(new CastPayload(true, Spell.LEVITATION.ordinal()));
                }
            }, 2).then(minecraft -> {
                if (minecraft.player != null && minecraft.player.getAbilities().flying) {
                    minecraft.player.getAbilities().flying = false;
                    minecraft.player.onUpdateAbilities();
                }
            }, 20);
        }

        /**
         * Starts creative flight, so the player stays wherever they are put next.
         */
        Scene fly() {
            return then(minecraft -> {
                if (minecraft.player != null && !minecraft.player.getAbilities().flying) {
                    minecraft.player.getAbilities().flying = true;
                    minecraft.player.onUpdateAbilities();
                }
            }, 2);
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

        /**
         * One press of a key, as the keyboard gives it to whatever screen is open.
         */
        Scene key(int key) {
            return then(minecraft -> {
                if (minecraft.gui.screen() != null) {
                    minecraft.gui.screen().keyPressed(new KeyEvent(key, 0, 0));
                }
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

        /**
         * A press on the Showrunner's remote, as its buttons send it.
         */
        Scene remote(int action, int value) {
            return then(minecraft -> Services.NETWORK.sendToServer(ShowrunnerPayload.of(action, value)), 2);
        }

        /**
         * One click of the attack button, as the left mouse button gives it.
         */
        Scene attack() {
            return then(minecraft -> KeyMapping.click(InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_LEFT)), 1);
        }

        /**
         * Turns the head of the mind you hold, as moving the mouse would.
         */
        Scene turnHeld(float degrees) {
            return then(minecraft -> MindControlClient.turn(degrees / 0.15F, 0.0), 2);
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
