package com.yashjit.scarlet.client.hex;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.client.fx.ScarletFx;
import com.yashjit.scarlet.hex.Era;
import com.yashjit.scarlet.hex.HexSnapshot;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.network.ResidentsPayload;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.ClientAsset;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Which mobs this client draws as a Hex's townspeople, and how each one looks: dressed for the era of the Hex it
 * lives in, always the same person for the same mob. When one is rewritten or turns back it flickers between its two
 * selves for a moment, in a burst of TV static.
 */
public final class ResidentsClient {

    /** Townspeople per era; the last half have slim arms. */
    public static final int PEOPLE = 6;
    private static final int FLICKER_TICKS = 12;

    private static IntOpenHashSet residents = new IntOpenHashSet();
    /** When each mob last changed between its two selves. */
    private static final Int2LongOpenHashMap CHANGED = new Int2LongOpenHashMap();
    private static final Map<Integer, PlayerSkin> SKINS = new HashMap<>();
    private static @Nullable ClientLevel seenLevel;

    private ResidentsClient() {
    }

    public static void receive(ResidentsPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        forgetIfLeft(minecraft.level);
        long now = minecraft.level != null ? minecraft.level.getGameTime() : 0L;
        IntOpenHashSet next = new IntOpenHashSet(payload.ids());
        for (int id : residents) {
            if (!next.contains(id)) {
                CHANGED.put(id, now);
            }
        }
        for (int id : next) {
            if (!residents.contains(id)) {
                CHANGED.put(id, now);
            }
        }
        residents = next;
    }

    public static void tick(Minecraft minecraft) {
        forgetIfLeft(minecraft.level);
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused() || CHANGED.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        RandomSource random = ScarletFx.random();
        var iterator = CHANGED.int2LongEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            long age = now - entry.getLongValue();
            if (age > FLICKER_TICKS) {
                iterator.remove();
                continue;
            }
            Entity entity = level.getEntity(entry.getIntKey());
            if (entity == null) {
                continue;
            }
            // static crawls over them while they change
            for (int i = 0; i < Math.round(3 * ScarletFx.density()); i++) {
                Vec3 at = entity.position().add((random.nextFloat() - 0.5F) * entity.getBbWidth() * 1.4F, random.nextFloat() * 1.9F,
                        (random.nextFloat() - 0.5F) * entity.getBbWidth() * 1.4F);
                int grey = 0x9A9A9A + random.nextInt(0x40) * 0x010101;
                ScarletFx.spark(at, new Vec3(0, 0.005, 0), 3 + random.nextInt(4), 0.035F, 0xFFFFFF, grey, 0.0F, 0.8F);
            }
        }
    }

    private static void forgetIfLeft(@Nullable ClientLevel level) {
        if (level != seenLevel) {
            seenLevel = level;
            residents = new IntOpenHashSet();
            CHANGED.clear();
        }
    }

    /**
     * Whether to draw a mob as a townsperson right now. Mid-change it flicks between its two selves, settling on the
     * new one.
     */
    public static boolean drawsAsResident(Entity entity) {
        boolean resident = residents.contains(entity.getId());
        long changed = CHANGED.getOrDefault(entity.getId(), Long.MIN_VALUE);
        if (changed != Long.MIN_VALUE && seenLevel != null) {
            long age = seenLevel.getGameTime() - changed;
            if (age >= 0 && age < FLICKER_TICKS && (age / 2) % 2 == 0 && age < FLICKER_TICKS - 3) {
                return !resident;
            }
        }
        return resident;
    }

    /**
     * Who a resident is: dressed for its Hex's era, and always the same person for the same mob.
     */
    public static PlayerSkin skin(Entity entity) {
        Era era = eraAt(entity.position());
        int person = Math.floorMod(entity.getUUID().hashCode(), PEOPLE);
        return SKINS.computeIfAbsent(era.ordinal() * PEOPLE + person, key -> PlayerSkin.insecure(
                new ClientAsset.ResourceTexture(Scarlet.id("entity/resident/" + name(era) + "_" + person)), null, null,
                person >= PEOPLE / 2 ? PlayerModelType.SLIM : PlayerModelType.WIDE));
    }

    public static Era eraAt(Vec3 point) {
        double now = seenLevel != null ? seenLevel.getGameTime() : 0.0;
        HexSnapshot hex = Hexes.clientHexAt(point, now);
        return hex != null ? hex.eraValue() : Era.FIFTIES;
    }

    /** The era's name in texture paths. */
    public static String name(Era era) {
        return switch (era) {
            case FIFTIES -> "fifties";
            case SIXTIES -> "sixties";
            case SEVENTIES -> "seventies";
            case EIGHTIES -> "eighties";
            case TWO_THOUSANDS -> "two_thousands";
            case PRESENT -> "present";
        };
    }
}
