package com.cosmic901.cosmicevents;

import com.cosmic901.cosmicevents.bridge.BlueArcadeBridge;
import com.cosmic901.cosmicevents.command.EventCommand;
import com.cosmic901.cosmicevents.listener.GameRestrictionListener;
import com.cosmic901.cosmicevents.placeholder.AliasPlaceholderExpansion;
import com.cosmic901.cosmicevents.placeholder.CosmicEventsPlaceholderExpansion;
import com.cosmic901.cosmicevents.service.EventManager;
import com.cosmic901.cosmicevents.util.Sounds;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class CosmicEvents extends JavaPlugin {
    private EventManager eventManager;
    private BlueArcadeBridge blueArcadeBridge;
    private GameRestrictionListener gameRestrictionListener;
    private Sounds sounds;
    private FileConfiguration soundsConfig;
    private File soundsFile;
    private FileConfiguration votesPowerConfig;
    private File votesPowerFile;
    private FileConfiguration voteGuiConfig;
    private File voteGuiFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSoundsConfig();
        reloadVotesPowerConfig();
        reloadVoteGuiConfig();

        sounds = new Sounds(this);
        sounds.reload(getSoundsConfig());

        blueArcadeBridge = new BlueArcadeBridge(this);

        if (!blueArcadeBridge.initialize()) {
            getServer().getScheduler().runTaskLater(this, this::retryBlueArcadeApi, 20L);
            getServer().getScheduler().runTaskLater(this, this::retryBlueArcadeApi, 60L);
            getServer().getScheduler().runTaskLater(this, this::retryBlueArcadeApi, 100L);
        }

        eventManager = new EventManager(this, blueArcadeBridge);

        EventCommand eventCommand = new EventCommand(this, eventManager);
        getCommand("event").setExecutor(eventCommand);
        getCommand("event").setTabCompleter(eventCommand);
        gameRestrictionListener = new GameRestrictionListener(this, eventManager);
        getServer().getPluginManager().registerEvents(gameRestrictionListener, this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CosmicEventsPlaceholderExpansion(this).register();
            new AliasPlaceholderExpansion(this, "autoevent").register();
        }
    }

    private void retryBlueArcadeApi() {
        if (blueArcadeBridge == null) {
            return;
        }
        if (!blueArcadeBridge.isAvailable()) {
            blueArcadeBridge.initialize();
        } else if (!blueArcadeBridge.getHostExtras().isAvailable()) {
            blueArcadeBridge.getHostExtras().initialize();
        }
        if (gameRestrictionListener != null) {
            gameRestrictionListener.registerEliminateListener();
        }
    }

    @Override
    public void onDisable() {
        if (eventManager != null) {
            eventManager.shutdown();
        }
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public BlueArcadeBridge getBlueArcadeBridge() {
        return blueArcadeBridge;
    }

    public Sounds getSounds() {
        return sounds;
    }

    public void reloadSoundsConfig() {
        if (soundsFile == null) {
            soundsFile = new File(getDataFolder(), "sounds.yml");
        }
        if (!soundsFile.exists()) {
            saveResource("sounds.yml", false);
        }
        soundsConfig = YamlConfiguration.loadConfiguration(soundsFile);
        if (sounds != null) {
            sounds.reload(soundsConfig);
        }
    }

    public FileConfiguration getSoundsConfig() {
        if (soundsConfig == null) {
            reloadSoundsConfig();
        }
        return soundsConfig;
    }

    public void reloadVotesPowerConfig() {
        if (votesPowerFile == null) {
            votesPowerFile = new File(getDataFolder(), "votes_power.yml");
        }
        if (!votesPowerFile.exists()) {
            saveResource("votes_power.yml", false);
        }
        votesPowerConfig = YamlConfiguration.loadConfiguration(votesPowerFile);
    }

    public FileConfiguration getVotesPowerConfig() {
        if (votesPowerConfig == null) {
            reloadVotesPowerConfig();
        }
        return votesPowerConfig;
    }

    public void reloadVoteGuiConfig() {
        if (voteGuiFile == null) {
            voteGuiFile = new File(getDataFolder(), "vote_gui.yml");
        }
        if (!voteGuiFile.exists()) {
            saveResource("vote_gui.yml", false);
        }
        voteGuiConfig = YamlConfiguration.loadConfiguration(voteGuiFile);
    }

    public FileConfiguration getVoteGuiConfig() {
        if (voteGuiConfig == null) {
            reloadVoteGuiConfig();
        }
        return voteGuiConfig;
    }
}

