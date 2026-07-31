package com.mrsuffix.tpapro;

import com.mrsuffix.tpapro.scheduler.ReloadableTask;
import com.mrsuffix.tpapro.scheduler.ScheduledTask;
import com.mrsuffix.tpapro.settings.PlayerSettings;
import com.mrsuffix.tpapro.util.Checks;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationReloadTest {
    @Test void invalidConfigurationNumbersUseSafeDefaults() {
        assertThat(Checks.boundedInt(-1, 0, 10, 5)).isEqualTo(5);
        assertThat(Checks.boundedLong(Long.MAX_VALUE, 0, 100, 10)).isEqualTo(10);
        assertThat(Checks.boundedDouble(Double.NaN, 0, 1, 0.5)).isEqualTo(0.5);
        assertThat(Checks.nonNegativeMoney(Double.POSITIVE_INFINITY, 7)).isEqualTo(7);
        assertThat(Checks.nonNegativeMoney(-1, 7)).isEqualTo(7);
        assertThatThrownBy(() -> PlayerSettings.defaults("../../evil.yml")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void reloadableTaskCancelsOldTaskAndCloseIsIdempotent() {
        ReloadableTask slot = new ReloadableTask(); FakeTask first = new FakeTask(), second = new FakeTask();
        slot.replace(first); slot.replace(second); assertThat(first.cancelled).isTrue(); assertThat(second.cancelled).isFalse();
        slot.close(); slot.close(); assertThat(second.cancelled).isTrue(); assertThat(slot.active()).isFalse();
    }
    private static final class FakeTask implements ScheduledTask {
        private boolean cancelled; @Override public void cancel() { cancelled = true; } @Override public boolean cancelled() { return cancelled; }
    }
}
