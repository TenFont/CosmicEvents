package com.cosmic901.cosmicevents.command;

import com.cosmic901.cosmicevents.CosmicEvents;
import com.cosmic901.cosmicevents.EventState;
import com.cosmic901.cosmicevents.gui.VoteGUI;
import com.cosmic901.cosmicevents.service.EventManager;
import com.cosmic901.cosmicevents.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class EventCommand implements CommandExecutor, TabCompleter {
    private final CosmicEvents plugin;
    private final EventManager eventManager;

    public EventCommand(CosmicEvents plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.eventManager = eventManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "join" -> {
                if (!(sender instanceof Player player)) {
                    Messages.send(sender, eventManager.formatMessage(msg("messages.only_players",
                            "&cThis command can only be executed by players.")));
                    return true;
                }
                eventManager.joinPlayer(player);
                return true;
            }
            case "leave" -> {
                if (!(sender instanceof Player player)) {
                    Messages.send(sender, eventManager.formatMessage(msg("messages.only_players",
                            "&cThis command can only be executed by players.")));
                    return true;
                }
                eventManager.leavePlayer(player);
                return true;
            }
            case "vote" -> {
                if (!(sender instanceof Player player)) {
                    Messages.send(sender, eventManager.formatMessage(msg("messages.only_players",
                            "&cThis command can only be executed by players.")));
                    return true;
                }
                if (!player.hasPermission("cosmicevents.use") && !player.hasPermission("cosmicevents.admin")) {
                    Messages.send(player, eventManager.formatMessage(msg("messages.no_permission",
                            "&cYou do not have permission to execute this command.")));
                    return true;
                }
                if (eventManager.getVoteService().getConfiguredMinigames().size() <= 1) {
                    Messages.send(player, eventManager.formatMessage(msg("messages.voting_disabled_single",
                            "&cThis event has a fixed minigame — voting is disabled.")));
                    return true;
                }
                if (args.length == 1) {
                    if (!eventManager.ensureCanVote(player)) {
                        return true;
                    }
                    new VoteGUI(plugin, eventManager, player, 0).open();
                } else {
                    eventManager.castVote(player, args[1].toLowerCase());
                }
                return true;
            }
            case "donorstart" -> {
                if (!(sender instanceof Player player)) {
                    Messages.send(sender, eventManager.formatMessage(msg("messages.only_players",
                            "&cThis command can only be executed by players.")));
                    return true;
                }
                eventManager.donorStartEvent(player);
                return true;
            }
            case "start" -> {
                if (!sender.hasPermission("cosmicevents.admin")) {
                    Messages.send(sender, eventManager.formatMessage(msg("messages.no_permission",
                            "&cYou do not have permission to execute this command.")));
                    return true;
                }
                if (eventManager.getState() != EventState.IDLE) {
                    Messages.send(sender, eventManager.formatMessage(msg(
                            "messages.event_already_running", "&cAn event is already running.")));
                    return true;
                }
                eventManager.startEvent();
                Messages.send(sender, eventManager.formatMessage(msg("messages.force_start",
                        "&aManually started the event join phase.")));
                return true;
            }
            case "forcestart", "debugstart" -> {
                if (!sender.hasPermission("cosmicevents.admin")) {
                    Messages.send(sender, eventManager.formatMessage(msg("messages.no_permission",
                            "&cYou do not have permission to execute this command.")));
                    return true;
                }
                int joinSeconds = 15;
                if (args.length >= 2) {
                    try {
                        joinSeconds = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ex) {
                        Messages.send(sender, eventManager.formatMessage(
                                "&cUsage: /event forcestart [join_seconds]"));
                        return true;
                    }
                    if (joinSeconds < 1 || joinSeconds > 600) {
                        Messages.send(sender, eventManager.formatMessage(
                                "&cJoin seconds must be between 1 and 600."));
                        return true;
                    }
                }
                Player autoJoin = sender instanceof Player player ? player : null;
                eventManager.forceStartEvent(autoJoin, joinSeconds);
                Messages.send(sender, eventManager.formatMessage(msg("messages.force_start_debug",
                                "&aForce-started event for debugging (min players bypassed, {time}s join).")
                        .replace("{time}", String.valueOf(joinSeconds))));
                return true;
            }
            case "stop", "cancel" -> {
                if (!sender.hasPermission("cosmicevents.admin")) {
                    Messages.send(sender, eventManager.formatMessage(msg("messages.no_permission",
                            "&cYou do not have permission to execute this command.")));
                    return true;
                }
                if (eventManager.getState() == EventState.IDLE) {
                    Messages.send(sender, eventManager.formatMessage(msg(
                            "messages.no_event_running", "&cThere is no event running right now.")));
                    return true;
                }
                eventManager.forceResetToIdle();
                Messages.send(sender, eventManager.formatMessage(msg("messages.force_stop",
                        "&cForce-stopped the active event.")));
                return true;
            }
            case "next", "time" -> {
                Messages.send(sender, eventManager.formatMessage(msg("messages.next_event_timer",
                                "&7Next auto event in: &e{time}")
                        .replace("{time}", eventManager.getNextEventTimeFormatted())));
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("cosmicevents.admin")) {
                    Messages.send(sender, eventManager.formatMessage(msg("messages.no_permission",
                            "&cYou do not have permission to execute this command.")));
                    return true;
                }
                plugin.reloadConfig();
                plugin.reloadSoundsConfig();
                plugin.reloadVotesPowerConfig();
                plugin.reloadVoteGuiConfig();
                eventManager.reloadAndReschedule();
                Messages.send(sender, eventManager.formatMessage(
                        msg("messages.reload_success", "&aCosmicEvents configuration reloaded.")));
                return true;
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    private String msg(String path, String def) {
        return plugin.getConfig().getString(path, def);
    }

    private boolean canDonorStart(CommandSender sender) {
        return eventManager.getDonorStartService().isEnabled()
                && (sender.hasPermission(eventManager.getDonorStartService().getPermission())
                || sender.hasPermission("cosmicevents.admin"));
    }

    private void sendHelp(CommandSender sender) {
        List<String> help = plugin.getConfig().getStringList("messages.help");
        if (help.isEmpty()) {
            help = List.of(
                    "&6&lCosmicEvents",
                    "&e/event join &7- Enter the active event",
                    "&e/event leave &7- Leave the event",
                    "&e/event vote &7- Vote for a game",
                    "&e/event time &7- Show the next automatic event");
        }
        for (String line : help) {
            Messages.send(sender, line);
        }
        if (canDonorStart(sender)) {
            List<String> donorHelp = plugin.getConfig().getStringList("messages.donor_help");
            if (donorHelp.isEmpty()) {
                donorHelp = List.of("&e/event donorstart &7- Start an event yourself");
            }
            for (String line : donorHelp) {
                Messages.send(sender, line);
            }
        }
        if (sender.hasPermission("cosmicevents.admin")) {
            List<String> adminHelp = plugin.getConfig().getStringList("messages.admin_help");
            if (adminHelp.isEmpty()) {
                adminHelp = List.of(
                        "&6Admin commands",
                        "&e/event start &7- Open event entry",
                        "&e/event forcestart [seconds] &7- Test an event",
                        "&e/event stop &7- Stop the active event",
                        "&e/event reload &7- Reload all settings");
            }
            for (String line : adminHelp) {
                Messages.send(sender, line);
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>(Arrays.asList("join", "leave", "vote", "time", "next"));
            if (canDonorStart(sender)) {
                subcommands.add("donorstart");
            }
            if (sender.hasPermission("cosmicevents.admin")) {
                subcommands.add("start");
                subcommands.add("forcestart");
                subcommands.add("stop");
                subcommands.add("reload");
            }
            return subcommands.stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("vote")) {
            return eventManager.getVoteService().getConfiguredMinigames().stream()
                    .filter(game -> game.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}

