package com.randomdrops.mapping;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

/**
 * Persistent server-side bingo state.
 * The 5×5 grid is deterministic (derived from world seed) and never stored.
 * Only the per-player collected cell indices are persisted.
 * Codec format per entry: "UUID:index1,index2,..."
 */
public class BingoState extends SavedData {

    private static final long BINGO_SEED_XOR = 0xB1B1_B1B1_B1B1_B1B1L;

    public static final Codec<BingoState> CODEC =
        Codec.STRING.listOf()
            .xmap(list -> {
                Map<String, Set<Integer>> data = new HashMap<>();
                for (String s : list) {
                    int colon = s.indexOf(':');
                    if (colon < 0) continue;
                    String uuid = s.substring(0, colon);
                    String[] parts = s.substring(colon + 1).split(",");
                    Set<Integer> indices = new HashSet<>();
                    for (String part : parts) {
                        try { indices.add(Integer.parseInt(part.trim())); } catch (NumberFormatException ignored) {}
                    }
                    data.put(uuid, indices);
                }
                return new BingoState(data);
            }, state -> {
                List<String> list = new ArrayList<>();
                state.collected.forEach((uuid, indices) -> {
                    if (indices.isEmpty()) return;
                    StringBuilder sb = new StringBuilder(uuid).append(':');
                    Iterator<Integer> it = indices.iterator();
                    while (it.hasNext()) {
                        sb.append(it.next());
                        if (it.hasNext()) sb.append(',');
                    }
                    list.add(sb.toString());
                });
                return list;
            });

    public static final SavedDataType<BingoState> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("randomdrops", "bingo"),
        BingoState::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<String, Set<Integer>> collected; // UUID → cell indices (0-24)

    public BingoState() { this.collected = new HashMap<>(); }
    private BingoState(Map<String, Set<Integer>> collected) { this.collected = collected; }

    public static BingoState get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Generates the deterministic 5×5 grid from the world seed. */
    public static Identifier[] generateGrid(long worldSeed) {
        List<Item> pool = new ArrayList<>(DropMappingGenerator.getItemPool());
        Collections.shuffle(pool, new Random(worldSeed ^ BINGO_SEED_XOR));
        Identifier[] grid = new Identifier[25];
        for (int i = 0; i < 25 && i < pool.size(); i++) {
            grid[i] = BuiltInRegistries.ITEM.getKey(pool.get(i));
        }
        return grid;
    }

    /** Returns the set of collected cell indices for the given player (0-24). */
    public Set<Integer> getCollected(String playerUUID) {
        return Collections.unmodifiableSet(collected.getOrDefault(playerUUID, Collections.emptySet()));
    }

    /**
     * Called when a player acquires an item. Marks all matching uncollected cells.
     * Returns true if any new cell was collected.
     */
    public boolean onItemAcquired(ServerPlayer player, Identifier[] grid, Item item) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null) return false;
        String uuid = player.getUUID().toString();
        Set<Integer> playerCollected = collected.computeIfAbsent(uuid, k -> new HashSet<>());
        boolean changed = false;
        for (int i = 0; i < grid.length; i++) {
            if (itemId.equals(grid[i]) && playerCollected.add(i)) {
                changed = true;
            }
        }
        if (changed) setDirty();
        return changed;
    }

    /** Returns true if the given collected set has at least one complete line. */
    public static boolean checkWin(Set<Integer> collectedIndices) {
        for (int r = 0; r < 5; r++) {
            boolean row = true;
            for (int c = 0; c < 5; c++) if (!collectedIndices.contains(r * 5 + c)) { row = false; break; }
            if (row) return true;
        }
        for (int c = 0; c < 5; c++) {
            boolean col = true;
            for (int r = 0; r < 5; r++) if (!collectedIndices.contains(r * 5 + c)) { col = false; break; }
            if (col) return true;
        }
        boolean d1 = true, d2 = true;
        for (int i = 0; i < 5; i++) {
            if (!collectedIndices.contains(i * 5 + i)) d1 = false;
            if (!collectedIndices.contains(i * 5 + (4 - i))) d2 = false;
        }
        return d1 || d2;
    }
}
