package com.cosmic901.cosmicevents.util;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class Sounds {
    private final JavaPlugin plugin;
    private FileConfiguration soundsConfig;

    public Sounds(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload(FileConfiguration soundsConfig) {
        this.soundsConfig = soundsConfig;
    }

    public void play(Player player, String soundPath) {
        resolve(soundPath).ifPresent(resolved ->
                player.playSound(player.getLocation(), resolved.sound(), resolved.volume(), resolved.pitch()));
    }

    public void playToAll(String soundPath) {
        resolve(soundPath).ifPresent(resolved -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), resolved.sound(), resolved.volume(), resolved.pitch());
            }
        });
    }

    public void playToPlayers(Collection<UUID> uuids, String soundPath) {
        Optional<ResolvedSound> resolved = resolve(soundPath);
        if (resolved.isEmpty()) {
            return;
        }
        ResolvedSound sound = resolved.get();
        for (UUID uuid : uuids) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }
            player.playSound(player.getLocation(), sound.sound(), sound.volume(), sound.pitch());
        }
    }

    private Optional<ResolvedSound> resolve(String soundPath) {
        if (soundsConfig == null || !soundsConfig.getBoolean(soundPath + ".enabled", false)) {
            return Optional.empty();
        }
        String soundName = soundsConfig.getString(soundPath + ".sound");
        if (soundName == null || soundName.isEmpty()) {
            return Optional.empty();
        }
        Sound sound = lookupSound(soundName);
        if (sound == null) {
            logger().warning("Invalid sound name '" + soundName + "' configured at path: " + soundPath);
            return Optional.empty();
        }
        float volume = (float) soundsConfig.getDouble(soundPath + ".volume", 1.0);
        float pitch = (float) soundsConfig.getDouble(soundPath + ".pitch", 1.0);
        return Optional.of(new ResolvedSound(sound, volume, pitch));
    }

    private Sound lookupSound(String soundName) {
        String raw = soundName.trim();
        if (raw.isEmpty()) {
            return null;
        }


        String keyValue = raw.toLowerCase(Locale.ROOT).replace(' ', '_');
        if (keyValue.contains(":") || keyValue.contains(".")) {
            NamespacedKey key = keyValue.contains(":")
                    ? NamespacedKey.fromString(keyValue)
                    : NamespacedKey.minecraft(keyValue);
            if (key != null) {
                Sound sound = Registry.SOUNDS.get(key);
                if (sound != null) {
                    return sound;
                }
            }
        }


        String bukkitStyle = raw.toUpperCase(Locale.ROOT).replace(' ', '_');
        for (Sound sound : Registry.SOUNDS) {
            NamespacedKey key = Registry.SOUNDS.getKey(sound);
            if (key == null) {
                continue;
            }
            String asBukkit = key.getKey().replace('.', '_').toUpperCase(Locale.ROOT);
            if (asBukkit.equals(bukkitStyle)) {
                return sound;
            }
        }


        if (!keyValue.contains(":") && !keyValue.contains(".")) {
            NamespacedKey key = NamespacedKey.minecraft(keyValue.replace('_', '.'));
            if (key != null) {
                return Registry.SOUNDS.get(key);
            }
        }
        return null;
    }

    private Logger logger() {
        return plugin.getLogger();
    }

    private record ResolvedSound(Sound sound, float volume, float pitch) {
    }
}

