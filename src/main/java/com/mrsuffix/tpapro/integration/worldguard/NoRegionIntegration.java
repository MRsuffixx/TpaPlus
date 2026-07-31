package com.mrsuffix.tpapro.integration.worldguard;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class NoRegionIntegration implements RegionIntegration {
    @Override public String name() { return "None"; }
    @Override public boolean available() { return false; }
    @Override public boolean allowed(Player player, Location location) { return true; }
}
