package com.mrsuffix.tpapro.locale;

import com.mrsuffix.tpapro.config.ConfigManager;
import com.mrsuffix.tpapro.config.ConfigurationBundle.SoundSetting;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class SoundService {
    private final ConfigManager configs;
    private final Predicate<Player> playerEnabled;
    private final Logger logger;
    private final Set<String> reportedInvalidSounds = ConcurrentHashMap.newKeySet();

    public SoundService(ConfigManager configs, Predicate<Player> playerEnabled, Logger logger) {
        this.configs = Objects.requireNonNull(configs, "configs");
        this.playerEnabled = Objects.requireNonNull(playerEnabled, "playerEnabled");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void play(Player player, String key) {
        SoundSetting setting = configs.get().sounds().get(key);
        if (setting == null || !setting.enabled() || !playerEnabled.test(player)) return;
        try {
            String raw = setting.sound().toLowerCase(Locale.ROOT);
            Key soundKey = raw.contains(":") ? Key.key(raw) : Key.key("minecraft", raw);
            player.playSound(Sound.sound(soundKey, Sound.Source.MASTER, setting.volume(), setting.pitch()));
        } catch (RuntimeException invalid) {
            String identity = key + "=" + setting.sound();
            if (reportedInvalidSounds.add(identity))
                logger.warning("Could not play configured sound " + identity + "; further failures for this value are suppressed");
        }
    }
}
