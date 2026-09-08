package com.cosmic901.cosmicevents.bridge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;


public final class BlueArcadeHostExtras {
    private final Plugin plugin;
    private final Logger logger;
    private final Map<Integer, Integer> overriddenRounds = new ConcurrentHashMap<>();

    private boolean available;
    private String unavailableReason = "Not initialized";

    private Object mainInstance;
    private Method getLifecycleManager;
    private Method forceStartArena;
    private Method stopArena;
    private Method getCountdownManager;
    private Method getLobbyCountdown;
    private Method cancelLobbyCountdown;
    private Method cancelLimboCountdown;
    private Method lobbyIsRunning;
    private Method reduceCountdown;
    private Method getGameManager;
    private Field gameManagerField;
    private Method isGameActive;
    private Method getPlayerJoinHandler;
    private Method joinPlayerMethod;
    private Method getPlayerLeaveHandler;
    private Method leavePlayerMethod;
    private Method getArenaRepository;
    private Method getArenaMethod;
    private Method arenaGetState;
    private Method arenaGetStatus;
    private Method arenaGetPlayerCount;
    private Method arenaClearVotes;
    private Method arenaAddVote;
    private Method arenaClearPlayedMinigames;
    private Method arenaSetAvailableMinigames;
    private Method arenaGetActualGame;
    private Method arenaSetForcedStart;
    private Method getGameSelector;
    private Method isVotingDisabled;
    private Method getGameSequencer;
    private Method nextGame;
    private Field dataManagerField;
    private Method getArenas;
    private Field arenasField;
    private Method arenasGet;
    private Method getRoundManager;
    private Method resetRounds;
    private Method getRewardNotificationBatcher;
    private Method cancelRewardNotification;
    private Method getAchievementsManager;
    private Field achievementPendingNotifications;
    private Field achievementPendingSummaryNotifications;
    private Field achievementSummaryCapturePlayers;

    private Method getArenaScoring;
    private Method getPodiumManager;
    private Field podiumManagerField;
    private Method podiumGetPodium;
    private Method arenaGetSharedFirstPlace;

    private Method configNodeNode;
    private Method configNodeGetInt;
    private Method configNodeSet;

    private Class<? extends Event> playerEliminateEventClass;
    private Method eliminateGetArenaId;
    private Method eliminateGetPlayer;
    private Field eliminatePlayerField;
    private boolean eliminateListenerRegistered;

    public BlueArcadeHostExtras(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public boolean initialize() {
        available = false;
        unavailableReason = "BlueArcade not found";
        eliminateListenerRegistered = false;
        playerEliminateEventClass = null;
        eliminateGetArenaId = null;
        eliminateGetPlayer = null;
        eliminatePlayerField = null;
        getArenaScoring = null;
        getPodiumManager = null;
        podiumManagerField = null;
        podiumGetPodium = null;
        arenaGetSharedFirstPlace = null;
        configNodeNode = null;
        configNodeGetInt = null;
        configNodeSet = null;
        lobbyIsRunning = null;
        reduceCountdown = null;
        getGameManager = null;
        gameManagerField = null;
        isGameActive = null;
        getArenas = null;
        arenasField = null;

        Plugin baPlugin = BlueArcadeBridge.findBlueArcadePlugin();
        if (baPlugin == null || !baPlugin.isEnabled()) {
            logger.warning("Host extras unavailable — BlueArcade missing.");
            return false;
        }

        try {
            ClassLoader cl = baPlugin.getClass().getClassLoader();
            Class<?> mainClass = Class.forName("net.blueva.arcade.v3.Main", true, cl);
            Method getPlugin = mainClass.getMethod("getPlugin");
            mainInstance = getPlugin.invoke(null);
            if (mainInstance == null) {
                throw new IllegalStateException("Main.getPlugin() returned null");
            }

            getLifecycleManager = mainClass.getMethod("getLifecycleManager");
            Object lifecycleManager = getLifecycleManager.invoke(mainInstance);
            if (lifecycleManager == null) {
                throw new IllegalStateException("getLifecycleManager() returned null");
            }
            forceStartArena = lifecycleManager.getClass().getMethod("forceStartArena", Integer.TYPE);
            stopArena = lifecycleManager.getClass().getMethod("stopArena", Integer.TYPE);

            getCountdownManager = mainClass.getMethod("getCountdownManager");
            Object countdownManager = getCountdownManager.invoke(mainInstance);
            if (countdownManager == null) {
                throw new IllegalStateException("getCountdownManager() returned null");
            }
            getLobbyCountdown = countdownManager.getClass().getMethod("getLobbyCountdown", Integer.TYPE);
            cancelLobbyCountdown = countdownManager.getClass().getMethod("cancelLobbyCountdown", Integer.TYPE);
            cancelLimboCountdown = countdownManager.getClass().getMethod("cancelLimboCountdown", Integer.TYPE);


            bindGameManagerAccess(mainClass);

            getPlayerJoinHandler = mainClass.getMethod("getPlayerJoinHandler");
            Object joinHandler = getPlayerJoinHandler.invoke(mainInstance);
            if (joinHandler == null) {
                throw new IllegalStateException("getPlayerJoinHandler() returned null");
            }
            joinPlayerMethod = joinHandler.getClass().getMethod("joinPlayer", Player.class, Integer.TYPE);

            getPlayerLeaveHandler = mainClass.getMethod("getPlayerLeaveHandler");
            Object leaveHandler = getPlayerLeaveHandler.invoke(mainInstance);
            if (leaveHandler == null) {
                throw new IllegalStateException("getPlayerLeaveHandler() returned null");
            }
            leavePlayerMethod = leaveHandler.getClass().getMethod(
                    "leavePlayer", Integer.TYPE, Player.class, Boolean.TYPE);

            getArenaRepository = mainClass.getMethod("getArenaRepository");
            Object arenaRepo = getArenaRepository.invoke(mainInstance);
            if (arenaRepo == null) {
                throw new IllegalStateException("getArenaRepository() returned null");
            }
            getArenaMethod = arenaRepo.getClass().getMethod("getArena", Integer.TYPE);

            Class<?> arenaClass = Class.forName("net.blueva.arcade.v3.arena.Arena", true, cl);
            arenaGetState = arenaClass.getMethod("getState");
            arenaGetStatus = arenaClass.getMethod("getStatus");
            arenaGetPlayerCount = arenaClass.getMethod("getPlayerCount");
            arenaClearVotes = arenaClass.getMethod("clearVotes");
            arenaAddVote = arenaClass.getMethod("addVote", String.class);
            arenaClearPlayedMinigames = arenaClass.getMethod("clearPlayedMinigames");
            arenaSetAvailableMinigames = arenaClass.getMethod("setAvailableMinigames", List.class);
            arenaGetActualGame = arenaClass.getMethod("getActualGame");
            arenaSetForcedStart = arenaClass.getMethod("setForcedStart", Boolean.TYPE);

            getGameSelector = mainClass.getMethod("getGameSelector");
            Object gameSelector = getGameSelector.invoke(mainInstance);
            if (gameSelector != null) {
                isVotingDisabled = gameSelector.getClass().getMethod("isVotingDisabled", Integer.TYPE);
            }

            getGameSequencer = mainClass.getMethod("getGameSequencer");
            Object sequencer = getGameSequencer.invoke(mainInstance);
            if (sequencer == null) {
                throw new IllegalStateException("getGameSequencer() returned null");
            }
            nextGame = sequencer.getClass().getMethod("nextGame", Integer.TYPE);


            dataManagerField = mainClass.getField("dataManager");
            Object dataManager = dataManagerField.get(mainInstance);
            if (dataManager == null) {
                throw new IllegalStateException("Main.dataManager is null");
            }

            bindMethodOrField(dataManager.getClass(), "getArenas", "arenas",
                    method -> getArenas = method, field -> arenasField = field);
            Object arenas = resolveArenas(dataManager);
            if (arenas == null) {
                throw new IllegalStateException("DataManager arenas unavailable");
            }
            arenasGet = arenas.getClass().getMethod("get", Integer.class);

            getRoundManager = mainClass.getMethod("getRoundManager");
            Object roundManager = getRoundManager.invoke(mainInstance);
            if (roundManager != null) {
                resetRounds = roundManager.getClass().getMethod("resetRounds", Integer.TYPE);
            }

            initializeNotificationSuppression(mainClass);
            initializePodiumAccess(mainClass, arenaClass);
            initializeEliminateEvent(cl);

            available = true;
            unavailableReason = null;
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            available = false;
            unavailableReason = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            logger.severe("Host extras failed to init: " + unavailableReason);
            return false;
        }
    }

    private void initializePodiumAccess(Class<?> mainClass, Class<?> arenaClass) {
        try {
            getArenaScoring = mainClass.getMethod("getArenaScoring");
            Object scoring = getArenaScoring.invoke(mainInstance);
            if (scoring == null) {
                throw new IllegalStateException("getArenaScoring() returned null");
            }

            getPodiumManager = null;
            podiumManagerField = null;
            bindMethodOrField(scoring.getClass(), "getPodiumManager", "podiumManager",
                    method -> getPodiumManager = method, field -> podiumManagerField = field);
            Object podiumManager = resolvePodiumManager(scoring);
            if (podiumManager == null) {
                throw new IllegalStateException("podiumManager unavailable");
            }
            podiumGetPodium = podiumManager.getClass().getMethod("getPodium", Integer.TYPE);
            arenaGetSharedFirstPlace = arenaClass.getMethod("getSharedFirstPlace");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            getArenaScoring = null;
            getPodiumManager = null;
            podiumManagerField = null;
            podiumGetPodium = null;
            arenaGetSharedFirstPlace = null;
            logger.warning("BlueArcade podium placements unavailable ("
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage()
                    + ") — reward placements will fall back to elimination order.");
        }
    }

    public boolean hasPodiumAccess() {
        return available && podiumGetPodium != null;
    }

    public Map<UUID, Integer> readPodiumPositions(int arenaId) {
        if (!hasPodiumAccess()) {
            return Map.of();
        }
        try {
            Object scoring = getArenaScoring.invoke(mainInstance);
            if (scoring == null) {
                return Map.of();
            }
            Object podiumManager = resolvePodiumManager(scoring);
            if (podiumManager == null) {
                return Map.of();
            }
            Object raw = podiumGetPodium.invoke(podiumManager, arenaId);
            if (!(raw instanceof Map<?, ?> podium) || podium.isEmpty()) {
                return Map.of();
            }
            Map<UUID, Integer> positions = new HashMap<>();
            for (Map.Entry<?, ?> entry : podium.entrySet()) {
                if (entry.getKey() instanceof Integer position
                        && entry.getValue() instanceof Player player) {
                    positions.put(player.getUniqueId(), position);
                }
            }
            return positions;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logger.warning("Failed reading BlueArcade podium for arena "
                    + arenaId + ": " + ex.getMessage());
            return Map.of();
        }
    }

    public Set<UUID> readSharedFirstPlace(int arenaId) {
        if (!available || arenaGetSharedFirstPlace == null) {
            return Set.of();
        }
        try {
            Object arena = getArena(arenaId);
            if (arena == null) {
                return Set.of();
            }
            Object raw = arenaGetSharedFirstPlace.invoke(arena);
            if (!(raw instanceof Set<?> rawSet) || rawSet.isEmpty()) {
                return Set.of();
            }
            Set<UUID> shared = new HashSet<>();
            for (Object value : rawSet) {
                if (value instanceof UUID uuid) {
                    shared.add(uuid);
                }
            }
            return shared;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logger.warning("Failed reading shared first place for arena "
                    + arenaId + ": " + ex.getMessage());
            return Set.of();
        }
    }

    private void initializeEliminateEvent(ClassLoader cl) {
        playerEliminateEventClass = null;
        eliminateGetArenaId = null;
        eliminateGetPlayer = null;
        eliminatePlayerField = null;
        try {
            Class<?> rawEliminate = findEliminateEventClass(cl);
            if (rawEliminate == null) {
                Plugin baPlugin = BlueArcadeBridge.findBlueArcadePlugin();
                String version = baPlugin != null ? baPlugin.getPluginMeta().getVersion() : "unknown";
                logger.warning("BlueArcade PlayerEliminateEvent not found "
                        + "(plugin v" + version + "). Elimination tracking will poll player status instead.");
                return;
            }
            if (!Event.class.isAssignableFrom(rawEliminate)) {
                logger.warning("PlayerEliminateEvent found but is not a Bukkit Event.");
                return;
            }
            @SuppressWarnings("unchecked")
            Class<? extends Event> eliminateClass = (Class<? extends Event>) rawEliminate;
            Method getArenaId = eliminateClass.getMethod("getArenaId");

            Method getPlayer = findMethod(eliminateClass, "getPlayer");
            Field playerField = getPlayer == null ? findField(eliminateClass, "player") : null;
            if (getPlayer == null && playerField == null) {
                throw new NoSuchMethodException("getPlayer()/player unavailable on "
                        + eliminateClass.getName());
            }
            playerEliminateEventClass = eliminateClass;
            eliminateGetArenaId = getArenaId;
            eliminateGetPlayer = getPlayer;
            eliminatePlayerField = playerField;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logger.warning("BlueArcade eliminate event unavailable: "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private static Class<?> findEliminateEventClass(ClassLoader cl) {
        String[] candidates = {
                "net.blueva.arcade.v3.arena.player.PlayerEliminateEvent",
                "net.blueva.arcade.v3.events.PlayerEliminateEvent"
        };
        for (String name : candidates) {
            try {
                return Class.forName(name, true, cl);
            } catch (ClassNotFoundException ignored) {

            }
        }
        return null;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getUnavailableReason() {
        return unavailableReason;
    }

    private void initializeNotificationSuppression(Class<?> mainClass) {
        try {
            getRewardNotificationBatcher = mainClass.getMethod("getRewardNotificationBatcher");
            Object batcher = getRewardNotificationBatcher.invoke(mainInstance);
            if (batcher != null) {
                cancelRewardNotification = batcher.getClass().getMethod("cancel", UUID.class);
            }

            getAchievementsManager = mainClass.getMethod("getAchievementsManager");
            Object achievements = getAchievementsManager.invoke(mainInstance);
            if (achievements != null) {
                Class<?> type = achievements.getClass();
                achievementPendingNotifications = accessibleField(type, "pendingNotifications");
                achievementPendingSummaryNotifications = accessibleField(type, "pendingSummaryNotifications");
                achievementSummaryCapturePlayers = accessibleField(type, "summaryCapturePlayers");
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            getRewardNotificationBatcher = null;
            cancelRewardNotification = null;
            getAchievementsManager = null;
            achievementPendingNotifications = null;
            achievementPendingSummaryNotifications = null;
            achievementSummaryCapturePlayers = null;
            logger.warning("BlueArcade notification suppression unavailable: "
                    + ex.getMessage());
        }
    }

    private static Field accessibleField(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    public void suppressPostGameNotifications(UUID playerId) {
        if (!available || playerId == null) {
            return;
        }
        try {
            if (getRewardNotificationBatcher != null && cancelRewardNotification != null) {
                Object batcher = getRewardNotificationBatcher.invoke(mainInstance);
                if (batcher != null) {
                    cancelRewardNotification.invoke(batcher, playerId);
                }
            }

            if (getAchievementsManager != null) {
                Object achievements = getAchievementsManager.invoke(mainInstance);
                removePlayerEntry(achievementPendingNotifications, achievements, playerId);
                removePlayerEntry(achievementPendingSummaryNotifications, achievements, playerId);
                removePlayerEntry(achievementSummaryCapturePlayers, achievements, playerId);
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logger.warning("Failed to suppress post-game notifications for "
                    + playerId + ": " + ex.getMessage());
        }
    }

    private static void removePlayerEntry(Field field, Object owner, UUID playerId)
            throws IllegalAccessException {
        if (field == null || owner == null) {
            return;
        }
        Object collection = field.get(owner);
        if (collection instanceof Map<?, ?> map) {
            map.remove(playerId);
        } else if (collection instanceof Set<?> set) {
            set.remove(playerId);
        }
    }

    public boolean joinPlayer(Player player, int arenaId) {
        if (!available || player == null || !player.isOnline()) {
            return false;
        }
        try {
            Object joinHandler = getPlayerJoinHandler.invoke(mainInstance);
            if (joinHandler == null) {
                return false;
            }
            Object result = joinPlayerMethod.invoke(joinHandler, player, arenaId);
            return result instanceof Boolean ? (Boolean) result : true;
        } catch (ReflectiveOperationException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            logger.warning("BA joinPlayer failed for " + player.getName()
                    + " → arena " + arenaId + ": " + cause.getMessage());
            return false;
        }
    }

    public boolean leavePlayer(Player player, int arenaId) {
        if (!available || player == null || !player.isOnline() || arenaId < 0) {
            return false;
        }
        try {
            Object leaveHandler = getPlayerLeaveHandler.invoke(mainInstance);
            if (leaveHandler == null) {
                return false;
            }
            leavePlayerMethod.invoke(leaveHandler, arenaId, player, true);
            return true;
        } catch (ReflectiveOperationException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            logger.warning("BA leavePlayer failed for " + player.getName()
                    + ": " + cause.getMessage());
            return false;
        }
    }

    public boolean isArenaJoinable(int arenaId) {
        Object arena = getArena(arenaId);
        if (arena == null) {
            return false;
        }
        try {
            Object status = arenaGetStatus.invoke(arena);
            if (status == null || !status.toString().equalsIgnoreCase("ENABLED")) {
                return false;
            }
            Object state = arenaGetState.invoke(arena);
            return state != null && state.toString().equalsIgnoreCase("WAITING");
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    public String getArenaStateName(int arenaId) {
        Object arena = getArena(arenaId);
        if (arena == null) {
            return "MISSING";
        }
        try {
            Object state = arenaGetState.invoke(arena);
            Object actual = arenaGetActualGame.invoke(arena);
            String stateName = state == null ? "?" : state.toString();
            String actualName = actual == null ? "?" : actual.toString();
            return stateName + "/" + actualName;
        } catch (ReflectiveOperationException ex) {
            return "ERROR";
        }
    }

    public String getActualGame(int arenaId) {
        Object arena = getArena(arenaId);
        if (arena == null) {
            return null;
        }
        try {
            Object actual = arenaGetActualGame.invoke(arena);
            return actual == null ? null : actual.toString();
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    public boolean isArenaFinishing(int arenaId) {
        String actual = getActualGame(arenaId);
        if (actual == null) {
            return false;
        }
        String lower = actual.toLowerCase();
        return lower.equals("finish") || lower.contains("limbo") || lower.equals("ending");
    }

    public int getArenaPlayerCount(int arenaId) {
        Object arena = getArena(arenaId);
        if (arena == null) {
            return 0;
        }
        try {
            Object count = arenaGetPlayerCount.invoke(arena);
            return count instanceof Integer ? (Integer) count : 0;
        } catch (ReflectiveOperationException ex) {
            return 0;
        }
    }


    public void enforceSingleRound(int arenaId) {
        if (!available) {
            return;
        }
        try {
            int previous = readConfiguredRounds(arenaId);
            if (previous != 1) {
                overriddenRounds.putIfAbsent(arenaId, previous);
                writeConfiguredRounds(arenaId, 1);
            } else {
                overriddenRounds.putIfAbsent(arenaId, 1);
            }
            if (resetRounds != null && getRoundManager != null) {
                Object rm = getRoundManager.invoke(mainInstance);
                if (rm != null) {
                    resetRounds.invoke(rm, arenaId);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logger.warning("enforceSingleRound failed: " + ex.getMessage());
        }
    }

    public void restoreRounds(int arenaId) {
        Integer previous = overriddenRounds.remove(arenaId);
        if (previous == null || !available) {
            return;
        }
        try {
            writeConfiguredRounds(arenaId, previous);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logger.warning("restoreRounds failed: " + ex.getMessage());
        }
    }

    public void restoreAllRounds() {
        for (Integer arenaId : new ArrayList<>(overriddenRounds.keySet())) {
            restoreRounds(arenaId);
        }
    }


    public boolean selectGameAndSkipLobby(int arenaId, String gameId) {
        if (!available) {
            logger.severe("Cannot start event game — host extras unavailable: "
                    + unavailableReason);
            return false;
        }
        try {
            enforceSingleRound(arenaId);
            lockArenaToMinigame(arenaId, gameId);
            if (!startGameImmediately(arenaId)) {
                return false;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logger.severe("selectGameAndSkipLobby failed: "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return false;
        }
    }


    private boolean startGameImmediately(int arenaId) {
        try {
            Object arena = getArena(arenaId);
            if (arena == null) {
                throw new IllegalStateException("Arena " + arenaId + " not found");
            }

            if (isGameActive(arenaId)) {
                return true;
            }

            Object state = arenaGetState.invoke(arena);
            if (state != null && state.toString().equalsIgnoreCase("RUNNING")
                    && !isArenaFinishing(arenaId)) {
                return true;
            }

            arenaSetForcedStart.invoke(arena, true);

            Object countdownManager = getCountdownManager.invoke(mainInstance);
            if (countdownManager != null) {
                cancelLobbyCountdown.invoke(countdownManager, arenaId);
                cancelLimboCountdown.invoke(countdownManager, arenaId);
            }

            Object sequencer = getGameSequencer.invoke(mainInstance);
            if (sequencer == null) {
                throw new IllegalStateException("GameSequencer is null");
            }


            try {
                nextGame.invoke(sequencer, arenaId);
                return true;
            } catch (ReflectiveOperationException nextEx) {
                Throwable cause = nextEx.getCause() != null ? nextEx.getCause() : nextEx;
                logger.warning("nextGame failed (" + cause.getMessage()
                        + ") — falling back to forceStartArena.");
            }

            return forceStartAndSkipLobby(arenaId);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logger.warning("startGameImmediately failed for arena " + arenaId
                    + ": " + ex.getMessage());
            return forceStartAndSkipLobby(arenaId);
        }
    }

    public void stopArena(int arenaId) {
        if (!available || stopArena == null) {
            return;
        }
        try {
            Object lifecycle = getLifecycleManager.invoke(mainInstance);
            if (lifecycle == null) {
                return;
            }
            Object countdownManager = getCountdownManager.invoke(mainInstance);
            if (countdownManager != null) {
                cancelLobbyCountdown.invoke(countdownManager, arenaId);
                cancelLimboCountdown.invoke(countdownManager, arenaId);
            }
            stopArena.invoke(lifecycle, arenaId);
        } catch (ReflectiveOperationException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            logger.warning("stopArena failed: " + cause.getMessage());
        }
    }

    private void lockArenaToMinigame(int arenaId, String gameId)
            throws ReflectiveOperationException {
        Object arena = getArena(arenaId);
        if (arena == null) {
            throw new IllegalStateException("Arena " + arenaId + " not found");
        }

        arenaClearPlayedMinigames.invoke(arena);
        List<String> only = new ArrayList<>(1);
        only.add(gameId);
        arenaSetAvailableMinigames.invoke(arena, only);
        arenaClearVotes.invoke(arena);
        arenaAddVote.invoke(arena, gameId);
    }

    private boolean forceStartAndSkipLobby(int arenaId) {
        try {
            Object lifecycleManager = getLifecycleManager.invoke(mainInstance);
            if (lifecycleManager == null) {
                throw new IllegalStateException("LifecycleManager is null");
            }
            forceStartArena.invoke(lifecycleManager, arenaId);

            Object countdownManager = getCountdownManager.invoke(mainInstance);
            if (countdownManager == null) {
                return false;
            }
            Object lobbyCountdown = getLobbyCountdown.invoke(countdownManager, arenaId);
            if (lobbyCountdown == null) {
                return false;
            }
            cacheLobbyMethods(lobbyCountdown);

            boolean running = lobbyIsRunning != null && (Boolean) lobbyIsRunning.invoke(lobbyCountdown);
            if (!running) {
                return true;
            }

            reduceCountdown.invoke(lobbyCountdown, 1);
            reduceCountdown.invoke(lobbyCountdown, 0);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logger.warning("forceStartAndSkipLobby failed for arena " + arenaId
                    + ": " + ex.getMessage());
            return false;
        }
    }

    private int readConfiguredRounds(int arenaId) throws ReflectiveOperationException {
        Object roundsNode = roundsNode(arenaId);
        if (roundsNode == null) {
            return 1;
        }
        configNodeGetInt = resolveNodeMethod(configNodeGetInt, roundsNode, "getInt");
        Object value = configNodeGetInt.invoke(roundsNode);
        return value instanceof Integer ? (Integer) value : 1;
    }

    private void writeConfiguredRounds(int arenaId, int rounds) throws ReflectiveOperationException {
        Object roundsNode = roundsNode(arenaId);
        if (roundsNode == null) {
            return;
        }
        configNodeSet = resolveNodeMethod(configNodeSet, roundsNode, "set", Object.class);
        configNodeSet.invoke(roundsNode, rounds);
    }

    private Object roundsNode(int arenaId) throws ReflectiveOperationException {
        Object dataManager = dataManagerField.get(mainInstance);
        if (dataManager == null) {
            return null;
        }
        Object arenas = resolveArenas(dataManager);
        if (arenas == null) {
            return null;
        }
        Object root = arenasGet.invoke(arenas, arenaId);
        if (root == null) {
            return null;
        }
        configNodeNode = resolveNodeMethod(configNodeNode, root, "node", Object[].class);
        return configNodeNode.invoke(root, (Object) new Object[]{"arena", "basic", "rounds"});
    }

    private static Method resolveNodeMethod(Method cached, Object target, String name, Class<?>... params)
            throws NoSuchMethodException {
        if (cached != null && cached.getDeclaringClass().isInstance(target)) {
            return cached;
        }
        Method resolved = accessibleMethod(target.getClass(), name, params);
        if (resolved == null) {
            throw new NoSuchMethodException(name + " not accessible on " + target.getClass().getName());
        }
        return resolved;
    }

    private static Method accessibleMethod(Class<?> type, String name, Class<?>... params) {
        Method direct = findMethod(type, name, params);
        if (direct != null && Modifier.isPublic(direct.getDeclaringClass().getModifiers())) {
            return direct;
        }
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Class<?> iface : current.getInterfaces()) {
                Method fromInterface = interfaceMethod(iface, name, params);
                if (fromInterface != null) {
                    return fromInterface;
                }
            }
        }
        return direct;
    }

    private static Method interfaceMethod(Class<?> iface, String name, Class<?>... params) {
        if (Modifier.isPublic(iface.getModifiers())) {
            Method method = findMethod(iface, name, params);
            if (method != null && Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
                return method;
            }
        }
        for (Class<?> parent : iface.getInterfaces()) {
            Method fromParent = interfaceMethod(parent, name, params);
            if (fromParent != null) {
                return fromParent;
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) {
        try {
            return type.getMethod(name, params);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    private void bindGameManagerAccess(Class<?> mainClass) {
        getGameManager = null;
        gameManagerField = null;
        isGameActive = null;

        if (!bindMethodOrField(mainClass, "getGameManager", "gameManager",
                method -> getGameManager = method, field -> gameManagerField = field)) {
            logger.warning("BlueArcade GameManager accessor unavailable "
                    + "(no getGameManager() / gameManager field). "
                    + "isGameActive checks will be skipped.");
            return;
        }

        try {
            Object gameManager = resolveGameManager();
            if (gameManager != null) {
                isGameActive = gameManager.getClass().getMethod("isGameActive", Integer.TYPE);
            }
        } catch (ReflectiveOperationException ex) {
            logger.warning("BlueArcade GameManager probe failed: " + ex.getMessage());
            isGameActive = null;
        }
    }

    private Object resolveGameManager() throws ReflectiveOperationException {
        return invokeOrGet(mainInstance, getGameManager, gameManagerField);
    }

    private Object resolveArenas(Object dataManager) throws ReflectiveOperationException {
        return invokeOrGet(dataManager, getArenas, arenasField);
    }

    private Object resolvePodiumManager(Object scoring) throws ReflectiveOperationException {
        return invokeOrGet(scoring, getPodiumManager, podiumManagerField);
    }

    @FunctionalInterface
    private interface MethodBinder {
        void bind(Method method);
    }

    @FunctionalInterface
    private interface FieldBinder {
        void bind(Field field);
    }

    private static boolean bindMethodOrField(
            Class<?> type,
            String methodName,
            String fieldName,
            MethodBinder methodBinder,
            FieldBinder fieldBinder) {
        Method method = findMethod(type, methodName);
        if (method != null) {
            methodBinder.bind(method);
            return true;
        }
        Field field = findField(type, fieldName);
        if (field != null) {
            fieldBinder.bind(field);
            return true;
        }
        return false;
    }

    private static Object invokeOrGet(Object target, Method method, Field field)
            throws ReflectiveOperationException {
        if (method != null) {
            return method.invoke(target);
        }
        if (field != null) {
            return field.get(target);
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        try {
            return type.getField(name);
        } catch (NoSuchFieldException ex) {
            return null;
        }
    }

    private Object getArena(int arenaId) {
        if (!available) {
            return null;
        }
        try {
            Object repo = getArenaRepository.invoke(mainInstance);
            if (repo == null) {
                return null;
            }
            return getArenaMethod.invoke(repo, arenaId);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private void cacheLobbyMethods(Object lobbyCountdown) throws NoSuchMethodException {
        if (lobbyIsRunning == null || reduceCountdown == null
                || !lobbyIsRunning.getDeclaringClass().isInstance(lobbyCountdown)) {
            lobbyIsRunning = lobbyCountdown.getClass().getMethod("isRunning");
            reduceCountdown = lobbyCountdown.getClass().getMethod("reduceCountdown", Integer.TYPE);
        }
    }

    public boolean isGameActive(int arenaId) {
        if (!available || isGameActive == null
                || (getGameManager == null && gameManagerField == null)) {
            return false;
        }
        try {
            Object gameManager = resolveGameManager();
            if (gameManager == null) {
                return false;
            }
            return (Boolean) isGameActive.invoke(gameManager, arenaId);
        } catch (ReflectiveOperationException ex) {
            logger.warning("isGameActive failed: " + ex.getMessage());
            return false;
        }
    }

    public void registerEliminateListener(Listener owner, EventExecutor executor) {
        if (!available) {
            logger.warning("Cannot register eliminate listener — host extras unavailable.");
            return;
        }
        if (playerEliminateEventClass == null) {
            return;
        }
        if (eliminateListenerRegistered) {
            return;
        }
        Bukkit.getPluginManager().registerEvent(
                playerEliminateEventClass,
                owner,
                EventPriority.NORMAL,
                executor,
                plugin
        );
        eliminateListenerRegistered = true;
    }

    public EliminateData readEliminateEvent(Event event) {
        if (!available || playerEliminateEventClass == null
                || !playerEliminateEventClass.isInstance(event)) {
            return null;
        }
        try {
            int arenaId = (Integer) eliminateGetArenaId.invoke(event);
            Player player = resolveEliminatePlayer(event);
            return new EliminateData(arenaId, player);
        } catch (ReflectiveOperationException ex) {
            logger.warning("Failed reading eliminate event: " + ex.getMessage());
            return null;
        }
    }

    private Player resolveEliminatePlayer(Event event) throws ReflectiveOperationException {
        if (eliminateGetPlayer != null) {
            return (Player) eliminateGetPlayer.invoke(event);
        }
        if (eliminatePlayerField != null) {
            return (Player) eliminatePlayerField.get(event);
        }
        return null;
    }

    public record EliminateData(int arenaId, Player player) {
    }
}

