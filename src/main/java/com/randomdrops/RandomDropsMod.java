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
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
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

        ServerPlayNetworking.registerGlobalReceiver(RequestDiscoveryPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                boolean shared = context.server().getGameRules().get(SHARED_DISCOVERY);
                UUID playerId = shared ? null : context.player().getUUID();

                List<String> entries = new ArrayList<>();

                entries.addAll(DropMappingState.get(context.server()).getCategorizedEntries(playerId));

                ChestSwapState.get(context.server()).getDiscoveredSwaps(playerId)
                    .forEach((src, tgt) -> entries.add("chest|" + src + "|" + tgt));

                ServerPlayNetworking.send(context.player(), new DiscoveryDataPayload(entries));
            });
        });

        LOGGER.info("RandomDrops initialised ({} items in pool).", DropMappingGenerator.getItemPool().size());
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
