package com.randomdrops.client;

import com.google.gson.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;

@Environment(EnvType.CLIENT)
public final class BingoHudConfig {

    private static int     hudX     = 4;
    private static int     hudY     = 4;
    private static int     cellSize = 20;
    private static boolean visible  = false;

    private BingoHudConfig() {}

    public static int     getX()        { return hudX; }
    public static int     getY()        { return hudY; }
    public static int     getCellSize() { return cellSize; }
    public static boolean isVisible()   { return visible; }

    public static void setVisible(boolean v)          { visible = v; save(); }
    public static void setPosition(int x, int y)      { hudX = x; hudY = y; save(); }
    public static void setCellSize(int s)              { cellSize = Math.max(18, Math.min(40, s)); save(); }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("randomdrops_bingo_hud.json");
    }

    public static void load() {
        try (Reader r = new FileReader(configPath().toFile())) {
            JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
            hudX     = obj.has("x")        ? obj.get("x").getAsInt()          : hudX;
            hudY     = obj.has("y")        ? obj.get("y").getAsInt()          : hudY;
            cellSize = obj.has("cellSize") ? obj.get("cellSize").getAsInt()   : cellSize;
            visible  = obj.has("visible")  ? obj.get("visible").getAsBoolean(): visible;
        } catch (Exception ignored) {}
    }

    static void save() {
        JsonObject obj = new JsonObject();
        obj.addProperty("x", hudX);
        obj.addProperty("y", hudY);
        obj.addProperty("cellSize", cellSize);
        obj.addProperty("visible", visible);
        try (Writer w = new FileWriter(configPath().toFile())) {
            new GsonBuilder().setPrettyPrinting().create().toJson(obj, w);
        } catch (Exception ignored) {}
    }
}
