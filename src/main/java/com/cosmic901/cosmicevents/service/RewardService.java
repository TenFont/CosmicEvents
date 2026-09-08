package com.cosmic901.cosmicevents.service;

import com.cosmic901.cosmicevents.util.CommandPlaceholders;
import com.cosmic901.cosmicevents.util.CommandRunner;
import com.cosmic901.cosmicevents.util.ConfigLists;
import com.cosmic901.cosmicevents.util.Items;
import com.cosmic901.cosmicevents.util.Messages;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RewardService {
    private final JavaPlugin plugin;
    private final Map<UUID, Integer> pendingRewards = new HashMap<>();
    private FileConfiguration config;

    public RewardService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload(FileConfiguration config) {
        this.config = config;
    }

    public Map<UUID, Integer> getPendingRewardsView() {
        return Map.copyOf(pendingRewards);
    }

    Map<UUID, Integer> pendingRewards() {
        return pendingRewards;
    }

    public void setPending(UUID uuid, int position) {
        pendingRewards.put(uuid, position);
    }

    public Integer removePending(UUID uuid) {
        return pendingRewards.remove(uuid);
    }

    public void clear() {
        pendingRewards.clear();
    }

    public void giveReward(Player player, int position) {
        if (config == null || player == null) {
            return;
        }

        ConfigurationSection rewardsSection = config.getConfigurationSection("rewards");
        ConfigurationSection positionSection = null;
        if (rewardsSection != null) {
            if (!rewardsSection.getBoolean("enabled", true)) {
                return;
            }
            String posKey = switch (position) {
                case 1 -> "first";
                case 2 -> "second";
                case 3 -> "third";
                default -> "";
            };
            if (!posKey.isEmpty()) {
                positionSection = rewardsSection.getConfigurationSection(posKey);
            }
        }

        if (positionSection == null && position == 1) {
            positionSection = config.getConfigurationSection("reward");
        }
        if (positionSection == null) {
            return;
        }
        if ("reward".equals(positionSection.getName()) && !positionSection.getBoolean("enabled", true)) {
            return;
        }

        giveItemReward(player, positionSection);
        sendRewardMessage(player, positionSection, position);
        runRewardCommands(player, positionSection, position);
    }

    private static boolean hasItem(ConfigurationSection positionSection) {
        return positionSection.isConfigurationSection("item") || positionSection.contains("item.material");
    }

    private void giveItemReward(Player player, ConfigurationSection positionSection) {
        if (!hasItem(positionSection)) {
            return;
        }

        String matName = positionSection.getString("item.material", "GOLD_INGOT");
        int amount = positionSection.getInt("item.amount", 1);
        String name = positionSection.getString("item.name");
        List<String> lore = positionSection.getStringList("item.lore");

        if (!Items.isValidMaterial(matName)
                && !com.cosmic901.cosmicevents.integration.NexoItemResolver.isNexoItem(matName)) {
            plugin.getLogger().warning("Invalid reward item material: " + matName);
        }

        ItemStack item = Items.create(plugin, matName, Material.GOLD_INGOT, amount, name, lore);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack leftoverItem : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem);
        }
    }

    private void sendRewardMessage(Player player, ConfigurationSection positionSection, int position) {
        String message = positionSection.getString("message");
        if (message == null || message.isBlank()) {
            return;
        }
        Messages.send(player, applyPlaceholders(
                applyItemPlaceholders(message, positionSection), player, position));
    }

    static String applyItemPlaceholders(String text, ConfigurationSection positionSection) {
        if (text == null) {
            return "";
        }
        if (!text.contains("{amount}") && !text.contains("{item}")) {
            return text;
        }
        String amount = "0";
        String itemName = "";
        if (hasItem(positionSection)) {
            amount = String.valueOf(Math.max(1, positionSection.getInt("item.amount", 1)));
            String configuredName = positionSection.getString("item.name");
            itemName = configuredName != null && !configuredName.isBlank()
                    ? configuredName
                    : positionSection.getString("item.material", "");
        }
        return text.replace("{amount}", amount).replace("{item}", itemName);
    }

    private void runRewardCommands(Player player, ConfigurationSection positionSection, int position) {
        List<RewardCommand> commands = parseCommands(positionSection);
        if (commands.isEmpty()) {
            return;
        }

        String source = "Reward (" + positionSection.getName() + ")";
        for (RewardCommand entry : commands) {
            String command = CommandPlaceholders.applyReward(entry.command(), player, position);
            if (entry.asPlayer()) {
                CommandRunner.runAsPlayer(plugin.getLogger(), player, command, source);
            } else {
                CommandRunner.runAsConsole(plugin.getLogger(), command, source);
            }
        }
    }

    static List<RewardCommand> parseCommands(ConfigurationSection positionSection) {
        if (positionSection == null) {
            return List.of();
        }
        List<RewardCommand> out = new ArrayList<>();
        for (String raw : ConfigLists.stringLists(positionSection, "commands", "command", "console_commands")) {
            addCommand(out, raw, false);
        }
        for (String raw : ConfigLists.stringList(positionSection, "player_commands")) {
            addCommand(out, raw, true);
        }
        return List.copyOf(out);
    }

    private static void addCommand(List<RewardCommand> out, String raw, boolean defaultAsPlayer) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        boolean asPlayer = defaultAsPlayer;
        String command = raw.trim();
        if (command.regionMatches(true, 0, "player:", 0, 7)) {
            asPlayer = true;
            command = command.substring(7).trim();
        } else if (command.regionMatches(true, 0, "console:", 0, 8)) {
            asPlayer = false;
            command = command.substring(8).trim();
        }
        if (command.isEmpty()) {
            return;
        }
        out.add(new RewardCommand(command, asPlayer));
    }

    record RewardCommand(String command, boolean asPlayer) {
    }

    static String applyPlaceholders(String text, Player player, int position) {
        return CommandPlaceholders.applyReward(text, player, position);
    }
}

