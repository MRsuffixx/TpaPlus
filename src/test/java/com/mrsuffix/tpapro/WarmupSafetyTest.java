package com.mrsuffix.tpapro;

import com.mrsuffix.tpapro.safety.SafetyRules;
import com.mrsuffix.tpapro.teleport.MovementPolicy;
import com.mrsuffix.tpapro.teleport.PositionSnapshot;
import com.mrsuffix.tpapro.teleport.WarmupRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WarmupSafetyTest {
    @Test void newerWarmupInvalidatesOldSessionToken() {
        WarmupRegistry registry = new WarmupRegistry(); UUID player = UUID.randomUUID(); PositionSnapshot position = new PositionSnapshot(UUID.randomUUID(), 1, 2, 3);
        var old = registry.start(player, UUID.randomUUID(), position, Instant.EPOCH, Instant.EPOCH.plusSeconds(5));
        var current = registry.start(player, UUID.randomUUID(), position, Instant.EPOCH, Instant.EPOCH.plusSeconds(3));
        assertThat(registry.isCurrent(player, old.id())).isFalse(); assertThat(registry.isCurrent(player, current.id())).isTrue();
        assertThat(registry.complete(player, old.id(), Instant.EPOCH.plusSeconds(10))).isEmpty();
    }
    @Test void movementUsesPositionToleranceAndIgnoresOrientationByDesign() {
        MovementPolicy policy = new MovementPolicy(); UUID world = UUID.randomUUID(); PositionSnapshot anchor = new PositionSnapshot(world, 0, 64, 0);
        assertThat(policy.moved(anchor, new PositionSnapshot(world, 0.05, 64, 0.05), 0.15)).isFalse();
        assertThat(policy.moved(anchor, new PositionSnapshot(world, 0.2, 64, 0), 0.15)).isTrue();
        assertThat(policy.moved(anchor, new PositionSnapshot(UUID.randomUUID(), 0, 64, 0), 100)).isTrue();
    }
    @Test void safetyRulesAcceptClearSpaceAndRejectConfiguredHazards() {
        SafetyRules rules = new SafetyRules(); SafetyRules.Cell ground = new SafetyRules.Cell(false, true, SafetyRules.Hazard.NONE);
        SafetyRules.Cell air = new SafetyRules.Cell(true, false, SafetyRules.Hazard.NONE);
        SafetyRules.Options all = new SafetyRules.Options(true, true, true, true, true, true, true, true, true, true);
        assertThat(rules.validate(ground, air, air, false, false, all)).isEqualTo(SafetyRules.Hazard.NONE);
        assertThat(rules.validate(new SafetyRules.Cell(true, false, SafetyRules.Hazard.LAVA), air, air, false, false, all)).isEqualTo(SafetyRules.Hazard.LAVA);
        assertThat(rules.validate(ground, new SafetyRules.Cell(false, true, SafetyRules.Hazard.NONE), air, false, false, all)).isEqualTo(SafetyRules.Hazard.SUFFOCATION);
        assertThat(rules.validate(air, air, air, false, true, all)).isEqualTo(SafetyRules.Hazard.FALL);
    }
}
