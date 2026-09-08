package com.cosmic901.cosmicevents.service;

import com.cosmic901.cosmicevents.CosmicEvents;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;


public final class DonorStartService {
    private static final String FILE_NAME = "donor_cooldowns.yml";

    private final CosmicEvents plugin;
    private final Map<UUID, Long> lastUsed = new HashMap<>();
    private File file;

    private boolean enabled = true;
    private String permission = "group.sun";
    private double cooldownHours = 24.0D;
    private int joinDurationSeconds;
    private boolean autoJoin = true;

    public DonorStartService(CosmicEvents plugin) {
        this.plugin = plugin;
    }

    public void reload(FileConfiguration config) {
        enabled = config.getBoolean("donor_start.enabled", true);
        permission = config.getString("donor_start.permission", "group.sun");
        cooldownHours = Math.max(0.0D, config.getDouble("donor_start.cooldown_hours", 24.0D));
        joinDurationSeconds = Math.max(0, config.getInt("donor_start.join_duration_seconds", 0));
        autoJoin = config.getBoolean("donor_start.auto_join", true);
        loadCooldowns();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getPermission() {
        return permission;
    }


    public int getJoinDurationSeconds() {
        return joinDurationSeconds;
    }

    public boolean isAutoJoin() {
        return autoJoin;
    }

    public long getCooldownMillis() {
        return (long) (cooldownHours * 3600_000.0D);
    }


    public long getRemainingCooldownMillis(UUID uuid) {
        Long used = lastUsed.get(uuid);
        if (used == null) {
            return 0L;
        }
        return remainingCooldownMillis(used, System.currentTimeMillis(), getCooldownMillis());
    }

    static long remainingCooldownMillis(long lastUsedMillis, long nowMillis, long windowMillis) {
        if (windowMillis <= 0L) {
            return 0L;
        }
        long elapsed = nowMillis - lastUsedMillis;
        if (elapsed < 0L) {


            return windowMillis;
        }
        return Math.max(0L, windowMillis - elapsed);
    }

    public void markUsed(UUID uuid) {
        lastUsed.put(uuid, System.currentTimeMillis());
        save();
    }

    private void loadCooldowns() {
        lastUsed.clear();
        long window = getCooldownMillis();
        if (window <= 0L) {
            return;
        }
        File target = resolveFile();
        if (!target.exists()) {
            return;
        }
        FileConfiguration storage = YamlConfiguration.loadConfiguration(target);
        long now = System.currentTimeMillis();
        for (String key : storage.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Ignoring malformed donor cooldown entry in "
                        + FILE_NAME + ": " + key);
                continue;
            }
            long used = storage.getLong(key);
            if (now - used < window) {
                lastUsed.put(uuid, used);
            }
        }
    }

    private void save() {
        long window = getCooldownMillis();
        long now = System.currentTimeMillis();
        lastUsed.entrySet().removeIf(entry -> window > 0L && now - entry.getValue() >= window);

        YamlConfiguration storage = new YamlConfiguration();
        for (Map.Entry<UUID, Long> entry : lastUsed.entrySet()) {
            storage.set(entry.getKey().toString(), entry.getValue());
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
                    + " — donor cooldowns will reset on restart.", ex);
        }
    }

    private File resolveFile() {
        if (file == null) {
            file = new File(plugin.getDataFolder(), FILE_NAME);
        }
        return file;
    }
}

