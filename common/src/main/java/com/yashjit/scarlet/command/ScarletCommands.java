package com.yashjit.scarlet.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.hex.Hex;
import com.yashjit.scarlet.hex.HexBuild;
import com.yashjit.scarlet.hex.HexData;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.platform.Services;
import java.util.Arrays;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * {@code /scarlet hex era|build|name|episodes|dispel}: the Hex's controls as commands, for casters and server operators.
 *
 * <ul>
 *     <li>{@code era <era>} moves your Hex to another era, beginning its next episode. Operators can change the Hex
 *     they stand in, here and for {@code name} and {@code episodes}.</li>
 *     <li>{@code build <town|home|nothing>} chooses what your next Hex builds.</li>
 *     <li>{@code name <name>} names your Hex, for its title card.</li>
 *     <li>{@code episodes <on|off>} lets the era move on by itself every morning.</li>
 *     <li>{@code dispel} brings your Hex down; {@code dispel all} takes down every Hex in the dimension at once.</li>
 * </ul>
 */
public final class ScarletCommands {

    private ScarletCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("scarlet")
                .then(Commands.literal("hex")
                        .then(Commands.literal("era")
                                .then(Commands.argument("era", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(Era.values()).map(Era::getSerializedName), builder))
                                        .executes(context -> era(context.getSource(), StringArgumentType.getString(context, "era")))))
                        .then(Commands.literal("build")
                                .then(Commands.argument("what", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(HexBuild.values()).map(HexBuild::getSerializedName), builder))
                                        .executes(context -> build(context.getSource(), StringArgumentType.getString(context, "what")))))
                        .then(Commands.literal("name")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> name(context.getSource(), StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("episodes")
                                .then(Commands.literal("on").executes(context -> episodes(context.getSource(), true)))
                                .then(Commands.literal("off").executes(context -> episodes(context.getSource(), false))))
                        .then(Commands.literal("dispel")
                                .executes(context -> dispel(context.getSource()))
                                .then(Commands.literal("all")
                                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                        .executes(context -> dispelAll(context.getSource()))))));
    }

    private static int era(CommandSourceStack source, String name) {
        Era era = Arrays.stream(Era.values()).filter(value -> value.getSerializedName().equals(name)).findFirst().orElse(null);
        if (era == null) {
            source.sendFailure(Component.translatable("command.scarlet.hex.no_such_era", name));
            return 0;
        }
        Hex hex = target(source);
        if (hex == null) {
            source.sendFailure(Component.translatable("command.scarlet.hex.no_hex"));
            return 0;
        }
        Hexes.setEra(source.getLevel(), hex, era);
        source.sendSuccess(() -> Component.translatable("command.scarlet.hex.era", hex.name(), era.displayName()), true);
        return 1;
    }

    private static int build(CommandSourceStack source, String name) {
        HexBuild build = Arrays.stream(HexBuild.values()).filter(value -> value.getSerializedName().equals(name)).findFirst().orElse(null);
        ServerPlayer player = source.getPlayer();
        if (build == null || player == null) {
            source.sendFailure(Component.translatable("command.scarlet.hex.no_such_build", name));
            return 0;
        }
        Services.PLAYER_DATA.set(player, Services.PLAYER_DATA.get(player).withHexBuild(build));
        source.sendSuccess(() -> Component.translatable("command.scarlet.hex.build", build.displayName()), false);
        return 1;
    }

    private static int name(CommandSourceStack source, String name) {
        String trimmed = name.strip();
        if (trimmed.isEmpty() || trimmed.length() > Hex.MAX_NAME_LENGTH) {
            source.sendFailure(Component.translatable("command.scarlet.hex.bad_name", Hex.MAX_NAME_LENGTH));
            return 0;
        }
        Hex hex = target(source);
        if (hex == null) {
            source.sendFailure(Component.translatable("command.scarlet.hex.no_hex"));
            return 0;
        }
        Hexes.rename(source.getLevel(), hex, trimmed);
        source.sendSuccess(() -> Component.translatable("command.scarlet.hex.name", trimmed), true);
        return 1;
    }

    private static int episodes(CommandSourceStack source, boolean on) {
        Hex hex = target(source);
        if (hex == null) {
            source.sendFailure(Component.translatable("command.scarlet.hex.no_hex"));
            return 0;
        }
        Hexes.setEpisodes(source.getLevel(), hex, on);
        source.sendSuccess(() -> Component.translatable(on ? "command.scarlet.hex.episodes_on" : "command.scarlet.hex.episodes_off",
                hex.name()), true);
        return 1;
    }

    private static int dispel(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        Hex hex = player == null ? null : HexData.of(source.getLevel()).byCaster(player.getUUID());
        if (hex == null) {
            source.sendFailure(Component.translatable("command.scarlet.hex.no_hex"));
            return 0;
        }
        Hexes.release(source.getLevel(), hex);
        source.sendSuccess(() -> Component.translatable("command.scarlet.hex.dispel", hex.name()), false);
        return 1;
    }

    private static int dispelAll(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        int count = HexData.of(level).all().size();
        Hexes.dispelAll(level);
        source.sendSuccess(() -> Component.translatable("command.scarlet.hex.dispel_all", count), true);
        return count;
    }

    /**
     * The caster's own Hex in this dimension, or for an operator, the one they stand in.
     */
    private static @Nullable Hex target(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            Hex own = HexData.of(source.getLevel()).byCaster(player.getUUID());
            if (own != null) {
                return own;
            }
        }
        if (Commands.LEVEL_GAMEMASTERS.check(source.permissions())) {
            return Hexes.at(source.getLevel(), source.getPosition());
        }
        return null;
    }
}
