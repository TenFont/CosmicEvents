package com.cosmic901.cosmicevents.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
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

public final class JoinStateService {
    private static final String FOLDER_NAME = "origins";

    private final Plugin plugin;
    private final Map<UUID, JoinState> states = new ConcurrentHashMap<>();
    private final File folder;

    public JoinStateService(Plugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), FOLDER_NAME);
    }

    public boolean hasState(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        return states.containsKey(uuid) || stateFile(uuid).exists();
    }

    public void captureIfAbsent(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (hasState(uuid)) {
            return;
        }
        JoinState state = JoinState.capture(player);
        states.put(uuid, state);
        saveToDisk(uuid, state);
    }

    public JoinState get(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        JoinState state = states.get(uuid);
        if (state != null) {
            return state;
        }
        state = loadFromDisk(uuid);
        if (state != null) {
            states.put(uuid, state);
        }
        return state;
    }

    public Location peekLocation(UUID uuid) {
        JoinState state = get(uuid);
        return state == null ? null : state.toLocation();
    }

    public boolean restore(Player player, boolean consume) {
        if (player == null) {
            return false;
        }
        JoinState state = get(player.getUniqueId());
        if (state == null) {
            return false;
        }
        try {
            state.apply(player);
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to restore event origin for " + player.getName() + ".", ex);
            return false;
        }
        if (consume) {
            discard(player.getUniqueId());
        }
        return true;
    }

    public boolean restoreEffects(Player player) {
        JoinState state = player == null ? null : get(player.getUniqueId());
        if (state == null) {
            clearAllEffects(player);
            return false;
        }
        state.restoreEffects(player);
        return true;
    }

    public boolean restoreGameMode(Player player) {
        JoinState state = player == null ? null : get(player.getUniqueId());
        if (state == null) {
            return false;
        }
        state.restoreGameMode(player);
        return true;
    }

    public boolean restorePersistedMovement(Player player) {
        JoinState state = player == null ? null : get(player.getUniqueId());
        if (state == null) {
            return false;
        }
        state.restoreMovement(player);
        return true;
    }

    public boolean teleportHome(Player player, Location fallback) {
        if (player == null) {
            return false;
        }
        JoinState state = get(player.getUniqueId());
        if (state != null && state.teleport(player)) {
            return true;
        }
        if (fallback == null || fallback.getWorld() == null) {
            return false;
        }
        try {
            return player.teleport(fallback);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void discard(UUID uuid) {
        if (uuid == null) {
            return;
        }
        states.remove(uuid);
        File file = stateFile(uuid);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete origin snapshot " + file.getName() + ".");
        }
    }

    public void restoreAllOnline(boolean consume) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (hasState(player.getUniqueId())) {
                restore(player, consume);
            }
        }
    }

    static void clearAllEffects(Player player) {
        if (player == null) {
            return;
        }
        try {
            for (org.bukkit.potion.PotionEffect effect : java.util.List.copyOf(player.getActivePotionEffects())) {
                player.removePotionEffect(effect.getType());
            }
        } catch (Throwable ignored) {
        }
    }

    private void saveToDisk(UUID uuid, JoinState state) {
        if (!ensureFolder()) {
            plugin.getLogger().warning("Could not create " + FOLDER_NAME
                    + " folder — origin restore will not survive a restart for "
                    + uuid + ".");
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        state.save(yaml);
        File target = stateFile(uuid);
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        try {
            yaml.save(temp);
            moveAtomically(temp.toPath(), target.toPath());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not save origin snapshot for " + uuid
                            + " — a restart before restore may dump this player into the overworld.", ex);
            if (temp.exists() && !temp.delete()) {
                plugin.getLogger().warning("Could not delete leftover temp origin " + temp.getName() + ".");
            }
        }
    }

    private JoinState loadFromDisk(UUID uuid) {
        File file = stateFile(uuid);
        if (!file.exists()) {
            return null;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            return JoinState.load(yaml);
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not read origin snapshot " + file.getName() + ".", ex);
            return null;
        }
    }

    private File stateFile(UUID uuid) {
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
