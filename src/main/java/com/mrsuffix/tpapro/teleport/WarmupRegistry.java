package com.mrsuffix.tpapro.teleport;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WarmupRegistry {
    private final Map<UUID, WarmupSession> byPlayer = new ConcurrentHashMap<>();
    public WarmupSession start(UUID player, UUID request, PositionSnapshot anchor, Instant now, Instant completesAt) {
        if (completesAt.isBefore(now)) throw new IllegalArgumentException("Completion before start");
        WarmupSession session = new WarmupSession(UUID.randomUUID(), player, request, anchor, now, completesAt);
        byPlayer.put(player, session); return session;
    }
    public Optional<WarmupSession> current(UUID player) { return Optional.ofNullable(byPlayer.get(player)); }
    public boolean isCurrent(UUID player, UUID sessionId) { WarmupSession session = byPlayer.get(player); return session != null && session.id().equals(sessionId); }
    public Optional<WarmupSession> cancel(UUID player, UUID expectedSession) {
        WarmupSession session = byPlayer.get(player);
        if (session == null || expectedSession != null && !session.id().equals(expectedSession)) return Optional.empty();
        return byPlayer.remove(player, session) ? Optional.of(session) : Optional.empty();
    }
    public Optional<WarmupSession> complete(UUID player, UUID sessionId, Instant now) {
        WarmupSession session = byPlayer.get(player);
        if (session == null || !session.id().equals(sessionId) || now.isBefore(session.completesAt())) return Optional.empty();
        return byPlayer.remove(player, session) ? Optional.of(session) : Optional.empty();
    }
    public int size() { return byPlayer.size(); }
    public void clear() { byPlayer.clear(); }
}
