package com.mrsuffix.tpapro.permission;

public enum Permission {
    USE("tpapro.use"), TPA("tpapro.tpa"), TPA_HERE("tpapro.tpahere"), ACCEPT("tpapro.accept"),
    DENY("tpapro.deny"), CANCEL("tpapro.cancel"), TOGGLE("tpapro.toggle"), AUTO_ACCEPT("tpapro.autaccept"),
    BLOCK("tpapro.block"), TRUST("tpapro.trust"), LIST("tpapro.list"), SETTINGS("tpapro.settings"),
    BACK("tpapro.back"), HISTORY("tpapro.history"), STATS("tpapro.stats"),
    BYPASS_COOLDOWN("tpapro.bypass.cooldown"), BYPASS_WARMUP("tpapro.bypass.warmup"),
    BYPASS_COST("tpapro.bypass.cost"), BYPASS_COMBAT("tpapro.bypass.combat"),
    BYPASS_WORLD("tpapro.bypass.world"), BYPASS_REGION("tpapro.bypass.region"),
    BYPASS_SAFETY("tpapro.bypass.safety"), BYPASS_MOVE_CANCEL("tpapro.bypass.move-cancel"),
    BYPASS_DAMAGE_CANCEL("tpapro.bypass.damage-cancel"), ADMIN("tpapro.admin"),
    ADMIN_HELP("tpapro.admin.help"), ADMIN_RELOAD("tpapro.admin.reload"), ADMIN_INFO("tpapro.admin.info"),
    ADMIN_DEBUG("tpapro.admin.debug"), ADMIN_INSPECT("tpapro.admin.inspect"),
    ADMIN_CLEAR_REQUESTS("tpapro.admin.clear-requests"), ADMIN_RESET_COOLDOWN("tpapro.admin.reset-cooldown"),
    ADMIN_FORCE_TELEPORT("tpapro.admin.force-teleport");

    private final String node;

    Permission(String node) { this.node = node; }
    public String node() { return node; }
}
