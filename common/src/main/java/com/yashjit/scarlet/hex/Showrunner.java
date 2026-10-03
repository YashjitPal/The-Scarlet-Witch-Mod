package com.yashjit.scarlet.hex;

import com.yashjit.scarlet.crown.CrownItem;
import com.yashjit.scarlet.magic.Mastery;
import com.yashjit.scarlet.magic.Spell;
import com.yashjit.scarlet.network.HomePayload;
import com.yashjit.scarlet.network.ShowrunnerPayload;
import com.yashjit.scarlet.platform.Services;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The Showrunner's remote, server side: what a caster changes about their Hex from it, as long as they wear a crown
 * that has mastered the Hex.
 */
public final class Showrunner {

    private Showrunner() {
    }

    public static void handle(ServerPlayer player, ShowrunnerPayload payload) {
        if (!CrownItem.isWearingCrown(player) || Mastery.rank(player) < Spell.HEX.rank()) {
            return;
        }
        if (payload.action() == ShowrunnerPayload.BUILD) {
            Services.PLAYER_DATA.set(player, Services.PLAYER_DATA.get(player).withHexBuild(HexBuild.byIndex(payload.value())));
            return;
        }
        ServerLevel level = player.level();
        Hex hex = HexData.of(level).byCaster(player.getUUID());
        if (hex == null || hex.phase == Hex.Phase.COLLAPSING) {
            return;
        }
        switch (payload.action()) {
            case ShowrunnerPayload.ERA -> Hexes.setEra(level, hex, Era.byIndex(payload.value()));
            case ShowrunnerPayload.EPISODES -> Hexes.setEpisodes(level, hex, payload.value() != 0);
            case ShowrunnerPayload.TIME -> Hexes.setSky(level, hex, hex.sky.withTime(HexSky.Time.byIndex(payload.value())));
            case ShowrunnerPayload.WEATHER -> Hexes.setSky(level, hex, hex.sky.withWeather(HexSky.Weather.byIndex(payload.value())));
            case ShowrunnerPayload.NAME -> {
                String name = payload.text().strip();
                if (name.isEmpty() || name.length() > Hex.MAX_NAME_LENGTH) {
                    return;
                }
                Hexes.rename(level, hex, name);
            }
            default -> {
                return;
            }
        }
        // a click of the dial, heard by the caster
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.25F, 1.4F);
    }

    public static void raiseHome(ServerPlayer player, HomePayload payload) {
        if (CrownItem.isWearingCrown(player) && Mastery.rank(player) >= Spell.HEX.rank()) {
            Hexes.raiseHome(player, payload.lot());
        }
    }
}
