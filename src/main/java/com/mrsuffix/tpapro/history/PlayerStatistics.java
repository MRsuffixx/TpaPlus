package com.mrsuffix.tpapro.history;

import java.util.UUID;

public record PlayerStatistics(long requestsSent, long requestsReceived, long requestsAccepted, long requestsDenied,
                               long requestsExpired, long successfulTeleports, long failedTeleports,
                               long cancelledWarmups, double totalEconomyCost, UUID mostFrequentTarget) {
    public static PlayerStatistics empty() { return new PlayerStatistics(0, 0, 0, 0, 0, 0, 0, 0, 0, null); }
}
