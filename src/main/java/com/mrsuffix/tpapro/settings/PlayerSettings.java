package com.mrsuffix.tpapro.settings;

import java.util.Objects;

public record PlayerSettings(PrivacyMode privacyMode, boolean autoAccept, boolean autoAcceptTrustedOnly,
                             boolean chatNotifications, boolean actionBarNotifications,
                             boolean titleNotifications, boolean sounds, boolean trapWarnings, String language) {
    public PlayerSettings {
        Objects.requireNonNull(privacyMode, "privacyMode");
        if (language != null && !language.matches("[a-z]{2}_[A-Z]{2}")) {
            throw new IllegalArgumentException("Invalid locale identifier");
        }
    }

    public static PlayerSettings defaults(String locale) {
        return new PlayerSettings(PrivacyMode.EVERYONE, false, true, true, true, true, true, true, locale);
    }

    public PlayerSettings withPrivacy(PrivacyMode mode) {
        return new PlayerSettings(mode, autoAccept, autoAcceptTrustedOnly, chatNotifications, actionBarNotifications,
                titleNotifications, sounds, trapWarnings, language);
    }

    public PlayerSettings withAutoAccept(boolean enabled) {
        return new PlayerSettings(privacyMode, enabled, autoAcceptTrustedOnly, chatNotifications, actionBarNotifications,
                titleNotifications, sounds, trapWarnings, language);
    }

    public PlayerSettings withLanguage(String locale) {
        return new PlayerSettings(privacyMode, autoAccept, autoAcceptTrustedOnly, chatNotifications,
                actionBarNotifications, titleNotifications, sounds, trapWarnings, locale);
    }

    public PlayerSettings withNotification(String name, boolean enabled) {
        return switch (name) {
            case "chat" -> new PlayerSettings(privacyMode, autoAccept, autoAcceptTrustedOnly, enabled,
                    actionBarNotifications, titleNotifications, sounds, trapWarnings, language);
            case "actionbar" -> new PlayerSettings(privacyMode, autoAccept, autoAcceptTrustedOnly, chatNotifications,
                    enabled, titleNotifications, sounds, trapWarnings, language);
            case "title" -> new PlayerSettings(privacyMode, autoAccept, autoAcceptTrustedOnly, chatNotifications,
                    actionBarNotifications, enabled, sounds, trapWarnings, language);
            case "sounds" -> new PlayerSettings(privacyMode, autoAccept, autoAcceptTrustedOnly, chatNotifications,
                    actionBarNotifications, titleNotifications, enabled, trapWarnings, language);
            case "trapwarnings" -> new PlayerSettings(privacyMode, autoAccept, autoAcceptTrustedOnly, chatNotifications,
                    actionBarNotifications, titleNotifications, sounds, enabled, language);
            default -> throw new IllegalArgumentException("Unknown setting " + name);
        };
    }
}
