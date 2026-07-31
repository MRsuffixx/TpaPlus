package com.mrsuffix.tpapro.locale;

import com.mrsuffix.tpapro.config.ConfigManager;
import com.mrsuffix.tpapro.config.ConfigurationBundle;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LocaleManager {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, String> preferences = new ConcurrentHashMap<>();
    private volatile MessageCatalog catalog;

    public LocaleManager(JavaPlugin plugin, ConfigManager configs) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configs = Objects.requireNonNull(configs, "configs");
    }

    public synchronized void reload() {
        ConfigurationBundle.Language settings = configs.get().main().language();
        File directory = new File(plugin.getDataFolder(), "messages");
        Map<String, Map<String, String>> all = new HashMap<>();
        File[] files = directory.listFiles((dir, name) -> name.matches("[a-z]{2}_[A-Z]{2}\\.yml"));
        if (files != null) {
            for (File file : files) {
                String locale = file.getName().substring(0, file.getName().length() - 4);
                all.put(locale, flatten(YamlConfiguration.loadConfiguration(file)));
            }
        }
        if (!all.containsKey(settings.fallbackLocale())) {
            plugin.getLogger().warning("Configured fallback locale is missing; using en_US");
            settings = new ConfigurationBundle.Language(settings.defaultLocale(), "en_US", settings.allowPlayerSelection());
        }
        if (!all.containsKey("en_US")) throw new IllegalStateException("Bundled en_US locale is missing");
        catalog = new MessageCatalog(settings.fallbackLocale(), all);
        auditLanguageKeys();
        preferences.entrySet().removeIf(entry -> !catalog.locales().contains(entry.getValue()));
    }

    public void setPreference(UUID playerId, String locale) {
        if (!isRegistered(locale)) throw new IllegalArgumentException("Unregistered locale");
        preferences.put(playerId, locale);
    }

    public void clearPreference(UUID playerId) { preferences.remove(playerId); }
    public String locale(UUID playerId) { return preferences.getOrDefault(playerId, configs.get().main().language().defaultLocale()); }
    public boolean isRegistered(String locale) { return locale != null && locale.matches("[a-z]{2}_[A-Z]{2}") && catalog.locales().contains(locale); }
    public Set<String> availableLocales() { return catalog.locales(); }

    public Component component(UUID viewer, String key, Map<String, ?> values) {
        return componentForLocale(locale(viewer), key, values);
    }

    public Component componentForLocale(String locale, String key, Map<String, ?> values) {
        TagResolver.Builder resolver = TagResolver.builder();
        values.forEach((name, value) -> {
            if (!name.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Unsafe placeholder name");
            Component component = value instanceof Component c ? c : Component.text(String.valueOf(value));
            resolver.resolver(Placeholder.component(name, component));
        });
        return miniMessage.deserialize(catalog.get(locale, key), resolver.build());
    }

    public Component prefixed(UUID viewer, String key, Map<String, ?> values) {
        return component(viewer, "prefix", Map.of()).append(component(viewer, key, values));
    }

    public void send(Audience audience, UUID viewer, String key) { send(audience, viewer, key, Map.of()); }
    public void send(Audience audience, UUID viewer, String key, Map<String, ?> values) {
        audience.sendMessage(prefixed(viewer, key, values));
    }

    public String plainTemplate(String locale, String key) { return catalog.get(locale, key); }

    private void auditLanguageKeys() {
        Set<String> reference = catalog.keys("en_US");
        for (String locale : catalog.locales()) {
            Set<String> missing = new HashSet<>(reference); missing.removeAll(catalog.keys(locale));
            Set<String> extra = new HashSet<>(catalog.keys(locale)); extra.removeAll(reference);
            if (!missing.isEmpty()) plugin.getLogger().warning(locale + " is missing message keys: " + missing);
            if (!extra.isEmpty()) plugin.getLogger().warning(locale + " has keys not present in en_US: " + extra);
        }
    }

    private static Map<String, String> flatten(ConfigurationSection root) {
        Map<String, String> result = new HashMap<>();
        flattenInto(root, "", result);
        return result;
    }

    private static void flattenInto(ConfigurationSection section, String prefix, Map<String, String> output) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = section.get(key);
            if (value instanceof ConfigurationSection child) flattenInto(child, path, output);
            else if (value instanceof String string) output.put(path, string);
        }
    }
}
