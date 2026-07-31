package com.mrsuffix.tpapro.safety;

import com.mrsuffix.tpapro.util.ClockSource;
import org.bukkit.Location;
import org.bukkit.World;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ConfirmationTokenService {
    private final ClockSource clock;
    private final Map<String, Token> tokens = new ConcurrentHashMap<>();
    public ConfirmationTokenService(ClockSource clock) { this.clock = clock; }
    public String issue(UUID player, UUID request, Location destination, Duration lifetime) {
        prune(); if (tokens.size() >= 2048) throw new IllegalStateException("Confirmation capacity exceeded");
        World world = destination.getWorld(); if (world == null) throw new IllegalArgumentException("Missing world");
        String value = UUID.randomUUID().toString().replace("-", "");
        tokens.put(value, new Token(player, request, world.getUID(), destination.getBlockX(), destination.getBlockY(),
                destination.getBlockZ(), clock.now().plus(lifetime))); return value;
    }
    public boolean consume(String value, UUID player, UUID request, Location destination) {
        if (value == null || !value.matches("[a-f0-9]{32}")) return false;
        Token token = tokens.remove(value); World world = destination.getWorld();
        return token != null && world != null && token.player.equals(player) && token.request.equals(request)
                && token.world.equals(world.getUID()) && token.x == destination.getBlockX() && token.y == destination.getBlockY()
                && token.z == destination.getBlockZ() && clock.now().isBefore(token.expiresAt);
    }
    public void invalidateRequest(UUID request) { tokens.entrySet().removeIf(entry -> entry.getValue().request.equals(request)); }
    public void prune() { Instant now = clock.now(); tokens.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt)); }
    private record Token(UUID player, UUID request, UUID world, int x, int y, int z, Instant expiresAt) { }
}
