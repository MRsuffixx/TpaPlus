package com.mrsuffix.tpapro.history;

import com.mrsuffix.tpapro.database.repository.PlayerDataRepository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class HistoryService {
    private final PlayerDataRepository repository;
    private final Logger logger;
    public HistoryService(PlayerDataRepository repository, Logger logger) { this.repository = repository; this.logger = logger; }
    public void record(HistoryEntry entry) { repository.addHistory(entry).exceptionally(error -> { logger.log(Level.WARNING, "Could not store teleport history for " + entry.playerId(), error); return null; }); }
    public CompletableFuture<List<HistoryEntry>> list(UUID player, int size, int page) {
        int safeSize = Math.max(1, Math.min(1000, size)); int safePage = Math.max(1, page);
        return repository.history(player, safeSize, (safePage - 1) * safeSize);
    }
}
