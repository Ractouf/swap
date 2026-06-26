package com.randomdrops;

import com.randomdrops.command.QueryCommand;
import com.randomdrops.mapping.DropMappingGenerator;
import com.randomdrops.hook.DropHook;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RandomDropsMod implements ModInitializer {

    public static final String MOD_ID = "randomdrops";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        DropMappingGenerator.init();
        DropHook.register();
        QueryCommand.register();
        LOGGER.info("RandomDrops initialised ({} items in pool).", DropMappingGenerator.getItemPool().size());
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
