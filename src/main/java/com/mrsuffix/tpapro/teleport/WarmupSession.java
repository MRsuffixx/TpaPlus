package com.mrsuffix.tpapro.teleport;

import java.time.Instant;
import java.util.UUID;

public record WarmupSession(UUID id, UUID playerId, UUID requestId, PositionSnapshot anchor, Instant startedAt,
                            Instant completesAt) { }
