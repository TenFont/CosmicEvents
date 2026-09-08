package com.cosmic901.cosmicevents.placeholder;

import com.cosmic901.cosmicevents.CosmicEvents;
import com.cosmic901.cosmicevents.util.Messages;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class CosmicEventsPlaceholderExpansion extends PlaceholderExpansion {
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    private final CosmicEvents plugin;

    public CosmicEventsPlaceholderExpansion(CosmicEvents plugin) {
        this.plugin = plugin;
    }

    @Override
    @NotNull
    public String getAuthor() {
        return "cosmic901";
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return "cosmicevents";
    }

    @Override
    @NotNull
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        return resolve(plugin, player, params);
    }

    static String resolve(CosmicEvents plugin, String params) {
        return resolve(plugin, null, params);
    }

    static String resolve(CosmicEvents plugin, OfflinePlayer player, String params) {
        if (plugin.getEventManager() == null) {
            return "";
        }
        return switch (params.toLowerCase()) {
            case "time" -> LEGACY.serialize(Messages.parse(plugin.getEventManager().getPlaceholderTime()));
            case "state" -> plugin.getEventManager().getState().name();
            case "participants" -> String.valueOf(plugin.getEventManager().getParticipants().size());
            case "active_participants" -> String.valueOf(plugin.getEventManager().getActiveParticipants().size());
            case "minigame" -> {
                String id = plugin.getEventManager().getActiveMinigameId();
                yield id == null ? "" : id;
            }
            case "wins" -> {
                if (player == null) {
                    yield "0";
                }
                yield String.valueOf(plugin.getEventManager().getWinStatsService().getWins(player.getUniqueId()));
            }
            default -> null;
        };
    }
}

