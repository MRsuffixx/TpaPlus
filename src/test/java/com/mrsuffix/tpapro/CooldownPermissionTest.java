package com.mrsuffix.tpapro;

import com.mrsuffix.tpapro.cooldown.CooldownService;
import com.mrsuffix.tpapro.cooldown.CooldownType;
import com.mrsuffix.tpapro.permission.PermissionGroupResolver;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CooldownPermissionTest {
    @Test void cooldownRoundsUpAndNeverBecomesNegative() {
        MutableClock clock = new MutableClock(Instant.EPOCH); CooldownService service = new CooldownService(clock); UUID id = UUID.randomUUID();
        service.start(id, CooldownType.TPA_SEND, Duration.ofMillis(1501));
        assertThat(service.remainingSecondsCeiling(id, CooldownType.TPA_SEND)).isEqualTo(2);
        clock.advance(Duration.ofSeconds(2)); assertThat(service.remaining(id, CooldownType.TPA_SEND)).isZero();
        assertThat(service.remainingSecondsCeiling(id, CooldownType.TPA_SEND)).isZero();
    }
    @Test void restoringExpiredCooldownDoesNothing() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z")); CooldownService service = new CooldownService(clock); UUID id = UUID.randomUUID();
        service.restore(id, CooldownType.TPBACK, clock.now().minusSeconds(1)); assertThat(service.active(id, CooldownType.TPBACK)).isFalse();
    }
    @Test void permissionGroupsChooseLowestForDurationsAndHighestForLimits() {
        PermissionGroupResolver resolver = new PermissionGroupResolver(); Set<String> held = Set.of("tpapro.use", "tpapro.vip", "tpapro.admin");
        List<PermissionGroupResolver.Entry> entries = List.of(new PermissionGroupResolver.Entry("tpapro.use", 5),
                new PermissionGroupResolver.Entry("tpapro.vip", 2), new PermissionGroupResolver.Entry("tpapro.admin", 10));
        assertThat(resolver.resolve(entries, held::contains, 30, PermissionGroupResolver.Benefit.LOWEST)).isEqualTo(2);
        assertThat(resolver.resolveLimit(entries, held::contains, 1)).isEqualTo(10);
    }
}
