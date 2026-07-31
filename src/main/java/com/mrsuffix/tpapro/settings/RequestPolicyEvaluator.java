package com.mrsuffix.tpapro.settings;

public final class RequestPolicyEvaluator {
    public enum Decision { ALLOW, BLOCKED, DISABLED, NOT_TRUSTED, NOT_FRIEND, DIFFERENT_WORLD }

    public Decision evaluate(PlayerSettings targetSettings, boolean requesterBlocked, boolean requesterTrusted,
                             boolean requesterFriend, boolean friendsAvailable, boolean trustAsFriendsFallback,
                             boolean sameWorld) {
        if (requesterBlocked) return Decision.BLOCKED;
        return switch (targetSettings.privacyMode()) {
            case EVERYONE -> Decision.ALLOW;
            case DISABLED -> Decision.DISABLED;
            case TRUSTED_ONLY -> requesterTrusted ? Decision.ALLOW : Decision.NOT_TRUSTED;
            case SAME_WORLD_ONLY -> sameWorld ? Decision.ALLOW : Decision.DIFFERENT_WORLD;
            case FRIENDS_ONLY -> (friendsAvailable ? requesterFriend : trustAsFriendsFallback && requesterTrusted)
                    ? Decision.ALLOW : Decision.NOT_FRIEND;
        };
    }
}
