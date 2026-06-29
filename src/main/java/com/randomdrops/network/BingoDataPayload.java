package com.randomdrops.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: sends the 5×5 bingo grid and which cells the player has collected.
 * grid: 25 item identifier strings (row-major order)
 * collected: 25 booleans
 * openScreen: true = open the bingo screen, false = silent cache update
 */
public record BingoDataPayload(List<String> grid, List<Boolean> collected, boolean openScreen)
        implements CustomPacketPayload {

    public static final Type<BingoDataPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("randomdrops", "bingo_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BingoDataPayload> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeBoolean(p.openScreen());
            for (String s : p.grid()) buf.writeUtf(s);
            for (boolean b : p.collected()) buf.writeBoolean(b);
        },
        buf -> {
            boolean openScreen = buf.readBoolean();
            List<String> grid = new ArrayList<>(25);
            for (int i = 0; i < 25; i++) grid.add(buf.readUtf());
            List<Boolean> collected = new ArrayList<>(25);
            for (int i = 0; i < 25; i++) collected.add(buf.readBoolean());
            return new BingoDataPayload(grid, collected, openScreen);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
