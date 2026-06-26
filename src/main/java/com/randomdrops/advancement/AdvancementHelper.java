package com.randomdrops.advancement;

import com.randomdrops.RandomDropsMod;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class AdvancementHelper {

    private AdvancementHelper() {}

    public static void award(ServerPlayer player, String path, String criterion) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        Identifier id = RandomDropsMod.id(path);
        AdvancementHolder holder = server.getAdvancements().get(id);
        if (holder != null) {
            player.getAdvancements().award(holder, criterion);
        }
    }
}
