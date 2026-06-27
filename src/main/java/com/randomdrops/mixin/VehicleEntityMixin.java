package com.randomdrops.mixin;

import com.randomdrops.mapping.DropMappingGenerator;
import com.randomdrops.mapping.DropMappingState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Intercepts the item-drop step of VehicleEntity.destroy() (shared by all boats and minecarts)
 * so their drops pass through the same randomisation and discovery-tagging pipeline as
 * block/mob loot-table drops.
 */
@Mixin(VehicleEntity.class)
public abstract class VehicleEntityMixin {

    @Shadow public abstract EntityType<?> getType();
    @Shadow public abstract ItemEntity spawnAtLocation(ServerLevel level, ItemStack stack);

    @Redirect(
        method = "destroy(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/Item;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/vehicle/VehicleEntity;spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;"
        )
    )
    private ItemEntity randomizeVehicleDrop(ServerLevel level, ItemStack original) {
        MinecraftServer server = level.getServer();
        if (server != null) {
            Identifier droppedId = BuiltInRegistries.ITEM.getKey(original.getItem());
            if (droppedId != null) {
                long seed = server.overworld().getSeed();
                Item replacement = DropMappingGenerator.getItem(seed, droppedId);
                ItemStack newStack = replacement == original.getItem()
                    ? original : new ItemStack(replacement, original.getCount());
                Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(getType());
                if (entityId != null) {
                    DropMappingState.tagStack(newStack, "mob|" + entityId + "|" + droppedId);
                }
                return spawnAtLocation(level, newStack);
            }
        }
        return spawnAtLocation(level, original);
    }
}
