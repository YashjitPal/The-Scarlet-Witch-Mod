package com.yashjit.scarlet.client.magic;

import com.yashjit.scarlet.magic.Magic;
import com.yashjit.scarlet.magic.Spell;
import com.yashjit.scarlet.network.HoldPayload;
import com.yashjit.scarlet.network.TelekinesisPayload;
import com.yashjit.scarlet.platform.Services;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Who holds what with Telekinesis, as told by the server, and your own hold on things: while you hold something the
 * scroll wheel draws it nearer or pushes it away instead of changing slot, and attacking throws it.
 */
public final class TelekinesisClient {

    private static final Int2IntMap HELD = new Int2IntOpenHashMap();

    static {
        HELD.defaultReturnValue(HoldPayload.NOTHING);
    }

    private TelekinesisClient() {
    }

    public static void receive(HoldPayload payload) {
        if (payload.targetId() == HoldPayload.NOTHING) {
            HELD.remove(payload.casterId());
        } else {
            HELD.put(payload.casterId(), payload.targetId());
        }
    }

    public static Int2IntMap held() {
        return HELD;
    }

    /**
     * What a player is holding, if anything is known to be held and is in view.
     */
    public static @Nullable Entity heldBy(Player player) {
        int id = HELD.get(player.getId());
        return id == HoldPayload.NOTHING ? null : player.level().getEntity(id);
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            HELD.clear();
            return;
        }
        // a channel that ended without word reaching us, or a caster who left view
        HELD.int2IntEntrySet().removeIf(entry -> !(level.getEntity(entry.getIntKey()) instanceof Player caster)
                || !Magic.state(caster).channeling(Spell.TELEKINESIS));
    }

    /**
     * @return true if the scroll went to what you hold
     */
    public static boolean scroll(double amount) {
        if (!holding() || amount == 0.0) {
            return false;
        }
        Services.NETWORK.sendToServer(new TelekinesisPayload(TelekinesisPayload.PULL, (float) amount));
        return true;
    }

    /**
     * @return true if attacking threw what you hold, rather than swinging at something
     */
    public static boolean attack() {
        if (!holding()) {
            return false;
        }
        Services.NETWORK.sendToServer(new TelekinesisPayload(TelekinesisPayload.THROW, 0.0F));
        return true;
    }

    /**
     * Whether you are channeling Telekinesis at all, held or still reaching: breaking blocks waits until you stop.
     */
    public static boolean channeling() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && Magic.state(player).channeling(Spell.TELEKINESIS);
    }

    private static boolean holding() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && Minecraft.getInstance().gui.screen() == null && Magic.state(player).channeling(Spell.TELEKINESIS)
                && HELD.get(player.getId()) != HoldPayload.NOTHING;
    }
}
