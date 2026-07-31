package com.mrsuffix.tpapro.teleport;

import com.mrsuffix.tpapro.api.event.TpaTeleportCancelEvent;
import com.mrsuffix.tpapro.api.event.TpaTeleportCompleteEvent;
import com.mrsuffix.tpapro.api.event.TpaTeleportPrepareEvent;
import com.mrsuffix.tpapro.api.event.TpaTeleportStartEvent;
import com.mrsuffix.tpapro.config.ConfigManager;
import com.mrsuffix.tpapro.config.ConfigurationBundle.ChargeMode;
import com.mrsuffix.tpapro.economy.EconomyTransactionService;
import com.mrsuffix.tpapro.cooldown.CooldownService;
import com.mrsuffix.tpapro.cooldown.CooldownType;
import com.mrsuffix.tpapro.history.HistoryEntry;
import com.mrsuffix.tpapro.history.HistoryService;
import com.mrsuffix.tpapro.history.StatisticsService;
import com.mrsuffix.tpapro.history.StoredLocation;
import com.mrsuffix.tpapro.history.TeleportKind;
import com.mrsuffix.tpapro.integration.combat.CombatService;
import com.mrsuffix.tpapro.integration.worldguard.RegionIntegration;
import com.mrsuffix.tpapro.locale.LocaleManager;
import com.mrsuffix.tpapro.locale.SoundService;
import com.mrsuffix.tpapro.permission.Permission;
import com.mrsuffix.tpapro.permission.PermissionService;
import com.mrsuffix.tpapro.request.RequestRegistry;
import com.mrsuffix.tpapro.request.RequestState;
import com.mrsuffix.tpapro.request.TeleportRequest;
import com.mrsuffix.tpapro.restriction.WorldRestrictionService;
import com.mrsuffix.tpapro.safety.SafeTeleportService;
import com.mrsuffix.tpapro.safety.TrapRiskAnalyzer;
import com.mrsuffix.tpapro.scheduler.ScheduledTask;
import com.mrsuffix.tpapro.scheduler.SchedulerAdapter;
import com.mrsuffix.tpapro.settings.PlayerSettings;
import com.mrsuffix.tpapro.user.UserService;
import com.mrsuffix.tpapro.util.ClockSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TeleportService implements Listener, AutoCloseable {
    public enum StartStatus { STARTED, PLAYER_OFFLINE, BUSY, RESTRICTED, COMBAT, REGION, EVENT_CANCELLED, INVALID_REQUEST, ECONOMY }
    public record StartResult(StartStatus status, String reason) { public boolean success() { return status == StartStatus.STARTED; } }
    private final ConfigManager configs; private final LocaleManager locales; private final SoundService sounds;
    private final PermissionService permissions; private final SchedulerAdapter scheduler; private final ClockSource clock;
    private final RequestRegistry requests; private final SafeTeleportService safety; private final TrapRiskAnalyzer trapAnalyzer;
    private final WorldRestrictionService worlds; private final RegionIntegration regions; private final CombatService combat;
    private final EconomyTransactionService economy; private final UserService users; private final HistoryService history;
    private final StatisticsService statistics; private final CooldownService cooldowns; private final WarmupRegistry warmups = new WarmupRegistry();
    private final MovementPolicy movement = new MovementPolicy(); private final Map<UUID, Active> active = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> executing = new ConcurrentHashMap<>(); private ScheduledTask ticker;
    private final java.util.function.Consumer<String> debug;

    public TeleportService(ConfigManager configs, LocaleManager locales, SoundService sounds, PermissionService permissions,
                           SchedulerAdapter scheduler, ClockSource clock, RequestRegistry requests, SafeTeleportService safety,
                           TrapRiskAnalyzer trapAnalyzer, WorldRestrictionService worlds, RegionIntegration regions,
                           CombatService combat, EconomyTransactionService economy, UserService users,
                           HistoryService history, StatisticsService statistics, CooldownService cooldowns,
                           java.util.function.Consumer<String> debug) {
        this.configs = configs; this.locales = locales; this.sounds = sounds; this.permissions = permissions;
        this.scheduler = scheduler; this.clock = clock; this.requests = requests; this.safety = safety;
        this.trapAnalyzer = trapAnalyzer; this.worlds = worlds; this.regions = regions; this.combat = combat;
        this.economy = economy; this.users = users; this.history = history; this.statistics = statistics;
        this.cooldowns = cooldowns; this.debug = debug;
    }

    public void start() {
        if (ticker != null && !ticker.cancelled()) return;
        ticker = scheduler.runRepeating(this::tick, Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    public StartResult startRequest(TeleportRequest request, Player traveler, Location destination, TeleportKind kind,
                                    UUID related, int warmupSeconds, double cost) {
        if (request.state() != RequestState.ACCEPTED) return new StartResult(StartStatus.INVALID_REQUEST, "request-invalid");
        return begin(traveler, request.id(), request.id(), request.senderId(), destination, kind, related,
                warmupSeconds, cost, false, permissions.has(traveler, Permission.BYPASS_SAFETY));
    }

    public StartResult startBack(Player player, Location destination, double cost, int warmupSeconds) {
        UUID transaction = UUID.randomUUID();
        ChargeMode mode = configs.get().integrations().economy().chargeMode();
        if (configs.get().integrations().economy().enabled() && mode != ChargeMode.ON_SUCCESS) {
            EconomyTransactionService.Result charge = economy.chargeOnce(transaction, player.getUniqueId(), cost,
                    permissions.has(player, Permission.BYPASS_COST));
            if (!charge.success()) return new StartResult(StartStatus.ECONOMY, charge.status().name().toLowerCase());
        }
        StartResult started = begin(player, null, transaction, player.getUniqueId(), destination, TeleportKind.BACK,
                null, warmupSeconds, cost, false, permissions.has(player, Permission.BYPASS_SAFETY));
        if (!started.success()) settleTransaction(transaction, player.getUniqueId(), true);
        return started;
    }

    public StartResult force(Player player, Player target, boolean safetyBypass) {
        if (target.isInsideVehicle() || target.isGliding())
            return new StartResult(StartStatus.RESTRICTED, "invalid-target-state");
        SafeTeleportService.Result safe = safety.find(player.getUniqueId(), target.getLocation(), safetyBypass);
        if (!safe.safe()) return new StartResult(StartStatus.RESTRICTED, safe.reason());
        return begin(player, null, UUID.randomUUID(), player.getUniqueId(), safe.location(), TeleportKind.ADMIN,
                target.getUniqueId(), 0, 0, true, safetyBypass);
    }

    private StartResult begin(Player traveler, UUID requestId, UUID transactionId, UUID payerId, Location destination, TeleportKind kind,
                              UUID related, int warmupSeconds, double cost, boolean restrictionBypass,
                              boolean safetyBypass) {
        if (!traveler.isOnline()) return new StartResult(StartStatus.PLAYER_OFFLINE, "offline");
        if (executing.containsKey(traveler.getUniqueId())) return new StartResult(StartStatus.BUSY, "teleport-executing");
        boolean worldBypass = restrictionBypass || permissions.has(traveler, Permission.BYPASS_WORLD);
        WorldRestrictionService.Result world = worlds.check(traveler.getWorld(), destination.getWorld(), worldBypass);
        if (!world.allowed()) return new StartResult(StartStatus.RESTRICTED, world.reason());
        if (!restrictionBypass && configs.get().restrictions().combat().blockTeleportStart()
                && !permissions.has(traveler, Permission.BYPASS_COMBAT) && combat.inCombat(traveler))
            return new StartResult(StartStatus.COMBAT, "combat");
        if (!restrictionBypass && configs.get().restrictions().region().enabled() && regions.available()
                && !permissions.has(traveler, Permission.BYPASS_REGION)) {
            if (configs.get().restrictions().region().checkSource() && !regions.allowed(traveler, traveler.getLocation())
                    || configs.get().restrictions().region().checkDestination() && !regions.allowed(traveler, destination))
                return new StartResult(StartStatus.REGION, "region");
        }
        WarmupSession previous = warmups.current(traveler.getUniqueId()).orElse(null);
        if (previous != null) cancel(traveler.getUniqueId(), previous.id(), "replaced");
        Instant now = clock.now(); int seconds = permissions.has(traveler, Permission.BYPASS_WARMUP) ? 0 : Math.max(0, warmupSeconds);
        WarmupSession session = warmups.start(traveler.getUniqueId(), requestId, position(traveler.getLocation()), now, now.plusSeconds(seconds));
        Active details = new Active(session.id(), traveler.getUniqueId(), requestId, transactionId, payerId, destination.clone(), kind,
                related, Math.max(0, cost), restrictionBypass, safetyBypass);
        TpaTeleportPrepareEvent prepare = new TpaTeleportPrepareEvent(session.id(), traveler.getUniqueId(), requestId, destination);
        Bukkit.getPluginManager().callEvent(prepare);
        if (prepare.isCancelled()) { warmups.cancel(traveler.getUniqueId(), session.id()); return new StartResult(StartStatus.EVENT_CANCELLED, "event-cancelled"); }
        active.put(traveler.getUniqueId(), details);
        debug.accept("session=" + session.id() + " request=" + requestId + " warmup-start player=" + traveler.getUniqueId()
                + " seconds=" + seconds);
        Bukkit.getPluginManager().callEvent(new TpaTeleportStartEvent(session.id(), traveler.getUniqueId(), requestId, destination));
        if (seconds == 0) complete(details); else notifyStart(traveler, seconds);
        return new StartResult(StartStatus.STARTED, "started");
    }

    private void tick() {
        Instant now = clock.now();
        for (Active details : active.values()) {
            WarmupSession session = warmups.current(details.playerId).orElse(null);
            if (session == null || !session.id().equals(details.sessionId)) { active.remove(details.playerId, details); continue; }
            if (!now.isBefore(session.completesAt())) { complete(details); continue; }
            Player player = Bukkit.getPlayer(details.playerId); if (player == null) { cancel(details.playerId, details.sessionId, "quit"); continue; }
            long seconds = Math.max(1, Duration.between(now, session.completesAt()).toSeconds() + 1);
            notifyTick(player, seconds);
        }
    }

    private void complete(Active details) {
        WarmupSession session = warmups.current(details.playerId).orElse(null);
        if (session == null || !session.id().equals(details.sessionId)) return;
        if (clock.now().isBefore(session.completesAt())) return;
        if (warmups.complete(details.playerId, details.sessionId, clock.now()).isEmpty()) return;
        active.remove(details.playerId, details);
        Player player = Bukkit.getPlayer(details.playerId);
        if (player == null) { fail(details, "offline"); return; }
        if (details.requestId != null) {
            TeleportRequest request = requests.find(details.requestId).orElse(null);
            if (request == null || request.state() != RequestState.ACCEPTED) { fail(details, "request-invalid"); return; }
        }
        if (!details.restrictionBypass && configs.get().restrictions().combat().blockTeleportStart()
                && !permissions.has(player, Permission.BYPASS_COMBAT) && combat.inCombat(player)) { fail(details, "combat"); return; }
        executing.put(details.playerId, details.sessionId);
        World destinationWorld = details.destination.getWorld();
        if (destinationWorld == null) { failExecuting(details, "world-unavailable"); return; }
        destinationWorld.getChunkAtAsync(details.destination.getBlockX() >> 4, details.destination.getBlockZ() >> 4, true)
                .whenComplete((chunk, error) -> scheduler.run(() -> {
                    if (!Objects.equals(executing.get(details.playerId), details.sessionId)) return;
                    if (error != null) { failExecuting(details, "chunk-load"); return; }
                    executeLoaded(details);
                }));
    }

    private void executeLoaded(Active details) {
        Player player = Bukkit.getPlayer(details.playerId);
        if (player == null) { failExecuting(details, "offline"); return; }
        SafeTeleportService.Result safe = safety.find(player.getUniqueId(), details.destination, details.safetyBypass);
        if (!safe.safe()) { failExecuting(details, safe.reason()); return; }
        TrapRiskAnalyzer.Risk risk = trapAnalyzer.analyze(safe.location(), false);
        if (!details.restrictionBypass && risk.risky()
                && configs.get().main().trap().mode() == com.mrsuffix.tpapro.config.ConfigurationBundle.TrapMode.BLOCK) {
            failExecuting(details, String.join(",", risk.reasons())); return;
        }
        if (player.isInsideVehicle() || player.isGliding()) { failExecuting(details, "invalid-player-state"); return; }
        if (configs.get().integrations().economy().enabled()
                && configs.get().integrations().economy().chargeMode() == ChargeMode.ON_SUCCESS) {
            Player payer = Bukkit.getPlayer(details.payerId);
            EconomyTransactionService.Result charge = economy.chargeOnce(details.transactionId, details.payerId, details.cost,
                    payer != null && permissions.has(payer, Permission.BYPASS_COST));
            if (!charge.success()) {
                if (payer != null && charge.status() == EconomyTransactionService.Status.INSUFFICIENT)
                    locales.send(payer, payer.getUniqueId(), "economy.insufficient", Map.of("cost", money(charge.amount()), "balance", money(charge.balance())));
                else if (payer != null && charge.status() == EconomyTransactionService.Status.UNAVAILABLE) locales.send(payer, payer.getUniqueId(), "economy.unavailable");
                failExecuting(details, "economy-" + charge.status().name().toLowerCase()); return;
            }
            if (payer != null && charge.status() == EconomyTransactionService.Status.CHARGED)
                locales.send(payer, payer.getUniqueId(), "economy.charged", Map.of("cost", money(charge.amount())));
        }
        StoredLocation previous = StoredLocation.from(player.getLocation(), clock.now());
        player.teleportAsync(safe.location(), PlayerTeleportEvent.TeleportCause.PLUGIN).whenComplete((success, error) -> scheduler.run(() -> {
            if (!Objects.equals(executing.remove(details.playerId), details.sessionId)) return;
            Player current = Bukkit.getPlayer(details.playerId);
            if (error != null || !Boolean.TRUE.equals(success) || current == null) { fail(details, error == null ? "teleport-rejected" : "teleport-exception"); return; }
            if (details.requestId != null && !requests.transition(details.requestId, RequestState.COMPLETED)) { fail(details, "request-state-race"); return; }
            saveBackIfConfigured(details, previous);
            if (configs.get().main().history().enabled()) history.record(HistoryEntry.create(details.playerId,
                    StoredLocation.from(safe.location(), clock.now()), details.kind, details.related));
            statistics.increment(details.playerId, StatisticsService.Metric.TELEPORT_SUCCESS);
            if (economy.charged(details.transactionId)) statistics.cost(details.payerId, details.cost);
            economy.forget(details.transactionId);
            if (details.related != null) {
                Player relatedPlayer = Bukkit.getPlayer(details.related);
                statistics.target(details.playerId, details.related, relatedPlayer == null ? null : relatedPlayer.getName());
            }
            int successCooldown = configs.get().main().teleport().successfulCooldownSeconds();
            if (successCooldown > 0 && !permissions.has(current, Permission.BYPASS_COOLDOWN))
                cooldowns.start(details.playerId, CooldownType.SUCCESSFUL_TELEPORT, Duration.ofSeconds(successCooldown));
            locales.send(current, current.getUniqueId(), "teleport.success"); sounds.play(current, "teleport-success");
            Bukkit.getPluginManager().callEvent(new TpaTeleportCompleteEvent(details.sessionId, details.playerId,
                    details.requestId, safe.location()));
            debug.accept("session=" + details.sessionId + " request=" + details.requestId + " teleport-complete player=" + details.playerId);
        }));
    }

    private void saveBackIfConfigured(Active details, StoredLocation previous) {
        var save = configs.get().main().back().saveOn();
        boolean enabled = switch (details.kind) { case TPA -> save.tpa(); case TPA_HERE -> save.tpaHere();
            case ADMIN -> save.adminTeleport(); case BACK -> false; };
        if (configs.get().main().back().enabled() && enabled) users.saveBack(details.playerId, previous);
    }

    public boolean cancel(UUID playerId, UUID expectedSession, String reason) {
        WarmupSession session = warmups.cancel(playerId, expectedSession).orElse(null); if (session == null) return false;
        Active details = active.remove(playerId); if (details == null) return true;
        if (details.requestId != null) requests.transition(details.requestId, RequestState.CANCELLED);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) { locales.send(player, playerId, "warmup.cancelled", Map.of("reason", reason)); sounds.play(player, "teleport-cancelled"); }
        statistics.increment(playerId, StatisticsService.Metric.WARMUP_CANCELLED);
        settleTransaction(details.transactionId, details.payerId, true);
        Bukkit.getPluginManager().callEvent(new TpaTeleportCancelEvent(details.sessionId, playerId, details.requestId,
                details.destination, reason)); return true;
    }

    private void failExecuting(Active details, String reason) { executing.remove(details.playerId, details.sessionId); fail(details, reason); }
    private void fail(Active details, String reason) {
        debug.accept("session=" + details.sessionId + " request=" + details.requestId + " teleport-failed reason=" + reason);
        if (details.requestId != null) requests.transition(details.requestId, RequestState.FAILED);
        Player player = Bukkit.getPlayer(details.playerId);
        if (player != null) locales.send(player, player.getUniqueId(), "errors.teleport-failed", Map.of("reason", reason));
        statistics.increment(details.playerId, StatisticsService.Metric.TELEPORT_FAILED);
        settleTransaction(details.transactionId, details.payerId, true);
        Bukkit.getPluginManager().callEvent(new TpaTeleportCancelEvent(details.sessionId, details.playerId,
                details.requestId, details.destination, reason));
    }
    private void settleTransaction(UUID transactionId, UUID payerId, boolean failed) {
        if (!failed || !configs.get().integrations().economy().refundOnFailure()) {
            economy.forget(transactionId);
            return;
        }
        EconomyTransactionService.Result result = economy.refundOnce(transactionId); Player payer = Bukkit.getPlayer(payerId);
        if (payer != null && result.status() == EconomyTransactionService.Status.REFUNDED)
            locales.send(payer, payer.getUniqueId(), "economy.refunded", Map.of("cost", money(result.amount())));
        if (result.status() == EconomyTransactionService.Status.FAILED)
            debug.accept("transaction=" + transactionId + " refund-failed error=" + result.error());
        economy.forget(transactionId);
    }

    private void notifyStart(Player player, int seconds) {
        PlayerSettings settings = users.get(player.getUniqueId()).settings();
        if (configs.get().main().teleport().notification().chat() && settings.chatNotifications())
            locales.send(player, player.getUniqueId(), "warmup.started", Map.of("seconds", seconds));
    }
    private void notifyTick(Player player, long seconds) {
        PlayerSettings settings = users.get(player.getUniqueId()).settings(); var channels = configs.get().main().teleport().notification();
        if (channels.actionBar() && settings.actionBarNotifications())
            player.sendActionBar(locales.component(player.getUniqueId(), "warmup.countdown", Map.of("seconds", seconds)));
        if (channels.title() && settings.titleNotifications()) {
            Component title = locales.component(player.getUniqueId(), "warmup.title", Map.of("seconds", seconds));
            Component subtitle = locales.component(player.getUniqueId(), "warmup.subtitle", Map.of());
            player.showTitle(Title.title(title, subtitle, Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
        }
        if (channels.sound() && settings.sounds()) sounds.play(player, "warmup-tick");
    }

    public int activeCount() { return warmups.size() + executing.size(); }
    public boolean active(UUID player) { return warmups.current(player).isPresent() || executing.containsKey(player); }
    public long remainingSeconds(UUID player) { return warmups.current(player).map(s -> Math.max(0, Duration.between(clock.now(), s.completesAt()).toSeconds())).orElse(0L); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!configs.get().main().teleport().cancelOnMove() || event.getTo() == null) return;
        Player player = event.getPlayer(); if (permissions.has(player, Permission.BYPASS_MOVE_CANCEL)) return;
        WarmupSession session = warmups.current(player.getUniqueId()).orElse(null);
        if (session != null && movement.moved(session.anchor(), position(event.getTo()), configs.get().main().teleport().movementTolerance()))
            cancel(player.getUniqueId(), session.id(), "moved");
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (configs.get().main().teleport().cancelOnDamage() && event.getEntity() instanceof Player player
                && !permissions.has(player, Permission.BYPASS_DAMAGE_CANCEL)) cancel(player.getUniqueId(), null, "damaged");
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!configs.get().main().teleport().cancelOnAttack()) return;
        Player attacker = playerDamager(event.getDamager()); if (attacker != null) cancel(attacker.getUniqueId(), null, "attacked");
    }
    @EventHandler public void onWorld(PlayerChangedWorldEvent event) { if (configs.get().main().teleport().cancelOnWorldChange()) cancel(event.getPlayer().getUniqueId(), null, "world-change"); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { if (configs.get().main().teleport().cancelOnQuit()) cancel(event.getPlayer().getUniqueId(), null, "quit"); }
    @EventHandler public void onDeath(PlayerDeathEvent event) { if (configs.get().main().teleport().cancelOnDeath()) cancel(event.getEntity().getUniqueId(), null, "death"); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) { if (configs.get().main().teleport().cancelOnCommand()) cancel(event.getPlayer().getUniqueId(), null, "command"); }

    @Override public void close() {
        if (ticker != null) ticker.cancel();
        for (Active details : active.values()) cancel(details.playerId, details.sessionId, "shutdown");
        active.clear(); warmups.clear(); executing.clear();
    }
    private static PositionSnapshot position(Location location) { return new PositionSnapshot(Objects.requireNonNull(location.getWorld()).getUID(), location.getX(), location.getY(), location.getZ()); }
    private static Player playerDamager(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile) { ProjectileSource source = projectile.getShooter(); if (source instanceof Player player) return player; }
        return null;
    }
    private static String money(double value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }
    private record Active(UUID sessionId, UUID playerId, UUID requestId, UUID transactionId, UUID payerId, Location destination,
                          TeleportKind kind, UUID related, double cost, boolean restrictionBypass,
                          boolean safetyBypass) { }
}
