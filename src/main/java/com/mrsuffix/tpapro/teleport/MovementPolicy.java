package com.mrsuffix.tpapro.teleport;

public final class MovementPolicy {
    public boolean moved(PositionSnapshot anchor, PositionSnapshot current, double tolerance) {
        if (tolerance < 0 || !Double.isFinite(tolerance)) throw new IllegalArgumentException("Invalid tolerance");
        if (!anchor.worldId().equals(current.worldId())) return true;
        double dx = anchor.x() - current.x(), dy = anchor.y() - current.y(), dz = anchor.z() - current.z();
        return dx * dx + dy * dy + dz * dz > tolerance * tolerance;
    }
}
