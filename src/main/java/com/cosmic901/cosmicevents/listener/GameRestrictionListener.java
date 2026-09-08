package com.cosmic901.cosmicevents.listener;

import com.cosmic901.cosmicevents.CosmicEvents;
import com.cosmic901.cosmicevents.bridge.BlueArcadeHostExtras;
import com.cosmic901.cosmicevents.gui.VoteGUI;
import com.cosmic901.cosmicevents.service.EventManager;
import com.cosmic901.cosmicevents.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class GameRestrictionListener implements Listener {
    private final CosmicEvents plugin;
    private final EventManager eventManager;
    private BukkitTask movementEnforceTask;

    public GameRestrictionListener(CosmicEvents plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        registerEliminateListener();
        startMovementEnforceTask();
    }

    public void registerEliminateListener() {
        BlueArcadeHostExtras extras = eventManager.getBlueArcade().getHostExtras();
        if (!extras.isAvailable()) {
            extras.initialize();
        }
        extras.registerEliminateListener(this, (listener, event) -> {
            BlueArcadeHostExtras.EliminateData data = extras.readEliminateEvent(event);
            if (data != null && data.player() != null) {
                eventManager.handlePlayerElimination(data.arenaId(), data.player());
            }
        });
    }

    private void startMovementEnforceTask() {
        if (movementEnforceTask != null) {
            movementEnforceTask.cancel();
        }
        movementEnforceTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Set<UUID> checked = new HashSet<>();
            for (UUID uuid : eventManager.getParticipants()) {
                if (checked.add(uuid)) {
                    enforceMovement(uuid);
                }
            }
            for (UUID uuid : eventManager.getActiveParticipants()) {
                if (checked.add(uuid)) {
                    enforceMovement(uuid);
                }
            }
        }, 20L, 20L);
    }

    private void enforceMovement(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline() || EventManager.isExempt(player)) {
            return;
        }
        eventManager.getTracker().enforceNeutralMovement(player);
        if (!eventManager.areMinigameEffectsAllowed()) {
            eventManager.getTracker().stripMovementEffects(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoinServer(PlayerJoinEvent event) {
        eventManager.restoreAfterReconnect(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof VoteGUI.VoteGUIHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player) {
            holder.getGUI().handleClick(event.getRawSlot());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof VoteGUI.VoteGUIHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!eventManager.isInEvent(player.getUniqueId())) {
            return;
        }
        if (EventManager.isExempt(player)) {
            return;
        }
        if (event.isFlying()) {
            event.setCancelled(true);
            player.setAllowFlight(false);
            player.setFlying(false);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (eventManager.getTracker().isAuthorized(player.getUniqueId())) {
            return;
        }

        String raw = event.getMessage();
        List<String> parts = tokenizeCommand(raw);
        if (parts.isEmpty()) {
            return;
        }

        String cmd = stripNamespace(parts.get(0));
        String firstArg = parts.size() > 1 ? parts.get(1).toLowerCase(Locale.ROOT) : "";

        if (eventManager.isInEvent(player.getUniqueId())) {
            if (EventManager.isExempt(player)) {
                return;
            }

            boolean isLeaveCmd = cmd.equals("leave")
                    || (cmd.equals("ba") && firstArg.equals("leave"))
                    || (cmd.equals("bluearcade") && firstArg.equals("leave"));
            if (isLeaveCmd) {
                event.setCancelled(true);
                Messages.send(player, eventManager.formatMessage(plugin.getConfig().getString(
                        "messages.command_blocked_during_game",
                        "&cYou cannot use this command during the event minigame! Use /event leave to exit.")));
                return;
            }

            Set<String> allowed = normalizeCommandNames(
                    plugin.getConfig().getStringList("allowed_commands_during_game"));
            if (allowed.isEmpty()) {
                allowed.addAll(List.of("event", "events", "msg", "tell", "r", "w"));
            }

            boolean isOurCommand = cmd.equals("event") || cmd.equals("events");
            if (!isOurCommand) {
                PluginCommand pluginCmd = Bukkit.getPluginCommand(cmd);
                if (pluginCmd != null && pluginCmd.getPlugin() != null && pluginCmd.getPlugin().equals(plugin)) {
                    isOurCommand = true;
                }
            }

            if (!(isOurCommand || allowed.contains(cmd) || allowed.contains(parts.get(0)))) {
                event.setCancelled(true);
                Messages.send(player, eventManager.formatMessage(plugin.getConfig().getString(
                        "messages.command_blocked_during_game",
                        "&cYou cannot use this command during the event minigame! Use /event leave to exit.")));
            }
            return;
        }

        Set<String> blockedCommands = normalizeCommandNames(plugin.getConfig().getStringList("blocked_commands"));
        if (blockedCommands.contains(cmd) || blockedCommands.contains(parts.get(0))) {
            if (player.hasPermission("cosmicevents.bypass") || player.hasPermission("cosmicevents.admin")) {
                return;
            }
            if (firstArg.equals("join") || firstArg.equals("play")
                    || firstArg.equals("quickjoin") || firstArg.equals("lobby")) {
                event.setCancelled(true);
                Messages.send(player, eventManager.formatMessage(plugin.getConfig().getString(
                        "messages.direct_join_blocked",
                        "&cYou can only join minigames via CosmicEvents!")));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerKick(PlayerKickEvent event) {
        eventManager.leaveOnDisconnect(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        eventManager.leaveOnDisconnect(event.getPlayer());
    }

    static Set<String> normalizeCommandNames(List<String> configured) {
        Set<String> out = new HashSet<>();
        if (configured == null) {
            return out;
        }
        for (String entry : configured) {
            if (entry == null) {
                continue;
            }
            String name = entry.trim().toLowerCase(Locale.ROOT);
            if (name.startsWith("/")) {
                name = name.substring(1);
            }
            if (!name.isEmpty()) {
                out.add(name);
            }
        }
        return out;
    }

    private static List<String> tokenizeCommand(String message) {
        String normalized = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return List.of();
        }
        String[] split = normalized.split("\\s+");
        List<String> parts = new ArrayList<>(split.length);
        for (String part : split) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        return parts;
    }

    private static String stripNamespace(String command) {
        int idx = command.indexOf(':');
        if (idx >= 0 && idx < command.length() - 1) {
            return command.substring(idx + 1);
        }
        return command;
    }
}

