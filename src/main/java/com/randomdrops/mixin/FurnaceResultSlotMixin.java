package com.randomdrops.mixin;

import com.randomdrops.RandomDropsMod;
import com.randomdrops.mapping.DropMappingState;
import com.randomdrops.mapping.RecipeMappingGenerator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FurnaceResultSlot.class)
public class FurnaceResultSlotMixin {

    @Inject(method = "onTake", at = @At("HEAD"))
    private void randomizeSmeltOutput(Player player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer sp)) return;
        Identifier originalId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (originalId == null) return;

        long seed = sp.level().getSeed();
        Item randomized = RecipeMappingGenerator.getItem(seed, originalId);
        if (randomized == stack.getItem()) return;

        // Record discovery
        var server = sp.level().getServer();
        if (server == null) return;
        String compositeKey = "smelt|" + originalId + "|" + originalId;
        boolean shared = server.getGameRules().get(RandomDropsMod.SHARED_DISCOVERY);
        String playerKey = shared ? null : sp.getUUID().toString();
        DropMappingState state = DropMappingState.get(server);
        boolean isNew = state.markDiscovered(compositeKey, playerKey);
        if (isNew) RandomDropsMod.sendDiscoveryUpdate(server, sp);

        // Swap: give randomized item, drain original so container handler adds nothing
        ItemStack randomizedStack = new ItemStack(randomized, stack.getCount());
        if (!sp.getInventory().add(randomizedStack)) {
            sp.drop(randomizedStack, false);
        }
        stack.shrink(stack.getCount());
    }
}
