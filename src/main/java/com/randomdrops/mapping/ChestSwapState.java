package com.randomdrops.mapping;

import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.*;
import java.util.stream.Collectors;

public class ChestSwapState extends SavedData {

    public static final Codec<ChestSwapState> CODEC =
        Codec.unboundedMap(Codec.STRING, Codec.STRING)
             .xmap(ChestSwapState::fromMap, s -> s.swaps);

    public static final SavedDataType<ChestSwapState> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("randomdrops", "chest_swaps"),
        ChestSwapState::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<String, String> swaps;
    private boolean generated = false;

    public ChestSwapState() {
        this.swaps = new HashMap<>();
    }

    private ChestSwapState(Map<String, String> swaps) {
        this.swaps = new HashMap<>(swaps);
    }

    private static ChestSwapState fromMap(Map<String, String> map) {
        return new ChestSwapState(map);
    }

    public static ChestSwapState get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        ChestSwapState state = overworld.getDataStorage().computeIfAbsent(TYPE);
        if (!state.generated) {
            state.generate(server);
            state.generated = true;
        }
        return state;
    }

    private void generate(MinecraftServer server) {
        if (!swaps.isEmpty()) return; // already loaded from disk

        HolderLookup.RegistryLookup<LootTable> reg = server.reloadableRegistries()
            .lookup()
            .lookupOrThrow(Registries.LOOT_TABLE);

        List<String> tables = reg.listElementIds()
            .map(ResourceKey::identifier)
            .filter(loc -> loc.getPath().startsWith("chests/"))
            .map(Identifier::toString)
            .sorted()
            .collect(Collectors.toList());

        if (tables.isEmpty()) return;

        List<String> shuffled = new ArrayList<>(tables);
        Collections.shuffle(shuffled, new Random(server.overworld().getSeed()));

        for (int i = 0; i < tables.size(); i++) {
            swaps.put(tables.get(i), shuffled.get(i));
        }
        setDirty();
    }

    public ResourceKey<LootTable> getSwap(ResourceKey<LootTable> original) {
        String swapped = swaps.get(original.identifier().toString());
        if (swapped == null) return original;
        String[] parts = swapped.split(":", 2);
        return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(parts[0], parts[1]));
    }
}
