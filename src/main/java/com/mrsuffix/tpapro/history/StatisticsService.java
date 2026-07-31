package com.mrsuffix.tpapro.history;

import com.mrsuffix.tpapro.database.repository.PlayerDataRepository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class StatisticsService {
    public enum Metric { REQUEST_SENT, REQUEST_RECEIVED, REQUEST_ACCEPTED, REQUEST_DENIED, REQUEST_EXPIRED,
        TELEPORT_SUCCESS, TELEPORT_FAILED, WARMUP_CANCELLED }
    private final PlayerDataRepository repository;
    private final Logger logger;
    private final Map<UUID, Accumulator> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Metric, LongAdder>> session = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastTargets = new ConcurrentHashMap<>();

    public StatisticsService(PlayerDataRepository repository, Logger logger) { this.repository = repository; this.logger = logger; }
    public void increment(UUID player, Metric metric) {
        pending.computeIfAbsent(player, ignored -> new Accumulator()).add(metric);
        session.computeIfAbsent(player, ignored -> new ConcurrentHashMap<>()).computeIfAbsent(metric, ignored -> new LongAdder()).increment();
    }
    public void cost(UUID player, double amount) { if (Double.isFinite(amount) && amount > 0) pending.computeIfAbsent(player, ignored -> new Accumulator()).cost.add(amount); }
    public void target(UUID player, UUID target) {
        pending.computeIfAbsent(player, ignored -> new Accumulator()).targets.computeIfAbsent(target, ignored -> new LongAdder()).increment();
        lastTargets.put(player, target);
    }
    public UUID lastTarget(UUID player) { return lastTargets.get(player); }
    public CompletableFuture<PlayerStatistics> load(UUID player) { return repository.statistics(player); }

    public long sessionValue(UUID player, Metric metric) {
        return session.getOrDefault(player, Map.of()).getOrDefault(metric, new LongAdder()).sum();
    }

    public CompletableFuture<Void> flush() {
        java.util.List<CompletableFuture<Void>> writes = new java.util.ArrayList<>();
        pending.forEach((player, accumulator) -> {
            Delta delta = accumulator.drain();
            if (delta.empty()) return;
            CompletableFuture<Void> write = repository.applyStatisticsDelta(player, delta.stats(), delta.targets()).exceptionally(error -> {
                accumulator.restore(delta); logger.log(Level.WARNING, "Could not flush statistics for " + player, error); return null;
            });
            writes.add(write);
        });
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    private static final class Accumulator {
        private final LongAdder sent = new LongAdder(), received = new LongAdder(), accepted = new LongAdder(), denied = new LongAdder();
        private final LongAdder expired = new LongAdder(), successful = new LongAdder(), failed = new LongAdder(), cancelled = new LongAdder();
        private final DoubleAdder cost = new DoubleAdder();
        private final Map<UUID, LongAdder> targets = new ConcurrentHashMap<>();
        void add(Metric metric) { switch (metric) {
            case REQUEST_SENT -> sent.increment(); case REQUEST_RECEIVED -> received.increment();
            case REQUEST_ACCEPTED -> accepted.increment(); case REQUEST_DENIED -> denied.increment();
            case REQUEST_EXPIRED -> expired.increment(); case TELEPORT_SUCCESS -> successful.increment();
            case TELEPORT_FAILED -> failed.increment(); case WARMUP_CANCELLED -> cancelled.increment();
        } }
        Delta drain() {
            Map<UUID, Long> drainedTargets = new java.util.HashMap<>();
            targets.forEach((id, value) -> { long amount = value.sumThenReset(); if (amount > 0) drainedTargets.put(id, amount); });
            return new Delta(new PlayerStatistics(sent.sumThenReset(), received.sumThenReset(), accepted.sumThenReset(),
                    denied.sumThenReset(), expired.sumThenReset(), successful.sumThenReset(), failed.sumThenReset(),
                    cancelled.sumThenReset(), cost.sumThenReset(), null), Map.copyOf(drainedTargets));
        }
        void restore(Delta d) {
            PlayerStatistics s = d.stats(); sent.add(s.requestsSent()); received.add(s.requestsReceived()); accepted.add(s.requestsAccepted());
            denied.add(s.requestsDenied()); expired.add(s.requestsExpired()); successful.add(s.successfulTeleports());
            failed.add(s.failedTeleports()); cancelled.add(s.cancelledWarmups()); cost.add(s.totalEconomyCost());
            d.targets().forEach((id, value) -> targets.computeIfAbsent(id, ignored -> new LongAdder()).add(value));
        }
    }
    private record Delta(PlayerStatistics stats, Map<UUID, Long> targets) {
        boolean empty() { return stats.requestsSent() == 0 && stats.requestsReceived() == 0 && stats.requestsAccepted() == 0
                && stats.requestsDenied() == 0 && stats.requestsExpired() == 0 && stats.successfulTeleports() == 0
                && stats.failedTeleports() == 0 && stats.cancelledWarmups() == 0 && stats.totalEconomyCost() == 0 && targets.isEmpty(); }
    }
}
