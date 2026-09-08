package com.cosmic901.cosmicevents.service;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventorySnapshotTest {
    @Test
    void cloneItemsCopiesTheArrayAndLeavesNullSlots() {
        ItemStack[] source = new ItemStack[4];
        ItemStack[] cloned = InventorySnapshot.cloneItems(source);

        assertEquals(4, cloned.length);
        assertNotSame(source, cloned);
        assertNull(cloned[0]);
        source[1] = null;
        assertNull(cloned[1]);
    }

    @Test
    void cloneItemsTreatsNullSourceAsEmpty() {
        ItemStack[] cloned = InventorySnapshot.cloneItems(null);
        assertEquals(0, cloned.length);
    }

    @Test
    void fitToSizePadsAndTruncates() {
        ItemStack[] source = new ItemStack[2];
        ItemStack[] padded = InventorySnapshot.fitToSize(source, 5);
        assertEquals(5, padded.length);
        assertNull(padded[0]);
        assertNull(padded[4]);

        ItemStack[] truncated = InventorySnapshot.fitToSize(source, 1);
        assertEquals(1, truncated.length);

        ItemStack[] empty = InventorySnapshot.fitToSize(null, 3);
        assertEquals(3, empty.length);
    }

    @Test
    void yamlRoundTripPreservesHeldSlotAndArraySizes(@TempDir Path tempDir) throws IOException {
        InventorySnapshot snapshot = new InventorySnapshot(
                new ItemStack[36],
                new ItemStack[4],
                new ItemStack[1],
                null,
                7
        );
        YamlConfiguration yaml = new YamlConfiguration();
        snapshot.save(yaml);

        File file = tempDir.resolve(UUID.randomUUID() + ".yml").toFile();
        yaml.save(file);
        YamlConfiguration loadedYaml = YamlConfiguration.loadConfiguration(file);
        InventorySnapshot loaded = InventorySnapshot.load(loadedYaml);

        assertEquals(7, loaded.heldSlot());
        assertEquals(36, loaded.contents().length);
        assertEquals(1, loadedYaml.getInt("version"));
        assertEquals(36, loadedYaml.getInt("contents.size"));
        assertEquals(4, loadedYaml.getInt("armor.size"));
        assertEquals(1, loadedYaml.getInt("extra.size"));
        assertTrue(loadedYaml.getKeys(false).contains("held-slot"));
    }

    @Test
    void readItemsSkipsInvalidKeysAndMissingSize() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("contents.not-a-slot", "x");
        yaml.set("contents.size", 9);

        ItemStack[] items = InventorySnapshot.readItems(yaml, "contents", 36);
        assertEquals(9, items.length);
        for (ItemStack item : items) {
            assertNull(item);
        }
        assertNull(InventorySnapshot.cloneOrNull(null));
    }
}

