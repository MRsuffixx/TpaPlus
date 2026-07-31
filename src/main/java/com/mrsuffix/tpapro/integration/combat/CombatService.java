package com.mrsuffix.tpapro.integration.combat;

import org.bukkit.entity.Player;

import java.util.List;

public final class CombatService {
    private final BuiltInCombatTracker builtIn; private final List<CombatIntegration> external;
    public CombatService(BuiltInCombatTracker builtIn, List<CombatIntegration> external) { this.builtIn = builtIn; this.external = List.copyOf(external); }
    public boolean inCombat(Player player) {
        for (CombatIntegration integration : external) if (integration.available() && integration.inCombat(player)) return true;
        return builtIn.inCombat(player);
    }
    public List<String> enabledIntegrations() { return external.stream().filter(CombatIntegration::available).map(CombatIntegration::name).toList(); }
}
