package com.randomdrops.mapping;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

/**
 * Stores items the player has actually picked up as randomised drops.
 * Key format: "type|sourceId|droppedItemId"
 *   type       = "block" or "mob"
 *   sourceId   = block/entity-type identifier (minecraft:grass_block, minecraft:sheep)
 *   droppedItemId = what the source would have normally dropped (minecraft:dirt, minecraft:white_wool)
 */
public class DropMappingState extends SavedData {

    public static final Codec<DropMappingState> CODEC =
        Codec.STRING.listOf()
             .xmap(list -> new DropMappingState(new HashSet<>(list)),
                   s -> new ArrayList<>(s.discovered));

    public static final SavedDataType<DropMappingState> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("randomdrops", "discoveries"),
        DropMappingState::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    static final String TAG_KEY = "randomdrops_src";

    private final Set<String> discovered;
    private long worldSeed;

    public DropMappingState() {
        this.discovered = new HashSet<>();
    }

    private DropMappingState(Set<String> discovered) {
        this.discovered = discovered;
    }

    public static DropMappingState get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        DropMappingState state = overworld.getDataStorage().computeIfAbsent(TYPE);
        state.worldSeed = overworld.getSeed();
        return state;
    }

    public long getWorldSeed() { return worldSeed; }

    /** Tag a replacement stack with composite source info for pickup recording. */
    public static void tagStack(ItemStack stack, String compositeKey) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
            tag -> tag.putString(TAG_KEY, compositeKey));
    }

    /**
     * Called on pickup: reads the composite tag, records the discovery, strips the tag.
     * Returns true if a new discovery was recorded.
     */
    public static boolean tryRecordAndStrip(ItemStack stack, MinecraftServer server) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) return false;
        CompoundTag tag = data.copyTag();
        if (!tag.contains(TAG_KEY)) return false;

        String compositeKey = tag.getStringOr(TAG_KEY, "");
        if (compositeKey.isEmpty()) return false;
        if (compositeKey.split("\\|").length != 3) return false;

        DropMappingState.get(server).markDiscovered(compositeKey);

        // Strip tag so the item stacks normally in inventory
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.remove(TAG_KEY));
        CustomData remaining = stack.get(DataComponents.CUSTOM_DATA);
        if (remaining != null && remaining.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);

        return true;
    }

    public void markDiscovered(String compositeKey) {
        if (discovered.add(compositeKey)) setDirty();
    }

    /**
     * Returns categorised entries for the network packet.
     * Format: "type|sourceId|replacementId"  (replacement is computed from droppedItemId)
     */
    public List<String> getCategorizedEntries() {
        List<String> result = new ArrayList<>();
        for (String key : discovered) {
            String[] parts = key.split("\\|", 3);
            if (parts.length != 3) continue;
            // parts: [type, sourceId, droppedItemId]
            Identifier droppedId = Identifier.tryParse(parts[2]);
            if (droppedId == null) continue;

            Item replacement = DropMappingGenerator.getItem(worldSeed, droppedId);
            Identifier replacementId = BuiltInRegistries.ITEM.getKey(replacement);
            if (replacementId == null || replacementId.equals(droppedId)) continue;

            // Output: "block|minecraft:grass_block|minecraft:gold_ingot"
            result.add(parts[0] + "|" + parts[1] + "|" + replacementId);
        }
        return result;
    }
}
