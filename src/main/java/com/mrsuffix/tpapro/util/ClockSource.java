package com.mrsuffix.tpapro.util;

import java.time.Instant;

@FunctionalInterface
public interface ClockSource {
    Instant now();

    static ClockSource system() {
        return Instant::now;
    }
}
