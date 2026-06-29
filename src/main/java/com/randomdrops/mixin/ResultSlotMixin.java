package com.randomdrops.mixin;

import com.randomdrops.RandomDropsMod;
import com.randomdrops.mapping.DropMappingState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResultSlot.class)
public class ResultSlotMixin {

    @Inject(method = "onTake", at = @At("HEAD"))
    private void recordCraftDiscovery(Player player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer sp)) return;
        var server = sp.level().getServer();
        if (server == null) return;
        boolean isNew = DropMappingState.tryRecordAndStrip(stack, server, sp);
        if (isNew) RandomDropsMod.sendDiscoveryUpdate(server, sp);
        RandomDropsMod.onItemAcquiredForBingo(server, sp, stack.getItem());
    }
}
