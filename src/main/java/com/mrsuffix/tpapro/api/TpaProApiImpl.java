package com.mrsuffix.tpapro.api;

import com.mrsuffix.tpapro.api.model.RestrictionContext;
import com.mrsuffix.tpapro.api.service.CustomRestriction;
import com.mrsuffix.tpapro.cooldown.CooldownService;
import com.mrsuffix.tpapro.cooldown.CooldownType;
import com.mrsuffix.tpapro.request.RequestCoordinator;
import com.mrsuffix.tpapro.request.RequestOutcome;
import com.mrsuffix.tpapro.request.RequestRegistry;
import com.mrsuffix.tpapro.request.RequestType;
import com.mrsuffix.tpapro.request.TeleportRequest;
import com.mrsuffix.tpapro.restriction.RestrictionRegistry;
import com.mrsuffix.tpapro.settings.PlayerSettings;
import com.mrsuffix.tpapro.user.UserService;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class TpaProApiImpl implements TpaProApi {
    private final RequestCoordinator coordinator; private final RequestRegistry requests; private final CooldownService cooldowns;
    private final UserService users; private final RestrictionRegistry restrictions;
    public TpaProApiImpl(RequestCoordinator coordinator, RequestRegistry requests, CooldownService cooldowns,
                         UserService users, RestrictionRegistry restrictions) {
        this.coordinator = coordinator; this.requests = requests; this.cooldowns = cooldowns; this.users = users; this.restrictions = restrictions;
    }
    @Override public RequestOutcome sendRequest(UUID sender, UUID target, RequestType type) { return coordinator.send(sender, target, type); }
    @Override public Optional<TeleportRequest.Snapshot> findRequest(UUID id) { return requests.find(id).map(TeleportRequest::snapshot); }
    @Override public List<TeleportRequest.Snapshot> incomingRequests(UUID target) { return requests.incoming(target).stream().map(TeleportRequest::snapshot).toList(); }
    @Override public List<TeleportRequest.Snapshot> outgoingRequests(UUID sender) { return requests.outgoing(sender).stream().map(TeleportRequest::snapshot).toList(); }
    @Override public RequestOutcome acceptRequest(UUID target, UUID senderFilter) { return coordinator.accept(target, senderFilter); }
    @Override public RequestOutcome denyRequest(UUID target, UUID senderFilter) { return coordinator.deny(target, senderFilter); }
    @Override public RequestOutcome cancelRequest(UUID sender, UUID targetFilter) { return coordinator.cancel(sender, targetFilter); }
    @Override public Duration cooldownRemaining(UUID player, CooldownType type) { return cooldowns.remaining(player, type); }
    @Override public PlayerSettings playerSettings(UUID player) { return users.get(player).settings(); }
    @Override public AutoCloseable registerRestriction(CustomRestriction restriction) { return restrictions.register(restriction); }
    @Override public RestrictionContext context(UUID sender, UUID target, RequestType type) { return coordinator.context(sender, target, type); }
}
