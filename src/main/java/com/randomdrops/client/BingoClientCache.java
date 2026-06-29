package com.randomdrops.client;

import com.randomdrops.mapping.BingoState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

@Environment(EnvType.CLIENT)
public final class BingoClientCache {

    private static final String[] grid      = new String[25];
    private static final boolean[] collected = new boolean[25];
    private static boolean won = false;

    private BingoClientCache() {}

    public static void update(List<String> gridIn, List<Boolean> collectedIn) {
        for (int i = 0; i < 25; i++) {
            grid[i]      = i < gridIn.size()      ? gridIn.get(i)      : "";
            collected[i] = i < collectedIn.size() && collectedIn.get(i);
        }
        Set<Integer> idx = new HashSet<>();
        for (int i = 0; i < 25; i++) if (collected[i]) idx.add(i);
        won = BingoState.checkWin(idx);
    }

    public static String  getCell(int i)      { String s = (i >= 0 && i < 25) ? grid[i] : null; return s != null ? s : ""; }
    public static boolean isCollected(int i)  { return i >= 0 && i < 25 && collected[i]; }
    public static boolean hasWon()            { return won; }

    /** Returns which lines are complete: rows 0-4, columns 5-9, diagonals 10-11. */
    public static Set<Integer> completedLines() {
        Set<Integer> lines = new HashSet<>();
        for (int r = 0; r < 5; r++) {
            boolean ok = true;
            for (int c = 0; c < 5; c++) if (!collected[r * 5 + c]) { ok = false; break; }
            if (ok) lines.add(r);
        }
        for (int c = 0; c < 5; c++) {
            boolean ok = true;
            for (int r = 0; r < 5; r++) if (!collected[r * 5 + c]) { ok = false; break; }
            if (ok) lines.add(5 + c);
        }
        boolean d1 = true, d2 = true;
        for (int i = 0; i < 5; i++) {
            if (!collected[i * 5 + i])       d1 = false;
            if (!collected[i * 5 + (4 - i)]) d2 = false;
        }
        if (d1) lines.add(10);
        if (d2) lines.add(11);
        return lines;
    }
}
