package com.cosmic901.cosmicevents.service;

import com.cosmic901.cosmicevents.util.ConfigLists;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardServiceTest {

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(text);
        return config;
    }

    @Test
    void commandsListIsRead() throws Exception {
        YamlConfiguration config = yaml("""
                first:
                  commands:
                    - "tellraw {player} you won"
                    - "player: warp spawn"
                    - "console: say hi"
                """);
        List<RewardService.RewardCommand> commands =
                RewardService.parseCommands(config.getConfigurationSection("first"));
        assertEquals(3, commands.size());
        assertEquals("tellraw {player} you won", commands.get(0).command());
        assertFalse(commands.get(0).asPlayer());
        assertEquals("warp spawn", commands.get(1).command());
        assertTrue(commands.get(1).asPlayer());
        assertEquals("say hi", commands.get(2).command());
        assertFalse(commands.get(2).asPlayer());
    }

    @Test
    void singleStringCommandsIsNotDropped() throws Exception {
        YamlConfiguration config = yaml("""
                first:
                  commands: "tellraw {player} you won"
                """);
        List<RewardService.RewardCommand> commands =
                RewardService.parseCommands(config.getConfigurationSection("first"));
        assertEquals(List.of(new RewardService.RewardCommand("tellraw {player} you won", false)), commands);
    }

    @Test
    void legacySingularCommandKeyStillWorks() throws Exception {
        YamlConfiguration config = yaml("""
                first:
                  message: "&aWinner!"
                  command: etokens give {player} 1
                """);
        List<RewardService.RewardCommand> commands =
                RewardService.parseCommands(config.getConfigurationSection("first"));
        assertEquals(List.of(new RewardService.RewardCommand("etokens give {player} 1", false)), commands);
    }

    @Test
    void playerCommandsKeyRunsAsPlayer() throws Exception {
        YamlConfiguration config = yaml("""
                first:
                  console_commands:
                    - "lp user {player} permission set x true"
                  player_commands:
                    - "spawn"
                    - "console: broadcast forced console"
                """);
        List<RewardService.RewardCommand> commands =
                RewardService.parseCommands(config.getConfigurationSection("first"));
        assertEquals(3, commands.size());
        assertFalse(commands.get(0).asPlayer());
        assertTrue(commands.get(1).asPlayer());
        assertFalse(commands.get(2).asPlayer());
        assertEquals("broadcast forced console", commands.get(2).command());
    }

    @Test
    void blankAndMissingEntriesAreIgnored() throws Exception {
        YamlConfiguration config = yaml("""
                first:
                  commands:
                    - ""
                    - "   "
                    - "player:"
                second:
                  item:
                    material: IRON_INGOT
                """);
        assertTrue(RewardService.parseCommands(config.getConfigurationSection("first")).isEmpty());
        assertTrue(RewardService.parseCommands(config.getConfigurationSection("second")).isEmpty());
        assertTrue(RewardService.parseCommands(null).isEmpty());
    }

    @Test
    void itemPlaceholdersFillFromConfiguredItem() throws Exception {
        YamlConfiguration config = yaml("""
                first:
                  message: "You got {amount}x {item}!"
                  item:
                    material: GOLD_INGOT
                    amount: 3
                    name: "&6Ticket"
                second:
                  message: "You got {amount}x {item}!"
                  item:
                    material: IRON_INGOT
                third:
                  message: "No item here {amount}"
                """);
        assertEquals("You got 3x &6Ticket!", RewardService.applyItemPlaceholders(
                "You got {amount}x {item}!", config.getConfigurationSection("first")));
        assertEquals("You got 1x IRON_INGOT!", RewardService.applyItemPlaceholders(
                "You got {amount}x {item}!", config.getConfigurationSection("second")));
        assertEquals("No item here 0", RewardService.applyItemPlaceholders(
                "No item here {amount}", config.getConfigurationSection("third")));
        assertEquals("plain", RewardService.applyItemPlaceholders(
                "plain", config.getConfigurationSection("third")));
    }

    @Test
    void configListsAcceptsListOrScalar() throws Exception {
        YamlConfiguration config = yaml("""
                as_list:
                  - one
                  - two
                as_string: "just one"
                as_number: 5
                empty_list: []
                nested:
                  key: value
                """);
        assertEquals(List.of("one", "two"), ConfigLists.stringList(config, "as_list"));
        assertEquals(List.of("just one"), ConfigLists.stringList(config, "as_string"));
        assertEquals(List.of("5"), ConfigLists.stringList(config, "as_number"));
        assertTrue(ConfigLists.stringList(config, "empty_list").isEmpty());
        assertTrue(ConfigLists.stringList(config, "nested").isEmpty());
        assertTrue(ConfigLists.stringList(config, "missing").isEmpty());
        assertEquals(List.of("one", "two", "just one"),
                ConfigLists.stringLists(config, "as_list", "missing", "as_string"));
    }
}
