package com.cosmic901.cosmicevents.util;

import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CommandRunner {
    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();

    private CommandRunner() {
    }

    public static String prepare(String command) {
        return normalizeTellraw(CommandPlaceholders.stripLeadingSlash(command));
    }

    public static boolean runAsConsole(Logger logger, String command, String source) {
        String prepared = prepare(command);
        if (prepared.isEmpty()) {
            return false;
        }
        if (!isRegistered(prepared)) {
            logger.warning(source + " command '" + rootOf(prepared)
                    + "' is not a registered command — skipped: /" + prepared);
            return false;
        }
        try {
            boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), prepared);
            if (!ok) {
                logger.warning(source + " command reported failure (check its usage): /" + prepared);
            }
            return ok;
        } catch (RuntimeException ex) {
            logger.log(Level.WARNING, source + " command threw while running as console: /" + prepared, ex);
            return false;
        }
    }

    public static boolean runAsPlayer(Logger logger, Player player, String command, String source) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        String prepared = prepare(command);
        if (prepared.isEmpty()) {
            return false;
        }
        if (!isRegistered(prepared)) {
            logger.warning(source + " command '" + rootOf(prepared)
                    + "' is not a registered command — skipped: /" + prepared);
            return false;
        }
        try {
            boolean ok = player.performCommand(prepared);
            if (!ok) {
                logger.warning(source + " command reported failure for " + player.getName()
                        + " (missing permission or bad usage?): /" + prepared);
            }
            return ok;
        } catch (RuntimeException ex) {
            logger.log(Level.WARNING, source + " command threw while running as "
                    + player.getName() + ": /" + prepared, ex);
            return false;
        }
    }

    static boolean isRegistered(String command) {
        String root = rootOf(command);
        if (root.isEmpty()) {
            return false;
        }
        try {
            Command mapped = Bukkit.getCommandMap().getCommand(root);
            if (mapped != null) {
                return true;
            }
            return Bukkit.getPluginCommand(root) != null;
        } catch (Throwable ignored) {
            return true;
        }
    }

    static String rootOf(String command) {
        if (command == null) {
            return "";
        }
        String trimmed = command.trim();
        int end = indexOfWhitespace(trimmed, 0);
        return end < 0 ? trimmed : trimmed.substring(0, end);
    }

    public static String normalizeTellraw(String command) {
        if (command == null) {
            return "";
        }
        String trimmed = command.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        int rootEnd = indexOfWhitespace(trimmed, 0);
        if (rootEnd < 0) {
            return trimmed;
        }
        String root = trimmed.substring(0, rootEnd).toLowerCase(Locale.ROOT);
        if (!root.equals("tellraw") && !root.equals("minecraft:tellraw")) {
            return trimmed;
        }

        int targetStart = skipWhitespace(trimmed, rootEnd);
        if (targetStart >= trimmed.length()) {
            return trimmed;
        }
        int targetEnd = endOfSelector(trimmed, targetStart);
        int messageStart = skipWhitespace(trimmed, targetEnd);
        if (messageStart >= trimmed.length()) {
            return trimmed;
        }

        String message = trimmed.substring(messageStart);
        if (looksLikeComponent(message)) {
            return trimmed;
        }
        return trimmed.substring(0, messageStart) + GSON.serialize(Messages.parse(message));
    }

    static boolean looksLikeComponent(String message) {
        if (message == null || message.isEmpty()) {
            return true;
        }
        char first = message.charAt(0);
        return first == '{' || first == '[' || first == '"' || first == '\'';
    }

    private static int endOfSelector(String text, int start) {
        int depth = 0;
        int index = start;
        while (index < text.length()) {
            char c = text.charAt(index);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth = Math.max(0, depth - 1);
            } else if (Character.isWhitespace(c) && depth == 0) {
                return index;
            }
            index++;
        }
        return index;
    }

    private static int indexOfWhitespace(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int skipWhitespace(String text, int from) {
        int index = from;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }
}
