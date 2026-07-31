package com.mrsuffix.tpapro.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Objects;

public final class PaperSchedulerAdapter implements SchedulerAdapter {
    private final Plugin plugin;

    public PaperSchedulerAdapter(Plugin plugin) { this.plugin = Objects.requireNonNull(plugin, "plugin"); }

    @Override public ScheduledTask run(Runnable action) {
        return wrap(Bukkit.getScheduler().runTask(plugin, action));
    }

    @Override public ScheduledTask runLater(Runnable action, Duration delay) {
        return wrap(Bukkit.getScheduler().runTaskLater(plugin, action, ticks(delay)));
    }

    @Override public ScheduledTask runRepeating(Runnable action, Duration initialDelay, Duration period) {
        return wrap(Bukkit.getScheduler().runTaskTimer(plugin, action, ticks(initialDelay), Math.max(1, ticks(period))));
    }

    @Override public void runAsync(Runnable action) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, action);
    }

    @Override public boolean isPrimaryThread() { return Bukkit.isPrimaryThread(); }
    @Override public void cancelAll() { Bukkit.getScheduler().cancelTasks(plugin); }

    private static long ticks(Duration duration) {
        if (duration.isNegative() || duration.isZero()) return 0;
        try { return Math.max(1, Math.addExact(duration.toMillis(), 49) / 50); }
        catch (ArithmeticException overflow) { return Long.MAX_VALUE; }
    }

    private static ScheduledTask wrap(BukkitTask task) {
        return new ScheduledTask() {
            @Override public void cancel() { task.cancel(); }
            @Override public boolean cancelled() { return task.isCancelled(); }
        };
    }
}
