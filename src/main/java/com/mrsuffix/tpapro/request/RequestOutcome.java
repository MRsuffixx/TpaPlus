package com.mrsuffix.tpapro.request;

import java.util.List;

public record RequestOutcome(boolean success, RequestFailure failure, TeleportRequest request,
                             List<TeleportRequest> candidates, boolean replaced, boolean refreshed) {
    public RequestOutcome {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public static RequestOutcome success(TeleportRequest request) {
        return new RequestOutcome(true, RequestFailure.NONE, request, List.of(), false, false);
    }

    public static RequestOutcome changed(TeleportRequest request, boolean replaced, boolean refreshed) {
        return new RequestOutcome(true, RequestFailure.NONE, request, List.of(), replaced, refreshed);
    }

    public static RequestOutcome failure(RequestFailure failure) {
        return new RequestOutcome(false, failure, null, List.of(), false, false);
    }

    public static RequestOutcome multiple(List<TeleportRequest> candidates) {
        return new RequestOutcome(false, RequestFailure.MULTIPLE_MATCHES, null, candidates, false, false);
    }
}
