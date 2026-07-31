package com.mrsuffix.tpapro;

import com.mrsuffix.tpapro.cooldown.CooldownType;
import com.mrsuffix.tpapro.database.model.PlayerData;
import com.mrsuffix.tpapro.database.repository.PlayerDataRepository;
import com.mrsuffix.tpapro.history.HistoryEntry;
import com.mrsuffix.tpapro.history.PlayerStatistics;
import com.mrsuffix.tpapro.history.StoredLocation;
import com.mrsuffix.tpapro.settings.PlayerSettings;
import com.mrsuffix.tpapro.settings.PrivacyMode;
import com.mrsuffix.tpapro.user.UserService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class UserServiceConcurrencyTest {
    @Test void lateLoadIsDiscardedAfterUnload() {
        ControlledRepository repository = new ControlledRepository(); UserService users = service(repository); UUID player = UUID.randomUUID();
        CompletableFuture<?> load = users.load(player); users.unload(player);
        repository.load.complete(new PlayerData(PlayerSettings.defaults("en_US"), Set.of(), Set.of(), Set.of(), null));
        load.join();
        assertThat(users.loaded(player)).isFalse(); assertThat(users.cached(player)).isEmpty();
    }

    @Test void failedSettingsWriteRollsBackAndWritesAreSerialized() {
        ControlledRepository repository = new ControlledRepository(); UserService users = service(repository); UUID player = UUID.randomUUID();
        repository.load.complete(new PlayerData(PlayerSettings.defaults("en_US"), Set.of(), Set.of(), Set.of(), null));
        users.load(player).join();
        PlayerSettings first = users.get(player).settings().withPrivacy(PrivacyMode.DISABLED);
        CompletableFuture<Boolean> firstResult = users.updateSettings(player, first);
        PlayerSettings second = first.withLanguage("tr_TR"); CompletableFuture<Boolean> secondResult = users.updateSettings(player, second);
        assertThat(repository.saveCalls.get()).isOne();
        repository.firstSave.completeExceptionally(new IllegalStateException("database unavailable"));
        assertThat(firstResult.join()).isFalse(); assertThat(repository.saveCalls.get()).isEqualTo(2);
        repository.secondSave.complete(null);
        assertThat(secondResult.join()).isTrue(); assertThat(users.get(player).settings()).isEqualTo(second);
    }

    private static UserService service(ControlledRepository repository) {
        return new UserService(repository, Logger.getAnonymousLogger(), "en_US");
    }

    private static final class ControlledRepository implements PlayerDataRepository {
        private final CompletableFuture<PlayerData> load = new CompletableFuture<>();
        private final CompletableFuture<Void> firstSave = new CompletableFuture<>(), secondSave = new CompletableFuture<>();
        private final AtomicInteger saveCalls = new AtomicInteger();
        @Override public CompletableFuture<PlayerData> load(UUID id, PlayerSettings defaults) { return load; }
        @Override public CompletableFuture<Void> saveSettings(UUID id, PlayerSettings settings) {
            return saveCalls.getAndIncrement() == 0 ? firstSave : secondSave;
        }
        @Override public CompletableFuture<Boolean> addTrusted(UUID owner, UUID target) { return CompletableFuture.completedFuture(true); }
        @Override public CompletableFuture<Boolean> removeTrusted(UUID owner, UUID target) { return CompletableFuture.completedFuture(true); }
        @Override public CompletableFuture<Boolean> addBlocked(UUID owner, UUID target) { return CompletableFuture.completedFuture(true); }
        @Override public CompletableFuture<Boolean> removeBlocked(UUID owner, UUID target) { return CompletableFuture.completedFuture(true); }
        @Override public CompletableFuture<Boolean> addAutoAccept(UUID owner, UUID target) { return CompletableFuture.completedFuture(true); }
        @Override public CompletableFuture<Boolean> removeAutoAccept(UUID owner, UUID target) { return CompletableFuture.completedFuture(true); }
        @Override public CompletableFuture<Void> saveBackLocation(UUID id, StoredLocation location) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> addHistory(HistoryEntry entry) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<List<HistoryEntry>> history(UUID id, int limit, int offset) { return CompletableFuture.completedFuture(List.of()); }
        @Override public CompletableFuture<Integer> pruneHistoryBefore(Instant cutoff) { return CompletableFuture.completedFuture(0); }
        @Override public CompletableFuture<Void> applyStatisticsDelta(UUID id, PlayerStatistics delta, Map<UUID, Long> targets) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<PlayerStatistics> statistics(UUID id) { return CompletableFuture.completedFuture(PlayerStatistics.empty()); }
        @Override public CompletableFuture<Void> saveCooldowns(UUID id, Map<CooldownType, Instant> values) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Map<CooldownType, Instant>> loadCooldowns(UUID id) { return CompletableFuture.completedFuture(Map.of()); }
    }
}
