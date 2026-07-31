package com.mrsuffix.tpapro.api.event;

import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.UUID;

public final class TpaSafetyCheckEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId; private final Location requested; private final Location resolved; private boolean safe; private String reason;
    public TpaSafetyCheckEvent(UUID playerId, Location requested, Location resolved, boolean safe, String reason) {
        this.playerId = Objects.requireNonNull(playerId); this.requested = Objects.requireNonNull(requested).clone();
        this.resolved = resolved == null ? null : resolved.clone(); this.safe = safe; this.reason = reason;
    }
    public UUID getPlayerId() { return playerId; }
    public Location getRequested() { return requested.clone(); }
    public Location getResolved() { return resolved == null ? null : resolved.clone(); }
    public boolean isSafe() { return safe; }
    public void setSafe(boolean safe) { this.safe = safe; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
