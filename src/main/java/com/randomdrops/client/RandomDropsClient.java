package com.randomdrops.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.randomdrops.client.screen.BingoScreen;
import com.randomdrops.client.screen.DiscoveryScreen;
import com.randomdrops.network.BingoDataPayload;
import com.randomdrops.network.DiscoveryDataPayload;
import com.randomdrops.network.RequestBingoPayload;
import com.randomdrops.network.RequestDiscoveryPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class RandomDropsClient implements ClientModInitializer {

    private static KeyMapping openDiscoveryKey;
    private static KeyMapping openBingoKey;

    @Override
    public void onInitializeClient() {
        openDiscoveryKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.randomdrops.open_discovery",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            KeyMapping.Category.MISC
        ));

        openBingoKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.randomdrops.open_bingo",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openDiscoveryKey.consumeClick()) {
                if (ClientPlayNetworking.canSend(RequestDiscoveryPayload.TYPE)) {
                    ClientPlayNetworking.send(new RequestDiscoveryPayload());
                }
            }
            while (openBingoKey.consumeClick()) {
                if (ClientPlayNetworking.canSend(RequestBingoPayload.TYPE)) {
                    ClientPlayNetworking.send(new RequestBingoPayload());
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(DiscoveryDataPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                DiscoveryClientCache.update(payload.entries());
                if (payload.openScreen()) {
                    context.client().setScreenAndShow(new DiscoveryScreen(payload.entries()));
                }
            })
        );

        ClientPlayNetworking.registerGlobalReceiver(BingoDataPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                BingoClientCache.update(payload.grid(), payload.collected());
                if (payload.openScreen()) {
                    context.client().setScreenAndShow(new BingoScreen());
                }
            })
        );
    }
}
