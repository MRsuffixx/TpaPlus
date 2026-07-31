package com.mrsuffix.tpapro;

import com.mrsuffix.tpapro.database.AsyncWriteTracker;
import com.mrsuffix.tpapro.safety.ConfirmationTokenService;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfirmationAndWriteTrackerTest {
    @Test void confirmationIsRequestBoundSingleUseAndCapacityReturnsFailureInsteadOfThrowing() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-01T00:00:00Z"));
        ConfirmationTokenService tokens = new ConfirmationTokenService(clock);
        World world = mock(World.class); when(world.getUID()).thenReturn(UUID.randomUUID());
        Location destination = new Location(world, 10.5, 64, -2.5);
        UUID player = UUID.randomUUID(), request = UUID.randomUUID();
        String token = tokens.issue(player, request, destination, Duration.ofSeconds(10)).orElseThrow();
        assertThat(tokens.request(token, player)).contains(request);
        assertThat(tokens.consume(token, player, request, destination)).isTrue();
        assertThat(tokens.consume(token, player, request, destination)).isFalse();

        for (int index = 0; index < 2048; index++)
            assertThat(tokens.issue(UUID.randomUUID(), UUID.randomUUID(), destination, Duration.ofMinutes(1))).isPresent();
        assertThat(tokens.issue(UUID.randomUUID(), UUID.randomUUID(), destination, Duration.ofMinutes(1))).isEmpty();
        assertThat(tokens.size()).isEqualTo(2048);
    }

    @Test void pendingWritesAreAwaitedAndRemovedAfterCompletion() {
        AsyncWriteTracker tracker = new AsyncWriteTracker(); CompletableFuture<Void> write = new CompletableFuture<>();
        tracker.track(write); assertThat(tracker.size()).isOne();
        assertThat(tracker.await(Duration.ofMillis(5))).isFalse();
        write.complete(null);
        assertThat(tracker.await(Duration.ofSeconds(1))).isTrue(); assertThat(tracker.size()).isZero();
    }
}
