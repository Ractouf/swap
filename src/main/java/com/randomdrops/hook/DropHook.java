package com.randomdrops.hook;

import com.randomdrops.mapping.DropMappingState;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;
import java.util.ListIterator;

public final class DropHook {

    private DropHook() {}

    public static void register() {
        LootTableEvents.MODIFY_DROPS.register(DropHook::onModifyDrops);
    }

    private static void onModifyDrops(
            Holder<LootTable> lootTable,
            LootContext context,
            List<ItemStack> drops
    ) {
        if (!context.hasParameter(LootContextParams.BLOCK_STATE)) return;

        BlockState state = context.getParameter(LootContextParams.BLOCK_STATE);
        Block block = state.getBlock();
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
        if (blockId == null) return;

        DropMappingState mappingState = DropMappingState.get(context.getLevel().getServer());

        ListIterator<ItemStack> iter = drops.listIterator();
        while (iter.hasNext()) {
            ItemStack original = iter.next();
            if (original.isEmpty()) continue;

            Item originalItem = original.getItem();
            Identifier droppedItemId = BuiltInRegistries.ITEM.getKey(originalItem);
            if (droppedItemId == null) continue;

            Item replacement = mappingState.getOrCompute(blockId, droppedItemId);
            if (replacement != originalItem) {
                iter.set(new ItemStack(replacement, original.getCount()));
            }
        }
    }
}
