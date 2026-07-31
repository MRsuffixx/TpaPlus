package com.mrsuffix.tpapro.api.event;

import com.mrsuffix.tpapro.request.TeleportRequest;
import org.bukkit.event.HandlerList;

public final class TpaRequestDeniedEvent extends TpaRequestEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    public TpaRequestDeniedEvent(TeleportRequest.Snapshot request) { super(request); }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
