package com.mrsuffix.tpapro.api.event;

import org.bukkit.Location;
import org.bukkit.event.Event;

import java.util.Objects;
import java.util.UUID;

public abstract class TpaTeleportEvent extends Event {
    private final UUID sessionId;
    private final UUID playerId;
    private final UUID requestId;
    private final Location destination;
    protected TpaTeleportEvent(UUID sessionId, UUID playerId, UUID requestId, Location destination) {
        super(false); this.sessionId = Objects.requireNonNull(sessionId); this.playerId = Objects.requireNonNull(playerId);
        this.requestId = requestId; this.destination = Objects.requireNonNull(destination).clone();
    }
    public final UUID getSessionId() { return sessionId; }
    public final UUID getPlayerId() { return playerId; }
    public final UUID getRequestId() { return requestId; }
    public final Location getDestination() { return destination.clone(); }
}
