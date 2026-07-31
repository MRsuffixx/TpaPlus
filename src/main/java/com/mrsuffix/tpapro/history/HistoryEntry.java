package com.mrsuffix.tpapro.history;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record HistoryEntry(UUID id, UUID playerId, Instant timestamp, UUID worldId, String worldName,
                           double x, double y, double z, TeleportKind kind, UUID relatedPlayerId) {
    public HistoryEntry {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(timestamp, "timestamp"); Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(worldName, "worldName"); Objects.requireNonNull(kind, "kind");
    }

    public static HistoryEntry create(UUID playerId, StoredLocation location, TeleportKind kind, UUID related) {
        return new HistoryEntry(UUID.randomUUID(), playerId, location.savedAt(), location.worldId(), location.worldName(),
                location.x(), location.y(), location.z(), kind, related);
    }
}
