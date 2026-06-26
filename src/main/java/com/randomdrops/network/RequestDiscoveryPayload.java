package com.randomdrops.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestDiscoveryPayload() implements CustomPacketPayload {

    public static final Type<RequestDiscoveryPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("randomdrops", "request_discovery"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestDiscoveryPayload> CODEC =
        StreamCodec.unit(new RequestDiscoveryPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
