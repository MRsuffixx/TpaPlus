package com.mrsuffix.tpapro.user;

import com.mrsuffix.tpapro.history.StoredLocation;
import com.mrsuffix.tpapro.settings.PlayerSettings;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class UserProfile {
    private PlayerSettings settings;
    private final Set<UUID> trusted;
    private final Set<UUID> blocked;
    private final Set<UUID> autoAcceptPlayers;
    private StoredLocation backLocation;

    public UserProfile(PlayerSettings settings, Set<UUID> trusted, Set<UUID> blocked, Set<UUID> autoAcceptPlayers,
                       StoredLocation backLocation) {
        this.settings = settings; this.trusted = new HashSet<>(trusted); this.blocked = new HashSet<>(blocked);
        this.autoAcceptPlayers = new HashSet<>(autoAcceptPlayers);
        this.backLocation = backLocation;
    }
    public synchronized PlayerSettings settings() { return settings; }
    public synchronized void settings(PlayerSettings value) { settings = value; }
    public synchronized boolean trust(UUID id) { return trusted.add(id); }
    public synchronized boolean untrust(UUID id) { return trusted.remove(id); }
    public synchronized boolean block(UUID id) { return blocked.add(id); }
    public synchronized boolean unblock(UUID id) { return blocked.remove(id); }
    public synchronized boolean trusts(UUID id) { return trusted.contains(id); }
    public synchronized boolean blocks(UUID id) { return blocked.contains(id); }
    public synchronized Set<UUID> trusted() { return Collections.unmodifiableSet(new HashSet<>(trusted)); }
    public synchronized Set<UUID> blocked() { return Collections.unmodifiableSet(new HashSet<>(blocked)); }
    public synchronized boolean addAutoAccept(UUID id) { return autoAcceptPlayers.add(id); }
    public synchronized boolean removeAutoAccept(UUID id) { return autoAcceptPlayers.remove(id); }
    public synchronized boolean autoAccepts(UUID id) { return autoAcceptPlayers.contains(id); }
    public synchronized Set<UUID> autoAcceptPlayers() { return Collections.unmodifiableSet(new HashSet<>(autoAcceptPlayers)); }
    public synchronized StoredLocation backLocation() { return backLocation; }
    public synchronized void backLocation(StoredLocation location) { backLocation = location; }
}
