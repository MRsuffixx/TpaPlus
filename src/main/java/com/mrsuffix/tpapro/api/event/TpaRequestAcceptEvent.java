package com.mrsuffix.tpapro.api.event;

import com.mrsuffix.tpapro.request.TeleportRequest;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public final class TpaRequestAcceptEvent extends TpaRequestEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList(); private boolean cancelled;
    public TpaRequestAcceptEvent(TeleportRequest.Snapshot request) { super(request); }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
