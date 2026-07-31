package com.mrsuffix.tpapro.safety;

import com.mrsuffix.tpapro.config.ConfigManager;
import com.mrsuffix.tpapro.config.ConfigurationBundle.Trap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.LinkedHashSet;
import java.util.Set;

public final class TrapRiskAnalyzer {
    public record Risk(boolean risky, Set<String> reasons) { public Risk { reasons = Set.copyOf(reasons); } }
    private final ConfigManager configs;
    public TrapRiskAnalyzer(ConfigManager configs) { this.configs = configs; }

    public Risk analyze(Location destination, boolean targetInCombat) {
        Trap config = configs.get().main().trap();
        if (config.mode() == com.mrsuffix.tpapro.config.ConfigurationBundle.TrapMode.OFF) return new Risk(false, Set.of());
        World world = destination.getWorld(); if (world == null) return new Risk(true, Set.of("world-unavailable"));
        Set<String> reasons = new LinkedHashSet<>(); int radius = config.scanRadius();
        int checked = 0, limit = Math.min(4096, (radius * 2 + 1) * (radius * 2 + 1) * (radius * 2 + 1));
        for (int x = -radius; x <= radius && checked < limit; x++) for (int y = -radius; y <= radius && checked < limit; y++)
            for (int z = -radius; z <= radius && checked++ < limit; z++) {
                Material material = world.getBlockAt(destination.getBlockX() + x, destination.getBlockY() + y,
                        destination.getBlockZ() + z).getType();
                if (config.lava() && material == Material.LAVA) reasons.add("lava");
                if (config.tnt() && material == Material.TNT) reasons.add("tnt");
                if (config.dangerousBlocks() && dangerous(material)) reasons.add("dangerous-blocks");
            }
        if (config.largeDrop() && largeDrop(destination, 8)) reasons.add("large-drop");
        if (config.suffocation() && (!destination.getBlock().isPassable() || !destination.clone().add(0, 1, 0).getBlock().isPassable()))
            reasons.add("suffocation");
        if (config.unsafeEnclosure() && enclosed(destination)) reasons.add("unsafe-enclosure");
        if (config.targetInCombat() && targetInCombat) reasons.add("target-in-combat");
        if (config.tnt() || config.endCrystals()) {
            for (Entity entity : world.getNearbyEntities(destination, radius, radius, radius)) {
                if (config.tnt() && entity.getType() == EntityType.TNT) reasons.add("tnt");
                if (config.endCrystals() && entity.getType() == EntityType.END_CRYSTAL) reasons.add("end-crystal");
            }
        }
        return new Risk(!reasons.isEmpty(), reasons);
    }

    private static boolean dangerous(Material material) {
        return switch (material) {
            case FIRE, SOUL_FIRE, CAMPFIRE, SOUL_CAMPFIRE, CACTUS, MAGMA_BLOCK, POWDER_SNOW,
                    SWEET_BERRY_BUSH, POINTED_DRIPSTONE, WITHER_ROSE -> true;
            default -> false;
        };
    }
    private static boolean largeDrop(Location location, int distance) {
        World world = location.getWorld(); if (world == null) return true;
        for (int y = location.getBlockY() - 1, checked = 0; y >= world.getMinHeight() && checked <= distance; y--, checked++)
            if (world.getBlockAt(location.getBlockX(), y, location.getBlockZ()).getType().isSolid()) return false;
        return true;
    }
    private static boolean enclosed(Location location) {
        int openSides = 0;
        for (int[] offset : new int[][]{{1,0},{-1,0},{0,1},{0,-1}})
            if (location.clone().add(offset[0], 0, offset[1]).getBlock().isPassable()) openSides++;
        return openSides == 0;
    }
}
