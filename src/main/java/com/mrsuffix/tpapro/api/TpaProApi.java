package com.mrsuffix.tpapro.api;

import com.mrsuffix.tpapro.api.model.RestrictionContext;
import com.mrsuffix.tpapro.api.service.CustomRestriction;
import com.mrsuffix.tpapro.cooldown.CooldownType;
import com.mrsuffix.tpapro.request.RequestOutcome;
import com.mrsuffix.tpapro.request.RequestType;
import com.mrsuffix.tpapro.request.TeleportRequest;
import com.mrsuffix.tpapro.settings.PlayerSettings;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** All methods must be invoked on the server thread unless their documentation explicitly says otherwise. */
public interface TpaProApi {
    RequestOutcome sendRequest(UUID sender, UUID target, RequestType type);
    Optional<TeleportRequest.Snapshot> findRequest(UUID requestId);
    List<TeleportRequest.Snapshot> incomingRequests(UUID target);
    List<TeleportRequest.Snapshot> outgoingRequests(UUID sender);
    RequestOutcome acceptRequest(UUID target, UUID senderFilter);
    RequestOutcome denyRequest(UUID target, UUID senderFilter);
    RequestOutcome cancelRequest(UUID sender, UUID targetFilter);
    Duration cooldownRemaining(UUID player, CooldownType type);
    PlayerSettings playerSettings(UUID player);
    AutoCloseable registerRestriction(CustomRestriction restriction);
    RestrictionContext context(UUID sender, UUID target, RequestType type);
}
