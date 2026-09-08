package com.cosmic901.cosmicevents.integration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class NexoItemResolver {

    private static final Set<String> WARNED_IDS = ConcurrentHashMap.newKeySet();

    private NexoItemResolver() {
    }

    public static boolean isNexoItem(String configuredMaterial) {
        if (configuredMaterial == null) {
            return false;
        }
        String normalized = configuredMaterial.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("nexo-") || normalized.startsWith("nexo:");
    }

    public static ItemStack create(Plugin plugin, String configuredMaterial, Material fallback, int amount) {
        int safeAmount = Math.max(1, amount);
        if (!isNexoItem(configuredMaterial)) {
            Material material = parseVanilla(configuredMaterial, fallback);
            return new ItemStack(material, Math.min(safeAmount, material.getMaxStackSize()));
        }

        String itemId = configuredMaterial.trim().substring(5);
        try {
            Class<?> nexoItems = Class.forName("com.nexomc.nexo.api.NexoItems");
            Method itemFromId = nexoItems.getMethod("itemFromId", String.class);
            Object builder = itemFromId.invoke(null, itemId);
            if (builder == null) {
                warnOnce(plugin, itemId, "is not registered");
                return fallback(fallback, safeAmount);
            }
            Object result = builder.getClass().getMethod("build").invoke(builder);
            if (result instanceof ItemStack item) {
                item.setAmount(Math.min(safeAmount, Math.max(1, item.getMaxStackSize())));
                return item;
            }
            warnOnce(plugin, itemId, "did not build an ItemStack");
        } catch (ClassNotFoundException ex) {
            warnOnce(plugin, itemId, "requires the Nexo plugin");
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | LinkageError ex) {
            warnOnce(plugin, itemId, "could not be built: " + rootMessage(ex));
        }
        return fallback(fallback, safeAmount);
    }

    public static Material parseVanilla(String configuredMaterial, Material fallback) {
        Material safeFallback = fallback != null ? fallback : Material.PAPER;
        if (configuredMaterial == null || configuredMaterial.isBlank()) {
            return safeFallback;
        }
        String normalized = configuredMaterial.trim()
                .toUpperCase(Locale.ROOT)
                .replace("MINECRAFT:", "")
                .replace(' ', '_');
        Material material = Material.matchMaterial(normalized);
        return material != null && !material.isAir() ? material : safeFallback;
    }

    private static ItemStack fallback(Material fallback, int amount) {
        Material material = fallback != null ? fallback : Material.PAPER;
        return new ItemStack(material, Math.min(amount, material.getMaxStackSize()));
    }

    private static void warnOnce(Plugin plugin, String itemId, String reason) {
        String key = itemId.toLowerCase(Locale.ROOT) + "|" + reason;
        if (WARNED_IDS.add(key)) {
            plugin.getLogger().warning("Nexo item '" + itemId + "' " + reason + "; using the fallback material.");
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}

