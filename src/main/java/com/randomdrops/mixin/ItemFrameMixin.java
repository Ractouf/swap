package com.randomdrops.mixin;

import com.randomdrops.mapping.DropMappingGenerator;
import com.randomdrops.mapping.DropMappingState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemFrame.class)
public abstract class ItemFrameMixin {

    @Redirect(
        method = "dropItem(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Z)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/decoration/ItemFrame;spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;"
        )
    )
    private ItemEntity randomizeFrameDrop(ItemFrame instance, ServerLevel level, ItemStack original) {
        Identifier originalId = BuiltInRegistries.ITEM.getKey(original.getItem());
        if (originalId != null) {
            String path = originalId.getPath();
            if ("item_frame".equals(path) || "glow_item_frame".equals(path)) {
                MinecraftServer server = level.getServer();
                if (server != null) {
                    long seed = server.overworld().getSeed();
                    Item replacement = DropMappingGenerator.getItem(seed, originalId);
                    ItemStack newStack = replacement == original.getItem()
                        ? original : new ItemStack(replacement, original.getCount());
                    Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(instance.getType());
                    if (entityId != null) {
                        DropMappingState.tagStack(newStack, "mob|" + entityId + "|" + originalId);
                    }
                    return instance.spawnAtLocation(level, newStack);
                }
            }
        }
        return instance.spawnAtLocation(level, original);
    }
}
