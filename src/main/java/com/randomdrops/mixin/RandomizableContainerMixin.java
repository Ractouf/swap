package com.randomdrops.mixin;

import com.randomdrops.RandomDropsMod;
import com.randomdrops.advancement.AdvancementHelper;
import com.randomdrops.mapping.ChestSwapState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(RandomizableContainer.class)
public interface RandomizableContainerMixin {

    @Shadow ResourceKey<LootTable> getLootTable();
    @Shadow void setLootTable(ResourceKey<LootTable> key);
    @Shadow Level getLevel();

    @Inject(method = "unpackLootTable", at = @At("HEAD"))
    private void swapChestTable(Player player, CallbackInfo ci) {
        Level level = getLevel();
        if (!(level instanceof ServerLevel sl)) return;
        MinecraftServer server = sl.getServer();
        if (server == null) return;
        ChestSwapState state = ChestSwapState.get(server);
        boolean shared = server.getGameRules().get(RandomDropsMod.SHARED_DISCOVERY);
        UUID playerId = (!shared && player instanceof ServerPlayer) ? player.getUUID() : null;

        ResourceKey<LootTable> current = getLootTable();
        if (current != null) {
            // First player to open — persist the position so later players can discover it too
            BlockPos pos = ((BlockEntity)(Object)this).getBlockPos();
            String posKey = sl.dimension().identifier() + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
            state.storePosition(posKey, current.identifier().toString());

            ResourceKey<LootTable> swapped = state.getSwap(current);
            if (swapped.identifier().equals(current.identifier()) && player instanceof ServerPlayer sp) {
                AdvancementHelper.award(sp, "normal_loot", "got_normal");
            }
            boolean isNew = state.recordDiscovered(current, playerId);
            if (isNew && player instanceof ServerPlayer sp) {
                AdvancementHelper.award(sp, "pandoras_chest", "chest_opened");
            }
            setLootTable(swapped);
        } else {
            // Loot table already consumed by a previous player — look up original by position
            BlockPos pos = ((BlockEntity)(Object)this).getBlockPos();
            String posKey = sl.dimension().identifier() + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
            String originalTableStr = state.getOriginalTable(posKey);
            if (originalTableStr != null) {
                String[] parts = originalTableStr.split(":", 2);
                if (parts.length == 2) {
                    ResourceKey<LootTable> originalKey = ResourceKey.create(
                        Registries.LOOT_TABLE,
                        Identifier.fromNamespaceAndPath(parts[0], parts[1])
                    );
                    ResourceKey<LootTable> swapped = state.getSwap(originalKey);
                    if (swapped.identifier().equals(originalKey.identifier()) && player instanceof ServerPlayer sp) {
                        AdvancementHelper.award(sp, "normal_loot", "got_normal");
                    }
                    boolean isNew = state.recordDiscovered(originalKey, playerId);
                    if (isNew && player instanceof ServerPlayer sp) {
                        AdvancementHelper.award(sp, "pandoras_chest", "chest_opened");
                    }
                }
            }
        }
    }
}
