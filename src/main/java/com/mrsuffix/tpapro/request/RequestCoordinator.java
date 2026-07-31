package com.mrsuffix.tpapro.request;

import com.mrsuffix.tpapro.api.event.TpaRequestAcceptEvent;
import com.mrsuffix.tpapro.api.event.TpaRequestAcceptedEvent;
import com.mrsuffix.tpapro.api.event.TpaRequestCancelEvent;
import com.mrsuffix.tpapro.api.event.TpaRequestCreateEvent;
import com.mrsuffix.tpapro.api.event.TpaRequestCreatedEvent;
import com.mrsuffix.tpapro.api.event.TpaRequestDenyEvent;
import com.mrsuffix.tpapro.api.event.TpaRequestDeniedEvent;
import com.mrsuffix.tpapro.api.event.TpaRequestExpireEvent;
import com.mrsuffix.tpapro.api.model.RestrictionContext;
import com.mrsuffix.tpapro.api.service.CustomRestriction;
import com.mrsuffix.tpapro.config.ConfigManager;
import com.mrsuffix.tpapro.config.ConfigurationBundle.ChargeMode;
import com.mrsuffix.tpapro.config.ConfigurationBundle.TrapMode;
import com.mrsuffix.tpapro.cooldown.CooldownService;
import com.mrsuffix.tpapro.cooldown.CooldownType;
import com.mrsuffix.tpapro.economy.EconomyTransactionService;
import com.mrsuffix.tpapro.history.StatisticsService;
import com.mrsuffix.tpapro.history.TeleportKind;
import com.mrsuffix.tpapro.integration.combat.CombatService;
import com.mrsuffix.tpapro.integration.worldguard.RegionIntegration;
import com.mrsuffix.tpapro.integration.friends.FriendsIntegration;
import com.mrsuffix.tpapro.locale.LocaleManager;
import com.mrsuffix.tpapro.locale.SoundService;
import com.mrsuffix.tpapro.permission.Permission;
import com.mrsuffix.tpapro.permission.PermissionGroupResolver;
import com.mrsuffix.tpapro.permission.PermissionService;
import com.mrsuffix.tpapro.restriction.RestrictionRegistry;
import com.mrsuffix.tpapro.restriction.WorldRestrictionService;
import com.mrsuffix.tpapro.safety.ConfirmationTokenService;
import com.mrsuffix.tpapro.safety.TrapRiskAnalyzer;
import com.mrsuffix.tpapro.settings.RequestPolicyEvaluator;
import com.mrsuffix.tpapro.teleport.TeleportService;
import com.mrsuffix.tpapro.user.UserProfile;
import com.mrsuffix.tpapro.user.UserService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RequestCoordinator {
    private final ConfigManager configs; private final RequestRegistry registry; private final CooldownService cooldowns;
    private final PermissionService permissions; private final PermissionGroupResolver groupResolver; private final UserService users;
    private final RequestPolicyEvaluator policies; private final WorldRestrictionService worlds; private final RegionIntegration regions;
    private final CombatService combat; private final RestrictionRegistry customRestrictions; private final EconomyTransactionService economy;
    private final TrapRiskAnalyzer trapAnalyzer; private final ConfirmationTokenService confirmations; private final TeleportService teleports;
    private final LocaleManager locales; private final SoundService sounds; private final StatisticsService statistics;
    private final FriendsIntegration friends; private final java.util.function.Consumer<String> debug;
    private final Map<String, UUID> confirmationRequests = new ConcurrentHashMap<>();

    public RequestCoordinator(ConfigManager configs, RequestRegistry registry, CooldownService cooldowns,
                              PermissionService permissions, PermissionGroupResolver groupResolver, UserService users,
                              RequestPolicyEvaluator policies, WorldRestrictionService worlds, RegionIntegration regions,
                              CombatService combat, RestrictionRegistry customRestrictions, EconomyTransactionService economy,
                              TrapRiskAnalyzer trapAnalyzer, ConfirmationTokenService confirmations, TeleportService teleports,
                              LocaleManager locales, SoundService sounds, StatisticsService statistics,
                              FriendsIntegration friends, java.util.function.Consumer<String> debug) {
        this.configs = configs; this.registry = registry; this.cooldowns = cooldowns; this.permissions = permissions;
        this.groupResolver = groupResolver; this.users = users; this.policies = policies; this.worlds = worlds;
        this.regions = regions; this.combat = combat; this.customRestrictions = customRestrictions; this.economy = economy;
        this.trapAnalyzer = trapAnalyzer; this.confirmations = confirmations; this.teleports = teleports;
        this.locales = locales; this.sounds = sounds; this.statistics = statistics; this.friends = friends; this.debug = debug;
    }

    public RequestOutcome send(UUID senderId, UUID targetId, RequestType type) {
        requireMain();
        Player sender = Bukkit.getPlayer(senderId), target = Bukkit.getPlayer(targetId);
        if (sender == null || target == null) return RequestOutcome.failure(RequestFailure.PLAYER_OFFLINE);
        if (!users.loaded(senderId) || !users.loaded(targetId)) return RequestOutcome.failure(RequestFailure.NOT_READY);
        Permission permission = type == RequestType.TPA ? Permission.TPA : Permission.TPA_HERE;
        if (!permissions.has(sender, Permission.USE) || !permissions.has(sender, permission))
            throw new PermissionService.PermissionDeniedException(permission);
        if (senderId.equals(targetId)) return RequestOutcome.failure(RequestFailure.SELF_REQUEST);
        if (configs.get().restrictions().combat().blockSending() && !permissions.has(sender, Permission.BYPASS_COMBAT)
                && combat.inCombat(sender)) return RequestOutcome.failure(RequestFailure.COMBAT);
        UserProfile targetProfile = users.get(targetId); boolean trusted = targetProfile.trusts(senderId);
        RequestPolicyEvaluator.Decision policy = policies.evaluate(targetProfile.settings(), targetProfile.blocks(senderId),
                trusted, friends.available() && friends.areFriends(senderId, targetId), friends.available(), configs.get().main().trusted().friendsFallback()
                == com.mrsuffix.tpapro.config.ConfigurationBundle.FriendsFallback.TRUSTED,
                sender.getWorld().equals(target.getWorld()));
        if (policy == RequestPolicyEvaluator.Decision.BLOCKED) return RequestOutcome.failure(RequestFailure.BLOCKED);
        if (policy != RequestPolicyEvaluator.Decision.ALLOW) return RequestOutcome.failure(RequestFailure.PRIVACY);
        CooldownType cooldownType = type == RequestType.TPA ? CooldownType.TPA_SEND : CooldownType.TPA_HERE_SEND;
        if (!permissions.has(sender, Permission.BYPASS_COOLDOWN) && cooldowns.active(senderId, cooldownType))
            return RequestOutcome.failure(RequestFailure.COOLDOWN);
        if (!permissions.has(sender, Permission.BYPASS_COOLDOWN) && cooldowns.active(senderId, CooldownType.SUCCESSFUL_TELEPORT))
            return RequestOutcome.failure(RequestFailure.COOLDOWN);
        Player traveler = type == RequestType.TPA ? sender : target;
        Player destinationOwner = type == RequestType.TPA ? target : sender;
        WorldRestrictionService.Result world = worlds.check(traveler.getWorld(), destinationOwner.getWorld(),
                permissions.has(sender, Permission.BYPASS_WORLD));
        if (!world.allowed()) return RequestOutcome.failure(RequestFailure.WORLD);
        if (configs.get().restrictions().region().enabled() && regions.available()
                && !permissions.has(sender, Permission.BYPASS_REGION)
                && (!regions.allowed(traveler, traveler.getLocation()) || !regions.allowed(traveler, destinationOwner.getLocation())))
            return RequestOutcome.failure(RequestFailure.REGION);
        CustomRestriction.Result custom = customRestrictions.check(context(senderId, targetId, type));
        if (!custom.allowed()) return RequestOutcome.failure(RequestFailure.REGION);
        int maxIncoming = groupResolver.resolveLimit(configs.get().main().permissionGroups().get("max-pending"),
                sender::hasPermission, configs.get().main().requests().maxPendingPerTarget());
        RequestOutcome created = registry.create(senderId, targetId, type,
                Duration.ofSeconds(configs.get().main().requests().expirationSeconds()),
                configs.get().main().requests().duplicateBehavior(), configs.get().main().requests().maxOutgoingPerSender(),
                maxIncoming, Map.of("sender_name", sender.getName(), "target_name", target.getName()));
        if (!created.success()) return created;
        TeleportRequest request = created.request();
        if (created.supersededRequest() != null) settleTerminal(created.supersededRequest());
        debug.accept("request=" + request.id() + " created type=" + type + " sender=" + senderId + " target=" + targetId
                + " replaced=" + created.replaced() + " refreshed=" + created.refreshed());
        if (!created.refreshed()) {
            TpaRequestCreateEvent before = new TpaRequestCreateEvent(request.snapshot());
            Bukkit.getPluginManager().callEvent(before);
            if (before.isCancelled()) {
                registry.transition(request.id(), RequestState.INVALIDATED);
                settleTerminal(request);
                return RequestOutcome.failure(RequestFailure.EVENT_CANCELLED);
            }
        }
        double cost = cost(sender, request, trusted);
        if (!created.refreshed() && configs.get().integrations().economy().enabled()
                && configs.get().integrations().economy().chargeMode() == ChargeMode.ON_REQUEST) {
            EconomyTransactionService.Result charge = economy.chargeOnce(request.id(), senderId, cost,
                    permissions.has(sender, Permission.BYPASS_COST));
            if (!charge.success()) {
                showEconomy(sender, charge);
                registry.transition(request.id(), RequestState.INVALIDATED);
                settleTerminal(request);
                return RequestOutcome.failure(RequestFailure.ECONOMY);
            }
            if (charge.status() == EconomyTransactionService.Status.CHARGED)
                locales.send(sender, senderId, "economy.charged", Map.of("cost", money(charge.amount())));
        }
        int seconds = (int) groupResolver.resolve(configs.get().main().permissionGroups().get("request-cooldown"),
                sender::hasPermission, configs.get().main().requests().sendCooldownSeconds(), PermissionGroupResolver.Benefit.LOWEST);
        if (trusted && configs.get().main().trusted().benefits().reducedCooldown())
            seconds = (int) Math.floor(seconds * configs.get().main().trusted().benefits().cooldownMultiplier());
        if (!permissions.has(sender, Permission.BYPASS_COOLDOWN)) cooldowns.start(senderId, cooldownType, Duration.ofSeconds(seconds));
        notifyRequest(request, sender, target);
        if (!created.refreshed()) {
            statistics.increment(senderId, StatisticsService.Metric.REQUEST_SENT);
            statistics.increment(targetId, StatisticsService.Metric.REQUEST_RECEIVED);
            Bukkit.getPluginManager().callEvent(new TpaRequestCreatedEvent(request.snapshot()));
        }
        boolean auto = targetProfile.autoAccepts(senderId) || targetProfile.settings().autoAccept()
                && (!targetProfile.settings().autoAcceptTrustedOnly() || trusted);
        if (auto) acceptSpecific(target, request, null);
        return created;
    }

    public RequestOutcome accept(UUID targetId, UUID senderFilter) {
        requireMain(); Player target = Bukkit.getPlayer(targetId);
        if (target == null) return RequestOutcome.failure(RequestFailure.PLAYER_OFFLINE);
        if (!users.loaded(targetId)) return RequestOutcome.failure(RequestFailure.NOT_READY);
        RequestOutcome selected = registry.selectIncoming(targetId, senderFilter);
        return selected.success() ? acceptSpecific(target, selected.request(), null) : selected;
    }

    public RequestOutcome acceptById(UUID targetId, UUID requestId) {
        requireMain(); Player target = Bukkit.getPlayer(targetId);
        TeleportRequest request = registry.find(requestId).orElse(null);
        if (target == null || request == null || !request.targetId().equals(targetId)) return RequestOutcome.failure(RequestFailure.NOT_FOUND);
        return acceptSpecific(target, request, null);
    }

    public RequestOutcome confirm(UUID targetId, String token) {
        requireMain(); UUID requestId = confirmationRequests.remove(token);
        if (requestId == null) return RequestOutcome.failure(RequestFailure.NOT_FOUND);
        Player target = Bukkit.getPlayer(targetId); TeleportRequest request = registry.find(requestId).orElse(null);
        if (target == null || request == null || !request.targetId().equals(targetId)) return RequestOutcome.failure(RequestFailure.NOT_FOUND);
        return acceptSpecific(target, request, token);
    }

    private RequestOutcome acceptSpecific(Player target, TeleportRequest request, String token) {
        if (!permissions.has(target, Permission.ACCEPT)) throw new PermissionService.PermissionDeniedException(Permission.ACCEPT);
        if (request.state() != RequestState.PENDING) return RequestOutcome.failure(RequestFailure.INVALID_STATE);
        if (configs.get().restrictions().combat().blockAccepting() && !permissions.has(target, Permission.BYPASS_COMBAT)
                && combat.inCombat(target)) return RequestOutcome.failure(RequestFailure.COMBAT);
        Player sender = Bukkit.getPlayer(request.senderId());
        if (sender == null) {
            registry.transition(request.id(), RequestState.INVALIDATED);
            settleTerminal(request);
            return RequestOutcome.failure(RequestFailure.PLAYER_OFFLINE);
        }
        Player traveler = request.type() == RequestType.TPA ? sender : target;
        Player destinationOwner = request.type() == RequestType.TPA ? target : sender;
        Location destination = destinationOwner.getLocation();
        if (destinationOwner.isInsideVehicle() || destinationOwner.isGliding()) return RequestOutcome.failure(RequestFailure.UNSAFE);
        TrapRiskAnalyzer.Risk risk = trapAnalyzer.analyze(destination, combat.inCombat(destinationOwner));
        TrapMode trapMode = configs.get().main().trap().mode();
        if (risk.risky() && trapMode == TrapMode.BLOCK) return RequestOutcome.failure(RequestFailure.UNSAFE);
        if (risk.risky() && trapMode == TrapMode.WARN && users.get(target.getUniqueId()).settings().trapWarnings()) {
            if (token == null) {
                String issued = confirmations.issue(target.getUniqueId(), request.id(), destination,
                        Duration.ofSeconds(configs.get().main().trap().confirmationSeconds()));
                confirmationRequests.put(issued, request.id()); sendTrapWarning(target, request, issued, risk); return RequestOutcome.failure(RequestFailure.CONFIRMATION_REQUIRED);
            }
            if (!confirmations.consume(token, target.getUniqueId(), request.id(), destination)) return RequestOutcome.failure(RequestFailure.CONFIRMATION_REQUIRED);
        }
        TpaRequestAcceptEvent before = new TpaRequestAcceptEvent(request.snapshot()); Bukkit.getPluginManager().callEvent(before);
        if (before.isCancelled()) return RequestOutcome.failure(RequestFailure.EVENT_CANCELLED);
        boolean trusted = users.get(target.getUniqueId()).trusts(sender.getUniqueId());
        double cost = cost(sender, request, trusted);
        if (configs.get().integrations().economy().enabled() && configs.get().integrations().economy().chargeMode() == ChargeMode.ON_ACCEPT) {
            EconomyTransactionService.Result charge = economy.chargeOnce(request.id(), sender.getUniqueId(), cost,
                    permissions.has(sender, Permission.BYPASS_COST));
            if (!charge.success()) {
                showEconomy(sender, charge); locales.send(target, target.getUniqueId(), "economy.failed");
                return RequestOutcome.failure(RequestFailure.ECONOMY);
            }
            if (charge.status() == EconomyTransactionService.Status.CHARGED)
                locales.send(sender, sender.getUniqueId(), "economy.charged", Map.of("cost", money(charge.amount())));
        }
        if (!registry.transition(request.id(), RequestState.ACCEPTED)) {
            settleTerminal(request);
            return RequestOutcome.failure(RequestFailure.INVALID_STATE);
        }
        debug.accept("request=" + request.id() + " accepted target=" + target.getUniqueId() + " traveler=" + traveler.getUniqueId());
        int warmup = (int) groupResolver.resolve(configs.get().main().permissionGroups().get("warmup"), traveler::hasPermission,
                configs.get().main().teleport().warmupSeconds(), PermissionGroupResolver.Benefit.LOWEST);
        if (trusted && configs.get().main().trusted().benefits().reducedWarmup())
            warmup = (int) Math.floor(warmup * configs.get().main().trusted().benefits().warmupMultiplier());
        TeleportKind kind = request.type() == RequestType.TPA ? TeleportKind.TPA : TeleportKind.TPA_HERE;
        TeleportService.StartResult started = teleports.startRequest(request, traveler, destination, kind,
                traveler.equals(sender) ? target.getUniqueId() : sender.getUniqueId(), warmup, cost);
        if (!started.success()) {
            registry.transition(request.id(), RequestState.FAILED);
            settleTerminal(request);
            return RequestOutcome.failure(started.status() == TeleportService.StartStatus.ECONOMY ? RequestFailure.ECONOMY : RequestFailure.UNSAFE);
        }
        locales.send(sender, sender.getUniqueId(), "request.accepted-sender", Map.of("target", target.getName()));
        locales.send(target, target.getUniqueId(), "request.accepted-target", Map.of("sender", sender.getName()));
        sounds.play(sender, "request-accepted"); sounds.play(target, "request-accepted");
        statistics.increment(target.getUniqueId(), StatisticsService.Metric.REQUEST_ACCEPTED);
        Bukkit.getPluginManager().callEvent(new TpaRequestAcceptedEvent(request.snapshot()));
        return RequestOutcome.success(request);
    }

    public RequestOutcome deny(UUID targetId, UUID senderFilter) {
        requireMain(); Player target = Bukkit.getPlayer(targetId);
        if (target == null) return RequestOutcome.failure(RequestFailure.PLAYER_OFFLINE);
        if (!permissions.has(target, Permission.DENY)) throw new PermissionService.PermissionDeniedException(Permission.DENY);
        RequestOutcome selection = registry.selectIncoming(targetId, senderFilter); if (!selection.success()) return selection;
        TeleportRequest request = selection.request(); TpaRequestDenyEvent before = new TpaRequestDenyEvent(request.snapshot());
        Bukkit.getPluginManager().callEvent(before); if (before.isCancelled()) return RequestOutcome.failure(RequestFailure.EVENT_CANCELLED);
        if (!registry.transition(request.id(), RequestState.DENIED)) return RequestOutcome.failure(RequestFailure.INVALID_STATE);
        debug.accept("request=" + request.id() + " denied target=" + targetId);
        Player sender = Bukkit.getPlayer(request.senderId());
        if (sender != null) { locales.send(sender, sender.getUniqueId(), "request.denied-sender", Map.of("target", target.getName())); sounds.play(sender, "request-denied"); }
        locales.send(target, targetId, "request.denied-target", Map.of("sender", sender == null ? request.senderId() : sender.getName()));
        sounds.play(target, "request-denied"); statistics.increment(targetId, StatisticsService.Metric.REQUEST_DENIED);
        settleTerminal(request);
        Bukkit.getPluginManager().callEvent(new TpaRequestDeniedEvent(request.snapshot())); return RequestOutcome.success(request);
    }

    public RequestOutcome denyById(UUID targetId, UUID requestId) {
        requireMain(); TeleportRequest request = registry.find(requestId).orElse(null);
        if (request == null || !request.targetId().equals(targetId)) return RequestOutcome.failure(RequestFailure.NOT_FOUND);
        return deny(targetId, request.senderId());
    }

    public RequestOutcome cancel(UUID senderId, UUID targetFilter) {
        requireMain(); Player sender = Bukkit.getPlayer(senderId);
        if (sender == null) return RequestOutcome.failure(RequestFailure.PLAYER_OFFLINE);
        if (!permissions.has(sender, Permission.CANCEL)) throw new PermissionService.PermissionDeniedException(Permission.CANCEL);
        RequestOutcome outcome = registry.cancel(senderId, targetFilter); if (!outcome.success()) return outcome;
        TeleportRequest request = outcome.request(); Player target = Bukkit.getPlayer(request.targetId());
        debug.accept("request=" + request.id() + " cancelled sender=" + senderId);
        locales.send(sender, senderId, "request.cancelled-sender", Map.of("target", target == null ? request.targetId() : target.getName()));
        if (target != null) locales.send(target, target.getUniqueId(), "request.cancelled-target", Map.of("sender", sender.getName()));
        settleTerminal(request);
        Bukkit.getPluginManager().callEvent(new TpaRequestCancelEvent(request.snapshot())); return outcome;
    }

    public void cleanupExpired() {
        for (TeleportRequest request : registry.expireDue()) {
            debug.accept("request=" + request.id() + " expired");
            Player sender = Bukkit.getPlayer(request.senderId()), target = Bukkit.getPlayer(request.targetId());
            if (sender != null) { locales.send(sender, sender.getUniqueId(), "request.expired-sender", Map.of("target", target == null ? request.targetId() : target.getName())); sounds.play(sender, "request-expired"); }
            if (target != null) { locales.send(target, target.getUniqueId(), "request.expired-target", Map.of("sender", sender == null ? request.senderId() : sender.getName())); sounds.play(target, "request-expired"); }
            statistics.increment(request.senderId(), StatisticsService.Metric.REQUEST_EXPIRED);
            settleTerminal(request);
            Bukkit.getPluginManager().callEvent(new TpaRequestExpireEvent(request.snapshot()));
        }
        registry.pruneTerminal(500); confirmations.prune();
    }

    public void handleQuit(UUID playerId) {
        boolean sender = configs.get().main().requests().cancelOnSenderQuit();
        boolean target = configs.get().main().requests().invalidateOnTargetQuit();
        for (TeleportRequest request : registry.invalidateFor(playerId, sender, target)) {
            Player other = Bukkit.getPlayer(request.senderId().equals(playerId) ? request.targetId() : request.senderId());
            if (other != null) locales.send(other, other.getUniqueId(), "request.cancelled-target",
                    Map.of("sender", playerId.toString()));
            settleTerminal(request);
            Bukkit.getPluginManager().callEvent(new TpaRequestCancelEvent(request.snapshot()));
        }
    }

    public int blockAndInvalidate(UUID blocker, UUID blocked) {
        List<TeleportRequest> invalidated = registry.invalidateBetween(blocked, blocker);
        invalidated.forEach(this::settleTerminal);
        return invalidated.size();
    }

    public int clearAndInvalidate(UUID playerId) {
        List<TeleportRequest> invalidated = registry.clearFor(playerId);
        invalidated.forEach(this::settleTerminal);
        return invalidated.size();
    }
    public RestrictionContext context(UUID sender, UUID target, RequestType type) {
        Player senderPlayer = Bukkit.getPlayer(sender), targetPlayer = Bukkit.getPlayer(target);
        return new RestrictionContext(sender, target, type, senderPlayer == null ? null : senderPlayer.getWorld().getName(),
                targetPlayer == null ? null : targetPlayer.getWorld().getName());
    }

    private double cost(Player payer, TeleportRequest request, boolean trusted) {
        double fallback = configs.get().integrations().economy().cost(request.type());
        double cost = groupResolver.resolve(configs.get().main().permissionGroups().get("teleport-cost"), payer::hasPermission,
                fallback, PermissionGroupResolver.Benefit.LOWEST);
        if (trusted && configs.get().main().trusted().benefits().reducedCost()) cost *= configs.get().main().trusted().benefits().costMultiplier();
        return Math.max(0, cost);
    }

    private void showEconomy(Player player, EconomyTransactionService.Result result) {
        if (result.status() == EconomyTransactionService.Status.INSUFFICIENT)
            locales.send(player, player.getUniqueId(), "economy.insufficient", Map.of("cost", money(result.amount()), "balance", money(result.balance())));
        else if (result.status() == EconomyTransactionService.Status.UNAVAILABLE)
            locales.send(player, player.getUniqueId(), "economy.unavailable");
        else locales.send(player, player.getUniqueId(), "economy.failed");
    }

    private void settleTerminal(TeleportRequest request) {
        confirmations.invalidateRequest(request.id());
        if (!configs.get().integrations().economy().refundOnFailure()) {
            economy.forget(request.id());
            return;
        }
        refund(request.id(), request.senderId());
    }

    private void refund(UUID transaction, UUID payerId) {
        EconomyTransactionService.Result result = economy.refundOnce(transaction); Player payer = Bukkit.getPlayer(payerId);
        if (payer != null && result.status() == EconomyTransactionService.Status.REFUNDED)
            locales.send(payer, payerId, "economy.refunded", Map.of("cost", money(result.amount())));
        if (result.status() == EconomyTransactionService.Status.FAILED)
            debug.accept("transaction=" + transaction + " refund-failed error=" + result.error());
        economy.forget(transaction);
    }

    private static String money(double value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }

    private void notifyRequest(TeleportRequest request, Player sender, Player target) {
        locales.send(sender, sender.getUniqueId(), "request.sent", Map.of("target", target.getName())); sounds.play(sender, "request-sent");
        Component base = locales.prefixed(target.getUniqueId(), "request.received.message",
                Map.of("sender", sender.getName(), "request_type", request.type().name()));
        String acceptCommand = "/tpapro accept " + request.id(); String denyCommand = "/tpdeny " + sender.getName();
        Component accept = locales.component(target.getUniqueId(), "request.received.accept-button", Map.of())
                .clickEvent(ClickEvent.runCommand(acceptCommand)).hoverEvent(HoverEvent.showText(
                        locales.component(target.getUniqueId(), "request.received.accept-hover", Map.of())));
        Component deny = locales.component(target.getUniqueId(), "request.received.deny-button", Map.of())
                .clickEvent(ClickEvent.runCommand(denyCommand)).hoverEvent(HoverEvent.showText(
                        locales.component(target.getUniqueId(), "request.received.deny-hover", Map.of())));
        target.sendMessage(base.appendSpace().append(accept).appendSpace().append(deny)); sounds.play(target, "request-received");
    }

    private void sendTrapWarning(Player target, TeleportRequest request, String token, TrapRiskAnalyzer.Risk risk) {
        Component base = locales.prefixed(target.getUniqueId(), "trap.warning", Map.of("reason", String.join(", ", risk.reasons())));
        Component proceed = locales.component(target.getUniqueId(), "trap.continue", Map.of())
                .clickEvent(ClickEvent.runCommand("/tpapro confirm " + token)).hoverEvent(HoverEvent.showText(
                        locales.component(target.getUniqueId(), "trap.continue-hover", Map.of())));
        Component cancel = locales.component(target.getUniqueId(), "trap.cancel", Map.of())
                .clickEvent(ClickEvent.runCommand("/tpapro deny " + request.id())).hoverEvent(HoverEvent.showText(
                        locales.component(target.getUniqueId(), "trap.cancel-hover", Map.of())));
        target.sendMessage(base.appendSpace().append(proceed).appendSpace().append(cancel)); sounds.play(target, "warning");
    }

    private static void requireMain() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("TpaPro request API must be called on the server thread"); }
}
