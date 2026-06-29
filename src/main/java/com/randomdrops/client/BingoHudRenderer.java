package com.randomdrops.client;

import com.randomdrops.client.screen.BingoHudEditScreen;
import com.randomdrops.client.screen.BingoScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

@Environment(EnvType.CLIENT)
public final class BingoHudRenderer implements HudElement {

    private static final int GAP = 2;

    private static final int C_EMPTY     = 0xCC1a1a30;
    private static final int C_COLLECTED = 0xCC1a3a1a;
    private static final int C_WIN_LINE  = 0xCC3a3000;
    private static final int C_BORDER    = 0xCC404080;
    private static final int C_BORDER_C  = 0xCC40a040;
    private static final int C_BORDER_W  = 0xCCc0a000;

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker dt) {
        if (!BingoHudConfig.isVisible()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof BingoScreen || mc.gui.screen() instanceof BingoHudEditScreen) return;

        Set<Integer> winLines = BingoClientCache.completedLines();
        int x0 = BingoHudConfig.getX();
        int y0 = BingoHudConfig.getY();
        int cs = BingoHudConfig.getCellSize();

        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                int idx = r * 5 + c;
                int x = x0 + c * (cs + GAP);
                int y = y0 + r * (cs + GAP);

                boolean collected = BingoClientCache.isCollected(idx);
                boolean winCell   = isOnWinLine(idx, winLines);

                int bg = winCell ? C_WIN_LINE : (collected ? C_COLLECTED : C_EMPTY);
                int bd = winCell ? C_BORDER_W : (collected ? C_BORDER_C : C_BORDER);

                g.fill(x, y, x + cs, y + cs, bg);
                g.fill(x,          y,          x + cs,     y + 1,      bd);
                g.fill(x,          y + cs - 1, x + cs,     y + cs,     bd);
                g.fill(x,          y,          x + 1,       y + cs,     bd);
                g.fill(x + cs - 1, y,          x + cs,     y + cs,     bd);

                String cellId = BingoClientCache.getCell(idx);
                if (cellId.isEmpty()) continue;
                Identifier id = Identifier.tryParse(cellId);
                Item item = id != null ? BuiltInRegistries.ITEM.getValue(id) : null;
                if (item != null && item != Items.AIR && cs >= 18) {
                    int ix = x + (cs - 16) / 2;
                    int iy = y + (cs - 16) / 2;
                    g.item(new ItemStack(item), ix, iy);
                }
            }
        }
    }

    private static boolean isOnWinLine(int idx, Set<Integer> lines) {
        int r = idx / 5, c = idx % 5;
        if (lines.contains(r))     return true;
        if (lines.contains(5 + c)) return true;
        if (lines.contains(10) && r == c)     return true;
        if (lines.contains(11) && r + c == 4) return true;
        return false;
    }
}
