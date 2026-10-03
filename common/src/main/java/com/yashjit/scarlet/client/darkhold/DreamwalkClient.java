package com.yashjit.scarlet.client.darkhold;

import com.yashjit.scarlet.client.fx.DreamFx;
import com.yashjit.scarlet.client.magic.MindControlClient;
import com.yashjit.scarlet.darkhold.Dreamwalk;
import com.yashjit.scarlet.magic.Magic;
import com.yashjit.scarlet.magic.MagicState;
import com.yashjit.scarlet.magic.Spell;
import com.yashjit.scarlet.network.DreamOptionsPayload;
import com.yashjit.scarlet.network.DreamPayload;
import com.yashjit.scarlet.network.DreamStatePayload;
import com.yashjit.scarlet.platform.Services;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Dreamwalking as this client knows it.
 *
 * <p>For yourself: choosing where your spirit goes, sitting still while your body rises, then looking out through the
 * creature it went into, steering it as Mind Control does, until you wake with the use key. For everyone: which
 * creatures nearby have a spirit in them, so their eyes burn.
 */
public final class DreamwalkClient {

    private static final int NOTHING = -1;

    /** The creature your spirit is in, by id in the level you are in now, whether or not it has reached you yet. */
    private static int into = NOTHING;
    private static boolean away;
    private static double awaySince = -1.0E9;
    private static double wokeAt = -1.0E9;
    private static boolean useWasDown;
    /** Creatures with a spirit in them, and how dark that dreamwalker's magic runs. */
    private static final Int2FloatMap POSSESSED = new Int2FloatOpenHashMap();
    private static @Nullable ClientLevel seenLevel;

    private DreamwalkClient() {
    }

    /**
     * Casting it: the dimensions open up to choose from.
     */
    public static void choose(Minecraft minecraft) {
        if (minecraft.gui.screen() == null) {
            Services.NETWORK.sendToServer(DreamPayload.ASKING);
            minecraft.gui.setScreen(new DreamScreen());
        }
    }

    public static void receive(DreamOptionsPayload payload) {
        if (Minecraft.getInstance().gui.screen() instanceof DreamScreen screen) {
            screen.offered(payload.destinations());
        }
    }

    public static void receive(DreamStatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        double now = minecraft.level == null ? 0.0 : minecraft.level.getGameTime();
        switch (payload.stage()) {
            case DreamStatePayload.AWAY -> {
                if (!away) {
                    awaySince = now;
                }
                away = true;
                into = payload.entityId();
            }
            case DreamStatePayload.POSSESSED -> POSSESSED.put(payload.entityId(), CorruptionClient.darkness(payload.darkness()));
            case DreamStatePayload.RELEASED -> {
                POSSESSED.remove(payload.entityId());
                DreamFx.released(payload.entityId());
            }
            case DreamStatePayload.WOKE -> {
                away = false;
                into = NOTHING;
                wokeAt = now;
                MindControlClient.dreamOut(minecraft);
            }
            default -> {
            }
        }
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level != seenLevel) {
            // ids belong to the level they came from; your own spirit's journey carries on into the next
            seenLevel = level;
            POSSESSED.clear();
        }
        if (level == null || player == null) {
            away = false;
            into = NOTHING;
            return;
        }
        POSSESSED.int2FloatEntrySet().removeIf(entry -> level.getEntity(entry.getIntKey()) == null);
        boolean useDown = minecraft.options.keyUse.isDown() && minecraft.gui.screen() == null;
        if (away) {
            Entity creature = level.getEntity(into);
            if (creature != null && creature.isAlive() && MindControlClient.insideOf() != creature) {
                MindControlClient.dreamInto(minecraft, creature);
            }
            if (useDown && !useWasDown) {
                Services.NETWORK.sendToServer(DreamPayload.WAKING);
            }
        }
        useWasDown = useDown;
    }

    /**
     * Whether your spirit is away from your body.
     */
    public static boolean away() {
        return away;
    }

    public static double awaySince() {
        return awaySince;
    }

    public static double wokeAt() {
        return wokeAt;
    }

    /**
     * Whether your body is sitting down and rising, about to let your spirit go: you sit still for it.
     */
    public static boolean rising(LocalPlayer player) {
        return !away && Magic.state(player).channeling(Spell.DREAMWALK);
    }

    /**
     * How far your body has risen before your spirit leaves it, 0 to 1.
     */
    public static float rise(LocalPlayer player, float partialTick) {
        MagicState state = Magic.state(player);
        if (away || !state.channeling(Spell.DREAMWALK)) {
            return 0.0F;
        }
        double since = player.level().getGameTime() + partialTick - state.channelStart();
        return (float) Math.clamp(since / Dreamwalk.RISE_TICKS, 0.0, 1.0);
    }

    /**
     * How dark the magic of the spirit in this creature runs, or below 0 if there is no spirit in it.
     */
    public static float possessedBy(Entity entity) {
        return POSSESSED.containsKey(entity.getId()) ? POSSESSED.get(entity.getId()) : -1.0F;
    }

    public static Int2FloatMap possessed() {
        return POSSESSED;
    }
}
