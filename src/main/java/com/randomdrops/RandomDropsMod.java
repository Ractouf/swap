package com.randomdrops;

import com.randomdrops.command.QueryCommand;
import com.randomdrops.hook.DropHook;
import com.randomdrops.mapping.ChestSwapState;
import com.randomdrops.mapping.DropMappingGenerator;
import com.randomdrops.mapping.DropMappingState;
import com.randomdrops.network.DiscoveryDataPayload;
import com.randomdrops.network.RequestDiscoveryPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class RandomDropsMod implements ModInitializer {

    public static final String MOD_ID = "randomdrops";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        DropMappingGenerator.init();
        DropHook.register();
        QueryCommand.register();

        PayloadTypeRegistry.serverboundPlay().register(RequestDiscoveryPayload.TYPE, RequestDiscoveryPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DiscoveryDataPayload.TYPE, DiscoveryDataPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RequestDiscoveryPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                List<String> entries = new ArrayList<>();

                // Block and mob drops (player must have physically picked them up)
                entries.addAll(DropMappingState.get(context.server()).getCategorizedEntries());

                // Chest swaps — only tables the player has actually opened
                ChestSwapState.get(context.server()).getDiscoveredSwaps()
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
