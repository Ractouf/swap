package com.randomdrops.mixin;

import com.randomdrops.advancement.AdvancementHelper;
import com.randomdrops.mapping.ChestSwapState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RandomizableContainer.class)
public interface RandomizableContainerMixin {

    @Shadow ResourceKey<LootTable> getLootTable();
    @Shadow void setLootTable(ResourceKey<LootTable> key);
    @Shadow Level getLevel();

    @Inject(method = "unpackLootTable", at = @At("HEAD"))
    private void swapChestTable(Player player, CallbackInfo ci) {
        ResourceKey<LootTable> current = getLootTable();
        if (current == null) return;
        Level level = getLevel();
        if (!(level instanceof ServerLevel sl)) return;
        MinecraftServer server = sl.getServer();
        if (server == null) return;
        ChestSwapState state = ChestSwapState.get(server);
        boolean isNew = state.recordDiscovered(current);
        if (isNew && player instanceof ServerPlayer sp) {
            AdvancementHelper.award(sp, "pandoras_chest", "chest_opened");
        }
        setLootTable(state.getSwap(current));
    }
}
