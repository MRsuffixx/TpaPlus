package com.mrsuffix.tpapro.database.repository;

import com.mrsuffix.tpapro.cooldown.CooldownType;
import com.mrsuffix.tpapro.database.model.PlayerData;
import com.mrsuffix.tpapro.history.HistoryEntry;
import com.mrsuffix.tpapro.history.PlayerStatistics;
import com.mrsuffix.tpapro.history.StoredLocation;
import com.mrsuffix.tpapro.settings.PlayerSettings;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerDataRepository {
    CompletableFuture<PlayerData> load(UUID playerId, PlayerSettings defaults);
    CompletableFuture<Void> saveSettings(UUID playerId, PlayerSettings settings);
    CompletableFuture<Boolean> addTrusted(UUID owner, UUID target);
    CompletableFuture<Boolean> removeTrusted(UUID owner, UUID target);
    CompletableFuture<Boolean> addBlocked(UUID owner, UUID target);
    CompletableFuture<Boolean> removeBlocked(UUID owner, UUID target);
    CompletableFuture<Boolean> addAutoAccept(UUID owner, UUID target);
    CompletableFuture<Boolean> removeAutoAccept(UUID owner, UUID target);
    CompletableFuture<Void> saveBackLocation(UUID playerId, StoredLocation location);
    CompletableFuture<Void> addHistory(HistoryEntry entry);
    CompletableFuture<List<HistoryEntry>> history(UUID playerId, int limit, int offset);
    CompletableFuture<Integer> pruneHistoryBefore(Instant cutoff);
    CompletableFuture<Void> applyStatisticsDelta(UUID playerId, PlayerStatistics delta, Map<UUID, Long> targetDeltas);
    CompletableFuture<PlayerStatistics> statistics(UUID playerId);
    CompletableFuture<Void> saveCooldowns(UUID playerId, Map<CooldownType, Instant> cooldowns);
    CompletableFuture<Map<CooldownType, Instant>> loadCooldowns(UUID playerId);
}
