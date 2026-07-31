package com.mrsuffix.tpapro.integration.combat;

import com.mrsuffix.tpapro.config.ConfigManager;
import com.mrsuffix.tpapro.util.ClockSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BuiltInCombatTracker implements CombatIntegration, Listener {
    private final ConfigManager configs; private final ClockSource clock; private final Map<UUID, Instant> until = new ConcurrentHashMap<>();
    public BuiltInCombatTracker(ConfigManager configs, ClockSource clock) { this.configs = configs; this.clock = clock; }
    @Override public String name() { return "BuiltIn"; }
    @Override public boolean available() { return configs.get().restrictions().combat().enabled(); }
    @Override public boolean inCombat(Player player) {
        Instant expiry = until.get(player.getUniqueId());
        if (expiry == null) return false;
        if (!clock.now().isBefore(expiry)) { until.remove(player.getUniqueId(), expiry); return false; }
        return true;
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player damaged = event.getEntity() instanceof Player player ? player : null;
        Player attacker = playerDamager(event.getDamager());
        if (damaged == null || attacker == null || damaged.equals(attacker) || !available()) return;
        Instant expiry = clock.now().plusSeconds(configs.get().restrictions().combat().durationSeconds());
        until.put(damaged.getUniqueId(), expiry); until.put(attacker.getUniqueId(), expiry);
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { until.remove(event.getPlayer().getUniqueId()); }
    private static Player playerDamager(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile) { ProjectileSource source = projectile.getShooter(); if (source instanceof Player player) return player; }
        return null;
    }
}
