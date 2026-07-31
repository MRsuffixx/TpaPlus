package com.mrsuffix.tpapro.locale;

import com.mrsuffix.tpapro.config.ConfigManager;
import com.mrsuffix.tpapro.config.ConfigurationBundle.Language;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocaleManagerSecurityTest {
    @TempDir Path temporary;

    @Test void playerSuppliedMiniMessageTagsRemainLiteralText() throws Exception {
        Path messages = Files.createDirectories(temporary.resolve("messages"));
        Files.writeString(messages.resolve("en_US.yml"), "value: \"<player>\"\n");
        JavaPlugin plugin = mock(JavaPlugin.class); when(plugin.getDataFolder()).thenReturn(temporary.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        ConfigManager configs = mock(ConfigManager.class, RETURNS_DEEP_STUBS);
        when(configs.get().main().language()).thenReturn(new Language("en_US", "en_US", true));
        LocaleManager locales = new LocaleManager(plugin, configs); locales.reload();
        var component = locales.componentForLocale("en_US", "value", Map.of("player", "<red>malicious</red>"));
        assertThat(PlainTextComponentSerializer.plainText().serialize(component)).isEqualTo("<red>malicious</red>");
    }
}
