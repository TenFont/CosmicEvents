package com.cosmic901.cosmicevents.service;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;


public final class InventorySnapshotService {
    public static final long RESTORE_DELAY_TICKS = 5L;
    public static final long RESTORE_RETRY_TICKS = 15L;
    public static final long RESTORE_CONSUME_DELAY_TICKS = 40L;
    private static final String FOLDER_NAME = "inventories";

    private final Plugin plugin;
    private final Map<UUID, InventorySnapshot> snapshots = new ConcurrentHashMap<>();
    private final File folder;
    private boolean enabled = true;

    public InventorySnapshotService(Plugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), FOLDER_NAME);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean hasSnapshot(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        return snapshots.containsKey(uuid) || snapshotFile(uuid).exists();
    }


    public void captureIfAbsent(Player player) {
        if (!enabled || player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (hasSnapshot(uuid)) {
            return;
        }
        InventorySnapshot snapshot = InventorySnapshot.capture(player);
        snapshots.put(uuid, snapshot);
        saveToDisk(uuid, snapshot);
    }

    public void clearLiveIfSnapshotted(Player player) {
        if (!enabled || player == null || !hasSnapshot(player.getUniqueId())) {
            return;
        }
        InventorySnapshot.clearLive(player);
    }


    public boolean restore(Player player, boolean consume) {
        if (player == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        InventorySnapshot snapshot = snapshots.get(uuid);
        if (snapshot == null) {
            snapshot = loadFromDisk(uuid);
            if (snapshot != null) {
                snapshots.put(uuid, snapshot);
            }
        }
        if (snapshot == null) {
            return false;
        }
        try {
            snapshot.apply(player);
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to restore event inventory for " + player.getName() + ".", ex);
            return false;
        }
        if (consume) {
            discard(uuid);
        }
        return true;
    }

    public void discard(UUID uuid) {
        if (uuid == null) {
            return;
        }
        snapshots.remove(uuid);
        File file = snapshotFile(uuid);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete inventory snapshot " + file.getName() + ".");
        }
    }


    public void restoreAllOnline(boolean consume) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (hasSnapshot(player.getUniqueId())) {
                restore(player, consume);
            }
        }
    }

    private void saveToDisk(UUID uuid, InventorySnapshot snapshot) {
        if (!ensureFolder()) {
            plugin.getLogger().warning("Could not create " + FOLDER_NAME
                    + " folder — inventory restore will not survive a restart for "
                    + uuid + ".");
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        snapshot.save(yaml);
        File target = snapshotFile(uuid);
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        try {
            yaml.save(temp);
            moveAtomically(temp.toPath(), target.toPath());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not save inventory snapshot for " + uuid
                            + " — a restart before restore may lose this player's items.", ex);
            if (temp.exists() && !temp.delete()) {
                plugin.getLogger().warning("Could not delete leftover temp snapshot " + temp.getName() + ".");
            }
        }
    }

    private InventorySnapshot loadFromDisk(UUID uuid) {
        File file = snapshotFile(uuid);
        if (!file.exists()) {
            return null;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            return InventorySnapshot.load(yaml);
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not read inventory snapshot " + file.getName() + ".", ex);
            return null;
        }
    }

    private File snapshotFile(UUID uuid) {
        return new File(folder, uuid.toString() + ".yml");
    }

    private boolean ensureFolder() {
        return folder.exists() || folder.mkdirs();
    }

    private static void moveAtomically(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

