package com.mrsuffix.tpapro.restriction;

import com.mrsuffix.tpapro.config.ConfigManager;
import com.mrsuffix.tpapro.config.ConfigurationBundle.Route;
import com.mrsuffix.tpapro.config.ConfigurationBundle.WorldMode;
import com.mrsuffix.tpapro.config.ConfigurationBundle.Worlds;
import org.bukkit.World;

import java.util.Locale;

public final class WorldRestrictionService {
    public record Result(boolean allowed, String reason) { }
    private final ConfigManager configs;
    public WorldRestrictionService(ConfigManager configs) { this.configs = configs; }
    public Result check(World source, World destination, boolean bypass) {
        if (bypass) return new Result(true, "bypass");
        if (source == null || destination == null) return new Result(false, "world-unavailable");
        Worlds rules = configs.get().restrictions().worlds();
        String from = source.getName().toLowerCase(Locale.ROOT), to = destination.getName().toLowerCase(Locale.ROOT);
        boolean sourceListed = rules.worlds().contains(from), targetListed = rules.worlds().contains(to);
        boolean allowedByList = rules.mode() == WorldMode.BLACKLIST ? !sourceListed && !targetListed : sourceListed && targetListed;
        if (!allowedByList) return new Result(false, "world-blocked");
        if (!source.equals(destination) && !rules.crossWorldEnabled()) return new Result(false, "cross-world-disabled");
        if (!source.equals(destination) && rules.blockedRoutes().contains(new Route(from, to))) return new Result(false, "cross-world-route");
        return new Result(true, "allowed");
    }
}
