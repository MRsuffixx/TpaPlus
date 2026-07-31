package com.mrsuffix.tpapro.database;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class AsyncWriteTracker {
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();

    public <T> CompletableFuture<T> track(CompletableFuture<T> write) {
        pending.add(write);
        write.whenComplete((ignored, error) -> pending.remove(write));
        return write;
    }

    public boolean await(Duration timeout) {
        if (timeout.isNegative()) throw new IllegalArgumentException("timeout must not be negative");
        long deadline;
        try { deadline = Math.addExact(System.nanoTime(), timeout.toNanos()); }
        catch (ArithmeticException overflow) { deadline = Long.MAX_VALUE; }
        while (!pending.isEmpty()) {
            CompletableFuture<?>[] snapshot = pending.toArray(CompletableFuture[]::new);
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return false;
            try { CompletableFuture.allOf(snapshot).get(remaining, TimeUnit.NANOSECONDS); }
            catch (ExecutionException completedExceptionally) { /* Completion still drains the tracked write. */ }
            catch (TimeoutException timeoutException) { return false; }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
        }
        return true;
    }

    public int size() { return pending.size(); }
}
