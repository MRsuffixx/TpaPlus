package com.mrsuffix.tpapro.util;

public final class Checks {
    private Checks() {
    }

    public static int boundedInt(int value, int minimum, int maximum, int fallback) {
        return value < minimum || value > maximum ? fallback : value;
    }

    public static long boundedLong(long value, long minimum, long maximum, long fallback) {
        return value < minimum || value > maximum ? fallback : value;
    }

    public static double boundedDouble(double value, double minimum, double maximum, double fallback) {
        return !Double.isFinite(value) || value < minimum || value > maximum ? fallback : value;
    }

    public static double nonNegativeMoney(double value, double fallback) {
        return !Double.isFinite(value) || value < 0 ? fallback : value;
    }
}
