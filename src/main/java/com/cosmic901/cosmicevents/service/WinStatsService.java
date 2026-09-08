package com.cosmic901.cosmicevents.service;

import com.cosmic901.cosmicevents.CosmicEvents;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;


public final class WinStatsService {
    private static final String FILE_NAME = "player_wins.yml";

    private final CosmicEvents plugin;
    private final Map<UUID, Integer> wins = new HashMap<>();
    private File file;

    public WinStatsService(CosmicEvents plugin) {
        this.plugin = plugin;
    }

    public void load() {
        wins.clear();
        File target = resolveFile();
        if (!target.exists()) {
            return;
        }
        FileConfiguration storage = YamlConfiguration.loadConfiguration(target);
        for (String key : storage.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Ignoring malformed win entry in " + FILE_NAME + ": " + key);
                continue;
            }
            int count = storage.getInt(key, 0);
            if (count > 0) {
                wins.put(uuid, count);
            }
        }
    }

    public int getWins(UUID uuid) {
        if (uuid == null) {
            return 0;
        }
        return wins.getOrDefault(uuid, 0);
    }


    public void recordWins(Collection<UUID> winners) {
        if (winners == null || winners.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (UUID uuid : winners) {
            if (uuid == null) {
                continue;
            }
            increment(wins, uuid);
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    public void recordWin(UUID uuid) {
        if (uuid == null) {
            return;
        }
        increment(wins, uuid);
        save();
    }

    static int increment(Map<UUID, Integer> storage, UUID uuid) {
        return storage.merge(uuid, 1, Integer::sum);
    }

    private void save() {
        YamlConfiguration storage = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : wins.entrySet()) {
            if (entry.getValue() > 0) {
                storage.set(entry.getKey().toString(), entry.getValue());
            }
        }
        File target = resolveFile();
        try {
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create plugin folder for " + FILE_NAME + ".");
                return;
            }
            storage.save(target);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save " + FILE_NAME
                    + " — event wins may reset on restart.", ex);
        }
    }

    private File resolveFile() {
        if (file == null) {
            file = new File(plugin.getDataFolder(), FILE_NAME);
        }
        return file;
    }
}

