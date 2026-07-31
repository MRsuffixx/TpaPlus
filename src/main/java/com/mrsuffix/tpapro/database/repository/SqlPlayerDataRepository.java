package com.mrsuffix.tpapro.database.repository;

import com.mrsuffix.tpapro.config.ConfigurationBundle.StorageType;
import com.mrsuffix.tpapro.cooldown.CooldownType;
import com.mrsuffix.tpapro.database.connection.SqlStorage;
import com.mrsuffix.tpapro.database.model.PlayerData;
import com.mrsuffix.tpapro.history.HistoryEntry;
import com.mrsuffix.tpapro.history.PlayerStatistics;
import com.mrsuffix.tpapro.history.StoredLocation;
import com.mrsuffix.tpapro.history.TeleportKind;
import com.mrsuffix.tpapro.settings.PlayerSettings;
import com.mrsuffix.tpapro.settings.PrivacyMode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public final class SqlPlayerDataRepository implements PlayerDataRepository {
    private final SqlStorage storage;
    private final Logger logger;
    public SqlPlayerDataRepository(SqlStorage storage) { this(storage, Logger.getLogger(SqlPlayerDataRepository.class.getName())); }
    public SqlPlayerDataRepository(SqlStorage storage, Logger logger) { this.storage = storage; this.logger = logger; }

    @Override public CompletableFuture<PlayerData> load(UUID playerId, PlayerSettings defaults) {
        return storage.query(connection -> {
            PlayerSettings settings = loadSettings(connection, playerId);
            if (settings == null) { settings = defaults; saveSettings(connection, playerId, defaults); }
            Set<UUID> trusted = loadRelation(connection, "tpapro_trusted", playerId);
            Set<UUID> blocked = loadRelation(connection, "tpapro_blocked", playerId);
            Set<UUID> autoAccept = loadRelation(connection, "tpapro_auto_accept", playerId);
            return new PlayerData(settings, trusted, blocked, autoAccept, loadBack(connection, playerId));
        });
    }

    @Override public CompletableFuture<Void> saveSettings(UUID playerId, PlayerSettings settings) {
        return storage.query(connection -> { saveSettings(connection, playerId, settings); return null; });
    }

    @Override public CompletableFuture<Boolean> addTrusted(UUID owner, UUID target) { return addRelation("tpapro_trusted", owner, target); }
    @Override public CompletableFuture<Boolean> removeTrusted(UUID owner, UUID target) { return removeRelation("tpapro_trusted", owner, target); }
    @Override public CompletableFuture<Boolean> addBlocked(UUID owner, UUID target) { return addRelation("tpapro_blocked", owner, target); }
    @Override public CompletableFuture<Boolean> removeBlocked(UUID owner, UUID target) { return removeRelation("tpapro_blocked", owner, target); }
    @Override public CompletableFuture<Boolean> addAutoAccept(UUID owner, UUID target) { return addRelation("tpapro_auto_accept", owner, target); }
    @Override public CompletableFuture<Boolean> removeAutoAccept(UUID owner, UUID target) { return removeRelation("tpapro_auto_accept", owner, target); }

    @Override public CompletableFuture<Void> saveBackLocation(UUID playerId, StoredLocation location) {
        return storage.query(connection -> {
            String columns = "player_uuid, world_uuid, world_name, x, y, z, yaw, pitch, saved_at";
            String updates = storage.type() == StorageType.MYSQL || storage.type() == StorageType.MARIADB
                    ? "world_uuid=VALUES(world_uuid), world_name=VALUES(world_name), x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch), saved_at=VALUES(saved_at)"
                    : "world_uuid=excluded.world_uuid, world_name=excluded.world_name, x=excluded.x, y=excluded.y, z=excluded.z, yaw=excluded.yaw, pitch=excluded.pitch, saved_at=excluded.saved_at";
            String sql = "INSERT INTO tpapro_back (" + columns + ") VALUES (?,?,?,?,?,?,?,?,?) " + upsert("player_uuid", updates);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, playerId.toString()); ps.setString(2, location.worldId().toString()); ps.setString(3, location.worldName());
                ps.setDouble(4, location.x()); ps.setDouble(5, location.y()); ps.setDouble(6, location.z());
                ps.setFloat(7, location.yaw()); ps.setFloat(8, location.pitch()); ps.setLong(9, location.savedAt().toEpochMilli()); ps.executeUpdate();
            }
            return null;
        });
    }

    @Override public CompletableFuture<Void> addHistory(HistoryEntry entry) {
        return storage.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO tpapro_history (id,player_uuid,timestamp_ms,world_uuid,world_name,x,y,z,teleport_type,related_uuid) VALUES (?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, entry.id().toString()); ps.setString(2, entry.playerId().toString()); ps.setLong(3, entry.timestamp().toEpochMilli());
                ps.setString(4, entry.worldId().toString()); ps.setString(5, entry.worldName()); ps.setDouble(6, entry.x());
                ps.setDouble(7, entry.y()); ps.setDouble(8, entry.z()); ps.setString(9, entry.kind().name());
                ps.setString(10, entry.relatedPlayerId() == null ? null : entry.relatedPlayerId().toString()); ps.executeUpdate();
            }
            return null;
        });
    }

    @Override public CompletableFuture<List<HistoryEntry>> history(UUID playerId, int limit, int offset) {
        int safeLimit = Math.max(1, Math.min(1000, limit)); int safeOffset = Math.max(0, offset);
        return storage.query(connection -> {
            List<HistoryEntry> entries = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM tpapro_history WHERE player_uuid=? ORDER BY timestamp_ms DESC LIMIT ? OFFSET ?")) {
                ps.setString(1, playerId.toString()); ps.setInt(2, safeLimit); ps.setInt(3, safeOffset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) entries.add(new HistoryEntry(UUID.fromString(rs.getString("id")), playerId,
                            Instant.ofEpochMilli(rs.getLong("timestamp_ms")), UUID.fromString(rs.getString("world_uuid")),
                            rs.getString("world_name"), rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                            TeleportKind.valueOf(rs.getString("teleport_type")), nullableUuid(rs.getString("related_uuid"))));
                }
            }
            return List.copyOf(entries);
        });
    }

    @Override public CompletableFuture<Integer> pruneHistoryBefore(Instant cutoff) {
        return storage.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM tpapro_history WHERE timestamp_ms < ?")) {
                ps.setLong(1, cutoff.toEpochMilli());
                return ps.executeUpdate();
            }
        });
    }

    @Override public CompletableFuture<Void> applyStatisticsDelta(UUID playerId, PlayerStatistics d, Map<UUID, Long> targetDeltas) {
        return storage.query(connection -> {
            boolean oldAuto = connection.getAutoCommit(); connection.setAutoCommit(false);
            try {
                String values = "?,?,?,?,?,?,?,?,?,?";
                String updates = statsUpdates();
                String sql = "INSERT INTO tpapro_statistics (player_uuid,requests_sent,requests_received,requests_accepted,requests_denied,requests_expired,successful_teleports,failed_teleports,cancelled_warmups,total_cost) VALUES (" + values + ") " + upsert("player_uuid", updates);
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, playerId.toString()); ps.setLong(2, d.requestsSent()); ps.setLong(3, d.requestsReceived());
                    ps.setLong(4, d.requestsAccepted()); ps.setLong(5, d.requestsDenied()); ps.setLong(6, d.requestsExpired());
                    ps.setLong(7, d.successfulTeleports()); ps.setLong(8, d.failedTeleports()); ps.setLong(9, d.cancelledWarmups());
                    ps.setDouble(10, d.totalEconomyCost()); ps.executeUpdate();
                }
                for (Map.Entry<UUID, Long> target : targetDeltas.entrySet()) upsertTarget(connection, playerId, target.getKey(), target.getValue());
                connection.commit();
            } catch (SQLException error) { connection.rollback(); throw error; }
            finally { connection.setAutoCommit(oldAuto); }
            return null;
        });
    }

    @Override public CompletableFuture<PlayerStatistics> statistics(UUID playerId) {
        return storage.query(connection -> {
            PlayerStatistics result = PlayerStatistics.empty();
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM tpapro_statistics WHERE player_uuid=?")) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) result = new PlayerStatistics(rs.getLong("requests_sent"), rs.getLong("requests_received"),
                            rs.getLong("requests_accepted"), rs.getLong("requests_denied"), rs.getLong("requests_expired"),
                            rs.getLong("successful_teleports"), rs.getLong("failed_teleports"), rs.getLong("cancelled_warmups"),
                            rs.getDouble("total_cost"), null);
                }
            }
            UUID most = null;
            try (PreparedStatement ps = connection.prepareStatement("SELECT target_uuid FROM tpapro_stat_targets WHERE player_uuid=? ORDER BY target_count DESC LIMIT 1")) {
                ps.setString(1, playerId.toString()); try (ResultSet rs = ps.executeQuery()) { if (rs.next()) most = UUID.fromString(rs.getString(1)); }
            }
            return new PlayerStatistics(result.requestsSent(), result.requestsReceived(), result.requestsAccepted(),
                    result.requestsDenied(), result.requestsExpired(), result.successfulTeleports(), result.failedTeleports(),
                    result.cancelledWarmups(), result.totalEconomyCost(), most);
        });
    }

    @Override public CompletableFuture<Void> saveCooldowns(UUID playerId, Map<CooldownType, Instant> cooldowns) {
        return storage.query(connection -> {
            boolean old = connection.getAutoCommit(); connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM tpapro_cooldowns WHERE player_uuid=?")) {
                delete.setString(1, playerId.toString()); delete.executeUpdate();
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO tpapro_cooldowns (player_uuid,cooldown_type,expires_at) VALUES (?,?,?)")) {
                    for (Map.Entry<CooldownType, Instant> entry : cooldowns.entrySet()) {
                        insert.setString(1, playerId.toString()); insert.setString(2, entry.getKey().name());
                        insert.setLong(3, entry.getValue().toEpochMilli()); insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
            } catch (SQLException error) { connection.rollback(); throw error; }
            finally { connection.setAutoCommit(old); }
            return null;
        });
    }

    @Override public CompletableFuture<Map<CooldownType, Instant>> loadCooldowns(UUID playerId) {
        return storage.query(connection -> {
            Map<CooldownType, Instant> result = new EnumMap<>(CooldownType.class);
            try (PreparedStatement ps = connection.prepareStatement("SELECT cooldown_type,expires_at FROM tpapro_cooldowns WHERE player_uuid=?")) {
                ps.setString(1, playerId.toString()); try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try { result.put(CooldownType.valueOf(rs.getString(1)), Instant.ofEpochMilli(rs.getLong(2))); }
                        catch (RuntimeException ignored) { }
                    }
                }
            }
            return Map.copyOf(result);
        });
    }

    private PlayerSettings loadSettings(Connection c, UUID playerId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM tpapro_players WHERE uuid=?")) {
            ps.setString(1, playerId.toString()); try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                PrivacyMode privacy;
                try { privacy = PrivacyMode.valueOf(rs.getString("privacy_mode")); } catch (RuntimeException invalid) { privacy = PrivacyMode.EVERYONE; }
                String language = rs.getString("language");
                if (language != null && !language.matches("[a-z]{2}_[A-Z]{2}")) {
                    logger.warning("Ignoring invalid stored language for player " + playerId);
                    language = null;
                }
                return new PlayerSettings(privacy, rs.getBoolean("auto_accept"), rs.getBoolean("auto_trusted_only"),
                        rs.getBoolean("chat_notifications"), rs.getBoolean("actionbar_notifications"),
                        rs.getBoolean("title_notifications"), rs.getBoolean("sounds"), rs.getBoolean("trap_warnings"), language);
            }
        }
    }

    private void saveSettings(Connection c, UUID playerId, PlayerSettings s) throws SQLException {
        String updates = storage.type() == StorageType.MYSQL || storage.type() == StorageType.MARIADB
                ? "privacy_mode=VALUES(privacy_mode),auto_accept=VALUES(auto_accept),auto_trusted_only=VALUES(auto_trusted_only),chat_notifications=VALUES(chat_notifications),actionbar_notifications=VALUES(actionbar_notifications),title_notifications=VALUES(title_notifications),sounds=VALUES(sounds),trap_warnings=VALUES(trap_warnings),language=VALUES(language)"
                : "privacy_mode=excluded.privacy_mode,auto_accept=excluded.auto_accept,auto_trusted_only=excluded.auto_trusted_only,chat_notifications=excluded.chat_notifications,actionbar_notifications=excluded.actionbar_notifications,title_notifications=excluded.title_notifications,sounds=excluded.sounds,trap_warnings=excluded.trap_warnings,language=excluded.language";
        String sql = "INSERT INTO tpapro_players (uuid,privacy_mode,auto_accept,auto_trusted_only,chat_notifications,actionbar_notifications,title_notifications,sounds,trap_warnings,language) VALUES (?,?,?,?,?,?,?,?,?,?) " + upsert("uuid", updates);
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerId.toString()); ps.setString(2, s.privacyMode().name()); ps.setBoolean(3, s.autoAccept());
            ps.setBoolean(4, s.autoAcceptTrustedOnly()); ps.setBoolean(5, s.chatNotifications());
            ps.setBoolean(6, s.actionBarNotifications()); ps.setBoolean(7, s.titleNotifications());
            ps.setBoolean(8, s.sounds()); ps.setBoolean(9, s.trapWarnings()); ps.setString(10, s.language()); ps.executeUpdate();
        }
    }

    private Set<UUID> loadRelation(Connection c, String table, UUID owner) throws SQLException {
        Set<UUID> result = new HashSet<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT target_uuid FROM " + table + " WHERE owner_uuid=?")) {
            ps.setString(1, owner.toString()); try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(UUID.fromString(rs.getString(1))); }
        }
        return result;
    }

    private StoredLocation loadBack(Connection c, UUID playerId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM tpapro_back WHERE player_uuid=?")) {
            ps.setString(1, playerId.toString()); try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new StoredLocation(UUID.fromString(rs.getString("world_uuid")), rs.getString("world_name"),
                        rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"),
                        Instant.ofEpochMilli(rs.getLong("saved_at"))) : null;
            }
        }
    }

    private CompletableFuture<Boolean> addRelation(String table, UUID owner, UUID target) {
        return storage.query(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + table + " (owner_uuid,target_uuid) VALUES (?,?)")) {
                ps.setString(1, owner.toString()); ps.setString(2, target.toString()); return ps.executeUpdate() == 1;
            } catch (SQLException error) { if (constraint(error)) return false; throw error; }
        });
    }

    private CompletableFuture<Boolean> removeRelation(String table, UUID owner, UUID target) {
        return storage.query(c -> { try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE owner_uuid=? AND target_uuid=?")) {
            ps.setString(1, owner.toString()); ps.setString(2, target.toString()); return ps.executeUpdate() == 1; } });
    }

    private void upsertTarget(Connection c, UUID player, UUID target, long count) throws SQLException {
        if (count <= 0) return;
        String update = storage.type() == StorageType.MYSQL || storage.type() == StorageType.MARIADB
                ? "target_count=target_count+VALUES(target_count)" : "target_count=tpapro_stat_targets.target_count+excluded.target_count";
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO tpapro_stat_targets (player_uuid,target_uuid,target_count) VALUES (?,?,?) " + upsert("player_uuid,target_uuid", update))) {
            ps.setString(1, player.toString()); ps.setString(2, target.toString()); ps.setLong(3, count); ps.executeUpdate();
        }
    }

    private String statsUpdates() {
        return java.util.stream.Stream.of("requests_sent", "requests_received", "requests_accepted", "requests_denied",
                        "requests_expired", "successful_teleports", "failed_teleports", "cancelled_warmups", "total_cost")
                .map(column -> column + "=tpapro_statistics." + column + "+" + incoming(column))
                .collect(java.util.stream.Collectors.joining(","));
    }

    private String incoming(String column) {
        return storage.type() == StorageType.MYSQL || storage.type() == StorageType.MARIADB
                ? "VALUES(" + column + ")" : "excluded." + column;
    }

    private String upsert(String conflictColumns, String updates) {
        return storage.type() == StorageType.MYSQL || storage.type() == StorageType.MARIADB
                ? "ON DUPLICATE KEY UPDATE " + updates : "ON CONFLICT(" + conflictColumns + ") DO UPDATE SET " + updates;
    }

    private static boolean constraint(SQLException error) {
        String state = error.getSQLState(); return state != null && state.startsWith("23") || error.getErrorCode() == 19 || error.getErrorCode() == 1062;
    }
    private static UUID nullableUuid(String value) { return value == null ? null : UUID.fromString(value); }
}
