package com.mrsuffix.tpapro.safety;

import com.mrsuffix.tpapro.api.event.TpaSafetyCheckEvent;
import com.mrsuffix.tpapro.config.ConfigManager;
import com.mrsuffix.tpapro.config.ConfigurationBundle.Safety;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SafeTeleportService {
    public record Result(boolean safe, Location location, String reason, int checkedBlocks) {
        public Result { location = location == null ? null : location.clone(); }
        @Override public Location location() { return location == null ? null : location.clone(); }
    }
    private final ConfigManager configs;
    private final SafetyRules rules = new SafetyRules();
    public SafeTeleportService(ConfigManager configs) { this.configs = configs; }

    public Result find(UUID playerId, Location requested, boolean bypass) {
        Safety config = configs.get().main().safety();
        if (bypass || !config.enabled()) return fire(playerId, requested, new Result(true, requested, "bypass", 0));
        World world = requested.getWorld();
        if (world == null) return fire(playerId, requested, new Result(false, null, "world-unavailable", 0));
        if (!world.isChunkLoaded(requested.getBlockX() >> 4, requested.getBlockZ() >> 4))
            return fire(playerId, requested, new Result(false, null, "chunk-unloaded", 0));
        Result exact = check(requested, 1);
        if (exact.safe()) return fire(playerId, requested, exact);
        if (!config.searchNearby()) return fire(playerId, requested, exact);
        int checked = 1;
        for (Offset offset : offsets(config.horizontalRadius(), config.verticalRadius())) {
            if (offset.x == 0 && offset.y == 0 && offset.z == 0) continue;
            if (checked >= config.maximumBlockChecks()) break;
            Location candidate = new Location(world, requested.getBlockX() + offset.x + 0.5,
                    requested.getBlockY() + offset.y, requested.getBlockZ() + offset.z + 0.5,
                    requested.getYaw(), requested.getPitch());
            if (!world.isChunkLoaded(candidate.getBlockX() >> 4, candidate.getBlockZ() >> 4)) continue;
            Result result = check(candidate, ++checked);
            if (result.safe()) return fire(playerId, requested, result);
        }
        return fire(playerId, requested, new Result(false, null, exact.reason(), checked));
    }

    private Result check(Location location, int checked) {
        Safety config = configs.get().main().safety(); World world = location.getWorld();
        if (world == null || !world.getWorldBorder().isInside(location)) return new Result(false, null, "world-border", checked);
        int y = location.getBlockY();
        if (y < world.getMinHeight() || y + 1 >= world.getMaxHeight()) return new Result(false, null, "void", checked);
        if (world.getEnvironment() == World.Environment.NETHER && !config.allowNetherRoof() && y >= world.getLogicalHeight())
            return new Result(false, null, "nether-roof", checked);
        Block below = world.getBlockAt(location.getBlockX(), y - 1, location.getBlockZ());
        Block feet = world.getBlockAt(location.getBlockX(), y, location.getBlockZ());
        Block head = world.getBlockAt(location.getBlockX(), y + 1, location.getBlockZ());
        boolean fall = !below.getType().isSolid() && noGroundWithin(world, location.getBlockX(), y - 1, location.getBlockZ(), config.maximumSafeFallDistance());
        SafetyRules.Options options = new SafetyRules.Options(config.requireSolidGround(), config.preventLava(), config.preventFire(),
                config.preventCampfire(), config.preventCactus(), config.preventMagma(), config.preventPowderSnow(),
                config.preventBerryBush(), config.preventVoid(), config.preventSuffocation());
        SafetyRules.Hazard hazard = rules.validate(cell(below), cell(feet), cell(head), y - 1 < world.getMinHeight(), fall, options);
        return hazard == SafetyRules.Hazard.NONE ? new Result(true, location, "safe", checked)
                : new Result(false, null, hazard.name().toLowerCase(Locale.ROOT), checked);
    }

    private Result fire(UUID player, Location requested, Result initial) {
        TpaSafetyCheckEvent event = new TpaSafetyCheckEvent(player, requested, initial.location(), initial.safe(), initial.reason());
        Bukkit.getPluginManager().callEvent(event);
        return new Result(event.isSafe(), event.isSafe() ? (event.getResolved() == null ? requested : event.getResolved()) : null,
                event.getReason(), initial.checkedBlocks());
    }

    private static boolean noGroundWithin(World world, int x, int fromY, int z, int distance) {
        for (int offset = 0; offset <= distance && fromY - offset >= world.getMinHeight(); offset++)
            if (world.getBlockAt(x, fromY - offset, z).getType().isSolid()) return false;
        return true;
    }

    private static SafetyRules.Cell cell(Block block) {
        Material material = block.getType();
        return new SafetyRules.Cell(block.isPassable(), material.isSolid(), hazard(material));
    }

    private static SafetyRules.Hazard hazard(Material m) {
        return switch (m) {
            case LAVA -> SafetyRules.Hazard.LAVA;
            case FIRE, SOUL_FIRE -> SafetyRules.Hazard.FIRE;
            case CAMPFIRE, SOUL_CAMPFIRE -> SafetyRules.Hazard.CAMPFIRE;
            case CACTUS -> SafetyRules.Hazard.CACTUS;
            case MAGMA_BLOCK -> SafetyRules.Hazard.MAGMA;
            case POWDER_SNOW -> SafetyRules.Hazard.POWDER_SNOW;
            case SWEET_BERRY_BUSH -> SafetyRules.Hazard.BERRY_BUSH;
            default -> SafetyRules.Hazard.NONE;
        };
    }

    private static List<Offset> offsets(int horizontal, int vertical) {
        List<Offset> result = new ArrayList<>((horizontal * 2 + 1) * (horizontal * 2 + 1) * (vertical * 2 + 1));
        for (int y = -vertical; y <= vertical; y++) for (int x = -horizontal; x <= horizontal; x++)
            for (int z = -horizontal; z <= horizontal; z++) result.add(new Offset(x, y, z));
        result.sort(Comparator.comparingInt(Offset::distance).thenComparingInt(o -> Math.abs(o.y))
                .thenComparingInt(o -> o.y).thenComparingInt(o -> o.x).thenComparingInt(o -> o.z));
        return result;
    }
    private record Offset(int x, int y, int z) { int distance() { return x * x + z * z + y * y * 2; } }
}
