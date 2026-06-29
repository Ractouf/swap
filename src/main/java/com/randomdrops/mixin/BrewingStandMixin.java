package com.randomdrops.mixin;

import com.randomdrops.RandomDropsMod;
import com.randomdrops.mapping.DropMappingState;
import com.randomdrops.mapping.RecipeMappingGenerator;
import net.minecraft.core.NonNullList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BrewingStandBlockEntity.class)
public class BrewingStandMixin {

    /** After brewing completes, replace all three potion output slots with randomised items. */
    @Inject(method = "doBrew", at = @At("TAIL"))
    private static void randomizeBrewOutputs(Level level, BlockPos pos, NonNullList<ItemStack> items, CallbackInfo ci) {
        if (!(level instanceof ServerLevel sl)) return;
        var server = sl.getServer();
        if (server == null || !server.getGameRules().get(RandomDropsMod.RECIPE_RANDOMIZE)) return;
        long seed = sl.getSeed();
        for (int i = 0; i < 3; i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;
            Identifier originalId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (originalId == null) continue;
            Item randomized = RecipeMappingGenerator.getItem(seed, originalId);
            if (randomized == stack.getItem()) continue;
            ItemStack randomizedStack = new ItemStack(randomized, stack.getCount());
            DropMappingState.tagStack(randomizedStack, "brew|" + originalId + "|" + originalId);
            items.set(i, randomizedStack);
        }
    }
}

/** Separate mixin targeting the inner PotionSlot to record brew discoveries on take. */
@Mixin(targets = "net.minecraft.world.inventory.BrewingStandMenu$PotionSlot")
class BrewingPotionSlotMixin {

    @Inject(method = "onTake", at = @At("HEAD"))
    private void recordBrewDiscovery(Player player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer sp)) return;
        var server = sp.level().getServer();
        if (server == null) return;
        boolean isNew = DropMappingState.tryRecordAndStrip(stack, server, sp);
        if (isNew) RandomDropsMod.sendDiscoveryUpdate(server, sp);
        RandomDropsMod.onItemAcquiredForBingo(server, sp, stack.getItem());
    }
}
