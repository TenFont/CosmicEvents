package com.cosmic901.cosmicevents.util;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

public final class Messages {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND =
            LegacyComponentSerializer.legacyAmpersand();

    private Messages() {
    }

    public static Component parse(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        String normalized = text.replace('§', '&');
        boolean looksLikeMiniMessage = normalized.indexOf('<') >= 0
                && normalized.indexOf('>') > normalized.indexOf('<');
        if (looksLikeMiniMessage) {


            String toParse = containsLegacyAmpersand(normalized)
                    ? legacyAmpersandToMini(normalized)
                    : normalized;
            try {
                return MINI.deserialize(toParse);
            } catch (Exception ignored) {
                return LEGACY_AMPERSAND.deserialize(normalized);
            }
        }
        return LEGACY_AMPERSAND.deserialize(normalized);
    }

    public static void send(Audience audience, String text) {
        audience.sendMessage(parse(text));
    }

    public static void send(Audience audience, Component component) {
        audience.sendMessage(component);
    }

    public static void broadcast(String text) {
        Bukkit.getServer().sendMessage(parse(text));
    }

    public static void broadcast(Component component) {
        Bukkit.getServer().sendMessage(component);
    }

    public static void sendBroadcastTo(Collection<UUID> uuids, String text) {
        Component component = parse(text);
        for (UUID uuid : uuids) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(component);
            }
        }
    }

    private static boolean containsLegacyAmpersand(String text) {
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) != '&') {
                continue;
            }
            if (legacyTag(text.charAt(i + 1)) != null) {
                return true;
            }
        }
        return false;
    }

    private static String legacyAmpersandToMini(String input) {
        StringBuilder out = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '&' && i + 1 < input.length()) {
                String tag = legacyTag(input.charAt(i + 1));
                if (tag != null) {
                    out.append('<').append(tag).append('>');
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String legacyTag(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> "black";
            case '1' -> "dark_blue";
            case '2' -> "dark_green";
            case '3' -> "dark_aqua";
            case '4' -> "dark_red";
            case '5' -> "dark_purple";
            case '6' -> "gold";
            case '7' -> "gray";
            case '8' -> "dark_gray";
            case '9' -> "blue";
            case 'a' -> "green";
            case 'b' -> "aqua";
            case 'c' -> "red";
            case 'd' -> "light_purple";
            case 'e' -> "yellow";
            case 'f' -> "white";
            case 'k' -> "obfuscated";
            case 'l' -> "bold";
            case 'm' -> "strikethrough";
            case 'n' -> "underlined";
            case 'o' -> "italic";
            case 'r' -> "reset";
            default -> null;
        };
    }
}

