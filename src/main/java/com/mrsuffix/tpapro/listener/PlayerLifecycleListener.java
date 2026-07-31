package com.mrsuffix.tpapro.listener;

import com.mrsuffix.tpapro.config.ConfigManager;
import com.mrsuffix.tpapro.cooldown.CooldownService;
import com.mrsuffix.tpapro.database.repository.PlayerDataRepository;
import com.mrsuffix.tpapro.history.StoredLocation;
import com.mrsuffix.tpapro.locale.LocaleManager;
import com.mrsuffix.tpapro.request.RequestCoordinator;
import com.mrsuffix.tpapro.scheduler.SchedulerAdapter;
import com.mrsuffix.tpapro.user.UserService;
import com.mrsuffix.tpapro.util.ClockSource;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class PlayerLifecycleListener implements Listener {
    private final ConfigManager configs; private final UserService users; private final PlayerDataRepository repository;
    private final LocaleManager locales; private final CooldownService cooldowns; private final RequestCoordinator requests;
    private final SchedulerAdapter scheduler; private final ClockSource clock; private final Logger logger;
    public PlayerLifecycleListener(ConfigManager configs, UserService users, PlayerDataRepository repository,
                                   LocaleManager locales, CooldownService cooldowns, RequestCoordinator requests,
                                   SchedulerAdapter scheduler, ClockSource clock, Logger logger) {
        this.configs = configs; this.users = users; this.repository = repository; this.locales = locales;
        this.cooldowns = cooldowns; this.requests = requests; this.scheduler = scheduler; this.clock = clock; this.logger = logger;
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) { load(event.getPlayer()); }
    public java.util.concurrent.CompletableFuture<Void> load(Player player) {
        return users.load(player.getUniqueId()).thenCompose(profile -> {
            if (player.isOnline() && profile.settings().language() != null && locales.isRegistered(profile.settings().language()))
                scheduler.run(() -> { if (player.isOnline()) locales.setPreference(player.getUniqueId(), profile.settings().language()); });
            return configs.get().storage().persistCooldowns() ? repository.loadCooldowns(player.getUniqueId())
                    : java.util.concurrent.CompletableFuture.completedFuture(java.util.Map.of());
        }).thenAccept(values -> scheduler.run(() -> {
            if (player.isOnline()) values.forEach((type, expiry) -> cooldowns.restore(player.getUniqueId(), type, expiry));
        })).exceptionally(error -> { logger.log(Level.WARNING, "Could not load player data for " + player.getUniqueId(), error); return null; });
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer(); requests.handleQuit(player.getUniqueId());
        if (configs.get().storage().persistCooldowns()) repository.saveCooldowns(player.getUniqueId(), cooldowns.snapshot(player.getUniqueId()))
                .exceptionally(error -> { logger.log(Level.WARNING, "Could not save cooldowns for " + player.getUniqueId(), error); return null; });
        cooldowns.reset(player.getUniqueId()); locales.clearPreference(player.getUniqueId()); users.unload(player.getUniqueId());
    }
    @EventHandler public void onDeath(PlayerDeathEvent event) {
        if (configs.get().main().back().saveOn().death()) users.saveBack(event.getEntity().getUniqueId(), StoredLocation.from(event.getEntity().getLocation(), clock.now()));
    }
    @EventHandler public void onPortal(PlayerPortalEvent event) {
        if (configs.get().main().back().saveOn().portal()) users.saveBack(event.getPlayer().getUniqueId(), StoredLocation.from(event.getFrom(), clock.now()));
    }
}
