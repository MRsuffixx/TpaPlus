package com.mrsuffix.tpapro.api.event;

import org.bukkit.Location;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public final class TpaTeleportPrepareEvent extends TpaTeleportEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList(); private boolean cancelled;
    public TpaTeleportPrepareEvent(UUID sessionId, UUID playerId, UUID requestId, Location destination) { super(sessionId, playerId, requestId, destination); }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
