package com.mrsuffix.tpapro.integration.combat;

import org.bukkit.entity.Player;

public interface CombatIntegration {
    String name();
    boolean available();
    boolean inCombat(Player player);
}
