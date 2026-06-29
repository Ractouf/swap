package com.randomdrops.client.screen;

import com.randomdrops.client.BingoClientCache;
import com.randomdrops.client.BingoHudConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

public class BingoHudEditScreen extends Screen {

    private static final int GAP         = 2;
    private static final int HANDLE_SIZE = 10;
    private static final int BTN_W       = 80;
    private static final int BTN_H       = 16;

    private static final int C_EMPTY     = 0xCC1a1a30;
    private static final int C_COLLECTED = 0xCC1a3a1a;
    private static final int C_WIN_LINE  = 0xCC3a3000;
    private static final int C_EDIT_BD   = 0xFF00CCFF;

    private int hudX, hudY, cellSize;
    private final int origX, origY, origSize;

    private boolean draggingGrid = false;
    private boolean resizingGrid = false;
    private int     dragOffX, dragOffY;

    public BingoHudEditScreen() {
        super(Component.literal("Edit Bingo HUD"));
        origX    = BingoHudConfig.getX();
        origY    = BingoHudConfig.getY();
        origSize = BingoHudConfig.getCellSize();
        hudX     = origX;
        hudY     = origY;
        cellSize = origSize;
    }

    private int gridW() { return 5 * cellSize + 4 * GAP; }
    private int gridH() { return gridW(); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0x88000000);

        Set<Integer> winLines = BingoClientCache.completedLines();
        int gw = gridW(), gh = gridH();

        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                int idx = r * 5 + c;
                int x   = hudX + c * (cellSize + GAP);
                int y   = hudY + r * (cellSize + GAP);

                boolean collected = BingoClientCache.isCollected(idx);
                boolean winCell   = isOnWinLine(idx, winLines);
                int bg = winCell ? C_WIN_LINE : (collected ? C_COLLECTED : C_EMPTY);

                g.fill(x, y, x + cellSize, y + cellSize, bg);
                g.fill(x,              y,               x + cellSize, y + 1,          C_EDIT_BD);
                g.fill(x,              y + cellSize - 1, x + cellSize, y + cellSize,  C_EDIT_BD);
                g.fill(x,              y,               x + 1,         y + cellSize,  C_EDIT_BD);
                g.fill(x + cellSize-1, y,               x + cellSize, y + cellSize,  C_EDIT_BD);

                String cellId = BingoClientCache.getCell(idx);
                Identifier id = Identifier.tryParse(cellId);
                Item item = id != null ? BuiltInRegistries.ITEM.getValue(id) : null;
                if (item != null && item != Items.AIR && cellSize >= 18) {
                    g.item(new ItemStack(item), x + (cellSize - 16) / 2, y + (cellSize - 16) / 2);
                }
            }
        }

        // Resize handle — yellow square at bottom-right corner
        int hx = hudX + gw - HANDLE_SIZE;
        int hy = hudY + gh - HANDLE_SIZE;
        g.fill(hx, hy, hx + HANDLE_SIZE, hy + HANDLE_SIZE, 0xFFFFDD00);
        g.fill(hx + 2, hy + 2, hx + HANDLE_SIZE - 2, hy + HANDLE_SIZE - 2, 0xFF887700);

        // Instructions
        g.centeredText(font, Component.literal("Drag grid to move  •  Drag ◢ corner to resize"),
                g.guiWidth() / 2, 8, 0xFFCCCCFF);

        // Done / Cancel buttons
        int btnY  = g.guiHeight() - BTN_H - 8;
        int doneX = g.guiWidth() / 2 - BTN_W - 4;
        int cancX = g.guiWidth() / 2 + 4;
        boolean hovDone = mx >= doneX && mx < doneX + BTN_W && my >= btnY && my < btnY + BTN_H;
        boolean hovCanc = mx >= cancX && mx < cancX + BTN_W && my >= btnY && my < btnY + BTN_H;

        drawBtn(g, doneX, btnY, "Done",   0xFF223322, 0xFF334433, 0xFF80C080, 0xFF80FF80, hovDone);
        drawBtn(g, cancX, btnY, "Cancel", 0xFF332222, 0xFF443333, 0xFFC08080, 0xFFFF8080, hovCanc);
    }

    private void drawBtn(GuiGraphicsExtractor g, int x, int y, String label,
                         int bgNorm, int bgHov, int border, int textCol, boolean hov) {
        g.fill(x, y, x + BTN_W, y + BTN_H, hov ? bgHov : bgNorm);
        g.fill(x,           y,           x + BTN_W, y + 1,         border);
        g.fill(x,           y + BTN_H-1, x + BTN_W, y + BTN_H,    border);
        g.fill(x,           y,           x + 1,      y + BTN_H,    border);
        g.fill(x + BTN_W-1, y,           x + BTN_W, y + BTN_H,    border);
        g.centeredText(font, Component.literal(label), x + BTN_W / 2, y + (BTN_H - 8) / 2, textCol);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean wasDragging) {
        int mx = (int) event.x(), my = (int) event.y();
        if (event.button() == 0) {
            int gw = gridW(), gh = gridH();
            // Resize handle (check before body — it's inside the grid area)
            int hx = hudX + gw - HANDLE_SIZE;
            int hy = hudY + gh - HANDLE_SIZE;
            if (mx >= hx && mx < hx + HANDLE_SIZE && my >= hy && my < hy + HANDLE_SIZE) {
                resizingGrid = true;
                draggingGrid = false;
                return true;
            }
            // Grid body drag
            if (mx >= hudX && mx < hudX + gw && my >= hudY && my < hudY + gh) {
                draggingGrid = true;
                dragOffX = mx - hudX;
                dragOffY = my - hudY;
                return true;
            }
            // Buttons
            int btnY  = height - BTN_H - 8;
            int doneX = width / 2 - BTN_W - 4;
            int cancX = width / 2 + 4;
            if (mx >= doneX && mx < doneX + BTN_W && my >= btnY && my < btnY + BTN_H) {
                BingoHudConfig.setPosition(hudX, hudY);
                BingoHudConfig.setCellSize(cellSize);
                onClose();
                return true;
            }
            if (mx >= cancX && mx < cancX + BTN_W && my >= btnY && my < btnY + BTN_H) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(event, wasDragging);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingGrid) {
            hudX = Math.max(0, Math.min((int) event.x() - dragOffX, width  - gridW()));
            hudY = Math.max(0, Math.min((int) event.y() - dragOffY, height - gridH()));
            return true;
        }
        if (resizingGrid) {
            int newW = (int) event.x() - hudX;
            cellSize = Math.max(18, Math.min(40, (newW - 4 * GAP) / 5));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingGrid = false;
        resizingGrid = false;
        return super.mouseReleased(event);
    }

    private static boolean isOnWinLine(int idx, Set<Integer> lines) {
        int r = idx / 5, c = idx % 5;
        if (lines.contains(r))     return true;
        if (lines.contains(5 + c)) return true;
        if (lines.contains(10) && r == c)     return true;
        if (lines.contains(11) && r + c == 4) return true;
        return false;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
