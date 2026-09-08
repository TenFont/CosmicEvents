package com.cosmic901.cosmicevents.util;

import org.bukkit.entity.Player;

import java.util.Locale;

public final class CommandPlaceholders {
    private CommandPlaceholders() {
    }

    public static boolean needsPlayer(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        String lower = command.toLowerCase(Locale.ROOT);
        return lower.contains("{player}")
                || lower.contains("{uuid}")
                || lower.contains("%player%")
                || lower.contains("%uuid%");
    }

    public static String apply(String command, Player player, int arenaId, String gameId) {
        String result = command == null ? "" : command;
        result = result
                .replace("{arena}", String.valueOf(arenaId))
                .replace("{game}", gameId == null ? "" : gameId)
                .replace("%arena%", String.valueOf(arenaId))
                .replace("%game%", gameId == null ? "" : gameId);
        if (player != null) {
            result = result
                    .replace("{player}", player.getName())
                    .replace("{uuid}", player.getUniqueId().toString())
                    .replace("%player%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString());
        }
        return result;
    }

    public static String applyReward(String command, Player player, int position) {
        String result = command == null ? "" : command;
        result = result
                .replace("{position}", String.valueOf(position))
                .replace("%position%", String.valueOf(position));
        if (player != null) {
            result = result
                    .replace("{player}", player.getName())
                    .replace("{uuid}", player.getUniqueId().toString())
                    .replace("%player%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString());
        }
        return result;
    }

    public static String normalizeWhitespace(String command) {
        if (command == null) {
            return "";
        }
        return command.trim().replaceAll("\\s+", " ");
    }

    public static String stripLeadingSlash(String command) {
        String trimmed = normalizeWhitespace(command);
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }
}

