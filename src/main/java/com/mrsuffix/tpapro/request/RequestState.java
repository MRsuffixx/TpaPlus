package com.mrsuffix.tpapro.request;

public enum RequestState {
    PENDING,
    ACCEPTED,
    DENIED,
    CANCELLED,
    EXPIRED,
    INVALIDATED,
    COMPLETED,
    FAILED;

    public boolean actionable() {
        return this == PENDING;
    }

    public boolean terminal() {
        return switch (this) {
            case DENIED, CANCELLED, EXPIRED, INVALIDATED, COMPLETED, FAILED -> true;
            case PENDING, ACCEPTED -> false;
        };
    }
}
