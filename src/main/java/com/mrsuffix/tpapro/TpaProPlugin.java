package com.mrsuffix.tpapro;

import com.mrsuffix.tpapro.api.TpaProApi;
import com.mrsuffix.tpapro.api.TpaProApiImpl;
import com.mrsuffix.tpapro.command.TpaCommandRouter;
import com.mrsuffix.tpapro.config.ConfigManager;
import com.mrsuffix.tpapro.cooldown.CooldownService;
import com.mrsuffix.tpapro.database.connection.SqlStorage;
import com.mrsuffix.tpapro.database.repository.PlayerDataRepository;
import com.mrsuffix.tpapro.database.repository.SqlPlayerDataRepository;
import com.mrsuffix.tpapro.economy.EconomyGateway;
import com.mrsuffix.tpapro.economy.EconomyTransactionService;
import com.mrsuffix.tpapro.economy.NoEconomyGateway;
import com.mrsuffix.tpapro.gui.GuiManager;
import com.mrsuffix.tpapro.history.HistoryService;
import com.mrsuffix.tpapro.history.StatisticsService;
import com.mrsuffix.tpapro.integration.combat.BuiltInCombatTracker;
import com.mrsuffix.tpapro.integration.combat.CombatIntegration;
import com.mrsuffix.tpapro.integration.combat.CombatService;
import com.mrsuffix.tpapro.integration.combat.ReflectiveCombatIntegration;
import com.mrsuffix.tpapro.integration.vault.VaultEconomyGateway;
import com.mrsuffix.tpapro.integration.worldguard.NoRegionIntegration;
import com.mrsuffix.tpapro.integration.worldguard.RegionIntegration;
import com.mrsuffix.tpapro.integration.friends.NoFriendsIntegration;
import com.mrsuffix.tpapro.listener.PlayerLifecycleListener;
import com.mrsuffix.tpapro.locale.LocaleManager;
import com.mrsuffix.tpapro.locale.SoundService;
import com.mrsuffix.tpapro.permission.PermissionGroupResolver;
import com.mrsuffix.tpapro.permission.PermissionService;
import com.mrsuffix.tpapro.request.RequestCoordinator;
import com.mrsuffix.tpapro.request.RequestRegistry;
import com.mrsuffix.tpapro.restriction.RestrictionRegistry;
import com.mrsuffix.tpapro.restriction.WorldRestrictionService;
import com.mrsuffix.tpapro.safety.ConfirmationTokenService;
import com.mrsuffix.tpapro.safety.SafeTeleportService;
import com.mrsuffix.tpapro.safety.TrapRiskAnalyzer;
import com.mrsuffix.tpapro.scheduler.PaperSchedulerAdapter;
import com.mrsuffix.tpapro.scheduler.ScheduledTask;
import com.mrsuffix.tpapro.scheduler.SchedulerAdapter;
import com.mrsuffix.tpapro.scheduler.ReloadableTask;
import com.mrsuffix.tpapro.settings.RequestPolicyEvaluator;
import com.mrsuffix.tpapro.teleport.TeleportService;
import com.mrsuffix.tpapro.user.UserService;
import com.mrsuffix.tpapro.util.ClockSource;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class TpaProPlugin extends JavaPlugin {
    private final AtomicBoolean ready = new AtomicBoolean();
    private volatile boolean debug;
    private ConfigManager configs; private LocaleManager locales; private SchedulerAdapter scheduler;
    private SqlStorage storage; private PlayerDataRepository repository; private RequestRegistry requests;
    private CooldownService cooldowns; private UserService users; private TeleportService teleports;
    private StatisticsService statistics; private HistoryService history; private RequestCoordinator coordinator; private PlayerLifecycleListener lifecycle;
    private ScheduledTask expirationTask; private ScheduledTask historyPruneTask;
    private final ReloadableTask statisticsTask = new ReloadableTask(); private RegionIntegration regions;
    private CombatService combat; private boolean foliaDetected; private final List<String> integrations = new ArrayList<>();
    private ClockSource clock;

    @Override public void onLoad() {
        foliaDetected = classPresent("io.papermc.paper.threadedregions.RegionizedServer");
        if (classPresent("com.sk89q.worldguard.WorldGuard")) {
            try {
                Class<?> registrar = Class.forName("com.mrsuffix.tpapro.integration.worldguard.WorldGuardFlagRegistrar");
                registrar.getMethod("register").invoke(null);
            } catch (ReflectiveOperationException | LinkageError error) {
                getLogger().log(Level.WARNING, "WorldGuard flag registration failed; region integration will be disabled", error);
            }
        }
    }

    @Override public void onEnable() {
        try {
            getDataFolder().mkdirs();
            configs = new ConfigManager(this); configs.initialize(); debug = configs.get().main().debug();
            locales = new LocaleManager(this, configs); locales.reload(); scheduler = new PaperSchedulerAdapter(this);
            clock = ClockSource.system(); requests = new RequestRegistry(clock); cooldowns = new CooldownService(clock);
            storage = new SqlStorage(configs.get().storage(), getLogger()); repository = new SqlPlayerDataRepository(storage, getLogger());
            users = new UserService(repository, getLogger(), configs.get().main().language().defaultLocale());
            PermissionService permissions = new PermissionService(); PermissionGroupResolver groups = new PermissionGroupResolver();
            WorldRestrictionService worlds = new WorldRestrictionService(configs); RestrictionRegistry restrictions = new RestrictionRegistry();
            SafeTeleportService safety = new SafeTeleportService(configs); TrapRiskAnalyzer traps = new TrapRiskAnalyzer(configs);
            ConfirmationTokenService confirmations = new ConfirmationTokenService(clock);
            BuiltInCombatTracker builtIn = new BuiltInCombatTracker(configs, clock);
            List<CombatIntegration> combatHooks = createCombatHooks(); combat = new CombatService(builtIn, combatHooks);
            regions = createRegionIntegration(); EconomyGateway gateway = createEconomyGateway();
            EconomyTransactionService economy = new EconomyTransactionService(gateway);
            statistics = new StatisticsService(repository, getLogger()); history = new HistoryService(repository, getLogger());
            SoundService sounds = new SoundService(configs, player -> users.get(player.getUniqueId()).settings().sounds(), getLogger());
            teleports = new TeleportService(configs, locales, sounds, permissions, scheduler, clock, requests, safety, traps,
                    worlds, regions, combat, economy, users, history, statistics, cooldowns, this::debug);
            coordinator = new RequestCoordinator(configs, requests, cooldowns, permissions, groups, users,
                    new RequestPolicyEvaluator(), worlds, regions, combat, restrictions, economy, traps, confirmations,
                    teleports, locales, sounds, statistics, new NoFriendsIntegration(), this::debug);
            GuiManager gui = new GuiManager(configs, locales, requests, coordinator, users, scheduler);
            lifecycle = new PlayerLifecycleListener(configs, users, repository, locales, cooldowns, coordinator,
                    scheduler, clock, getLogger());
            Bukkit.getPluginManager().registerEvents(builtIn, this); Bukkit.getPluginManager().registerEvents(teleports, this);
            Bukkit.getPluginManager().registerEvents(gui, this); Bukkit.getPluginManager().registerEvents(lifecycle, this);
            TpaCommandRouter commands = new TpaCommandRouter(this, configs, locales, permissions, groups, coordinator,
                    requests, cooldowns, users, teleports, history, statistics, scheduler, gui, clock);
            registerCommands(commands); teleports.start(); scheduleMaintenance();
            TpaProApi api = new TpaProApiImpl(coordinator, requests, cooldowns, users, restrictions);
            Bukkit.getServicesManager().register(TpaProApi.class, api, this, ServicePriority.Normal);
            registerPlaceholderApi();
            storage.initialize().whenComplete((ignored, error) -> scheduler.run(() -> {
                if (!isEnabled()) return;
                if (error != null) { getLogger().log(Level.SEVERE, "Database initialization failed; player commands remain unavailable", error); return; }
                java.util.concurrent.CompletableFuture<?>[] loads = Bukkit.getOnlinePlayers().stream()
                        .map(lifecycle::load).toArray(java.util.concurrent.CompletableFuture[]::new);
                java.util.concurrent.CompletableFuture.allOf(loads).whenComplete((loaded, loadError) -> scheduler.run(() -> {
                    if (!isEnabled()) return;
                    if (loadError != null) getLogger().log(Level.WARNING, "Some online player data could not be preloaded", loadError);
                    ready.set(true);
                    getLogger().info("TpaPro " + getPluginMeta().getVersion() + " enabled with " + configs.get().storage().type() + " storage.");
                }));
            }));
            if (foliaDetected) getLogger().warning("Folia was detected. TpaPro currently provides Paper scheduling only and is not declared Folia-compatible.");
        } catch (RuntimeException error) {
            getLogger().log(Level.SEVERE, "TpaPro could not start safely", error); Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override public void onDisable() {
        ready.set(false);
        if (expirationTask != null) expirationTask.cancel();
        if (historyPruneTask != null) historyPruneTask.cancel();
        statisticsTask.close();
        if (teleports != null) teleports.close();
        if (lifecycle != null) {
            Duration timeout = Duration.ofSeconds(configs == null ? 10 : configs.get().storage().shutdownTimeoutSeconds());
            if (!lifecycle.awaitPendingCooldownWrites(timeout))
                getLogger().warning("Timed out waiting for pending cooldown persistence writes.");
        }
        if (statistics != null) {
            try { statistics.flush().get(5, TimeUnit.SECONDS); }
            catch (Exception error) { getLogger().log(Level.WARNING, "Timed out flushing final statistics", error); }
        }
        Bukkit.getServicesManager().unregisterAll(this);
        if (storage != null) storage.close(); if (scheduler != null) scheduler.cancelAll();
        getLogger().info("TpaPro disabled; schedulers and storage were closed.");
    }

    public boolean reloadTpaPro() {
        try {
            configs.reload(); locales.reload(); debug = configs.get().main().debug();
            scheduleStatistics();
            return true;
        } catch (RuntimeException error) {
            getLogger().log(Level.WARNING, "Configuration reload failed; review the reported validation error", error); return false;
        }
    }

    private void scheduleMaintenance() {
        expirationTask = scheduler.runRepeating(coordinator::cleanupExpired, Duration.ofSeconds(1), Duration.ofSeconds(1));
        historyPruneTask = scheduler.runRepeating(this::pruneHistory, Duration.ofMinutes(1), Duration.ofHours(6));
        scheduleStatistics();
    }
    private void pruneHistory() {
        int retentionDays = configs.get().main().history().retentionDays();
        history.pruneBefore(clock.now().minus(Duration.ofDays(retentionDays))).thenAccept(count -> {
            if (debug && count > 0) debug("history-prune removed=" + count + " retention-days=" + retentionDays);
        });
    }
    private void scheduleStatistics() {
        Duration period = Duration.ofSeconds(configs.get().main().statistics().flushSeconds());
        statisticsTask.replace(scheduler.runRepeating(() -> statistics.flush(), period, period));
    }

    private List<CombatIntegration> createCombatHooks() {
        List<CombatIntegration> hooks = new ArrayList<>();
        if (configs.get().integrations().combatLogX()) addCombatHook(hooks, "CombatLogX");
        if (configs.get().integrations().pvpManager()) addCombatHook(hooks, "PvPManager");
        return hooks;
    }
    private void addCombatHook(List<CombatIntegration> hooks, String name) {
        org.bukkit.plugin.Plugin dependency = Bukkit.getPluginManager().getPlugin(name);
        if (dependency != null && dependency.isEnabled()) { hooks.add(new ReflectiveCombatIntegration(dependency, name, getLogger())); integrations.add(name); }
    }
    private RegionIntegration createRegionIntegration() {
        if (!configs.get().integrations().worldGuard() || Bukkit.getPluginManager().getPlugin("WorldGuard") == null)
            return new NoRegionIntegration();
        try {
            RegionIntegration integration = (RegionIntegration) Class.forName("com.mrsuffix.tpapro.integration.worldguard.WorldGuardRegionIntegration")
                    .getConstructor().newInstance(); integrations.add("WorldGuard"); return integration;
        } catch (ReflectiveOperationException | LinkageError error) {
            getLogger().log(Level.WARNING, "WorldGuard integration failed safely", error); return new NoRegionIntegration();
        }
    }
    private EconomyGateway createEconomyGateway() {
        if (!configs.get().integrations().vault() || Bukkit.getPluginManager().getPlugin("Vault") == null) {
            if (configs.get().integrations().economy().enabled()) getLogger().warning("Economy is enabled but Vault is unavailable; paid actions will fail safely.");
            return new NoEconomyGateway();
        }
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null) {
            getLogger().warning("Vault is installed without an economy provider; economy integration is unavailable."); return new NoEconomyGateway();
        }
        integrations.add("Vault"); return new VaultEconomyGateway(registration.getProvider());
    }

    private void registerPlaceholderApi() {
        if (!configs.get().integrations().placeholderApi() || Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        try {
            Class<?> type = Class.forName("com.mrsuffix.tpapro.integration.placeholderapi.TpaProExpansion");
            Constructor<?> constructor = type.getConstructors()[0]; Object expansion = constructor.newInstance(this, requests, cooldowns, teleports, users, statistics);
            Method register = type.getMethod("register"); if (Boolean.TRUE.equals(register.invoke(expansion))) integrations.add("PlaceholderAPI");
        } catch (ReflectiveOperationException | LinkageError error) { getLogger().log(Level.WARNING, "PlaceholderAPI integration failed safely", error); }
    }

    private void registerCommands(TpaCommandRouter router) {
        for (String name : List.of("tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel", "tpatoggle", "tpautaccept",
                "tpblock", "tpunblock", "tpblocklist", "tpatrust", "tpalist", "tpasettings", "tpback", "tphistory", "tpastats", "tpapro")) {
            PluginCommand command = Objects.requireNonNull(getCommand(name), "Command missing from plugin.yml: " + name);
            command.setExecutor(router); command.setTabCompleter(router);
        }
    }

    public boolean isReady() { return ready.get(); }
    public boolean debugEnabled() { return debug; }
    public void setDebugEnabled(boolean value) { debug = value; }
    public void debug(String message) { if (debug) getLogger().info("[debug] " + message); }
    public List<String> infoLines() {
        return List.of("Plugin: TpaPro " + getPluginMeta().getVersion(), "Server: " + Bukkit.getName() + " " + Bukkit.getVersion(),
                "Java: " + System.getProperty("java.version"), "Storage: " + configs.get().storage().type(),
                "Database: " + storage.status(), "Integrations: " + (integrations.isEmpty() ? "none" : String.join(", ", integrations)),
                "Default language: " + configs.get().main().language().defaultLocale(), "Active requests: " + requests.activeCount(),
                "Active warmups: " + teleports.activeCount(), "Folia detected: " + foliaDetected + " (supported: false)", "Debug: " + debug);
    }
    private static boolean classPresent(String name) { try { Class.forName(name, false, TpaProPlugin.class.getClassLoader()); return true; } catch (ClassNotFoundException | LinkageError absent) { return false; } }
}
