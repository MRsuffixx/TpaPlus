package com.mrsuffix.tpapro.api.event;

import org.bukkit.Location;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.UUID;

public final class TpaTeleportCancelEvent extends TpaTeleportEvent {
    private static final HandlerList HANDLERS = new HandlerList(); private final String reason;
    public TpaTeleportCancelEvent(UUID sessionId, UUID playerId, UUID requestId, Location destination, String reason) {
        super(sessionId, playerId, requestId, destination); this.reason = Objects.requireNonNull(reason);
    }
    public String getReason() { return reason; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
