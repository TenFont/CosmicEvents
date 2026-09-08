package com.cosmic901.cosmicevents.service;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;


public final class InventorySnapshot {
    static final int CURRENT_VERSION = 1;
    private static final int DEFAULT_STORAGE_SIZE = 36;
    private static final int DEFAULT_ARMOR_SIZE = 4;
    private static final int DEFAULT_EXTRA_SIZE = 1;

    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final ItemStack[] extra;
    private final ItemStack cursor;
    private final int heldSlot;

    InventorySnapshot(ItemStack[] contents, ItemStack[] armor, ItemStack[] extra, ItemStack cursor, int heldSlot) {
        this.contents = cloneItems(contents);
        this.armor = cloneItems(armor);
        this.extra = cloneItems(extra);
        this.cursor = cloneOrNull(cursor);
        this.heldSlot = heldSlot;
    }

    public static InventorySnapshot capture(Player player) {
        PlayerInventory inventory = player.getInventory();
        return new InventorySnapshot(
                inventory.getStorageContents(),
                inventory.getArmorContents(),
                inventory.getExtraContents(),
                player.getItemOnCursor(),
                inventory.getHeldItemSlot()
        );
    }

    public static void clearLive(Player player) {
        if (player == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        try {
            player.closeInventory();
        } catch (Throwable ignored) {
        }
        try {
            inventory.clear();
        } catch (Throwable ignored) {
        }
        try {
            inventory.setArmorContents(new ItemStack[lengthOf(inventory.getArmorContents(), DEFAULT_ARMOR_SIZE)]);
        } catch (Throwable ignored) {
        }
        try {
            inventory.setExtraContents(new ItemStack[lengthOf(inventory.getExtraContents(), DEFAULT_EXTRA_SIZE)]);
        } catch (Throwable ignored) {
        }
        try {
            player.setItemOnCursor(null);
        } catch (Throwable ignored) {
        }
        try {
            player.updateInventory();
        } catch (Throwable ignored) {
        }
    }

    public void apply(Player player) {
        PlayerInventory inventory = player.getInventory();
        clearLive(player);

        try {
            inventory.setStorageContents(fitToSize(contents, lengthOf(inventory.getStorageContents(), DEFAULT_STORAGE_SIZE)));
        } catch (Throwable ignored) {
        }
        try {
            inventory.setArmorContents(fitToSize(armor, lengthOf(inventory.getArmorContents(), DEFAULT_ARMOR_SIZE)));
        } catch (Throwable ignored) {
        }
        try {
            inventory.setExtraContents(fitToSize(extra, lengthOf(inventory.getExtraContents(), DEFAULT_EXTRA_SIZE)));
        } catch (Throwable ignored) {
        }
        try {
            if (heldSlot >= 0 && heldSlot <= 8) {
                inventory.setHeldItemSlot(heldSlot);
            }
        } catch (Throwable ignored) {
        }
        try {
            player.setItemOnCursor(cloneOrNull(cursor));
        } catch (Throwable ignored) {
        }
        try {
            player.updateInventory();
        } catch (Throwable ignored) {
        }
    }

    public void save(FileConfiguration config) {
        config.set("version", CURRENT_VERSION);
        config.set("held-slot", heldSlot);
        writeItems(config, "contents", contents);
        writeItems(config, "armor", armor);
        writeItems(config, "extra", extra);
        if (cursor != null) {
            config.set("cursor", cursor.clone());
        }
    }

    public static InventorySnapshot load(FileConfiguration config) {
        int heldSlot = config.getInt("held-slot", 0);
        ItemStack cursor = config.getItemStack("cursor");
        return new InventorySnapshot(
                readItems(config, "contents", DEFAULT_STORAGE_SIZE),
                readItems(config, "armor", DEFAULT_ARMOR_SIZE),
                readItems(config, "extra", DEFAULT_EXTRA_SIZE),
                cursor,
                heldSlot
        );
    }

    int heldSlot() {
        return heldSlot;
    }

    ItemStack[] contents() {
        return contents;
    }

    static void writeItems(FileConfiguration config, String path, ItemStack[] items) {
        ItemStack[] source = items == null ? new ItemStack[0] : items;
        config.set(path + ".size", source.length);
        for (int i = 0; i < source.length; i++) {
            ItemStack item = cloneOrNull(source[i]);
            if (item != null) {
                config.set(path + "." + i, item);
            }
        }
    }

    static ItemStack[] readItems(FileConfiguration config, String path, int defaultSize) {
        int size = Math.max(0, config.getInt(path + ".size", defaultSize));
        ItemStack[] items = new ItemStack[size];
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return items;
        }
        for (String key : section.getKeys(false)) {
            if ("size".equals(key)) {
                continue;
            }
            int slot;
            try {
                slot = Integer.parseInt(key);
            } catch (NumberFormatException ex) {
                continue;
            }
            if (slot < 0 || slot >= items.length) {
                continue;
            }
            items[slot] = cloneOrNull(config.getItemStack(path + "." + key));
        }
        return items;
    }

    static ItemStack[] cloneItems(ItemStack[] source) {
        if (source == null) {
            return new ItemStack[0];
        }
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = cloneOrNull(source[i]);
        }
        return copy;
    }

    static ItemStack[] fitToSize(ItemStack[] source, int size) {
        int target = Math.max(0, size);
        ItemStack[] result = new ItemStack[target];
        if (source == null || target == 0) {
            return result;
        }
        int copy = Math.min(source.length, target);
        for (int i = 0; i < copy; i++) {
            result[i] = cloneOrNull(source[i]);
        }
        return result;
    }

    static ItemStack cloneOrNull(ItemStack item) {
        if (item == null || item.getAmount() <= 0) {
            return null;
        }
        try {
            if (item.getType().isAir()) {
                return null;
            }
        } catch (Throwable ignored) {
        }
        return item.clone();
    }

    private static int lengthOf(ItemStack[] array, int fallback) {
        return array == null ? fallback : array.length;
    }
}

