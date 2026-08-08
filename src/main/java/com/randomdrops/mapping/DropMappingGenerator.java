package com.randomdrops.mapping;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public final class DropMappingGenerator {

    // Package-private: shared with RecipeMappingGenerator so both pools honor the same exclusions.
    static final Set<Item> EXCLUDED = Set.of(
        Items.AIR,
        Items.COMMAND_BLOCK,
        Items.REPEATING_COMMAND_BLOCK,
        Items.CHAIN_COMMAND_BLOCK,
        Items.COMMAND_BLOCK_MINECART,
        Items.BARRIER,
        Items.STRUCTURE_BLOCK,
        Items.STRUCTURE_VOID,
        Items.DEBUG_STICK,
        Items.KNOWLEDGE_BOOK,
        Items.LIGHT,
        Items.JIGSAW,
        Items.TEST_BLOCK,
        Items.TEST_INSTANCE_BLOCK,
        Items.BEDROCK,
        Items.END_PORTAL_FRAME,
        Items.REINFORCED_DEEPSLATE,
        Items.BUDDING_AMETHYST,
        Items.TRIAL_SPAWNER,
        Items.VAULT,
        Items.FARMLAND,
        Items.PETRIFIED_OAK_SLAB,
        Items.ENCHANTED_BOOK,
        Items.FILLED_MAP
    );

    private static final Logger LOGGER = LoggerFactory.getLogger(DropMappingGenerator.class);
    private static final FileToIdConverter LOOT_TABLE_FILES = FileToIdConverter.registry(Registries.LOOT_TABLE);

    // Pool of items that are actual loot-table drops (blocks + mobs).
    // This is the universe for both sources and targets, guaranteeing every
    // item has exactly one source via the world-seed permutation.
    private static List<Item> SOURCE_POOL = null;
    private static Map<Item, Integer> SOURCE_INDEX = null;

    // Block-derived sources, gathered by init() and held here until loadMobDrops()
    // merges in the mob-derived sources and freezes SOURCE_POOL/SOURCE_INDEX.
    private static Set<Item> pendingSources = null;

    // Permutation cache — recomputed only when the world seed changes.
    private static long cachedSeed = Long.MIN_VALUE;
    private static List<Item> cachedPermutation = null;
    private static Map<Item, Item> cachedInverse = null;

    private DropMappingGenerator() {}

    /** Call once from ModInitializer after registries are frozen. Gathers block-derived sources only —
     *  call loadMobDrops(server) once a server is available to complete the pool. */
    public static void init() {
        Set<Item> sources = new LinkedHashSet<>();

        // Block loot-table drops
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block.getLootTable().isEmpty()) continue;
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
            if (blockId == null) continue;
            String path = normalizePath(blockId.getPath());
            Identifier normalizedId = path.equals(blockId.getPath())
                ? blockId : Identifier.fromNamespaceAndPath(blockId.getNamespace(), path);
            Item item = BuiltInRegistries.ITEM.getValue(normalizedId);
            if (item != null && item != Items.AIR && !EXCLUDED.contains(item)) {
                sources.add(item);
            }
        }

        pendingSources = sources;
    }

    /**
     * Scans every entity type's actual loot table (recursively, including tag and nested-table
     * entries) to discover real mob-drop items, then merges them with the block-derived sources
     * from init() and freezes SOURCE_POOL/SOURCE_INDEX. Call once a server (and its resources) is
     * available, e.g. from ServerLifecycleEvents.SERVER_STARTING.
     */
    public static void loadMobDrops(MinecraftServer server) {
        if (pendingSources == null) throw new IllegalStateException("DropMappingGenerator.init() not called");

        ResourceManager resources = server.getResourceManager();
        Set<Identifier> visited = new HashSet<>();
        Set<Item> mobItems = new LinkedHashSet<>();

        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            Optional<ResourceKey<LootTable>> lootTable = type.getDefaultLootTable();
            if (lootTable.isEmpty()) continue;
            collectLootTableItems(resources, lootTable.get().identifier(), visited, mobItems);
        }

        Set<Item> sources = pendingSources;
        for (Item item : mobItems) {
            if (item != null && item != Items.AIR && !EXCLUDED.contains(item)) sources.add(item);
        }

        SOURCE_POOL = Collections.unmodifiableList(new ArrayList<>(sources));
        SOURCE_INDEX = new HashMap<>();
        for (int i = 0; i < SOURCE_POOL.size(); i++) {
            SOURCE_INDEX.put(SOURCE_POOL.get(i), i);
        }
        pendingSources = null;
    }

    /** Recursively reads a loot table's JSON and collects every item it can produce (item entries,
     *  expanded item tags, and nested/referenced loot tables). Best-effort: unreadable or malformed
     *  tables are logged and skipped rather than failing startup. */
    private static void collectLootTableItems(ResourceManager resources, Identifier tableId,
                                               Set<Identifier> visited, Set<Item> out) {
        if (!visited.add(tableId)) return;
        Identifier fileId = LOOT_TABLE_FILES.idToFile(tableId);
        resources.getResource(fileId).ifPresent(resource -> {
            try (var reader = resource.openAsReader()) {
                scanLootJson(JsonParser.parseReader(reader), resources, visited, out);
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Failed to read loot table {} while building the drop pool", tableId, e);
            }
        });
    }

    private static void scanLootJson(JsonElement el, ResourceManager resources,
                                      Set<Identifier> visited, Set<Item> out) {
        if (el.isJsonArray()) {
            for (JsonElement child : el.getAsJsonArray()) {
                scanLootJson(child, resources, visited, out);
            }
            return;
        }
        if (!el.isJsonObject()) return;

        JsonObject obj = el.getAsJsonObject();
        String type = obj.has("type") && obj.get("type").isJsonPrimitive() ? obj.get("type").getAsString() : null;

        if ("minecraft:item".equals(type) && obj.has("name")) {
            Identifier itemId = Identifier.tryParse(obj.get("name").getAsString());
            Item item = itemId == null ? null : BuiltInRegistries.ITEM.getValue(itemId);
            if (item != null) out.add(item);
        } else if ("minecraft:tag".equals(type) && obj.has("name")) {
            String tagStr = obj.get("name").getAsString();
            Identifier tagId = Identifier.tryParse(tagStr.startsWith("#") ? tagStr.substring(1) : tagStr);
            if (tagId != null) {
                TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
                for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tagKey)) {
                    out.add(holder.value());
                }
            }
        } else if ("minecraft:loot_table".equals(type) && obj.has("value")) {
            JsonElement value = obj.get("value");
            if (value.isJsonPrimitive()) {
                Identifier nestedId = Identifier.tryParse(value.getAsString());
                if (nestedId != null) collectLootTableItems(resources, nestedId, visited, out);
            } else if (value.isJsonObject()) {
                scanLootJson(value, resources, visited, out);
            }
        }

        for (var entry : obj.entrySet()) {
            scanLootJson(entry.getValue(), resources, visited, out);
        }
    }

    /**
     * Returns the item that replaces {@code droppedItemId} in this world.
     * Uses a single seed-derived permutation of SOURCE_POOL, guaranteeing
     * every item has exactly one source.
     * Returns the original item unchanged if it is not in the pool.
     */
    public static Item getItem(long worldSeed, Identifier droppedItemId) {
        if (SOURCE_POOL == null) throw new IllegalStateException("DropMappingGenerator.init() not called");
        Item droppedItem = BuiltInRegistries.ITEM.getValue(droppedItemId);
        Integer idx = SOURCE_INDEX.get(droppedItem);
        if (idx == null) return droppedItem;
        return permutation(worldSeed).get(idx);
    }

    /** Returns the unique source item that maps to {@code target} in this world, or null if none. */
    public static Item getSource(long worldSeed, Item target) {
        if (SOURCE_POOL == null) throw new IllegalStateException("DropMappingGenerator.init() not called");
        ensurePermutation(worldSeed);
        return cachedInverse.get(target);
    }

    public static List<Item> getItemPool() {
        if (SOURCE_POOL == null) throw new IllegalStateException("DropMappingGenerator.init() not called");
        return SOURCE_POOL;
    }

    // Normalises a block registry path to the item it drops.
    // Kept here so both DropHook and the pool builder share one copy.
    public static String normalizePath(String path) {
        if (path.contains("_wall_hanging_sign")) return path.replace("_wall_hanging_sign", "_hanging_sign");
        if (path.contains("_wall_sign"))         return path.replace("_wall_sign", "_sign");
        if (path.contains("_wall_banner"))       return path.replace("_wall_banner", "_banner");
        if (path.contains("_wall_skull"))        return path.replace("_wall_skull", "_skull");
        if (path.contains("_wall_head"))         return path.replace("_wall_head", "_head");
        if (path.equals("kelp_plant"))           return "kelp";
        if (path.equals("twisting_vines_plant")) return "twisting_vines";
        if (path.equals("weeping_vines_plant"))  return "weeping_vines";
        if (path.equals("carrots"))              return "carrot";
        if (path.equals("potatoes"))             return "potato";
        if (path.equals("beetroots"))            return "beetroot";
        if (path.equals("sweet_berry_bush"))     return "sweet_berries";
        if (path.equals("cave_vines_plant"))     return "glow_berries";
        if (path.equals("cocoa"))                return "cocoa_beans";
        if (path.equals("clay"))                 return "clay_ball";
        if (path.equals("glowstone"))            return "glowstone_dust";
        if (path.equals("sea_lantern"))          return "prismarine_crystals";
        if (path.equals("amethyst_cluster") || path.equals("large_amethyst_bud")) return "amethyst_shard";
        if (path.equals("melon"))                return "melon_slice";
        if (path.equals("bamboo_sapling"))       return "bamboo";
        if (path.equals("chorus_plant"))         return "chorus_fruit";
        if (path.equals("torchflower_crop"))     return "torchflower";
        if (path.equals("pitcher_crop"))         return "pitcher_plant";
        return path;
    }

    private static synchronized void ensurePermutation(long worldSeed) {
        if (worldSeed == cachedSeed) return;
        List<Item> perm = new ArrayList<>(SOURCE_POOL);
        Collections.shuffle(perm, new Random(worldSeed));
        cachedPermutation = Collections.unmodifiableList(perm);
        Map<Item, Item> inv = new HashMap<>();
        for (int i = 0; i < SOURCE_POOL.size(); i++) {
            inv.put(perm.get(i), SOURCE_POOL.get(i));
        }
        cachedInverse = Collections.unmodifiableMap(inv);
        cachedSeed = worldSeed;
    }

    private static List<Item> permutation(long worldSeed) {
        ensurePermutation(worldSeed);
        return cachedPermutation;
    }
}
