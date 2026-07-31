package com.mrsuffix.tpapro.api.event;

import org.bukkit.Location;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public final class TpaTeleportStartEvent extends TpaTeleportEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    public TpaTeleportStartEvent(UUID sessionId, UUID playerId, UUID requestId, Location destination) { super(sessionId, playerId, requestId, destination); }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
