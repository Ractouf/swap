package com.randomdrops.mapping;

import com.mojang.serialization.Codec;
import com.randomdrops.RandomDropsMod;
import com.randomdrops.advancement.AdvancementHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

/**
 * Stores items the player has actually picked up as randomised drops.
 * Key format: "type|sourceId|droppedItemId"
 *   type       = "block" or "mob"
 *   sourceId   = block/entity-type identifier (minecraft:grass_block, minecraft:sheep)
 *   droppedItemId = what the source would have normally dropped (minecraft:dirt, minecraft:white_wool)
 *
 * Codec encoding:
 *   plain string       → global discovery entry
 *   "p|UUID|key"       → per-player discovery entry
 */
public class DropMappingState extends SavedData {

    public static final Codec<DropMappingState> CODEC =
        Codec.STRING.listOf()
             .xmap(list -> {
                 Set<String> global = new HashSet<>();
                 Map<String, Set<String>> perPlayer = new HashMap<>();
                 for (String s : list) {
                     if (s.startsWith("p|")) {
                         int sep = s.indexOf('|', 2);
                         if (sep > 2) {
                             perPlayer.computeIfAbsent(s.substring(2, sep), k -> new HashSet<>())
                                      .add(s.substring(sep + 1));
                         }
                     } else {
                         global.add(s);
                     }
                 }
                 return new DropMappingState(global, perPlayer);
             },
             state -> {
                 List<String> list = new ArrayList<>(state.discovered);
                 state.perPlayer.forEach((uuid, keys) ->
                     keys.forEach(k -> list.add("p|" + uuid + "|" + k)));
                 return list;
             });

    public static final SavedDataType<DropMappingState> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("randomdrops", "discoveries"),
        DropMappingState::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    static final String TAG_KEY = "randomdrops_src";

    private static final Set<String> MINERAL_ITEMS = Set.of(
        "diamond", "emerald", "raw_iron", "raw_gold", "raw_copper",
        "lapis_lazuli", "coal", "redstone", "quartz", "gold_nugget",
        "netherite_scrap", "amethyst_shard", "iron_nugget",
        "iron_ingot", "gold_ingot", "copper_ingot"
    );
    private static final Set<String> LUXURY_FOODS = Set.of(
        "golden_apple", "enchanted_golden_apple", "cake",
        "pumpkin_pie", "rabbit_stew", "suspicious_stew", "mushroom_stew",
        "honey_bottle", "golden_carrot"
    );
    private final Set<String> discovered;             // global pool
    private final Map<String, Set<String>> perPlayer; // UUID string → composite keys
    private long worldSeed;

    public DropMappingState() {
        this.discovered = new HashSet<>();
        this.perPlayer = new HashMap<>();
    }

    private DropMappingState(Set<String> discovered, Map<String, Set<String>> perPlayer) {
        this.discovered = discovered;
        this.perPlayer = perPlayer;
    }

    public static DropMappingState get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        DropMappingState state = overworld.getDataStorage().computeIfAbsent(TYPE);
        state.worldSeed = overworld.getSeed();
        return state;
    }

    public long getWorldSeed() { return worldSeed; }

    /** Tag a replacement stack with composite source info for pickup recording. */
    public static void tagStack(ItemStack stack, String compositeKey) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
            tag -> tag.putString(TAG_KEY, compositeKey));
    }

    /**
     * Called on pickup: reads the composite tag, records the discovery, strips the tag.
     * Routes to the global pool or the player's personal pool based on the gamerule.
     * Returns true if a new discovery was recorded.
     */
    public static boolean tryRecordAndStrip(ItemStack stack, MinecraftServer server, ServerPlayer player) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) return false;
        CompoundTag tag = data.copyTag();
        if (!tag.contains(TAG_KEY)) return false;

        String compositeKey = tag.getStringOr(TAG_KEY, "");
        if (compositeKey.isEmpty()) return false;
        String[] parts = compositeKey.split("\\|", 3);
        if (parts.length != 3) return false;

        DropMappingState state = DropMappingState.get(server);

        // Check if this is a "normal" drop (seed happened to map item to itself)
        Identifier droppedId = Identifier.tryParse(parts[2]);
        if (droppedId != null) {
            Item replacement = DropMappingGenerator.getItem(state.worldSeed, droppedId);
            Identifier replacementId = BuiltInRegistries.ITEM.getKey(replacement);
            if (droppedId.equals(replacementId)) {
                if (player != null) AdvancementHelper.award(player, "normal_loot", "got_normal");
                stripTag(stack);
                return false;
            }
        }

        boolean shared = server.getGameRules().get(RandomDropsMod.SHARED_DISCOVERY);
        String playerKey = (!shared && player != null) ? player.getUUID().toString() : null;

        boolean isNew = state.markDiscovered(compositeKey, playerKey);
        if (isNew && player != null) {
            state.fireAdvancementTriggers(player, compositeKey, playerKey);
        }

        stripTag(stack);
        return isNew;
    }

    private static void stripTag(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.remove(TAG_KEY));
        CustomData remaining = stack.get(DataComponents.CUSTOM_DATA);
        if (remaining != null && remaining.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
    }

    private void fireAdvancementTriggers(ServerPlayer player, String compositeKey, String playerKey) {
        String[] parts = compositeKey.split("\\|", 3);
        String type = parts[0];
        String sourceId = parts[1];
        String droppedItemId = parts[2];

        AdvancementHelper.award(player, "root", "first_drop");

        if ("block".equals(type) && sourceId.contains("shulker_box"))
            AdvancementHelper.award(player, "gone_forever", "shulker_broken");
        if ("block".equals(type) && isLogBlock(sourceId))
            AdvancementHelper.award(player, "woodnt_you_know_it", "log_non_log");
        if ("block".equals(type) && isOreBlock(sourceId))
            AdvancementHelper.award(player, "amateur_geologist", "ore_broken");
        if ("block".equals(type) && sourceId.contains("diamond_ore"))
            AdvancementHelper.award(player, "wrong_ore", "ore_non_ore");
        if ("block".equals(type) && isCropSource(sourceId))
            AdvancementHelper.award(player, "first_harvest", "crop_broken");
        if ("block".equals(type) && "minecraft:crafting_table".equals(sourceId))
            AdvancementHelper.award(player, "table_flip", "table_transformed");
        Identifier droppedId = Identifier.tryParse(droppedItemId);
        if (droppedId != null) {
            Item replacement = DropMappingGenerator.getItem(worldSeed, droppedId);
            Identifier replacementId = BuiltInRegistries.ITEM.getKey(replacement);
            if (replacementId != null) {
                String rPath = replacementId.getPath();
                if (replacement.components().has(DataComponents.FOOD)) {
                    if ("block".equals(type)) AdvancementHelper.award(player, "bon_appetit", "food_from_block");
                    if ("mob".equals(type))   AdvancementHelper.award(player, "chefs_surprise", "food_from_mob");
                    if (LUXURY_FOODS.contains(rPath)) AdvancementHelper.award(player, "michelin_star", "got_luxury_food");
                }
                if (rPath.endsWith("_spawn_egg")) {
                    AdvancementHelper.award(player, "spawn_egg", "got_egg");
                    if ("mob".equals(type)) {
                        Identifier srcId = Identifier.tryParse(sourceId);
                        if (srcId != null && (srcId.getPath() + "_spawn_egg").equals(rPath))
                            AdvancementHelper.award(player, "self_sustaining", "got_own_egg");
                    }
                }
                if ("block".equals(type) && isOreBlock(sourceId) && MINERAL_ITEMS.contains(rPath))
                    AdvancementHelper.award(player, "transmutation", "ore_transmuted");
                if ("mob".equals(type) && isPlantItem(rPath))
                    AdvancementHelper.award(player, "circle_of_life", "mob_drops_plant");
            }
        }

        // Count against whichever pool this discovery was recorded in
        Set<String> countSet = playerKey != null
            ? perPlayer.getOrDefault(playerKey, Collections.emptySet())
            : discovered;
        int count = countSet.size();
        if (count >= 50)  AdvancementHelper.award(player, "curious_mind", "discovery_50");
        if (count >= 200) AdvancementHelper.award(player, "cartographer", "discovery_200");
        if (count >= 500) AdvancementHelper.award(player, "grand_unified", "discovery_500");
    }

    private static boolean isLogBlock(String sourceId) {
        return sourceId.contains("_log") || sourceId.contains("_stem") || sourceId.contains("_wood");
    }

    private static boolean isOreBlock(String sourceId) {
        return sourceId.contains("_ore") || sourceId.contains("ancient_debris");
    }

    private static boolean isCropSource(String sourceId) {
        String path = sourceId.contains(":") ? sourceId.substring(sourceId.indexOf(':') + 1) : sourceId;
        return switch (path) {
            case "wheat", "carrot", "potato", "beetroot", "sweet_berries",
                 "glow_berries", "cocoa_beans", "nether_wart", "melon_slice",
                 "chorus_fruit", "bamboo", "torchflower", "pitcher_plant" -> true;
            default -> false;
        };
    }

    private static boolean isPlantItem(String path) {
        if (path.endsWith("_sapling") || path.endsWith("_seeds") || path.endsWith("_seed")
                || path.endsWith("_leaves") || path.endsWith("_mushroom")) return true;
        return Set.of(
            "bamboo", "kelp", "cactus", "sugar_cane", "vine", "lily_pad",
            "wheat", "carrot", "potato", "beetroot", "sweet_berries", "glow_berries",
            "nether_wart", "moss_block", "short_grass", "fern", "dead_bush",
            "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet",
            "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy",
            "cornflower", "lily_of_the_valley", "wither_rose", "sunflower",
            "lilac", "rose_bush", "peony", "torchflower", "spore_blossom"
        ).contains(path);
    }

    /** Records a discovery. playerKey null = global pool; non-null = per-player pool. */
    public boolean markDiscovered(String compositeKey, String playerKey) {
        if (playerKey != null) {
            if (perPlayer.computeIfAbsent(playerKey, k -> new HashSet<>()).add(compositeKey)) {
                setDirty();
                return true;
            }
            return false;
        }
        if (discovered.add(compositeKey)) { setDirty(); return true; }
        return false;
    }

    public boolean markDiscovered(String compositeKey) {
        return markDiscovered(compositeKey, null);
    }

    /**
     * Returns categorised entries for the network packet.
     * playerId null = global pool; non-null = that player's personal pool.
     * Format: "type|sourceId|replacementId"
     */
    public List<String> getCategorizedEntries(UUID playerId) {
        Set<String> source = playerId != null
            ? perPlayer.getOrDefault(playerId.toString(), Collections.emptySet())
            : discovered;

        List<String> result = new ArrayList<>();
        for (String key : source) {
            String[] parts = key.split("\\|", 3);
            if (parts.length != 3) continue;
            Identifier droppedId = Identifier.tryParse(parts[2]);
            if (droppedId == null) continue;

            Item replacement = DropMappingGenerator.getItem(worldSeed, droppedId);
            Identifier replacementId = BuiltInRegistries.ITEM.getKey(replacement);
            if (replacementId == null || replacementId.equals(droppedId)) continue;

            result.add(parts[0] + "|" + parts[1] + "|" + replacementId);
        }
        return result;
    }
}
