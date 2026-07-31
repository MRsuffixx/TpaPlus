package com.mrsuffix.tpapro.database.model;

import com.mrsuffix.tpapro.history.StoredLocation;
import com.mrsuffix.tpapro.settings.PlayerSettings;

import java.util.Set;
import java.util.UUID;

public record PlayerData(PlayerSettings settings, Set<UUID> trusted, Set<UUID> blocked, Set<UUID> autoAcceptPlayers,
                         StoredLocation backLocation) {
    public PlayerData {
        trusted = Set.copyOf(trusted); blocked = Set.copyOf(blocked); autoAcceptPlayers = Set.copyOf(autoAcceptPlayers);
    }
}
