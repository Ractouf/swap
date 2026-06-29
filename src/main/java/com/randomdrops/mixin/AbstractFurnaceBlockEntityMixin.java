package com.randomdrops.mixin;

import com.randomdrops.RandomDropsMod;
import com.randomdrops.mapping.DropMappingState;
import com.randomdrops.mapping.RecipeMappingGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {

    @Shadow
    protected NonNullList<ItemStack> items;

    /**
     * After each burn() call, replace the result slot item with a randomised version and tag it.
     * Subsequent burns that grow an existing (already-tagged) stack are skipped.
     * The tag is stripped and the discovery recorded in FurnaceResultSlotMixin when the player takes the item.
     */
    @Inject(method = "serverTick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;burn(Lnet/minecraft/core/NonNullList;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V",
        shift = At.Shift.AFTER))
    private static void randomizeFurnaceSlot(ServerLevel level, BlockPos pos, BlockState state,
            AbstractFurnaceBlockEntity blockEntity, CallbackInfo ci) {
        var server = level.getServer();
        if (server == null || !server.getGameRules().get(RandomDropsMod.RECIPE_RANDOMIZE)) return;

        AbstractFurnaceBlockEntityMixin self = (AbstractFurnaceBlockEntityMixin)(Object)blockEntity;
        ItemStack result = self.items.get(2);
        if (result.isEmpty()) return;

        // Already tagged — stack just grew from a previous burn, nothing to do
        CustomData data = result.get(DataComponents.CUSTOM_DATA);
        if (data != null && !data.isEmpty() && data.copyTag().contains(DropMappingState.TAG_KEY)) return;

        Identifier originalId = BuiltInRegistries.ITEM.getKey(result.getItem());
        if (originalId == null) return;

        Item randomized = RecipeMappingGenerator.getItem(level.getSeed(), originalId);
        if (randomized == result.getItem()) return;

        ItemStack randomizedStack = new ItemStack(randomized, result.getCount());
        DropMappingState.tagStack(randomizedStack, "smelt|" + originalId + "|" + originalId);
        self.items.set(2, randomizedStack);
    }
}
