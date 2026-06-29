package com.randomdrops.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.randomdrops.mapping.DropMappingState;
import com.randomdrops.mapping.RecipeMappingGenerator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CraftingMenu.class)
public class CraftingMenuMixin {

    @Redirect(
        method = "slotChangedCraftingGrid",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/ResultContainer;setItem(ILnet/minecraft/world/item/ItemStack;)V")
    )
    private static void randomizeCraftResult(ResultContainer container, int slot, ItemStack result,
            @Local ServerLevel level,
            @Local RecipeHolder<CraftingRecipe> recipe) {
        if (result.isEmpty()) {
            container.setItem(slot, result);
            return;
        }
        Identifier originalId = BuiltInRegistries.ITEM.getKey(result.getItem());
        if (originalId == null) {
            container.setItem(slot, result);
            return;
        }
        Item randomized = RecipeMappingGenerator.getItem(level.getSeed(), originalId);
        if (randomized == result.getItem()) {
            container.setItem(slot, result);
            return;
        }
        ItemStack randomizedStack = new ItemStack(randomized, result.getCount());
        DropMappingState.tagStack(randomizedStack, "craft|" + originalId + "|" + originalId);
        container.setItem(slot, randomizedStack);
    }
}
