package com.mrsuffix.tpapro.cooldown;

import com.mrsuffix.tpapro.util.ClockSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownService {
    private final ClockSource clock;
    private final Map<Key, Instant> expirations = new ConcurrentHashMap<>();

    public CooldownService(ClockSource clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void start(UUID playerId, CooldownType type, Duration duration) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative() || duration.isZero()) {
            expirations.remove(new Key(playerId, type));
            return;
        }
        Instant expiry;
        try { expiry = clock.now().plus(duration); } catch (RuntimeException overflow) { expiry = Instant.MAX; }
        expirations.put(new Key(playerId, type), expiry);
    }

    public Duration remaining(UUID playerId, CooldownType type) {
        Key key = new Key(playerId, type);
        Instant expires = expirations.get(key);
        if (expires == null) return Duration.ZERO;
        Instant now = clock.now();
        if (!now.isBefore(expires)) {
            expirations.remove(key, expires);
            return Duration.ZERO;
        }
        try { return Duration.between(now, expires); } catch (ArithmeticException overflow) { return Duration.ofDays(365000); }
    }

    public long remainingSecondsCeiling(UUID playerId, CooldownType type) {
        Duration remaining = remaining(playerId, type);
        if (remaining.isZero()) return 0;
        long seconds = remaining.getSeconds();
        return remaining.getNano() > 0 && seconds < Long.MAX_VALUE ? seconds + 1 : Math.max(0, seconds);
    }

    public boolean active(UUID playerId, CooldownType type) {
        return !remaining(playerId, type).isZero();
    }

    public void restore(UUID playerId, CooldownType type, Instant expiresAt) {
        if (expiresAt.isAfter(clock.now())) expirations.put(new Key(playerId, type), expiresAt);
    }

    public int reset(UUID playerId) {
        int before = expirations.size();
        expirations.keySet().removeIf(key -> key.playerId.equals(playerId));
        return before - expirations.size();
    }

    public Map<CooldownType, Instant> snapshot(UUID playerId) {
        Map<CooldownType, Instant> result = new java.util.EnumMap<>(CooldownType.class);
        expirations.forEach((key, value) -> { if (key.playerId.equals(playerId) && value.isAfter(clock.now())) result.put(key.type, value); });
        return Map.copyOf(result);
    }

    public void clear() { expirations.clear(); }

    private record Key(UUID playerId, CooldownType type) { }
}
