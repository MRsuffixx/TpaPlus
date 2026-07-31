package com.mrsuffix.tpapro.teleport;

import java.util.UUID;

public record PositionSnapshot(UUID worldId, double x, double y, double z) {
    public PositionSnapshot {
        if (worldId == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Invalid position");
    }
}
