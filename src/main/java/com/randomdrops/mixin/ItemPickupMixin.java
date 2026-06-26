package com.randomdrops.mixin;

import com.randomdrops.mapping.DropMappingState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public class ItemPickupMixin {

    @Inject(method = "playerTouch", at = @At("HEAD"))
    private void onPlayerTouch(Player player, CallbackInfo ci) {
        if (player.level().isClientSide()) return;
        ItemEntity self = (ItemEntity)(Object)this;
        if (self.hasPickUpDelay()) return;

        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        ItemStack stack = self.getItem();
        ServerPlayer sp = player instanceof ServerPlayer spl ? spl : null;
        DropMappingState.tryRecordAndStrip(stack, server, sp);
    }
}
