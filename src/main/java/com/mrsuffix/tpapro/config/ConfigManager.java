package com.mrsuffix.tpapro.config;

import com.mrsuffix.tpapro.permission.PermissionGroupResolver;
import com.mrsuffix.tpapro.request.DuplicateBehavior;
import com.mrsuffix.tpapro.request.RequestType;
import com.mrsuffix.tpapro.util.Checks;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static com.mrsuffix.tpapro.config.ConfigurationBundle.*;

public final class ConfigManager {
    public static final int CONFIG_VERSION = 2;
    private static final int RETAINED_CONFIG_BACKUPS = 3;
    private static final List<String> RESOURCES = List.of("config.yml", "storage.yml", "restrictions.yml",
            "integrations.yml", "sounds.yml", "menus.yml", "messages/en_US.yml", "messages/tr_TR.yml");
    private final JavaPlugin plugin;
    private final Logger logger;
    private final AtomicReference<ConfigurationBundle> current = new AtomicReference<>();
    private volatile YamlConfiguration menus;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
    }

    public void initialize() {
        for (String resource : RESOURCES) copyIfMissing(resource);
        current.set(loadBundle());
    }

    public synchronized ConfigurationBundle reload() {
        ConfigurationBundle candidate = loadBundle();
        current.set(candidate);
        return candidate;
    }

    public ConfigurationBundle get() {
        ConfigurationBundle bundle = current.get();
        if (bundle == null) throw new IllegalStateException("Configuration has not been initialized");
        return bundle;
    }

    public YamlConfiguration menus() { return menus; }

    private ConfigurationBundle loadBundle() {
        YamlConfiguration config = load("config.yml");
        YamlConfiguration restrictions = load("restrictions.yml");
        YamlConfiguration integrations = load("integrations.yml");
        YamlConfiguration storage = load("storage.yml");
        YamlConfiguration sounds = load("sounds.yml");
        menus = load("menus.yml");
        validateVersion("config.yml", config);
        validateVersion("restrictions.yml", restrictions);
        validateVersion("integrations.yml", integrations);
        validateVersion("storage.yml", storage);
        validateVersion("sounds.yml", sounds);
        validateVersion("menus.yml", menus);
        return new ConfigurationBundle(loadMain(config), loadRestrictions(restrictions),
                loadIntegrations(integrations), loadStorage(storage), loadSounds(sounds));
    }

    private Main loadMain(YamlConfiguration y) {
        Language language = new Language(locale(y.getString("language.default", "en_US")),
                locale(y.getString("language.fallback", "en_US")), y.getBoolean("language.allow-player-selection", true));
        Requests requests = new Requests(integer(y, "requests.expiration-seconds", 1, 86400, 60),
                integer(y, "requests.send-cooldown-seconds", 0, 86400, 30),
                integer(y, "requests.max-pending-per-target", 1, 1000, 5),
                integer(y, "requests.max-outgoing-per-sender", 1, 1000, 3),
                enumValue(y, "requests.duplicate-behavior", DuplicateBehavior.class, DuplicateBehavior.REFRESH),
                y.getBoolean("requests.cancel-on-sender-quit", true),
                y.getBoolean("requests.invalidate-on-target-quit", true));
        Notification notification = new Notification(y.getBoolean("teleport.notifications.chat", true),
                y.getBoolean("teleport.notifications.action-bar", true), y.getBoolean("teleport.notifications.title", false),
                y.getBoolean("teleport.notifications.sound", true));
        Teleport teleport = new Teleport(integer(y, "teleport.warmup-seconds", 0, 3600, 5),
                integer(y, "teleport.successful-teleport-cooldown-seconds", 0, 86400, 0),
                y.getBoolean("teleport.cancel-on-move", true), decimal(y, "teleport.movement-tolerance", 0, 10, 0.15),
                y.getBoolean("teleport.cancel-on-damage", true), y.getBoolean("teleport.cancel-on-attack", true),
                y.getBoolean("teleport.cancel-on-world-change", true), y.getBoolean("teleport.cancel-on-quit", true),
                y.getBoolean("teleport.cancel-on-death", true), y.getBoolean("teleport.cancel-on-command", false), notification);
        Safety safety = new Safety(y.getBoolean("safe-teleport.enabled", true),
                y.getBoolean("safe-teleport.search-nearby-location", true),
                integer(y, "safe-teleport.horizontal-search-radius", 0, 16, 5),
                integer(y, "safe-teleport.vertical-search-radius", 0, 16, 4),
                integer(y, "safe-teleport.maximum-block-checks", 1, 10000, 512),
                y.getBoolean("safe-teleport.require-solid-ground", true), y.getBoolean("safe-teleport.prevent-lava", true),
                y.getBoolean("safe-teleport.prevent-fire", true), y.getBoolean("safe-teleport.prevent-campfire", true),
                y.getBoolean("safe-teleport.prevent-cactus", true), y.getBoolean("safe-teleport.prevent-magma", true),
                y.getBoolean("safe-teleport.prevent-powder-snow", true), y.getBoolean("safe-teleport.prevent-berry-bush", true),
                y.getBoolean("safe-teleport.prevent-void", true), y.getBoolean("safe-teleport.prevent-suffocation", true),
                y.getBoolean("safe-teleport.allow-nether-roof", false),
                integer(y, "safe-teleport.maximum-safe-fall-distance", 0, 64, 3));
        Trap trap = new Trap(enumValue(y, "trap-protection.mode", TrapMode.class, TrapMode.WARN),
                integer(y, "trap-protection.confirmation-seconds", 1, 300, 15),
                integer(y, "trap-protection.scan-radius", 1, 8, 3), y.getBoolean("trap-protection.lava", true),
                y.getBoolean("trap-protection.large-drop", true), y.getBoolean("trap-protection.tnt", true),
                y.getBoolean("trap-protection.end-crystals", true), y.getBoolean("trap-protection.suffocation", true),
                y.getBoolean("trap-protection.unsafe-enclosure", true), y.getBoolean("trap-protection.dangerous-blocks", true),
                y.getBoolean("trap-protection.target-in-combat", true));
        Benefits benefits = new Benefits(y.getBoolean("trusted-players.benefits.auto-accept", true),
                y.getBoolean("trusted-players.benefits.reduced-warmup", true),
                decimal(y, "trusted-players.benefits.warmup-multiplier", 0, 1, 0.5),
                y.getBoolean("trusted-players.benefits.reduced-cooldown", false),
                decimal(y, "trusted-players.benefits.cooldown-multiplier", 0, 1, 0.5),
                y.getBoolean("trusted-players.benefits.reduced-cost", false),
                decimal(y, "trusted-players.benefits.cost-multiplier", 0, 1, 0.5));
        Trusted trusted = new Trusted(integer(y, "trusted-players.maximum", 0, 10000, 30),
                y.getBoolean("trusted-players.mutual", false),
                enumValue(y, "trusted-players.friends-fallback", FriendsFallback.class, FriendsFallback.TRUSTED), benefits);
        Back back = new Back(y.getBoolean("back.enabled", true), integer(y, "back.expiration-seconds", 1, 604800, 600),
                integer(y, "back.cooldown-seconds", 0, 86400, 30), new SaveOn(y.getBoolean("back.save-on.tpa", true),
                y.getBoolean("back.save-on.tpahere", true), y.getBoolean("back.save-on.admin-teleport", true),
                y.getBoolean("back.save-on.death", false), y.getBoolean("back.save-on.portal", false)));
        History history = new History(y.getBoolean("history.enabled", true),
                integer(y, "history.default-size", 1, 1000, 20),
                integer(y, "history.retention-days", 1, 36500, 90));
        Statistics statistics = new Statistics(y.getBoolean("statistics.enabled", true),
                integer(y, "statistics.flush-seconds", 5, 3600, 30));
        return new Main(y.getBoolean("debug", false), language, requests, teleport, safety, trap, trusted, back,
                history, statistics, y.getBoolean("gui.enabled", true), loadPermissionGroups(y));
    }

    private Restrictions loadRestrictions(YamlConfiguration y) {
        Set<String> worldNames = new HashSet<>();
        y.getStringList("world-restrictions.worlds").stream().map(s -> s.toLowerCase(Locale.ROOT)).forEach(worldNames::add);
        Set<Route> routes = new HashSet<>();
        for (Map<?, ?> raw : y.getMapList("world-restrictions.cross-world.blocked-routes")) {
            Object from = raw.get("from"); Object to = raw.get("to");
            if (from instanceof String f && to instanceof String t && !f.isBlank() && !t.isBlank()) {
                routes.add(new Route(f.toLowerCase(Locale.ROOT), t.toLowerCase(Locale.ROOT)));
            } else logger.warning("Ignoring invalid blocked world route");
        }
        Worlds worlds = new Worlds(enumValue(y, "world-restrictions.mode", WorldMode.class, WorldMode.BLACKLIST),
                worldNames, y.getBoolean("world-restrictions.cross-world.enabled", true), routes);
        Combat combat = new Combat(y.getBoolean("combat.enabled", true), integer(y, "combat.duration-seconds", 1, 3600, 15),
                y.getBoolean("combat.block-request-sending", true), y.getBoolean("combat.block-request-accepting", true),
                y.getBoolean("combat.block-teleport-start", true), y.getBoolean("combat.cancel-active-warmup", true));
        Region region = new Region(y.getBoolean("region.enabled", true), y.getBoolean("region.check-source", true),
                y.getBoolean("region.check-destination", true));
        return new Restrictions(worlds, combat, region);
    }

    private Integrations loadIntegrations(YamlConfiguration y) {
        Map<RequestType, Double> costs = Map.of(RequestType.TPA, money(y, "economy.costs.tpa", 100),
                RequestType.TPA_HERE, money(y, "economy.costs.tpahere", 150));
        Economy economy = new Economy(y.getBoolean("economy.enabled", false),
                enumValue(y, "economy.charge-mode", ChargeMode.class, ChargeMode.ON_SUCCESS),
                y.getBoolean("economy.refund-on-failure", true), costs, money(y, "economy.costs.tpback", 250));
        return new Integrations(economy, y.getBoolean("vault.enabled", true),
                y.getBoolean("placeholderapi.enabled", true), y.getBoolean("worldguard.enabled", true),
                y.getBoolean("combatlogx.enabled", true), y.getBoolean("pvpmanager.enabled", true));
    }

    private Storage loadStorage(YamlConfiguration y) {
        StorageType type = enumValue(y, "type", StorageType.class, StorageType.SQLITE);
        String username = y.getString("sql.username", "tpapro");
        String password = y.getString("sql.password", "");
        String url;
        if (type == StorageType.SQLITE) {
            String fileName = y.getString("sqlite.file", "tpapro.db");
            if (!fileName.matches("[A-Za-z0-9._-]+") || fileName.equals(".") || fileName.equals("..")) {
                logger.warning("Invalid SQLite file name; using tpapro.db"); fileName = "tpapro.db";
            }
            url = "jdbc:sqlite:" + new File(plugin.getDataFolder(), fileName).getAbsolutePath();
            username = ""; password = "";
        } else {
            String host = y.getString("sql.host", "localhost").replaceAll("[^A-Za-z0-9_.:-]", "");
            int defaultPort = type == StorageType.POSTGRESQL ? 5432 : 3306;
            int port = integer(y, "sql.port", 1, 65535, defaultPort);
            String database = safeIdentifier(y.getString("sql.database", "tpapro"), "tpapro");
            String params = y.getString("sql.parameters", "");
            String scheme = switch (type) { case MYSQL -> "mysql"; case MARIADB -> "mariadb"; case POSTGRESQL -> "postgresql"; default -> throw new IllegalStateException(); };
            url = "jdbc:" + scheme + "://" + host + ":" + port + "/" + database + (params.isBlank() ? "" : "?" + params);
        }
        int max = integer(y, "pool.maximum-size", 1, 100, type == StorageType.SQLITE ? 1 : 10);
        int min = Math.min(max, integer(y, "pool.minimum-idle", 0, 100, type == StorageType.SQLITE ? 1 : 1));
        return new Storage(type, url, username, password, max, min,
                longValue(y, "pool.connection-timeout-millis", 250, 120000, 10000),
                longValue(y, "pool.max-lifetime-millis", 30000, 7200000, 1800000),
                longValue(y, "pool.leak-detection-threshold-millis", 0, 600000, 0),
                integer(y, "shutdown-timeout-seconds", 1, 60, 10), y.getBoolean("cooldowns.persist", false));
    }

    private Map<String, SoundSetting> loadSounds(YamlConfiguration y) {
        boolean global = y.getBoolean("enabled", true);
        Map<String, SoundSetting> result = new HashMap<>();
        for (String key : y.getKeys(false)) {
            if (key.equals("enabled") || key.equals("config-version")) continue;
            ConfigurationSection section = y.getConfigurationSection(key);
            if (section == null) continue;
            String sound = section.getString("sound", "");
            String normalized = normalizeSound(sound);
            if (normalized == null) { logger.warning("Sound " + key + " references unknown sound '" + sound + "' and was disabled"); continue; }
            result.put(key, new SoundSetting(global, normalized, (float) decimal(section, "volume", 0, 10, 1),
                    (float) decimal(section, "pitch", 0.5, 2, 1)));
        }
        return Map.copyOf(result);
    }

    private PermissionGroups loadPermissionGroups(YamlConfiguration y) {
        Map<String, List<PermissionGroupResolver.Entry>> groups = new HashMap<>();
        ConfigurationSection root = y.getConfigurationSection("permission-groups");
        if (root == null) return new PermissionGroups(Map.of());
        for (String key : root.getKeys(false)) {
            List<PermissionGroupResolver.Entry> entries = new ArrayList<>();
            for (Map<?, ?> raw : y.getMapList("permission-groups." + key)) {
                Object permission = raw.get("permission"); Object value = raw.get("value");
                if (permission instanceof String node && node.matches("[a-z0-9_.-]+") && value instanceof Number number
                        && Double.isFinite(number.doubleValue()) && number.doubleValue() >= 0) {
                    entries.add(new PermissionGroupResolver.Entry(node, number.doubleValue()));
                } else logger.warning("Ignoring invalid permission group entry in " + key);
            }
            groups.put(key, List.copyOf(entries));
        }
        return new PermissionGroups(groups);
    }

    private YamlConfiguration load(String resource) {
        File file = new File(plugin.getDataFolder(), resource);
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        if (!file.isFile()) throw new IllegalStateException("Missing configuration " + resource);
        return configuration;
    }

    private void copyIfMissing(String resource) {
        File file = new File(plugin.getDataFolder(), resource);
        if (!file.exists()) plugin.saveResource(resource, false);
    }

    private void validateVersion(String name, YamlConfiguration y) {
        int version = y.getInt("config-version", 0);
        if (version > CONFIG_VERSION) logger.warning(name + " is from a newer TpaPro version; unknown keys are retained.");
        if (version < CONFIG_VERSION) {
            File source = new File(plugin.getDataFolder(), name);
            try { ConfigBackupManager.backup(source.toPath(), new File(plugin.getDataFolder(), "backups").toPath(), RETAINED_CONFIG_BACKUPS); }
            catch (IOException e) { throw new IllegalStateException("Could not back up old " + name, e); }
            logger.warning(name + " uses configuration version " + version + "; a rotated backup was created. Safe defaults cover missing keys.");
        }
    }

    @SuppressWarnings("deprecation")
    private String normalizeSound(String raw) {
        try {
            if (raw.matches("[A-Z0-9_]+")) {
                org.bukkit.Sound sound = org.bukkit.Sound.valueOf(raw);
                NamespacedKey key = Registry.SOUNDS.getKey(sound);
                return key == null ? null : key.asString();
            }
            if (!raw.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")) return null;
            NamespacedKey key = NamespacedKey.fromString(raw);
            if (key == null) return null;
            if (key.getNamespace().equals(NamespacedKey.MINECRAFT) && Registry.SOUNDS.get(key) == null) return null;
            return key.asString();
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private int integer(ConfigurationSection y, String path, int min, int max, int fallback) {
        int raw = y.getInt(path, fallback); int value = Checks.boundedInt(raw, min, max, fallback);
        if (raw != value) logger.warning(path + " is out of range; using " + fallback);
        return value;
    }

    private long longValue(ConfigurationSection y, String path, long min, long max, long fallback) {
        long raw = y.getLong(path, fallback); long value = Checks.boundedLong(raw, min, max, fallback);
        if (raw != value) logger.warning(path + " is out of range; using " + fallback);
        return value;
    }

    private double decimal(ConfigurationSection y, String path, double min, double max, double fallback) {
        double raw = y.getDouble(path, fallback); double value = Checks.boundedDouble(raw, min, max, fallback);
        if (Double.compare(raw, value) != 0) logger.warning(path + " is invalid; using " + fallback);
        return value;
    }

    private double money(ConfigurationSection y, String path, double fallback) {
        double raw = y.getDouble(path, fallback); double value = Checks.nonNegativeMoney(raw, fallback);
        if (Double.compare(raw, value) != 0) logger.warning(path + " is invalid; using " + fallback);
        return value;
    }

    private <E extends Enum<E>> E enumValue(ConfigurationSection y, String path, Class<E> type, E fallback) {
        String raw = y.getString(path, fallback.name());
        try { return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT).replace('-', '_')); }
        catch (RuntimeException invalid) { logger.warning(path + " has invalid value '" + raw + "'; using " + fallback); return fallback; }
    }

    private String locale(String value) {
        if (value != null && value.matches("[a-z]{2}_[A-Z]{2}")) return value;
        logger.warning("Invalid locale identifier '" + value + "'; using en_US"); return "en_US";
    }

    private String safeIdentifier(String value, String fallback) {
        return value != null && value.matches("[A-Za-z0-9_]+") ? value : fallback;
    }
}
