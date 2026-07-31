package com.mrsuffix.tpapro.scheduler;

public final class ReloadableTask implements AutoCloseable {
    private ScheduledTask current;
    public synchronized void replace(ScheduledTask replacement) {
        if (current != null) current.cancel();
        current = replacement;
    }
    public synchronized boolean active() { return current != null && !current.cancelled(); }
    @Override public synchronized void close() { if (current != null) current.cancel(); current = null; }
}
