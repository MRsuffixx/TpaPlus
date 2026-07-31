package com.mrsuffix.tpapro;

import com.mrsuffix.tpapro.config.ConfigurationBundle.Storage;
import com.mrsuffix.tpapro.config.ConfigurationBundle.StorageType;
import com.mrsuffix.tpapro.database.connection.SqlStorage;
import com.mrsuffix.tpapro.database.repository.SqlPlayerDataRepository;
import com.mrsuffix.tpapro.settings.PlayerSettings;
import com.mrsuffix.tpapro.history.HistoryEntry;
import com.mrsuffix.tpapro.history.TeleportKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SqlRepositoryTest {
    @TempDir Path temporary;
    @Test void sqliteRepositoryEnforcesRelationshipUniquenessAndPersistsSettings() {
        Storage config = new Storage(StorageType.SQLITE, "jdbc:sqlite:" + temporary.resolve("test.db"), "", "", 1, 1,
                10_000, 1_800_000, 0, 5, false);
        try (SqlStorage storage = new SqlStorage(config, Logger.getAnonymousLogger())) {
            storage.initialize().join(); SqlPlayerDataRepository repository = new SqlPlayerDataRepository(storage);
            UUID owner = UUID.randomUUID(), target = UUID.randomUUID();
            assertThat(repository.addTrusted(owner, target).join()).isTrue();
            assertThat(repository.addTrusted(owner, target).join()).isFalse();
            PlayerSettings settings = PlayerSettings.defaults("tr_TR").withAutoAccept(true);
            repository.saveSettings(owner, settings).join();
            var loaded = repository.load(owner, PlayerSettings.defaults("en_US")).join();
            assertThat(loaded.trusted()).containsExactly(target); assertThat(loaded.settings().language()).isEqualTo("tr_TR");
            assertThat(loaded.settings().autoAccept()).isTrue();
        }
    }

    @Test void historyRetentionPrunesOnlyRowsOlderThanCutoff() {
        Storage config = new Storage(StorageType.SQLITE, "jdbc:sqlite:" + temporary.resolve("history.db"), "", "", 1, 1,
                10_000, 1_800_000, 0, 5, false);
        try (SqlStorage storage = new SqlStorage(config, Logger.getAnonymousLogger())) {
            storage.initialize().join(); SqlPlayerDataRepository repository = new SqlPlayerDataRepository(storage);
            UUID player = UUID.randomUUID(), world = UUID.randomUUID(); Instant cutoff = Instant.parse("2026-08-01T00:00:00Z");
            repository.addHistory(new HistoryEntry(UUID.randomUUID(), player, cutoff.minusSeconds(1), world, "world",
                    0, 64, 0, TeleportKind.TPA, null)).join();
            repository.addHistory(new HistoryEntry(UUID.randomUUID(), player, cutoff, world, "world",
                    1, 64, 1, TeleportKind.TPA_HERE, null)).join();
            assertThat(repository.pruneHistoryBefore(cutoff).join()).isOne();
            assertThat(repository.history(player, 10, 0).join()).singleElement().satisfies(entry ->
                    assertThat(entry.timestamp()).isEqualTo(cutoff));
        }
    }

    @Test void invalidStoredLanguageFallsBackAndEmitsOperatorWarning() {
        Storage config = new Storage(StorageType.SQLITE, "jdbc:sqlite:" + temporary.resolve("language.db"), "", "", 1, 1,
                10_000, 1_800_000, 0, 5, false);
        Logger logger = Logger.getAnonymousLogger(); java.util.List<String> warnings = new java.util.concurrent.CopyOnWriteArrayList<>();
        logger.setUseParentHandlers(false); logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { warnings.add(record.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        try (SqlStorage storage = new SqlStorage(config, logger)) {
            storage.initialize().join(); SqlPlayerDataRepository repository = new SqlPlayerDataRepository(storage, logger);
            UUID player = UUID.randomUUID(); repository.saveSettings(player, PlayerSettings.defaults("en_US")).join();
            storage.query(connection -> {
                try (var statement = connection.prepareStatement("UPDATE tpapro_players SET language=? WHERE uuid=?")) {
                    statement.setString(1, "../../unsafe"); statement.setString(2, player.toString()); statement.executeUpdate();
                }
                return null;
            }).join();
            assertThat(repository.load(player, PlayerSettings.defaults("en_US")).join().settings().language()).isNull();
            assertThat(warnings).anyMatch(message -> message.contains("invalid stored language") && message.contains(player.toString()));
        }
    }
}
