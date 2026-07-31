package com.mrsuffix.tpapro.locale;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MessageCatalog {
    private final String fallbackLocale;
    private final Map<String, Map<String, String>> messages;

    public MessageCatalog(String fallbackLocale, Map<String, Map<String, String>> messages) {
        this.fallbackLocale = Objects.requireNonNull(fallbackLocale, "fallbackLocale");
        this.messages = messages.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
        if (!this.messages.containsKey(fallbackLocale)) throw new IllegalArgumentException("Fallback locale is missing");
    }

    public String get(String locale, String key) {
        Map<String, String> selected = messages.getOrDefault(locale, messages.get(fallbackLocale));
        String value = selected.get(key);
        if (value != null) return value;
        value = messages.get(fallbackLocale).get(key);
        return value != null ? value : "<red>Missing message: " + escape(key) + "</red>";
    }

    public Set<String> locales() { return messages.keySet(); }
    public Set<String> keys(String locale) { return messages.getOrDefault(locale, Map.of()).keySet(); }

    private static String escape(String value) { return value.replace("<", "").replace(">", ""); }
}
