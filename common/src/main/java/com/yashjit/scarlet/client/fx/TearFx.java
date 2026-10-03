package com.yashjit.scarlet.client.fx;

import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.client.hex.HexClient;
import com.yashjit.scarlet.client.magic.Hands;
import com.yashjit.scarlet.hex.HexShape;
import com.yashjit.scarlet.hex.HexSnapshot;
import com.yashjit.scarlet.hex.Hexes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Hex being parted, around the opening the wall shader cuts: embers drop from its two burning edges, sparks stream
 * from the caster's palms to the edges they are pulling apart, and it roars and crackles while it stands open.
 */
public final class TearFx {

    /** Within this height above where it was taken hold of, embers fall from the edges where they can be seen. */
    private static final double EMBER_HEIGHT = 18.0;
    private static final Map<UUID, Hum> HUMS = new HashMap<>();

    private TearFx() {
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            HUMS.values().forEach(Hum::end);
            HUMS.clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        double now = level.getGameTime();
        RandomSource random = ScarletFx.random();
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        float density = ScarletFx.density();
        for (HexSnapshot hex : Hexes.clientHexes()) {
            float pulled = HexClient.opening(hex);
            Vec3 taken = hex.tearAt();
            if (taken == null || pulled < 0.0F || taken.distanceToSqr(camera) > 128 * 128) {
                continue;
            }
            if (!HUMS.containsKey(hex.caster())) {
                Hum hum = new Hum(hex);
                HUMS.put(hex.caster(), hum);
                minecraft.getSoundManager().play(hum);
            }
            Vec3 normal = HexShape.outward(hex.center(), taken);
            Vec3 along = new Vec3(-normal.z, 0.0, normal.x);
            // onto the wall where it stands now, in case the Hex has been resized since
            Vec3 held = taken.add(normal.scale(hex.radius() - taken.subtract(hex.center()).dot(normal)));
            // each edge stops short of the corner on its side, as the wall shader draws it
            double offset = held.subtract(hex.center()).dot(along);
            double corner = HexShape.halfSide(hex.radius()) - Hexes.PART_MARGIN;
            float open = Math.min(1.0F, 0.25F + pulled / 6.0F);
            for (int sign = -1; sign <= 1; sign += 2) {
                double across = sign * Math.max(0.0, Math.min(pulled, corner - sign * offset));
                for (int i = 0, n = Math.round(5 * open * density); i < n; i++) {
                    double up = Math.pow(random.nextDouble(), 1.6) * EMBER_HEIGHT - 1.0;
                    Vec3 at = held.add(along.scale(across + (random.nextDouble() - 0.5) * 0.4)).add(0.0, up, 0.0);
                    Vec3 velocity = along.scale(sign * (0.01 + random.nextDouble() * 0.03))
                            .add(normal.scale((random.nextDouble() - 0.5) * 0.06)).add(0.0, random.nextDouble() * 0.03, 0.0);
                    int color = random.nextFloat() < 0.3F ? ScarletPalette.CORE : random.nextBoolean() ? ScarletPalette.BRIGHT_SCARLET : ScarletPalette.SCARLET;
                    ScarletFx.spark(at, velocity, 16 + random.nextInt(18), 0.05F + random.nextFloat() * 0.04F, color, ScarletPalette.CRIMSON, 0.004F, 0.94F);
                }
                if (random.nextFloat() < 0.15F * open) {
                    Vec3 at = held.add(along.scale(across)).add(0.0, random.nextDouble() * 4.0, 0.0);
                    level.playLocalSound(at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.3F, 1.4F + random.nextFloat() * 0.4F, false);
                }
            }
            streamFromPalms(level, hex, held, along, pulled, offset, corner, random);
        }
        HUMS.entrySet().removeIf(entry -> {
            HexSnapshot hex = find(entry.getKey());
            if (hex == null || !hex.parted()) {
                entry.getValue().end();
                return true;
            }
            return false;
        });
    }

    /**
     * While the caster holds it open, sparks fly from each palm to the edge on its side.
     */
    private static void streamFromPalms(ClientLevel level, HexSnapshot hex, Vec3 held, Vec3 along, double pulled, double offset, double corner,
                                        RandomSource random) {
        if (!hex.parting()) {
            return;
        }
        Player caster = level.getPlayerByUUID(hex.caster());
        if (caster == null || caster.isInvisible()) {
            return;
        }
        for (HumanoidArm arm : HumanoidArm.values()) {
            Vec3 palm = Hands.palm(caster, arm);
            // each hand takes the edge on its own side of the body, or the corner once the opening has reached it
            double side = palm.subtract(caster.position()).dot(along) >= 0.0 ? 1.0 : -1.0;
            double across = side * Math.max(0.0, Math.min(pulled, corner - side * offset));
            Vec3 edge = held.add(along.scale(across)).add(0.0, palm.y - held.y, 0.0);
            Vec3 path = edge.subtract(palm);
            double length = path.length();
            if (length < 0.2) {
                continue;
            }
            for (int i = 0; i < 2; i++) {
                double speed = 0.35 + random.nextDouble() * 0.15;
                int life = (int) Math.max(2, Math.min(30, length / speed));
                Vec3 jitter = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(0.04);
                ScarletFx.spark(palm, path.normalize().scale(speed).add(jitter), life, 0.045F, random.nextBoolean() ? ScarletPalette.CORE : ScarletPalette.BRIGHT_SCARLET,
                        ScarletPalette.SCARLET, 0.0F, 1.0F);
            }
        }
    }

    private static @Nullable HexSnapshot find(UUID caster) {
        for (HexSnapshot hex : Hexes.clientHexes()) {
            if (hex.caster().equals(caster)) {
                return hex;
            }
        }
        return null;
    }

    /**
     * The low roar of the wall held open.
     */
    private static final class Hum extends AbstractTickableSoundInstance {

        private final UUID caster;
        private boolean ending;

        Hum(HexSnapshot hex) {
            super(SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            Vec3 at = hex.tearAt() != null ? hex.tearAt() : hex.center();
            this.caster = hex.caster();
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001F;
            this.pitch = 0.5F;
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
        }

        void end() {
            ending = true;
        }

        @Override
        public void tick() {
            HexSnapshot hex = find(caster);
            float open = hex == null ? 0.0F : Math.min(1.0F, Math.max(0.0F, HexClient.opening(hex)) / 8.0F);
            volume = ending ? volume * 0.7F : 0.25F + 0.75F * open;
            pitch = 0.45F + 0.25F * open;
            if (ending && volume < 0.01F) {
                stop();
            }
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
