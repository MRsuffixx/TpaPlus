package com.mrsuffix.tpapro.database.connection;

import com.mrsuffix.tpapro.config.ConfigurationBundle.Storage;
import com.mrsuffix.tpapro.config.ConfigurationBundle.StorageType;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SqlStorage implements AutoCloseable {
    public enum Status { NEW, CONNECTING, CONNECTED, FAILED, CLOSED }
    @FunctionalInterface public interface SqlFunction<T> { T apply(Connection connection) throws SQLException; }

    private final Storage settings;
    private final Logger logger;
    private final ExecutorService executor;
    private final AtomicReference<Status> status = new AtomicReference<>(Status.NEW);
    private volatile HikariDataSource dataSource;
    private volatile CompletableFuture<Void> readiness;

    public SqlStorage(Storage settings, Logger logger) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.logger = Objects.requireNonNull(logger, "logger");
        int threads = settings.type() == StorageType.SQLITE ? 1 : Math.max(2, Math.min(8, settings.maximumPoolSize()));
        this.executor = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "TpaPro-Database"); thread.setDaemon(true); return thread;
        });
    }

    public synchronized CompletableFuture<Void> initialize() {
        if (readiness != null) return readiness;
        status.set(Status.CONNECTING);
        readiness = CompletableFuture.runAsync(() -> {
            try {
                loadDriver();
                HikariConfig hikari = new HikariConfig();
                hikari.setPoolName("TpaPro-" + settings.type());
                hikari.setJdbcUrl(settings.jdbcUrl());
                if (!settings.username().isBlank()) hikari.setUsername(settings.username());
                if (!settings.password().isBlank()) hikari.setPassword(settings.password());
                hikari.setMaximumPoolSize(settings.maximumPoolSize());
                hikari.setMinimumIdle(settings.minimumIdle());
                hikari.setConnectionTimeout(settings.connectionTimeoutMillis());
                hikari.setMaxLifetime(settings.maxLifetimeMillis());
                hikari.setLeakDetectionThreshold(settings.leakDetectionMillis());
                if (settings.type() == StorageType.SQLITE) {
                    hikari.setConnectionTestQuery("SELECT 1");
                    hikari.addDataSourceProperty("busy_timeout", "5000");
                }
                dataSource = new HikariDataSource(hikari);
                migrate();
                if (!status.compareAndSet(Status.CONNECTING, Status.CONNECTED)) {
                    dataSource.close(); throw new IllegalStateException("Storage was closed during initialization");
                }
                logger.info("Database connected and schema migrated (" + settings.type() + ").");
            } catch (Throwable error) {
                status.set(Status.FAILED);
                HikariDataSource source = dataSource;
                if (source != null) source.close();
                throw new CompletionException(error);
            }
        }, executor);
        return readiness;
    }

    public <T> CompletableFuture<T> query(SqlFunction<T> operation) {
        Objects.requireNonNull(operation, "operation");
        CompletableFuture<Void> ready = initialize();
        return ready.thenApplyAsync(ignored -> {
            if (status.get() != Status.CONNECTED) throw new CompletionException(new SQLException("Storage unavailable"));
            try (Connection connection = dataSource.getConnection()) { return operation.apply(connection); }
            catch (SQLException error) { throw new CompletionException(error); }
        }, executor);
    }

    public Status status() { return status.get(); }
    public StorageType type() { return settings.type(); }

    private void loadDriver() throws ClassNotFoundException {
        Class.forName(switch (settings.type()) {
            case SQLITE -> "org.sqlite.JDBC";
            case MYSQL -> "com.mysql.cj.jdbc.Driver";
            case MARIADB -> "org.mariadb.jdbc.Driver";
            case POSTGRESQL -> "org.postgresql.Driver";
        });
    }

    private void migrate() throws SQLException {
        List<String> tables = List.of(
                "CREATE TABLE IF NOT EXISTS tpapro_meta (meta_key VARCHAR(64) PRIMARY KEY, meta_value VARCHAR(255) NOT NULL)",
                "CREATE TABLE IF NOT EXISTS tpapro_players (uuid VARCHAR(36) PRIMARY KEY, privacy_mode VARCHAR(32) NOT NULL, auto_accept BOOLEAN NOT NULL, auto_trusted_only BOOLEAN NOT NULL, chat_notifications BOOLEAN NOT NULL, actionbar_notifications BOOLEAN NOT NULL, title_notifications BOOLEAN NOT NULL, sounds BOOLEAN NOT NULL, trap_warnings BOOLEAN NOT NULL, language VARCHAR(16))",
                "CREATE TABLE IF NOT EXISTS tpapro_trusted (owner_uuid VARCHAR(36) NOT NULL, target_uuid VARCHAR(36) NOT NULL, PRIMARY KEY(owner_uuid, target_uuid))",
                "CREATE TABLE IF NOT EXISTS tpapro_blocked (owner_uuid VARCHAR(36) NOT NULL, target_uuid VARCHAR(36) NOT NULL, PRIMARY KEY(owner_uuid, target_uuid))",
                "CREATE TABLE IF NOT EXISTS tpapro_auto_accept (owner_uuid VARCHAR(36) NOT NULL, target_uuid VARCHAR(36) NOT NULL, PRIMARY KEY(owner_uuid, target_uuid))",
                "CREATE TABLE IF NOT EXISTS tpapro_back (player_uuid VARCHAR(36) PRIMARY KEY, world_uuid VARCHAR(36) NOT NULL, world_name VARCHAR(255) NOT NULL, x DOUBLE PRECISION NOT NULL, y DOUBLE PRECISION NOT NULL, z DOUBLE PRECISION NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL, saved_at BIGINT NOT NULL)",
                "CREATE TABLE IF NOT EXISTS tpapro_history (id VARCHAR(36) PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL, timestamp_ms BIGINT NOT NULL, world_uuid VARCHAR(36) NOT NULL, world_name VARCHAR(255) NOT NULL, x DOUBLE PRECISION NOT NULL, y DOUBLE PRECISION NOT NULL, z DOUBLE PRECISION NOT NULL, teleport_type VARCHAR(32) NOT NULL, related_uuid VARCHAR(36))",
                "CREATE TABLE IF NOT EXISTS tpapro_statistics (player_uuid VARCHAR(36) PRIMARY KEY, requests_sent BIGINT NOT NULL, requests_received BIGINT NOT NULL, requests_accepted BIGINT NOT NULL, requests_denied BIGINT NOT NULL, requests_expired BIGINT NOT NULL, successful_teleports BIGINT NOT NULL, failed_teleports BIGINT NOT NULL, cancelled_warmups BIGINT NOT NULL, total_cost DOUBLE PRECISION NOT NULL)",
                "CREATE TABLE IF NOT EXISTS tpapro_stat_targets (player_uuid VARCHAR(36) NOT NULL, target_uuid VARCHAR(36) NOT NULL, target_count BIGINT NOT NULL, PRIMARY KEY(player_uuid, target_uuid))",
                "CREATE TABLE IF NOT EXISTS tpapro_cooldowns (player_uuid VARCHAR(36) NOT NULL, cooldown_type VARCHAR(32) NOT NULL, expires_at BIGINT NOT NULL, PRIMARY KEY(player_uuid, cooldown_type))"
        );
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            if (settings.type() == StorageType.SQLITE) {
                statement.execute("PRAGMA journal_mode=WAL"); statement.execute("PRAGMA foreign_keys=ON");
            }
            for (String sql : tables) statement.executeUpdate(sql);
            createIndex(connection, "tpapro_history", "idx_tpapro_history_player_time", "player_uuid, timestamp_ms");
            createIndex(connection, "tpapro_trusted", "idx_tpapro_trusted_target", "target_uuid");
            createIndex(connection, "tpapro_blocked", "idx_tpapro_blocked_target", "target_uuid");
        }
    }

    private void createIndex(Connection connection, String table, String index, String columns) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getIndexInfo(null, null, table, false, false)) {
            while (result.next()) {
                String found = result.getString("INDEX_NAME");
                if (found != null && found.equalsIgnoreCase(index)) return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE INDEX " + index + " ON " + table + " (" + columns + ")");
        }
    }

    @Override public synchronized void close() {
        if (status.getAndSet(Status.CLOSED) == Status.CLOSED) return;
        HikariDataSource source = dataSource;
        if (source != null) source.close();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(settings.shutdownTimeoutSeconds(), TimeUnit.SECONDS)) {
                logger.warning("Database executor did not stop before the configured shutdown timeout.");
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); executor.shutdownNow();
            logger.log(Level.WARNING, "Interrupted while closing database executor", interrupted);
        }
    }
}
