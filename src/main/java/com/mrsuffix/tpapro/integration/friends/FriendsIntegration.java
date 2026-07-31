package com.mrsuffix.tpapro.integration.friends;

import java.util.UUID;

public interface FriendsIntegration {
    String name();
    boolean available();
    boolean areFriends(UUID first, UUID second);
}
