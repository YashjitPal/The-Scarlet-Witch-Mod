package com.yashjit.scarlet.decor;

import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.hex.Hex;
import com.yashjit.scarlet.hex.HexData;
import com.yashjit.scarlet.hex.HexShape;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.registry.ScarletDataComponents;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What a caster takes out of thin air in their own Hex is the Hex's, and lasts only inside it. Carried out past its wall,
 * or left outside it as the wall is drawn in or falls, it glitches for a moment and is gone, from wherever it is:
 * someone's pockets, a chest they have open, a box carried in them, the ground. Back inside before it goes, it stays.
 * Put down inside, a decoration stands only as long as it is inside too, and broken there it gives back the Hex's own
 * again, so nothing the Hex makes can be carried out of it in another form.
 */
public final class HexMade {

    /** How long something a Hex made glitches once it is outside, before it is gone. */
    public static final int FADE_TICKS = 30;
    private static final int CHECK_EVERY = 5;
    private static final int PLACED_EVERY = 10;

    private HexMade() {
    }

    public static void mark(ItemStack stack, UUID caster) {
        stack.set(ScarletDataComponents.HEX_MADE.get(), caster);
    }

    public static @Nullable UUID maker(ItemStack stack) {
        return stack.get(ScarletDataComponents.HEX_MADE.get());
    }

    /** How far through glitching out something is, from 0 to 1; below 0 if it isn't. */
    public static float fading(ItemStack stack, long now) {
        Long since = stack.get(ScarletDataComponents.HEX_FADING.get());
        return since == null ? -1.0F : Mth.clamp((now - since) / (float) FADE_TICKS, 0.0F, 1.0F);
    }

    /** Whether a point stands inside the Hex a caster has here: inside its wall as it stands now, falling or not. */
    public static boolean inside(ServerLevel level, UUID caster, Vec3 at) {
        Hex hex = HexData.of(level).byCaster(caster);
        return hex != null && HexShape.contains(hex.center(), Hexes.wallRadius(hex, level.getGameTime()), at);
    }

    public static void addTooltip(ItemStack stack, Consumer<Component> lines) {
        if (maker(stack) != null) {
            lines.accept(Component.translatable("item.scarlet.hex_made").withStyle(ChatFormatting.RED));
        }
    }

    // ---------------------------------------------------------------- carried

    /**
     * Everything a Hex made that someone carries, or has open in front of them, while they are outside its Hex.
     */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % CHECK_EVERY != 0 || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.level();
        long now = level.getGameTime();
        Vec3 at = player.position();
        boolean gone = false;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            ItemStack after = check(level, at, stack, now);
            if (after != stack) {
                inventory.setItem(slot, after);
                gone = true;
            }
        }
        AbstractContainerMenu menu = player.containerMenu;
        ItemStack carried = menu.getCarried();
        ItemStack after = check(level, at, carried, now);
        if (after != carried) {
            menu.setCarried(after);
            gone = true;
        }
        if (menu != player.inventoryMenu) {
            for (Slot slot : menu.slots) {
                if (slot.container == inventory) {
                    continue;
                }
                ItemStack stack = slot.getItem();
                ItemStack left = check(level, at, stack, now);
                if (left != stack) {
                    slot.set(left);
                    gone = true;
                }
            }
        }
        if (gone) {
            glitch(level, at.add(0.0, 1.0, 0.0), 0.4);
        }
    }

    /**
     * What becomes of something carried to a point: as it was, glitching out, or gone; a box carried with things a Hex
     * made in it loses them.
     */
    private static ItemStack check(ServerLevel level, Vec3 at, ItemStack stack, long now) {
        if (stack.isEmpty()) {
            return stack;
        }
        UUID maker = maker(stack);
        if (maker == null) {
            return emptied(level, at, stack);
        }
        Long since = stack.get(ScarletDataComponents.HEX_FADING.get());
        if (inside(level, maker, at)) {
            if (since != null) {
                stack.remove(ScarletDataComponents.HEX_FADING.get());
            }
            return stack;
        }
        if (since == null) {
            stack.set(ScarletDataComponents.HEX_FADING.get(), now);
            return stack;
        }
        return now - since >= FADE_TICKS ? ItemStack.EMPTY : stack;
    }

    private static ItemStack emptied(ServerLevel level, Vec3 at, ItemStack stack) {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null || contents == ItemContainerContents.EMPTY) {
            return stack;
        }
        List<ItemStack> items = new ArrayList<>(contents.itemCopies().toList());
        boolean changed = false;
        for (int k = 0; k < items.size(); k++) {
            UUID maker = maker(items.get(k));
            if (maker != null && !inside(level, maker, at)) {
                items.set(k, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        }
        return stack;
    }

    /**
     * Something a Hex made, dropped and lying outside it, goes at once.
     */
    public static void tickDropped(ServerLevel level, ItemEntity item) {
        UUID maker = maker(item.getItem());
        if (maker != null && !inside(level, maker, item.position())) {
            glitch(level, item.position().add(0.0, 0.25, 0.0), 0.2);
            item.discard();
        }
    }

    // ---------------------------------------------------------------- put down

    /**
     * A decoration put down: if a Hex made it, it is that Hex's where it stands, with the rest of it if it stands more
     * than a block tall.
     */
    public static void placed(ServerLevel level, BlockPos pos, ItemStack stack, BlockPos... more) {
        UUID maker = maker(stack);
        if (maker == null) {
            return;
        }
        HexData data = HexData.of(level);
        data.conjured().put(pos.asLong(), maker);
        for (BlockPos other : more) {
            data.conjured().put(other.asLong(), maker);
        }
        data.setDirty();
    }

    /** Whose Hex made the decoration standing somewhere, if one did. */
    public static @Nullable UUID madeAt(ServerLevel level, BlockPos pos) {
        return HexData.of(level).conjured().get(pos.asLong());
    }

    /**
     * Decorations a Hex made standing outside it go, glitching out, spilling what they hold; those broken meanwhile are
     * forgotten.
     */
    public static void tickPlaced(ServerLevel level) {
        if (level.getGameTime() % PLACED_EVERY != 0) {
            return;
        }
        HexData data = HexData.of(level);
        if (data.conjured().isEmpty()) {
            return;
        }
        boolean changed = false;
        ObjectIterator<Long2ObjectMap.Entry<UUID>> entries = data.conjured().long2ObjectEntrySet().iterator();
        while (entries.hasNext()) {
            Long2ObjectMap.Entry<UUID> entry = entries.next();
            BlockPos pos = BlockPos.of(entry.getLongKey());
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!EraDecor.is(state)) {
                entries.remove();
                changed = true;
                continue;
            }
            if (!inside(level, entry.getValue(), Vec3.atCenterOf(pos))) {
                entries.remove();
                changed = true;
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                glitch(level, Vec3.atCenterOf(pos), 0.35);
            }
        }
        if (changed) {
            data.setDirty();
        }
    }

    /** Something a Hex made going: a scatter of scarlet, and the sound of a set losing its picture. */
    private static void glitch(ServerLevel level, Vec3 at, double spread) {
        level.sendParticles(new DustParticleOptions(ScarletPalette.SCARLET, 1.0F), at.x, at.y, at.z, 14, spread, spread, spread, 0.0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 0.35F, 1.8F);
    }
}
