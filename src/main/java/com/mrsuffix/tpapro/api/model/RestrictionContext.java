package com.mrsuffix.tpapro.api.model;

import com.mrsuffix.tpapro.request.RequestType;

import java.util.UUID;

public record RestrictionContext(UUID senderId, UUID targetId, RequestType requestType, String sourceWorld,
                                 String destinationWorld) { }
