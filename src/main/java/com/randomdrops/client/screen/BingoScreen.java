package com.randomdrops.client.screen;

import com.randomdrops.client.BingoClientCache;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class BingoScreen extends Screen {

    private static final int CELL    = 40;
    private static final int ICON    = 16;
    private static final int GAP     = 4;
    private static final int GRID_W  = 5 * CELL + 4 * GAP;
    private static final int GRID_H  = GRID_W;

    // Colours
    private static final int C_BG         = 0xFF0d0d1a;
    private static final int C_EMPTY      = 0xFF1a1a30;
    private static final int C_COLLECTED  = 0xFF1a3a1a;
    private static final int C_WIN_LINE   = 0xFF3a3000;
    private static final int C_BORDER     = 0xFF404080;
    private static final int C_BORDER_C   = 0xFF40a040;
    private static final int C_BORDER_W   = 0xFFc0a000;
    private static final int C_BINGO_TEXT = 0xFFFFD700;
    private static final int C_TITLE      = 0xFFCCCCFF;

    public BingoScreen() {
        super(Component.literal("Bingo"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, C_BG);

        int gridX = (width  - GRID_W) / 2;
        int gridY = (height - GRID_H) / 2;

        Set<Integer> winLines = BingoClientCache.completedLines();

        // Title
        g.centeredText(font, Component.literal("BINGO"), width / 2, gridY - 28, C_TITLE);

        // Progress
        int total = 0;
        for (int i = 0; i < 25; i++) if (BingoClientCache.isCollected(i)) total++;
        g.centeredText(font, Component.literal(total + " / 25"), width / 2, gridY - 16, 0xFF888899);

        // Draw cells
        Identifier hovCell = null;
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                int idx = r * 5 + c;
                int x = gridX + c * (CELL + GAP);
                int y = gridY + r * (CELL + GAP);

                boolean isCollected = BingoClientCache.isCollected(idx);
                boolean isWinCell   = isOnWinLine(idx, winLines);
                boolean hovered     = mx >= x && mx < x + CELL && my >= y && my < y + CELL;

                int bg = isWinCell ? C_WIN_LINE : (isCollected ? C_COLLECTED : C_EMPTY);
                int bd = isWinCell ? C_BORDER_W : (isCollected ? C_BORDER_C : C_BORDER);
                if (hovered) { bg = brighten(bg); bd = 0xFFFFFFFF; }

                g.fill(x, y, x + CELL, y + CELL, bg);
                g.fill(x,          y,          x + CELL,     y + 1,          bd);
                g.fill(x,          y + CELL - 1, x + CELL,   y + CELL,       bd);
                g.fill(x,          y,          x + 1,         y + CELL,       bd);
                g.fill(x + CELL - 1, y,        x + CELL,     y + CELL,       bd);

                String cellId = BingoClientCache.getCell(idx);
                Identifier id = Identifier.tryParse(cellId);
                Item item = id != null ? BuiltInRegistries.ITEM.getValue(id) : null;
                if (item != null && item != Items.AIR) {
                    int ix = x + (CELL - ICON) / 2;
                    int iy = y + (CELL - ICON) / 2;
                    g.item(new ItemStack(item), ix, iy);
                }

                if (hovered) hovCell = id;
            }
        }

        // Tooltip on hover
        if (hovCell != null) {
            Item item = BuiltInRegistries.ITEM.getValue(hovCell);
            if (item != null && item != Items.AIR) {
                List<Component> lines = new ArrayList<>();
                lines.add(new ItemStack(item).getHoverName());
                lines.add(Component.literal(hovCell.toString()).withStyle(s -> s.withColor(0x888888)));
                g.setTooltipForNextFrame(font, lines, Optional.empty(), mx, my);
            }
        }

        // "BINGO!" overlay when won
        if (BingoClientCache.hasWon()) {
            g.centeredText(font, Component.literal("BINGO!"), width / 2, gridY + GRID_H + 12, C_BINGO_TEXT);
        }
    }

    private static boolean isOnWinLine(int idx, Set<Integer> lines) {
        int r = idx / 5, c = idx % 5;
        if (lines.contains(r))     return true;
        if (lines.contains(5 + c)) return true;
        if (lines.contains(10) && r == c)       return true;
        if (lines.contains(11) && r + c == 4)   return true;
        return false;
    }

    private static int brighten(int color) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, ((color >> 16) & 0xFF) + 30);
        int g = Math.min(255, ((color >> 8)  & 0xFF) + 30);
        int b = Math.min(255, (color & 0xFF) + 30);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
