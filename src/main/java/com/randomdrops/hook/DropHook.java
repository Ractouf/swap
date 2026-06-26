package com.randomdrops.hook;

import com.randomdrops.mapping.DropMappingGenerator;
import com.randomdrops.mapping.DropMappingState;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
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
        // Returns "block|minecraft:grass_block" or "mob|minecraft:sheep", or null to skip
        String source = resolveSource(context);
        if (source == null) return;

        MinecraftServer server = context.getLevel().getServer();
        if (server == null) return;
        long worldSeed = server.overworld().getSeed();

        ListIterator<ItemStack> iter = drops.listIterator();
        while (iter.hasNext()) {
            ItemStack original = iter.next();
            if (original.isEmpty()) continue;

            Item originalItem = original.getItem();
            Identifier droppedItemId = BuiltInRegistries.ITEM.getKey(originalItem);
            if (droppedItemId == null) continue;

            Item replacement = DropMappingGenerator.getItem(worldSeed, droppedItemId);

            // Copy for normal drops to preserve existing NBT; new stack for replacements
            ItemStack newStack = (replacement == originalItem)
                ? original.copy()
                : new ItemStack(replacement, original.getCount());
            DropMappingState.tagStack(newStack, source + "|" + droppedItemId);
            iter.set(newStack);
        }
    }

    /** Returns "type|sourceId" for block and mob sources, null for everything else (chests, etc.). */
    private static String resolveSource(LootContext context) {
        if (context.hasParameter(LootContextParams.BLOCK_STATE)) {
            Block block = context.getParameter(LootContextParams.BLOCK_STATE).getBlock();
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
            return blockId == null ? null : "block|" + blockId;
        }
        if (context.hasParameter(LootContextParams.THIS_ENTITY)) {
            Entity entity = context.getParameter(LootContextParams.THIS_ENTITY);
            if (entity instanceof Player) return null;
            Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            return entityId == null ? null : "mob|" + entityId;
        }
        return null;
    }
}
