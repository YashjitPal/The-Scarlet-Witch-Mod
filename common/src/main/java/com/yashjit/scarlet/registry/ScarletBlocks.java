package com.yashjit.scarlet.registry;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.decor.CarBodyBlock;
import com.yashjit.scarlet.decor.ChimeDecorBlock;
import com.yashjit.scarlet.decor.CouchBlock;
import com.yashjit.scarlet.decor.DecorBlock;
import com.yashjit.scarlet.decor.EraDecor;
import com.yashjit.scarlet.decor.RefrigeratorBlock;
import com.yashjit.scarlet.decor.SeatBlock;
import com.yashjit.scarlet.decor.SwitchedDecorBlock;
import com.yashjit.scarlet.decor.WallDecorBlock;
import com.yashjit.scarlet.platform.Services;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The era decorations: furniture that changes model with the Hex's era, which its homes are furnished with.
 */
public final class ScarletBlocks {

    /** Every decoration's item, in the order they show in the creative tab. */
    public static final List<Supplier<Item>> DECOR_ITEMS = new ArrayList<>();

    /** How high the cushion is on each era's couch and armchair, in sixteenths of a block. */
    private static final float[] COUCH_SEATS = {7, 6.5F, 7.5F, 7, 8, 6.5F};
    private static final float[] COUCH_BACKS = {14, 12, 14, 13.5F, 15, 12};
    private static final float[] CHAIR_SEATS = {7, 6, 7.5F, 6, 8, 7};
    private static final float[] CHAIR_BACKS = {15.5F, 14, 15, 16, 15, 14};
    /** How far up into its upper block each era's refrigerator reaches. */
    private static final float[] FRIDGE_TOPS = {13.5F, 14, 14, 14, 14, 15};

    public static final Supplier<Block> TELEVISION = register("television", p -> new SwitchedDecorBlock(p, DecorBlock.byEra(
                    box(1, 0, 3, 15, 14.5, 13), box(2, 0, 4, 14, 15, 13), box(0, 0, 2, 16, 12, 14), box(1, 0, 3, 15, 15, 13),
                    box(0, 0, 3, 16, 15, 13), box(0, 0, 4, 16, 15.5, 13)),
                    SwitchedDecorBlock.Kind.TELEVISION, false, SoundEvents.STONE_BUTTON_CLICK_ON, SoundEvents.STONE_BUTTON_CLICK_OFF),
            decor(MapColor.WOOD, SoundType.WOOD).lightLevel(lit(7)), false);
    public static final Supplier<Block> RADIO = register("radio", p -> new SwitchedDecorBlock(p, DecorBlock.byEra(
                    box(3, 0, 6, 13, 8.5, 11), box(1, 0, 2, 15, 14, 14), box(1, 0, 3, 15, 7, 13), box(1, 0, 6, 15, 11.5, 10),
                    box(0.5, 0, 5, 15.5, 9, 12), box(5, 0, 5, 11, 9.5, 11)),
                    SwitchedDecorBlock.Kind.RADIO, false, SoundEvents.STONE_BUTTON_CLICK_ON, SoundEvents.STONE_BUTTON_CLICK_OFF),
            decor(MapColor.WOOD, SoundType.WOOD).lightLevel(lit(3)), false);
    public static final Supplier<Block> TELEPHONE = register("telephone", p -> new ChimeDecorBlock(p, DecorBlock.byEra(
                    box(3, 0, 4.5, 13, 7, 11), box(4, 0, 5, 12, 4.5, 11), box(3.5, 0, 5, 12.5, 5, 11), box(4, 0, 5, 12, 5.5, 11),
                    box(5, 0, 6, 11, 10, 11), box(5, 0, 4, 11, 1.5, 12)), ChimeDecorBlock.Kind.TELEPHONE),
            decor(MapColor.COLOR_BLACK, SoundType.STONE), false);
    public static final Supplier<Block> COUCH = register("couch", p -> new CouchBlock(p, state -> {
                int era = state.getValue(EraDecor.ERA).ordinal();
                return Shapes.or(box(0, 0, 1, 16, COUCH_SEATS[era], 16), box(0, 0, 12.5, 16, COUCH_BACKS[era], 16));
            }, state -> COUCH_SEATS[state.getValue(EraDecor.ERA).ordinal()] / 16.0),
            decor(MapColor.WOOL, SoundType.WOOL), false);
    public static final Supplier<Block> ARMCHAIR = register("armchair", p -> new SeatBlock(p, state -> {
                int era = state.getValue(EraDecor.ERA).ordinal();
                return Shapes.or(box(0, 0, 1, 16, CHAIR_SEATS[era], 16), box(0, 0, 12, 16, CHAIR_BACKS[era], 16));
            }, state -> CHAIR_SEATS[state.getValue(EraDecor.ERA).ordinal()] / 16.0),
            decor(MapColor.WOOL, SoundType.WOOL), false);
    public static final Supplier<Block> LAMP = register("lamp", p -> new SwitchedDecorBlock(p, DecorBlock.byEra(
                    box(4, 0, 4, 12, 13, 12), box(4.5, 0, 4.5, 11.5, 10.25, 11.5), box(6, 0, 6, 10, 12.5, 10), box(5, 0, 5, 11, 12, 11),
                    box(4.5, 0, 4.5, 11.5, 13, 11.5), box(5, 0, 5, 11, 6.5, 11)),
                    SwitchedDecorBlock.Kind.LAMP, true, SoundEvents.STONE_BUTTON_CLICK_ON, SoundEvents.STONE_BUTTON_CLICK_OFF),
            decor(MapColor.WOOL, SoundType.WOOL).lightLevel(lit(14)), false);
    public static final Supplier<Block> REFRIGERATOR = register("refrigerator", p -> new RefrigeratorBlock(p, state -> {
                if (state.getValue(RefrigeratorBlock.HALF) == DoubleBlockHalf.LOWER) {
                    return box(0.5, 0, 2.5, 15.5, 16, 15);
                }
                return box(0.5, 0, 2.5, 15.5, FRIDGE_TOPS[state.getValue(EraDecor.ERA).ordinal()], 15);
            }),
            decor(MapColor.SNOW, SoundType.METAL).strength(1.5F), true);
    public static final Supplier<Block> STOVE = register("stove", p -> new SwitchedDecorBlock(p, state -> box(0, 0, 2, 16, 16, 16),
                    SwitchedDecorBlock.Kind.STOVE, false, SoundEvents.STONE_BUTTON_CLICK_ON, SoundEvents.STONE_BUTTON_CLICK_OFF),
            decor(MapColor.SNOW, SoundType.METAL).strength(1.5F).lightLevel(lit(6)), false);
    public static final Supplier<Block> TOASTER = register("toaster", p -> new ChimeDecorBlock(p, DecorBlock.byEra(
                    box(4, 0, 6, 12.75, 5.75, 10), box(3.5, 0, 6, 13.25, 5, 10), box(4, 0, 5.9, 12.75, 5, 10.1), box(4, 0, 5.6, 12.75, 5.5, 10),
                    box(3, 0, 4.8, 13.75, 6, 11), box(4, 0, 5.8, 12.75, 6, 10)), ChimeDecorBlock.Kind.TOASTER),
            decor(MapColor.METAL, SoundType.METAL), false);
    public static final Supplier<Block> WALL_CLOCK = register("wall_clock", p -> new WallDecorBlock(p, DecorBlock.byEra(
                    box(0, 0, 14.75, 16, 16, 16), box(0, 0, 14.75, 16, 16, 16), box(3.5, 1, 13.5, 12.5, 15.5, 16), box(3, 3, 15, 13, 13, 16),
                    box(3, 3, 15, 13, 13, 16), box(1.5, 1.5, 15, 14.5, 14.5, 16))),
            decor(MapColor.WOOD, SoundType.WOOD).noCollision(), false);
    public static final Supplier<Block> PICTURE_FRAME = register("picture_frame", p -> new WallDecorBlock(p, state -> box(2, 3, 15, 14, 13, 16)),
            decor(MapColor.WOOD, SoundType.WOOD).noCollision(), false);
    public static final Supplier<Block> POSTER = register("poster", p -> new WallDecorBlock(p, state -> box(0.5, 0, 15.5, 15.5, 16, 16)),
            decor(MapColor.SNOW, SoundType.WOOL).noCollision().instabreak(), false);

    /** What a parked car is drawn from; never put down, and with no item. */
    public static final Supplier<Block> CAR_BODY = registerHidden("car_body", CarBodyBlock::new,
            BlockBehaviour.Properties.of().noOcclusion().noLootTable().noCollision());

    private ScarletBlocks() {
    }

    private static Supplier<Block> registerHidden(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Scarlet.id(name));
        return Services.REGISTRY.register(BuiltInRegistries.BLOCK, name, () -> factory.apply(properties.setId(key)));
    }

    public static void bootstrap() {
    }

    private static BlockBehaviour.Properties decor(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).sound(sound).strength(0.8F).noOcclusion().pushReaction(PushReaction.POPPED);
    }

    private static ToIntFunction<BlockState> lit(int light) {
        return state -> state.getValue(SwitchedDecorBlock.LIT) ? light : 0;
    }

    private static VoxelShape box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return Block.box(x0, y0, z0, x1, y1, z1);
    }

    private static Supplier<Block> register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties,
                                            boolean tall) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Scarlet.id(name));
        Supplier<Block> block = Services.REGISTRY.register(BuiltInRegistries.BLOCK, name, () -> factory.apply(properties.setId(key)));
        DECOR_ITEMS.add(ScarletItems.blockItem(name, block, tall));
        return block;
    }
}
