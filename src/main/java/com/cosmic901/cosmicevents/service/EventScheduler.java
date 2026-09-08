package com.cosmic901.cosmicevents.service;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Consumer;

public final class EventScheduler {
    private final JavaPlugin plugin;

    private boolean enabled = true;
    private double intervalHours = 2.0;
    private long nextEventTimeMillis;
    private BukkitTask nextEventTask;
    private Consumer<Void> onDue;

    public EventScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void configure(boolean enabled, double intervalHours, Consumer<Void> onDue) {
        this.enabled = enabled;
        this.intervalHours = Math.max(0.01, intervalHours);
        this.onDue = onDue;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getNextEventTimeMillis() {
        return nextEventTimeMillis;
    }

    public void scheduleNext() {
        cancel();
        nextEventTimeMillis = System.currentTimeMillis() + hoursToMillis(intervalHours);
        if (!enabled || onDue == null) {
            return;
        }
        long delayTicks = Math.max(1L, millisToTicks(hoursToMillis(intervalHours)));
        nextEventTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            nextEventTask = null;
            if (enabled && onDue != null) {
                onDue.accept(null);
            }
        }, delayTicks);
    }

    public void scheduleImmediateCheck() {

        if (!enabled || onDue == null) {
            scheduleNext();
            return;
        }
        long delayMillis = nextEventTimeMillis - System.currentTimeMillis();
        cancelTaskOnly();
        if (delayMillis <= 0L) {
            nextEventTask = plugin.getServer().getScheduler().runTask(plugin, () -> {
                nextEventTask = null;
                onDue.accept(null);
            });
        } else {
            nextEventTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                nextEventTask = null;
                onDue.accept(null);
            }, Math.max(1L, millisToTicks(delayMillis)));
        }
    }

    public void cancel() {
        cancelTaskOnly();
    }

    private void cancelTaskOnly() {
        if (nextEventTask != null) {
            nextEventTask.cancel();
            nextEventTask = null;
        }
    }

    public String formatRemaining() {
        long diff = nextEventTimeMillis - System.currentTimeMillis();
        if (diff <= 0L) {
            return "Starting soon...";
        }
        long seconds = diff / 1000L;
        long hours = seconds / 3600L;
        long minutes = seconds % 3600L / 60L;
        long secs = seconds % 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    private static long hoursToMillis(double hours) {
        return (long) (hours * 3600.0d * 1000.0d);
    }

    private static long millisToTicks(long millis) {
        return Math.max(1L, millis / 50L);
    }
}

