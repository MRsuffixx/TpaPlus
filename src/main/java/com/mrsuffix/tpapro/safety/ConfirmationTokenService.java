package com.mrsuffix.tpapro.safety;

import com.mrsuffix.tpapro.util.ClockSource;
import org.bukkit.Location;
import org.bukkit.World;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ConfirmationTokenService {
    private static final int MAX_TOKENS = 2048;
    private final ClockSource clock;
    private final Map<String, Token> tokens = new HashMap<>();
    public ConfirmationTokenService(ClockSource clock) { this.clock = clock; }
    public synchronized Optional<String> issue(UUID player, UUID request, Location destination, Duration lifetime) {
        if (lifetime.isZero() || lifetime.isNegative()) throw new IllegalArgumentException("lifetime must be positive");
        pruneInternal();
        invalidateRequest(request);
        if (tokens.size() >= MAX_TOKENS) return Optional.empty();
        World world = destination.getWorld(); if (world == null) throw new IllegalArgumentException("Missing world");
        String value = UUID.randomUUID().toString().replace("-", "");
        tokens.put(value, new Token(player, request, world.getUID(), destination.getBlockX(), destination.getBlockY(),
                destination.getBlockZ(), safePlus(clock.now(), lifetime)));
        return Optional.of(value);
    }
    public synchronized Optional<UUID> request(String value, UUID player) {
        if (!valid(value)) return Optional.empty();
        Token token = tokens.get(value);
        if (token == null || !token.player.equals(player) || !clock.now().isBefore(token.expiresAt)) {
            tokens.remove(value);
            return Optional.empty();
        }
        return Optional.of(token.request);
    }
    public synchronized boolean consume(String value, UUID player, UUID request, Location destination) {
        if (value == null || !value.matches("[a-f0-9]{32}")) return false;
        Token token = tokens.remove(value); World world = destination.getWorld();
        return token != null && world != null && token.player.equals(player) && token.request.equals(request)
                && token.world.equals(world.getUID()) && token.x == destination.getBlockX() && token.y == destination.getBlockY()
                && token.z == destination.getBlockZ() && clock.now().isBefore(token.expiresAt);
    }
    public synchronized void invalidateRequest(UUID request) { tokens.entrySet().removeIf(entry -> entry.getValue().request.equals(request)); }
    public synchronized void prune() { pruneInternal(); }
    public synchronized int size() { return tokens.size(); }
    private void pruneInternal() { Instant now = clock.now(); tokens.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt)); }
    private static boolean valid(String value) { return value != null && value.matches("[a-f0-9]{32}"); }
    private static Instant safePlus(Instant now, Duration lifetime) {
        try { return now.plus(lifetime); }
        catch (RuntimeException overflow) { return Instant.MAX; }
    }
    private record Token(UUID player, UUID request, UUID world, int x, int y, int z, Instant expiresAt) { }
}
