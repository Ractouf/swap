package com.randomdrops.hook;

import com.randomdrops.advancement.AdvancementHelper;
import com.randomdrops.mapping.DropMappingGenerator;
import com.randomdrops.mapping.DropMappingState;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
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

        // Award silk touch advancement the first time a player mines with a silk touch tool
        if (source.startsWith("block") && context.hasParameter(LootContextParams.TOOL)
                && context.hasParameter(LootContextParams.THIS_ENTITY)) {
            Entity miner = context.getParameter(LootContextParams.THIS_ENTITY);
            if (miner instanceof ServerPlayer sp && hasSilkTouch(context.getParameter(LootContextParams.TOOL))) {
                AdvancementHelper.award(sp, "silk_touch_pickaxe", "used_silk_touch");
            }
        }

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

    private static boolean hasSilkTouch(ItemInstance tool) {
        ItemEnchantments enchantments = tool.get(DataComponents.ENCHANTMENTS);
        if (enchantments == null) return false;
        Identifier silkTouch = Identifier.fromNamespaceAndPath("minecraft", "silk_touch");
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            if (holder.is(silkTouch)) return true;
        }
        return false;
    }

    /** Returns "type|sourceId" for block and mob sources, null for everything else (chests, etc.). */
    private static String resolveSource(LootContext context) {
        if (context.hasParameter(LootContextParams.BLOCK_STATE)) {
            Block block = context.getParameter(LootContextParams.BLOCK_STATE).getBlock();
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
            if (blockId == null) return null;
            // Wall-mounted sign variants use a separate block ID but drop the same item as the
            // floor sign, so normalise them to avoid duplicate discovery entries.
            String path = blockId.getPath();
            // Wall-mounted variants and plant-body blocks share their drop item with a
            // canonical counterpart — normalise to avoid duplicate discovery entries.
            if (path.contains("_wall_hanging_sign")) {
                path = path.replace("_wall_hanging_sign", "_hanging_sign");
            } else if (path.contains("_wall_sign")) {
                path = path.replace("_wall_sign", "_sign");
            } else if (path.contains("_wall_banner")) {
                path = path.replace("_wall_banner", "_banner");
            } else if (path.contains("_wall_skull")) {
                path = path.replace("_wall_skull", "_skull");
            } else if (path.contains("_wall_head")) {
                path = path.replace("_wall_head", "_head");
            } else if (path.equals("kelp_plant")) {
                path = "kelp";
            } else if (path.equals("twisting_vines_plant")) {
                path = "twisting_vines";
            } else if (path.equals("weeping_vines_plant")) {
                path = "weeping_vines";
            }
            Identifier normalizedId = path.equals(blockId.getPath())
                ? blockId : Identifier.fromNamespaceAndPath(blockId.getNamespace(), path);
            return "block|" + normalizedId;
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
