package com.mrsuffix.tpapro.config;

import com.mrsuffix.tpapro.permission.PermissionGroupResolver;
import com.mrsuffix.tpapro.request.DuplicateBehavior;
import com.mrsuffix.tpapro.request.RequestType;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ConfigurationBundle(Main main, Restrictions restrictions, Integrations integrations, Storage storage,
                                  Map<String, SoundSetting> sounds) {
    public enum TrapMode { OFF, WARN, BLOCK }
    public enum WorldMode { BLACKLIST, WHITELIST }
    public enum ChargeMode { ON_REQUEST, ON_ACCEPT, ON_SUCCESS }
    public enum StorageType { SQLITE, MYSQL, MARIADB, POSTGRESQL }
    public enum FriendsFallback { TRUSTED, UNAVAILABLE }

    public record Main(boolean debug, Language language, Requests requests, Teleport teleport, Safety safety,
                       Trap trap, Trusted trusted, Back back, History history, Statistics statistics,
                       boolean guiEnabled, PermissionGroups permissionGroups) { }
    public record Language(String defaultLocale, String fallbackLocale, boolean allowPlayerSelection) { }
    public record Requests(int expirationSeconds, int sendCooldownSeconds, int maxPendingPerTarget,
                           int maxOutgoingPerSender, DuplicateBehavior duplicateBehavior,
                           boolean cancelOnSenderQuit, boolean invalidateOnTargetQuit) { }
    public record Teleport(int warmupSeconds, int successfulCooldownSeconds, boolean cancelOnMove,
                           double movementTolerance, boolean cancelOnDamage, boolean cancelOnAttack,
                           boolean cancelOnWorldChange, boolean cancelOnQuit, boolean cancelOnDeath,
                           boolean cancelOnCommand, Notification notification) { }
    public record Notification(boolean chat, boolean actionBar, boolean title, boolean sound) { }
    public record Safety(boolean enabled, boolean searchNearby, int horizontalRadius, int verticalRadius,
                         int maximumBlockChecks, boolean requireSolidGround, boolean preventLava,
                         boolean preventFire, boolean preventCampfire, boolean preventCactus,
                         boolean preventMagma, boolean preventPowderSnow, boolean preventBerryBush,
                         boolean preventVoid, boolean preventSuffocation, boolean allowNetherRoof,
                         int maximumSafeFallDistance) { }
    public record Trap(TrapMode mode, int confirmationSeconds, int scanRadius, boolean lava, boolean largeDrop,
                       boolean tnt, boolean endCrystals, boolean suffocation, boolean unsafeEnclosure,
                       boolean dangerousBlocks, boolean targetInCombat) { }
    public record Trusted(int maximum, boolean mutual, FriendsFallback friendsFallback, Benefits benefits) { }
    public record Benefits(boolean autoAccept, boolean reducedWarmup, double warmupMultiplier,
                           boolean reducedCooldown, double cooldownMultiplier, boolean reducedCost,
                           double costMultiplier) { }
    public record Back(boolean enabled, int expirationSeconds, int cooldownSeconds, SaveOn saveOn) { }
    public record SaveOn(boolean tpa, boolean tpaHere, boolean adminTeleport, boolean death, boolean portal) { }
    public record History(boolean enabled, int defaultSize, int retentionDays) { }
    public record Statistics(boolean enabled, int flushSeconds) { }
    public record PermissionGroups(Map<String, List<PermissionGroupResolver.Entry>> values) {
        public PermissionGroups { values = Map.copyOf(values); }
        public List<PermissionGroupResolver.Entry> get(String key) { return values.getOrDefault(key, List.of()); }
    }

    public record Restrictions(Worlds worlds, Combat combat, Region region) { }
    public record Worlds(WorldMode mode, Set<String> worlds, boolean crossWorldEnabled, Set<Route> blockedRoutes) {
        public Worlds { worlds = Set.copyOf(worlds); blockedRoutes = Set.copyOf(blockedRoutes); }
    }
    public record Route(String from, String to) { }
    public record Combat(boolean enabled, int durationSeconds, boolean blockSending, boolean blockAccepting,
                         boolean blockTeleportStart, boolean cancelActiveWarmup) { }
    public record Region(boolean enabled, boolean checkSource, boolean checkDestination) { }

    public record Integrations(Economy economy, boolean vault, boolean placeholderApi, boolean worldGuard,
                               boolean combatLogX, boolean pvpManager) { }
    public record Economy(boolean enabled, ChargeMode chargeMode, boolean refundOnFailure,
                          Map<RequestType, Double> requestCosts, double backCost) {
        public Economy { requestCosts = Map.copyOf(requestCosts); }
        public double cost(RequestType type) { return requestCosts.getOrDefault(type, 0.0); }
    }

    public record Storage(StorageType type, String jdbcUrl, String username, String password, int maximumPoolSize,
                          int minimumIdle, long connectionTimeoutMillis, long maxLifetimeMillis,
                          long leakDetectionMillis, int shutdownTimeoutSeconds, boolean persistCooldowns) { }

    public record SoundSetting(boolean enabled, String sound, float volume, float pitch) { }
}
