package com.yashjit.scarlet.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.yashjit.scarlet.registry.ScarletItems;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RandomizableContainer.class)
interface CovenChestMixin {
    @WrapOperation(method="unpackLootTable", at=@At(value="INVOKE",target="Lnet/minecraft/world/level/storage/loot/LootTable;fill(Lnet/minecraft/world/Container;Lnet/minecraft/world/level/storage/loot/LootParams;J)V"))
    private void scarlet$placeGrimoire(LootTable table, Container chest, LootParams params, long seed, Operation<Void> fill,
                                       @Local ResourceKey<LootTable> key) {
        fill.call(table,chest,params,seed);
        if (!key.identifier().getNamespace().equals("minecraft")) return;
        Item book = switch (key.identifier().getPath()) {
            case "chests/abandoned_mineshaft", "chests/simple_dungeon" -> ScarletItems.ORB_GRIMOIRE.get();
            case "chests/desert_pyramid", "chests/jungle_temple" -> ScarletItems.BEAM_GRIMOIRE.get();
            case "chests/stronghold_library", "chests/woodland_mansion" -> ScarletItems.RUNE_GRIMOIRE.get();
            default -> null;
        };
        if (book==null) return;
        var container=(RandomizableContainer)this;
        var random=seed==0 ? container.getLevel().getRandom() : RandomSource.create(seed ^ container.getBlockPos().asLong());
        if (random.nextInt(4)!=0) return;
        int empty=-1, seen=0;
        for (int i=0;i<chest.getContainerSize();i++)
            if (chest.getItem(i).isEmpty() && random.nextInt(++seen)==0) empty=i;
        if (empty>=0) chest.setItem(empty,book.getDefaultInstance());
    }
}
