package com.mrsuffix.tpapro.scheduler;

public interface ScheduledTask {
    void cancel();
    boolean cancelled();
}
