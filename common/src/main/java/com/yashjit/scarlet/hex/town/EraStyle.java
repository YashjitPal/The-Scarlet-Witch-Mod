package com.yashjit.scarlet.hex.town;

import com.yashjit.scarlet.hex.Era;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What each era builds the town from: picket-fence pastels in the 1950s, crisp mid-century white and mint in the
 * 1960s, wood paneling and earth tones in the 1970s, bold color in the 1980s, beige in the 2000s and grey and black
 * today. Every era builds a role from a block of the same shape, so swapping one for another keeps its facing, half
 * and connections.
 */
public final class EraStyle {

    /** How many paints a house's siding can come in, per era. */
    public static final int PAINTS = 4;
    /** How many kinds of flower a bed can hold. */
    public static final int FLOWERS = 6;

    private static final Map<Role, Block[]> BY_ERA = new EnumMap<>(Role.class);
    private static final Block[][] SIDING = {
            {Blocks.PALE_OAK_PLANKS, Blocks.BIRCH_PLANKS, Blocks.CHERRY_PLANKS, terracotta(DyeColor.LIGHT_BLUE)},
            {Blocks.SMOOTH_QUARTZ, terracotta(DyeColor.CYAN), terracotta(DyeColor.LIME), terracotta(DyeColor.WHITE)},
            {Blocks.SPRUCE_PLANKS, terracotta(DyeColor.BROWN), terracotta(DyeColor.ORANGE), terracotta(DyeColor.YELLOW)},
            {concrete(DyeColor.LIGHT_BLUE), concrete(DyeColor.PINK), concrete(DyeColor.YELLOW), concrete(DyeColor.LIME)},
            {Blocks.SMOOTH_SANDSTONE, terracotta(DyeColor.WHITE), Blocks.MUD_BRICKS, Blocks.BIRCH_PLANKS},
            {concrete(DyeColor.WHITE), concrete(DyeColor.LIGHT_GRAY), Blocks.POLISHED_TUFF, Blocks.PALE_OAK_PLANKS}
    };
    private static final Block[] FLOWER_KINDS = {
            Blocks.POPPY, Blocks.DANDELION, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.PINK_TULIP, Blocks.CORNFLOWER
    };

    static {
        era(Role.FILL, Blocks.DIRT, Blocks.DIRT, Blocks.DIRT, Blocks.DIRT, Blocks.DIRT, Blocks.DIRT);
        era(Role.FOUNDATION, Blocks.STONE_BRICKS, Blocks.SMOOTH_STONE, Blocks.BRICKS, Blocks.STONE_BRICKS, Blocks.SMOOTH_STONE,
                Blocks.POLISHED_DEEPSLATE);
        era(Role.FLOOR, Blocks.OAK_PLANKS, Blocks.BIRCH_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS,
                Blocks.PALE_OAK_PLANKS);
        era(Role.PORCH, Blocks.SPRUCE_PLANKS, Blocks.SMOOTH_STONE, Blocks.DARK_OAK_PLANKS, Blocks.PALE_OAK_PLANKS, Blocks.OAK_PLANKS,
                Blocks.POLISHED_DEEPSLATE);
        era(Role.STEP, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_STAIRS, Blocks.BRICK_STAIRS, Blocks.STONE_BRICK_STAIRS,
                Blocks.POLISHED_ANDESITE_STAIRS, Blocks.POLISHED_DEEPSLATE_STAIRS);
        era(Role.CEILING, Blocks.PALE_OAK_PLANKS, Blocks.SMOOTH_QUARTZ, Blocks.SPRUCE_PLANKS, Blocks.PALE_OAK_PLANKS, Blocks.BIRCH_PLANKS,
                Blocks.SMOOTH_QUARTZ);
        era(Role.TRIM, Blocks.QUARTZ_PILLAR, Blocks.QUARTZ_PILLAR, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.QUARTZ_PILLAR,
                Blocks.STRIPPED_BIRCH_LOG, Blocks.POLISHED_BASALT);
        era(Role.ROOF, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.STONE_BRICK_STAIRS, Blocks.SPRUCE_STAIRS, Blocks.RED_NETHER_BRICK_STAIRS,
                Blocks.MUD_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_STAIRS);
        era(Role.ROOF_SLAB, Blocks.DEEPSLATE_TILE_SLAB, Blocks.STONE_BRICK_SLAB, Blocks.SPRUCE_SLAB, Blocks.RED_NETHER_BRICK_SLAB,
                Blocks.MUD_BRICK_SLAB, Blocks.POLISHED_BLACKSTONE_SLAB);
        era(Role.ROOF_RIDGE, Blocks.DEEPSLATE_TILES, Blocks.STONE_BRICKS, Blocks.SPRUCE_PLANKS, Blocks.RED_NETHER_BRICKS,
                Blocks.MUD_BRICKS, Blocks.POLISHED_BLACKSTONE);
        era(Role.WINDOW, Blocks.GLASS_PANE, Blocks.GLASS_PANE, Blocks.GLASS_PANE, Blocks.GLASS_PANE, Blocks.GLASS_PANE,
                pane(DyeColor.GRAY));
        era(Role.DOOR, Blocks.BIRCH_DOOR, Blocks.CHERRY_DOOR, Blocks.DARK_OAK_DOOR, Blocks.OAK_DOOR, Blocks.SPRUCE_DOOR,
                Blocks.COPPER_DOOR.waxed().unaffected());
        era(Role.SHUTTER, Blocks.DARK_OAK_TRAPDOOR, Blocks.SPRUCE_TRAPDOOR, Blocks.BIRCH_TRAPDOOR, Blocks.WARPED_TRAPDOOR,
                Blocks.OAK_TRAPDOOR, Blocks.IRON_TRAPDOOR);
        era(Role.PORCH_POST, Blocks.PALE_OAK_FENCE, Blocks.BIRCH_FENCE, Blocks.DARK_OAK_FENCE, Blocks.PALE_OAK_FENCE, Blocks.OAK_FENCE,
                Blocks.NETHER_BRICK_FENCE);
        era(Role.CHIMNEY, Blocks.BRICKS, Blocks.BRICKS, Blocks.BRICKS, Blocks.STONE_BRICKS, Blocks.BRICKS, Blocks.POLISHED_DEEPSLATE);
        era(Role.LAWN, Blocks.GRASS_BLOCK, Blocks.GRASS_BLOCK, Blocks.GRASS_BLOCK, Blocks.GRASS_BLOCK, Blocks.GRASS_BLOCK,
                Blocks.GRASS_BLOCK);
        era(Role.WALKWAY, Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE, Blocks.BRICKS, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE,
                Blocks.POLISHED_DEEPSLATE);
        era(Role.PICKET, Blocks.PALE_OAK_FENCE, Blocks.BIRCH_FENCE, Blocks.SPRUCE_FENCE, Blocks.PALE_OAK_FENCE, Blocks.BIRCH_FENCE,
                Blocks.NETHER_BRICK_FENCE);
        era(Role.GATE, Blocks.PALE_OAK_FENCE_GATE, Blocks.BIRCH_FENCE_GATE, Blocks.SPRUCE_FENCE_GATE, Blocks.PALE_OAK_FENCE_GATE,
                Blocks.BIRCH_FENCE_GATE, Blocks.DARK_OAK_FENCE_GATE);
        era(Role.HEDGE, Blocks.OAK_LEAVES, Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.AZALEA_LEAVES, Blocks.OAK_LEAVES,
                Blocks.AZALEA_LEAVES);
        era(Role.MAILBOX_POST, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_FENCE, Blocks.SPRUCE_FENCE, Blocks.NETHER_BRICK_FENCE,
                Blocks.DARK_OAK_FENCE, Blocks.NETHER_BRICK_FENCE);
        era(Role.MAILBOX, concrete(DyeColor.LIGHT_GRAY), concrete(DyeColor.WHITE), concrete(DyeColor.BROWN), concrete(DyeColor.BLUE),
                concrete(DyeColor.BLACK), concrete(DyeColor.GRAY));
        era(Role.ROAD, concrete(DyeColor.GRAY), concrete(DyeColor.GRAY), concrete(DyeColor.GRAY), concrete(DyeColor.GRAY), concrete(DyeColor.GRAY),
                concrete(DyeColor.BLACK));
        era(Role.ROAD_LINE, concrete(DyeColor.WHITE), concrete(DyeColor.WHITE), concrete(DyeColor.YELLOW), concrete(DyeColor.YELLOW),
                concrete(DyeColor.YELLOW), concrete(DyeColor.YELLOW));
        era(Role.SIDEWALK, Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE, concrete(DyeColor.LIGHT_GRAY),
                Blocks.POLISHED_DIORITE);
        era(Role.LAMP_POST, Blocks.NETHER_BRICK_FENCE, Blocks.NETHER_BRICK_FENCE, Blocks.DARK_OAK_FENCE, Blocks.NETHER_BRICK_FENCE,
                Blocks.NETHER_BRICK_FENCE, Blocks.NETHER_BRICK_FENCE);
        era(Role.LAMP, Blocks.LANTERN, Blocks.LANTERN, Blocks.LANTERN, Blocks.SOUL_LANTERN, Blocks.LANTERN,
                Blocks.COPPER_LANTERN.waxed().unaffected());
        era(Role.PLAZA, Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE, Blocks.BRICKS, Blocks.QUARTZ_BRICKS, Blocks.CUT_SANDSTONE,
                Blocks.POLISHED_DIORITE);
        era(Role.GAZEBO_POST, Blocks.PALE_OAK_FENCE, Blocks.BIRCH_FENCE, Blocks.DARK_OAK_FENCE, Blocks.PALE_OAK_FENCE, Blocks.OAK_FENCE,
                Blocks.NETHER_BRICK_FENCE);
        era(Role.BENCH, Blocks.DARK_OAK_STAIRS, Blocks.BIRCH_STAIRS, Blocks.SPRUCE_STAIRS, Blocks.QUARTZ_STAIRS, Blocks.OAK_STAIRS,
                Blocks.POLISHED_BLACKSTONE_STAIRS);
        era(Role.TREE_LOG, Blocks.OAK_LOG, Blocks.OAK_LOG, Blocks.OAK_LOG, Blocks.OAK_LOG, Blocks.OAK_LOG, Blocks.OAK_LOG);
        era(Role.TREE_LEAVES, Blocks.OAK_LEAVES, Blocks.OAK_LEAVES, Blocks.OAK_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES, Blocks.OAK_LEAVES,
                Blocks.OAK_LEAVES);
        era(Role.RUG, carpet(DyeColor.LIGHT_GRAY), carpet(DyeColor.CYAN), carpet(DyeColor.ORANGE), carpet(DyeColor.PINK), carpet(DyeColor.BROWN),
                carpet(DyeColor.GRAY));
        era(Role.COUCH, Blocks.CHERRY_STAIRS, Blocks.WARPED_STAIRS, Blocks.ACACIA_STAIRS, Blocks.PURPUR_STAIRS,
                Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.POLISHED_DEEPSLATE_STAIRS);
        era(Role.TABLE, Blocks.DARK_OAK_SLAB, Blocks.BIRCH_SLAB, Blocks.SPRUCE_SLAB, Blocks.QUARTZ_SLAB, Blocks.OAK_SLAB,
                Blocks.POLISHED_BLACKSTONE_SLAB);
        era(Role.SHELF, Blocks.BOOKSHELF, Blocks.BOOKSHELF, Blocks.BOOKSHELF, Blocks.BOOKSHELF, Blocks.BOOKSHELF, Blocks.BOOKSHELF);
        era(Role.FRIDGE, Blocks.QUARTZ_PILLAR, Blocks.QUARTZ_PILLAR, Blocks.QUARTZ_PILLAR, Blocks.QUARTZ_PILLAR, Blocks.QUARTZ_PILLAR,
                Blocks.POLISHED_BASALT);
        era(Role.COUNTER, Blocks.SMOOTH_QUARTZ, Blocks.SMOOTH_QUARTZ, Blocks.SPRUCE_PLANKS, concrete(DyeColor.WHITE), Blocks.POLISHED_GRANITE,
                Blocks.POLISHED_DEEPSLATE);
        era(Role.SINK, Blocks.CAULDRON, Blocks.CAULDRON, Blocks.CAULDRON, Blocks.CAULDRON, Blocks.CAULDRON, Blocks.CAULDRON);
        era(Role.PLANT, Blocks.POTTED_FERN, Blocks.POTTED_FERN, Blocks.POTTED_FERN, Blocks.POTTED_FERN, Blocks.POTTED_FERN,
                Blocks.POTTED_FERN);
    }

    private EraStyle() {
    }

    private static Block concrete(DyeColor color) {
        return Blocks.CONCRETE.pick(color);
    }

    private static Block terracotta(DyeColor color) {
        return Blocks.DYED_TERRACOTTA.pick(color);
    }

    private static Block carpet(DyeColor color) {
        return Blocks.CARPET.pick(color);
    }

    private static Block pane(DyeColor color) {
        return Blocks.STAINED_GLASS_PANE.pick(color);
    }

    private static void era(Role role, Block... blocks) {
        if (blocks.length != Era.values().length) {
            throw new IllegalArgumentException(role + " needs a block for every era");
        }
        BY_ERA.put(role, blocks);
    }

    /**
     * The block an era builds a role from.
     *
     * @param paint which of the era's paints, for siding, or which flower, for flower beds
     */
    public static Block block(Role role, Era era, int paint) {
        return switch (role) {
            case CLEAR -> Blocks.AIR;
            case WALL -> SIDING[era.ordinal()][Math.floorMod(paint, PAINTS)];
            case FLOWER -> FLOWER_KINDS[Math.floorMod(paint, FLOWERS)];
            default -> BY_ERA.get(role)[era.ordinal()];
        };
    }

    /**
     * A role built in an era, shaped like the template: the same facing, half, axis and so on wherever the block has
     * them.
     */
    public static BlockState state(Role role, Era era, int paint, BlockState template) {
        BlockState state = block(role, era, paint).withPropertiesOf(template);
        if (state.hasProperty(LeavesBlock.PERSISTENT)) {
            state = state.setValue(LeavesBlock.PERSISTENT, true);
        }
        return state;
    }

    /**
     * Whether a block in the world is what the town built for a role, in any era, so it can tell its own blocks from
     * ones a player put in their place.
     */
    public static boolean isStyleOf(Role role, int paint, BlockState state) {
        if (role == Role.CLEAR) {
            return state.isAir();
        }
        for (Era era : Era.values()) {
            if (state.is(block(role, era, paint))) {
                return true;
            }
        }
        return false;
    }
}
