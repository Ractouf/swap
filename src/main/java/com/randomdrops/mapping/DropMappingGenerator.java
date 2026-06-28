package com.randomdrops.mapping;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.*;

public final class DropMappingGenerator {

    private static final Set<Item> EXCLUDED = Set.of(
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

    private static List<Item> ITEM_POOL = null;

    private DropMappingGenerator() {}

    /** Call once from ModInitializer after registries are frozen. */
    public static void init() {
        List<Item> pool = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!EXCLUDED.contains(item)) {
                pool.add(item);
            }
        }
        ITEM_POOL = Collections.unmodifiableList(pool);
    }

    /**
     * Returns the item that maps to a given (block, droppedItem) pair in this world.
     */
    public static Item getItem(long worldSeed, Identifier droppedItemId) {
        if (ITEM_POOL == null) throw new IllegalStateException("DropMappingGenerator.init() not called");
        long seed = deriveSeed(worldSeed, droppedItemId);
        List<Item> pool = new ArrayList<>(ITEM_POOL);
        Collections.shuffle(pool, new Random(seed));
        return pool.get(0);
    }

    public static List<Item> getItemPool() {
        if (ITEM_POOL == null) throw new IllegalStateException("DropMappingGenerator.init() not called");
        return ITEM_POOL;
    }

    // LCG mixing: namespace and path incorporated separately so "a:bc" ≠ "ab:c".
    private static long deriveSeed(long worldSeed, Identifier droppedItemId) {
        long h = worldSeed;
        h = h * 6364136223846793005L + droppedItemId.getNamespace().hashCode();
        h = h * 6364136223846793005L + droppedItemId.getPath().hashCode();
        return h;
    }
}
