package com.randomdrops.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestBingoPayload() implements CustomPacketPayload {

    public static final Type<RequestBingoPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("randomdrops", "request_bingo"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestBingoPayload> CODEC =
        StreamCodec.unit(new RequestBingoPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
