package com.randomdrops.mapping;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

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

    // Registry IDs of items dropped by mobs that have no block form.
    // Using string IDs avoids field-name differences across MC versions.
    private static final String[] MOB_DROP_IDS = {
        "rotten_flesh", "bone", "arrow", "string", "spider_eye",
        "gunpowder", "ender_pearl", "blaze_rod", "ghast_tear",
        "slime_ball", "magma_cream", "shulker_shell", "phantom_membrane",
        "prismarine_shard", "prismarine_crystals",
        "leather", "beef", "porkchop", "chicken", "mutton", "rabbit",
        "rabbit_foot", "rabbit_hide", "feather", "cod", "salmon",
        "tropical_fish", "pufferfish", "ink_sac", "glow_ink_sac",
        "copper_ingot", "gold_nugget", "gold_ingot", "iron_ingot",
        "nether_star", "totem_of_undying", "trident", "nautilus_shell",
        "wither_skeleton_skull", "emerald"
    };

    // Pool of items that are actual loot-table drops (blocks + mobs).
    // This is the universe for both sources and targets, guaranteeing every
    // item has exactly one source via the world-seed permutation.
    private static List<Item> SOURCE_POOL = null;
    private static Map<Item, Integer> SOURCE_INDEX = null;

    // Permutation cache — recomputed only when the world seed changes.
    private static long cachedSeed = Long.MIN_VALUE;
    private static List<Item> cachedPermutation = null;
    private static Map<Item, Item> cachedInverse = null;

    private DropMappingGenerator() {}

    /** Call once from ModInitializer after registries are frozen. */
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

        // Mob drops that have no block form
        for (String id : MOB_DROP_IDS) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", id));
            if (item != null && item != Items.AIR && !EXCLUDED.contains(item)) sources.add(item);
        }

        SOURCE_POOL = Collections.unmodifiableList(new ArrayList<>(sources));
        SOURCE_INDEX = new HashMap<>();
        for (int i = 0; i < SOURCE_POOL.size(); i++) {
            SOURCE_INDEX.put(SOURCE_POOL.get(i), i);
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
