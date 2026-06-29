package com.randomdrops.mixin.client;

import com.randomdrops.client.DiscoveryClientCache;
import com.randomdrops.hook.DropHook;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
@Mixin(Item.class)
public class ItemTooltipMixin {

    @Inject(method = "appendHoverText", at = @At("TAIL"))
    private void addDiscoveryTooltip(ItemStack stack, Item.TooltipContext context,
            TooltipDisplay tooltipDisplay, Consumer<Component> components,
            TooltipFlag flag, CallbackInfo ci) {

        Item item = stack.getItem();
        Identifier sourceId = resolveSourceId(item);
        if (sourceId == null || !DiscoveryClientCache.isKnownSource(sourceId)) return;

        if (DiscoveryClientCache.isDiscovered(sourceId)) {
            Set<Identifier> dropIds = DiscoveryClientCache.getDrops(sourceId);
            String dropList = dropIds.stream()
                .map(id -> {
                    Item dropItem = BuiltInRegistries.ITEM.getValue(id);
                    return new ItemStack(dropItem).getHoverName().getString();
                })
                .collect(Collectors.joining(", "));
            components.accept(Component.literal("✓ Drops: " + dropList)
                .withStyle(s -> s.withColor(0x55FF55)));
        } else {
            components.accept(Component.literal("✗ Not yet discovered")
                .withStyle(s -> s.withColor(0xFF5555)));
        }
    }

    private static Identifier resolveSourceId(Item item) {
        // Block source: look up via block registry + normalizePath
        Block block = Block.byItem(item);
        if (block != Blocks.AIR) {
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
            if (blockId != null) {
                String normalized = DropHook.normalizePath(blockId.getPath());
                return Identifier.fromNamespaceAndPath(blockId.getNamespace(), normalized);
            }
        }
        // Mob-source items (item_frame, glow_item_frame): check item ID directly
        return BuiltInRegistries.ITEM.getKey(item);
    }
}
