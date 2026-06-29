package com.randomdrops.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Carries categorised discovery entries from server → client.
 * Each entry is a pipe-separated string:
 *   "block|minecraft:grass_block|minecraft:gold_ingot"   (block drops)
 *   "mob|minecraft:sheep|minecraft:dirt"                 (mob drops)
 *   "chest|minecraft:chests/simple_dungeon|minecraft:chests/bastion_treasure"  (chest swap)
 *
 * openScreen=true  → player pressed H, open the discovery screen after updating cache
 * openScreen=false → server push on new discovery, update cache silently
 */
public record DiscoveryDataPayload(List<String> entries, boolean openScreen) implements CustomPacketPayload {

    public static final Type<DiscoveryDataPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("randomdrops", "discovery_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DiscoveryDataPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBoolean(payload.openScreen());
            buf.writeVarInt(payload.entries().size());
            payload.entries().forEach(buf::writeUtf);
        },
        buf -> {
            boolean openScreen = buf.readBoolean();
            int count = buf.readVarInt();
            List<String> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) list.add(buf.readUtf());
            return new DiscoveryDataPayload(Collections.unmodifiableList(list), openScreen);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
