package com.mrsuffix.tpapro.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

final class ConfigBackupManager {
    private ConfigBackupManager() { }

    static Path backup(Path source, Path backupDirectory, int retain) throws IOException {
        Files.createDirectories(backupDirectory);
        Path backup = Files.createTempFile(backupDirectory, source.getFileName() + ".backup-", ".yml");
        Files.copy(source, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        rotate(backupDirectory, source.getFileName() + ".backup-", Math.max(1, retain));
        return backup;
    }

    private static void rotate(Path directory, String prefix, int retain) throws IOException {
        List<Path> backups;
        try (var files = Files.list(directory)) {
            backups = files.filter(path -> path.getFileName().toString().startsWith(prefix))
                    .sorted(Comparator.comparing(ConfigBackupManager::modified).reversed()
                            .thenComparing(path -> path.getFileName().toString(), Comparator.reverseOrder()))
                    .toList();
        }
        for (Path expired : backups.stream().skip(retain).toList()) Files.deleteIfExists(expired);
    }

    private static long modified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException ignored) { return Long.MIN_VALUE; }
    }
}
