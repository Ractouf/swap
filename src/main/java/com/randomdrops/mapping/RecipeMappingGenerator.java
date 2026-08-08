package com.randomdrops.mapping;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.*;

/**
 * Derives a randomised output item for a given recipe/smelt/brew output, using
 * a different seed mix from DropMappingGenerator so the two mappings are independent.
 * Uses its own pool spanning the full item registry (minus DropMappingGenerator's
 * EXCLUDED set) rather than DropMappingGenerator's loot-drop-only SOURCE_POOL, so
 * tools, armor, and other craft-only items remain reachable as recipe outputs.
 */
public final class RecipeMappingGenerator {

    private static List<Item> ITEM_POOL = null;

    private RecipeMappingGenerator() {}

    /** Call once from ModInitializer after registries are frozen. */
    public static void init() {
        List<Item> pool = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!DropMappingGenerator.EXCLUDED.contains(item)) {
                pool.add(item);
            }
        }
        ITEM_POOL = Collections.unmodifiableList(pool);
    }

    public static Item getItem(long worldSeed, Identifier recipeOutputId) {
        if (ITEM_POOL == null) throw new IllegalStateException("RecipeMappingGenerator.init() not called");
        List<Item> pool = new ArrayList<>(ITEM_POOL);
        Collections.shuffle(pool, new Random(deriveSeed(worldSeed, recipeOutputId)));
        return pool.get(0);
    }

    private static long deriveSeed(long worldSeed, Identifier id) {
        long h = worldSeed ^ 0xC0FFEE_DEADBEEFL;
        h = h * 6364136223846793005L + id.getNamespace().hashCode();
        h = h * 6364136223846793005L + id.getPath().hashCode();
        return h;
    }
}
