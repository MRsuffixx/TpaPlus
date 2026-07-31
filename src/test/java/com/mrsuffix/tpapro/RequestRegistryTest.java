package com.mrsuffix.tpapro;

import com.mrsuffix.tpapro.request.DuplicateBehavior;
import com.mrsuffix.tpapro.request.RequestFailure;
import com.mrsuffix.tpapro.request.RequestOutcome;
import com.mrsuffix.tpapro.request.RequestRegistry;
import com.mrsuffix.tpapro.request.RequestState;
import com.mrsuffix.tpapro.request.RequestType;
import com.mrsuffix.tpapro.request.TeleportRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestRegistryTest {
    private MutableClock clock; private RequestRegistry registry; private UUID sender; private UUID target;
    @BeforeEach void setUp() { clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z")); registry = new RequestRegistry(clock); sender = UUID.randomUUID(); target = UUID.randomUUID(); }
    private RequestOutcome create(DuplicateBehavior behavior) { return registry.create(sender, target, RequestType.TPA, Duration.ofSeconds(60), behavior, 3, 5, Map.of()); }

    @Test void createsPendingRequestWithIdentityAndExpiry() {
        RequestOutcome result = create(DuplicateBehavior.REJECT);
        assertThat(result.success()).isTrue(); assertThat(result.request().state()).isEqualTo(RequestState.PENDING);
        assertThat(result.request().senderId()).isEqualTo(sender); assertThat(result.request().targetId()).isEqualTo(target);
        assertThat(result.request().expiresAt()).isEqualTo(clock.now().plusSeconds(60));
    }
    @Test void rejectsSelfRequest() {
        RequestOutcome result = registry.create(sender, sender, RequestType.TPA, Duration.ofSeconds(60), DuplicateBehavior.REJECT, 3, 5, Map.of());
        assertThat(result.success()).isFalse(); assertThat(result.failure()).isEqualTo(RequestFailure.SELF_REQUEST);
    }
    @Test void duplicateRejectReplaceAndRefreshBehaveDifferently() {
        TeleportRequest first = create(DuplicateBehavior.REJECT).request();
        assertThat(create(DuplicateBehavior.REJECT).failure()).isEqualTo(RequestFailure.DUPLICATE);
        clock.advance(Duration.ofSeconds(5)); RequestOutcome refreshed = create(DuplicateBehavior.REFRESH);
        assertThat(refreshed.refreshed()).isTrue(); assertThat(refreshed.request().id()).isEqualTo(first.id());
        RequestOutcome replaced = create(DuplicateBehavior.REPLACE);
        assertThat(replaced.replaced()).isTrue(); assertThat(replaced.request().id()).isNotEqualTo(first.id());
        assertThat(first.state()).isEqualTo(RequestState.INVALIDATED);
    }
    @Test void expiresAndRemovesFromActionableIndexes() {
        TeleportRequest request = create(DuplicateBehavior.REJECT).request(); clock.advance(Duration.ofSeconds(60));
        assertThat(registry.expireDue()).containsExactly(request); assertThat(request.state()).isEqualTo(RequestState.EXPIRED);
        assertThat(registry.incoming(target)).isEmpty();
    }
    @Test void acceptsExactlyOneRequest() {
        TeleportRequest request = create(DuplicateBehavior.REJECT).request(); RequestOutcome accepted = registry.accept(target, null);
        assertThat(accepted.success()).isTrue(); assertThat(request.state()).isEqualTo(RequestState.ACCEPTED);
    }
    @Test void deniesRequest() {
        TeleportRequest request = create(DuplicateBehavior.REJECT).request(); assertThat(registry.deny(target, sender).success()).isTrue();
        assertThat(request.state()).isEqualTo(RequestState.DENIED);
    }
    @Test void senderCancelsOutgoingRequest() {
        TeleportRequest request = create(DuplicateBehavior.REJECT).request(); assertThat(registry.cancel(sender, target).success()).isTrue();
        assertThat(request.state()).isEqualTo(RequestState.CANCELLED);
    }
    @Test void doubleAcceptanceIsPrevented() {
        create(DuplicateBehavior.REJECT); assertThat(registry.accept(target, null).success()).isTrue();
        assertThat(registry.accept(target, null).failure()).isEqualTo(RequestFailure.NOT_FOUND);
    }
    @Test void acceptanceAfterExpiryIsPrevented() {
        create(DuplicateBehavior.REJECT); clock.advance(Duration.ofSeconds(61));
        assertThat(registry.accept(target, null).success()).isFalse(); assertThat(registry.activeCount()).isZero();
    }
    @Test void multiplePendingRequiresExplicitSelection() {
        UUID other = UUID.randomUUID(); create(DuplicateBehavior.REJECT);
        registry.create(other, target, RequestType.TPA_HERE, Duration.ofSeconds(60), DuplicateBehavior.REJECT, 3, 5, Map.of());
        RequestOutcome result = registry.accept(target, null);
        assertThat(result.failure()).isEqualTo(RequestFailure.MULTIPLE_MATCHES); assertThat(result.candidates()).hasSize(2);
        assertThat(registry.accept(target, sender).success()).isTrue();
    }
    @Test void outgoingAndIncomingLimitsAreEnforced() {
        create(DuplicateBehavior.REJECT);
        RequestOutcome senderLimited = registry.create(sender, UUID.randomUUID(), RequestType.TPA, Duration.ofSeconds(60), DuplicateBehavior.REJECT, 1, 5, Map.of());
        assertThat(senderLimited.failure()).isEqualTo(RequestFailure.SENDER_LIMIT);
        RequestOutcome targetLimited = registry.create(UUID.randomUUID(), target, RequestType.TPA_HERE, Duration.ofSeconds(60), DuplicateBehavior.REJECT, 3, 1, Map.of());
        assertThat(targetLimited.failure()).isEqualTo(RequestFailure.TARGET_LIMIT);
    }
}
