package com.mrsuffix.tpapro.integration.combat;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ReflectiveCombatIntegration implements CombatIntegration {
    private final Plugin plugin; private final String name; private final Logger logger; private volatile boolean disabled;
    public ReflectiveCombatIntegration(Plugin plugin, String name, Logger logger) { this.plugin = plugin; this.name = name; this.logger = logger; }
    @Override public String name() { return name; }
    @Override public boolean available() { return plugin != null && plugin.isEnabled() && !disabled; }
    @Override public boolean inCombat(Player player) {
        if (!available()) return false;
        try {
            return switch (name) {
                case "CombatLogX" -> invokeCombatLogX(player);
                case "PvPManager" -> invokePvpManager(player);
                default -> false;
            };
        } catch (ReflectiveOperationException | LinkageError failure) {
            disabled = true; logger.log(Level.WARNING, name + " combat hook became unavailable; using built-in tracking", failure); return false;
        }
    }
    private boolean invokeCombatLogX(Player player) throws ReflectiveOperationException {
        Object manager = method(plugin.getClass(), "getCombatManager").invoke(plugin);
        return (boolean) method(manager.getClass(), "isInCombat", Player.class).invoke(manager, player);
    }
    private boolean invokePvpManager(Player player) throws ReflectiveOperationException {
        Object handler = method(plugin.getClass(), "getPlayerHandler").invoke(plugin);
        Object pvpPlayer = method(handler.getClass(), "get", Player.class).invoke(handler, player);
        return (boolean) method(pvpPlayer.getClass(), "isInCombat").invoke(pvpPlayer);
    }
    private static Method method(Class<?> type, String name, Class<?>... arguments) throws NoSuchMethodException {
        Method method = type.getMethod(name, arguments); method.setAccessible(true); return method;
    }
}
