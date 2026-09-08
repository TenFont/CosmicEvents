package com.cosmic901.cosmicevents.service;

import com.cosmic901.cosmicevents.CosmicEvents;
import com.cosmic901.cosmicevents.EventState;
import com.cosmic901.cosmicevents.bridge.BlueArcadeBridge;
import com.cosmic901.cosmicevents.util.ConfigLists;
import com.cosmic901.cosmicevents.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class EventManager {
    static final long REWARD_DELAY_TICKS = InventorySnapshotService.RESTORE_CONSUME_DELAY_TICKS;

    private final CosmicEvents plugin;
    private final VoteService voteService;
    private final RewardService rewardService;
    private final EventScheduler scheduler;
    private final DonorStartService donorStart;
    private final WinStatsService winStats;
    private final ParticipantTracker tracker;
    private final InventorySnapshotService inventorySnapshots;
    private final JoinStateService joinStates;
    private final BlueArcadeBridge blueArcade;
    private final Random random = new Random();

    private EventState state = EventState.IDLE;
    private int joinDurationSeconds = 60;
    private int minPlayers = 2;
    private int maxPlayers = 0;
    private int joinTimeLeftSeconds;
    private int authorizationTicks = 40;
    private int gameTimeoutMinutes = 30;
    private int gameStartTimeoutSeconds = 10;
    private int directStartPollTicks = 2;
    private int directStartTimeoutSeconds = 10;
    private int joinStaggerTicks = 2;
    private int playersPerJoinTick = 1;
    private int joinCloseGraceTicks = 5;
    private int arenaReadyTimeoutSeconds = 60;
    private int arenaReadyPollTicks = 10;
    private boolean bypassMinPlayers;
    private String joinCommandFormat = "ba join {arena}";
    private String leaveCommand = "ba leave confirm";
    private List<String> postJoinConsoleCommands = List.of();
    private List<String> postJoinPlayerCommands = List.of();
    private List<String> arenas = List.of();
    private List<String> allowedWorlds = List.of();
    private String activeMinigameId;
    private int activeArenaId = -1;

    private BukkitTask countdownTask;
    private BukkitTask gameTimeoutTask;
    private BukkitTask gameMonitorTask;
    private BukkitTask joinStaggerTask;
    private BukkitTask joinCloseTask;
    private BukkitTask postLaunchClearTask;
    private int eventGeneration;
    private boolean minigameEffectsAllowed;

    public EventManager(CosmicEvents plugin, BlueArcadeBridge blueArcade) {
        this.plugin = plugin;
        this.blueArcade = blueArcade;
        this.voteService = new VoteService();
        this.rewardService = new RewardService(plugin);
        this.scheduler = new EventScheduler(plugin);
        this.donorStart = new DonorStartService(plugin);
        this.winStats = new WinStatsService(plugin);
        this.tracker = new ParticipantTracker();
        this.inventorySnapshots = new InventorySnapshotService(plugin);
        this.joinStates = new JoinStateService(plugin);
        loadConfigValues();
        Bukkit.getScheduler().runTask(plugin, () -> {
            inventorySnapshots.restoreAllOnline(true);
            joinStates.restoreAllOnline(true);
        });
        scheduler.configure(
                plugin.getConfig().getBoolean("enable_scheduler", true),
                plugin.getConfig().getDouble("event_interval_hours", 2.0),
                ignored -> {
                    if (state == EventState.IDLE) {
                        startEvent();
                    } else {
                        scheduler.scheduleNext();
                    }
                }
        );
        if (scheduler.isEnabled()) {
            scheduler.scheduleNext();
        }
    }

    public void loadConfigValues() {
        joinDurationSeconds = plugin.getConfig().getInt("join_duration_seconds", 60);
        minPlayers = plugin.getConfig().getInt("min_players", 2);
        maxPlayers = plugin.getConfig().getInt("max_players", 0);
        authorizationTicks = plugin.getConfig().getInt("authorization_ticks", 40);
        gameTimeoutMinutes = plugin.getConfig().getInt("game_timeout_minutes", 30);
        gameStartTimeoutSeconds = Math.max(3,
                plugin.getConfig().getInt("direct_start_confirmation_timeout_seconds", 10));
        directStartPollTicks = Math.max(1, plugin.getConfig().getInt("direct_start_poll_ticks",
                plugin.getConfig().getInt("skip_lobby_delay_ticks", 2)));
        directStartTimeoutSeconds = Math.max(3,
                plugin.getConfig().getInt("direct_start_timeout_seconds", 10));
        joinStaggerTicks = Math.max(1, plugin.getConfig().getInt("join_stagger_ticks", 1));
        playersPerJoinTick = Math.max(1, plugin.getConfig().getInt("players_per_join_tick", 64));
        joinCloseGraceTicks = Math.max(0, plugin.getConfig().getInt("join_close_grace_ticks", 5));
        arenaReadyTimeoutSeconds = Math.max(5, plugin.getConfig().getInt("arena_ready_timeout_seconds", 60));
        arenaReadyPollTicks = Math.max(5, plugin.getConfig().getInt("arena_ready_poll_ticks", 10));
        joinCommandFormat = plugin.getConfig().getString("join_command", "ba join {arena}");
        leaveCommand = plugin.getConfig().getString("leave_command", "ba leave confirm");
        postJoinConsoleCommands = ConfigLists.stringList(plugin.getConfig(), "post_join_console_commands");
        postJoinPlayerCommands = ConfigLists.stringList(plugin.getConfig(), "post_join_player_commands");
        arenas = sanitizeArenaIds(plugin.getConfig().getStringList("arenas"));
        allowedWorlds = List.copyOf(plugin.getConfig().getStringList("allowed_worlds"));
        inventorySnapshots.setEnabled(plugin.getConfig().getBoolean("restore_inventory", true));

        List<String> minigames = plugin.getConfig().getStringList("minigames");
        if (minigames.isEmpty()) {
            minigames = List.of(
                    "all_against_all", "block_party", "bridge_race", "chairs",
                    "exploding_sheep", "fast_zone", "knockback", "minefield",
                    "one_in_the_chamber", "race", "red_alert", "run_from_the_beast",
                    "snowball_fight", "spleef", "splegg", "tnt_run", "tnt_tag",
                    "traffic_light", "water_well");
        }
        boolean opsAlwaysUseOpWeight = plugin.getVotesPowerConfig().getBoolean("ops_always_use_op_weight", true);
        voteService.reload(minigames, plugin.getVotesPowerConfig(), opsAlwaysUseOpWeight);
        rewardService.reload(plugin.getConfig());
        donorStart.reload(plugin.getConfig());
        winStats.load();

        boolean enableScheduler = plugin.getConfig().getBoolean("enable_scheduler", true);
        double intervalHours = plugin.getConfig().getDouble("event_interval_hours", 2.0);
        scheduler.configure(enableScheduler, intervalHours, ignored -> {
            if (state == EventState.IDLE) {
                startEvent();
            } else {
                scheduler.scheduleNext();
            }
        });
    }

    public void reloadAndReschedule() {
        loadConfigValues();
        if (state == EventState.IDLE) {
            if (scheduler.isEnabled()) {
                scheduler.scheduleNext();
            } else {
                scheduler.cancel();
            }
        }
    }

    public VoteService getVoteService() {
        return voteService;
    }

    public RewardService getRewardService() {
        return rewardService;
    }

    public DonorStartService getDonorStartService() {
        return donorStart;
    }

    public WinStatsService getWinStatsService() {
        return winStats;
    }

    public ParticipantTracker getTracker() {
        return tracker;
    }

    public InventorySnapshotService getInventorySnapshots() {
        return inventorySnapshots;
    }

    public JoinStateService getJoinStates() {
        return joinStates;
    }

    public BlueArcadeBridge getBlueArcade() {
        return blueArcade;
    }

    public void startEvent() {
        startEvent(joinDurationSeconds, false);
    }


    public void forceStartEvent(Player autoJoin, int joinSeconds) {
        if (state != EventState.IDLE) {
            forceResetToIdle();
        }
        startEvent(Math.max(1, joinSeconds), true);
        if (autoJoin != null && autoJoin.isOnline()) {
            joinPlayer(autoJoin);
        }
    }


    public boolean donorStartEvent(Player player) {
        if (!donorStart.isEnabled()) {
            Messages.send(player, formatMessage(plugin.getConfig().getString(
                    "messages.donor_start_disabled", "&cDonor event starting is disabled.")));
            return false;
        }

        boolean admin = player.hasPermission("cosmicevents.admin");
        if (!admin && !player.hasPermission(donorStart.getPermission())) {
            Messages.send(player, formatMessage(plugin.getConfig().getString(
                    "messages.no_permission", "&cYou do not have permission to execute this command.")));
            return false;
        }

        if (state != EventState.IDLE) {
            Messages.send(player, formatMessage(plugin.getConfig().getString(
                    "messages.donor_start_event_running",
                    "&cAn event is already running — you cannot start another one right now.")));
            return false;
        }

        long remaining = admin ? 0L : donorStart.getRemainingCooldownMillis(player.getUniqueId());
        if (remaining > 0L) {
            Messages.send(player, formatMessage(plugin.getConfig().getString(
                            "messages.donor_start_cooldown",
                            "&cYou can start another event in &e{time}&c.")
                    .replace("{time}", formatDuration(remaining))));
            return false;
        }

        int joinSeconds = donorStart.getJoinDurationSeconds() > 0
                ? donorStart.getJoinDurationSeconds()
                : joinDurationSeconds;
        startEvent(joinSeconds, false);
        if (state == EventState.IDLE) {
            Messages.send(player, formatMessage(plugin.getConfig().getString(
                    "messages.donor_start_failed", "&cThe event could not be started. Try again shortly.")));
            return false;
        }

        if (!admin) {
            donorStart.markUsed(player.getUniqueId());
        }
        Messages.send(player, formatMessage(plugin.getConfig().getString(
                "messages.donor_start_success", "&aYou started the event!")));


        List<String> broadcast = plugin.getConfig().getStringList("messages.donor_start_broadcast");
        if (broadcast.isEmpty() && !plugin.getConfig().contains("messages.donor_start_broadcast")) {
            broadcast = List.of("&e{player} &astarted an event! Type &b/event join &ato enter.");
        }
        for (String msg : broadcast) {
            Messages.broadcast(formatMessage(msg.replace("{player}", player.getName())));
        }
        if (donorStart.isAutoJoin()) {
            joinPlayer(player);
        }
        return true;
    }

    public void startEvent(int joinSeconds, boolean bypassMinPlayerRequirement) {
        if (state != EventState.IDLE) {
            return;
        }
        eventGeneration++;
        cancelPostLaunchClear();
        bypassMinPlayers = bypassMinPlayerRequirement;
        state = EventState.JOINING;
        tracker.participants().clear();
        voteService.clear();
        joinTimeLeftSeconds = Math.max(1, joinSeconds);
        plugin.getSounds().playToAll("event_start");

        List<String> announcement = plugin.getConfig().getStringList("messages.event_announcement");
        for (String msg : announcement) {
            Messages.broadcast(msg.replace("{time}", String.valueOf(joinTimeLeftSeconds)));
        }
        startCountdownTask();
    }

    private void startCountdownTask() {
        cancelCountdown();
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            joinTimeLeftSeconds--;
            if (joinTimeLeftSeconds <= 0) {
                joinTimeLeftSeconds = 0;
                cancelCountdown();
                scheduleJoinClose();
                return;
            }
            if (joinTimeLeftSeconds == 30
                    || joinTimeLeftSeconds == 15
                    || joinTimeLeftSeconds == 10
                    || (joinTimeLeftSeconds <= 5 && joinTimeLeftSeconds > 0)) {
                String format = plugin.getConfig().getString(
                        "messages.event_countdown",
                        "&7Event starting in &e{time} &7seconds! Type &b/event join &7to enter.");
                Messages.broadcast(formatMessage(format.replace("{time}", String.valueOf(joinTimeLeftSeconds))));
                plugin.getSounds().playToAll("event_countdown");
            }
        }, 20L, 20L);
    }

    private void scheduleJoinClose() {
        cancelJoinClose();
        if (joinCloseGraceTicks <= 0) {
            endJoiningPhase();
            return;
        }
        joinCloseTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            joinCloseTask = null;
            if (state == EventState.JOINING) {
                endJoiningPhase();
            }
        }, joinCloseGraceTicks);
    }

    private void snapshotJoinInventory(Player player) {
        try {
            player.closeInventory();
        } catch (Throwable ignored) {
        }
        joinStates.captureIfAbsent(player);
        inventorySnapshots.captureIfAbsent(player);
    }

    boolean acceptsJoin() {
        if (state == EventState.JOINING) {
            return true;
        }
        return acceptsJoin(state, minigameEffectsAllowed, activeArenaId)
                && blueArcade.isArenaJoinable(activeArenaId);
    }

    static boolean acceptsJoin(EventState state, boolean minigameEffectsAllowed, int activeArenaId) {
        if (state == EventState.JOINING) {
            return true;
        }
        return !minigameEffectsAllowed
                && state == EventState.GAME_ACTIVE
                && activeArenaId >= 0;
    }

    private int currentPlayerCount() {
        Set<UUID> current = new HashSet<>(tracker.participants());
        current.addAll(tracker.activeParticipants());
        return current.size();
    }

    private void endJoiningPhase() {
        int requiredPlayers = bypassMinPlayers ? 1 : minPlayers;
        if (tracker.participants().size() < requiredPlayers) {
            plugin.getSounds().playToPlayers(tracker.participants(), "event_cancelled");
            String format = plugin.getConfig().getString(
                    "messages.event_cancelled_low_players",
                    "&cEvent cancelled due to lack of players (min {min_players}).");
            Messages.broadcast(formatMessage(format.replace("{min_players}", String.valueOf(requiredPlayers))));
            returnToIdleAndSchedule();
            return;
        }

        Messages.sendBroadcastTo(tracker.participants(), formatMessage(
                plugin.getConfig().getString("messages.entry_closed",
                        "&aEntry closed. Starting the selected game now...")));

        String selectedGame = voteService.determineWinner(Bukkit::getPlayer);
        if (selectedGame == null) {
            Messages.sendBroadcastTo(tracker.participants(),
                    formatMessage(plugin.getConfig().getString("messages.no_minigames",
                            "&cNo event games are configured.")));
            returnToIdleAndSchedule();
            return;
        }
        launchMinigame(selectedGame);
    }

    private void launchMinigame(String gameId) {
        state = EventState.STARTING_GAME;
        activeMinigameId = gameId;

        if (!blueArcade.isAvailable() && !blueArcade.initialize()) {
            Messages.sendBroadcastTo(tracker.participants(), formatMessage(
                    plugin.getConfig().getString("messages.bluearcade_unavailable",
                            "&cThe game service is unavailable. Please try again shortly.")));
            plugin.getLogger().severe("Aborting launch — " + blueArcade.getUnavailableReason());
            returnToIdleAndSchedule();
            return;
        }
        var extras = blueArcade.getHostExtras();
        if (!extras.isAvailable() && !extras.initialize()) {
            Messages.sendBroadcastTo(tracker.participants(), formatMessage(
                    plugin.getConfig().getString("messages.direct_start_unavailable",
                            "&cThe game could not start. Please try again shortly.")));
            plugin.getLogger().severe("Direct BlueArcade start unavailable: "
                    + extras.getUnavailableReason());
            returnToIdleAndSchedule();
            return;
        }

        if (arenas.isEmpty()) {
            Messages.sendBroadcastTo(tracker.participants(), formatMessage(
                    plugin.getConfig().getString("messages.no_arenas_available", "&cNo arenas configured!")
                            .replace("{minigame}", gameId)));
            returnToIdleAndSchedule();
            return;
        }


        String chosenArena = arenas.size() == 1
                ? arenas.get(0)
                : arenas.get(random.nextInt(arenas.size()));
        int arenaId;
        try {
            arenaId = Integer.parseInt(chosenArena);
        } catch (NumberFormatException ex) {
            plugin.getLogger().severe("Configured arena '" + chosenArena + "' is not a valid integer ID!");
            Messages.sendBroadcastTo(tracker.participants(), formatMessage(
                    plugin.getConfig().getString("messages.invalid_arena",
                            "&cThe event arena is configured incorrectly.")));
            returnToIdleAndSchedule();
            return;
        }

        Messages.sendBroadcastTo(tracker.participants(), formatMessage(
                plugin.getConfig().getString("messages.game_selected",
                                "&aSelected game: &e{minigame}&a. Connecting now...")
                        .replace("{minigame}", gameId)));

        List<UUID> joinQueue = new ArrayList<>(tracker.participants());
        tracker.activeParticipants().clear();
        tracker.activeParticipants().addAll(joinQueue);
        tracker.aliveParticipants().clear();
        tracker.eliminatedParticipants().clear();
        activeArenaId = arenaId;
        state = EventState.GAME_ACTIVE;
        minigameEffectsAllowed = false;
        startGameTimeout();


        extras.enforceSingleRound(arenaId);

        plugin.getSounds().playToPlayers(joinQueue, "event_game_launch");

        waitForArenaThenJoin(joinQueue, arenaId, gameId);

        cancelPostLaunchClear();
        final int generation = eventGeneration;
        postLaunchClearTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            postLaunchClearTask = null;
            if (generation != eventGeneration) {
                return;
            }
            if (state == EventState.GAME_ACTIVE || state == EventState.STARTING_GAME) {
                tracker.participants().clear();
                voteService.clear();
            }
        }, 120L);
    }


    private void waitForArenaThenJoin(List<UUID> joinQueue, int arenaId, String gameId) {
        cancelJoinStagger();

        var extras = blueArcade.getHostExtras();
        if (!extras.isAvailable()) {
            extras.initialize();
        }


        if (!extras.isAvailable() || extras.isArenaJoinable(arenaId)) {
            beginStaggeredArenaJoin(joinQueue, arenaId, gameId);
            return;
        }

        Messages.sendBroadcastTo(joinQueue, formatMessage(
                plugin.getConfig().getString("messages.arena_restarting",
                        "&eThe game arena is resetting. Please wait...")));

        final int[] waitedTicks = {0};
        final int maxTicks = arenaReadyTimeoutSeconds * 20;
        joinStaggerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != EventState.GAME_ACTIVE || activeArenaId != arenaId) {
                cancelJoinStagger();
                return;
            }
            waitedTicks[0] += arenaReadyPollTicks;

            if (blueArcade.isArenaJoinable(arenaId)) {
                cancelJoinStagger();
                beginStaggeredArenaJoin(joinQueue, arenaId, gameId);
                return;
            }

            if (waitedTicks[0] >= maxTicks) {
                plugin.getLogger().severe("Arena " + arenaId
                        + " still not joinable after " + arenaReadyTimeoutSeconds
                        + "s (state=" + blueArcade.describeArena(arenaId) + "). Cancelling.");
                Messages.sendBroadcastTo(joinQueue, formatMessage(
                        plugin.getConfig().getString("messages.arena_restart_timeout",
                                "&cThe arena was not ready in time. You have been returned.")));
                cancelJoinStagger();
                forceResetToIdle();
            }
        }, arenaReadyPollTicks, arenaReadyPollTicks);
    }

    private void beginStaggeredArenaJoin(List<UUID> joinQueue, int arenaId, String gameId) {
        cancelJoinStagger();
        if (joinQueue.isEmpty()) {
            waitForPlayersAndStartGame(arenaId, gameId);
            return;
        }

        final int[] index = {0};
        joinStaggerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != EventState.GAME_ACTIVE || activeArenaId != arenaId) {
                cancelJoinStagger();
                return;
            }

            int joinedThisTick = 0;
            while (joinedThisTick < playersPerJoinTick && index[0] < joinQueue.size()) {
                UUID uuid = joinQueue.get(index[0]++);
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    tracker.saveJoinSnapshot(player);
                    snapshotJoinInventory(player);
                    sendPlayerToArena(player, arenaId, gameId);
                }
                joinedThisTick++;
            }

            if (index[0] >= joinQueue.size()) {
                cancelJoinStagger();
                waitForPlayersAndStartGame(arenaId, gameId);
            }
        }, 1L, joinStaggerTicks);
    }

    private void sendPlayerToArena(Player player, int arenaId, String gameId) {
        if (!isExempt(player)) {
            tracker.saveAndNeutralizeMovement(player);
            tracker.stripMovementEffects(player);
        }
        authorizeTemporarily(player.getUniqueId());

        if (!blueArcade.joinArena(player, arenaId, joinCommandFormat)) {
            plugin.getLogger().warning("Initial BlueArcade join failed for "
                    + player.getName() + " in arena " + arenaId + ".");
        }
        inventorySnapshots.clearLiveIfSnapshotted(player);
    }


    private void waitForPlayersAndStartGame(int arenaId, String gameId) {
        cancelJoinStagger();
        final int[] waitedTicks = {0};
        final int timeoutTicks = directStartTimeoutSeconds * 20;
        joinStaggerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != EventState.GAME_ACTIVE || activeArenaId != arenaId) {
                cancelJoinStagger();
                return;
            }

            int onlineCount = 0;
            int inArenaCount = 0;
            for (Player player : currentActivePlayers()) {
                onlineCount++;
                Integer current = blueArcade.getPlayerArena(player);
                if (current != null && current == arenaId) {
                    inArenaCount++;
                    continue;
                }
                authorizeTemporarily(player.getUniqueId());
                blueArcade.joinArena(player, arenaId, joinCommandFormat);
            }

            if (onlineCount > 0 && inArenaCount == onlineCount) {
                cancelJoinStagger();
                List<Player> online = currentActivePlayers();
                stripDonorMovement(online);
                for (Player player : online) {
                    blueArcade.runPlayerCommands(player, postJoinPlayerCommands, arenaId, gameId);
                }
                blueArcade.runConsoleCommands(postJoinConsoleCommands, online, arenaId, gameId);
                if (!blueArcade.getHostExtras().selectGameAndSkipLobby(arenaId, gameId)) {
                    Messages.sendBroadcastTo(tracker.activeParticipants(), formatMessage(
                            plugin.getConfig().getString("messages.direct_start_failed",
                                    "&cThe game failed to start. You have been returned.")));
                    forceResetToIdle();
                } else {
                    minigameEffectsAllowed = true;
                    startMonitoringGame(arenaId);
                }
                return;
            }

            waitedTicks[0] += directStartPollTicks;
            if (waitedTicks[0] >= timeoutTicks) {
                plugin.getLogger().severe("Only " + inArenaCount + "/" + onlineCount
                        + " online participants entered BlueArcade arena " + arenaId
                        + " within " + directStartTimeoutSeconds + "s. Cancelling.");
                Messages.sendBroadcastTo(tracker.activeParticipants(), formatMessage(
                        plugin.getConfig().getString("messages.join_failed",
                                "&cThe game could not connect every player. You have been returned.")));
                cancelJoinStagger();
                forceResetToIdle();
            }
        }, 1L, directStartPollTicks);
    }

    private void cancelJoinStagger() {
        if (joinStaggerTask != null) {
            joinStaggerTask.cancel();
            joinStaggerTask = null;
        }
    }

    private List<String> sanitizeArenaIds(List<String> rawArenas) {
        List<String> cleaned = new ArrayList<>();
        for (String raw : rawArenas) {
            if (raw == null) {
                continue;
            }
            String arena = raw.trim();
            if (arena.isEmpty()) {
                plugin.getLogger().warning("Ignoring blank arena entry in config.");
                continue;
            }
            try {
                Integer.parseInt(arena);
                cleaned.add(arena);
            } catch (NumberFormatException ex) {
                plugin.getLogger().severe("Ignoring invalid arena '" + arena
                        + "' — arena IDs must be integers.");
            }
        }
        return List.copyOf(cleaned);
    }

    private void authorizeTemporarily(UUID uuid) {
        tracker.authorize(uuid);
        Bukkit.getScheduler().runTaskLater(plugin, () -> tracker.unauthorize(uuid), Math.max(1, authorizationTicks));
    }

    public boolean joinPlayer(Player player) {
        if (!acceptsJoin()) {
            Messages.send(player, formatMessage(plugin.getConfig().getString(
                    "messages.no_event_running", "&cThere is no event running right now.")));
            return false;
        }
        if (!player.hasPermission("cosmicevents.use") && !player.hasPermission("cosmicevents.admin")) {
            Messages.send(player, formatMessage(plugin.getConfig().getString(
                    "messages.no_permission", "&cYou do not have permission to execute this command.")));
            return false;
        }
        if (!allowedWorlds.isEmpty() && !allowedWorlds.contains(player.getWorld().getName())) {
            Messages.send(player, formatMessage(plugin.getConfig().getString(
                    "messages.wrong_world", "&cYou cannot join the event from this world.")));
            return false;
        }
        if (tracker.isInEvent(player.getUniqueId())) {
            Messages.send(player, formatMessage(plugin.getConfig().getString(
                    "messages.already_in_event", "&cYou are already in the event!")));
            return false;
        }
        if (maxPlayers > 0 && currentPlayerCount() >= maxPlayers) {
            Messages.send(player, formatMessage(plugin.getConfig().getString(
                    "messages.event_full", "&cThe event is full (&e{max}&c players).")
                    .replace("{max}", String.valueOf(maxPlayers))));
            return false;
        }

        boolean lateJoin = state != EventState.JOINING;
        if (lateJoin) {
            tracker.activeParticipants().add(player.getUniqueId());
        } else {
            tracker.participants().add(player.getUniqueId());
        }
        if (!isExempt(player)) {
            tracker.saveAndNeutralizeMovement(player);
            tracker.stripMovementEffects(player);
        }
        if (lateJoin && activeArenaId >= 0) {
            sendPlayerToArena(player, activeArenaId, activeMinigameId);
        }
        String joinMessageKey = lateJoin || voteService.getConfiguredMinigames().size() <= 1
                ? "messages.joined_event_single"
                : "messages.joined_event";
        String joinMessageDefault = lateJoin || voteService.getConfiguredMinigames().size() <= 1
                ? "&aYou joined the event! The game will start shortly."
                : "&aYou joined the event! Vote using &b/event vote&a.";
        Messages.send(player, formatMessage(plugin.getConfig().getString(joinMessageKey, joinMessageDefault)));
        plugin.getSounds().play(player, "event_join");

        String maxDisplay = maxPlayers > 0 ? String.valueOf(maxPlayers) : "∞";
        for (String msg : plugin.getConfig().getStringList("messages.player_joined_broadcast")) {
            Messages.broadcast(formatMessage(msg
                    .replace("{player}", player.getName())
                    .replace("{count}", String.valueOf(currentPlayerCount()))
                    .replace("{min_players}", String.valueOf(minPlayers))
                    .replace("{max_players}", maxDisplay)));
        }
        return true;
    }

    public boolean leavePlayer(Player player) {
        return leavePlayer(player, false);
    }

    public boolean leavePlayer(Player player, boolean isDisconnect) {
        UUID uuid = player.getUniqueId();
        if (tracker.activeParticipants().contains(uuid)) {
            if (isDisconnect) {
                leaveOnDisconnect(player);
            } else {
                returnPlayerToOrigin(player);
                if (player.isOnline()) {
                    Messages.send(player, formatMessage(plugin.getConfig().getString(
                            "messages.left_game", "&cYou left the event game.")));
                }
            }
            return true;
        }

        if (!tracker.participants().contains(uuid)) {
            if (!isDisconnect && player.isOnline()) {
                Messages.send(player, formatMessage(plugin.getConfig().getString(
                        "messages.not_in_event", "&cYou are not in the event! Type /event join to join.")));
            }
            return false;
        }

        tracker.participants().remove(uuid);
        voteService.remove(uuid);
        tracker.savedLocations().remove(uuid);
        if (player.isOnline()) {
            tracker.restoreMovement(player);
        }
        restoreOriginState(player, !isDisconnect);
        if (!isDisconnect && player.isOnline()) {
            Messages.send(player, formatMessage(plugin.getConfig().getString(
                    "messages.left_event", "&cYou left the event.")));
        }

        String maxDisplay = maxPlayers > 0 ? String.valueOf(maxPlayers) : "∞";
        for (String msg : plugin.getConfig().getStringList("messages.player_left_broadcast")) {
            Messages.broadcast(formatMessage(msg
                    .replace("{player}", player.getName())
                    .replace("{count}", String.valueOf(tracker.participants().size()))
                    .replace("{min_players}", String.valueOf(minPlayers))
                    .replace("{max_players}", maxDisplay)));
        }
        return true;
    }


    public void leaveOnDisconnect(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        boolean wasActive = tracker.activeParticipants().contains(uuid);
        boolean wasJoining = tracker.participants().contains(uuid);
        if (!wasActive && !wasJoining && !inventorySnapshots.hasSnapshot(uuid) && !joinStates.hasState(uuid)) {
            return;
        }

        if (wasActive) {
            if (player.isOnline()) {
                authorizeTemporarily(uuid);
                blueArcade.leaveArena(player, leaveCommand);
            }
            restoreOriginImmediate(player);
            handleDisconnectDuringGame(uuid);
            tracker.participants().remove(uuid);
            voteService.remove(uuid);
            return;
        }

        if (wasJoining) {
            leavePlayer(player, true);
            return;
        }

        restoreOriginImmediate(player);
    }

    public void handleDisconnectDuringGame(UUID uuid) {
        tracker.activeParticipants().remove(uuid);
        tracker.aliveParticipants().remove(uuid);
        if (!tracker.eliminatedParticipants().contains(uuid)) {
            tracker.eliminatedParticipants().add(uuid);
        }
        endGameIfNoActivePlayers();
    }

    public boolean ensureCanVote(Player player) {
        if (state != EventState.JOINING) {
            Messages.send(player, formatMessage(plugin.getConfig().getString(
                    "messages.no_event_running", "&cThere is no event running right now.")));
            return false;
        }
        if (!player.hasPermission("cosmicevents.use") && !player.hasPermission("cosmicevents.admin")) {
            Messages.send(player, formatMessage(plugin.getConfig().getString(
                    "messages.no_permission", "&cYou do not have permission to execute this command.")));
            return false;
        }
        if (!tracker.participants().contains(player.getUniqueId())) {
            Messages.send(player, formatMessage(plugin.getConfig().getString(
                    "messages.not_in_event", "&cYou are not in the event! Type /event join to join.")));
            return false;
        }
        return true;
    }

    public boolean castVote(Player player, String minigameId) {
        if (!ensureCanVote(player)) {
            return false;
        }

        String normalized = voteService.normalizeMinigameId(minigameId);
        if (normalized == null) {
            Messages.send(player, formatMessage(plugin.getConfig().getString(
                    "messages.invalid_minigame", "&cThat minigame is not available for this event.")));
            return false;
        }
        if (voteService.hasVotedFor(player.getUniqueId(), normalized)) {
            Messages.send(player, formatMessage(plugin.getConfig().getString(
                    "messages.already_voted", "&cYou have already voted for this minigame!")));
            return false;
        }

        voteService.cast(player.getUniqueId(), normalized);
        int weight = voteService.getVoteWeight(player);
        plugin.getSounds().play(player, "event_vote");
        Messages.send(player, formatMessage(plugin.getConfig().getString(
                        "messages.vote_cast", "&aYou voted for &e{minigame}&a! (Your vote power: &b{weight}&a)")
                .replace("{minigame}", normalized)
                .replace("{weight}", String.valueOf(weight))));

        if (plugin.getConfig().getBoolean("broadcast_votes_to_participants", true)) {
            int totalVotes = voteService.getVoteCount(normalized, Bukkit::getPlayer);
            for (UUID uuid : tracker.participants()) {
                Player target = Bukkit.getPlayer(uuid);
                if (target == null || !target.isOnline()) {
                    continue;
                }
                for (String msg : plugin.getConfig().getStringList("messages.player_voted_broadcast")) {
                    Messages.send(target, formatMessage(msg
                            .replace("{player}", player.getName())
                            .replace("{minigame}", normalized)
                            .replace("{votes}", String.valueOf(totalVotes))
                            .replace("{weight}", String.valueOf(weight))));
                }
            }
        }
        return true;
    }

    public void returnPlayerToOrigin(Player player) {
        UUID uuid = player.getUniqueId();
        tracker.participants().remove(uuid);
        voteService.remove(uuid);
        tracker.activeParticipants().remove(uuid);
        tracker.aliveParticipants().remove(uuid);
        tracker.eliminatedParticipants().remove(uuid);

        authorizeTemporarily(uuid);
        blueArcade.leaveArena(player, leaveCommand);
        Location loc = tracker.savedLocations().remove(uuid);
        restoreOriginState(player, false);
        teleportHome(player, loc);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            teleportHome(player, loc);
            restoreOriginState(player, false);
            tracker.restoreMovement(player);
        }, InventorySnapshotService.RESTORE_DELAY_TICKS);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                restoreOriginState(player, false);
            }
        }, InventorySnapshotService.RESTORE_RETRY_TICKS);

        Integer position = rewardService.removePending(uuid);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                restoreOriginState(player, true);
                if (position != null) {
                    rewardService.giveReward(player, position);
                }
            } else if (position != null) {
                rewardService.setPending(uuid, position);
            }
        }, REWARD_DELAY_TICKS);

        endGameIfNoActivePlayers();
    }

    private void deliverPendingReward(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Integer position = rewardService.removePending(player.getUniqueId());
        if (position != null) {
            rewardService.giveReward(player, position);
        }
    }

    private void endGameIfNoActivePlayers() {
        if (state != EventState.GAME_ACTIVE || !tracker.activeParticipants().isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (state == EventState.GAME_ACTIVE && tracker.activeParticipants().isEmpty()) {
                finishGameCleanup();
            }
        });
    }

    public void handlePlayerElimination(int arenaId, Player player) {
        if (state != EventState.GAME_ACTIVE || arenaId != activeArenaId) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (tracker.aliveParticipants().remove(uuid) && !tracker.eliminatedParticipants().contains(uuid)) {
            tracker.eliminatedParticipants().add(uuid);
        }
    }

    private void startMonitoringGame(final int arenaId) {
        cancelGameMonitor();
        gameMonitorTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private boolean gameStarted;
            private int ticksWaiting;

            @Override
            public void run() {
                List<Player> online = currentActivePlayers();

                for (Player player : online) {
                    if (tracker.aliveParticipants().contains(player.getUniqueId())
                            && blueArcade.isEliminatedStatus(player)) {
                        handlePlayerElimination(arenaId, player);
                    }
                }

                var extras = blueArcade.getHostExtras();
                boolean extrasActive = extras.isAvailable() && extras.isGameActive(arenaId);
                boolean apiStarted = blueArcade.hasAnyPlayerStarted(arenaId, online);
                boolean finishing = extras.isAvailable() && extras.isArenaFinishing(arenaId);
                boolean stillOccupied = blueArcade.isArenaOccupied(arenaId, online)
                        || tracker.activeParticipants().stream().anyMatch(uuid -> {
                            Player p = Bukkit.getPlayer(uuid);
                            return p != null && (blueArcade.isInGame(p) || blueArcade.isInWaitingArena(p));
                        });

                if (!gameStarted) {
                    ticksWaiting += 2;
                    if (extrasActive || apiStarted) {
                        gameStarted = true;
                        tracker.aliveParticipants().addAll(tracker.activeParticipants());
                    } else if (ticksWaiting >= gameStartTimeoutSeconds * 20) {
                        plugin.getLogger().warning("Game in arena " + arenaId
                                + " failed to start within " + gameStartTimeoutSeconds + "s. Resetting.");
                        Messages.sendBroadcastTo(tracker.activeParticipants(), formatMessage(
                                plugin.getConfig().getString("messages.game_start_timeout",
                                        "&cThe game did not start. You have been returned.")));
                        cancelGameMonitor();
                        forceResetToIdle();
                    }
                } else if (finishing || (!extrasActive && !stillOccupied)) {
                    cancelGameMonitor();
                    processGameEnd();
                }
            }
        }, 2L, 2L);
    }

    private List<Player> currentActivePlayers() {
        List<Player> online = new ArrayList<>();
        for (UUID uuid : tracker.activeParticipants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                online.add(player);
            }
        }
        return online;
    }

    private void processGameEnd() {
        int endedArena = activeArenaId;
        var extras = blueArcade.getHostExtras();

        List<UUID> finishOrder = resolveFinishOrder(endedArena);
        Set<UUID> sharedFirst = endedArena >= 0 ? extras.readSharedFirstPlace(endedArena) : Set.of();

        if (extras.isAvailable() && endedArena >= 0) {

            extras.stopArena(endedArena);
        }

        suppressBlueArcadePostGameNotifications();

        Set<UUID> eventWinners = new HashSet<>();
        for (int index = 0; index < finishOrder.size(); index++) {
            UUID uuid = finishOrder.get(index);
            int position = sharedFirst.contains(uuid) ? 1 : index + 1;
            rewardService.setPending(uuid, position);
            if (position == 1) {
                eventWinners.add(uuid);
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            if (position == 1) {
                plugin.getSounds().play(player, "game_win");
            } else if (position == 2) {
                plugin.getSounds().play(player, "game_second");
            } else if (position == 3) {
                plugin.getSounds().play(player, "game_third");
            }
        }
        winStats.recordWins(eventWinners);

        for (UUID uuid : new ArrayList<>(tracker.activeParticipants())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                returnPlayerToOrigin(player);
            }
        }
        finishGameCleanup();
    }

    private List<UUID> resolveFinishOrder(int arenaId) {
        List<UUID> order = new ArrayList<>();
        Set<UUID> placed = new HashSet<>();

        var extras = blueArcade.getHostExtras();
        Map<UUID, Integer> podium = arenaId >= 0 ? extras.readPodiumPositions(arenaId) : Map.of();
        if (podium.isEmpty()) {
            if (!extras.hasPodiumAccess()) {
                plugin.getLogger().warning("BlueArcade podium data is unreadable for arena "
                        + arenaId + " — ranking by elimination order instead.");
            }
        } else {
            List<UUID> ranked = new ArrayList<>();
            for (UUID uuid : tracker.activeParticipants()) {
                if (podium.containsKey(uuid)) {
                    ranked.add(uuid);
                }
            }
            ranked.sort(Comparator.comparingInt(podium::get));
            for (UUID uuid : ranked) {
                if (placed.add(uuid)) {
                    order.add(uuid);
                }
            }
        }

        for (UUID uuid : tracker.aliveParticipants()) {
            if (placed.add(uuid)) {
                order.add(uuid);
            }
        }

        List<UUID> eliminated = tracker.eliminatedParticipants();
        for (int index = eliminated.size() - 1; index >= 0; index--) {
            UUID uuid = eliminated.get(index);
            if (placed.add(uuid)) {
                order.add(uuid);
            }
        }
        return order;
    }

    private void suppressBlueArcadePostGameNotifications() {
        List<UUID> eventPlayers = new ArrayList<>(tracker.activeParticipants());
        if (eventPlayers.isEmpty()) {
            return;
        }

        final int[] runs = {0};
        final org.bukkit.scheduler.BukkitTask[] task = new org.bukkit.scheduler.BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            var extras = blueArcade.getHostExtras();
            if (extras.isAvailable()) {
                for (UUID uuid : eventPlayers) {
                    extras.suppressPostGameNotifications(uuid);
                }
            }
            if (++runs[0] >= 20) {
                task[0].cancel();
            }
        }, 0L, 1L);
    }

    private void startGameTimeout() {
        cancelGameTimeout();
        long ticks = Math.max(20L, gameTimeoutMinutes * 60L * 20L);
        gameTimeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state == EventState.GAME_ACTIVE || state == EventState.STARTING_GAME) {
                plugin.getLogger().warning("Minigame didn't finish within "
                        + gameTimeoutMinutes + " minutes. Resetting event state to IDLE.");
                forceResetToIdle();
            }
        }, ticks);
    }

    public void forceResetToIdle() {
        cancelGameTimeout();
        cancelGameMonitor();
        cancelCountdown();
        cancelJoinStagger();
        cancelPostLaunchClear();

        int arenaToRestore = activeArenaId;
        if (state == EventState.GAME_ACTIVE || state == EventState.STARTING_GAME) {
            var extras = blueArcade.getHostExtras();
            if (extras.isAvailable() && arenaToRestore >= 0) {
                extras.stopArena(arenaToRestore);
            }
            for (UUID uuid : new ArrayList<>(tracker.activeParticipants())) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    returnPlayerToOrigin(player);
                } else {
                    tracker.activeParticipants().remove(uuid);
                }
            }
            tracker.clearGameTracking();
            activeArenaId = -1;
            activeMinigameId = null;
            minigameEffectsAllowed = false;
        }
        if (arenaToRestore >= 0) {
            blueArcade.getHostExtras().restoreRounds(arenaToRestore);
        }
        returnToIdleAndSchedule();
    }

    private void finishGameCleanup() {
        cancelGameTimeout();
        cancelGameMonitor();
        cancelJoinStagger();
        cancelPostLaunchClear();
        int arenaToRestore = activeArenaId;
        tracker.clearGameTracking();
        activeArenaId = -1;
        activeMinigameId = null;
        minigameEffectsAllowed = false;
        if (arenaToRestore >= 0) {
            blueArcade.getHostExtras().restoreRounds(arenaToRestore);
        }
        returnToIdleAndSchedule();
    }

    private void returnToIdleAndSchedule() {
        eventGeneration++;
        cancelCountdown();
        cancelPostLaunchClear();
        for (UUID uuid : new ArrayList<>(tracker.participants())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                tracker.restoreMovement(player);
                restoreOriginState(player, true);
            }
        }
        tracker.participants().clear();
        voteService.clear();
        bypassMinPlayers = false;
        state = EventState.IDLE;
        activeMinigameId = null;
        minigameEffectsAllowed = false;
        if (scheduler.isEnabled()) {
            scheduler.scheduleNext();
        } else {
            scheduler.cancel();
        }
    }

    public void shutdown() {
        cancelCountdown();
        cancelGameTimeout();
        cancelGameMonitor();
        cancelJoinStagger();
        cancelPostLaunchClear();
        Set<UUID> inEvent = new HashSet<>(tracker.participants());
        inEvent.addAll(tracker.activeParticipants());
        for (UUID uuid : inEvent) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            authorizeTemporarily(uuid);
            blueArcade.leaveArena(player, leaveCommand);
            restoreOriginImmediate(player);
            inventorySnapshots.restore(player, true);
            joinStates.discard(uuid);
        }
        restoreAllMovement();
        inventorySnapshots.restoreAllOnline(true);
        joinStates.restoreAllOnline(true);
        scheduler.cancel();
        blueArcade.getHostExtras().restoreAllRounds();
    }

    public void restoreAfterReconnect(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        boolean hadState = joinStates.hasState(uuid)
                || inventorySnapshots.hasSnapshot(uuid)
                || tracker.savedLocations().containsKey(uuid);
        Location loc = tracker.savedLocations().remove(uuid);

        inventorySnapshots.restore(player, false);
        if (hadState) {
            restoreOriginState(player, false);
            teleportHome(player, loc);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (hadState) {
                restoreOriginState(player, false);
                teleportHome(player, loc);
                if (tracker.hasMovementSnapshot(uuid)) {
                    tracker.restoreMovement(player);
                } else if (!isExempt(player)) {
                    tracker.resetToVanillaMovement(player);
                    joinStates.restorePersistedMovement(player);
                } else {
                    joinStates.restorePersistedMovement(player);
                }
            } else {
                deliverPendingReward(player);
            }
        }, REWARD_DELAY_TICKS);

        if (!hadState) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                restoreOriginState(player, true);
                deliverPendingReward(player);
            }
        }, REWARD_DELAY_TICKS + InventorySnapshotService.RESTORE_CONSUME_DELAY_TICKS);
    }

    private void restoreOriginState(Player player, boolean consume) {
        if (player == null) {
            return;
        }
        joinStates.restoreEffects(player);
        joinStates.restoreGameMode(player);
        if (!tracker.hasMovementSnapshot(player.getUniqueId())) {
            joinStates.restorePersistedMovement(player);
        }
        inventorySnapshots.restore(player, consume);
        if (consume) {
            joinStates.discard(player.getUniqueId());
        }
    }

    private void restoreOriginImmediate(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Location loc = tracker.savedLocations().get(uuid);
        joinStates.restoreEffects(player);
        joinStates.restoreGameMode(player);
        inventorySnapshots.restore(player, false);
        teleportHome(player, loc);
        if (tracker.hasMovementSnapshot(uuid)) {
            tracker.restoreMovement(player);
        } else if (!isExempt(player)) {
            tracker.resetToVanillaMovement(player);
            joinStates.restorePersistedMovement(player);
        } else {
            joinStates.restorePersistedMovement(player);
        }
    }

    private void teleportHome(Player player, Location fallback) {
        joinStates.teleportHome(player, fallback);
    }

    private void stripDonorMovement(List<Player> players) {
        for (Player player : players) {
            if (player == null || !player.isOnline() || isExempt(player)) {
                continue;
            }
            tracker.enforceNeutralMovement(player);
            tracker.stripMovementEffects(player);
        }
    }


    private void restoreAllMovement() {
        Set<UUID> inEvent = new HashSet<>(tracker.participants());
        inEvent.addAll(tracker.activeParticipants());
        for (UUID uuid : inEvent) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                tracker.restoreMovement(player);
            }
        }
    }

    private void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        cancelJoinClose();
    }

    private void cancelJoinClose() {
        if (joinCloseTask != null) {
            joinCloseTask.cancel();
            joinCloseTask = null;
        }
    }

    private void cancelGameTimeout() {
        if (gameTimeoutTask != null) {
            gameTimeoutTask.cancel();
            gameTimeoutTask = null;
        }
    }

    private void cancelGameMonitor() {
        if (gameMonitorTask != null) {
            gameMonitorTask.cancel();
            gameMonitorTask = null;
        }
    }

    private void cancelPostLaunchClear() {
        if (postLaunchClearTask != null) {
            postLaunchClearTask.cancel();
            postLaunchClearTask = null;
        }
    }

    public String formatMessage(String message) {
        String prefix = plugin.getConfig().getString("messages.prefix", "&e[&6CosmicEvents&e] &r");
        return prefix + message;
    }

    public String getNextEventTimeFormatted() {
        return scheduler.formatRemaining();
    }

    public String getPlaceholderTime() {
        if (state == EventState.JOINING) {
            String format = plugin.getConfig().getString("placeholders.joining_format", "&aJoining ({time}s)");
            return format.replace("{time}", String.valueOf(joinTimeLeftSeconds));
        }
        if (state == EventState.STARTING_GAME || state == EventState.GAME_ACTIVE) {
            return plugin.getConfig().getString("placeholders.active_format", "&cActive");
        }
        long diff = scheduler.getNextEventTimeMillis() - System.currentTimeMillis();
        if (diff <= 0L) {
            return plugin.getConfig().getString("placeholders.idle_starting_soon", "Starting soon...");
        }
        return formatDuration(diff);
    }


    public String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long hours = seconds / 3600L;
        long minutes = seconds % 3600L / 60L;
        long secs = seconds % 60L;
        if (hours > 0L) {
            return plugin.getConfig().getString("placeholders.time_hours_format", "{hours}h {minutes}m {seconds}s")
                    .replace("{hours}", String.valueOf(hours))
                    .replace("{minutes}", String.valueOf(minutes))
                    .replace("{seconds}", String.valueOf(secs));
        }
        if (minutes > 0L) {
            return plugin.getConfig().getString("placeholders.time_minutes_format", "{minutes}m {seconds}s")
                    .replace("{minutes}", String.valueOf(minutes))
                    .replace("{seconds}", String.valueOf(secs));
        }
        return plugin.getConfig().getString("placeholders.time_seconds_format", "{seconds}s")
                .replace("{seconds}", String.valueOf(secs));
    }

    public EventState getState() {
        return state;
    }

    public Set<UUID> getParticipants() {
        return tracker.getParticipantsView();
    }

    public Set<UUID> getActiveParticipants() {
        return tracker.getActiveParticipantsView();
    }

    public Map<UUID, Location> getSavedLocations() {
        return tracker.getSavedLocationsView();
    }

    public boolean isActiveParticipant(UUID uuid) {
        return tracker.activeParticipants().contains(uuid);
    }

    public boolean areMinigameEffectsAllowed() {
        return minigameEffectsAllowed;
    }

    public boolean isInEvent(UUID uuid) {
        return tracker.isInEvent(uuid);
    }


    public static boolean isExempt(Player player) {
        return player != null
                && (player.isOp()
                || player.hasPermission("cosmicevents.bypass")
                || player.hasPermission("cosmicevents.admin"));
    }

    public boolean isAuthorized(Player player) {
        return player.hasPermission("cosmicevents.bypass") || tracker.isAuthorized(player.getUniqueId());
    }

    public String getActiveMinigameId() {
        return activeMinigameId;
    }

    public int getVoteCount(String minigameId) {
        return voteService.getVoteCount(minigameId, Bukkit::getPlayer);
    }

    public int getVoteWeight(Player player) {
        return voteService.getVoteWeight(player);
    }
}

