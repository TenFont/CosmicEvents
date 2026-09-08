package com.cosmic901.cosmicevents.placeholder;

import com.cosmic901.cosmicevents.CosmicEvents;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class AliasPlaceholderExpansion extends PlaceholderExpansion {
    private final CosmicEvents plugin;
    private final String identifier;

    public AliasPlaceholderExpansion(CosmicEvents plugin, String identifier) {
        this.plugin = plugin;
        this.identifier = identifier;
    }

    @Override
    @NotNull
    public String getAuthor() {
        return "cosmic901";
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return identifier;
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
        return CosmicEventsPlaceholderExpansion.resolve(plugin, player, params);
    }
}

