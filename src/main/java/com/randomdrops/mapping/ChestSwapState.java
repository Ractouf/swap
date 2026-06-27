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

    // Encoded as a flat list of prefixed strings:
    //   "s:src=dst"       → swap entry (always global)
    //   "d:tableId"       → globally discovered loot table
    //   "pd|UUID|tableId" → per-player discovered loot table
    public static final Codec<ChestSwapState> CODEC = Codec.STRING.listOf()
        .xmap(
            list -> {
                Map<String, String> swaps = new HashMap<>();
                Set<String> discovered = new HashSet<>();
                Map<String, Set<String>> perPlayer = new HashMap<>();
                for (String s : list) {
                    if (s.startsWith("s:")) {
                        int eq = s.indexOf('=', 2);
                        if (eq > 2) swaps.put(s.substring(2, eq), s.substring(eq + 1));
                    } else if (s.startsWith("pd|")) {
                        int sep = s.indexOf('|', 3);
                        if (sep > 3) {
                            perPlayer.computeIfAbsent(s.substring(3, sep), k -> new HashSet<>())
                                     .add(s.substring(sep + 1));
                        }
                    } else if (s.startsWith("d:")) {
                        discovered.add(s.substring(2));
                    }
                }
                return new ChestSwapState(swaps, discovered, perPlayer);
            },
            state -> {
                List<String> list = new ArrayList<>();
                state.swaps.forEach((k, v) -> list.add("s:" + k + "=" + v));
                state.discoveredSrcs.forEach(d -> list.add("d:" + d));
                state.perPlayerDiscovered.forEach((uuid, tables) ->
                    tables.forEach(t -> list.add("pd|" + uuid + "|" + t)));
                return list;
            }
        );

    public static final SavedDataType<ChestSwapState> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("randomdrops", "chest_swaps"),
        ChestSwapState::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<String, String> swaps;
    private final Set<String> discoveredSrcs;             // global
    private final Map<String, Set<String>> perPlayerDiscovered; // UUID → table IDs
    private boolean generated = false;

    public ChestSwapState() {
        this.swaps = new HashMap<>();
        this.discoveredSrcs = new HashSet<>();
        this.perPlayerDiscovered = new HashMap<>();
    }

    private ChestSwapState(Map<String, String> swaps, Set<String> discoveredSrcs,
                           Map<String, Set<String>> perPlayerDiscovered) {
        this.swaps = new HashMap<>(swaps);
        this.discoveredSrcs = new HashSet<>(discoveredSrcs);
        this.perPlayerDiscovered = new HashMap<>(perPlayerDiscovered);
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
        if (!swaps.isEmpty()) return;

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

    /**
     * Record that a chest with this loot table has been opened.
     * playerId null = global pool; non-null = per-player pool.
     * Returns true if newly discovered.
     */
    public boolean recordDiscovered(ResourceKey<LootTable> tableKey, UUID playerId) {
        String tableStr = tableKey.identifier().toString();
        if (playerId != null) {
            if (perPlayerDiscovered.computeIfAbsent(playerId.toString(), k -> new HashSet<>())
                                   .add(tableStr)) {
                setDirty();
                return true;
            }
            return false;
        }
        if (discoveredSrcs.add(tableStr)) { setDirty(); return true; }
        return false;
    }

    /**
     * Returns only swaps for loot tables that have been opened.
     * playerId null = global pool; non-null = that player's personal pool.
     */
    public Map<String, String> getDiscoveredSwaps(UUID playerId) {
        Set<String> srcs = playerId != null
            ? perPlayerDiscovered.getOrDefault(playerId.toString(), Collections.emptySet())
            : discoveredSrcs;
        Map<String, String> result = new HashMap<>();
        for (String src : srcs) {
            String dst = swaps.get(src);
            if (dst != null) result.put(src, dst);
        }
        return Collections.unmodifiableMap(result);
    }

    public ResourceKey<LootTable> getSwap(ResourceKey<LootTable> original) {
        String swapped = swaps.get(original.identifier().toString());
        if (swapped == null) return original;
        String[] parts = swapped.split(":", 2);
        return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(parts[0], parts[1]));
    }
}
