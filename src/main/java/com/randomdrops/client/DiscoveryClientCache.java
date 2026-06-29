package com.randomdrops.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;

import java.util.*;

@Environment(EnvType.CLIENT)
public final class DiscoveryClientCache {

    private static Map<Identifier, Set<Identifier>> discoveredDrops = new HashMap<>();
    private static Set<Identifier> allSourceIds = new HashSet<>();

    private DiscoveryClientCache() {}

    public static void update(List<String> entries) {
        Map<Identifier, Set<Identifier>> drops = new HashMap<>();
        Set<Identifier> sources = new HashSet<>();
        for (String entry : entries) {
            String[] p = entry.split("\\|", 3);
            if (p.length < 2) continue;
            switch (p[0]) {
                case "block", "mob" -> {
                    if (p.length < 3) break;
                    Identifier src = Identifier.tryParse(p[1]);
                    Identifier dst = Identifier.tryParse(p[2]);
                    if (src != null && dst != null)
                        drops.computeIfAbsent(src, k -> new LinkedHashSet<>()).add(dst);
                }
                case "src" -> {
                    if (p.length < 3) break;
                    Identifier id = Identifier.tryParse(p[2]);
                    if (id != null) sources.add(id);
                }
            }
        }
        discoveredDrops = drops;
        allSourceIds = sources;
    }

    public static boolean isKnownSource(Identifier id) { return allSourceIds.contains(id); }
    public static boolean isDiscovered(Identifier id)  { return discoveredDrops.containsKey(id); }
    public static Set<Identifier> getDrops(Identifier id) {
        return discoveredDrops.getOrDefault(id, Collections.emptySet());
    }
}
