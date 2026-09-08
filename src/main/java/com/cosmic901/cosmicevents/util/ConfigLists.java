package com.cosmic901.cosmicevents.util;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public final class ConfigLists {
    private ConfigLists() {
    }

    public static List<String> stringList(ConfigurationSection section, String path) {
        if (section == null || path == null || path.isEmpty() || !section.contains(path)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        if (section.isList(path)) {
            List<?> raw = section.getList(path);
            if (raw != null) {
                for (Object entry : raw) {
                    addIfPresent(out, entry);
                }
            }
        } else if (!section.isConfigurationSection(path)) {
            addIfPresent(out, section.get(path));
        }
        return List.copyOf(out);
    }

    public static List<String> stringLists(ConfigurationSection section, String... paths) {
        if (section == null || paths == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String path : paths) {
            out.addAll(stringList(section, path));
        }
        return List.copyOf(out);
    }

    private static void addIfPresent(List<String> out, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value);
        if (!text.isBlank()) {
            out.add(text);
        }
    }
}
