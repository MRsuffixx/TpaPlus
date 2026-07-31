package com.mrsuffix.tpapro.integration.worldguard;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface RegionIntegration {
    String name();
    boolean available();
    boolean allowed(Player player, Location location);
}
