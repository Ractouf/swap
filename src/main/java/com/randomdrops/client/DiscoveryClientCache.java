package com.randomdrops.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;

import java.util.*;

@Environment(EnvType.CLIENT)
public final class DiscoveryClientCache {

    private static Map<Identifier, Set<Identifier>> discoveredDrops  = new HashMap<>();
    private static Map<Identifier, Set<Identifier>> discoveredCrafts = new HashMap<>();
    private static Set<Identifier> allSourceIds      = new HashSet<>();
    private static Set<Identifier> allCraftSourceIds = new HashSet<>();

    private DiscoveryClientCache() {}

    public static void update(List<String> entries) {
        Map<Identifier, Set<Identifier>> drops  = new HashMap<>();
        Map<Identifier, Set<Identifier>> crafts = new HashMap<>();
        Set<Identifier> sources      = new HashSet<>();
        Set<Identifier> craftSources = new HashSet<>();

        for (String entry : entries) {
            String[] p = entry.split("\\|", 3);
            if (p.length < 2) continue;
            switch (p[0]) {
                case "block", "mob", "chest" -> {
                    if (p.length < 3) break;
                    Identifier src = Identifier.tryParse(p[1]);
                    Identifier dst = Identifier.tryParse(p[2]);
                    if (src != null && dst != null)
                        drops.computeIfAbsent(src, k -> new LinkedHashSet<>()).add(dst);
                }
                case "craft", "smelt", "brew" -> {
                    if (p.length < 3) break;
                    Identifier src = Identifier.tryParse(p[1]);
                    Identifier dst = Identifier.tryParse(p[2]);
                    if (src != null && dst != null)
                        crafts.computeIfAbsent(src, k -> new LinkedHashSet<>()).add(dst);
                }
                case "src" -> {
                    if (p.length < 3) break;
                    Identifier id = Identifier.tryParse(p[2]);
                    if (id != null) sources.add(id);
                }
                case "craft_src" -> {
                    if (p.length < 3) break;
                    Identifier id = Identifier.tryParse(p[2]);
                    if (id != null) craftSources.add(id);
                }
            }
        }
        discoveredDrops  = drops;
        discoveredCrafts = crafts;
        allSourceIds      = sources;
        allCraftSourceIds = craftSources;
    }

    // Drop sources
    public static boolean isKnownSource(Identifier id)    { return allSourceIds.contains(id); }
    public static boolean isDiscovered(Identifier id)     { return discoveredDrops.containsKey(id); }
    public static Set<Identifier> getDrops(Identifier id) { return discoveredDrops.getOrDefault(id, Collections.emptySet()); }

    // Craft/smelt/brew sources
    public static boolean isKnownCraftSource(Identifier id)    { return allCraftSourceIds.contains(id); }
    public static boolean isCraftDiscovered(Identifier id)     { return discoveredCrafts.containsKey(id); }
    public static Set<Identifier> getCraftDrops(Identifier id) { return discoveredCrafts.getOrDefault(id, Collections.emptySet()); }
    public static Map<Identifier, Set<Identifier>> getAllCraftDiscoveries() { return Collections.unmodifiableMap(discoveredCrafts); }
}
