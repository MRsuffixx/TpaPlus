package com.mrsuffix.tpapro.api.event;

import com.mrsuffix.tpapro.request.TeleportRequest;
import org.bukkit.event.Event;

import java.util.Objects;

public abstract class TpaRequestEvent extends Event {
    private final TeleportRequest.Snapshot request;
    protected TpaRequestEvent(TeleportRequest.Snapshot request) {
        super(false); this.request = Objects.requireNonNull(request, "request");
    }
    public final TeleportRequest.Snapshot getRequest() { return request; }
}
