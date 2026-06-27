package com.randomdrops.mixin;

import com.randomdrops.mapping.DropMappingGenerator;
import com.randomdrops.mapping.DropMappingState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(VehicleEntity.class)
public abstract class VehicleEntityMixin {

    @Redirect(
        method = "destroy(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/Item;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/vehicle/VehicleEntity;spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;"
        )
    )
    private ItemEntity randomizeVehicleDrop(VehicleEntity instance, ServerLevel level, ItemStack original) {
        MinecraftServer server = level.getServer();
        if (server != null) {
            Identifier droppedId = BuiltInRegistries.ITEM.getKey(original.getItem());
            if (droppedId != null) {
                long seed = server.overworld().getSeed();
                Item replacement = DropMappingGenerator.getItem(seed, droppedId);
                ItemStack newStack = replacement == original.getItem()
                    ? original : new ItemStack(replacement, original.getCount());
                Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(instance.getType());
                if (entityId != null) {
                    DropMappingState.tagStack(newStack, "mob|" + entityId + "|" + droppedId);
                }
                return instance.spawnAtLocation(level, newStack);
            }
        }
        return instance.spawnAtLocation(level, original);
    }
}
