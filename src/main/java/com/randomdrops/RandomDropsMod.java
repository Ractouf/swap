package com.randomdrops;

import com.randomdrops.command.QueryCommand;
import com.randomdrops.hook.DropHook;
import com.randomdrops.mapping.ChestSwapState;
import com.randomdrops.mapping.DropMappingGenerator;
import com.randomdrops.mapping.DropMappingState;
import com.randomdrops.network.DiscoveryDataPayload;
import com.randomdrops.network.RequestDiscoveryPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class RandomDropsMod implements ModInitializer {

    public static final String MOD_ID = "randomdrops";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** When true (default), all players share one discovery pool. When false, each player tracks their own. */
    public static final GameRule<Boolean> SHARED_DISCOVERY =
        GameRuleBuilder.forBoolean(true)
            .category(GameRuleCategory.MISC)
            .buildAndRegister(Identifier.fromNamespaceAndPath(MOD_ID, "shared_discovery"));

    @Override
    public void onInitialize() {
        DropMappingGenerator.init();
        DropHook.register();
        QueryCommand.register();

        PayloadTypeRegistry.serverboundPlay().register(RequestDiscoveryPayload.TYPE, RequestDiscoveryPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DiscoveryDataPayload.TYPE, DiscoveryDataPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RequestDiscoveryPayload.TYPE, (payload, context) ->
            context.server().execute(() ->
                ServerPlayNetworking.send(context.player(),
                    new DiscoveryDataPayload(buildEntriesForPlayer(context.server(), context.player()), true))
            )
        );

        LOGGER.info("RandomDrops initialised ({} items in pool).", DropMappingGenerator.getItemPool().size());
    }

    /**
     * Pushes a silent cache-only discovery update.
     * If SHARED_DISCOVERY is on, broadcasts to all online players.
     * If off, sends only to the player who triggered the discovery.
     */
    public static void sendDiscoveryUpdate(MinecraftServer server, ServerPlayer triggeringPlayer) {
        boolean shared = server.getGameRules().get(SHARED_DISCOVERY);
        if (shared) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(player,
                    new DiscoveryDataPayload(buildEntriesForPlayer(server, player), false));
            }
        } else {
            ServerPlayNetworking.send(triggeringPlayer,
                new DiscoveryDataPayload(buildEntriesForPlayer(server, triggeringPlayer), false));
        }
    }

    /** Builds the full discovery entry list for a specific player. */
    private static List<String> buildEntriesForPlayer(MinecraftServer server, ServerPlayer player) {
        boolean shared = server.getGameRules().get(SHARED_DISCOVERY);
        UUID playerId = shared ? null : player.getUUID();

        List<String> entries = new ArrayList<>();
        entries.addAll(DropMappingState.get(server).getCategorizedEntries(playerId));
        ChestSwapState.get(server).getDiscoveredSwaps(playerId)
            .forEach((src, tgt) -> entries.add("chest|" + src + "|" + tgt));
        entries.addAll(buildAllSources());

        AdvancementHolder cartographer = server.getAdvancements().get(id("cartographer"));
        if (cartographer != null && player.getAdvancements().getOrStartProgress(cartographer).isDone())
            entries.add("flag|missing_tab_unlocked");

        return entries;
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Scans all block and entity loot tables to produce the full set of possible source IDs. */
    private static List<String> buildAllSources() {
        Set<String> result = new LinkedHashSet<>();

        for (Block block : BuiltInRegistries.BLOCK) {
            var lt = block.getLootTable();
            if (lt.isEmpty()) continue;
            if (!lt.get().identifier().getPath().startsWith("blocks/")) continue;
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
            if (blockId == null) continue;
            String path = DropHook.normalizePath(blockId.getPath());
            Identifier normalizedId = path.equals(blockId.getPath())
                ? blockId : Identifier.fromNamespaceAndPath(blockId.getNamespace(), path);
            // Crop blocks have asItem()==AIR; check the normalised item ID instead
            Item item = BuiltInRegistries.ITEM.getValue(normalizedId);
            if (item == null || item == Items.AIR) continue;
            result.add("src|block|" + normalizedId);
        }

        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type == EntityTypes.PLAYER) continue;
            var lt = type.getDefaultLootTable();
            if (lt.isEmpty()) continue;
            if (!lt.get().identifier().getPath().startsWith("entities/")) continue;
            Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (entityId == null) continue;
            Identifier eggId = Identifier.fromNamespaceAndPath(entityId.getNamespace(), entityId.getPath() + "_spawn_egg");
            Item egg = BuiltInRegistries.ITEM.getValue(eggId);
            if (egg == null || egg == Items.AIR) continue;
            result.add("src|mob|" + entityId);
        }

        // Item frames have no loot table and no spawn egg; drops are handled by ItemFrameMixin
        result.add("src|mob|minecraft:item_frame");
        result.add("src|mob|minecraft:glow_item_frame");

        return new ArrayList<>(result);
    }
}
