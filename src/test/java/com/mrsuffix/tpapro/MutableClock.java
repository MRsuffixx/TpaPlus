package com.mrsuffix.tpapro;

import com.mrsuffix.tpapro.util.ClockSource;

import java.time.Duration;
import java.time.Instant;

final class MutableClock implements ClockSource {
    private Instant now;
    MutableClock(Instant now) { this.now = now; }
    @Override public Instant now() { return now; }
    void advance(Duration duration) { now = now.plus(duration); }
}
