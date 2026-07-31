package com.mrsuffix.tpapro;

import com.mrsuffix.tpapro.config.ConfigurationBundle.Storage;
import com.mrsuffix.tpapro.config.ConfigurationBundle.StorageType;
import com.mrsuffix.tpapro.database.connection.SqlStorage;
import com.mrsuffix.tpapro.database.repository.SqlPlayerDataRepository;
import com.mrsuffix.tpapro.settings.PlayerSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

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
}
