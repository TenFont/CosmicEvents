package com.cosmic901.cosmicevents.service;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class JoinState {
    static final int CURRENT_VERSION = 1;

    private final String worldName;
    private final UUID worldUid;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final String gameMode;
    private final float walkSpeed;
    private final float flySpeed;
    private final boolean allowFlight;
    private final boolean flying;
    private final List<SerializedEffect> effects;

    JoinState(
            String worldName,
            UUID worldUid,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            String gameMode,
            float walkSpeed,
            float flySpeed,
            boolean allowFlight,
            boolean flying,
            List<SerializedEffect> effects
    ) {
        this.worldName = worldName;
        this.worldUid = worldUid;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.gameMode = gameMode;
        this.walkSpeed = walkSpeed;
        this.flySpeed = flySpeed;
        this.allowFlight = allowFlight;
        this.flying = flying;
        this.effects = List.copyOf(effects == null ? List.of() : effects);
    }

    public static JoinState capture(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        List<SerializedEffect> effects = new ArrayList<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            SerializedEffect serialized = SerializedEffect.from(effect);
            if (serialized != null) {
                effects.add(serialized);
            }
        }
        return new JoinState(
                world != null ? world.getName() : null,
                world != null ? world.getUID() : null,
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                loc.getYaw(),
                loc.getPitch(),
                player.getGameMode().name(),
                player.getWalkSpeed(),
                player.getFlySpeed(),
                player.getAllowFlight(),
                player.isFlying(),
                effects
        );
    }

    public Location toLocation() {
        World world = resolveWorld();
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    public boolean teleport(Player player) {
        if (player == null) {
            return false;
        }
        Location loc = toLocation();
        if (loc == null) {
            return false;
        }
        try {
            return player.teleport(loc);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void restoreEffects(Player player) {
        if (player == null) {
            return;
        }
        try {
            for (PotionEffect current : List.copyOf(player.getActivePotionEffects())) {
                player.removePotionEffect(current.getType());
            }
        } catch (Throwable ignored) {
        }
        for (SerializedEffect effect : effects) {
            PotionEffect applied = effect.toPotionEffect();
            if (applied == null) {
                continue;
            }
            try {
                player.addPotionEffect(applied);
            } catch (Throwable ignored) {
            }
        }
    }

    public void restoreGameMode(Player player) {
        if (player == null || gameMode == null || gameMode.isBlank()) {
            return;
        }
        try {
            player.setGameMode(GameMode.valueOf(gameMode));
        } catch (Throwable ignored) {
        }
    }

    public void restoreMovement(Player player) {
        if (player == null) {
            return;
        }
        try {
            player.setWalkSpeed(clampSpeed(walkSpeed));
            player.setFlySpeed(clampSpeed(flySpeed));
            player.setAllowFlight(allowFlight);
            player.setFlying(flying && allowFlight);
        } catch (Throwable ignored) {
        }
    }

    public void apply(Player player) {
        restoreEffects(player);
        restoreGameMode(player);
        restoreMovement(player);
        teleport(player);
    }

    public void save(FileConfiguration config) {
        config.set("version", CURRENT_VERSION);
        config.set("world", worldName);
        if (worldUid != null) {
            config.set("world-uid", worldUid.toString());
        }
        config.set("x", x);
        config.set("y", y);
        config.set("z", z);
        config.set("yaw", yaw);
        config.set("pitch", pitch);
        config.set("gamemode", gameMode);
        config.set("walk-speed", walkSpeed);
        config.set("fly-speed", flySpeed);
        config.set("allow-flight", allowFlight);
        config.set("flying", flying);
        List<java.util.Map<String, Object>> serialized = new ArrayList<>();
        for (SerializedEffect effect : effects) {
            serialized.add(effect.toMap());
        }
        config.set("effects", serialized);
    }

    public static JoinState load(FileConfiguration config) {
        UUID worldUid = null;
        String uidRaw = config.getString("world-uid");
        if (uidRaw != null && !uidRaw.isBlank()) {
            try {
                worldUid = UUID.fromString(uidRaw.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return new JoinState(
                config.getString("world"),
                worldUid,
                config.getDouble("x"),
                config.getDouble("y"),
                config.getDouble("z"),
                (float) config.getDouble("yaw"),
                (float) config.getDouble("pitch"),
                config.getString("gamemode"),
                (float) config.getDouble("walk-speed", 0.2D),
                (float) config.getDouble("fly-speed", 0.1D),
                config.getBoolean("allow-flight", false),
                config.getBoolean("flying", false),
                readEffects(config)
        );
    }

    String worldName() {
        return worldName;
    }

    UUID worldUid() {
        return worldUid;
    }

    double x() {
        return x;
    }

    double y() {
        return y;
    }

    double z() {
        return z;
    }

    float yaw() {
        return yaw;
    }

    float pitch() {
        return pitch;
    }

    String gameMode() {
        return gameMode;
    }

    float walkSpeed() {
        return walkSpeed;
    }

    float flySpeed() {
        return flySpeed;
    }

    boolean allowFlight() {
        return allowFlight;
    }

    boolean flying() {
        return flying;
    }

    List<SerializedEffect> effects() {
        return effects;
    }

    private static float clampSpeed(float speed) {
        if (speed < -1.0f) {
            return -1.0f;
        }
        if (speed > 1.0f) {
            return 1.0f;
        }
        return speed;
    }

    private World resolveWorld() {
        if (worldName != null && !worldName.isBlank()) {
            World named = Bukkit.getWorld(worldName);
            if (named != null) {
                return named;
            }
        }
        if (worldUid != null) {
            return Bukkit.getWorld(worldUid);
        }
        return null;
    }

    static List<SerializedEffect> readEffects(FileConfiguration config) {
        List<SerializedEffect> effects = new ArrayList<>();
        List<?> raw = config.getList("effects");
        if (raw == null) {
            ConfigurationSection section = config.getConfigurationSection("effects");
            if (section == null) {
                return List.of();
            }
            for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry != null) {
                    SerializedEffect effect = SerializedEffect.fromSection(entry);
                    if (effect != null) {
                        effects.add(effect);
                    }
                }
            }
            return List.copyOf(effects);
        }
        for (Object entry : raw) {
            if (entry instanceof java.util.Map<?, ?> map) {
                SerializedEffect effect = SerializedEffect.fromMap(map);
                if (effect != null) {
                    effects.add(effect);
                }
            }
        }
        return List.copyOf(effects);
    }

    record SerializedEffect(
            String type,
            int duration,
            int amplifier,
            boolean ambient,
            boolean particles,
            boolean icon
    ) {
        static SerializedEffect from(PotionEffect effect) {
            if (effect == null || effect.getType() == null) {
                return null;
            }
            NamespacedKey key;
            try {
                key = effect.getType().getKey();
            } catch (Throwable ignored) {
                return null;
            }
            if (key == null) {
                return null;
            }
            return new SerializedEffect(
                    key.toString(),
                    effect.getDuration(),
                    effect.getAmplifier(),
                    effect.isAmbient(),
                    effect.hasParticles(),
                    effect.hasIcon()
            );
        }

        static SerializedEffect fromSection(ConfigurationSection section) {
            String type = section.getString("type");
            if (type == null || type.isBlank()) {
                return null;
            }
            return new SerializedEffect(
                    type,
                    section.getInt("duration", 1),
                    section.getInt("amplifier", 0),
                    section.getBoolean("ambient", false),
                    section.getBoolean("particles", true),
                    section.getBoolean("icon", true)
            );
        }

        static SerializedEffect fromMap(java.util.Map<?, ?> map) {
            Object type = map.get("type");
            if (type == null) {
                return null;
            }
            return new SerializedEffect(
                    String.valueOf(type),
                    intValue(map.get("duration"), 1),
                    intValue(map.get("amplifier"), 0),
                    boolValue(map.get("ambient"), false),
                    boolValue(map.get("particles"), true),
                    boolValue(map.get("icon"), true)
            );
        }

        java.util.Map<String, Object> toMap() {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("type", type);
            map.put("duration", duration);
            map.put("amplifier", amplifier);
            map.put("ambient", ambient);
            map.put("particles", particles);
            map.put("icon", icon);
            return map;
        }

        PotionEffect toPotionEffect() {
            PotionEffectType effectType = resolveType(type);
            if (effectType == null) {
                return null;
            }
            try {
                return new PotionEffect(effectType, duration, amplifier, ambient, particles, icon);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static PotionEffectType resolveType(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String normalized = raw.toLowerCase(Locale.ROOT).trim();
            NamespacedKey key = NamespacedKey.fromString(normalized);
            if (key == null && !normalized.contains(":")) {
                key = NamespacedKey.minecraft(normalized.replace(' ', '_'));
            }
            if (key == null) {
                return null;
            }
            return PotionEffectType.getByKey(key);
        }

        private static int intValue(Object value, int fallback) {
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value != null) {
                try {
                    return Integer.parseInt(String.valueOf(value));
                } catch (NumberFormatException ignored) {
                }
            }
            return fallback;
        }

        private static boolean boolValue(Object value, boolean fallback) {
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value != null) {
                return Boolean.parseBoolean(String.valueOf(value));
            }
            return fallback;
        }
    }
}
