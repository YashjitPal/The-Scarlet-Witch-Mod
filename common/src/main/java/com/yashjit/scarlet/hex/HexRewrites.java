package com.yashjit.scarlet.hex;

import com.yashjit.scarlet.network.RewritePayload;
import com.yashjit.scarlet.platform.Services;
import java.util.Collection;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What comes into a Hex is rewritten to fit the show, the moment it crosses the wall or the wall comes over it.
 *
 * <ul>
 *     <li>Arrows become flowers of the era. One that could have been picked up leaves a flower that can be; any other
 *     only scatters petals, so the Hex never makes something out of nothing.</li>
 *     <li>Fireballs, skulls and shulker bullets burst into fireworks in the era's colors.</li>
 *     <li>Thrown potions and spit become soap bubbles, colored like what they were.</li>
 *     <li>Lit TNT becomes a cake, with confetti.</li>
 * </ul>
 *
 * <p>Only what comes in is rewritten: what is thrown or shot inside a Hex stays as it is, and so does what leaves it.
 * Tridents, wind charges and fireworks pass in untouched.
 */
public final class HexRewrites {

    /** How strongly the wall ripples where something comes through it to be rewritten. */
    private static final float RIPPLE = 0.45F;
    private static final double SEEN_FROM = 128.0;

    /** The flowers each era's arrows become, with the color of their petals. */
    private static final Bloom[][] BLOOMS = {
            // roses and daisies in the yard
            {new Bloom(Items.POPPY, 0xD92B2B), new Bloom(Items.OXEYE_DAISY, 0xF2F0E4), new Bloom(Items.RED_TULIP, 0xD8432E),
                    new Bloom(Items.PINK_TULIP, 0xF2A7C3)},
            // flower power
            {new Bloom(Items.DANDELION, 0xFFD43B), new Bloom(Items.AZURE_BLUET, 0xE8EEF4), new Bloom(Items.WHITE_TULIP, 0xEFEFEA),
                    new Bloom(Items.ORANGE_TULIP, 0xF28C2C)},
            // harvest gold and burnt orange
            {new Bloom(Items.ORANGE_TULIP, 0xF28C2C), new Bloom(Items.SUNFLOWER, 0xFFC21C), new Bloom(Items.DANDELION, 0xFFD43B)},
            // neon
            {new Bloom(Items.PINK_TULIP, 0xF2A7C3), new Bloom(Items.ALLIUM, 0xB45FD8), new Bloom(Items.CORNFLOWER, 0x4C6FE3)},
            {new Bloom(Items.BLUE_ORCHID, 0x2EA9E8), new Bloom(Items.LILY_OF_THE_VALLEY, 0xF7F7F2), new Bloom(Items.CORNFLOWER, 0x4C6FE3)},
            {new Bloom(Items.POPPY, 0xD92B2B), new Bloom(Items.CORNFLOWER, 0x4C6FE3), new Bloom(Items.OXEYE_DAISY, 0xF2F0E4),
                    new Bloom(Items.ALLIUM, 0xB45FD8)}
    };
    private static final Bloom GLOWING = new Bloom(Items.TORCHFLOWER, 0xF5A33C);
    private static final int SPIT = 0xF0F0EA;

    private HexRewrites() {
    }

    /**
     * After each entity's tick on the server: whatever has just come into a Hex is rewritten, whether it flew in or the
     * wall came out over it.
     */
    public static void ticked(ServerLevel level, Entity entity) {
        int kind = kind(entity);
        if (kind < 0 || entity.isRemoved()) {
            return;
        }
        Collection<Hex> hexes = HexData.of(level).all();
        if (hexes.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Vec3 from = entity.oldPosition();
        Vec3 to = entity.position();
        for (Hex hex : hexes) {
            float wall = Hexes.wallRadius(hex, now);
            if (wall < 1.0F || !HexShape.contains(hex.center, wall, to)
                    || HexShape.contains(hex.center, Hexes.wallRadius(hex, now - 1), from)) {
                continue;
            }
            Vec3 through = HexRipples.crossing(hex.center, wall, from, to);
            rewrite(level, hex, entity, kind, through);
            return;
        }
    }

    private static int kind(Entity entity) {
        if (entity instanceof AbstractArrow) {
            return entity instanceof ThrownTrident ? -1 : RewritePayload.FLOWER;
        }
        if (entity instanceof AbstractHurtingProjectile) {
            return entity instanceof AbstractWindCharge ? -1 : RewritePayload.FIREWORK;
        }
        if (entity instanceof ShulkerBullet) {
            return RewritePayload.FIREWORK;
        }
        if (entity instanceof AbstractThrownPotion || entity instanceof LlamaSpit) {
            return RewritePayload.BUBBLES;
        }
        return entity instanceof PrimedTnt ? RewritePayload.CAKE : -1;
    }

    /**
     * @param through where it came through the wall, or null if the wall came over it
     */
    private static void rewrite(ServerLevel level, Hex hex, Entity entity, int kind, @Nullable Vec3 through) {
        RandomSource random = level.getRandom();
        Vec3 motion = entity.getDeltaMovement();
        // a little way in from the wall, so what it becomes lands inside
        Vec3 at = through == null ? entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0)
                : through.add(motion.lengthSqr() > 1.0E-6 ? motion.normalize().scale(0.4) : Vec3.ZERO);
        int variant = 0;
        switch (kind) {
            case RewritePayload.FLOWER -> {
                AbstractArrow arrow = (AbstractArrow) entity;
                Bloom[] blooms = BLOOMS[hex.era.ordinal() % BLOOMS.length];
                Bloom bloom = arrow instanceof SpectralArrow ? GLOWING : blooms[random.nextInt(blooms.length)];
                variant = bloom.petal();
                if (arrow.pickup == AbstractArrow.Pickup.ALLOWED) {
                    drop(level, at, motion.scale(0.08).add(0.0, 0.16, 0.0), new ItemStack(bloom.item()));
                }
                play(level, at, SoundEvents.PINK_PETALS_PLACE, 1.0F, 1.3F);
            }
            case RewritePayload.FIREWORK -> variant = (entity instanceof LargeFireball ? FireworkExplosion.Shape.LARGE_BALL
                    : entity instanceof WitherSkull ? FireworkExplosion.Shape.STAR
                    : entity instanceof DragonFireball ? FireworkExplosion.Shape.BURST : FireworkExplosion.Shape.SMALL_BALL).ordinal();
            case RewritePayload.BUBBLES -> {
                variant = entity instanceof AbstractThrownPotion potion
                        ? potion.getItem().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getColor() & 0xFFFFFF : SPIT;
                play(level, at, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 1.0F, 1.5F);
            }
            case RewritePayload.CAKE -> {
                drop(level, entity.position().add(0.0, 0.2, 0.0), new Vec3(0.0, 0.22, 0.0), new ItemStack(Items.CAKE));
                play(level, at, SoundEvents.CAKE_ADD_CANDLE, 1.2F, 1.0F);
            }
            default -> {
            }
        }
        play(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
        entity.discard();
        if (through != null) {
            HexRipples.ripple(level, through, RIPPLE);
        }
        RewritePayload payload = new RewritePayload(at, motion, kind, variant, hex.era.ordinal());
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(at) < SEEN_FROM * SEEN_FROM) {
                Services.NETWORK.sendToPlayer(player, payload);
            }
        }
    }

    private static void drop(ServerLevel level, Vec3 at, Vec3 motion, ItemStack stack) {
        ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, stack, motion.x, motion.y, motion.z);
        item.setPickUpDelay(10);
        level.addFreshEntity(item);
    }

    private static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.NEUTRAL, volume, pitch);
    }

    private record Bloom(Item item, int petal) {
    }
}
