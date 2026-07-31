package com.mrsuffix.tpapro.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigBackupManagerTest {
    @TempDir Path temporary;

    @Test void backupRotationRetainsOnlyNewestThreeCopies() throws Exception {
        Path source = temporary.resolve("config.yml"), backups = temporary.resolve("backups");
        Files.writeString(source, "config-version: 1");
        for (int index = 0; index < 5; index++) ConfigBackupManager.backup(source, backups, 3);
        try (var files = Files.list(backups)) {
            assertThat(files.filter(path -> path.getFileName().toString().startsWith("config.yml.backup-"))).hasSize(3);
        }
    }
}
