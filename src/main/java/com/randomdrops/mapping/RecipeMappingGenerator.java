package com.randomdrops.mapping;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.*;

/**
 * Derives a randomised output item for a given recipe/smelt/brew output, using
 * a different seed mix from DropMappingGenerator so the two mappings are independent.
 */
public final class RecipeMappingGenerator {

    private RecipeMappingGenerator() {}

    public static Item getItem(long worldSeed, Identifier recipeOutputId) {
        List<Item> pool = new ArrayList<>(DropMappingGenerator.getItemPool());
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
