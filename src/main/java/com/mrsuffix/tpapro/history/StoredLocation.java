package com.mrsuffix.tpapro.history;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record StoredLocation(UUID worldId, String worldName, double x, double y, double z, float yaw, float pitch,
                             Instant savedAt) {
    public StoredLocation {
        Objects.requireNonNull(worldId, "worldId"); Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(savedAt, "savedAt");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) throw new IllegalArgumentException("Non-finite location");
    }

    public static StoredLocation from(Location location, Instant time) {
        World world = Objects.requireNonNull(location.getWorld(), "location world");
        return new StoredLocation(world.getUID(), world.getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(), time);
    }

    public Optional<Location> resolve() {
        World world = Bukkit.getWorld(worldId);
        if (world == null) world = Bukkit.getWorld(worldName);
        return world == null ? Optional.empty() : Optional.of(new Location(world, x, y, z, yaw, pitch));
    }
}
