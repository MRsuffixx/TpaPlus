package com.mrsuffix.tpapro.integration.worldguard;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;

public final class WorldGuardFlagRegistrar {
    private static StateFlag teleportFlag;
    private WorldGuardFlagRegistrar() { }
    public static void register() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        StateFlag proposed = new StateFlag("tpapro-teleport", true);
        try { registry.register(proposed); teleportFlag = proposed; }
        catch (FlagConflictException conflict) {
            Flag<?> existing = registry.get("tpapro-teleport");
            if (existing instanceof StateFlag stateFlag) teleportFlag = stateFlag;
            else throw new IllegalStateException("WorldGuard flag tpapro-teleport exists with an incompatible type", conflict);
        }
    }
    public static StateFlag flag() { return teleportFlag; }
}
