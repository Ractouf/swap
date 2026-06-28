package com.randomdrops.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class DiscoveryScreen extends Screen {

    // ── visual constants ───────────────────────────────────────────────────
    private static final int NODE_SIZE = 24;
    private static final int ICON_SIZE = 16;
    private static final int COL_GAP   = 50;
    private static final int ROW_STEP  = 30;
    private static final int COMP_GAP  = 16;
    private static final int TAB_Y     = 4;
    private static final int TAB_H     = 26;
    private static final int TAB_W     = 100;
    private static final int SEARCH_Y  = TAB_Y + TAB_H + 4;
    private static final int SEARCH_H  = 14;
    private static final int SEARCH_W  = 180;
    private static final int HEADER_H  = SEARCH_Y + SEARCH_H + 6;

    // Missing panel layout
    private static final int MROW_H    = 18;
    private static final int MHDR_H    = 14;
    private static final int MINDENT   = 24;

    private static final int C_BG           = 0xFF0d0d1a;
    private static final int C_NODE         = 0xFF1a1a30;
    private static final int C_NODE_H       = 0xFF2a2a50;
    private static final int C_NODE_DIM     = 0xFF0e0e1a;
    private static final int C_BORDER       = 0xFF404080;
    private static final int C_BORDER_H     = 0xFF8080ff;
    private static final int C_BORDER_DIM   = 0xFF202038;
    private static final int C_EDGE         = 0xFF303550;
    private static final int C_ARROW        = 0xFF404060;
    private static final int C_EDGE_OUT     = 0xFF80c0ff;
    private static final int C_EDGE_IN      = 0xFFffaa60;
    private static final int C_TAB_ON       = 0xFF2d2d58;
    private static final int C_TAB_OFF      = 0xFF1a1a30;
    private static final int C_DIVIDER      = 0xFF404080;
    private static final int C_SEARCH_BG    = 0xFF111128;
    private static final int C_SEARCH_BORDER= 0xFF505090;
    private static final int C_SEARCH_FOCUS = 0xFF7070c0;
    private static final int C_GROUP_HDR    = 0xFF1a1a35;

    private static final Set<String> SKIP_TABS = Set.of("search", "op_blocks", "inventory", "hotbar");

    private static final List<String> TAB_PRIORITY = List.of(
        "building_blocks", "colored_blocks", "natural_blocks", "functional_blocks",
        "redstone_blocks", "tools_and_utilities", "combat", "food_and_drinks",
        "ingredients", "spawn_eggs"
    );

    // ── special entity sources ─────────────────────────────────────────────
    private static final Map<String, Item>   SPECIAL_ICONS = new HashMap<>();
    private static final Map<String, String> SPECIAL_NAMES = new HashMap<>();
    static {
        SPECIAL_ICONS.put("minecraft:fishing_bobber", Items.FISHING_ROD);
        SPECIAL_NAMES.put("minecraft:fishing_bobber", "Fishing");
    }

    // ── tabs ───────────────────────────────────────────────────────────────
    enum Tab {
        DROPS  ("Drops",      Items.GRASS_BLOCK),
        CHESTS ("Chest Loot", Items.CHEST),
        MISSING("Missing",    Items.SPYGLASS);
        final String label; final Item icon;
        Tab(String l, Item i) { label = l; icon = i; }
    }

    private Tab currentTab = Tab.DROPS;

    // ── raw data ───────────────────────────────────────────────────────────
    private final Map<Identifier, Set<Identifier>> drops      = new LinkedHashMap<>();
    private final Map<Identifier, String>          nodeType   = new LinkedHashMap<>();
    private final Map<Identifier, Identifier>      chestSwaps = new LinkedHashMap<>();
    /** All possible source IDs from the server ("block|id" or "mob|id"). */
    private final List<String> allSources = new ArrayList<>();

    // ── layout ────────────────────────────────────────────────────────────
    private final Map<Identifier, float[]>   nodePos        = new LinkedHashMap<>();
    private Map<Identifier, Set<Identifier>> activeEdges    = Collections.emptyMap();
    private Map<Identifier, Set<Identifier>> activeIncoming = Collections.emptyMap();

    // ── missing panel state ────────────────────────────────────────────────
    private record MissingEntry(String type, Identifier id) {}
    private record MissingGroup(String tabName, List<MissingEntry> items) {}
    private List<MissingGroup> missingGroups = null;
    private int missingScrollY = 0;
    private final Set<Integer> collapsedGroups = new HashSet<>();
    /** Persists collapsed/expanded state across screen instances. Null = not yet initialised. */
    private static Set<Integer> persistedCollapsedGroups = null;

    // ── state ─────────────────────────────────────────────────────────────
    private final boolean missingTabUnlocked;
    private int searchCountDrops   = 0;
    private int searchCountChests  = 0;
    private int searchCountMissing = 0;
    private Identifier hovNode     = null;
    private float      panX = 0, panY = 0;
    private String           searchText    = "";
    private boolean          searchFocused = false;
    private int              searchX;
    private List<Identifier> searchMatches = new ArrayList<>();
    private int              matchIndex    = 0;
    private boolean          initialPanDone = false;
    private Set<Identifier>  visibleCache   = null;
    private String           visibleCacheKey = null;

    // ── constructor ───────────────────────────────────────────────────────
    public DiscoveryScreen(List<String> entries) {
        super(Component.literal("Discoveries"));
        boolean unlocked = false;
        for (String entry : entries) {
            String[] p = entry.split("\\|", 3);
            if (p.length < 2) continue;
            switch (p[0]) {
                case "block", "mob" -> {
                    if (p.length < 3) break;
                    Identifier src = Identifier.tryParse(p[1]);
                    Identifier dst = Identifier.tryParse(p[2]);
                    if (src == null || dst == null) break;
                    drops.computeIfAbsent(src, k -> new LinkedHashSet<>()).add(dst);
                    nodeType.put(src, p[0]);
                }
                case "chest" -> {
                    if (p.length < 3) break;
                    Identifier src = Identifier.tryParse(p[1]);
                    Identifier dst = Identifier.tryParse(p[2]);
                    if (src != null && dst != null) chestSwaps.put(src, dst);
                }
                case "src" -> {
                    // "src|block|minecraft:oak_button" or "src|mob|minecraft:cow"
                    if (p.length < 3) break;
                    allSources.add(p[1] + "|" + p[2]);
                }
                case "flag" -> { if ("missing_tab_unlocked".equals(p[1])) unlocked = true; }
            }
        }
        this.missingTabUnlocked = unlocked;
        // Synthesize spawn-egg → mob chains
        for (Identifier mobId : new ArrayList<>(nodeType.keySet())) {
            if (!"mob".equals(nodeType.get(mobId))) continue;
            Identifier eggId = Identifier.fromNamespaceAndPath(
                mobId.getNamespace(), mobId.getPath() + "_spawn_egg");
            boolean eggIsTarget = drops.values().stream().anyMatch(s -> s.contains(eggId));
            if (!eggIsTarget) continue;
            Set<Identifier> mobDrops = drops.get(mobId);
            if (mobDrops == null || mobDrops.isEmpty()) continue;
            drops.computeIfAbsent(eggId, k -> new LinkedHashSet<>()).addAll(mobDrops);
            drops.remove(mobId);
            nodeType.remove(mobId);
        }
    }

    @Override
    protected void init() {
        searchX = width / 2 - SEARCH_W / 2;
        rebuildLayout();
        if (!initialPanDone && !nodePos.isEmpty()) {
            initialPanDone = true;
            boolean pannedToItem = false;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                ItemStack held = mc.player.getMainHandItem();
                if (!held.isEmpty()) {
                    Identifier heldId = BuiltInRegistries.ITEM.getKey(held.getItem());
                    if (heldId != null && nodePos.containsKey(heldId)) {
                        panToNode(heldId);
                        pannedToItem = true;
                    }
                }
            }
            if (!pannedToItem) {
                panX = 0;
                panY = NODE_SIZE / 2f;
            }
        }
    }

    // ── layout ────────────────────────────────────────────────────────────

    private void rebuildLayout() {
        nodePos.clear();
        visibleCache = null; visibleCacheKey = null;
        if (currentTab == Tab.DROPS) rebuildDropLayout();
        else if (currentTab == Tab.CHESTS) rebuildChestLayout();
        // MISSING tab has no node-graph layout
    }

    private void updateSearchMatches() {
        searchMatches.clear();
        matchIndex = 0;
        missingScrollY = 0;
        searchCountDrops = searchCountChests = searchCountMissing = 0;
        if (searchText.isEmpty()) return;
        String q = searchText.toLowerCase();
        for (Identifier id : nodePos.keySet())
            if (id.toString().toLowerCase().contains(q)) searchMatches.add(id);
        if (!searchMatches.isEmpty()) panToNode(searchMatches.get(0));
        // Cross-tab badge counts
        searchCountDrops  = (int) drops.keySet().stream()
            .filter(id -> id.toString().toLowerCase().contains(q)).count();
        searchCountChests = (int) chestSwaps.keySet().stream()
            .filter(id -> id.toString().toLowerCase().contains(q)).count();
        if (missingTabUnlocked) {
            if (missingGroups == null) buildMissingGroups();
            searchCountMissing = (int) missingGroups.stream()
                .flatMap(g -> g.items().stream())
                .filter(this::matchesMissingSearch).count();
        }
    }

    private void panToNode(Identifier id) {
        float[] pos = nodePos.get(id);
        if (pos == null) return;
        panX = -pos[0];
        panY = (height - HEADER_H) / 2f - pos[1];
    }

    private void rebuildDropLayout() {
        if (drops.isEmpty()) {
            activeEdges = drops; activeIncoming = Collections.emptyMap(); return;
        }
        activeEdges = drops;

        Set<Identifier> all = new LinkedHashSet<>();
        drops.keySet().forEach(all::add);
        drops.values().forEach(all::addAll);

        Map<Identifier, Set<Identifier>> inc = new LinkedHashMap<>();
        for (var e : drops.entrySet())
            for (Identifier dst : e.getValue())
                inc.computeIfAbsent(dst, k -> new LinkedHashSet<>()).add(e.getKey());
        activeIncoming = inc;

        List<Set<Identifier>> components = connectedComponents(all, drops);
        components.sort((a, b) -> b.size() - a.size());

        int yOffset = 0;
        for (Set<Identifier> comp : components) {
            int h = layoutComponent(comp, drops, yOffset);
            yOffset += h + COMP_GAP;
        }
    }

    private int layoutComponent(Set<Identifier> comp,
                                Map<Identifier, Set<Identifier>> edges, int yOffset) {
        Map<Identifier, Integer> inDeg = new HashMap<>();
        for (Identifier n : comp) inDeg.put(n, 0);
        for (Identifier src : comp) {
            Set<Identifier> ch = edges.get(src);
            if (ch != null) for (Identifier c : ch)
                if (comp.contains(c)) inDeg.merge(c, 1, Integer::sum);
        }
        Queue<Identifier> queue = new ArrayDeque<>();
        for (Identifier n : comp) if (inDeg.getOrDefault(n, 0) == 0) queue.add(n);
        List<Identifier> topo = new ArrayList<>();
        Set<Identifier> seen = new HashSet<>();
        while (!queue.isEmpty()) {
            Identifier n = queue.poll(); topo.add(n); seen.add(n);
            Set<Identifier> ch = edges.get(n);
            if (ch != null) for (Identifier c : ch)
                if (comp.contains(c) && inDeg.merge(c, -1, Integer::sum) == 0) queue.add(c);
        }
        for (Identifier n : comp) if (!seen.contains(n)) topo.add(n);

        Map<Identifier, Integer> depth = new HashMap<>();
        for (Identifier n : comp) depth.put(n, 0);
        for (Identifier n : topo) {
            int d = depth.get(n);
            Set<Identifier> ch = edges.get(n);
            if (ch != null) for (Identifier c : ch)
                if (comp.contains(c)) depth.merge(c, d + 1, Math::max);
        }

        int maxDepth = depth.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<List<Identifier>> cols = new ArrayList<>();
        for (int i = 0; i <= maxDepth; i++) cols.add(new ArrayList<>());
        for (Identifier n : comp) cols.get(depth.get(n)).add(n);

        cols.get(maxDepth).sort(Comparator.comparing(Identifier::toString));
        placeColumn(cols.get(maxDepth), maxDepth, maxDepth, yOffset);
        for (int c = maxDepth - 1; c >= 0; c--) {
            cols.get(c).sort((a, b) -> {
                float ya = baryOf(edges.get(a), comp);
                float yb = baryOf(edges.get(b), comp);
                return ya != yb ? Float.compare(ya, yb) : a.toString().compareTo(b.toString());
            });
            placeColumn(cols.get(c), c, maxDepth, yOffset);
        }

        int maxRows = cols.stream().mapToInt(List::size).max().orElse(0);
        return Math.max(0, maxRows - 1) * ROW_STEP + NODE_SIZE;
    }

    private void placeColumn(List<Identifier> col, int colIdx, int maxDepth, int yOffset) {
        if (col.isEmpty()) return;
        float xOff = (colIdx - maxDepth / 2f) * COL_GAP;
        for (int i = 0; i < col.size(); i++)
            nodePos.put(col.get(i), new float[]{xOff, yOffset + i * ROW_STEP});
    }

    private float baryOf(Set<Identifier> targets, Set<Identifier> comp) {
        if (targets == null) return 0;
        float s = 0; int n = 0;
        for (Identifier t : targets) {
            if (!comp.contains(t)) continue;
            float[] p = nodePos.get(t);
            if (p != null) { s += p[1]; n++; }
        }
        return n > 0 ? s / n : 0;
    }

    private List<Set<Identifier>> connectedComponents(Set<Identifier> all,
                                                       Map<Identifier, Set<Identifier>> edges) {
        Map<Identifier, Set<Identifier>> undirected = new HashMap<>();
        for (var e : edges.entrySet()) {
            undirected.computeIfAbsent(e.getKey(), k -> new HashSet<>()).addAll(e.getValue());
            for (Identifier dst : e.getValue())
                undirected.computeIfAbsent(dst, k -> new HashSet<>()).add(e.getKey());
        }
        Set<Identifier> visited = new HashSet<>();
        List<Set<Identifier>> result = new ArrayList<>();
        for (Identifier start : all) {
            if (visited.contains(start)) continue;
            Set<Identifier> comp = new LinkedHashSet<>();
            Queue<Identifier> bfs = new ArrayDeque<>();
            bfs.add(start); visited.add(start);
            while (!bfs.isEmpty()) {
                Identifier cur = bfs.poll(); comp.add(cur);
                for (Identifier nb : undirected.getOrDefault(cur, Collections.emptySet()))
                    if (visited.add(nb)) bfs.add(nb);
            }
            result.add(comp);
        }
        return result;
    }

    private void rebuildChestLayout() {
        if (chestSwaps.isEmpty()) {
            activeEdges = Collections.emptyMap(); activeIncoming = Collections.emptyMap(); return;
        }
        Map<Identifier, Set<Identifier>> edges = new LinkedHashMap<>();
        Map<Identifier, Set<Identifier>> inc   = new LinkedHashMap<>();
        chestSwaps.forEach((src, dst) -> {
            edges.computeIfAbsent(src, k -> new LinkedHashSet<>()).add(dst);
            inc.computeIfAbsent(dst, k -> new LinkedHashSet<>()).add(src);
        });
        activeEdges = edges; activeIncoming = inc;

        List<Identifier> targets = new ArrayList<>(chestSwaps.values());
        targets.sort(Comparator.comparing(Identifier::toString));
        for (int i = 0; i < targets.size(); i++)
            nodePos.put(targets.get(i), new float[]{COL_GAP / 2f, i * ROW_STEP});

        List<Identifier> sources = new ArrayList<>(chestSwaps.keySet());
        sources.sort((a, b) -> {
            float ya = baryChest(edges.get(a)); float yb = baryChest(edges.get(b));
            return ya != yb ? Float.compare(ya, yb) : a.toString().compareTo(b.toString());
        });
        for (int i = 0; i < sources.size(); i++)
            nodePos.put(sources.get(i), new float[]{-COL_GAP / 2f, i * ROW_STEP});
    }

    private float baryChest(Set<Identifier> targets) {
        if (targets == null) return 0;
        float s = 0; int n = 0;
        for (Identifier t : targets) {
            float[] p = nodePos.get(t); if (p != null) { s += p[1]; n++; }
        }
        return n > 0 ? s / n : 0;
    }

    // ── rendering ─────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, C_BG);

        if (currentTab == Tab.MISSING) {
            renderMissingPanel(g, mx, my);
        } else {
            renderGraph(g, mx, my);
        }

        g.fill(0, 0, width, SEARCH_Y, C_BG);
        drawTabBar(g, mx, my);
        drawSearchBar(g);
    }

    private void renderGraph(GuiGraphicsExtractor g, int mx, int my) {
        int originX = width / 2 + (int) panX;
        int originY = HEADER_H + (int) panY;

        hovNode = null;
        if (my >= HEADER_H) {
            int cmx = mx - originX, cmy = my - originY;
            int half = NODE_SIZE / 2;
            int gLeft = -originX - half, gRight = width - originX + half;
            int gTop  = HEADER_H - originY - half, gBottom = height - originY + half;
            for (var e : nodePos.entrySet()) {
                float[] p = e.getValue();
                if (p[0] < gLeft || p[0] > gRight || p[1] < gTop || p[1] > gBottom) continue;
                if (Math.abs(cmx - p[0]) < half && Math.abs(cmy - p[1]) < half) {
                    hovNode = e.getKey(); break;
                }
            }
        }

        Set<Identifier> visible = computeVisible();

        // Normal edges (dimmed pass)
        for (var e : activeEdges.entrySet()) {
            float[] from = nodePos.get(e.getKey());
            if (from == null) continue;
            int fx = originX + (int)from[0], fy = originY + (int)from[1];
            for (Identifier tgt : e.getValue()) {
                boolean isOut = e.getKey().equals(hovNode);
                boolean isIn  = tgt.equals(hovNode);
                if (isOut || isIn) continue;
                if (!visible.contains(e.getKey()) || !visible.contains(tgt)) continue;
                float[] to = nodePos.get(tgt);
                if (to == null) continue;
                int tx = originX + (int)to[0], ty = originY + (int)to[1];
                if (Math.max(fx, tx) < 0 || Math.min(fx, tx) > width)  continue;
                if (Math.max(fy, ty) < HEADER_H || Math.min(fy, ty) > height) continue;
                drawEdge(g, fx, fy, tx, ty, C_EDGE, C_ARROW);
            }
        }

        // Highlighted edges
        if (hovNode != null) {
            float[] hFrom = nodePos.get(hovNode);
            if (hFrom != null) {
                for (Identifier tgt : activeEdges.getOrDefault(hovNode, Collections.emptySet())) {
                    float[] to = nodePos.get(tgt);
                    if (to != null)
                        drawEdge(g, originX + (int)hFrom[0], originY + (int)hFrom[1],
                                    originX + (int)to[0],    originY + (int)to[1], C_EDGE_OUT, C_EDGE_OUT);
                }
            }
            float[] hTo = nodePos.get(hovNode);
            for (Identifier src : activeIncoming.getOrDefault(hovNode, Collections.emptySet())) {
                float[] from = nodePos.get(src);
                if (from != null && hTo != null)
                    drawEdge(g, originX + (int)from[0], originY + (int)from[1],
                                originX + (int)hTo[0],  originY + (int)hTo[1], C_EDGE_IN, C_EDGE_IN);
            }
        }

        // Nodes
        int half = NODE_SIZE / 2;
        for (var e : nodePos.entrySet()) {
            Identifier id = e.getKey();
            int sx = originX + (int)e.getValue()[0];
            int sy = originY + (int)e.getValue()[1];
            if (sx + half < 0 || sx - half > width)   continue;
            if (sy + half < HEADER_H || sy - half > height) continue;
            boolean vis = visible.contains(id);
            boolean hov = id.equals(hovNode);
            drawNode(g, iconFor(id), sx, sy, hov, vis);
        }

        if (hovNode != null) drawTooltip(g, mx, my);

        if (nodePos.isEmpty()) {
            g.centeredText(font,
                Component.literal("No discoveries yet, pick up randomised drops to see them here"),
                width / 2, height / 2, 0xFF777799);
        }
    }

    // ── Missing panel ──────────────────────────────────────────────────────

    private void renderMissingPanel(GuiGraphicsExtractor g, int mx, int my) {
        if (missingGroups == null) buildMissingGroups();

        int y = HEADER_H - missingScrollY;
        int totalH = 0;
        boolean anyVisible = false;

        if (missingGroups.isEmpty()) {
            g.centeredText(font, Component.literal("All sources discovered!"),
                width / 2, height / 2, 0xFF77FF77);
            return;
        }

        for (int gi = 0; gi < missingGroups.size(); gi++) {
            MissingGroup group = missingGroups.get(gi);
            List<MissingEntry> filtered = group.items().stream()
                .filter(this::matchesMissingSearch).toList();
            if (filtered.isEmpty()) continue;
            anyVisible = true;
            boolean collapsed = collapsedGroups.contains(gi);

            // Group header
            if (y + MHDR_H > HEADER_H && y < height) {
                g.fill(0, Math.max(y, HEADER_H), width, Math.min(y + MHDR_H, height), C_GROUP_HDR);
                String arrow = collapsed ? "▶" : "▼";
                String label = arrow + " " + group.tabName() + " (" + filtered.size() + " missing)";
                int textY = y + (MHDR_H - 7) / 2;
                if (textY >= HEADER_H)
                    g.text(font, Component.literal(label), 6, textY, 0xFFCCCCCC);
            }
            y += MHDR_H;
            totalH += MHDR_H;

            if (!collapsed) {
                for (MissingEntry entry : filtered) {
                    if (y + MROW_H > HEADER_H && y < height) {
                        boolean hovered = my >= Math.max(y, HEADER_H) && my < y + MROW_H;
                        int iconY = y + (MROW_H - ICON_SIZE) / 2;
                        Item item = itemForMissing(entry.type(), entry.id());
                        if (iconY >= HEADER_H - ICON_SIZE)
                            g.item(new ItemStack(item), MINDENT, iconY);
                        int textY = y + (MROW_H - 7) / 2;
                        int textColor = hovered ? 0xFFFFFFFF : 0xFF999999;
                        if (textY >= HEADER_H)
                            g.text(font, Component.literal(new ItemStack(item).getHoverName().getString()),
                                MINDENT + ICON_SIZE + 4, textY, textColor);
                        if (hovered) {
                            g.setTooltipForNextFrame(font,
                                List.of(Component.literal(entry.id().toString())
                                    .withStyle(s -> s.withColor(0x888888))),
                                Optional.empty(), mx, my);
                        }
                    }
                    y += MROW_H;
                    totalH += MROW_H;
                }
            }
        }

        if (!anyVisible) {
            g.centeredText(font, Component.literal("No results for \"" + searchText + "\""),
                width / 2, height / 2, 0xFF777799);
        }

        // Clamp scroll to content
        int maxScroll = Math.max(0, totalH - (height - HEADER_H));
        if (missingScrollY > maxScroll) missingScrollY = maxScroll;
    }

    /** Expands groups that have search matches; restores persisted state when search is cleared. */
    private void updateMissingCollapse() {
        if (missingGroups == null) return;
        if (searchText.isEmpty()) {
            collapsedGroups.clear();
            if (persistedCollapsedGroups != null) collapsedGroups.addAll(persistedCollapsedGroups);
            return;
        }
        for (int gi = 0; gi < missingGroups.size(); gi++) {
            boolean hasMatch = missingGroups.get(gi).items().stream().anyMatch(this::matchesMissingSearch);
            if (hasMatch) collapsedGroups.remove(gi);
        }
    }

    private boolean matchesMissingSearch(MissingEntry entry) {
        if (searchText.isEmpty()) return true;
        String q = searchText.toLowerCase();
        if (entry.id().toString().toLowerCase().contains(q)) return true;
        Item item = itemForMissing(entry.type(), entry.id());
        return new ItemStack(item).getHoverName().getString().toLowerCase().contains(q);
    }

    private void buildMissingGroups() {
        Set<String> discoveredSrcs = new HashSet<>();
        for (var entry : nodeType.entrySet())
            discoveredSrcs.add(entry.getValue() + "|" + entry.getKey());

        // "minecraft:chest" → "block", "minecraft:cow" → "mob"
        Map<String, String> sourceTypeMap = new HashMap<>();
        for (String src : allSources) {
            String[] p = src.split("\\|", 2);
            if (p.length == 2) sourceTypeMap.put(p[1], p[0]);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { missingGroups = List.of(); return; }
        FeatureFlagSet features = mc.level.enabledFeatures();
        HolderLookup.Provider holders = mc.level.registryAccess();

        Map<String, List<MissingEntry>> byTab = new LinkedHashMap<>();
        Set<String> matchedSrcs = new HashSet<>();

        // Build path→tab map, then produce a priority-ordered list so that redstone_blocks
        // is processed before combat (ensures TNT stays in the redstone group).
        Map<String, CreativeModeTab> tabByPath = new LinkedHashMap<>();
        for (var e : BuiltInRegistries.CREATIVE_MODE_TAB.entrySet()) {
            String p = e.getKey().identifier().getPath();
            if (!SKIP_TABS.contains(p)) tabByPath.put(p, e.getValue());
        }
        List<Map.Entry<String, CreativeModeTab>> orderedTabs = new ArrayList<>();
        Set<String> added = new HashSet<>();
        for (String p : TAB_PRIORITY) {
            CreativeModeTab t = tabByPath.get(p);
            if (t != null) { orderedTabs.add(Map.entry(p, t)); added.add(p); }
        }
        for (var e : tabByPath.entrySet()) {
            if (!added.contains(e.getKey())) orderedTabs.add(e);
        }

        for (var tabPair : orderedTabs) {
            String tabPath = tabPair.getKey();
            CreativeModeTab tab = tabPair.getValue();
            try {
                tab.buildContents(new CreativeModeTab.ItemDisplayParameters(features, false, holders));
            } catch (Exception ignored) { continue; }

            String tabName = tab.getDisplayName().getString();
            // Spawn egg → mob mapping only applies in the spawn_eggs tab
            boolean isSpawnEggsTab = tabPath.equals("spawn_eggs");
            List<MissingEntry> groupItems = new ArrayList<>();
            Set<Identifier> seenIds = new LinkedHashSet<>();

            for (ItemStack stack : tab.getDisplayItems()) {
                Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (itemId == null) continue;

                String type = sourceTypeMap.get(itemId.toString());
                Identifier sourceId = itemId;

                if (type == null && isSpawnEggsTab) {
                    // Spawn egg → mob source: "cow_spawn_egg" → "cow"
                    String path = itemId.getPath();
                    if (path.endsWith("_spawn_egg")) {
                        Identifier mobId = Identifier.fromNamespaceAndPath(itemId.getNamespace(),
                            path.substring(0, path.length() - "_spawn_egg".length()));
                        type = sourceTypeMap.get(mobId.toString());
                        if (type != null) sourceId = mobId;
                    }
                }

                if (type == null) continue;
                String srcKey = type + "|" + sourceId;
                if (discoveredSrcs.contains(srcKey)) continue;
                if (matchedSrcs.contains(srcKey)) continue;
                if (!seenIds.add(sourceId)) continue;

                groupItems.add(new MissingEntry(type, sourceId));
                matchedSrcs.add(srcKey);
            }

            // TNT was moved to the combat tab in MC 26.2 but belongs with redstone.
            // Force it into the redstone_blocks group if not already claimed elsewhere.
            if (tabPath.equals("redstone_blocks")) {
                String tntKey = "block|minecraft:tnt";
                if (!discoveredSrcs.contains(tntKey) && !matchedSrcs.contains(tntKey)
                        && sourceTypeMap.containsKey("minecraft:tnt")) {
                    groupItems.add(new MissingEntry("block", Identifier.fromNamespaceAndPath("minecraft", "tnt")));
                    matchedSrcs.add(tntKey);
                }
            }

            if (!groupItems.isEmpty()) byTab.put(tabName, groupItems);
        }
        // Items not in any creative tab (legacy blocks, unobtainable mobs) are intentionally omitted

        missingGroups = byTab.entrySet().stream()
            .map(e -> new MissingGroup(e.getKey(), e.getValue()))
            .toList();
        if (persistedCollapsedGroups == null) {
            persistedCollapsedGroups = new HashSet<>();
            for (int i = 0; i < missingGroups.size(); i++) persistedCollapsedGroups.add(i);
        }
        collapsedGroups.clear();
        collapsedGroups.addAll(persistedCollapsedGroups);
    }

    private Item itemForMissing(String type, Identifier id) {
        if ("mob".equals(type)) {
            Item special = SPECIAL_ICONS.get(id.toString());
            if (special != null) return special;
            Identifier egg = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_spawn_egg");
            Item e = BuiltInRegistries.ITEM.getValue(egg);
            if (e != null && e != Items.AIR) return e;
            Item direct = BuiltInRegistries.ITEM.getValue(id);
            return (direct != null && direct != Items.AIR) ? direct : Items.BONE;
        }
        Item it = BuiltInRegistries.ITEM.getValue(id);
        return (it == null || it == Items.AIR) ? Items.BARRIER : it;
    }

    private Set<Identifier> computeVisible() {
        if (searchText.isEmpty()) return nodePos.keySet();
        if (searchText.equals(visibleCacheKey) && visibleCache != null) return visibleCache;
        String q = searchText.toLowerCase();
        Set<Identifier> visible = new HashSet<>();
        Queue<Identifier> bfs = new ArrayDeque<>();
        for (Identifier id : nodePos.keySet())
            if (id.toString().toLowerCase().contains(q) && visible.add(id)) bfs.add(id);
        while (!bfs.isEmpty()) {
            Identifier cur = bfs.poll();
            for (Identifier nb : activeEdges.getOrDefault(cur, Collections.emptySet()))
                if (visible.add(nb)) bfs.add(nb);
            for (Identifier nb : activeIncoming.getOrDefault(cur, Collections.emptySet()))
                if (visible.add(nb)) bfs.add(nb);
        }
        visibleCacheKey = searchText;
        return visibleCache = visible;
    }

    private List<Tab> visibleTabs() {
        List<Tab> tabs = new ArrayList<>();
        for (Tab tab : Tab.values())
            if (tab != Tab.MISSING || missingTabUnlocked) tabs.add(tab);
        return tabs;
    }

    private void drawTabBar(GuiGraphicsExtractor g, int mx, int my) {
        List<Tab> tabs = visibleTabs();
        int x0 = width / 2 - tabs.size() * TAB_W / 2;
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            int tx = x0 + i * TAB_W;
            boolean active = tab == currentTab;
            boolean hov = mx >= tx && mx < tx + TAB_W && my >= TAB_Y && my < TAB_Y + TAB_H;
            int bg = active ? C_TAB_ON : (hov ? 0xFF202040 : C_TAB_OFF);
            int bd = active ? C_BORDER_H : C_BORDER;
            g.fill(tx, TAB_Y, tx + TAB_W, TAB_Y + TAB_H, bg);
            g.fill(tx,             TAB_Y,             tx + TAB_W,     TAB_Y + 1,      bd);
            g.fill(tx,             TAB_Y + TAB_H - 1, tx + TAB_W,     TAB_Y + TAB_H,  bd);
            g.fill(tx,             TAB_Y,             tx + 1,         TAB_Y + TAB_H,  bd);
            g.fill(tx + TAB_W - 1, TAB_Y,             tx + TAB_W,     TAB_Y + TAB_H,  bd);
            int count = searchText.isEmpty() ? 0 : switch (tab) {
                case DROPS   -> searchCountDrops;
                case CHESTS  -> searchCountChests;
                case MISSING -> searchCountMissing;
            };
            String label = count > 0 ? tab.label + " (" + count + ")" : tab.label;
            g.item(new ItemStack(tab.icon), tx + 4, TAB_Y + (TAB_H - ICON_SIZE) / 2);
            g.text(font, Component.literal(label),
                tx + 24, TAB_Y + (TAB_H - 8) / 2, active ? 0xFFFFFFFF : 0xFFAAAAAA);
        }
        g.fill(0, TAB_Y + TAB_H, width, TAB_Y + TAB_H + 1, C_DIVIDER);
    }

    private void drawSearchBar(GuiGraphicsExtractor g) {
        int bd = searchFocused ? C_SEARCH_FOCUS : C_SEARCH_BORDER;
        g.fill(searchX, SEARCH_Y, searchX + SEARCH_W, SEARCH_Y + SEARCH_H, C_SEARCH_BG);
        g.fill(searchX,              SEARCH_Y,              searchX + SEARCH_W, SEARCH_Y + 1,             bd);
        g.fill(searchX,              SEARCH_Y + SEARCH_H - 1, searchX + SEARCH_W, SEARCH_Y + SEARCH_H,   bd);
        g.fill(searchX,              SEARCH_Y,              searchX + 1,        SEARCH_Y + SEARCH_H,      bd);
        g.fill(searchX + SEARCH_W - 1, SEARCH_Y,           searchX + SEARCH_W, SEARCH_Y + SEARCH_H,      bd);
        String display = searchText.isEmpty() && !searchFocused ? "Search..." : searchText + (searchFocused ? "_" : "");
        int tc = searchText.isEmpty() && !searchFocused ? 0xFF555577 : 0xFFCCCCCC;
        g.text(font, Component.literal(display), searchX + 4, SEARCH_Y + (SEARCH_H - 7) / 2, tc);
        if (!searchMatches.isEmpty()) {
            String counter = (matchIndex + 1) + " / " + searchMatches.size();
            int cw = font.width(counter);
            g.text(font, Component.literal(counter),
                searchX + SEARCH_W - cw - 4, SEARCH_Y + (SEARCH_H - 7) / 2, 0xFF8888bb);
        }
    }

    // ── icons & names ─────────────────────────────────────────────────────

    private Item iconFor(Identifier id) {
        Item special = SPECIAL_ICONS.get(id.toString());
        if (special != null) return special;
        if (currentTab == Tab.CHESTS) return Items.CHEST;
        String type = nodeType.get(id);
        if ("mob".equals(type)) {
            Identifier egg = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_spawn_egg");
            Item e = BuiltInRegistries.ITEM.getValue(egg);
            if (e != null && e != Items.AIR) return e;
            Item direct = BuiltInRegistries.ITEM.getValue(id);
            return (direct != null && direct != Items.AIR) ? direct : Items.BONE;
        }
        return safeItem(id);
    }

    private String nodeDisplayName(Identifier id) {
        String special = SPECIAL_NAMES.get(id.toString());
        if (special != null) return special;
        return new ItemStack(iconFor(id)).getHoverName().getString();
    }

    private Item safeItem(Identifier id) {
        Item it = BuiltInRegistries.ITEM.getValue(id);
        return (it == null || it == Items.AIR) ? Items.BARRIER : it;
    }

    // ── tooltip ───────────────────────────────────────────────────────────

    private void drawTooltip(GuiGraphicsExtractor g, int mx, int my) {
        List<Component> lines = new ArrayList<>();
        if (currentTab == Tab.CHESTS) {
            lines.add(Component.literal(chestShort(hovNode)).withStyle(s -> s.withBold(true)));
            lines.add(Component.literal(hovNode.toString()).withStyle(s -> s.withColor(0x888888)));
            Set<Identifier> outs = activeEdges.get(hovNode);
            if (outs != null) outs.forEach(t ->
                lines.add(Component.literal("→ " + chestShort(t)).withStyle(s -> s.withColor(0xaaaaff))));
            Set<Identifier> ins = activeIncoming.get(hovNode);
            if (ins != null) ins.forEach(t ->
                lines.add(Component.literal("← " + chestShort(t)).withStyle(s -> s.withColor(0xffaa66))));
        } else {
            lines.add(Component.literal(nodeDisplayName(hovNode)).withStyle(s -> s.withBold(true)));
            lines.add(Component.literal(hovNode.toString()).withStyle(s -> s.withColor(0x888888)));
            Set<Identifier> outs = activeEdges.get(hovNode);
            if (outs != null) outs.forEach(tId ->
                lines.add(Component.literal("→ " + nodeDisplayName(tId)).withStyle(s -> s.withColor(0xaaaaff))));
            Set<Identifier> ins = activeIncoming.get(hovNode);
            if (ins != null) ins.forEach(srcId ->
                lines.add(Component.literal("← " + nodeDisplayName(srcId)).withStyle(s -> s.withColor(0xffaa66))));
        }
        float[] hPos = nodePos.get(hovNode);
        int nodeCenterX = width / 2 + (int) panX + (int) hPos[0];
        int tooltipX;
        if (mx >= nodeCenterX) {
            int maxLineW = lines.stream().mapToInt(font::width).max().orElse(0);
            tooltipX = Math.max(0, mx - maxLineW - 22);
        } else {
            tooltipX = mx;
        }
        g.setTooltipForNextFrame(font, lines, Optional.empty(), tooltipX, my);
    }

    private String chestShort(Identifier id) {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        return (slash >= 0 ? path.substring(slash + 1) : path).replace('_', ' ');
    }

    // ── draw helpers ──────────────────────────────────────────────────────

    private void drawEdge(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2,
                          int lineColor, int arrowColor) {
        int dx = x2 - x1, dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) return;
        int skip = NODE_SIZE / 2 + 2;
        for (int i = skip; i <= steps - skip; i++)
            g.fill(x1 + dx * i / steps, y1 + dy * i / steps,
                   x1 + dx * i / steps + 1, y1 + dy * i / steps + 1, lineColor);
        g.fill(x2 - 3, y2 - 3, x2 + 3, y2 + 3, arrowColor);
    }

    private void drawNode(GuiGraphicsExtractor g, Item item, int cx, int cy, boolean hov, boolean vis) {
        int h  = NODE_SIZE / 2;
        int bg = hov ? C_NODE_H : (vis ? C_NODE : C_NODE_DIM);
        int bd = hov ? C_BORDER_H : (vis ? C_BORDER : C_BORDER_DIM);
        g.fill(cx - h, cy - h, cx + h, cy + h, bg);
        g.fill(cx - h, cy - h,     cx + h, cy - h + 1, bd);
        g.fill(cx - h, cy + h - 1, cx + h, cy + h,     bd);
        g.fill(cx - h, cy - h,     cx - h + 1, cy + h, bd);
        g.fill(cx + h - 1, cy - h, cx + h,     cy + h, bd);
        if (vis || hov) g.item(new ItemStack(item), cx - ICON_SIZE / 2, cy - ICON_SIZE / 2);
    }

    // ── input ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean wasDragging) {
        int mx = (int) event.x(), my = (int) event.y();
        if (event.button() == 0) {
            // Tab bar
            List<Tab> tabs = visibleTabs();
            int x0 = width / 2 - tabs.size() * TAB_W / 2;
            for (int i = 0; i < tabs.size(); i++) {
                Tab tab = tabs.get(i);
                int tx = x0 + i * TAB_W;
                if (mx >= tx && mx < tx + TAB_W && my >= TAB_Y && my < TAB_Y + TAB_H) {
                    if (tab != currentTab) {
                        currentTab = tab; panX = panY = 0;
                        searchMatches.clear(); matchIndex = 0;
                        rebuildLayout();
                        if (!searchText.isEmpty()) {
                            updateSearchMatches();
                            if (currentTab == Tab.MISSING) updateMissingCollapse();
                        }
                    }
                    searchFocused = false; return true;
                }
            }

            if (currentTab == Tab.MISSING && missingGroups != null && my >= HEADER_H) {
                int y = HEADER_H - missingScrollY;
                for (int gi = 0; gi < missingGroups.size(); gi++) {
                    MissingGroup group = missingGroups.get(gi);
                    // Mirror the render filter exactly so y positions match
                    List<MissingEntry> filtered = group.items().stream()
                        .filter(this::matchesMissingSearch).toList();
                    if (filtered.isEmpty()) continue;
                    if (my >= y && my < y + MHDR_H) {
                        if (collapsedGroups.contains(gi)) collapsedGroups.remove(gi);
                        else collapsedGroups.add(gi);
                        if (persistedCollapsedGroups != null) {
                            persistedCollapsedGroups.clear();
                            persistedCollapsedGroups.addAll(collapsedGroups);
                        }
                        return true;
                    }
                    y += MHDR_H;
                    if (!collapsedGroups.contains(gi)) y += filtered.size() * MROW_H;
                }
                return true;
            }

            // Search box
            boolean inSearch = mx >= searchX && mx < searchX + SEARCH_W
                && my >= SEARCH_Y && my < SEARCH_Y + SEARCH_H;
            searchFocused = inSearch;
            if (inSearch) return true;
        }
        return super.mouseClicked(event, wasDragging);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (currentTab == Tab.MISSING) {
            missingScrollY = Math.max(0, missingScrollY - (int)(deltaY * 12));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int cp = event.codepoint();
        if (searchFocused && cp >= 32 && cp != 127) {
            searchText += Character.toString(cp);
            updateSearchMatches();
            if (currentTab == Tab.MISSING) updateMissingCollapse();
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (searchFocused) {
            int key = event.key();
            if (key == GLFW.GLFW_KEY_BACKSPACE && !searchText.isEmpty()) {
                searchText = searchText.substring(0, searchText.length() - 1);
                updateSearchMatches();
                if (currentTab == Tab.MISSING) updateMissingCollapse();
                return true;
            }
            if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)
                    && !searchMatches.isEmpty()) {
                matchIndex = (matchIndex + 1) % searchMatches.size();
                panToNode(searchMatches.get(matchIndex));
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (!searchText.isEmpty()) {
                    searchText = ""; searchMatches.clear(); matchIndex = 0; return true;
                }
                searchFocused = false;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (event.button() == 0 && currentTab != Tab.MISSING) {
            panX += (float) dx; panY += (float) dy; return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
