package com.cosmic901.cosmicevents.service;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

public final class VoteService {
    private final Map<UUID, String> votes = new HashMap<>();
    private final Random random = new Random();

    private List<String> configuredMinigames = List.of();
    private FileConfiguration votesPowerConfig;
    private Map<String, Integer> permissionWeights = Map.of();
    private boolean opsAlwaysUseOpWeight = true;

    public void reload(List<String> minigames, FileConfiguration votesPowerConfig, boolean opsAlwaysUseOpWeight) {
        this.configuredMinigames = List.copyOf(minigames);
        this.votesPowerConfig = votesPowerConfig;
        this.opsAlwaysUseOpWeight = opsAlwaysUseOpWeight;
        this.permissionWeights = flattenPermissionWeights(votesPowerConfig);
    }

    public void clear() {
        votes.clear();
    }

    public Map<UUID, String> getVotesView() {
        return Collections.unmodifiableMap(votes);
    }

    public List<String> getConfiguredMinigames() {
        return configuredMinigames;
    }

    public boolean isValidMinigame(String minigameId) {
        if (minigameId == null) {
            return false;
        }
        String normalized = minigameId.toLowerCase(Locale.ROOT);
        for (String game : configuredMinigames) {
            if (game.equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return false;
    }

    public String normalizeMinigameId(String minigameId) {
        if (minigameId == null) {
            return null;
        }
        String normalized = minigameId.toLowerCase(Locale.ROOT);
        for (String game : configuredMinigames) {
            if (game.equalsIgnoreCase(normalized)) {
                return game;
            }
        }
        return null;
    }

    public boolean hasVotedFor(UUID uuid, String minigameId) {
        String existing = votes.get(uuid);
        return existing != null && existing.equalsIgnoreCase(minigameId);
    }

    public void cast(UUID uuid, String normalizedMinigameId) {
        votes.put(uuid, normalizedMinigameId);
    }

    public void remove(UUID uuid) {
        votes.remove(uuid);
    }

    public int getVoteWeight(Player player) {
        if (votesPowerConfig == null) {
            return 1;
        }
        return resolveVoteWeight(
                votesPowerConfig.getInt("default_weight", 1),
                votesPowerConfig.getInt("op_weight", 5),
                opsAlwaysUseOpWeight,
                player.isOp(),
                permissionWeights,
                player::hasPermission);
    }


    static Map<String, Integer> flattenPermissionWeights(ConfigurationSection config) {
        if (config == null) {
            return Map.of();
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        flattenSection(config.getConfigurationSection("permissions"), "", out);
        Map<String, Integer> groups = new LinkedHashMap<>();
        flattenSection(config.getConfigurationSection("groups"), "", groups);
        for (Map.Entry<String, Integer> entry : groups.entrySet()) {
            String key = entry.getKey();
            String permission = key.startsWith("group.") ? key : "group." + key;
            out.merge(permission, entry.getValue(), Math::max);
        }
        return Collections.unmodifiableMap(out);
    }

    private static void flattenSection(ConfigurationSection section, String prefix, Map<String, Integer> out) {
        if (section == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : section.getValues(false).entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = entry.getValue();
            if (value instanceof ConfigurationSection nested) {
                flattenSection(nested, path, out);
                continue;
            }
            int weight = toInt(value, -1);
            if (weight >= 0) {
                out.merge(path, weight, Math::max);
            }
        }
    }

    static int resolveVoteWeight(
            int defaultWeight,
            int opWeight,
            boolean opsAlwaysUseOpWeight,
            boolean isOp,
            Map<String, Integer> permissionWeights,
            Predicate<String> hasPermission) {
        int maxWeight = defaultWeight;
        if (permissionWeights != null) {
            for (Map.Entry<String, Integer> entry : permissionWeights.entrySet()) {
                if (matchesVotePermission(entry.getKey(), hasPermission)) {
                    maxWeight = Math.max(maxWeight, entry.getValue());
                }
            }
        }
        if (isOp && opsAlwaysUseOpWeight) {
            return Math.max(maxWeight, opWeight);
        }
        return maxWeight;
    }

    static boolean matchesVotePermission(String configuredKey, Predicate<String> hasPermission) {
        if (configuredKey == null || configuredKey.isBlank() || hasPermission == null) {
            return false;
        }
        if (hasPermission.test(configuredKey)) {
            return true;
        }

        return !configuredKey.contains(".") && hasPermission.test("group." + configuredKey);
    }

    static int toInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public int getVoteCount(String minigameId, Function<UUID, Player> playerLookup) {
        int total = 0;
        for (Map.Entry<UUID, String> entry : votes.entrySet()) {
            if (!entry.getValue().equalsIgnoreCase(minigameId)) {
                continue;
            }
            Player player = playerLookup.apply(entry.getKey());
            if (player == null) {
                continue;
            }
            total += getVoteWeight(player);
        }
        return total;
    }

    public String determineWinner(Function<UUID, Player> playerLookup) {
        if (configuredMinigames.isEmpty()) {
            return null;
        }

        Map<String, Integer> tally = new LinkedHashMap<>();
        for (String game : configuredMinigames) {
            tally.put(game, 0);
        }

        for (Map.Entry<UUID, String> entry : votes.entrySet()) {
            Player player = playerLookup.apply(entry.getKey());
            if (player == null) {
                continue;
            }
            String game = normalizeMinigameId(entry.getValue());
            if (game == null) {
                continue;
            }
            tally.put(game, tally.getOrDefault(game, 0) + getVoteWeight(player));
        }

        int maxVotes = -1;
        List<String> winners = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : tally.entrySet()) {
            int value = entry.getValue();
            if (value > maxVotes) {
                maxVotes = value;
                winners.clear();
                winners.add(entry.getKey());
            } else if (value == maxVotes) {
                winners.add(entry.getKey());
            }
        }

        if (winners.isEmpty()) {
            return configuredMinigames.get(random.nextInt(configuredMinigames.size()));
        }
        return winners.get(random.nextInt(winners.size()));
    }


    public static String selectWinner(List<String> minigames, Map<String, Integer> weightedVotes, Random random) {
        Objects.requireNonNull(random, "random");
        if (minigames == null || minigames.isEmpty()) {
            return null;
        }
        Map<String, Integer> tally = new LinkedHashMap<>();
        for (String game : minigames) {
            tally.put(game, 0);
        }
        if (weightedVotes != null) {
            for (Map.Entry<String, Integer> entry : weightedVotes.entrySet()) {
                if (!tally.containsKey(entry.getKey())) {
                    continue;
                }
                tally.put(entry.getKey(), tally.get(entry.getKey()) + Math.max(0, entry.getValue()));
            }
        }
        int max = -1;
        List<String> winners = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : tally.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                winners.clear();
                winners.add(entry.getKey());
            } else if (entry.getValue() == max) {
                winners.add(entry.getKey());
            }
        }
        return winners.get(random.nextInt(winners.size()));
    }
}

