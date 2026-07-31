package com.mrsuffix.tpapro.request;

import com.mrsuffix.tpapro.util.ClockSource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RequestRegistry {
    private final ClockSource clock;
    private final Map<UUID, TeleportRequest> requests = new LinkedHashMap<>();
    private final List<TeleportRequest> unreportedExpirations = new ArrayList<>();

    public RequestRegistry(ClockSource clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized RequestOutcome create(UUID sender, UUID target, RequestType type, Duration lifetime,
                                              DuplicateBehavior duplicateBehavior, int maxOutgoing, int maxIncoming,
                                              Map<String, String> metadata) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(lifetime, "lifetime");
        Objects.requireNonNull(duplicateBehavior, "duplicateBehavior");
        if (sender.equals(target)) return RequestOutcome.failure(RequestFailure.SELF_REQUEST);
        if (lifetime.isZero() || lifetime.isNegative()) throw new IllegalArgumentException("lifetime must be positive");
        expireDueInternal(clock.now());

        Optional<TeleportRequest> duplicate = active().stream()
                .filter(r -> r.senderId().equals(sender) && r.targetId().equals(target) && r.type() == type)
                .findFirst();
        Instant now = clock.now();
        if (duplicate.isPresent()) {
            TeleportRequest existing = duplicate.get();
            return switch (duplicateBehavior) {
                case REJECT -> RequestOutcome.failure(RequestFailure.DUPLICATE);
                case REFRESH -> existing.refresh(safePlus(now, lifetime))
                        ? RequestOutcome.changed(existing, false, true)
                        : RequestOutcome.failure(RequestFailure.INVALID_STATE);
                case REPLACE -> {
                    if (!existing.transitionTo(RequestState.INVALIDATED)) {
                        yield RequestOutcome.failure(RequestFailure.INVALID_STATE);
                    }
                    TeleportRequest replacement = newRequest(sender, target, type, now, lifetime, metadata);
                    requests.put(replacement.id(), replacement);
                    yield RequestOutcome.changed(replacement, true, false, existing);
                }
            };
        }

        if (outgoing(sender).size() >= Math.max(1, maxOutgoing)) {
            return RequestOutcome.failure(RequestFailure.SENDER_LIMIT);
        }
        if (incoming(target).size() >= Math.max(1, maxIncoming)) {
            return RequestOutcome.failure(RequestFailure.TARGET_LIMIT);
        }
        TeleportRequest created = newRequest(sender, target, type, now, lifetime, metadata);
        requests.put(created.id(), created);
        return RequestOutcome.success(created);
    }

    public synchronized RequestOutcome accept(UUID target, UUID senderFilter) {
        return selectAndTransition(target, senderFilter, RequestState.ACCEPTED);
    }

    public synchronized RequestOutcome deny(UUID target, UUID senderFilter) {
        return selectAndTransition(target, senderFilter, RequestState.DENIED);
    }

    public synchronized RequestOutcome cancel(UUID sender, UUID targetFilter) {
        expireDueInternal(clock.now());
        List<TeleportRequest> matches = outgoing(sender).stream()
                .filter(r -> targetFilter == null || r.targetId().equals(targetFilter)).toList();
        return transitionSelection(matches, RequestState.CANCELLED);
    }

    public synchronized RequestOutcome selectIncoming(UUID target, UUID senderFilter) {
        expireDueInternal(clock.now());
        List<TeleportRequest> matches = incoming(target).stream()
                .filter(r -> senderFilter == null || r.senderId().equals(senderFilter)).toList();
        return selection(matches);
    }

    public synchronized RequestOutcome selectOutgoing(UUID sender, UUID targetFilter) {
        expireDueInternal(clock.now());
        List<TeleportRequest> matches = outgoing(sender).stream()
                .filter(r -> targetFilter == null || r.targetId().equals(targetFilter)).toList();
        return selection(matches);
    }

    public synchronized boolean transition(UUID requestId, RequestState next) {
        TeleportRequest request = requests.get(requestId);
        if (request == null) return false;
        if (request.state() == RequestState.PENDING && request.isExpiredAt(clock.now())) {
            if (request.transitionTo(RequestState.EXPIRED)) unreportedExpirations.add(request);
            return false;
        }
        return request.transitionTo(next);
    }

    public synchronized Optional<TeleportRequest> find(UUID id) {
        expireDueInternal(clock.now());
        return Optional.ofNullable(requests.get(id));
    }

    public synchronized List<TeleportRequest> incoming(UUID target) {
        expireDueInternal(clock.now());
        return active().stream().filter(r -> r.state() == RequestState.PENDING && r.targetId().equals(target))
                .sorted(byCreation()).toList();
    }

    public synchronized List<TeleportRequest> outgoing(UUID sender) {
        expireDueInternal(clock.now());
        return active().stream().filter(r -> r.state() == RequestState.PENDING && r.senderId().equals(sender))
                .sorted(byCreation()).toList();
    }

    public synchronized List<TeleportRequest> expireDue() {
        expireDueInternal(clock.now());
        List<TeleportRequest> expired = List.copyOf(unreportedExpirations);
        unreportedExpirations.clear();
        return expired;
    }

    public synchronized List<TeleportRequest> invalidateFor(UUID player, boolean asSender, boolean asTarget) {
        List<TeleportRequest> invalidated = new ArrayList<>();
        for (TeleportRequest request : active()) {
            if (request.state() == RequestState.PENDING
                    && ((asSender && request.senderId().equals(player)) || (asTarget && request.targetId().equals(player)))) {
                if (request.transitionTo(RequestState.INVALIDATED)) invalidated.add(request);
            }
        }
        return List.copyOf(invalidated);
    }

    public synchronized List<TeleportRequest> invalidateBetween(UUID sender, UUID target) {
        List<TeleportRequest> invalidated = new ArrayList<>();
        for (TeleportRequest request : active()) {
            if (request.state() == RequestState.PENDING && request.senderId().equals(sender) && request.targetId().equals(target)
                    && request.transitionTo(RequestState.INVALIDATED)) invalidated.add(request);
        }
        return List.copyOf(invalidated);
    }

    public synchronized List<TeleportRequest> clearFor(UUID player) {
        return invalidateFor(player, true, true);
    }

    public synchronized int activeCount() {
        expireDueInternal(clock.now());
        return active().size();
    }

    public synchronized void pruneTerminal(int retainNewest) {
        List<TeleportRequest> terminal = requests.values().stream().filter(r -> r.state().terminal())
                .sorted(byCreation().reversed()).toList();
        terminal.stream().skip(Math.max(0, retainNewest)).map(TeleportRequest::id).forEach(requests::remove);
    }

    private RequestOutcome selectAndTransition(UUID target, UUID senderFilter, RequestState next) {
        expireDueInternal(clock.now());
        List<TeleportRequest> matches = incoming(target).stream()
                .filter(r -> senderFilter == null || r.senderId().equals(senderFilter)).toList();
        return transitionSelection(matches, next);
    }

    private RequestOutcome transitionSelection(List<TeleportRequest> matches, RequestState next) {
        RequestOutcome selection = selection(matches);
        if (!selection.success()) return selection;
        TeleportRequest selected = selection.request();
        if (selected.isExpiredAt(clock.now())) {
            selected.transitionTo(RequestState.EXPIRED);
            return RequestOutcome.failure(RequestFailure.EXPIRED);
        }
        return selected.transitionTo(next) ? RequestOutcome.success(selected)
                : RequestOutcome.failure(RequestFailure.INVALID_STATE);
    }

    private RequestOutcome selection(List<TeleportRequest> matches) {
        if (matches.isEmpty()) return RequestOutcome.failure(RequestFailure.NOT_FOUND);
        if (matches.size() > 1) return RequestOutcome.multiple(matches);
        TeleportRequest selected = matches.getFirst();
        if (selected.isExpiredAt(clock.now())) {
            selected.transitionTo(RequestState.EXPIRED);
            return RequestOutcome.failure(RequestFailure.EXPIRED);
        }
        return RequestOutcome.success(selected);
    }

    private TeleportRequest newRequest(UUID sender, UUID target, RequestType type, Instant now, Duration lifetime,
                                       Map<String, String> metadata) {
        return new TeleportRequest(UUID.randomUUID(), sender, target, type, now, safePlus(now, lifetime), metadata);
    }

    private List<TeleportRequest> expireDueInternal(Instant now) {
        List<TeleportRequest> expired = new ArrayList<>();
        for (TeleportRequest request : active()) {
            if (request.isExpiredAt(now) && request.transitionTo(RequestState.EXPIRED)) {
                expired.add(request);
                unreportedExpirations.add(request);
            }
        }
        return List.copyOf(expired);
    }

    private List<TeleportRequest> active() {
        return requests.values().stream().filter(r -> !r.state().terminal()).toList();
    }

    private static Comparator<TeleportRequest> byCreation() {
        return Comparator.comparing(TeleportRequest::createdAt).thenComparing(TeleportRequest::id);
    }

    private static Instant safePlus(Instant now, Duration duration) {
        try {
            return now.plus(duration);
        } catch (RuntimeException overflow) {
            return Instant.MAX;
        }
    }
}
