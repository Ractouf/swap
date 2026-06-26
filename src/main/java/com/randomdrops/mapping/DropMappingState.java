package com.randomdrops.mapping;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;

public class DropMappingState extends SavedData {

    public static final Codec<DropMappingState> CODEC =
        Codec.unboundedMap(Codec.STRING, BuiltInRegistries.ITEM.byNameCodec())
             .xmap(DropMappingState::fromMap, s -> s.mappings);

    public static final SavedDataType<DropMappingState> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("randomdrops", "drop_mappings"),
        DropMappingState::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    // Lazily populated as loot tables fire; persisted so algorithm changes don't break saves.
    private final Map<String, Item> mappings;
    // Transient — set when the state is retrieved from storage.
    private long worldSeed;

    /** Used by SavedDataType supplier for new worlds. */
    public DropMappingState() {
        this.mappings = new HashMap<>();
    }

    private DropMappingState(Map<String, Item> mappings) {
        this.mappings = new HashMap<>(mappings);
    }

    private static DropMappingState fromMap(Map<String, Item> map) {
        return new DropMappingState(map);
    }

    /** Retrieve (or create) the state for the overworld of the given server. */
    public static DropMappingState get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        DropMappingState state = overworld.getDataStorage().computeIfAbsent(TYPE);
        state.worldSeed = overworld.getSeed();
        return state;
    }

    /**
     * Returns the mapped item for a (block, droppedItem) pair.
     * Works uniformly for blocks, entities, chests, fishing, and any other loot source.
     * Computed on first call, then persisted.
     */
    public Item getOrCompute(Identifier droppedItemId) {
        String key = droppedItemId.toString();
        Item existing = mappings.get(key);
        if (existing != null) return existing;

        Item computed = DropMappingGenerator.getItem(worldSeed, droppedItemId);
        mappings.put(key, computed);
        setDirty();
        return computed;
    }
}
