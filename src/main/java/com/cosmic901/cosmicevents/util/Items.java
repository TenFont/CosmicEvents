package com.cosmic901.cosmicevents.util;

import com.cosmic901.cosmicevents.integration.NexoItemResolver;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public final class Items {
    private Items() {
    }

    public static ItemStack create(Material material, int amount, String name, List<String> lore) {
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        return applyDisplay(item, name, lore);
    }

    public static ItemStack create(
            Plugin plugin,
            String configuredMaterial,
            Material fallback,
            int amount,
            String name,
            List<String> lore
    ) {
        ItemStack item = NexoItemResolver.create(plugin, configuredMaterial, fallback, amount);
        return applyDisplay(item, name, lore);
    }

    private static ItemStack applyDisplay(ItemStack item, String name, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null && !name.isEmpty()) {
                meta.displayName(Messages.parse(name));
            }
            if (lore != null && !lore.isEmpty()) {
                List<Component> components = new ArrayList<>(lore.size());
                for (String line : lore) {
                    components.add(Messages.parse(line));
                }
                meta.lore(components);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static Material materialOr(String name, Material fallback) {
        if (name == null || name.isEmpty()) {
            return fallback;
        }
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    public static boolean isValidMaterial(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        try {
            Material.valueOf(name.toUpperCase());
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}

