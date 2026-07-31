package com.mrsuffix.tpapro.request;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class TeleportRequest {
    private static final Map<RequestState, Set<RequestState>> TRANSITIONS = Map.of(
            RequestState.PENDING, EnumSet.of(RequestState.ACCEPTED, RequestState.DENIED, RequestState.CANCELLED,
                    RequestState.EXPIRED, RequestState.INVALIDATED),
            RequestState.ACCEPTED, EnumSet.of(RequestState.COMPLETED, RequestState.FAILED, RequestState.CANCELLED,
                    RequestState.INVALIDATED)
    );

    private final UUID id;
    private final UUID senderId;
    private final UUID targetId;
    private final RequestType type;
    private final Instant createdAt;
    private final AtomicReference<Instant> expiresAt;
    private final AtomicReference<RequestState> state;
    private final Map<String, String> metadata;

    public TeleportRequest(UUID id, UUID senderId, UUID targetId, RequestType type, Instant createdAt,
                           Instant expiresAt, Map<String, String> metadata) {
        this.id = Objects.requireNonNull(id, "id");
        this.senderId = Objects.requireNonNull(senderId, "senderId");
        this.targetId = Objects.requireNonNull(targetId, "targetId");
        this.type = Objects.requireNonNull(type, "type");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = new AtomicReference<>(Objects.requireNonNull(expiresAt, "expiresAt"));
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        this.state = new AtomicReference<>(RequestState.PENDING);
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public UUID id() { return id; }
    public UUID senderId() { return senderId; }
    public UUID targetId() { return targetId; }
    public RequestType type() { return type; }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt.get(); }
    public RequestState state() { return state.get(); }
    public Map<String, String> metadata() { return metadata; }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt());
    }

    public boolean refresh(Instant newExpiration) {
        Objects.requireNonNull(newExpiration, "newExpiration");
        if (state() != RequestState.PENDING || !newExpiration.isAfter(createdAt)) {
            return false;
        }
        expiresAt.set(newExpiration);
        return state() == RequestState.PENDING;
    }

    public boolean transitionTo(RequestState next) {
        Objects.requireNonNull(next, "next");
        while (true) {
            RequestState current = state.get();
            Set<RequestState> allowed = TRANSITIONS.getOrDefault(current, Set.of());
            if (!allowed.contains(next)) {
                return false;
            }
            if (state.compareAndSet(current, next)) {
                return true;
            }
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(id, senderId, targetId, type, createdAt, expiresAt(), state(), metadata);
    }

    public record Snapshot(UUID id, UUID senderId, UUID targetId, RequestType type, Instant createdAt,
                           Instant expiresAt, RequestState state, Map<String, String> metadata) {
        public Snapshot {
            metadata = Map.copyOf(metadata);
        }
    }
}
