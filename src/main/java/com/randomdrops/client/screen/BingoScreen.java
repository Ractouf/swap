package com.randomdrops.client.screen;

import com.randomdrops.client.BingoClientCache;
import com.randomdrops.client.BingoHudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
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
    private static final int BTN_W   = 90;
    private static final int BTN_H   = 14;

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
                g.fill(x,          y,            x + CELL,   y + 1,          bd);
                g.fill(x,          y + CELL - 1, x + CELL,   y + CELL,       bd);
                g.fill(x,          y,            x + 1,       y + CELL,       bd);
                g.fill(x + CELL-1, y,            x + CELL,   y + CELL,       bd);

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

        // Pin HUD / Edit Layout buttons
        boolean pinned = BingoHudConfig.isVisible();
        int btnY   = gridY + GRID_H + (BingoClientCache.hasWon() ? 26 : 12);
        int totalW = 2 * BTN_W + 8;
        int pinX   = (width - totalW) / 2;
        int editX  = pinX + BTN_W + 8;

        boolean hovPin  = mx >= pinX  && mx < pinX  + BTN_W && my >= btnY && my < btnY + BTN_H;
        boolean hovEdit = mx >= editX && mx < editX + BTN_W && my >= btnY && my < btnY + BTN_H;

        int pinBg  = hovPin ? 0xFF2a2a50 : 0xFF1a1a35;
        int pinBd  = pinned ? 0xFF40a040 : 0xFF404080;
        int pinCol = pinned ? 0xFF80FF80 : 0xFFCCCCFF;
        String pinLabel = pinned ? "HUD: ON" : "HUD: OFF";
        g.fill(pinX, btnY, pinX + BTN_W, btnY + BTN_H, pinBg);
        g.fill(pinX,          btnY,           pinX + BTN_W, btnY + 1,        pinBd);
        g.fill(pinX,          btnY + BTN_H-1, pinX + BTN_W, btnY + BTN_H,    pinBd);
        g.fill(pinX,          btnY,           pinX + 1,      btnY + BTN_H,    pinBd);
        g.fill(pinX + BTN_W-1, btnY,          pinX + BTN_W, btnY + BTN_H,    pinBd);
        g.centeredText(font, Component.literal(pinLabel), pinX + BTN_W / 2, btnY + (BTN_H - 8) / 2, pinCol);

        int editBg  = !pinned ? 0xFF141424 : (hovEdit ? 0xFF2a2a50 : 0xFF1a1a35);
        int editBd  = !pinned ? 0xFF282840 : 0xFF404080;
        int editCol = !pinned ? 0xFF555566 : 0xFFCCCCFF;
        g.fill(editX, btnY, editX + BTN_W, btnY + BTN_H, editBg);
        g.fill(editX,           btnY,           editX + BTN_W, btnY + 1,        editBd);
        g.fill(editX,           btnY + BTN_H-1, editX + BTN_W, btnY + BTN_H,    editBd);
        g.fill(editX,           btnY,           editX + 1,      btnY + BTN_H,    editBd);
        g.fill(editX + BTN_W-1, btnY,           editX + BTN_W, btnY + BTN_H,    editBd);
        g.centeredText(font, Component.literal("Edit Layout"), editX + BTN_W / 2, btnY + (BTN_H - 8) / 2, editCol);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean wasDragging) {
        if (event.button() == 0) {
            int mx = (int) event.x(), my = (int) event.y();
            int gridX = (width  - GRID_W) / 2;
            int gridY = (height - GRID_H) / 2;
            boolean pinned = BingoHudConfig.isVisible();
            int btnY   = gridY + GRID_H + (BingoClientCache.hasWon() ? 26 : 12);
            int totalW = 2 * BTN_W + 8;
            int pinX   = (width - totalW) / 2;
            int editX  = pinX + BTN_W + 8;

            if (mx >= pinX && mx < pinX + BTN_W && my >= btnY && my < btnY + BTN_H) {
                BingoHudConfig.setVisible(!pinned);
                return true;
            }
            if (pinned && mx >= editX && mx < editX + BTN_W && my >= btnY && my < btnY + BTN_H) {
                Minecraft.getInstance().setScreenAndShow(new BingoHudEditScreen());
                return true;
            }
        }
        return super.mouseClicked(event, wasDragging);
    }

    private static boolean isOnWinLine(int idx, Set<Integer> lines) {
        int r = idx / 5, c = idx % 5;
        if (lines.contains(r))     return true;
        if (lines.contains(5 + c)) return true;
        if (lines.contains(10) && r == c)     return true;
        if (lines.contains(11) && r + c == 4) return true;
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
