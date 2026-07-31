package com.mrsuffix.tpapro;

import com.mrsuffix.tpapro.cooldown.CooldownType;
import com.mrsuffix.tpapro.database.model.PlayerData;
import com.mrsuffix.tpapro.database.repository.PlayerDataRepository;
import com.mrsuffix.tpapro.history.HistoryEntry;
import com.mrsuffix.tpapro.history.PlayerStatistics;
import com.mrsuffix.tpapro.history.StoredLocation;
import com.mrsuffix.tpapro.settings.PlayerSettings;
import com.mrsuffix.tpapro.user.UserService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedUserTest {
    @Test void trustIsDirectionalUniqueLimitedAndRejectsSelf() {
        UserService service = new UserService(new MemoryRepository(), Logger.getAnonymousLogger(), "en_US");
        UUID owner = UUID.randomUUID(), target = UUID.randomUUID();
        assertThat(service.addTrusted(owner, owner, 10)).isFalse();
        assertThat(service.addTrusted(owner, target, 10)).isTrue();
        assertThat(service.addTrusted(owner, target, 10)).isFalse();
        assertThat(service.get(owner).trusts(target)).isTrue(); assertThat(service.get(target).trusts(owner)).isFalse();
        assertThat(service.addTrusted(owner, UUID.randomUUID(), 1)).isFalse();
        assertThat(service.removeTrusted(owner, UUID.randomUUID())).isFalse();
    }
    @Test void blockRelationshipsAreUniqueAndRemovable() {
        UserService service = new UserService(new MemoryRepository(), Logger.getAnonymousLogger(), "en_US"); UUID owner = UUID.randomUUID(), target = UUID.randomUUID();
        assertThat(service.addBlocked(owner, owner)).isFalse(); assertThat(service.addBlocked(owner, target)).isTrue();
        assertThat(service.addBlocked(owner, target)).isFalse(); assertThat(service.get(owner).blocks(target)).isTrue();
        assertThat(service.removeBlocked(owner, target)).isTrue(); assertThat(service.removeBlocked(owner, target)).isFalse();
    }
    private static final class MemoryRepository implements PlayerDataRepository {
        @Override public CompletableFuture<PlayerData> load(UUID id, PlayerSettings defaults) { return CompletableFuture.completedFuture(new PlayerData(defaults, Set.of(), Set.of(), Set.of(), null)); }
        @Override public CompletableFuture<Void> saveSettings(UUID id, PlayerSettings settings) { return CompletableFuture.completedFuture(null); }
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
