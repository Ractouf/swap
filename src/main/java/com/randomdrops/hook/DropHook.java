package com.randomdrops.hook;

import com.randomdrops.mapping.DropMappingState;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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
        if (resolveSourceId(context) == null) return;

        DropMappingState mappingState = DropMappingState.get(context.getLevel().getServer());

        ListIterator<ItemStack> iter = drops.listIterator();
        while (iter.hasNext()) {
            ItemStack original = iter.next();
            if (original.isEmpty()) continue;

            Item originalItem = original.getItem();
            Identifier droppedItemId = BuiltInRegistries.ITEM.getKey(originalItem);
            if (droppedItemId == null) continue;

            Item replacement = mappingState.getOrCompute(droppedItemId);
            if (replacement != originalItem) {
                iter.set(new ItemStack(replacement, original.getCount()));
            }
        }
    }

    private static Identifier resolveSourceId(LootContext context) {
        if (context.hasParameter(LootContextParams.BLOCK_STATE)) {
            Block block = context.getParameter(LootContextParams.BLOCK_STATE).getBlock();
            return BuiltInRegistries.BLOCK.getKey(block);
        }
        if (context.hasParameter(LootContextParams.THIS_ENTITY)) {
            Entity entity = context.getParameter(LootContextParams.THIS_ENTITY);
            if (entity instanceof Player) return null; // chest opened by player, not a mob death
            return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        }
        return null;
    }
}
