package com.mrsuffix.tpapro.integration.worldguard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class WorldGuardRegionIntegration implements RegionIntegration {
    private final StateFlag flag;
    public WorldGuardRegionIntegration() {
        this.flag = WorldGuardFlagRegistrar.flag();
        if (flag == null) throw new IllegalStateException("WorldGuard flag was not registered during plugin load");
    }
    @Override public String name() { return "WorldGuard"; }
    @Override public boolean available() { return flag != null; }
    @Override public boolean allowed(Player player, Location location) {
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        return query.testState(BukkitAdapter.adapt(location), WorldGuardPlugin.inst().wrapPlayer(player), flag);
    }
}
