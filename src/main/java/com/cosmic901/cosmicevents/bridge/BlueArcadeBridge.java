package com.cosmic901.cosmicevents.bridge;

import net.blueva.arcade.api.ModuleAPI;
import net.blueva.arcade.api.utils.PlayerUtil;
import com.cosmic901.cosmicevents.util.CommandPlaceholders;
import com.cosmic901.cosmicevents.util.CommandRunner;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

public final class BlueArcadeBridge {
    private final Plugin plugin;
    private final Logger logger;
    private final BlueArcadeHostExtras hostExtras;

    private boolean available;
    private String unavailableReason = "Not initialized";
    private PlayerUtil<Player> playerUtil;

    public BlueArcadeBridge(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.hostExtras = new BlueArcadeHostExtras(plugin);
    }

    @SuppressWarnings("unchecked")
    public boolean initialize() {
        available = false;
        playerUtil = null;
        unavailableReason = "BlueArcade not found";

        Plugin baPlugin = findBlueArcadePlugin();
        if (baPlugin == null || !baPlugin.isEnabled()) {
            logger.severe("BlueArcade is missing or disabled.");
            return false;
        }

        PlayerUtil<?> util = ModuleAPI.getPlayerUtil();
        if (util == null) {
            unavailableReason = "ModuleAPI provider not ready yet";
            logger.warning("BlueArcade is enabled but ModuleAPI.getPlayerUtil() is null. "
                    + "Will retry shortly...");
            return false;
        }

        playerUtil = (PlayerUtil<Player>) util;
        available = true;
        unavailableReason = null;

        if (!hostExtras.isAvailable()) {
            hostExtras.initialize();
        }
        return true;
    }

    public BlueArcadeHostExtras getHostExtras() {
        return hostExtras;
    }

    public static Plugin findBlueArcadePlugin() {
        Plugin ba = Bukkit.getPluginManager().getPlugin("BlueArcade");
        if (ba == null) {
            ba = Bukkit.getPluginManager().getPlugin("BlueArcade3");
        }
        return ba;
    }

    public boolean isAvailable() {
        return available && playerUtil != null;
    }

    public String getUnavailableReason() {
        return unavailableReason;
    }

    public PlayerUtil<Player> getPlayerUtil() {
        return playerUtil;
    }


    public boolean joinArena(Player player, int arenaId, String commandFormat) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        ensureHostExtras();

        if (hostExtras.isAvailable()) {
            boolean joined = hostExtras.joinPlayer(player, arenaId);
            if (joined) {
                return true;
            }
            logger.warning("BA API join failed for " + player.getName()
                    + " (arena state=" + hostExtras.getArenaStateName(arenaId)
                    + "); trying command fallback...");
        }

        String format = commandFormat == null || commandFormat.isBlank()
                ? "ba join {arena}"
                : commandFormat;
        String command = format
                .replace("{arena}", String.valueOf(arenaId))
                .replace("{player}", player.getName());
        return dispatchAsPlayer(player, CommandPlaceholders.stripLeadingSlash(command));
    }


    public boolean leaveArena(Player player, String leaveCommand) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        if (isAvailable()
                && !playerUtil.isInGame(player)
                && !playerUtil.isInWaitingArena(player)
                && playerUtil.getPlayerArena(player) == null) {
            return true;
        }

        ensureHostExtras();

        Integer arenaId = getPlayerArena(player);
        if (hostExtras.isAvailable() && arenaId != null) {
            if (hostExtras.leavePlayer(player, arenaId)) {
                return true;
            }
        }

        String command = leaveCommand == null || leaveCommand.isBlank()
                ? "ba leave confirm"
                : leaveCommand.replace("{player}", player.getName());
        return dispatchAsPlayer(player, CommandPlaceholders.stripLeadingSlash(command));
    }

    public boolean isArenaJoinable(int arenaId) {
        ensureHostExtras();
        return hostExtras.isAvailable() && hostExtras.isArenaJoinable(arenaId);
    }

    public String describeArena(int arenaId) {
        ensureHostExtras();
        if (!hostExtras.isAvailable()) {
            return "extras-unavailable";
        }
        return hostExtras.getArenaStateName(arenaId);
    }

    private void ensureHostExtras() {
        if (!hostExtras.isAvailable()) {
            hostExtras.initialize();
        }
    }


    private boolean dispatchAsPlayer(Player player, String command) {
        String trimmed = command == null ? "" : command.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        String root = trimmed.split("\\s+")[0];
        if (Bukkit.getCommandMap().getCommand(root) == null
                && Bukkit.getPluginCommand(root) == null) {

            if (root.equalsIgnoreCase("ba") || root.equalsIgnoreCase("bluearcade")
                    || root.equalsIgnoreCase("barcade")) {
                if (Bukkit.getPluginCommand("arcade") != null) {
                    trimmed = "arcade" + trimmed.substring(root.length());
                    root = "arcade";
                }
            }
        }
        if (Bukkit.getCommandMap().getCommand(root) == null
                && Bukkit.getPluginCommand(root) == null) {
            logger.warning("Leave/join command '" + root
                    + "' is not registered. Check BlueArcade is loaded.");
            return false;
        }
        return player.performCommand(trimmed);
    }

    public boolean isInGame(Player player) {
        return isAvailable() && player != null && playerUtil.isInGame(player);
    }

    public boolean isInWaitingArena(Player player) {
        return isAvailable() && player != null && playerUtil.isInWaitingArena(player);
    }

    public Integer getPlayerArena(Player player) {
        if (!isAvailable() || player == null) {
            return null;
        }
        return playerUtil.getPlayerArena(player);
    }

    public String getInGameStatus(Player player) {
        if (!isAvailable() || player == null) {
            return null;
        }
        return playerUtil.getPlayerInGameStatus(player);
    }

    public boolean isEliminatedStatus(Player player) {
        String status = getInGameStatus(player);
        if (status == null) {
            return false;
        }
        String normalized = status.toLowerCase(Locale.ROOT);
        return normalized.contains("spectator")
                || normalized.contains("eliminat")
                || normalized.contains("dead")
                || normalized.equals("spec");
    }

    public boolean isArenaOccupied(int arenaId, Iterable<Player> players) {
        if (!isAvailable()) {
            return false;
        }
        for (Player player : players) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            Integer arena = playerUtil.getPlayerArena(player);
            if (arena != null && arena == arenaId) {
                return true;
            }
            if (playerUtil.isInGame(player) || playerUtil.isInWaitingArena(player)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAnyPlayerStarted(int arenaId, Iterable<Player> players) {
        if (!isAvailable()) {
            return false;
        }
        for (Player player : players) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            Integer arena = playerUtil.getPlayerArena(player);
            if (arena != null && arena != arenaId) {
                continue;
            }
            if (playerUtil.isInGame(player) && !playerUtil.isInWaitingArena(player)) {
                return true;
            }

            if (playerUtil.isInGame(player)) {
                String status = playerUtil.getPlayerInGameStatus(player);
                if (status != null && status.toLowerCase(Locale.ROOT).contains("play")) {
                    return true;
                }
            }
        }
        return false;
    }

    public void runConsoleCommands(List<String> commands, int arenaId, String gameId) {
        runConsoleCommands(commands, List.of(), arenaId, gameId);
    }

    public void runConsoleCommands(List<String> commands, List<Player> players, int arenaId, String gameId) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        for (String raw : commands) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            if (CommandPlaceholders.needsPlayer(raw)) {
                if (players == null || players.isEmpty()) {
                    logger.warning("Skipping console command that needs a player placeholder: " + raw);
                    continue;
                }
                for (Player player : players) {
                    if (player == null || !player.isOnline()) {
                        continue;
                    }
                    CommandRunner.runAsConsole(logger,
                            CommandPlaceholders.apply(raw, player, arenaId, gameId),
                            "Post-join console");
                }
                continue;
            }
            CommandRunner.runAsConsole(logger,
                    CommandPlaceholders.apply(raw, null, arenaId, gameId),
                    "Post-join console");
        }
    }

    public void runPlayerCommands(Player player, List<String> commands, int arenaId, String gameId) {
        if (player == null || commands == null || commands.isEmpty()) {
            return;
        }
        for (String raw : commands) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            CommandRunner.runAsPlayer(logger, player,
                    CommandPlaceholders.apply(raw, player, arenaId, gameId),
                    "Post-join player");
        }
    }
}

