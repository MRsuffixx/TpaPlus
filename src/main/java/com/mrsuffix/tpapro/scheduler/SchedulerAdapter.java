package com.mrsuffix.tpapro.scheduler;

import java.time.Duration;

public interface SchedulerAdapter {
    ScheduledTask run(Runnable action);
    ScheduledTask runLater(Runnable action, Duration delay);
    ScheduledTask runRepeating(Runnable action, Duration initialDelay, Duration period);
    void runAsync(Runnable action);
    boolean isPrimaryThread();
    void cancelAll();
}
