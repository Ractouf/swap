package com.randomdrops.mixin;

import com.randomdrops.RandomDropsMod;
import com.randomdrops.mapping.DropMappingState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Strips the discovery tag from the clicked slot's stack before vanilla processes the click.
 * Slot#onTake only fires for single-click pickup, and quick-move (shift-click) merges stacks
 * via direct component comparison — a tagged stack never matches an already-untagged stack of
 * the same item, so it lands in a new slot instead of stacking. Stripping here, before any
 * merge comparison runs, fixes stacking for every click type (pickup, quick-move, swap, etc.).
 */
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerClickMixin {

    @Inject(method = "clicked", at = @At("HEAD"))
    private void stripBeforeClick(int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer sp)) return;
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        if (slotId < 0 || slotId >= self.slots.size()) return;

        ItemStack stack = self.getSlot(slotId).getItem();
        if (stack.isEmpty()) return;

        MinecraftServer server = sp.level().getServer();
        if (server == null) return;

        boolean isNew = DropMappingState.tryRecordAndStrip(stack, server, sp);
        if (isNew) RandomDropsMod.sendDiscoveryUpdate(server, sp);
    }
}
