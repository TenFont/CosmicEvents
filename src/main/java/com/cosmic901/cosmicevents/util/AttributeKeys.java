package com.cosmic901.cosmicevents.util;

import org.bukkit.NamespacedKey;

public final class AttributeKeys {
    private AttributeKeys() {
    }


    public static boolean isPluginOwned(NamespacedKey key) {
        return key != null && !NamespacedKey.MINECRAFT.equals(key.getNamespace());
    }

    public static boolean keepDuringLiveGame(NamespacedKey key) {
        return key != null && NamespacedKey.MINECRAFT.equals(key.getNamespace());
    }
}

