package com.mrsuffix.tpapro.integration.friends;

import java.util.UUID;

public final class NoFriendsIntegration implements FriendsIntegration {
    @Override public String name() { return "None"; }
    @Override public boolean available() { return false; }
    @Override public boolean areFriends(UUID first, UUID second) { return false; }
}
