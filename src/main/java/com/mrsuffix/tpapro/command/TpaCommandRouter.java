package com.mrsuffix.tpapro.command;

import com.mrsuffix.tpapro.TpaProPlugin;
import com.mrsuffix.tpapro.config.ConfigManager;
import com.mrsuffix.tpapro.cooldown.CooldownService;
import com.mrsuffix.tpapro.cooldown.CooldownType;
import com.mrsuffix.tpapro.gui.GuiManager;
import com.mrsuffix.tpapro.history.HistoryEntry;
import com.mrsuffix.tpapro.history.HistoryService;
import com.mrsuffix.tpapro.history.PlayerStatistics;
import com.mrsuffix.tpapro.history.StatisticsService;
import com.mrsuffix.tpapro.history.StoredLocation;
import com.mrsuffix.tpapro.locale.LocaleManager;
import com.mrsuffix.tpapro.permission.Permission;
import com.mrsuffix.tpapro.permission.PermissionGroupResolver;
import com.mrsuffix.tpapro.permission.PermissionService;
import com.mrsuffix.tpapro.request.RequestCoordinator;
import com.mrsuffix.tpapro.request.RequestFailure;
import com.mrsuffix.tpapro.request.RequestOutcome;
import com.mrsuffix.tpapro.request.RequestRegistry;
import com.mrsuffix.tpapro.request.RequestType;
import com.mrsuffix.tpapro.request.TeleportRequest;
import com.mrsuffix.tpapro.scheduler.SchedulerAdapter;
import com.mrsuffix.tpapro.settings.PlayerSettings;
import com.mrsuffix.tpapro.settings.PrivacyMode;
import com.mrsuffix.tpapro.teleport.TeleportService;
import com.mrsuffix.tpapro.user.UserProfile;
import com.mrsuffix.tpapro.user.UserService;
import com.mrsuffix.tpapro.util.ClockSource;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class TpaCommandRouter implements CommandExecutor, TabCompleter {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private final TpaProPlugin plugin; private final ConfigManager configs; private final LocaleManager locales;
    private final PermissionService permissions; private final PermissionGroupResolver groups; private final RequestCoordinator coordinator;
    private final RequestRegistry requests; private final CooldownService cooldowns; private final UserService users;
    private final TeleportService teleports; private final HistoryService history; private final StatisticsService statistics;
    private final SchedulerAdapter scheduler; private final GuiManager gui; private final ClockSource clock;
    public TpaCommandRouter(TpaProPlugin plugin, ConfigManager configs, LocaleManager locales, PermissionService permissions,
                            PermissionGroupResolver groups, RequestCoordinator coordinator, RequestRegistry requests,
                            CooldownService cooldowns, UserService users, TeleportService teleports, HistoryService history,
                            StatisticsService statistics, SchedulerAdapter scheduler, GuiManager gui, ClockSource clock) {
        this.plugin = plugin; this.configs = configs; this.locales = locales; this.permissions = permissions;
        this.groups = groups; this.coordinator = coordinator; this.requests = requests; this.cooldowns = cooldowns;
        this.users = users; this.teleports = teleports; this.history = history; this.statistics = statistics;
        this.scheduler = scheduler; this.gui = gui; this.clock = clock;
    }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        try {
            boolean diagnostic = command.getName().equals("tpapro") && (args.length == 0
                    || args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("info"));
            if (!plugin.isReady() && !diagnostic) { send(sender, "errors.not-ready"); return true; }
            if (sender instanceof Player player && plugin.isReady() && !users.loaded(player.getUniqueId())) {
                send(sender, "errors.not-ready"); return true;
            }
            return dispatch(sender, command.getName(), args);
        } catch (PermissionService.PermissionDeniedException denied) {
            send(sender, "errors.no-permission"); return true;
        } catch (PlayerOnlyException ignored) {
            return true;
        } catch (IllegalArgumentException expected) {
            send(sender, "errors.invalid-value", Map.of("expected", expected.getMessage() == null ? "valid input" : expected.getMessage())); return true;
        } catch (RuntimeException unexpected) {
            plugin.getLogger().log(Level.SEVERE, "Command /" + label + " failed safely for " + sender.getName(), unexpected);
            send(sender, "errors.internal"); return true;
        }
    }

    private boolean dispatch(CommandSender sender, String name, String[] args) {
        return switch (name) {
            case "tpa" -> sendRequest(sender, args, RequestType.TPA);
            case "tpahere" -> sendRequest(sender, args, RequestType.TPA_HERE);
            case "tpaccept" -> accept(sender, args);
            case "tpdeny" -> deny(sender, args);
            case "tpcancel" -> cancel(sender, args);
            case "tpatoggle" -> toggle(sender);
            case "tpautaccept" -> autoAccept(sender, args);
            case "tpblock" -> block(sender, args, true);
            case "tpunblock" -> block(sender, args, false);
            case "tpblocklist" -> blockList(sender, args);
            case "tpatrust" -> trust(sender, args);
            case "tpalist" -> requestList(sender, args);
            case "tpasettings" -> settings(sender, args);
            case "tpback" -> back(sender);
            case "tphistory" -> history(sender, args);
            case "tpastats" -> stats(sender, args);
            case "tpapro" -> main(sender, args);
            default -> false;
        };
    }

    private boolean sendRequest(CommandSender sender, String[] args, RequestType type) {
        Player player = player(sender); Permission permission = type == RequestType.TPA ? Permission.TPA : Permission.TPA_HERE;
        permissions.require(sender, permission);
        if (args.length != 1) return usage(player, type == RequestType.TPA ? "/tpa <player>" : "/tpahere <player>");
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { send(player, "errors.player-offline", Map.of("player", args[0])); return true; }
        RequestOutcome outcome = coordinator.send(player.getUniqueId(), target.getUniqueId(), type);
        showOutcome(player, outcome, type == RequestType.TPA ? CooldownType.TPA_SEND : CooldownType.TPA_HERE_SEND); return true;
    }

    private boolean accept(CommandSender sender, String[] args) {
        Player player = player(sender); permissions.require(sender, Permission.ACCEPT);
        if (args.length > 1) return usage(player, "/tpaccept [player]");
        UUID filter = args.length == 0 ? null : onlineUuid(args[0], player); if (args.length == 1 && filter == null) return true;
        showOutcome(player, coordinator.accept(player.getUniqueId(), filter), null); return true;
    }
    private boolean deny(CommandSender sender, String[] args) {
        Player player = player(sender); permissions.require(sender, Permission.DENY);
        if (args.length > 1) return usage(player, "/tpdeny [player]");
        UUID filter = args.length == 0 ? null : onlineUuid(args[0], player); if (args.length == 1 && filter == null) return true;
        showOutcome(player, coordinator.deny(player.getUniqueId(), filter), null); return true;
    }
    private boolean cancel(CommandSender sender, String[] args) {
        Player player = player(sender); permissions.require(sender, Permission.CANCEL);
        if (args.length > 1) return usage(player, "/tpcancel [player]");
        UUID filter = args.length == 0 ? null : onlineUuid(args[0], player); if (args.length == 1 && filter == null) return true;
        showOutcome(player, coordinator.cancel(player.getUniqueId(), filter), null); return true;
    }

    private boolean toggle(CommandSender sender) {
        Player player = player(sender); permissions.require(sender, Permission.TOGGLE); UserProfile profile = users.get(player.getUniqueId());
        boolean disabling = profile.settings().privacyMode() != PrivacyMode.DISABLED;
        users.updateSettings(player.getUniqueId(), profile.settings().withPrivacy(disabling ? PrivacyMode.DISABLED : PrivacyMode.EVERYONE));
        send(player, disabling ? "privacy.disabled" : "privacy.enabled"); return true;
    }

    private boolean autoAccept(CommandSender sender, String[] args) {
        Player player = player(sender); permissions.require(sender, Permission.AUTO_ACCEPT);
        if (args.length > 1) return usage(player, "/tpautaccept [player]");
        if (args.length == 0) {
            PlayerSettings settings = users.get(player.getUniqueId()).settings().withAutoAccept(!users.get(player.getUniqueId()).settings().autoAccept());
            users.updateSettings(player.getUniqueId(), settings); send(player, settings.autoAccept() ? "autaccept.enabled" : "autaccept.disabled"); return true;
        }
        OfflinePlayer target = cached(args[0]); if (target == null) { send(player, "errors.player-not-found", Map.of("player", args[0])); return true; }
        if (target.getUniqueId().equals(player.getUniqueId())) { send(player, "errors.self-target"); return true; }
        boolean enabled = users.toggleAutoAccept(player.getUniqueId(), target.getUniqueId());
        send(player, enabled ? "autaccept.player-enabled" : "autaccept.player-disabled", Map.of("player", display(target))); return true;
    }

    private boolean trust(CommandSender sender, String[] args) {
        Player player = player(sender); permissions.require(sender, Permission.TRUST);
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) return trustList(player);
        if (args.length != 2 || !args[0].equalsIgnoreCase("add") && !args[0].equalsIgnoreCase("remove"))
            return usage(player, "/tpatrust <add|remove|list> [player]");
        OfflinePlayer target = cached(args[1]); if (target == null) { send(player, "errors.player-not-found", Map.of("player", args[1])); return true; }
        if (target.getUniqueId().equals(player.getUniqueId())) { send(player, "errors.self-target"); return true; }
        if (args[0].equalsIgnoreCase("add")) {
            int limit = groups.resolveLimit(configs.get().main().permissionGroups().get("trusted-limit"), player::hasPermission,
                    configs.get().main().trusted().maximum());
            if (users.get(player.getUniqueId()).trusted().size() >= limit) { send(player, "trust.limit", Map.of("limit", limit)); return true; }
            boolean added = users.addTrusted(player.getUniqueId(), target.getUniqueId(), limit);
            if (added && configs.get().main().trusted().mutual())
                users.addTrusted(target.getUniqueId(), player.getUniqueId(), configs.get().main().trusted().maximum());
            if (!target.isOnline()) users.unload(target.getUniqueId());
            send(player, added ? "trust.added" : "trust.already", Map.of("player", display(target)));
        } else {
            boolean removed = users.removeTrusted(player.getUniqueId(), target.getUniqueId());
            if (removed && configs.get().main().trusted().mutual()) users.removeTrusted(target.getUniqueId(), player.getUniqueId());
            if (!target.isOnline()) users.unload(target.getUniqueId());
            send(player, removed ? "trust.removed" : "trust.missing", Map.of("player", display(target)));
        }
        return true;
    }
    private boolean trustList(Player player) {
        if (configs.get().main().guiEnabled()) { gui.openTrusted(player, 1); return true; }
        var list = users.get(player.getUniqueId()).trusted(); if (list.isEmpty()) { send(player, "trust.list-empty"); return true; }
        send(player, "trust.list-title", Map.of("count", list.size())); list.forEach(id -> player.sendMessage(Component.text("• " + display(Bukkit.getOfflinePlayer(id))))); return true;
    }

    private boolean block(CommandSender sender, String[] args, boolean add) {
        Player player = player(sender); permissions.require(sender, Permission.BLOCK);
        if (args.length != 1) return usage(player, add ? "/tpblock <player>" : "/tpunblock <player>");
        OfflinePlayer target = cached(args[0]); if (target == null) { send(player, "errors.player-not-found", Map.of("player", args[0])); return true; }
        if (target.getUniqueId().equals(player.getUniqueId())) { send(player, "errors.self-target"); return true; }
        if (add) {
            boolean added = users.addBlocked(player.getUniqueId(), target.getUniqueId()); int count = added ? coordinator.blockAndInvalidate(player.getUniqueId(), target.getUniqueId()) : 0;
            send(player, added ? "block.added" : "block.already", Map.of("player", display(target), "count", count));
        } else {
            boolean removed = users.removeBlocked(player.getUniqueId(), target.getUniqueId());
            send(player, removed ? "block.removed" : "block.missing", Map.of("player", display(target)));
        }
        return true;
    }
    private boolean blockList(CommandSender sender, String[] args) {
        Player player = player(sender); permissions.require(sender, Permission.BLOCK);
        if (configs.get().main().guiEnabled()) { gui.openBlocked(player, page(args)); return true; }
        var list = users.get(player.getUniqueId()).blocked(); if (list.isEmpty()) { send(player, "block.list-empty"); return true; }
        send(player, "block.list-title", Map.of("count", list.size())); list.forEach(id -> player.sendMessage(Component.text("• " + display(Bukkit.getOfflinePlayer(id))))); return true;
    }

    private boolean requestList(CommandSender sender, String[] args) {
        Player player = player(sender); permissions.require(sender, Permission.LIST);
        if (configs.get().main().guiEnabled()) { gui.openRequests(player, page(args)); return true; }
        List<TeleportRequest> list = requests.incoming(player.getUniqueId()); if (list.isEmpty()) { send(player, "request.no-pending"); return true; }
        sendMultiple(player, list); return true;
    }

    private boolean settings(CommandSender sender, String[] args) {
        Player player = player(sender); permissions.require(sender, Permission.SETTINGS); UserProfile profile = users.get(player.getUniqueId());
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) { if (configs.get().main().guiEnabled()) gui.openSettings(player); else showSettings(player, profile.settings()); return true; }
        if (args.length != 2) return usage(player, "/tpasettings <privacy|language|chat|actionbar|title|sounds|trapwarnings> <value>");
        String key = args[0].toLowerCase(Locale.ROOT), value = args[1]; PlayerSettings updated = profile.settings();
        if (key.equals("privacy")) updated = updated.withPrivacy(parse(PrivacyMode.class, value));
        else if (key.equals("language")) {
            if (!configs.get().main().language().allowPlayerSelection() || !locales.isRegistered(value)) {
                send(player, "settings.locale-invalid", Map.of("locales", String.join(", ", locales.availableLocales()))); return true;
            }
            updated = updated.withLanguage(value); locales.setPreference(player.getUniqueId(), value);
        } else updated = updated.withNotification(key, parseBoolean(value));
        users.updateSettings(player.getUniqueId(), updated); send(player, key.equals("language") ? "settings.language-set" : "settings.updated",
                key.equals("language") ? Map.of("language", value) : Map.of("setting", key, "value", value)); return true;
    }
    private void showSettings(Player player, PlayerSettings settings) {
        send(player, "settings.title"); Map.of("privacy", settings.privacyMode(), "auto_accept", settings.autoAccept(),
                "chat", settings.chatNotifications(), "actionbar", settings.actionBarNotifications(), "title", settings.titleNotifications(),
                "sounds", settings.sounds(), "trap_warnings", settings.trapWarnings(), "language", String.valueOf(settings.language()))
                .forEach((key, value) -> player.sendMessage(locales.component(player.getUniqueId(), "settings.line", Map.of("setting", key, "value", value))));
    }

    private boolean back(CommandSender sender) {
        Player player = player(sender); permissions.require(sender, Permission.BACK);
        if (!configs.get().main().back().enabled()) { send(player, "errors.feature-disabled"); return true; }
        if (!permissions.has(player, Permission.BYPASS_COOLDOWN) && (cooldowns.active(player.getUniqueId(), CooldownType.TPBACK)
                || cooldowns.active(player.getUniqueId(), CooldownType.SUCCESSFUL_TELEPORT))) {
            send(player, "cooldown.active", Map.of("seconds", Math.max(cooldowns.remainingSecondsCeiling(player.getUniqueId(), CooldownType.TPBACK),
                    cooldowns.remainingSecondsCeiling(player.getUniqueId(), CooldownType.SUCCESSFUL_TELEPORT)))); return true;
        }
        StoredLocation stored = users.get(player.getUniqueId()).backLocation(); if (stored == null) { send(player, "teleport.back-missing"); return true; }
        if (!clock.now().isBefore(stored.savedAt().plusSeconds(configs.get().main().back().expirationSeconds()))) { send(player, "teleport.back-expired"); return true; }
        var destination = stored.resolve(); if (destination.isEmpty()) { send(player, "errors.world-unavailable"); return true; }
        int warmup = (int) groups.resolve(configs.get().main().permissionGroups().get("warmup"), player::hasPermission,
                configs.get().main().teleport().warmupSeconds(), PermissionGroupResolver.Benefit.LOWEST);
        double cost = groups.resolve(configs.get().main().permissionGroups().get("teleport-cost"), player::hasPermission,
                configs.get().integrations().economy().backCost(), PermissionGroupResolver.Benefit.LOWEST);
        TeleportService.StartResult result = teleports.startBack(player, destination.get(), cost, warmup);
        if (!result.success()) { send(player, result.status() == TeleportService.StartStatus.ECONOMY ? "economy.failed" : "errors.teleport-failed", Map.of("reason", result.reason())); return true; }
        if (!permissions.has(player, Permission.BYPASS_COOLDOWN)) cooldowns.start(player.getUniqueId(), CooldownType.TPBACK, Duration.ofSeconds(configs.get().main().back().cooldownSeconds()));
        return true;
    }

    private boolean history(CommandSender sender, String[] args) {
        Player player = player(sender); permissions.require(sender, Permission.HISTORY);
        if (!configs.get().main().history().enabled()) { send(player, "errors.feature-disabled"); return true; }
        int page = page(args); int size = groups.resolveLimit(configs.get().main().permissionGroups().get("history-size"), player::hasPermission,
                configs.get().main().history().defaultSize());
        history.list(player.getUniqueId(), size, page).thenAccept(entries -> scheduler.run(() -> {
            if (!player.isOnline()) return;
            if (entries.isEmpty()) { send(player, "history.empty"); return; }
            if (configs.get().main().guiEnabled()) { gui.openHistory(player, 1, entries); return; }
            send(player, "history.title", Map.of("page", page, "total_pages", page + (entries.size() == size ? 1 : 0)));
            entries.forEach(entry -> player.sendMessage(historyLine(player, entry)));
        })).exceptionally(error -> { scheduler.run(() -> send(player, "errors.database-unavailable")); return null; }); return true;
    }
    private Component historyLine(Player player, HistoryEntry entry) {
        return locales.component(player.getUniqueId(), "history.entry", Map.of("time", TIME.format(entry.timestamp()), "type", entry.kind(),
                "world", entry.worldName(), "x", Math.round(entry.x()), "y", Math.round(entry.y()), "z", Math.round(entry.z())));
    }

    private boolean stats(CommandSender sender, String[] args) {
        permissions.require(sender, Permission.STATS); UUID id; String name;
        if (args.length == 0) { Player player = player(sender); id = player.getUniqueId(); name = player.getName(); }
        else {
            permissions.require(sender, Permission.ADMIN_INSPECT); OfflinePlayer target = cached(args[0]);
            if (target == null) { send(sender, "errors.player-not-found", Map.of("player", args[0])); return true; }
            id = target.getUniqueId(); name = display(target);
        }
        if (!configs.get().main().statistics().enabled()) { send(sender, "stats.disabled"); return true; }
        UUID finalId = id; String finalName = name;
        statistics.load(id).thenAccept(value -> scheduler.run(() -> showStats(sender, finalId, finalName, value)))
                .exceptionally(error -> { scheduler.run(() -> send(sender, "errors.database-unavailable")); return null; }); return true;
    }
    private void showStats(CommandSender sender, UUID id, String name, PlayerStatistics stats) {
        send(sender, "stats.title", Map.of("player", name)); Map<String, Object> values = Map.of(
                "requests_sent", stats.requestsSent(), "requests_received", stats.requestsReceived(),
                "accepted", stats.requestsAccepted(), "denied", stats.requestsDenied(), "expired", stats.requestsExpired(),
                "successful_teleports", stats.successfulTeleports(), "failed_teleports", stats.failedTeleports(),
                "cancelled_warmups", stats.cancelledWarmups(), "total_cost", String.format(Locale.ROOT, "%.2f", stats.totalEconomyCost()));
        values.forEach((label, value) -> sender.sendMessage(locales.componentForLocale(locale(sender), "stats.line", Map.of("label", label, "value", value))));
    }

    private boolean main(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) return help(sender);
        String sub = args[0].toLowerCase(Locale.ROOT); String[] tail = Arrays.copyOfRange(args, 1, args.length);
        return switch (sub) {
            case "tpa" -> sendRequest(sender, tail, RequestType.TPA); case "tpahere" -> sendRequest(sender, tail, RequestType.TPA_HERE);
            case "accept" -> internalAccept(sender, tail); case "deny" -> internalDeny(sender, tail); case "confirm" -> confirm(sender, tail);
            case "reload" -> reload(sender); case "info" -> info(sender); case "debug" -> debug(sender);
            case "inspect" -> inspect(sender, tail); case "requests" -> clearRequests(sender, tail);
            case "cooldown" -> resetCooldown(sender, tail); case "forceteleport" -> force(sender, tail);
            default -> help(sender);
        };
    }
    private boolean internalAccept(CommandSender sender, String[] args) { Player player = player(sender); if (args.length != 1) return usage(player, "/tpapro accept <request-id>"); showOutcome(player, coordinator.acceptById(player.getUniqueId(), uuid(args[0])), null); return true; }
    private boolean internalDeny(CommandSender sender, String[] args) { Player player = player(sender); if (args.length != 1) return usage(player, "/tpapro deny <request-id>"); showOutcome(player, coordinator.denyById(player.getUniqueId(), uuid(args[0])), null); return true; }
    private boolean confirm(CommandSender sender, String[] args) { Player player = player(sender); if (args.length != 1 || !args[0].matches("[a-f0-9]{32}")) { send(player, "trap.token-invalid"); return true; }
        RequestOutcome outcome = coordinator.confirm(player.getUniqueId(), args[0]);
        if (outcome.failure() == RequestFailure.CONFIRMATION_REQUIRED || outcome.failure() == RequestFailure.NOT_FOUND) send(player, "trap.token-invalid");
        else showOutcome(player, outcome, null); return true; }
    private boolean help(CommandSender sender) { permissions.require(sender, sender instanceof Player ? Permission.USE : Permission.ADMIN_HELP); send(sender, "help.header"); send(sender, "help.player"); send(sender, "help.social"); send(sender, "help.teleport"); if (permissions.has(sender, Permission.ADMIN)) send(sender, "help.admin"); return true; }
    private boolean reload(CommandSender sender) { permissions.require(sender, Permission.ADMIN_RELOAD); boolean success = plugin.reloadTpaPro(); send(sender, success ? "admin.reload-success" : "admin.reload-failed", success ? Map.of() : Map.of("reason", "validation error")); return true; }
    private boolean info(CommandSender sender) { permissions.require(sender, Permission.ADMIN_INFO); send(sender, "admin.info-title"); plugin.infoLines().forEach(line -> sender.sendMessage(Component.text(line))); return true; }
    private boolean debug(CommandSender sender) { permissions.require(sender, Permission.ADMIN_DEBUG); plugin.setDebugEnabled(!plugin.debugEnabled()); send(sender, plugin.debugEnabled() ? "admin.debug-enabled" : "admin.debug-disabled"); return true; }
    private boolean inspect(CommandSender sender, String[] args) {
        permissions.require(sender, Permission.ADMIN_INSPECT); if (args.length != 1) return usage(sender, "/tpapro inspect <player>");
        OfflinePlayer target = cached(args[0]); if (target == null) { send(sender, "errors.player-not-found", Map.of("player", args[0])); return true; }
        users.load(target.getUniqueId()).thenAccept(profile -> scheduler.run(() -> showInspection(sender, target, profile)))
                .exceptionally(error -> { scheduler.run(() -> send(sender, "errors.database-unavailable")); return null; });
        return true;
    }
    private void showInspection(CommandSender sender, OfflinePlayer target, UserProfile profile) {
        send(sender, "admin.inspect-title", Map.of("player", display(target)));
        for (String line : List.of("UUID: " + target.getUniqueId(), "Privacy: " + profile.settings().privacyMode(),
                "Auto accept: " + profile.settings().autoAccept(), "Pending: " + requests.incoming(target.getUniqueId()).size(),
                "Outgoing: " + requests.outgoing(target.getUniqueId()).size(), "Trusted count: " + profile.trusted().size(),
                "Blocked count: " + profile.blocked().size(), "Cooldowns: " + cooldowns.snapshot(target.getUniqueId()),
                "Warmup: " + teleports.active(target.getUniqueId()), "Language: " + profile.settings().language())) sender.sendMessage(Component.text(line));
        if (!target.isOnline()) users.unload(target.getUniqueId());
    }
    private boolean clearRequests(CommandSender sender, String[] args) { permissions.require(sender, Permission.ADMIN_CLEAR_REQUESTS);
        if (args.length != 2 || !args[0].equalsIgnoreCase("clear")) return usage(sender, "/tpapro requests clear <player>");
        OfflinePlayer target = cached(args[1]); if (target == null) { send(sender, "errors.player-not-found", Map.of("player", args[1])); return true; }
        int count = coordinator.clearAndInvalidate(target.getUniqueId()); send(sender, "request.cleared", Map.of("player", display(target), "count", count)); return true; }
    private boolean resetCooldown(CommandSender sender, String[] args) { permissions.require(sender, Permission.ADMIN_RESET_COOLDOWN);
        if (args.length != 2 || !args[0].equalsIgnoreCase("reset")) return usage(sender, "/tpapro cooldown reset <player>");
        OfflinePlayer target = cached(args[1]); if (target == null) { send(sender, "errors.player-not-found", Map.of("player", args[1])); return true; }
        cooldowns.reset(target.getUniqueId()); send(sender, "cooldown.reset", Map.of("player", display(target))); return true; }
    private boolean force(CommandSender sender, String[] args) { permissions.require(sender, Permission.ADMIN_FORCE_TELEPORT);
        if (args.length != 2) return usage(sender, "/tpapro forceteleport <player> <target>"); Player player = Bukkit.getPlayerExact(args[0]), target = Bukkit.getPlayerExact(args[1]);
        if (player == null || target == null) { send(sender, "errors.player-offline", Map.of("player", player == null ? args[0] : args[1])); return true; }
        TeleportService.StartResult result = teleports.force(player, target); if (result.success()) send(sender, "admin.force-success", Map.of("player", player.getName(), "target", target.getName()));
        else send(sender, "errors.teleport-failed", Map.of("reason", result.reason())); return true; }

    private void showOutcome(Player player, RequestOutcome outcome, CooldownType cooldownType) {
        if (outcome.success()) return;
        if (outcome.failure() == RequestFailure.MULTIPLE_MATCHES) { send(player, "request.multiple"); sendMultiple(player, outcome.candidates()); return; }
        String key = switch (outcome.failure()) {
            case SELF_REQUEST -> "errors.self-target"; case PLAYER_OFFLINE -> "errors.player-offline";
            case DUPLICATE -> "request.duplicate"; case SENDER_LIMIT -> "request.sender-limit"; case TARGET_LIMIT -> "request.target-limit";
            case BLOCKED -> "request.blocked"; case PRIVACY -> "request.privacy"; case COOLDOWN -> "cooldown.active";
            case COMBAT -> "teleport.combat-blocked"; case WORLD -> "teleport.world-blocked"; case REGION -> "teleport.region-blocked";
            case ECONOMY, CONFIRMATION_REQUIRED -> null; case UNSAFE -> "errors.unsafe-destination";
            case NOT_FOUND -> "request.no-pending"; case EXPIRED, INVALID_STATE, EVENT_CANCELLED -> "request.invalid-state";
            case NOT_READY -> "errors.not-ready";
            default -> "errors.internal";
        };
        if (key == null) return;
        long remaining = cooldownType == null ? 0 : Math.max(cooldowns.remainingSecondsCeiling(player.getUniqueId(), cooldownType),
                cooldowns.remainingSecondsCeiling(player.getUniqueId(), CooldownType.SUCCESSFUL_TELEPORT));
        send(player, key, Map.of("player", "player", "target", "target", "limit", configs.get().main().requests().maxOutgoingPerSender(), "seconds", remaining));
    }
    private void sendMultiple(Player player, List<TeleportRequest> candidates) {
        Instant now = clock.now(); for (TeleportRequest request : candidates) {
            OfflinePlayer sender = Bukkit.getOfflinePlayer(request.senderId()); long seconds = Math.max(0, Duration.between(now, request.expiresAt()).toSeconds());
            player.sendMessage(locales.component(player.getUniqueId(), "request.list-entry", Map.of("sender", display(sender), "request_type", request.type(), "seconds", seconds)));
        }
    }

    @Override public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        List<String> choices = new ArrayList<>(); String name = command.getName();
        if (args.length == 1 && List.of("tpa","tpahere","tpaccept","tpdeny","tpcancel","tpautaccept","tpblock","tpunblock","tpastats").contains(name)) choices.addAll(players(sender));
        else if (name.equals("tpatrust")) { if (args.length == 1) choices.addAll(List.of("add","remove","list")); else if (args.length == 2) choices.addAll(players(sender)); }
        else if (name.equals("tpasettings")) { if (args.length == 1) choices.addAll(List.of("gui","privacy","language","chat","actionbar","title","sounds","trapwarnings"));
            else if (args.length == 2) choices.addAll(args[0].equalsIgnoreCase("privacy") ? Arrays.stream(PrivacyMode.values()).map(Enum::name).toList() : List.of("true","false")); }
        else if (name.equals("tpapro")) choices.addAll(mainCompletions(sender, args));
        if (args.length == 0) return choices; String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
    private List<String> mainCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) { List<String> result = new ArrayList<>(List.of("help","tpa","tpahere"));
            if (permissions.has(sender, Permission.ADMIN_RELOAD)) result.add("reload"); if (permissions.has(sender, Permission.ADMIN_INFO)) result.add("info");
            if (permissions.has(sender, Permission.ADMIN_DEBUG)) result.add("debug"); if (permissions.has(sender, Permission.ADMIN_INSPECT)) result.add("inspect");
            if (permissions.has(sender, Permission.ADMIN_CLEAR_REQUESTS)) result.add("requests"); if (permissions.has(sender, Permission.ADMIN_RESET_COOLDOWN)) result.add("cooldown");
            if (permissions.has(sender, Permission.ADMIN_FORCE_TELEPORT)) result.add("forceteleport"); return result; }
        if (args.length == 2 && List.of("tpa","tpahere","inspect").contains(args[0].toLowerCase(Locale.ROOT))) return players(sender);
        if (args.length == 2 && args[0].equalsIgnoreCase("requests")) return List.of("clear");
        if (args.length == 2 && args[0].equalsIgnoreCase("cooldown")) return List.of("reset");
        if (args.length >= 2 && List.of("forceteleport","requests","cooldown").contains(args[0].toLowerCase(Locale.ROOT))) return players(sender);
        return List.of();
    }

    private Player player(CommandSender sender) { if (!(sender instanceof Player player)) { send(sender, "errors.player-only"); throw new PlayerOnlyException(); } return player; }
    private UUID onlineUuid(String name, Player viewer) { Player found = Bukkit.getPlayerExact(name); if (found == null) { send(viewer, "errors.player-offline", Map.of("player", name)); return null; } return found.getUniqueId(); }
    private OfflinePlayer cached(String value) {
        try { if (value.matches("[a-fA-F0-9-]{36}")) return Bukkit.getOfflinePlayer(UUID.fromString(value)); }
        catch (IllegalArgumentException ignored) { }
        Player online = Bukkit.getPlayerExact(value); if (online != null) return online;
        return Bukkit.getOfflinePlayerIfCached(value);
    }
    private List<String> players(CommandSender sender) { return Bukkit.getOnlinePlayers().stream().filter(player -> !(sender instanceof Player viewer) || viewer.canSee(player)).map(Player::getName).toList(); }
    private int page(String[] args) { if (args.length == 0) return 1; try { return Math.max(1, Integer.parseInt(args[0])); } catch (NumberFormatException invalid) { throw new IllegalArgumentException("positive page number"); } }
    private boolean usage(CommandSender sender, String usage) { send(sender, "errors.invalid-usage", Map.of("usage", usage)); return true; }
    private void send(CommandSender sender, String key) { send(sender, key, Map.of()); }
    private void send(CommandSender sender, String key, Map<String, ?> values) { if (sender instanceof Player player) locales.send(player, player.getUniqueId(), key, values); else sender.sendMessage(locales.componentForLocale(configs.get().main().language().defaultLocale(), "prefix", Map.of()).append(locales.componentForLocale(configs.get().main().language().defaultLocale(), key, values))); }
    private String locale(CommandSender sender) { return sender instanceof Player player ? locales.locale(player.getUniqueId()) : configs.get().main().language().defaultLocale(); }
    private static String display(OfflinePlayer player) { return player.getName() == null ? player.getUniqueId().toString().substring(0, 8) : player.getName(); }
    private static UUID uuid(String value) { try { return UUID.fromString(value); } catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("request UUID"); } }
    private static boolean parseBoolean(String value) { if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on")) return true; if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("off")) return false; throw new IllegalArgumentException("true or false"); }
    private static <E extends Enum<E>> E parse(Class<E> type, String value) { try { return Enum.valueOf(type, value.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException invalid) { throw new IllegalArgumentException(String.join(", ", Arrays.stream(type.getEnumConstants()).map(Enum::name).toList())); } }
    private static final class PlayerOnlyException extends RuntimeException { private static final long serialVersionUID = 1L; }
}
