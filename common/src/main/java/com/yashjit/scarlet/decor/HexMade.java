package com.yashjit.scarlet.decor;

import com.yashjit.scarlet.ScarletPalette;
import com.yashjit.scarlet.hex.Hex;
import com.yashjit.scarlet.hex.HexData;
import com.yashjit.scarlet.hex.HexShape;
import com.yashjit.scarlet.hex.HexSnapshot;
import com.yashjit.scarlet.hex.Hexes;
import com.yashjit.scarlet.hex.town.Terrain;
import com.yashjit.scarlet.registry.ScarletDataComponents;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
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
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
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
    private static final Map<ServerPlayer, Vec3> OPENED_AT = new WeakHashMap<>();

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

    /** Whether something may be put down at a spot: anything not made by a Hex, or inside the Hex that made it. */
    public static boolean canPlace(Level level, ItemStack stack, Vec3 at) {
        UUID maker = maker(stack);
        if (maker == null) {
            return true;
        }
        if (level instanceof ServerLevel server) {
            return inside(server, maker, at);
        }
        double now = level.getGameTime();
        for (HexSnapshot hex : Hexes.clientHexes()) {
            if (hex.caster().equals(maker)) {
                return HexShape.contains(hex.center(), Hexes.wallRadius(hex, now), at);
            }
        }
        return false;
    }

    /** Records where a block container was opened from, so double chests and the like are checked where they stand. */
    public static void openedAt(ServerPlayer player, Vec3 at) {
        OPENED_AT.put(player, at);
    }

    public static void addTooltip(ItemStack stack, Consumer<Component> lines) {
        if (maker(stack) != null) {
            lines.accept(Component.translatable("item.scarlet.hex_made").withStyle(ChatFormatting.RED));
        }
    }

    // ---------------------------------------------------------------- carried

    /**
     * Everything a Hex made that someone carries, or has open in front of them, while it is outside its Hex.
     */
    public static void tick(ServerPlayer player) {
        if (player.isSpectator()) {
            return;
        }
        ServerLevel level = player.level();
        long now = level.getGameTime();
        Vec3 at = player.position();
        Inventory inventory = player.getInventory();
        PlayerEnderChestContainer enderChest = player.getEnderChestInventory();
        AbstractContainerMenu menu = player.containerMenu;
        boolean gone = false;
        Vec3 goneAt = at.add(0.0, 1.0, 0.0);
        if (menu == player.inventoryMenu) {
            OPENED_AT.remove(player);
        } else {
            Vec3 fallbackAt = OPENED_AT.getOrDefault(player, at);
            for (Slot slot : menu.slots) {
                if (slot.container == inventory || slot.container == enderChest) {
                    continue;
                }
                Vec3 slotAt = containerAt(slot.container, fallbackAt);
                ItemStack stack = slot.getItem();
                boolean changed = stripNested(level, slotAt, stack);
                ItemStack left = check(level, slotAt, stack, now, true);
                if (left != stack || changed) {
                    slot.set(left);
                    if (left.isEmpty() && !stack.isEmpty() || changed) {
                        gone = true;
                        goneAt = slotAt;
                    }
                }
            }
        }
        if (player.tickCount % CHECK_EVERY == 0) {
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                boolean changed = stripNested(level, at, stack);
                ItemStack after = check(level, at, stack, now, false);
                if (after != stack || changed) {
                    inventory.setItem(slot, after);
                    if (after.isEmpty() && !stack.isEmpty() || changed) {
                        gone = true;
                    }
                }
            }
            for (int slot = 0; slot < enderChest.getContainerSize(); slot++) {
                ItemStack stack = enderChest.getItem(slot);
                boolean changed = stripNested(level, at, stack);
                ItemStack after = check(level, at, stack, now, false);
                if (after != stack || changed) {
                    enderChest.setItem(slot, after);
                    if (after.isEmpty() && !stack.isEmpty() || changed) {
                        gone = true;
                    }
                }
            }
            ItemStack carried = menu.getCarried();
            boolean changed = stripNested(level, at, carried);
            ItemStack after = check(level, at, carried, now, false);
            if (after != carried || changed) {
                menu.setCarried(after);
                if (after.isEmpty() && !carried.isEmpty() || changed) {
                    gone = true;
                }
            }
        }
        if (gone) {
            glitch(level, goneAt, 0.4);
        }
    }

    private static Vec3 containerAt(Container container, Vec3 fallback) {
        if (container instanceof BlockEntity blockEntity) {
            return Vec3.atCenterOf(blockEntity.getBlockPos());
        }
        if (container instanceof Entity entity) {
            return entity.position();
        }
        return fallback;
    }

    /**
     * What becomes of something at a point: as it was, glitching out, or gone.
     */
    private static ItemStack check(ServerLevel level, Vec3 at, ItemStack stack, long now, boolean immediate) {
        if (stack.isEmpty()) {
            return stack;
        }
        UUID maker = maker(stack);
        if (maker == null) {
            return stack;
        }
        Long since = stack.get(ScarletDataComponents.HEX_FADING.get());
        if (inside(level, maker, at)) {
            if (since != null) {
                stack.remove(ScarletDataComponents.HEX_FADING.get());
            }
            return stack;
        }
        if (immediate) {
            return ItemStack.EMPTY;
        }
        if (since == null) {
            stack.set(ScarletDataComponents.HEX_FADING.get(), now);
            return stack;
        }
        return now - since >= FADE_TICKS ? ItemStack.EMPTY : stack;
    }

    /**
     * Strips anything a Hex made out of a box or bundle carried or kept outside that Hex, however deep inside it lies.
     */
    private static boolean stripNested(ServerLevel level, Vec3 at, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        boolean changed = false;
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null && contents != ItemContainerContents.EMPTY) {
            List<ItemStack> items = new ArrayList<>(contents.itemCopies().toList());
            boolean boxChanged = false;
            for (int k = 0; k < items.size(); k++) {
                ItemStack inner = items.get(k);
                UUID maker = maker(inner);
                if (maker != null && !inside(level, maker, at)) {
                    items.set(k, ItemStack.EMPTY);
                    boxChanged = true;
                } else if (stripNested(level, at, inner)) {
                    boxChanged = true;
                }
            }
            if (boxChanged) {
                stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
                changed = true;
            }
        }
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null && !bundle.isEmpty()) {
            List<ItemStack> items = bundle.itemCopies().toList();
            List<ItemStack> kept = new ArrayList<>(items.size());
            boolean bundleChanged = false;
            for (ItemStack inner : items) {
                UUID maker = maker(inner);
                if (maker != null && !inside(level, maker, at)) {
                    bundleChanged = true;
                } else {
                    if (stripNested(level, at, inner)) {
                        bundleChanged = true;
                    }
                    kept.add(inner);
                }
            }
            if (bundleChanged) {
                stack.set(DataComponents.BUNDLE_CONTENTS, kept.isEmpty() ? BundleContents.EMPTY : bundle.copyWithContents(kept.stream()));
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Something a Hex made, dropped and lying outside it, goes at once; a box or bundle dropped outside loses what a Hex made inside it.
     */
    public static void tickDropped(ServerLevel level, ItemEntity item) {
        ItemStack stack = item.getItem();
        UUID maker = maker(stack);
        if (maker != null && !inside(level, maker, item.position())) {
            glitch(level, item.position().add(0.0, 0.25, 0.0), 0.2);
            item.discard();
            return;
        }
        if (stripNested(level, item.position(), stack)) {
            item.setItem(stack.copy());
            glitch(level, item.position().add(0.0, 0.25, 0.0), 0.2);
        }
    }

    /**
     * Something a Hex made, hung in a frame outside it, goes at once.
     */
    public static void tickFrame(ServerLevel level, ItemFrame frame) {
        ItemStack held = frame.getItem();
        if (held.isEmpty()) {
            return;
        }
        UUID maker = maker(held);
        if (maker != null && !inside(level, maker, frame.position())) {
            frame.setItem(ItemStack.EMPTY, false);
            glitch(level, frame.position(), 0.25);
            return;
        }
        ItemStack copy = held.copy();
        if (stripNested(level, frame.position(), copy)) {
            frame.setItem(copy, false);
            glitch(level, frame.position(), 0.25);
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
            if (!Terrain.isReady(level, pos)) {
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
    public static void glitch(ServerLevel level, Vec3 at, double spread) {
        level.sendParticles(new DustParticleOptions(ScarletPalette.SCARLET, 1.0F), at.x, at.y, at.z, 14, spread, spread, spread, 0.0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 0.35F, 1.8F);
    }
}
